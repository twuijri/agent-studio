import { mkdtempSync, readFileSync, rmSync, statSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const tempDirs: string[] = []
function tempDir(): string {
  const dir = mkdtempSync(join(tmpdir(), 'device-gw-'))
  tempDirs.push(dir)
  return dir
}

afterEach(() => {
  for (const dir of tempDirs.splice(0)) rmSync(dir, { recursive: true, force: true })
  vi.doUnmock('../../packages/server/src/modules/studio/public/config')
  vi.doUnmock('../../packages/server/src/modules/studio/services/network/lan-peer-socket')
  vi.resetModules()
})

describe('device ↔ profile bindings', () => {
  let home = ''
  beforeEach(() => {
    home = tempDir()
    vi.doMock('../../packages/server/src/modules/studio/public/config', () => ({ config: { appHome: home, corsOrigins: '' } }))
  })

  it('defaults to "every profile" and persists normalized profile lists', async () => {
    const bindings = await import('../../packages/server/src/modules/studio/services/devices/device-bindings')
    bindings.resetDeviceBindingsCache()
    expect(bindings.isDeviceAllowedForProfile('dev-1', 'work')).toBe(true)
    expect(bindings.isDeviceAllowedForProfile('dev-1', '')).toBe(true)

    expect(bindings.setDeviceBinding('dev-1', [' work ', 'home', 'work', 42])).toEqual(['home', 'work'])
    expect(bindings.isDeviceAllowedForProfile('dev-1', 'work')).toBe(true)
    expect(bindings.isDeviceAllowedForProfile('dev-1', 'personal')).toBe(false)
    expect(bindings.isDeviceAllowedForProfile('dev-1', '')).toBe(true)
    expect(bindings.isDeviceAllowedForProfile('other', 'personal')).toBe(true)

    const file = join(home, 'device-profile-bindings.json')
    expect(JSON.parse(readFileSync(file, 'utf8'))).toEqual({ 'dev-1': ['home', 'work'] })
    expect(statSync(file).mode & 0o077).toBe(0)

    bindings.resetDeviceBindingsCache()
    expect(bindings.listDeviceBindings()).toEqual({ 'dev-1': ['home', 'work'] })
    expect(bindings.setDeviceBinding('dev-1', [])).toEqual([])
    expect(bindings.listDeviceBindings()).toEqual({})
    expect(() => bindings.setDeviceBinding(' ', ['x'])).toThrow(/Device id/)
  })
})

describe('device browser gateway', () => {
  let home = ''
  let listeners: Array<() => void> = []
  let connections: any[] = []
  const proxied: Array<{ id: string; path: string; headers: Record<string, string>; body: string }> = []

  function fakeConnection(id: string, deviceId: string, capabilities: string[]) {
    return {
      id,
      deviceId,
      info: () => ({ id, role: 'server', device_id: deviceId, computer_name: deviceId, url: '', connected_at: 1, controllable: true, capabilities }),
      proxyHttp: async (request: { path: string; headers: Record<string, string>; body: Buffer }) => {
        proxied.push({ id, path: request.path, headers: request.headers, body: request.body.toString('utf8') })
        if (request.path === '/v1/session') {
          return { status: 200, headers: { 'content-type': 'application/json' }, body: Buffer.from(JSON.stringify({ client_id: `client-${id}`, session_token: `session-${id}` })) }
        }
        return { status: 200, headers: { 'content-type': 'application/json' }, body: Buffer.from(JSON.stringify({ result: { echoed: JSON.parse(request.body.toString('utf8')).method, via: id } })) }
      },
    }
  }

  beforeEach(() => {
    home = tempDir()
    listeners = []
    connections = []
    proxied.length = 0
    vi.doMock('../../packages/server/src/modules/studio/public/config', () => ({ config: { appHome: home, corsOrigins: '' } }))
    vi.doMock('../../packages/server/src/modules/studio/services/network/lan-peer-socket', () => ({
      getLanPeerSocketManager: () => ({
        listConnections: () => connections.map(connection => connection.info()),
        getConnection: (id: string) => connections.find(connection => connection.id === id) || null,
        subscribe: (listener: () => void) => { listeners.push(listener); return () => { listeners = listeners.filter(item => item !== listener) } },
      }),
    }))
  })

  it('publishes a broker descriptor only while a browser-capable device is connected', async () => {
    const { DeviceBrowserGateway } = await import('../../packages/server/src/modules/studio/services/network/device-browser-gateway')
    const gateway = new DeviceBrowserGateway()
    await gateway.start()
    const descriptorPath = join(home, 'desktop-browser', 'broker.json')
    expect(gateway.descriptorInfo().written).toBe(false)
    expect(() => statSync(descriptorPath)).toThrow()

    connections.push(fakeConnection('c1', 'mac', ['exec', 'browser']))
    for (const listener of listeners) listener()
    await gateway.refresh()
    const descriptor = JSON.parse(readFileSync(descriptorPath, 'utf8'))
    expect(descriptor).toMatchObject({ schema: 1, desktopPid: process.pid, token: gateway.descriptorInfo().token, gateway: 'device-browser' })
    expect(descriptor.endpoint).toMatch(/^http:\/\/127\.0\.0\.1:\d+\/v1$/)
    expect(statSync(descriptorPath).mode & 0o077).toBe(0)
    expect(statSync(join(home, 'desktop-browser')).mode & 0o077).toBe(0)

    connections.length = 0
    for (const listener of listeners) listener()
    await gateway.refresh()
    expect(() => statSync(descriptorPath)).toThrow()
    await gateway.stop()
  })

  it('tunnels session and operation calls to the device bound to the requesting profile', async () => {
    const bindings = await import('../../packages/server/src/modules/studio/services/devices/device-bindings')
    bindings.resetDeviceBindingsCache()
    bindings.setDeviceBinding('mac-work', ['work'])
    const { DeviceBrowserGateway } = await import('../../packages/server/src/modules/studio/services/network/device-browser-gateway')
    const gateway = new DeviceBrowserGateway()
    await gateway.start()
    connections.push(fakeConnection('c-home', 'mac-home', ['browser']), fakeConnection('c-work', 'mac-work', ['browser']))
    await gateway.refresh()
    const { endpoint, token } = gateway.descriptorInfo()

    const unauthorized = await fetch(`${endpoint}/session`, { method: 'POST', headers: { authorization: 'Bearer nope' }, body: '{}' })
    expect(unauthorized.status).toBe(401)

    // "work" profile → only the device bound to it (mac-work); "home" device is unbound and open to everyone,
    // so a request without profile takes the first available device.
    const session = await fetch(`${endpoint}/session`, {
      method: 'POST',
      headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json', 'x-hermes-profile': 'work' },
      body: JSON.stringify({ client: 'mcp', client_pid: 1 }),
    })
    expect(session.status).toBe(200)
    const payload = await session.json()
    expect(payload).toEqual({ client_id: 'client-c-work', session_token: 'session-c-work' })
    expect(proxied[0]).toMatchObject({ id: 'c-work', path: '/v1/session' })
    expect(proxied[0].headers.authorization).toBe(`Bearer ${token}`)

    const operation = await fetch(endpoint, {
      method: 'POST',
      headers: { authorization: 'Bearer session-c-work', 'content-type': 'application/json' },
      body: JSON.stringify({ method: 'tabs.list', params: {}, operation_id: 'op-1' }),
    })
    expect(operation.status).toBe(200)
    expect(await operation.json()).toEqual({ result: { echoed: 'tabs.list', via: 'c-work' } })

    const unknownSession = await fetch(endpoint, { method: 'POST', headers: { authorization: 'Bearer forged' }, body: '{}' })
    expect(unknownSession.status).toBe(401)

    const personal = await fetch(`${endpoint}/session`, {
      method: 'POST',
      headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json', 'x-hermes-profile': 'personal' },
      body: '{}',
    })
    expect(personal.status).toBe(200)
    expect(await personal.json()).toMatchObject({ client_id: 'client-c-home' })
    await gateway.stop()
  })
})

describe('devices controller: profile-scoped peer connections and screen routes', () => {
  let home = ''
  beforeEach(() => {
    home = tempDir()
    vi.doMock('../../packages/server/src/modules/studio/public/config', () => ({ config: { appHome: home, corsOrigins: '' } }))
  })

  it('hides bound devices from other profiles and forwards screen calls for allowed ones', async () => {
    const captureScreen = vi.fn(async () => ({ media_type: 'image/png', data: 'AAAA', width: 10, height: 5, display_id: '1', displays: [] }))
    const screenAction = vi.fn(async () => ({ ok: true }))
    const connection = {
      id: 'c1',
      deviceId: 'mac',
      info: () => ({ id: 'c1', role: 'server', device_id: 'mac', computer_name: 'mac', url: '', connected_at: 1, controllable: true, capabilities: ['screen'] }),
      captureScreen,
      screenAction,
    }
    vi.doMock('../../packages/server/src/modules/studio/services/network/lan-peer-socket', () => ({
      getLanPeerSocketManager: () => ({
        listConnections: () => [connection.info(), { id: 'c2', role: 'client', device_id: 'other', controllable: false, capabilities: [] }],
        getConnection: (id: string) => (id === 'c1' ? connection : null),
        disconnectDevice: vi.fn(),
      }),
      validateLanPeerPath: (path: string) => path,
    }))
    const bindings = await import('../../packages/server/src/modules/studio/services/devices/device-bindings')
    bindings.resetDeviceBindingsCache()
    bindings.setDeviceBinding('mac', ['work'])
    const ctrl = await import('../../packages/server/src/modules/studio/controllers/devices')

    const workList: any = { state: { profile: { name: 'work' } } }
    await ctrl.listPeerConnections(workList)
    expect(workList.body.connections.map((c: any) => c.id)).toEqual(['c1', 'c2'])

    const homeList: any = { state: { profile: { name: 'home' } } }
    await ctrl.listPeerConnections(homeList)
    expect(homeList.body.connections.map((c: any) => c.id)).toEqual(['c2'])

    const denied: any = { state: { profile: { name: 'home' } }, params: { connectionId: 'c1' }, query: {} }
    await ctrl.capturePeerScreen(denied)
    expect(denied.status).toBe(403)
    expect(captureScreen).not.toHaveBeenCalled()

    const allowed: any = { state: { profile: { name: 'work' } }, params: { connectionId: 'c1' }, query: { max_width: '800' } }
    await ctrl.capturePeerScreen(allowed)
    expect(allowed.body).toMatchObject({ media_type: 'image/png', data: 'AAAA' })
    expect(captureScreen).toHaveBeenCalledWith({ displayId: undefined, maxWidth: 800 })

    const action: any = { state: {}, params: { connectionId: 'c1' }, request: { body: { action: 'click', x: 1, y: 2 } } }
    await ctrl.performPeerScreenAction(action)
    expect(action.body).toEqual({ ok: true })
    expect(screenAction).toHaveBeenCalledWith({ action: 'click', x: 1, y: 2 })

    const update: any = { params: { id: 'mac' }, request: { body: { profiles: ['home'] } } }
    await ctrl.updateDeviceBindingController(update)
    expect(update.body).toMatchObject({ device_id: 'mac', profiles: ['home'] })
    const listing: any = {}
    await ctrl.listDeviceBindingsController(listing)
    expect(listing.body).toEqual({ bindings: { mac: ['home'] } })
  })
})
