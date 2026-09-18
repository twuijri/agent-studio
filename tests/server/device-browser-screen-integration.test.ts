import { createServer, type Server } from 'node:http'
import { mkdtempSync, readFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { DeviceAgent } from '../../packages/desktop/src/main/device-agent/agent'
import { defaultDeviceAgentConfig, type DeviceAgentConfig } from '../../packages/desktop/src/main/device-agent/store'

// End-to-end for phases 3 and 4: the real server-side gateway impersonates
// the desktop browser broker and tunnels calls over the real peer socket to
// the real Device Agent, which forwards them to a broker on the device; the
// same connection serves screenshots and screen actions.

const wait = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))
const tempDirs: string[] = []
function tempDir(): string {
  const dir = mkdtempSync(join(tmpdir(), 'browser-e2e-'))
  tempDirs.push(dir)
  return dir
}

describe('device browser gateway and screen over the real peer socket', () => {
  let server: Server | null = null
  let deviceBroker: Server | null = null
  let agent: DeviceAgent | null = null
  let manager: any = null
  let gateway: any = null

  afterEach(async () => {
    agent?.stop()
    agent = null
    await gateway?.stop().catch(() => undefined)
    gateway = null
    await manager?.shutdown().catch(() => undefined)
    manager = null
    for (const srv of [server, deviceBroker]) await new Promise<void>(resolve => (srv ? srv.close(() => resolve()) : resolve()))
    server = null
    deviceBroker = null
    for (const dir of tempDirs.splice(0)) rmSync(dir, { recursive: true, force: true })
    vi.doUnmock('../../packages/server/src/modules/studio/repositories/devices-store')
    vi.doUnmock('../../packages/server/src/modules/studio/public/config')
    vi.resetModules()
  })

  it('drives the device browser through the broker descriptor and captures the device screen', async () => {
    const serverHome = tempDir()
    const deviceDir = tempDir()
    let approvedDeviceId = ''
    vi.doMock('../../packages/server/src/modules/studio/public/config', () => ({ config: { appHome: serverHome, corsOrigins: '' } }))
    vi.doMock('../../packages/server/src/modules/studio/repositories/devices-store', () => ({
      getDeviceRelation: (id: string) => (id === approvedDeviceId ? { inbound_status: 'approved' } : null),
    }))
    const socketModule = await import('../../packages/server/src/modules/studio/services/network/lan-peer-socket')
    const { DeviceBrowserGateway } = await import('../../packages/server/src/modules/studio/services/network/device-browser-gateway')
    const { getLanPeerToolsService } = await import('../../packages/server/src/modules/studio/services/network/lan-peer-tools')
    manager = socketModule.getLanPeerSocketManager()
    server = createServer((_req, res) => { res.statusCode = 404; res.end() })
    manager.setupServer(server)
    await new Promise<void>(resolve => server!.listen(0, '127.0.0.1', () => resolve()))
    const serverUrl = `http://127.0.0.1:${(server.address() as { port: number }).port}`
    gateway = new DeviceBrowserGateway()
    await gateway.start()

    // A fake browser broker running inside the "desktop app".
    const brokerCalls: Array<{ url: string; auth: string; method?: string }> = []
    deviceBroker = createServer(async (req, res) => {
      const chunks: Buffer[] = []
      for await (const chunk of req) chunks.push(Buffer.from(chunk))
      const body = chunks.length ? JSON.parse(Buffer.concat(chunks).toString('utf8')) : {}
      brokerCalls.push({ url: req.url || '', auth: String(req.headers.authorization || ''), method: body.method })
      res.setHeader('content-type', 'application/json')
      if (req.url === '/v1/session') {
        if (req.headers.authorization !== 'Bearer device-broker-token') { res.statusCode = 401; res.end(JSON.stringify({ error: 'bad token' })); return }
        res.end(JSON.stringify({ client_id: 'device-client', session_token: 'device-session' }))
        return
      }
      res.end(JSON.stringify({ operation_id: body.operation_id, result: { tabs: [{ id: 'tab-1', url: 'https://example.com/' }] } }))
    })
    await new Promise<void>(resolve => deviceBroker!.listen(0, '127.0.0.1', () => resolve()))
    const brokerEndpoint = `http://127.0.0.1:${(deviceBroker.address() as { port: number }).port}/v1`

    let config: DeviceAgentConfig = {
      ...defaultDeviceAgentConfig(),
      enabled: true,
      capabilities: { exec: false, files: false, browser: true, screen: true },
      allowedFolders: [],
      approvalMode: 'ask',
      pairedServerUrl: serverUrl,
      pairedAt: Date.now(),
    }
    const approveScreen = vi.fn(async () => true)
    const screenActions: Array<Record<string, unknown>> = []
    agent = new DeviceAgent({
      identityFile: join(deviceDir, 'device-identity.json'),
      loadConfig: () => config,
      saveConfig: next => { config = next; return next },
      serverUrl: () => serverUrl,
      approveExec: async () => 'deny',
      approveScreen,
      browserProxy: async request => {
        const target = new URL(brokerEndpoint)
        target.pathname = request.path
        const headers = { ...request.headers }
        if (request.path === '/v1/session') headers.authorization = 'Bearer device-broker-token'
        delete headers.host
        delete headers['content-length']
        const response = await fetch(target, { method: request.method, headers, body: request.body.length ? new Uint8Array(request.body) : undefined })
        const responseHeaders: Record<string, string> = {}
        response.headers.forEach((value, name) => { responseHeaders[name] = value })
        return { status: response.status, headers: responseHeaders, body: Buffer.from(await response.arrayBuffer()) }
      },
      screen: {
        capture: async options => ({ media_type: 'image/png', data: 'iVBORw0KGgo=', width: options.maxWidth || 1440, height: 900, display_id: '1', displays: [] }),
        action: async action => { screenActions.push(action) },
      },
      audit: () => undefined,
      appVersion: 'test',
      fetchImpl: (async () => ({ ok: true, status: 200, json: async () => ({ status: 'approved' }) })) as unknown as typeof fetch,
      timing: { pollMs: 50, reconnectBaseMs: 50, reconnectMaxMs: 100 },
    })
    await agent.start()
    approvedDeviceId = agent.getState().deviceId
    const deadline = Date.now() + 5000
    while (agent.getState().status !== 'connected' && Date.now() < deadline) await wait(20)
    expect(agent.getState().status).toBe('connected')
    await gateway.refresh()

    // The gateway published a broker descriptor the MCP browser toolset can consume.
    const descriptorPath = join(serverHome, 'desktop-browser', 'broker.json')
    const descriptor = JSON.parse(readFileSync(descriptorPath, 'utf8'))
    expect(descriptor).toMatchObject({ schema: 1, desktopPid: process.pid, gateway: 'device-browser' })

    const session = await fetch(`${descriptor.endpoint}/session`, {
      method: 'POST',
      headers: { authorization: `Bearer ${descriptor.token}`, 'content-type': 'application/json', 'x-hermes-profile': 'work' },
      body: JSON.stringify({ client: 'mcp', client_pid: process.pid }),
    })
    expect(session.status).toBe(200)
    expect(await session.json()).toEqual({ client_id: 'device-client', session_token: 'device-session' })

    const tabs = await fetch(descriptor.endpoint, {
      method: 'POST',
      headers: { authorization: 'Bearer device-session', 'content-type': 'application/json', 'x-hermes-browser-client': 'device-client' },
      body: JSON.stringify({ method: 'tabs.list', params: {}, operation_id: 'op-1' }),
    })
    expect(tabs.status).toBe(200)
    expect(await tabs.json()).toEqual({ operation_id: 'op-1', result: { tabs: [{ id: 'tab-1', url: 'https://example.com/' }] } })
    expect(brokerCalls.map(call => call.url)).toEqual(['/v1/session', '/v1'])
    expect(brokerCalls[1].auth).toBe('Bearer device-session')

    // Screen: one approval per session, then capture and act through the tools service.
    const connectionId = manager.listConnections()[0].id
    const tools = getLanPeerToolsService()
    const shot = await tools.captureScreen(connectionId, { maxWidth: 800 })
    expect(shot).toMatchObject({ media_type: 'image/png', width: 800, display_id: '1' })
    await tools.screenAction(connectionId, { action: 'click', x: 5, y: 6 })
    expect(screenActions).toEqual([{ action: 'click', x: 5, y: 6 }])
    expect(approveScreen).toHaveBeenCalledTimes(1)

    // Stopping the session from the device withdraws access until the user approves again.
    agent.revokeScreenSession()
    await tools.captureScreen(connectionId)
    expect(approveScreen).toHaveBeenCalledTimes(2)

    // Descriptor disappears when the device goes away.
    agent.stop()
    const gone = Date.now() + 3000
    while (manager.listConnections().length > 0 && Date.now() < gone) await wait(20)
    await gateway.refresh()
    expect(() => readFileSync(descriptorPath)).toThrow()
  })
})
