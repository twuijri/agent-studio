import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'fs'
import { dirname, join } from 'path'
import { config } from '../../public/config'

// Device ↔ profile bindings (phase 4 of docs/DESKTOP-SERVER-MODE.md).
//
// A controllable device (desktop Device Agent) may be restricted to a set of
// Hermes profiles. No entry, or an empty list, means every profile may use
// the device. Bindings are Studio-side state (never written into Hermes
// config) and live in a small JSON file under the Web UI home.

export type DeviceBindings = Record<string, string[]>

const FILE_NAME = 'device-profile-bindings.json'
let cache: { path: string; bindings: DeviceBindings } | null = null

function bindingsPath(): string {
  return join(config.appHome, FILE_NAME)
}

function normalizeProfiles(input: unknown): string[] {
  if (!Array.isArray(input)) return []
  const profiles = input
    .filter((value): value is string => typeof value === 'string')
    .map(value => value.trim())
    .filter(Boolean)
  return [...new Set(profiles)].sort()
}

function normalizeBindings(raw: unknown): DeviceBindings {
  if (!raw || typeof raw !== 'object') return {}
  const bindings: DeviceBindings = {}
  for (const [deviceId, profiles] of Object.entries(raw as Record<string, unknown>)) {
    const key = deviceId.trim()
    if (!key) continue
    const normalized = normalizeProfiles(profiles)
    if (normalized.length > 0) bindings[key] = normalized
  }
  return bindings
}

function load(): DeviceBindings {
  const path = bindingsPath()
  if (cache && cache.path === path) return cache.bindings
  let bindings: DeviceBindings = {}
  if (existsSync(path)) {
    try {
      bindings = normalizeBindings(JSON.parse(readFileSync(path, 'utf8')))
    } catch {
      bindings = {}
    }
  }
  cache = { path, bindings }
  return bindings
}

function save(bindings: DeviceBindings): void {
  const path = bindingsPath()
  mkdirSync(dirname(path), { recursive: true })
  const tmp = `${path}.${process.pid}.tmp`
  writeFileSync(tmp, `${JSON.stringify(bindings, null, 2)}\n`, { encoding: 'utf8', mode: 0o600 })
  renameSync(tmp, path)
  cache = { path, bindings }
}

export function listDeviceBindings(): DeviceBindings {
  const bindings = load()
  return Object.fromEntries(Object.entries(bindings).map(([id, profiles]) => [id, [...profiles]]))
}

export function getDeviceBinding(deviceId: string): string[] {
  return [...(load()[deviceId] || [])]
}

/** Replace the profile list for a device; an empty list removes the restriction. */
export function setDeviceBinding(deviceId: string, profiles: unknown): string[] {
  const key = deviceId.trim()
  if (!key) throw Object.assign(new Error('Device id is required'), { status: 400 })
  const next = { ...load() }
  const normalized = normalizeProfiles(profiles)
  if (normalized.length > 0) next[key] = normalized
  else delete next[key]
  save(next)
  return [...normalized]
}

export function deleteDeviceBinding(deviceId: string): void {
  const next = { ...load() }
  if (!(deviceId in next)) return
  delete next[deviceId]
  save(next)
}

/** Whether a profile may use the device. An empty/missing profile means "no profile context": allowed. */
export function isDeviceAllowedForProfile(deviceId: string, profile: string | null | undefined): boolean {
  const name = (profile || '').trim()
  if (!name) return true
  const bound = load()[deviceId]
  if (!bound || bound.length === 0) return true
  return bound.includes(name)
}

/** Test hook: forget the in-memory cache so a different app home is re-read. */
export function resetDeviceBindingsCache(): void {
  cache = null
}
