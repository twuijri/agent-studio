import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'fs'
import { dirname, join } from 'path'
import { config } from '../../public/config'

// Shared MCP apps announced by linked devices (phase 5 of
// docs/DESKTOP-SERVER-MODE.md). Persisted so the managed MCP entries injected
// into agent profiles stay stable while a device is offline; the device's live
// connection remains the source of truth for what can actually be opened.

export interface DeviceSharedApp {
  id: string
  name: string
  source: string
}

export interface DeviceAppsRecord {
  computerName: string
  apps: DeviceSharedApp[]
  updatedAt: number
}

export type DeviceAppsStore = Record<string, DeviceAppsRecord>

const FILE_NAME = 'device-apps.json'
let cache: { path: string; store: DeviceAppsStore } | null = null

function storePath(): string {
  return join(config.appHome, FILE_NAME)
}

function normalizeApps(raw: unknown): DeviceSharedApp[] {
  if (!Array.isArray(raw)) return []
  const apps: DeviceSharedApp[] = []
  const seen = new Set<string>()
  for (const item of raw) {
    if (!item || typeof item !== 'object') continue
    const record = item as Record<string, unknown>
    const id = typeof record.id === 'string' ? record.id.trim() : ''
    const name = typeof record.name === 'string' ? record.name.trim() : ''
    if (!id || !name || seen.has(id)) continue
    seen.add(id)
    apps.push({ id, name, source: typeof record.source === 'string' ? record.source : '' })
  }
  return apps
}

function load(): DeviceAppsStore {
  const path = storePath()
  if (cache && cache.path === path) return cache.store
  let store: DeviceAppsStore = {}
  if (existsSync(path)) {
    try {
      const raw = JSON.parse(readFileSync(path, 'utf8'))
      if (raw && typeof raw === 'object') {
        for (const [deviceId, value] of Object.entries(raw as Record<string, unknown>)) {
          const record = value as Record<string, unknown> | null
          if (!record || typeof record !== 'object') continue
          store[deviceId] = {
            computerName: typeof record.computerName === 'string' ? record.computerName : '',
            apps: normalizeApps(record.apps),
            updatedAt: typeof record.updatedAt === 'number' ? record.updatedAt : 0,
          }
        }
      }
    } catch {
      store = {}
    }
  }
  cache = { path, store }
  return store
}

function save(store: DeviceAppsStore): void {
  const path = storePath()
  mkdirSync(dirname(path), { recursive: true })
  const tmp = `${path}.${process.pid}.tmp`
  writeFileSync(tmp, `${JSON.stringify(store, null, 2)}\n`, { encoding: 'utf8', mode: 0o600 })
  renameSync(tmp, path)
  cache = { path, store }
}

export function listDeviceApps(): DeviceAppsStore {
  return structuredClone(load())
}

export function getDeviceApps(deviceId: string): DeviceAppsRecord | null {
  const record = load()[deviceId]
  return record ? structuredClone(record) : null
}

/** Record the apps a device currently shares; returns true when the list changed. */
export function setDeviceApps(deviceId: string, computerName: string, apps: unknown): boolean {
  const key = deviceId.trim()
  if (!key) return false
  const normalized = normalizeApps(apps)
  const store = { ...load() }
  const previous = store[key]
  const unchanged = previous
    && previous.computerName === computerName
    && JSON.stringify(previous.apps) === JSON.stringify(normalized)
  if (unchanged) return false
  if (normalized.length === 0) delete store[key]
  else store[key] = { computerName, apps: normalized, updatedAt: Date.now() }
  save(store)
  return true
}

export function deleteDeviceApps(deviceId: string): boolean {
  const store = { ...load() }
  if (!(deviceId in store)) return false
  delete store[deviceId]
  save(store)
  return true
}

/** Test hook. */
export function resetDeviceAppsCache(): void {
  cache = null
}
