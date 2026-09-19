import { createServer, type Server } from 'node:http'
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import type { Readable } from 'node:stream'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { DeviceAgent } from '../../packages/desktop/src/main/device-agent/agent'
import { defaultDeviceAgentConfig, type DeviceAgentConfig } from '../../packages/desktop/src/main/device-agent/store'

// A file produced on a linked device (e.g. a video exported by a device app)
// is streamed from the device into the chat's media player: explicit
// device://<id>/<path> links, byte ranges, the fallback for plain device paths
// that do not exist on the server, and the device's shared-folder policy.

const wait = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))
const tempDirs: string[] = []
function tempDir(): string {
  const dir = mkdtempSync(join(tmpdir(), 'device-media-'))
  tempDirs.push(dir)
  return dir
}

async function collect(body: unknown): Promise<Buffer> {
  const chunks: Buffer[] = []
  for await (const chunk of body as Readable) chunks.push(Buffer.from(chunk))
  return Buffer.concat(chunks)
}

function fakeCtx(query: Record<string, string>, headers: Record<string, string> = {}) {
  const set: Record<string, string> = {}
  return {
    query, status: 200, body: undefined as unknown, headers: set, state: {},
    set(name: string, value: string) { set[name.toLowerCase()] = value },
    get(name: string) { return headers[name.toLowerCase()] || '' },
  }
}

describe('device media streaming through the download route', () => {
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
    vi.doUnmock('../../packages/server/src/modules/studio/public/profile-config')
    vi.doUnmock('../../packages/server/src/modules/studio/services/files/file-provider')
    vi.doUnmock('../../packages/server/src/modules/studio/services/devices/device-bindings')
    vi.doUnmock('fs/promises')
    vi.resetModules()
  })

  it('streams a device video with ranges, falls back for plain device paths, and respects shared folders', { timeout: 20000 }, async () => {
    const deviceDir = tempDir()
    const serverDir = tempDir()
    const shared = join(deviceDir, 'Core Hub')
    mkdirSync(shared)
    const video = Buffer.from('MOOV'.repeat(2048)) // 8 KiB "video"
    writeFileSync(join(shared, 'final.mp4'), video)
    writeFileSync(join(deviceDir, 'private.mp4'), video)

    let approvedDeviceId = ''
    vi.doMock('../../packages/server/src/modules/studio/repositories/devices-store', () => ({
      getDeviceRelation: (id: string) => (id === approvedDeviceId ? { inbound_status: 'approved' } : null),
    }))
    vi.doMock('../../packages/server/src/modules/studio/public/profile-config', () => ({ getActiveProfileName: () => 'default' }))
    vi.doMock('../../packages/server/src/modules/studio/services/devices/device-bindings', () => ({
      isDeviceAllowedForProfile: (deviceId: string, profile: string) => profile === 'default' && deviceId === approvedDeviceId,
    }))
    vi.doMock('../../packages/server/src/modules/studio/services/files/file-provider', async importOriginal => {
      const actual = await importOriginal<typeof import('../../packages/server/src/modules/studio/services/files/file-provider')>()
      return { ...actual, createFileProvider: async () => actual.localProvider }
    })
    const socketModule = await import('../../packages/server/src/modules/studio/services/network/lan-peer-socket')
    socketModule.configureLanPeerFilesystem({ getActiveProfileDir: () => serverDir, getTerminalConfig: () => ({}), validatePath: (path: string) => path })
    manager = socketModule.getLanPeerSocketManager()
    server = createServer((_req, res) => { res.statusCode = 404; res.end() })
    manager.setupServer(server)
    await new Promise<void>(resolve => server!.listen(0, '127.0.0.1', resolve))
    const port = (server.address() as { port: number }).port

    let config: DeviceAgentConfig = { ...defaultDeviceAgentConfig(), enabled: true, capabilities: { exec: false, files: true, browser: false, screen: false, apps: false }, allowedFolders: [shared], pairedServerUrl: `http://127.0.0.1:${port}`, pairedAt: Date.now() }
    agent = new DeviceAgent({
      identityFile: join(deviceDir, 'identity.json'),
      loadConfig: () => config,
      saveConfig: next => { config = next; return next },
      serverUrl: () => `http://127.0.0.1:${port}`,
      approveExec: async () => 'deny',
      audit: () => undefined,
      appVersion: '1.0.0-test',
      fetchImpl: (async (url: string) => {
        if (String(url).endsWith('/api/devices/link-status')) return new Response(JSON.stringify({ status: 'approved' }), { status: 200, headers: { 'content-type': 'application/json' } })
        return new Response('{}', { status: 404 })
      }) as unknown as typeof fetch,
      timing: { pollMs: 50, reconnectBaseMs: 50, reconnectMaxMs: 100 },
    })
    await agent.start()
    approvedDeviceId = agent.getState().deviceId
    for (let i = 0; i < 100 && agent.getState().status !== 'connected'; i++) await wait(50)
    expect(agent.getState().status).toBe('connected')

    // Server and "device" share this machine's filesystem in the test, so make
    // the server side believe the exported file does not exist locally.
    let hideFromServer = false
    vi.doMock('fs/promises', async importOriginal => {
      const actual = await importOriginal<typeof import('fs/promises')>()
      return {
        ...actual,
        stat: async (target: any, ...rest: any[]) => {
          if (hideFromServer && String(target).endsWith('final.mp4')) throw Object.assign(new Error('ENOENT'), { code: 'ENOENT' })
          return (actual.stat as any)(target, ...rest)
        },
      }
    })
    const { download } = await import('../../packages/server/src/modules/studio/controllers/download')

    // Explicit device link: full stream.
    const full = fakeCtx({ path: `device://${approvedDeviceId}${join(shared, 'final.mp4')}` })
    await download(full)
    expect(full.status).toBe(200)
    expect(full.headers['content-type']).toBe('video/mp4')
    expect(full.headers['accept-ranges']).toBe('bytes')
    expect(full.headers['x-core-hub-source']).toBe(`device:${approvedDeviceId}`)
    expect(full.headers['content-length']).toBe(String(video.length))
    expect((await collect(full.body)).equals(video)).toBe(true)

    // Byte range for the player's seek.
    const partial = fakeCtx({ device: approvedDeviceId, path: join(shared, 'final.mp4') }, { range: 'bytes=4096-4099' })
    await download(partial)
    expect(partial.status).toBe(206)
    expect(partial.headers['content-range']).toBe(`bytes 4096-4099/${video.length}`)
    expect((await collect(partial.body)).toString()).toBe('MOOV')

    // Plain device path that does not exist on the server: found on the bound device.
    hideFromServer = true
    const fallback = fakeCtx({ path: join(shared, 'final.mp4') })
    await download(fallback)
    hideFromServer = false
    expect(fallback.status).toBe(200)
    expect(fallback.headers['x-core-hub-source']).toBe(`device:${approvedDeviceId}`)
    expect((await collect(fallback.body)).length).toBe(video.length)

    // Outside the device's shared folders: the device refuses, the server reports not found.
    const outside = fakeCtx({ path: `device://${approvedDeviceId}${join(deviceDir, 'private.mp4')}` })
    await download(outside)
    expect(outside.status).toBe(404)
    expect((outside.body as any).code).toBe('device_file_unavailable')

    // A device not bound to the profile is never asked.
    const foreign = fakeCtx({ path: `device://hwui_someoneelse${join(shared, 'final.mp4')}` })
    await download(foreign)
    expect(foreign.status).toBe(404)
  })
})
