import { createHash, generateKeyPairSync, sign } from 'crypto'
import { readFileSync } from 'fs'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

describe('controllable peer connections (desktop Device Agent)', () => {
  afterEach(() => {
    vi.resetModules()
    vi.doUnmock('../../packages/server/src/modules/studio/services/network/lan-peer-socket')
  })

  it('parses the handshake declaration and only known capabilities', async () => {
    const { parseLanPeerCapabilities, parseLanPeerControllable } = await import('../../packages/server/src/modules/studio/services/network/lan-peer-socket')
    expect(parseLanPeerControllable('1')).toBe(true)
    expect(parseLanPeerControllable('true')).toBe(true)
    expect(parseLanPeerControllable(null)).toBe(false)
    expect(parseLanPeerControllable('0')).toBe(false)
    expect(parseLanPeerCapabilities('exec, files,bogus,TERMINAL')).toEqual(['exec', 'files', 'terminal'])
    expect(parseLanPeerCapabilities('')).toEqual([])
  })

  it('accepts a declared workspace folder and rejects control characters or oversized values', async () => {
    const { parseLanPeerWorkspace } = await import('../../packages/server/src/modules/studio/services/network/lan-peer-socket')
    expect(parseLanPeerWorkspace(' /Users/me/Core Hub ')).toBe('/Users/me/Core Hub')
    expect(parseLanPeerWorkspace('')).toBeUndefined()
    expect(parseLanPeerWorkspace('bad\u0000path')).toBeUndefined()
    expect(parseLanPeerWorkspace('x'.repeat(1025))).toBeUndefined()
  })

  it('lets remote tools run against an inbound peer that declared itself controllable', async () => {
    const execRemoteCommand = vi.fn(async () => ({ stdout: 'mac', stderr: '', exit_code: 0, timed_out: false }))
    vi.doMock('../../packages/server/src/modules/studio/services/network/lan-peer-socket', () => ({
      getLanPeerSocketManager: () => ({
        getConnection: () => ({
          info: () => ({ role: 'server', controllable: true, capabilities: ['exec', 'files'] }),
          execRemoteCommand,
        }),
      }),
    }))
    const { getLanPeerToolsService } = await import('../../packages/server/src/modules/studio/services/network/lan-peer-tools')
    const result = await getLanPeerToolsService().exec({ connectionId: 'desktop', command: 'hostname' })
    expect(result.stdout).toBe('mac')
    expect(execRemoteCommand).toHaveBeenCalledOnce()
  })

  it('still refuses passive inbound peers that did not declare control', async () => {
    vi.doMock('../../packages/server/src/modules/studio/services/network/lan-peer-socket', () => ({
      getLanPeerSocketManager: () => ({
        getConnection: () => ({
          info: () => ({ role: 'server', controllable: false, capabilities: [] }),
          execRemoteCommand: vi.fn(),
        }),
      }),
    }))
    const { getLanPeerToolsService } = await import('../../packages/server/src/modules/studio/services/network/lan-peer-tools')
    expect(() => getLanPeerToolsService().exec({ connectionId: 'passive', command: 'id' }))
      .toThrow('Peer connection is not authorized for remote tools')
  })

  it('only honours the declaration after the inbound-approval check in the upgrade handler', () => {
    const source = readFileSync('packages/server/src/modules/studio/services/network/lan-peer-socket.ts', 'utf8')
    const setup = source.slice(source.indexOf('setupServer(httpServers'), source.indexOf('forceClose(): void'))
    expect(setup.indexOf('await this.authenticateUpgrade(url, req)')).toBeLessThan(setup.indexOf('parseLanPeerControllable(url.searchParams.get'))
    expect(setup).toContain("{ controllable, capabilities, workspace }")
    const auth = source.slice(source.indexOf('private async authenticateUpgrade'))
    expect(auth).toContain("if (relation?.inbound_status !== 'approved')")
  })
})

describe('devices controller: callback-less controllable devices', () => {
  const keyPair = generateKeyPairSync('ed25519', {
    publicKeyEncoding: { type: 'spki', format: 'pem' },
    privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
  })
  const deviceId = `hwui_${createHash('sha256').update(keyPair.publicKey).digest('base64url').slice(0, 32)}`
  let db: any = null

  beforeEach(async () => {
    vi.resetModules()
    const { DatabaseSync } = await import('node:sqlite')
    db = new DatabaseSync(':memory:')
    vi.doMock('../../packages/server/src/modules/studio/infrastructure/database/index', () => ({
      getDb: () => db,
      getStoragePath: () => ':memory:',
    }))
    vi.doMock('../../packages/server/src/modules/studio/services/devices/pairing-code', () => ({
      getDevicePairingCode: () => 'pair-secret',
      verifyDevicePairingCode: (value: unknown) => value === 'pair-secret',
    }))
    const { initAllHermesTables } = await import('../../packages/server/src/modules/studio/infrastructure/database/schemas')
    initAllHermesTables()
  })

  afterEach(() => {
    db?.close()
    db = null
    vi.doUnmock('../../packages/server/src/modules/studio/infrastructure/database/index')
    vi.doUnmock('../../packages/server/src/modules/studio/services/devices/pairing-code')
    vi.resetModules()
  })

  function signedBody(extra: Record<string, unknown>) {
    const timestamp = Date.now()
    const nonce = `nonce-${Math.random()}`
    const signature = sign(null, Buffer.from(`${deviceId}.${nonce}.${timestamp}`), keyPair.privateKey).toString('base64url')
    return {
      device_id: deviceId,
      device_public_key: keyPair.publicKey,
      computer_name: 'owner-mac',
      os: { type: 'Darwin', platform: 'darwin', release: '24', arch: 'arm64' },
      hermes_agent_version: '',
      hermes_web_ui_version: 'desktop',
      pairing_code: 'pair-secret',
      timestamp,
      nonce,
      signature,
      ...extra,
    }
  }

  it('accepts a controllable pairing request without an HTTP port and stores it without a callback URL', async () => {
    const ctx: any = { ip: '8.8.4.4', request: { ip: '8.8.4.4', body: signedBody({ controllable: true, endpoint_kind: 'desktop' }) } }
    const { requestDeviceLinkController } = await import('../../packages/server/src/modules/studio/controllers/devices')
    const { getDeviceRelation } = await import('../../packages/server/src/modules/studio/repositories/devices-store')
    await requestDeviceLinkController(ctx)

    expect(ctx.status).toBeUndefined()
    expect(ctx.body).toEqual({ status: 'pending' })
    const relation = getDeviceRelation(deviceId)
    expect(relation?.inbound_status).toBe('pending')
    expect(relation?.http_port).toBe(0)
    expect(relation?.url).toBe('')
    expect(relation?.endpoint_kind).toBe('desktop')
  })

  it('still rejects port-less requests that do not declare control', async () => {
    const ctx: any = { ip: '8.8.4.4', request: { ip: '8.8.4.4', body: signedBody({}) } }
    const { requestDeviceLinkController } = await import('../../packages/server/src/modules/studio/controllers/devices')
    await requestDeviceLinkController(ctx)
    expect(ctx.status).toBe(400)
  })

  it('keeps an approved callback-less device in the list and marks it online while its peer socket is up', async () => {
    vi.doMock('../../packages/server/src/modules/studio/services/network/lan-discovery', async () => {
      const actual = await vi.importActual<any>('../../packages/server/src/modules/studio/services/network/lan-discovery')
      return { ...actual, getLanDiscoveryCache: () => ({ devices: [], scanning: false, last_scanned_at: null }) }
    })
    const connectToDevice = vi.fn()
    vi.doMock('../../packages/server/src/modules/studio/services/network/lan-peer-socket', () => ({
      getLanPeerSocketManager: () => ({
        listConnections: () => [{ id: 'c1', role: 'server', device_id: deviceId, controllable: true, capabilities: ['exec'] }],
        connectToDevice,
        disconnectDevice: vi.fn(),
      }),
    }))

    const pairCtx: any = { ip: '8.8.4.4', request: { ip: '8.8.4.4', body: signedBody({ controllable: true }) } }
    const { requestDeviceLinkController, approveDevice, listDevices } = await import('../../packages/server/src/modules/studio/controllers/devices')
    await requestDeviceLinkController(pairCtx)
    const approveCtx: any = { params: { id: deviceId } }
    await approveDevice(approveCtx)

    const listCtx: any = {}
    await listDevices(listCtx)
    const listed = listCtx.body.devices.find((device: any) => device.id === deviceId)
    expect(listed).toBeDefined()
    expect(listed.online).toBe(true)
    expect(listed.inbound_status).toBe('approved')
    expect(listed.url).toBe('')
    expect(connectToDevice).not.toHaveBeenCalled()
  })
})
