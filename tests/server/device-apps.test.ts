import { spawn } from 'node:child_process'
import { createServer, type Server } from 'node:http'
import { mkdtempSync, readFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { DeviceAgent } from '../../packages/desktop/src/main/device-agent/agent'
import { defaultDeviceAgentConfig, type DeviceAgentConfig } from '../../packages/desktop/src/main/device-agent/store'

// Phase 5: apps shared by a linked device become MCP servers for the agents.

const wait = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))
const tempDirs: string[] = []
function tempDir(): string {
  const dir = mkdtempSync(join(tmpdir(), 'device-apps-'))
  tempDirs.push(dir)
  return dir
}

afterEach(() => {
  for (const dir of tempDirs.splice(0)) rmSync(dir, { recursive: true, force: true })
  vi.doUnmock('../../packages/server/src/modules/studio/public/config')
  vi.doUnmock('../../packages/server/src/modules/studio/public/profile-config')
  vi.doUnmock('../../packages/server/src/modules/hermes/services/profiles/profile')
  vi.doUnmock('../../packages/server/src/modules/studio/repositories/devices-store')
  vi.doUnmock('../../packages/server/src/modules/studio/public/auth')
  vi.resetModules()
})

describe('device apps store and MCP injection', () => {
  let home = ''
  beforeEach(() => {
    home = tempDir()
    vi.doMock('../../packages/server/src/modules/studio/public/config', () => ({ config: { appHome: home, corsOrigins: '', port: 6060 } }))
  })

  it('persists announced apps per device and drops empty announcements', async () => {
    const store = await import('../../packages/server/src/modules/studio/services/devices/device-apps-store')
    store.resetDeviceAppsCache()
    expect(store.setDeviceApps('dev1', 'MacBook', [{ id: 'a', name: 'DaVinci', source: 'claude-extension' }, { id: 'a', name: 'dup', source: '' }, { name: 'no-id' }])).toBe(true)
    expect(store.setDeviceApps('dev1', 'MacBook', [{ id: 'a', name: 'DaVinci', source: 'claude-extension' }])).toBe(false)
    expect(JSON.parse(readFileSync(join(home, 'device-apps.json'), 'utf8')).dev1.apps).toEqual([{ id: 'a', name: 'DaVinci', source: 'claude-extension' }])
    store.resetDeviceAppsCache()
    expect(store.getDeviceApps('dev1')?.computerName).toBe('MacBook')
    expect(store.setDeviceApps('dev1', 'MacBook', [])).toBe(true)
    expect(store.getDeviceApps('dev1')).toBeNull()
  })

  it('writes one managed bridge entry per allowed device app into Hermes profiles and removes stale ones', async () => {
    const configs: Record<string, any> = {
      default: { mcp_servers: { mine: { command: 'keep-me' }, 'device-old-x-gone': { command: 'node', env: { CORE_HUB_DEVICE_APP: '1' } } } },
      work: { mcp_servers: {} },
    }
    vi.doMock('../../packages/server/src/modules/studio/public/profile-config', () => ({
      updateConfigYamlForProfile: async (profile: string, updater: (cfg: any) => any) => {
        const outcome = updater(structuredClone(configs[profile] || {}))
        if (outcome.write !== false) configs[profile] = outcome.data
        return outcome.result
      },
    }))
    const store = await import('../../packages/server/src/modules/studio/services/devices/device-apps-store')
    const bindings = await import('../../packages/server/src/modules/studio/services/devices/device-bindings')
    const injection = await import('../../packages/server/src/modules/studio/services/devices/device-mcp-injection')
    injection.configureDeviceMcpSync({ listProfiles: () => ['default', 'work'] })
    store.resetDeviceAppsCache()
    bindings.resetDeviceBindingsCache()
    store.setDeviceApps('hwui_abcdef123456', 'Taha MacBook Pro', [{ id: 'claude-extension:xyz', name: 'DaVinci Resolve', source: 'claude-extension' }])
    bindings.setDeviceBinding('hwui_abcdef123456', ['work'])

    const script = '/opt/core-hub/bin/core-hub-device-mcp.mjs'
    const targets = await Promise.all(['default', 'work'].map(profile => injection.injectDeviceMcpServersIntoProfile(profile, script)))
    expect(targets.map(target => target.status)).toEqual(['updated', 'updated'])
    // default is not allowed → nothing injected, stale device entry removed, user entry kept
    expect(Object.keys(configs.default.mcp_servers)).toEqual(['mine'])
    // work gets the bridge
    const [name, server] = Object.entries(configs.work.mcp_servers)[0] as [string, any]
    expect(name).toBe('device-taha-macbook-pro-123456-davinci-resolve')
    expect(server.args).toEqual([script, 'hwui_abcdef123456', 'claude-extension:xyz'])
    expect(server.env).toMatchObject({ HERMES_WEB_UI_PROFILE: 'work', CORE_HUB_DEVICE_APP: '1', HERMES_WEB_UI_MANAGED_MCP: '1', CORE_HUB_DEVICE_APP_LABEL: 'Taha MacBook Pro › DaVinci Resolve' })

    // unchanged on the second run; disabled-by-user entries are respected
    expect((await injection.injectDeviceMcpServersIntoProfile('work', script)).status).toBe('unchanged')
    configs.work.mcp_servers[name].enabled = false
    expect((await injection.injectDeviceMcpServersIntoProfile('work', script)).status).toBe('unchanged')
    expect(configs.work.mcp_servers[name].enabled).toBe(false)

    // device forgotten → entry removed
    injection.forgetDeviceApps('hwui_abcdef123456')
    store.resetDeviceAppsCache()
    expect((await injection.injectDeviceMcpServersIntoProfile('work', script)).status).toBe('updated')
    expect(configs.work.mcp_servers).toEqual({})
  })
})

describe('device app sessions end to end', () => {
  let server: Server | null = null
  let agent: DeviceAgent | null = null
  let manager: any = null
  let sessions: any = null

  afterEach(async () => {
    agent?.stop()
    agent = null
    await sessions?.close().catch(() => undefined)
    sessions = null
    await manager?.shutdown().catch(() => undefined)
    manager = null
    await new Promise<void>(resolve => (server ? server.close(() => resolve()) : resolve()))
    server = null
  })

  it('pipes an agent stdio MCP client through the bridge script to the app on the device', { timeout: 30000 }, async () => {
    const home = tempDir()
    const deviceDir = tempDir()
    let approvedDeviceId = ''
    vi.doMock('../../packages/server/src/modules/studio/public/config', () => ({ config: { appHome: home, corsOrigins: '', port: 6060 } }))
    vi.doMock('../../packages/server/src/modules/studio/repositories/devices-store', () => ({
      getDeviceRelation: (id: string) => (id === approvedDeviceId ? { inbound_status: 'approved' } : null),
    }))
    vi.doMock('../../packages/server/src/modules/studio/public/auth', () => ({
      isAuthEnabled: async () => true,
      authenticateUserToken: async (token: string) => (token === 'run-token' ? { id: 1, role: 'super_admin' } : null),
    }))
    const socketModule = await import('../../packages/server/src/modules/studio/services/network/lan-peer-socket')
    const { DeviceAppSessionServer } = await import('../../packages/server/src/modules/studio/services/devices/device-app-sessions')
    manager = socketModule.getLanPeerSocketManager()
    sessions = new DeviceAppSessionServer()
    server = createServer((_req, res) => { res.statusCode = 404; res.end() })
    manager.setupServer(server)
    sessions.setupServer(server)
    await new Promise<void>(resolve => server!.listen(0, '127.0.0.1', () => resolve()))
    const port = (server.address() as { port: number }).port
    const serverUrl = `http://127.0.0.1:${port}`

    // The "app": a tiny stdio JSON-RPC echo server living on the device.
    const echoApp = {
      id: 'claude-code:echo', name: 'Echo', source: 'claude-code', command: process.execPath,
      args: ['-e', "process.stdin.setEncoding('utf8');let buf='';process.stdin.on('data',d=>{buf+=d;let i;while((i=buf.indexOf('\\n'))>=0){const line=buf.slice(0,i);buf=buf.slice(i+1);if(!line)continue;const m=JSON.parse(line);process.stdout.write(JSON.stringify({jsonrpc:'2.0',id:m.id,result:{echo:m.method}})+'\\n')}})"],
      env: {}, enabled: true,
    }
    let config: DeviceAgentConfig = { ...defaultDeviceAgentConfig(), enabled: true, capabilities: { exec: false, files: false, browser: false, screen: false, apps: true }, pairedServerUrl: serverUrl, pairedAt: 1, sharedApps: [echoApp] }
    const approveApp = vi.fn(async () => true)
    agent = new DeviceAgent({
      identityFile: join(deviceDir, 'device-identity.json'),
      loadConfig: () => config,
      saveConfig: next => { config = next; return next },
      serverUrl: () => serverUrl,
      approveExec: async () => 'deny',
      approveApp,
      audit: () => undefined,
      appVersion: 'test',
      fetchImpl: (async () => ({ ok: true, status: 200, json: async () => ({ status: 'approved' }) })) as unknown as typeof fetch,
      timing: { pollMs: 50, reconnectBaseMs: 50, reconnectMaxMs: 100 },
    })
    await agent.start()
    approvedDeviceId = agent.getState().deviceId
    const deadline = Date.now() + 5000
    while ((manager.listConnections()[0]?.apps?.length ?? 0) === 0 && Date.now() < deadline) await wait(20)
    const info = manager.listConnections()[0]
    expect(info).toMatchObject({ controllable: true, capabilities: ['apps'], apps: [{ id: 'claude-code:echo', name: 'Echo', source: 'claude-code' }] })

    // Run the bridge exactly as an agent would: a stdio child with the managed env.
    const bridge = spawn(process.execPath, [join(process.cwd(), 'bin/core-hub-device-mcp.mjs'), approvedDeviceId, 'claude-code:echo'], {
      env: { ...process.env, HERMES_WEB_UI_URL: serverUrl, HERMES_WEB_UI_HOME: home, HERMES_WEB_UI_PROFILE: 'default', HERMES_WEB_UI_TOKEN: 'run-token' },
      stdio: ['pipe', 'pipe', 'pipe'],
    })
    let stdout = ''
    let stderr = ''
    bridge.stdout.on('data', chunk => { stdout += chunk })
    bridge.stderr.on('data', chunk => { stderr += chunk })
    bridge.stdin.write(JSON.stringify({ jsonrpc: '2.0', id: 1, method: 'initialize', params: {} }) + '\n')
    const until = Date.now() + 8000
    while (!stdout.includes('"id":1') && Date.now() < until) await wait(25)
    expect(stdout, stderr).toContain('{"jsonrpc":"2.0","id":1,"result":{"echo":"initialize"}}')
    expect(approveApp).toHaveBeenCalledTimes(1)

    bridge.stdin.write(JSON.stringify({ jsonrpc: '2.0', id: 2, method: 'tools/list' }) + '\n')
    const until2 = Date.now() + 8000
    while (!stdout.includes('"id":2') && Date.now() < until2) await wait(25)
    expect(stdout).toContain('"result":{"echo":"tools/list"}')

    bridge.stdin.end()
    const exit = await new Promise<number | null>(resolve => bridge.on('close', resolve))
    expect(exit).toBe(0)

    // A device that is not allowed to the profile is refused at the websocket.
    const bindings = await import('../../packages/server/src/modules/studio/services/devices/device-bindings')
    bindings.resetDeviceBindingsCache()
    bindings.setDeviceBinding(approvedDeviceId, ['other'])
    const refused = spawn(process.execPath, [join(process.cwd(), 'bin/core-hub-device-mcp.mjs'), approvedDeviceId, 'claude-code:echo'], {
      env: { ...process.env, HERMES_WEB_UI_URL: serverUrl, HERMES_WEB_UI_HOME: home, HERMES_WEB_UI_PROFILE: 'default', HERMES_WEB_UI_TOKEN: 'run-token' },
      stdio: ['pipe', 'pipe', 'pipe'],
    })
    const refusedExit = await new Promise<number | null>(resolve => refused.on('close', resolve))
    expect(refusedExit).not.toBe(0)
  })
})

describe('Ekko config sync for device apps', () => {
  it('adds, replaces, and removes device bridge servers without touching user-defined ones', async () => {
    const { injectDeviceMcpServersIntoEkko } = await import('../../packages/server/src/modules/ekko/services/mcp')
    let stored: any = { mcp: { enabled: true, profiles: { default: { servers: { mine: { command: 'x', enabled: true }, 'device-old': { command: 'n', env: { CORE_HUB_DEVICE_APP: '1' }, enabled: true } } } } } }
    const setup: any = {
      config: { read: () => structuredClone(stored), replace: (next: any) => { stored = next } },
      profiles: () => [{ profile: 'work' }],
    }
    const bridge = { command: 'node', args: ['bridge.mjs', 'dev', 'app'], env: { CORE_HUB_DEVICE_APP: '1' }, enabled: true }
    const result = injectDeviceMcpServersIntoEkko(profile => (profile === 'work' ? [{ name: 'device-mac-app', config: bridge }] : []), setup)
    expect(result.changed).toBe(true)
    expect(Object.keys(stored.mcp.profiles.default.servers)).toEqual(['mine'])
    expect(stored.mcp.profiles.work.servers['device-mac-app']).toEqual(bridge)
    expect(injectDeviceMcpServersIntoEkko(profile => (profile === 'work' ? [{ name: 'device-mac-app', config: bridge }] : []), setup).changed).toBe(false)
  })
})
