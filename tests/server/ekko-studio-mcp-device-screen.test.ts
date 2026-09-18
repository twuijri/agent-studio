import { spawn, type ChildProcessWithoutNullStreams } from 'node:child_process'
import { createServer, type Server } from 'node:http'
import { mkdtemp, mkdir, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, describe, expect, it } from 'vitest'

// The bundled MCP command gains screen tools in the devices toolset and sends
// the profile with browser broker calls so a Studio server can route them to
// the linked device bound to that profile (docs/DESKTOP-SERVER-MODE.md).

let child: ChildProcessWithoutNullStreams | null = null
let server: Server | null = null
let root = ''

afterEach(async () => {
  child?.kill()
  child = null
  await new Promise<void>(resolve => (server ? server.close(() => resolve()) : resolve()))
  server = null
  if (root) await rm(root, { recursive: true, force: true })
  root = ''
})

function rpcClient(proc: ChildProcessWithoutNullStreams) {
  let buffer = ''
  const waiters = new Map<number, (value: any) => void>()
  proc.stdout.on('data', chunk => {
    buffer += String(chunk)
    let newline = buffer.indexOf('\n')
    while (newline >= 0) {
      const line = buffer.slice(0, newline).trim()
      buffer = buffer.slice(newline + 1)
      if (line) {
        const response = JSON.parse(line)
        waiters.get(response.id)?.(response)
        waiters.delete(response.id)
      }
      newline = buffer.indexOf('\n')
    }
  })
  return (id: number, method: string, params: Record<string, unknown> = {}) => new Promise<any>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`RPC ${id} timed out`)), 8000)
    waiters.set(id, value => { clearTimeout(timer); resolve(value) })
    proc.stdin.write(`${JSON.stringify({ jsonrpc: '2.0', id, method, params })}\n`)
  })
}

async function listen(handler: Parameters<typeof createServer>[1]): Promise<number> {
  server = createServer(handler)
  await new Promise<void>((resolve, reject) => {
    server!.once('error', reject)
    server!.listen(0, '127.0.0.1', () => resolve())
  })
  const address = server.address()
  if (!address || typeof address === 'string') throw new Error('test server did not bind')
  return address.port
}

describe('ekko-studio-mcp devices toolset: linked device screen', () => {
  it('captures a screenshot as image content and posts screen actions through the Studio API', async () => {
    root = await mkdtemp(join(tmpdir(), 'mcp-screen-'))
    const requests: Array<{ method: string; url: string; body: string; profile: string }> = []
    const port = await listen(async (request, response) => {
      const chunks: Buffer[] = []
      for await (const chunk of request) chunks.push(Buffer.from(chunk))
      requests.push({ method: request.method || '', url: request.url || '', body: Buffer.concat(chunks).toString('utf8'), profile: String(request.headers['x-hermes-profile'] || '') })
      response.setHeader('Content-Type', 'application/json')
      if (request.url?.startsWith('/api/devices/peer-connections/c1/screen/action')) {
        response.end(JSON.stringify({ ok: true }))
        return
      }
      if (request.url?.startsWith('/api/devices/peer-connections/c1/screen')) {
        response.end(JSON.stringify({ media_type: 'image/png', data: 'AA==', width: 1440, height: 900, display_id: '1', displays: [{ id: '1', width: 1440, height: 900, scale: 2, primary: true }] }))
        return
      }
      response.statusCode = 404
      response.end(JSON.stringify({ error: 'not found' }))
    })

    child = spawn(process.execPath, [join(process.cwd(), 'bin/ekko-studio-mcp.mjs'), 'devices'], {
      env: { ...process.env, HERMES_WEB_UI_HOME: root, HERMES_WEB_UI_PORT: String(port), HERMES_WEB_UI_TOKEN: 'test-token', HERMES_WEB_UI_PROFILE: 'work' },
      stdio: ['pipe', 'pipe', 'pipe'],
    })
    const rpc = rpcClient(child)
    await rpc(1, 'initialize', { protocolVersion: '2024-11-05' })
    const catalog = await rpc(2, 'tools/call', { name: 'ekko_studio_devices_toolset', arguments: { action: 'list' } })
    const names = JSON.parse(catalog.result.content[0].text).operations.map((operation: any) => operation.name)
    expect(names).toContain('ekko_studio_lan_screen_capture')
    expect(names).toContain('ekko_studio_lan_screen_action')

    const shot = await rpc(3, 'tools/call', {
      name: 'ekko_studio_devices_toolset',
      arguments: { action: 'call', tool: 'ekko_studio_lan_screen_capture', arguments: { connection_id: 'c1', max_width: 800, token: 'test-token' } },
    })
    expect(shot.result.isError).toBeFalsy()
    expect(shot.result.content[1]).toEqual({ type: 'image', data: 'AA==', mimeType: 'image/png' })
    expect(JSON.parse(shot.result.content[0].text)).toMatchObject({ width: 1440, display_id: '1' })
    const capture = requests.find(item => item.url.includes('/screen?'))
    expect(capture?.url).toContain('max_width=800')
    expect(capture?.profile).toBe('work')

    const action = await rpc(4, 'tools/call', {
      name: 'ekko_studio_devices_toolset',
      arguments: { action: 'call', tool: 'ekko_studio_lan_screen_action', arguments: { connection_id: 'c1', action: 'click', x: 10, y: 20, token: 'test-token' } },
    })
    expect(JSON.parse(action.result.content[0].text)).toEqual({ ok: true })
    const posted = requests.find(item => item.url.endsWith('/screen/action'))
    expect(JSON.parse(posted!.body)).toEqual({ action: 'click', x: 10, y: 20 })
  })

  it('sends the profile with browser broker calls so a server gateway can pick the bound device', async () => {
    root = await mkdtemp(join(tmpdir(), 'mcp-browser-profile-'))
    const profiles: string[] = []
    const port = await listen(async (request, response) => {
      const chunks: Buffer[] = []
      for await (const chunk of request) chunks.push(Buffer.from(chunk))
      profiles.push(String(request.headers['x-hermes-profile'] || ''))
      response.setHeader('Content-Type', 'application/json')
      if (request.url === '/v1/session') {
        response.end(JSON.stringify({ client_id: 'client-1', session_token: 'session-1' }))
        return
      }
      const body = JSON.parse(Buffer.concat(chunks).toString('utf8'))
      response.end(JSON.stringify({ operation_id: body.operation_id, result: { tabs: [] } }))
    })
    const brokerRoot = join(root, 'desktop-browser')
    await mkdir(brokerRoot, { recursive: true, mode: 0o700 })
    await writeFile(join(brokerRoot, 'broker.json'), JSON.stringify({
      schema: 1, desktopPid: process.pid, endpoint: `http://127.0.0.1:${port}/v1`, token: 'gateway-token', instanceId: 'gw', createdAt: new Date().toISOString(), gateway: 'device-browser',
    }), { mode: 0o600 })

    child = spawn(process.execPath, [join(process.cwd(), 'bin/ekko-studio-mcp.mjs'), 'browser'], {
      env: { ...process.env, HERMES_WEB_UI_HOME: root, HERMES_WEB_UI_PROFILE: 'work' },
      stdio: ['pipe', 'pipe', 'pipe'],
    })
    const rpc = rpcClient(child)
    await rpc(1, 'initialize', { protocolVersion: '2024-11-05' })
    const tabs = await rpc(2, 'tools/call', {
      name: 'ekko_studio_browser_toolset',
      arguments: { action: 'call', tool: 'ekko_studio_browser_tabs', arguments: { action: 'list' } },
    })
    expect(tabs.result.isError).toBeFalsy()
    expect(profiles).toEqual(['work', 'work'])
  })
})
