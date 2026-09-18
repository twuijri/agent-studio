import { mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs'
import { dirname } from 'node:path'

// Desktop connection mode.
//
// `local`  — the historical behaviour: the app boots the bundled Web UI server
//            with Hermes, models and data on this machine.
// `server` — the app is a thin client for a Studio server the user owns: the
//            window loads the server's Web UI, and nothing local is started
//            (the Device Agent that lets the server operate this machine is a
//            later phase; see docs/DESKTOP-SERVER-MODE.md).
//
// Existing installs default to `local` and are never prompted. The mode is a
// small JSON file under the Electron userData directory; automation can pin it
// with HERMES_DESKTOP_MODE / HERMES_DESKTOP_SERVER_URL.

export type DesktopMode = 'local' | 'server'

export interface DesktopModeConfig {
  mode: DesktopMode
  serverUrl: string | null
}

export type DesktopModeSource = 'default' | 'file' | 'env'

export interface ResolvedDesktopMode extends DesktopModeConfig {
  source: DesktopModeSource
}

export interface StudioServerProbeResult {
  ok: boolean
  url: string
  status?: number
  error?: string
}

export const DESKTOP_MODE_FILE_NAME = 'desktop-mode.json'
export const DESKTOP_MODE_ENV = 'HERMES_DESKTOP_MODE'
export const DESKTOP_SERVER_URL_ENV = 'HERMES_DESKTOP_SERVER_URL'

export const LOCAL_DESKTOP_MODE: ResolvedDesktopMode = Object.freeze({
  mode: 'local',
  serverUrl: null,
  source: 'default',
}) as ResolvedDesktopMode

export function isDesktopMode(value: unknown): value is DesktopMode {
  return value === 'local' || value === 'server'
}

/**
 * Normalize a user-entered Studio server address.
 *
 * Accepts `host`, `host:port`, or a full http(s) URL, optionally with a path
 * prefix. Drops query/hash and trailing slashes so hash routes can be appended.
 * Returns null for anything that is not a plain http(s) origin (+ path).
 */
export function normalizeStudioServerUrl(input: unknown): string | null {
  if (typeof input !== 'string') return null
  let raw = input.trim()
  if (!raw) return null
  if (!/^[a-z][a-z0-9+.-]*:\/\//i.test(raw)) raw = `https://${raw}`
  let parsed: URL
  try {
    parsed = new URL(raw)
  } catch {
    return null
  }
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') return null
  if (!parsed.hostname) return null
  if (parsed.username || parsed.password) return null
  const path = parsed.pathname.replace(/\/+$/, '')
  return `${parsed.origin}${path}`
}

export function parseDesktopModeConfig(raw: unknown): DesktopModeConfig {
  if (!raw || typeof raw !== 'object') return { mode: 'local', serverUrl: null }
  const record = raw as Record<string, unknown>
  const serverUrl = normalizeStudioServerUrl(record.serverUrl)
  if (record.mode === 'server' && serverUrl) return { mode: 'server', serverUrl }
  // A saved server URL is kept so the chooser can pre-fill it, but the app
  // only runs in server mode when both the mode and a valid URL are present.
  return { mode: 'local', serverUrl }
}

export function readDesktopModeConfig(filePath: string): DesktopModeConfig {
  let text: string
  try {
    text = readFileSync(filePath, 'utf8')
  } catch {
    return { mode: 'local', serverUrl: null }
  }
  try {
    return parseDesktopModeConfig(JSON.parse(text))
  } catch {
    return { mode: 'local', serverUrl: null }
  }
}

export function writeDesktopModeConfig(filePath: string, config: DesktopModeConfig): DesktopModeConfig {
  const normalized = parseDesktopModeConfig(config)
  if (config.mode === 'server' && normalized.mode !== 'server') {
    throw new Error('A valid http(s) server address is required for server mode')
  }
  mkdirSync(dirname(filePath), { recursive: true })
  const tmp = `${filePath}.${process.pid}.tmp`
  writeFileSync(tmp, `${JSON.stringify({ version: 1, ...normalized }, null, 2)}\n`, 'utf8')
  renameSync(tmp, filePath)
  return normalized
}

/**
 * Resolve the effective mode: environment overrides win (for automation and
 * tests), then the saved file, then the historical local default.
 */
export function resolveDesktopMode(
  env: NodeJS.ProcessEnv,
  filePath: string | null,
): ResolvedDesktopMode {
  const envMode = env[DESKTOP_MODE_ENV]?.trim().toLowerCase()
  const envUrl = normalizeStudioServerUrl(env[DESKTOP_SERVER_URL_ENV])
  if (envMode === 'server') {
    if (envUrl) return { mode: 'server', serverUrl: envUrl, source: 'env' }
    console.warn(`[desktop-mode] ${DESKTOP_MODE_ENV}=server ignored: ${DESKTOP_SERVER_URL_ENV} is missing or invalid`)
  } else if (envMode === 'local') {
    return { ...LOCAL_DESKTOP_MODE, source: 'env' }
  }
  if (!filePath) return LOCAL_DESKTOP_MODE
  const saved = readDesktopModeConfig(filePath)
  if (saved.mode === 'server') return { ...saved, source: 'file' }
  return { mode: 'local', serverUrl: saved.serverUrl, source: saved.serverUrl ? 'file' : 'default' }
}

export function isDesktopModeLockedByEnv(env: NodeJS.ProcessEnv): boolean {
  const envMode = env[DESKTOP_MODE_ENV]?.trim().toLowerCase()
  return envMode === 'local' || (envMode === 'server' && !!normalizeStudioServerUrl(env[DESKTOP_SERVER_URL_ENV]))
}

type FetchLike = (input: string, init?: { signal?: AbortSignal; redirect?: 'follow' | 'manual' | 'error' }) => Promise<{ ok: boolean; status: number }>

/**
 * Check that a Studio server answers at the given address. Uses the
 * unauthenticated readiness endpoint the desktop already relies on locally,
 * so any Studio version behind any reverse proxy qualifies.
 */
export async function probeStudioServer(
  input: unknown,
  options: { fetchImpl?: FetchLike; timeoutMs?: number } = {},
): Promise<StudioServerProbeResult> {
  const url = normalizeStudioServerUrl(input)
  if (!url) return { ok: false, url: typeof input === 'string' ? input.trim() : '', error: 'invalid-url' }
  const fetchImpl = options.fetchImpl ?? (globalThis.fetch as unknown as FetchLike)
  const timeoutMs = options.timeoutMs ?? 8000
  let lastStatus: number | undefined
  for (const path of ['/health/ready', '/health']) {
    try {
      const res = await fetchImpl(`${url}${path}`, { signal: AbortSignal.timeout(timeoutMs), redirect: 'follow' })
      lastStatus = res.status
      if (res.ok) return { ok: true, url, status: res.status }
    } catch (error) {
      return { ok: false, url, error: error instanceof Error ? error.message : String(error) }
    }
  }
  return { ok: false, url, status: lastStatus, error: lastStatus ? `http-${lastStatus}` : 'unreachable' }
}
