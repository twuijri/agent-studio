import { createHash, verify } from 'node:crypto'
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { loadOrCreateDeviceIdentity, signDeviceChallenge } from '../../packages/desktop/src/main/device-agent/identity'
import { DeviceAgentProtocol, resolveAllowedPath, type DeviceAgentAuditEntry, type DeviceAgentPolicy } from '../../packages/desktop/src/main/device-agent/protocol'
import { appendDeviceAgentAudit, readDeviceAgentAudit, readDeviceAgentConfig, writeDeviceAgentConfig, defaultDeviceAgentConfig } from '../../packages/desktop/src/main/device-agent/store'
import { DeviceAgent, extractPairingCode, peerSocketUrl } from '../../packages/desktop/src/main/device-agent/agent'

const tempDirs: string[] = []
function tempDir(): string {
  const dir = mkdtempSync(join(tmpdir(), 'device-agent-'))
  tempDirs.push(dir)
  return dir
}
afterEach(() => {
  for (const dir of tempDirs.splice(0)) rmSync(dir, { recursive: true, force: true })
  vi.useRealTimers()
})

const wait = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))

describe('device identity', () => {
  it('creates a Studio-compatible ed25519 identity once and signs challenges the server can verify', () => {
    const file = join(tempDir(), 'nested', 'device-identity.json')
    const identity = loadOrCreateDeviceIdentity(file)
    expect(identity.device_id).toBe(`hwui_${createHash('sha256').update(identity.device_public_key).digest('base64url').slice(0, 32)}`)
    expect(loadOrCreateDeviceIdentity(file)).toEqual(identity)

    const signature = signDeviceChallenge(identity, 'nonce-1', 1700000000000)
    expect(verify(null, Buffer.from(`${identity.device_id}.nonce-1.1700000000000`), identity.device_public_key, Buffer.from(signature, 'base64url'))).toBe(true)
  })

  it('replaces a corrupt or tampered identity file', () => {
    const file = join(tempDir(), 'device-identity.json')
    writeFileSync(file, '{"device_id":"hwui_forged","device_public_key":"-----BEGIN PUBLIC KEY-----x","device_private_key":"-----BEGIN PRIVATE KEY-----y"}')
    const identity = loadOrCreateDeviceIdentity(file)
    expect(identity.device_id).not.toBe('hwui_forged')
    expect(JSON.parse(readFileSync(file, 'utf8')).device_id).toBe(identity.device_id)
  })
})

describe('device agent store', () => {
  it('normalizes config and keeps an audit log with newest entries first', () => {
    const dir = tempDir()
    const config = join(dir, 'device-agent.json')
    expect(readDeviceAgentConfig(config)).toEqual(defaultDeviceAgentConfig())
    writeDeviceAgentConfig(config, { ...defaultDeviceAgentConfig(), enabled: true, capabilities: { exec: true, files: false, browser: true, screen: false }, allowedFolders: [' /tmp/a ', '/tmp/a', ''], approvalMode: 'always', pairedServerUrl: 'https://s.example', pairedAt: 5 })
    expect(readDeviceAgentConfig(config)).toEqual({ enabled: true, capabilities: { exec: true, files: false, browser: true, screen: false }, allowedFolders: ['/tmp/a'], approvalMode: 'always', pairedServerUrl: 'https://s.example', pairedAt: 5 })

    const audit = join(dir, 'audit.jsonl')
    appendDeviceAgentAudit(audit, { at: 1, kind: 'exec', detail: 'first', ok: true })
    appendDeviceAgentAudit(audit, { at: 2, kind: 'denied', detail: 'second', ok: false })
    expect(readDeviceAgentAudit(audit, 10).map(entry => entry.detail)).toEqual(['second', 'first'])
  })
})

describe('device agent protocol', () => {
  function harness(policy: Partial<DeviceAgentPolicy> & { folders?: string[] }) {
    const sent: Record<string, unknown>[] = []
    const audit: DeviceAgentAuditEntry[] = []
    const folders = policy.folders ?? []
    const protocol = new DeviceAgentProtocol({
      send: payload => sent.push(payload),
      audit: entry => audit.push(entry),
      policy: () => ({
        capabilities: policy.capabilities ?? ['exec', 'files'],
        allowedFolders: folders,
        approveExec: policy.approveExec ?? (async () => true),
        defaultCwd: folders[0],
      }),
    })
    const waitFor = async (type: string, timeoutMs = 5000) => {
      const deadline = Date.now() + timeoutMs
      while (Date.now() < deadline) {
        const found = sent.find(message => message.type === type)
        if (found) return found
        await wait(10)
      }
      throw new Error(`no ${type} message; got ${sent.map(m => m.type).join(',')}`)
    }
    return { protocol, sent, audit, waitFor }
  }

  it('restricts paths to the shared folders', () => {
    const root = tempDir()
    expect(resolveAllowedPath(join(root, 'a', 'b.txt'), [root])).toBe(join(root, 'a', 'b.txt'))
    expect(resolveAllowedPath(root, [root])).toBe(root)
    expect(() => resolveAllowedPath(join(root, '..', 'escape.txt'), [root])).toThrow(/outside/)
    expect(() => resolveAllowedPath('/etc/passwd', [root])).toThrow(/outside/)
    expect(() => resolveAllowedPath(join(root, 'x'), [])).toThrow(/outside/)
  })

  it('runs an approved command inside a shared folder and reports the result', async () => {
    const root = tempDir()
    const approveExec = vi.fn(async () => true)
    const { protocol, waitFor, audit } = harness({ folders: [root], approveExec })
    protocol.handle({ type: 'terminal.exec', request_id: 'r1', command: process.execPath, args: ['-e', 'process.stdout.write(process.cwd())'] })
    const result = await waitFor('terminal.exec.result')
    expect(result).toMatchObject({ request_id: 'r1', exit_code: 0, timed_out: false })
    expect(String(result.stdout)).toContain(root.replace(/^\/private/, ''))
    expect(approveExec).toHaveBeenCalledWith(expect.objectContaining({ command: process.execPath, cwd: root }))
    expect(audit.at(-1)).toMatchObject({ kind: 'exec', ok: true })
  })

  it('refuses commands the user denies, commands outside shared folders, and disabled capabilities', async () => {
    const root = tempDir()
    const denied = harness({ folders: [root], approveExec: async () => false })
    denied.protocol.handle({ type: 'terminal.exec', request_id: 'r2', command: 'id' })
    expect(await denied.waitFor('terminal.exec.error')).toMatchObject({ request_id: 'r2', message: expect.stringMatching(/denied/) })

    const outside = harness({ folders: [root] })
    outside.protocol.handle({ type: 'terminal.exec', request_id: 'r3', command: 'id', cwd: '/' })
    expect(await outside.waitFor('terminal.exec.error')).toMatchObject({ request_id: 'r3', message: expect.stringMatching(/outside/) })

    const disabled = harness({ folders: [root], capabilities: ['files'] })
    disabled.protocol.handle({ type: 'terminal.exec', request_id: 'r4', command: 'id' })
    expect(await disabled.waitFor('terminal.exec.error')).toMatchObject({ message: expect.stringMatching(/disabled/) })
    expect(disabled.audit.at(-1)).toMatchObject({ kind: 'denied' })
  })

  it('streams a shared file to the server and writes uploads only inside shared folders', async () => {
    const root = tempDir()
    mkdirSync(join(root, 'docs'))
    writeFileSync(join(root, 'docs', 'note.txt'), 'hello device')
    const { protocol, sent, waitFor } = harness({ folders: [root] })

    protocol.handle({ type: 'file.download', request_id: 'd1', transfer_id: 't1', path: join(root, 'docs', 'note.txt') })
    await waitFor('file.download.complete')
    const chunks = sent.filter(message => message.type === 'file.download.chunk').map(message => Buffer.from(String(message.data), 'base64').toString('utf8'))
    expect(chunks.join('')).toBe('hello device')
    expect(sent.find(message => message.type === 'file.download.started')).toMatchObject({ request_id: 'd1', transfer_id: 't1' })

    protocol.handle({ type: 'file.upload.start', request_id: 'u1', transfer_id: 't2', path: join(root, 'in', 'new.txt') })
    expect(await waitFor('file.upload.ready')).toMatchObject({ request_id: 'u1', transfer_id: 't2' })
    protocol.handle({ type: 'file.upload.chunk', transfer_id: 't2', data: Buffer.from('from server').toString('base64') })
    protocol.handle({ type: 'file.upload.complete', request_id: 'u2', transfer_id: 't2' })
    expect(await waitFor('file.upload.complete')).toMatchObject({ request_id: 'u2', path: join(root, 'in', 'new.txt') })
    expect(readFileSync(join(root, 'in', 'new.txt'), 'utf8')).toBe('from server')

    protocol.handle({ type: 'file.download', request_id: 'd2', transfer_id: 't3', path: '/etc/hostname' })
    expect(await waitFor('file.error')).toMatchObject({ transfer_id: 't3', message: expect.stringMatching(/outside/) })
  })

  it('answers terminal requests with the protocol error and rejects garbage', () => {
    const { protocol, sent } = harness({})
    protocol.handle({ type: 'terminal.create', request_id: 'tc' })
    expect(sent[0]).toMatchObject({ type: 'terminal.error', request_id: 'tc' })
    protocol.handleRaw('not json')
    expect(sent[1]).toMatchObject({ type: 'error' })
    protocol.handle({ type: 'peer.ready' })
    expect(sent).toHaveLength(2)
  })
})

describe('device agent pairing and connection', () => {
  it('extracts pairing codes from copied links, hash routes, or bare codes', () => {
    expect(extractPairingCode('https://ai.example/#/hermes/devices?pairing_code=abc-123')).toBe('abc-123')
    expect(extractPairingCode('https://ai.example/?pairing_code=q1')).toBe('q1')
    expect(extractPairingCode('  code-only ')).toBe('code-only')
    expect(extractPairingCode('')).toBe('')
    expect(peerSocketUrl('https://ai.example/studio')).toBe('wss://ai.example/studio/api/devices/peer-socket')
    expect(peerSocketUrl('http://10.0.0.5:6060')).toBe('ws://10.0.0.5:6060/api/devices/peer-socket')
  })

  class FakeSocket {
    static instances: FakeSocket[] = []
    readyState = 0
    sent: string[] = []
    private listeners = new Map<string, Array<(event: any) => void>>()
    constructor(readonly url: string) {
      FakeSocket.instances.push(this)
    }
    addEventListener(type: string, listener: (event: any) => void) {
      this.listeners.set(type, [...(this.listeners.get(type) || []), listener])
    }
    emit(type: string, event: any = {}) {
      for (const listener of this.listeners.get(type) || []) listener(event)
    }
    send(data: string) { this.sent.push(data) }
    close() { this.readyState = 3; this.emit('close', { code: 1000 }) }
    open() { this.readyState = 1; this.emit('open', {}) }
  }

  function makeAgent(options: { linkStatuses: string[]; serverUrl?: string | null; approve?: () => Promise<'allow' | 'allow-session' | 'deny'> }) {
    const dir = tempDir()
    const configFile = join(dir, 'device-agent.json')
    const statuses = [...options.linkStatuses]
    const requests: Array<{ url: string; body: any }> = []
    const fetchImpl = vi.fn(async (url: string, init?: any) => {
      const body = JSON.parse(init.body)
      requests.push({ url, body })
      if (url.endsWith('/api/devices/link-request')) {
        expect(body.controllable).toBe(true)
        expect(body.pairing_code).toBe('pair-secret')
        return { ok: true, status: 200, json: async () => ({ status: 'pending' }) }
      }
      const status = statuses.length > 1 ? statuses.shift() : statuses[0]
      return { ok: true, status: 200, json: async () => ({ status }) }
    }) as unknown as typeof fetch
    const audit: DeviceAgentAuditEntry[] = []
    FakeSocket.instances = []
    const agent = new DeviceAgent({
      identityFile: join(dir, 'device-identity.json'),
      loadConfig: () => readDeviceAgentConfig(configFile),
      saveConfig: config => writeDeviceAgentConfig(configFile, config),
      serverUrl: () => (options.serverUrl === undefined ? 'https://ai.example' : options.serverUrl),
      approveExec: options.approve ?? (async () => 'allow'),
      audit: entry => audit.push(entry),
      appVersion: '0.7.21',
      fetchImpl,
      webSocketImpl: FakeSocket as unknown as typeof WebSocket,
      timing: { pollMs: 20, reconnectBaseMs: 20, reconnectMaxMs: 40 },
    })
    return { agent, requests, audit, configFile }
  }

  it('pairs, waits for approval, then opens a controllable peer socket declaring the enabled capabilities', async () => {
    const { agent, requests } = makeAgent({ linkStatuses: ['pending', 'approved'] })
    await agent.setConfig({ capabilities: { exec: true, files: true, browser: false, screen: false }, allowedFolders: ['/tmp/shared'] })
    const state = await agent.pair('https://ai.example/#/hermes/devices?pairing_code=pair-secret')
    expect(state.status).toBe('pending')
    expect(state.config.pairedServerUrl).toBe('https://ai.example')
    expect(requests[0].url).toBe('https://ai.example/api/devices/link-request')
    expect(requests[0].body.endpoint_kind).toBe('desktop')
    expect(requests[0].body.http_port).toBeUndefined()

    const deadline = Date.now() + 3000
    while (FakeSocket.instances.length === 0 && Date.now() < deadline) await wait(10)
    const socket = FakeSocket.instances[0]
    expect(socket).toBeDefined()
    const url = new URL(socket.url)
    expect(url.pathname).toBe('/api/devices/peer-socket')
    expect(url.searchParams.get('controllable')).toBe('1')
    expect(url.searchParams.get('capabilities')).toBe('exec,files')
    expect(url.searchParams.get('device_id')).toBe(state.deviceId)
    expect(url.searchParams.get('signature')).toBeTruthy()
    expect(agent.getState().status).toBe('connecting')

    socket.open()
    expect(agent.getState().status).toBe('connected')

    socket.emit('message', { data: JSON.stringify({ type: 'terminal.create', request_id: 'x' }) })
    expect(JSON.parse(socket.sent[0])).toMatchObject({ type: 'terminal.error', request_id: 'x' })
    agent.stop()
    expect(agent.getState().status).toBe('offline')
  })

  it('stays idle when the app is not linked to a server or the agent is disabled, and reports rejection', async () => {
    const local = makeAgent({ linkStatuses: ['approved'], serverUrl: null })
    await local.agent.start()
    expect(local.agent.getState().status).toBe('disabled')
    await expect(local.agent.pair('x')).rejects.toThrow(/not linked/)

    const rejected = makeAgent({ linkStatuses: ['rejected'] })
    await rejected.agent.pair('pair-secret')
    await wait(60)
    expect(rejected.agent.getState().status).toBe('rejected')
    expect(FakeSocket.instances).toHaveLength(0)
    rejected.agent.stop()
  })

  it('re-checks approval before reconnecting and stops reconnecting when the owner revokes the device', async () => {
    const { agent } = makeAgent({ linkStatuses: ['approved', 'none'] })
    await agent.pair('pair-secret')
    const deadline = Date.now() + 3000
    while (FakeSocket.instances.length === 0 && Date.now() < deadline) await wait(10)
    FakeSocket.instances[0].open()
    expect(agent.getState().status).toBe('connected')
    FakeSocket.instances[0].close()
    expect(agent.getState().status).toBe('offline')
    await wait(150)
    expect(agent.getState().status).toBe('unpaired')
    expect(FakeSocket.instances).toHaveLength(1)
    agent.stop()
  })

  it('allows a whole session after the user picks "allow for this session"', async () => {
    const approve = vi.fn(async () => 'allow-session' as const)
    const root = tempDir()
    const { agent } = makeAgent({ linkStatuses: ['approved'], approve })
    await agent.setConfig({ capabilities: { exec: true, files: false, browser: false, screen: false }, allowedFolders: [root] })
    await agent.pair('pair-secret')
    const deadline = Date.now() + 3000
    while (FakeSocket.instances.length === 0 && Date.now() < deadline) await wait(10)
    const socket = FakeSocket.instances[0]
    socket.open()
    for (const id of ['a', 'b']) {
      socket.emit('message', { data: JSON.stringify({ type: 'terminal.exec', request_id: id, command: process.execPath, args: ['-e', '0'] }) })
      const until = Date.now() + 5000
      while (!socket.sent.some(raw => JSON.parse(raw).request_id === id) && Date.now() < until) await wait(10)
    }
    expect(socket.sent.map(raw => JSON.parse(raw).type)).toEqual(['terminal.exec.result', 'terminal.exec.result'])
    expect(approve).toHaveBeenCalledTimes(1)
    expect(agent.getState().sessionApprovedAll).toBe(true)
    agent.stop()
    expect(agent.getState().sessionApprovedAll).toBe(false)
  })
})

describe('default workspace on first pairing', () => {
  it('creates ~/Core Hub, enables commands and files, and declares the workspace at handshake', () => {
    const mainSource = readFileSync(join(process.cwd(), 'packages/desktop/src/main/index.ts'), 'utf8')
    expect(mainSource).toContain("export const DEFAULT_DEVICE_WORKSPACE_DIR_NAME = 'Core Hub'")
    expect(mainSource).toContain('await ensureDefaultDeviceWorkspace(agent)')
    expect(mainSource).toContain('capabilities: { ...config.capabilities, exec: true, files: true }')
    const agentSource = readFileSync(join(process.cwd(), 'packages/desktop/src/main/device-agent/agent.ts'), 'utf8')
    expect(agentSource).toContain("url.searchParams.set('workspace', this.state.config.allowedFolders[0])")
  })
})
