import { createServer, type Server } from 'node:http'
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { DeviceAgent } from '../../packages/desktop/src/main/device-agent/agent'
import { defaultDeviceAgentConfig, type DeviceAgentConfig } from '../../packages/desktop/src/main/device-agent/store'
import type { DeviceAgentAuditEntry } from '../../packages/desktop/src/main/device-agent/protocol'

// End-to-end: the real server-side peer socket manager accepts the real
// desktop Device Agent (Node's built-in WebSocket client), and the remote
// tools service that backs the `ekko_studio_devices` MCP toolset runs a
// command and moves files on the "device".

const wait = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))
const tempDirs: string[] = []
function tempDir(): string {
  const dir = mkdtempSync(join(tmpdir(), 'peer-e2e-'))
  tempDirs.push(dir)
  return dir
}

describe('desktop Device Agent against the real peer socket manager', () => {
  let server: Server | null = null
  let agent: DeviceAgent | null = null
  let manager: any = null

  afterEach(async () => {
    agent?.stop()
    agent = null
    await manager?.shutdown().catch(() => undefined)
    manager = null
    await new Promise<void>(resolve => (server ? server.close(() => resolve()) : resolve()))
    server = null
    for (const dir of tempDirs.splice(0)) rmSync(dir, { recursive: true, force: true })
    vi.doUnmock('../../packages/server/src/modules/studio/repositories/devices-store')
    vi.resetModules()
  })

  it('lets server-side tools run commands and exchange files with an approved controllable device', async () => {
    const deviceDir = tempDir()
    const serverDir = tempDir()
    const shared = join(deviceDir, 'shared')
    mkdirSync(shared)
    writeFileSync(join(shared, 'hello.txt'), 'from the device')

    let approvedDeviceId = ''
    vi.doMock('../../packages/server/src/modules/studio/repositories/devices-store', () => ({
      getDeviceRelation: (id: string) => (id === approvedDeviceId ? { inbound_status: 'approved' } : null),
    }))
    const socketModule = await import('../../packages/server/src/modules/studio/services/network/lan-peer-socket')
    const { getLanPeerToolsService } = await import('../../packages/server/src/modules/studio/services/network/lan-peer-tools')
    socketModule.configureLanPeerFilesystem({
      getActiveProfileDir: () => serverDir,
      getTerminalConfig: () => ({}),
      validatePath: (path: string) => path,
    })
    manager = socketModule.getLanPeerSocketManager()

    server = createServer((_req, res) => { res.statusCode = 404; res.end() })
    manager.setupServer(server)
    await new Promise<void>(resolve => server!.listen(0, '127.0.0.1', () => resolve()))
    const port = (server.address() as { port: number }).port
    const serverUrl = `http://127.0.0.1:${port}`

    let config: DeviceAgentConfig = {
      ...defaultDeviceAgentConfig(),
      enabled: true,
      capabilities: { exec: true, files: true },
      allowedFolders: [shared],
      approvalMode: 'always',
      pairedServerUrl: serverUrl,
      pairedAt: Date.now(),
    }
    const audit: DeviceAgentAuditEntry[] = []
    agent = new DeviceAgent({
      identityFile: join(deviceDir, 'device-identity.json'),
      loadConfig: () => config,
      saveConfig: next => { config = next; return next },
      serverUrl: () => serverUrl,
      approveExec: async () => 'allow',
      audit: entry => audit.push(entry),
      appVersion: 'test',
      // The agent checks approval over HTTP before dialling; this server only has the socket.
      fetchImpl: (async () => ({ ok: true, status: 200, json: async () => ({ status: 'approved' }) })) as unknown as typeof fetch,
      timing: { pollMs: 50, reconnectBaseMs: 50, reconnectMaxMs: 100 },
    })
    await agent.start()
    approvedDeviceId = agent.getState().deviceId

    const deadline = Date.now() + 5000
    while (agent.getState().status !== 'connected' && Date.now() < deadline) await wait(20)
    expect(agent.getState().status).toBe('connected')

    const connections = manager.listConnections()
    expect(connections).toHaveLength(1)
    expect(connections[0]).toMatchObject({ role: 'server', device_id: approvedDeviceId, controllable: true, capabilities: ['exec', 'files'] })
    const connectionId = connections[0].id

    const tools = getLanPeerToolsService()
    const result = await tools.exec({ connectionId, command: process.execPath, args: ['-e', 'process.stdout.write("device says " + require("path").basename(process.cwd()))'] })
    expect(result).toMatchObject({ exit_code: 0, timed_out: false })
    expect(result.stdout).toBe('device says shared')

    const outside = await tools.exec({ connectionId, command: process.execPath, args: ['-e', '0'], cwd: deviceDir }).catch(err => err)
    expect(outside).toBeInstanceOf(Error)
    expect(String(outside.message)).toMatch(/outside/)

    const downloaded = await tools.downloadFile({ connectionId, remotePath: join(shared, 'hello.txt'), localPath: join(serverDir, 'copy.txt') })
    expect(downloaded.size).toBe('from the device'.length)
    expect(readFileSync(join(serverDir, 'copy.txt'), 'utf8')).toBe('from the device')

    writeFileSync(join(serverDir, 'push.txt'), 'from the server')
    await tools.uploadFile({ connectionId, localPath: join(serverDir, 'push.txt'), remotePath: join(shared, 'incoming', 'push.txt') })
    expect(readFileSync(join(shared, 'incoming', 'push.txt'), 'utf8')).toBe('from the server')

    await expect(tools.createTerminal(connectionId)).rejects.toThrow(/not available/)
    expect(audit.filter(entry => entry.kind === 'exec' && entry.ok)).toHaveLength(1)
    expect(audit.some(entry => entry.kind === 'denied')).toBe(true)
  })

  it('refuses the socket for devices that are not inbound-approved', async () => {
    vi.doMock('../../packages/server/src/modules/studio/repositories/devices-store', () => ({
      getDeviceRelation: () => ({ inbound_status: 'pending' }),
    }))
    const socketModule = await import('../../packages/server/src/modules/studio/services/network/lan-peer-socket')
    manager = socketModule.getLanPeerSocketManager()
    server = createServer((_req, res) => { res.statusCode = 404; res.end() })
    manager.setupServer(server)
    await new Promise<void>(resolve => server!.listen(0, '127.0.0.1', () => resolve()))
    const serverUrl = `http://127.0.0.1:${(server.address() as { port: number }).port}`

    const dir = tempDir()
    let config: DeviceAgentConfig = { ...defaultDeviceAgentConfig(), enabled: true, capabilities: { exec: true, files: false }, allowedFolders: [dir], pairedServerUrl: serverUrl, pairedAt: 1 }
    agent = new DeviceAgent({
      identityFile: join(dir, 'device-identity.json'),
      loadConfig: () => config,
      saveConfig: next => { config = next; return next },
      serverUrl: () => serverUrl,
      approveExec: async () => 'allow',
      audit: () => undefined,
      appVersion: 'test',
      fetchImpl: (async () => ({ ok: true, status: 200, json: async () => ({ status: 'approved' }) })) as unknown as typeof fetch,
      timing: { pollMs: 50, reconnectBaseMs: 50, reconnectMaxMs: 100 },
    })
    await agent.start()
    await wait(300)
    expect(manager.listConnections()).toHaveLength(0)
    expect(['offline', 'connecting', 'error']).toContain(agent.getState().status)
  })
})
