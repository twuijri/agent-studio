import type { SharedMcpApp } from './device-agent/store'

// Local desktop mode: hands the apps the user shared on the App connections
// page to the Core Hub server running on this machine, which turns them into
// managed MCP servers for every profile. Loopback only, authenticated with the
// same desktop token the shell uses for graceful shutdown.

export const LOCAL_APPS_PATH = '/api/desktop/local-apps'

export interface LocalAppSyncTarget {
  port: number
  token: string
  computerName: string
}

export interface LocalAppSyncResult {
  ok: boolean
  changed: boolean
  apps: string[]
  status: number
}

export function localAppSyncPayload(computerName: string, apps: SharedMcpApp[]): { computerName: string; apps: Array<Record<string, unknown>> } {
  return {
    computerName,
    apps: apps
      .filter(app => app.enabled !== false && !!app.command)
      .map(app => ({
        id: app.id,
        name: app.name,
        source: app.source,
        command: app.command,
        args: [...(app.args || [])],
        env: { ...(app.env || {}) },
        ...(app.cwd ? { cwd: app.cwd } : {}),
      })),
  }
}

export async function syncLocalSharedApps(
  target: LocalAppSyncTarget,
  apps: SharedMcpApp[],
  fetchImpl: typeof fetch = fetch,
): Promise<LocalAppSyncResult> {
  const response = await fetchImpl(`http://127.0.0.1:${target.port}${LOCAL_APPS_PATH}`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${target.token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify(localAppSyncPayload(target.computerName, apps)),
    signal: AbortSignal.timeout(10_000),
  })
  if (!response.ok) throw new Error(`local app sync returned HTTP ${response.status}`)
  const body = await response.json().catch(() => ({})) as Partial<LocalAppSyncResult>
  return { ok: body.ok === true, changed: body.changed === true, apps: Array.isArray(body.apps) ? body.apps : [], status: response.status }
}
