import { existsSync } from 'fs'
import { join, resolve } from 'path'
import { config } from '../../public/config'
import { logger } from '../../public/logging'
import { updateConfigYamlForProfile } from '../../public/profile-config'
import { getLanPeerSocketManager } from '../network/lan-peer-socket'
import { isDeviceAllowedForProfile } from './device-bindings'
import { deleteDeviceApps, listDeviceApps, setDeviceApps } from './device-apps-store'

// Turns the MCP apps shared by linked devices into managed MCP servers in the
// agents' profile configs (phase 5 of docs/DESKTOP-SERVER-MODE.md). Each entry
// runs bin/core-hub-device-mcp.mjs, a stdio bridge to the app on the device,
// so any agent that speaks stdio MCP (Hermes, Ekko, …) can use the app without
// knowing about devices. Entries are marked so they can be replaced or
// removed without touching servers the user configured by hand.

export const DEVICE_MCP_ENV_KEY = 'CORE_HUB_DEVICE_APP'
const MANAGED_ENV_KEY = 'HERMES_WEB_UI_MANAGED_MCP'

export interface DeviceMcpServerDefinition {
  name: string
  deviceId: string
  appId: string
  label: string
}

export interface DeviceMcpInjectionTarget {
  profile: string
  status: 'updated' | 'unchanged' | 'skipped'
  servers: string[]
  reason?: string
}

function slug(value: string, max = 40): string {
  const cleaned = value.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '')
  return (cleaned || 'app').slice(0, max)
}

function shortId(value: string): string {
  return value.replace(/[^a-zA-Z0-9]/g, '').slice(-6).toLowerCase() || 'device'
}

function bridgeScriptPath(): string | null {
  const candidates = [
    process.env.CORE_HUB_DEVICE_MCP_SCRIPT?.trim(),
    resolve(process.cwd(), 'bin/core-hub-device-mcp.mjs'),
    // bundled server: dist/server/index.js → <root>/bin
    resolve(__dirname, '../../bin/core-hub-device-mcp.mjs'),
    // source tree: packages/server/src/modules/studio/services/devices → <root>/bin
    resolve(__dirname, '../../../../../../../bin/core-hub-device-mcp.mjs'),
  ].filter((value): value is string => !!value)
  return candidates.find(candidate => existsSync(candidate)) || null
}

function runtimeNodePath(): string {
  return process.env.HERMES_AGENT_NODE?.trim() || process.execPath
}

/** Desired device MCP servers for one profile, from the persisted store and profile bindings. */
export function desiredDeviceMcpServers(profile: string): DeviceMcpServerDefinition[] {
  const definitions: DeviceMcpServerDefinition[] = []
  for (const [deviceId, record] of Object.entries(listDeviceApps())) {
    if (!isDeviceAllowedForProfile(deviceId, profile)) continue
    const deviceSlug = slug(record.computerName || 'device', 24)
    for (const app of record.apps) {
      definitions.push({
        name: `device-${deviceSlug}-${shortId(deviceId)}-${slug(app.name)}`,
        deviceId,
        appId: app.id,
        label: `${record.computerName || deviceId} › ${app.name}`,
      })
    }
  }
  return definitions
}

export function deviceMcpServerConfig(profile: string, definition: DeviceMcpServerDefinition, script: string): Record<string, unknown> {
  return {
    command: runtimeNodePath(),
    args: [script, definition.deviceId, definition.appId],
    env: {
      ELECTRON_RUN_AS_NODE: '1',
      HERMES_WEB_UI_URL: `http://127.0.0.1:${config.port}`,
      HERMES_WEB_UI_HOME: config.appHome,
      HERMES_WEBUI_STATE_DIR: config.appHome,
      HERMES_WEB_UI_PROFILE: profile,
      CORE_HUB_DEVICE_APP_LABEL: definition.label,
      [DEVICE_MCP_ENV_KEY]: '1',
      [MANAGED_ENV_KEY]: '1',
    },
    enabled: true,
  }
}

function isRecord(value: unknown): value is Record<string, any> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

export function isDeviceMcpServer(server: unknown): boolean {
  return isRecord(server) && isRecord(server.env) && server.env[DEVICE_MCP_ENV_KEY] === '1'
}

function sameConfig(existing: Record<string, any>, desired: Record<string, unknown>): boolean {
  const desiredArgs = desired.args as string[]
  const desiredEnv = desired.env as Record<string, string>
  return existing.command === desired.command
    && Array.isArray(existing.args) && existing.args.length === desiredArgs.length && existing.args.every((arg: unknown, index: number) => arg === desiredArgs[index])
    && isRecord(existing.env)
    && Object.entries(desiredEnv).every(([key, value]) => existing.env[key] === value)
    && existing.enabled !== false
}

/** Sync device MCP servers into one Hermes profile's config.yaml. */
export async function injectDeviceMcpServersIntoProfile(profile: string, script: string | null = bridgeScriptPath()): Promise<DeviceMcpInjectionTarget> {
  const desired = script ? desiredDeviceMcpServers(profile) : []
  const result = await updateConfigYamlForProfile<DeviceMcpInjectionTarget>(profile, current => {
    const cfg = isRecord(current) ? current : {}
    if (!isRecord(cfg.mcp_servers)) cfg.mcp_servers = {}
    let changed = false
    const desiredNames = new Set(desired.map(item => item.name))

    for (const [name, server] of Object.entries(cfg.mcp_servers)) {
      if (isDeviceMcpServer(server) && !desiredNames.has(name)) {
        delete cfg.mcp_servers[name]
        changed = true
      }
    }
    for (const definition of desired) {
      const existing = cfg.mcp_servers[definition.name]
      if (existing && !isDeviceMcpServer(existing)) {
        return { data: cfg, write: false, result: { profile, status: 'skipped', servers: [], reason: `existing ${definition.name} MCP server is not managed by Core Hub` } }
      }
      const config = deviceMcpServerConfig(profile, definition, script as string)
      if (isRecord(existing) && existing.enabled === false) continue // the user switched it off; respect that
      if (!existing || !sameConfig(existing, config)) {
        cfg.mcp_servers[definition.name] = config
        changed = true
      }
    }
    const servers = desired.map(item => item.name)
    if (!changed) return { data: cfg, write: false, result: { profile, status: 'unchanged', servers } }
    return { data: cfg, result: { profile, status: 'updated', servers } }
  })
  return result || { profile, status: 'unchanged', servers: [] }
}

export async function injectDeviceMcpServers(): Promise<DeviceMcpInjectionTarget[]> {
  const script = bridgeScriptPath()
  if (!script) {
    logger.warn('[device-apps] bridge script bin/core-hub-device-mcp.mjs not found; device apps are not injected')
  }
  const targets: DeviceMcpInjectionTarget[] = []
  for (const profile of listProfiles()) {
    try {
      targets.push(await injectDeviceMcpServersIntoProfile(profile, script))
    } catch (error) {
      logger.warn({ err: error, profile }, '[device-apps] failed to sync device MCP servers')
    }
  }
  const changed = targets.filter(target => target.status === 'updated')
  if (changed.length > 0) logger.info({ targets: changed }, '[device-apps] synced device MCP servers into profiles')
  for (const hook of extraSyncHooks) {
    try { await hook() } catch (error) { logger.warn({ err: error }, '[device-apps] agent sync hook failed') }
  }
  return targets
}

let syncTimer: NodeJS.Timeout | null = null
let subscribed = false
const extraSyncHooks: Array<() => void | Promise<void>> = []
// Profile discovery belongs to the Hermes module; bootstrap wires it in so this
// studio service does not depend on hermes internals.
let listProfiles: () => string[] = () => []

export function configureDeviceMcpSync(options: { listProfiles: () => string[] }): void {
  listProfiles = options.listProfiles
}

/** Other agent families (Ekko, …) register how they persist device MCP servers. */
export function registerDeviceMcpSyncHook(hook: () => void | Promise<void>): void {
  extraSyncHooks.push(hook)
}

/** Debounced full sync (device announcements can arrive in bursts). */
export function scheduleDeviceMcpSync(delayMs = 500): void {
  if (syncTimer) clearTimeout(syncTimer)
  syncTimer = setTimeout(() => {
    syncTimer = null
    void injectDeviceMcpServers()
  }, delayMs)
  syncTimer.unref?.()
}

/** Record device announcements and keep profiles in sync. Idempotent. */
export function startDeviceMcpSync(): void {
  if (subscribed) return
  subscribed = true
  getLanPeerSocketManager().subscribeDeviceApps(connection => {
    const info = connection.info()
    if (setDeviceApps(info.device_id, info.computer_name, info.apps || [])) scheduleDeviceMcpSync()
  })
  scheduleDeviceMcpSync(0)
}

/** Forget a device's apps (device record deleted) and drop its MCP entries. */
export function forgetDeviceApps(deviceId: string): void {
  if (deleteDeviceApps(deviceId)) scheduleDeviceMcpSync(0)
}

export function resolveDeviceMcpBridgeScript(): string | null {
  return bridgeScriptPath()
}

export function deviceAppsDirectory(): string {
  return join(config.appHome, 'device-apps')
}
