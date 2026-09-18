#!/usr/bin/env node
// Core Hub device MCP bridge.
//
// Agents (Hermes, Ekko, …) run this as an ordinary stdio MCP server. It opens a
// WebSocket to the Core Hub server, which forwards every byte to the app that
// a linked device shares (for example DaVinci Resolve on the user's Mac) and
// streams the app's replies back. Usage (written into profile configs by the
// server; not meant to be typed by hand):
//
//   core-hub-device-mcp.mjs <device_id> <app_id>
//
// Environment: HERMES_WEB_UI_URL, HERMES_WEB_UI_HOME, HERMES_WEB_UI_PROFILE,
// optional HERMES_WEB_UI_TOKEN. The profile's temporary token file is used
// when no explicit token is given, exactly like ekko-studio-mcp.

import { readFileSync } from 'node:fs'
import { homedir } from 'node:os'
import { join } from 'node:path'

const [deviceId, appId] = process.argv.slice(2)
const label = process.env.CORE_HUB_DEVICE_APP_LABEL || `${deviceId}/${appId}`

function appHome() {
  return process.env.HERMES_WEB_UI_HOME || process.env.HERMES_WEBUI_STATE_DIR || join(homedir(), '.hermes-web-ui')
}

function profileSegment(profile) {
  const raw = String(profile || '').trim()
  if (!raw) return ''
  const sanitized = raw.replace(/[<>:"/\\|?*\x00-\x1f]/g, '_')
  return sanitized === '.' || sanitized === '..' || sanitized.length > 128 ? '' : sanitized
}

function readToken(profile) {
  const explicit = process.env.HERMES_WEB_UI_TOKEN || process.env.AUTH_TOKEN
  if (explicit) return explicit.trim()
  const segment = profileSegment(profile)
  if (segment) {
    try { return readFileSync(join(appHome(), 'profiles', segment, '.model-run-token'), 'utf8').trim() } catch { /* fall through */ }
  }
  try { return readFileSync(join(appHome(), '.token'), 'utf8').trim() } catch { return '' }
}

function fail(message, code = 1) {
  // Answer a pending JSON-RPC initialize (if any arrives) with an error so the
  // agent sees a readable reason instead of a silent crash.
  process.stderr.write(`[core-hub-device-mcp] ${message}\n`)
  process.exit(code)
}

if (!deviceId || !appId) fail('usage: core-hub-device-mcp.mjs <device_id> <app_id>', 2)

const profile = String(process.env.HERMES_WEB_UI_PROFILE || '').trim()
const base = (process.env.HERMES_WEB_UI_URL || 'http://127.0.0.1:8648').replace(/\/$/, '')
const url = new URL(`${base}/api/devices/app-session`)
url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
url.searchParams.set('device', deviceId)
url.searchParams.set('app', appId)
if (profile) url.searchParams.set('profile', profile)
const token = readToken(profile)
if (token) url.searchParams.set('token', token)

const ws = new WebSocket(url)
ws.binaryType = 'arraybuffer' // Node's client hands binary frames over as Blob otherwise
let ready = false
const pendingStdin = []

ws.addEventListener('open', () => {
  // wait for the server's ready control frame before forwarding stdin
})
ws.addEventListener('message', event => {
  const data = event.data
  if (typeof data === 'string') {
    let control = null
    try { control = JSON.parse(data) } catch { control = null }
    if (control && typeof control === 'object' && typeof control.type === 'string') {
      if (control.type === 'ready') {
        ready = true
        for (const chunk of pendingStdin.splice(0)) ws.send(chunk)
        return
      }
      if (control.type === 'stderr') { process.stderr.write(`[${label}] ${control.data}\n`); return }
      if (control.type === 'exit') { process.stderr.write(`[${label}] app exited (${control.code ?? 'signal'})\n`); process.exit(typeof control.code === 'number' ? control.code : 0) }
      if (control.type === 'error') fail(`${label}: ${control.message}`)
      return
    }
    process.stdout.write(data)
    return
  }
  const buffer = data instanceof ArrayBuffer ? Buffer.from(data) : Buffer.from(data)
  process.stdout.write(buffer)
})
ws.addEventListener('close', event => {
  if (event.code === 1000) process.exit(0)
  fail(`${label}: connection closed (${event.code}${event.reason ? ` ${event.reason}` : ''})`)
})
ws.addEventListener('error', () => {
  fail(`${label}: cannot reach the Core Hub server at ${base}`)
})

process.stdin.on('data', chunk => {
  const bytes = Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk)
  if (ready && ws.readyState === 1) ws.send(bytes)
  else pendingStdin.push(bytes)
})
process.stdin.on('end', () => {
  try { ws.close(1000, 'stdin closed') } catch { /* ignore */ }
})
process.on('SIGTERM', () => { try { ws.close(1000, 'terminated') } catch { /* ignore */ } process.exit(0) })
