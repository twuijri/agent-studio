import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'fs'
import { dirname, join } from 'path'
import { config } from '../../public/config'

// MCP apps shared by the desktop app when it runs Core Hub locally (no linked
// server). They run on this same machine, so the agents get the app's own
// command instead of the device bridge. Written only by the desktop shell
// through the loopback-only /api/desktop/local-apps route.

export interface LocalSharedApp {
  id: string
  name: string
  source: string
  command: string
  args: string[]
  env: Record<string, string>
  cwd?: string
}

export interface LocalAppsRecord {
  computerName: string
  apps: LocalSharedApp[]
  updatedAt: number
}

const FILE_NAME = 'local-apps.json'
let cache: { path: string; record: LocalAppsRecord | null } | null = null

function storePath(): string {
  return join(config.appHome, FILE_NAME)
}

function stringRecord(value: unknown): Record<string, string> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return {}
  return Object.fromEntries(Object.entries(value as Record<string, unknown>).filter((entry): entry is [string, string] => typeof entry[1] === 'string'))
}

export function normalizeLocalApps(raw: unknown): LocalSharedApp[] {
  if (!Array.isArray(raw)) return []
  const apps: LocalSharedApp[] = []
  const seen = new Set<string>()
  for (const item of raw) {
    if (!item || typeof item !== 'object') continue
    const record = item as Record<string, unknown>
    const id = typeof record.id === 'string' ? record.id.trim() : ''
    const name = typeof record.name === 'string' ? record.name.trim() : ''
    const command = typeof record.command === 'string' ? record.command.trim() : ''
    if (!id || !name || !command || seen.has(id)) continue
    if (record.enabled === false) continue
    seen.add(id)
    const cwd = typeof record.cwd === 'string' && record.cwd.trim() ? record.cwd.trim() : undefined
    apps.push({
      id,
      name,
      source: typeof record.source === 'string' ? record.source : '',
      command,
      args: Array.isArray(record.args) ? record.args.filter((arg): arg is string => typeof arg === 'string') : [],
      env: stringRecord(record.env),
      ...(cwd ? { cwd } : {}),
    })
  }
  return apps
}

function load(): LocalAppsRecord | null {
  const path = storePath()
  if (cache && cache.path === path) return cache.record
  let record: LocalAppsRecord | null = null
  if (existsSync(path)) {
    try {
      const raw = JSON.parse(readFileSync(path, 'utf8')) as Record<string, unknown> | null
      if (raw && typeof raw === 'object') {
        const apps = normalizeLocalApps(raw.apps)
        record = apps.length > 0
          ? { computerName: typeof raw.computerName === 'string' ? raw.computerName : '', apps, updatedAt: typeof raw.updatedAt === 'number' ? raw.updatedAt : 0 }
          : null
      }
    } catch {
      record = null
    }
  }
  cache = { path, record }
  return record
}

function persist(record: LocalAppsRecord | null): void {
  const path = storePath()
  mkdirSync(dirname(path), { recursive: true })
  const tmp = `${path}.tmp`
  writeFileSync(tmp, `${JSON.stringify(record ?? { computerName: '', apps: [], updatedAt: Date.now() }, null, 2)}\n`, 'utf8')
  renameSync(tmp, path)
  cache = { path, record }
}

export function getLocalApps(): LocalAppsRecord | null {
  return load()
}

/** Replaces the locally shared apps. Returns true when something changed. */
export function setLocalApps(computerName: string, apps: unknown): boolean {
  const normalized = normalizeLocalApps(apps)
  const previous = load()
  const next: LocalAppsRecord | null = normalized.length > 0 ? { computerName, apps: normalized, updatedAt: Date.now() } : null
  const same = (previous === null && next === null)
    || (previous !== null && next !== null && previous.computerName === computerName && JSON.stringify(previous.apps) === JSON.stringify(next.apps))
  if (same) return false
  persist(next)
  return true
}

export function resetLocalAppsCache(): void {
  cache = null
}
