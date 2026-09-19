import { spawn, type ChildProcess } from 'node:child_process'
import { createReadStream, createWriteStream, existsSync, mkdirSync, statSync, type WriteStream } from 'node:fs'
import { dirname, isAbsolute, relative, resolve, sep } from 'node:path'

// Device-side handler for the Studio LAN peer protocol
// (packages/server/src/modules/studio/services/network/lan-peer-socket.ts).
// The desktop app opens the WebSocket, declares itself controllable, and this
// class answers the tool requests a server-side Hermes sends through the
// `ekko_studio_devices` toolset: command execution and file transfer.
// Interactive terminals (node-pty) are not bundled with the desktop app yet,
// so `terminal.*` requests are answered with the protocol's error message.
//
// Kept free of Electron imports so it can be unit-tested with a fake sender.

export type PeerMessage = {
  type?: string
  request_id?: string
  terminal_id?: string
  transfer_id?: string
  path?: string
  data?: string
  command?: string
  args?: string[]
  cwd?: string
  timeout_ms?: number
  [key: string]: unknown
}

export type DeviceAgentCapability = 'exec' | 'files' | 'browser' | 'screen' | 'apps'

export interface DeviceAgentPolicy {
  /** Capabilities the user enabled; anything else is refused. */
  capabilities: DeviceAgentCapability[]
  /** Folders the server may read/write and use as working directories. Empty = none. */
  allowedFolders: string[]
  /** Ask the user before running a command; resolves true to allow. */
  approveExec: (request: { command: string; args: string[]; cwd: string }) => Promise<boolean>
  /** Default working directory when the server does not send one (must be allowed). */
  defaultCwd?: string
  /** Ask the user once per session before the server may see or drive the screen. */
  approveScreen?: () => Promise<boolean>
  /** MCP apps shared with the server (`apps` capability). */
  apps?: SharedAppDefinition[]
  /** Ask the user before the server first uses an app in this session. */
  approveApp?: (app: SharedAppDefinition) => Promise<boolean>
}

export interface SharedAppDefinition {
  id: string
  name: string
  command: string
  args: string[]
  env: Record<string, string>
  cwd?: string
}

export interface DeviceAgentProxyRequest {
  method: string
  path: string
  headers: Record<string, string>
  body: Buffer
}

export interface DeviceAgentProxyResponse {
  status: number
  headers: Record<string, string>
  body: Buffer
}

export interface DeviceAgentScreenCapture {
  media_type: string
  data: string
  width: number
  height: number
  display_id: string
  displays: Array<{ id: string; width: number; height: number; scale: number; primary: boolean }>
}

export interface DeviceAgentAuditEntry {
  at: number
  kind: 'exec' | 'file.download' | 'file.upload' | 'terminal' | 'denied' | 'browser' | 'screen.capture' | 'screen.action' | 'app'
  detail: string
  ok: boolean
}

export interface DeviceAgentProtocolOptions {
  send: (payload: Record<string, unknown>) => void
  policy: () => DeviceAgentPolicy
  audit?: (entry: DeviceAgentAuditEntry) => void
  /** Forward a tunnelled HTTP request to the local browser broker (`browser` capability). */
  browserProxy?: (request: DeviceAgentProxyRequest) => Promise<DeviceAgentProxyResponse>
  /** Screen capture and input (`screen` capability). */
  screen?: {
    capture: (options: { displayId?: string; maxWidth?: number }) => Promise<DeviceAgentScreenCapture>
    action: (action: Record<string, unknown>) => Promise<void>
  }
  execOutputLimit?: number
  execTimeoutMs?: number
  maxExecTimeoutMs?: number
  fileChunkSize?: number
}

const DEFAULT_EXEC_OUTPUT_LIMIT = 5 * 1024 * 1024
const DEFAULT_EXEC_TIMEOUT_MS = 30_000
const DEFAULT_MAX_EXEC_TIMEOUT_MS = 10 * 60 * 1000
const DEFAULT_FILE_CHUNK_SIZE = 64 * 1024

export class PathNotAllowedError extends Error {
  constructor(path: string) {
    super(`Path is outside the folders shared with the server: ${path}`)
    this.name = 'PathNotAllowedError'
  }
}

/** Resolve a path and require it to live inside one of the allowed folders. */
export function resolveAllowedPath(input: string, allowedFolders: string[]): string {
  const candidate = resolve(input)
  for (const folder of allowedFolders) {
    const root = resolve(folder)
    const rel = relative(root, candidate)
    if (rel === '' || (!rel.startsWith('..') && !isAbsolute(rel) && !rel.split(sep).includes('..'))) return candidate
  }
  throw new PathNotAllowedError(input)
}

export class DeviceAgentProtocol {
  private readonly uploads = new Map<string, { path: string; stream: WriteStream }>()
  private readonly children = new Set<ChildProcess>()
  /** Running MCP app processes keyed by the server's session id. */
  private readonly appSessions = new Map<string, { child: ChildProcess; appId: string }>()
  private closed = false

  constructor(private readonly options: DeviceAgentProtocolOptions) {}

  /** Handle one raw WebSocket text frame from the server. */
  handleRaw(raw: string | Buffer): void {
    let msg: PeerMessage | null = null
    try {
      const parsed = JSON.parse(typeof raw === 'string' ? raw : raw.toString('utf8'))
      if (parsed && typeof parsed === 'object') msg = parsed as PeerMessage
    } catch {
      msg = null
    }
    if (!msg?.type) {
      this.send({ type: 'error', message: 'Invalid peer message' })
      return
    }
    this.handle(msg)
  }

  handle(msg: PeerMessage): void {
    if (this.closed) return
    switch (msg.type) {
      case 'peer.ready':
      case 'terminal.data':
      case 'terminal.exit':
      case 'file.download.started':
      case 'file.download.chunk':
      case 'file.download.complete':
      case 'file.upload.ready':
      case 'terminal.created':
      case 'terminal.exec.result':
      case 'error':
        // Responses or notifications only a controlling side would emit; the
        // device never asks the server for anything, so ignore them.
        return
      case 'terminal.create':
        this.refuseTerminal(msg, 'terminal.error')
        return
      case 'terminal.input':
      case 'terminal.resize':
      case 'terminal.close':
        return
      case 'terminal.exec':
        void this.exec(msg)
        return
      case 'file.download':
        this.download(msg)
        return
      case 'file.upload.start':
        this.startUpload(msg)
        return
      case 'file.upload.chunk':
        this.writeUploadChunk(msg)
        return
      case 'file.upload.complete':
        this.completeUpload(msg)
        return
      case 'http.proxy':
        void this.proxyHttp(msg)
        return
      case 'screen.capture':
        void this.captureScreen(msg)
        return
      case 'screen.action':
        void this.screenAction(msg)
        return
      case 'mcp.open':
        void this.openApp(msg)
        return
      case 'mcp.data':
        this.writeApp(msg)
        return
      case 'mcp.close':
        this.closeApp(msg)
        return
      default:
        this.send({ type: 'error', request_id: msg.request_id, message: `Unsupported peer message: ${msg.type}` })
    }
  }

  close(): void {
    if (this.closed) return
    this.closed = true
    for (const child of this.children) {
      try { child.kill() } catch { /* ignore */ }
    }
    this.children.clear()
    for (const upload of this.uploads.values()) {
      try { upload.stream.destroy() } catch { /* ignore */ }
    }
    this.uploads.clear()
    for (const session of this.appSessions.values()) {
      try { session.child.kill() } catch { /* ignore */ }
    }
    this.appSessions.clear()
  }

  /** Number of MCP app processes currently running for the server. */
  get openAppSessions(): number {
    return this.appSessions.size
  }

  private async openApp(msg: PeerMessage): Promise<void> {
    const policy = this.options.policy()
    const sessionId = typeof msg.session_id === 'string' ? msg.session_id : ''
    const appId = typeof msg.app_id === 'string' ? msg.app_id : ''
    const fail = (message: string) => this.send({ type: 'mcp.error', request_id: msg.request_id, session_id: sessionId, message })
    if (!sessionId || !appId) {
      fail('Missing app or session id')
      return
    }
    const app = policy.capabilities.includes('apps') ? (policy.apps || []).find(item => item.id === appId) : undefined
    if (!app) {
      this.audit({ at: Date.now(), kind: 'denied', detail: `app not shared: ${appId}`, ok: false })
      fail('This app is not shared with the server')
      return
    }
    if (this.appSessions.has(sessionId)) {
      fail('Session already open')
      return
    }
    let approved = true
    if (policy.approveApp) {
      try { approved = await policy.approveApp(app) } catch { approved = false }
    }
    if (!approved) {
      this.audit({ at: Date.now(), kind: 'denied', detail: `user denied app: ${app.name}`, ok: false })
      fail('The device owner denied access to this app')
      return
    }
    if (this.closed) return
    let child: ChildProcess
    try {
      child = spawn(app.command, app.args, {
        cwd: app.cwd && existsSync(app.cwd) ? app.cwd : undefined,
        env: { ...process.env, ...app.env },
        shell: false,
        windowsHide: true,
        stdio: ['pipe', 'pipe', 'pipe'],
      })
    } catch (err) {
      this.audit({ at: Date.now(), kind: 'app', detail: `${app.name}: ${err instanceof Error ? err.message : String(err)}`, ok: false })
      fail(err instanceof Error ? err.message : 'Failed to start the app')
      return
    }
    this.appSessions.set(sessionId, { child, appId })
    this.audit({ at: Date.now(), kind: 'app', detail: `${app.name} session opened`, ok: true })
    child.stdout?.on('data', (chunk: Buffer) => {
      if (!this.appSessions.has(sessionId)) return
      this.send({ type: 'mcp.data', session_id: sessionId, data: Buffer.from(chunk).toString('base64') })
    })
    child.stderr?.on('data', (chunk: Buffer) => {
      const text = Buffer.from(chunk).toString('utf8').trim()
      if (text) this.send({ type: 'mcp.stderr', session_id: sessionId, data: text.slice(0, 4000) })
    })
    child.on('error', err => {
      if (!this.appSessions.has(sessionId)) return
      this.appSessions.delete(sessionId)
      this.audit({ at: Date.now(), kind: 'app', detail: `${app.name}: ${err.message}`, ok: false })
      this.send({ type: 'mcp.exit', session_id: sessionId, code: null, message: err.message })
    })
    child.on('close', code => {
      if (!this.appSessions.has(sessionId)) return
      this.appSessions.delete(sessionId)
      this.audit({ at: Date.now(), kind: 'app', detail: `${app.name} session closed (exit ${code})`, ok: true })
      this.send({ type: 'mcp.exit', session_id: sessionId, code })
    })
    this.send({ type: 'mcp.opened', request_id: msg.request_id, session_id: sessionId, pid: child.pid })
  }

  private writeApp(msg: PeerMessage) {
    const session = typeof msg.session_id === 'string' ? this.appSessions.get(msg.session_id) : undefined
    if (!session || typeof msg.data !== 'string') return
    try { session.child.stdin?.write(Buffer.from(msg.data, 'base64')) } catch { /* process gone; exit event follows */ }
  }

  private closeApp(msg: PeerMessage) {
    const sessionId = typeof msg.session_id === 'string' ? msg.session_id : ''
    const session = this.appSessions.get(sessionId)
    if (!session) return
    this.appSessions.delete(sessionId)
    try { session.child.stdin?.end() } catch { /* ignore */ }
    try { session.child.kill() } catch { /* ignore */ }
  }

  private send(payload: Record<string, unknown>) {
    if (this.closed) return
    this.options.send(payload)
  }

  private audit(entry: DeviceAgentAuditEntry) {
    try { this.options.audit?.(entry) } catch { /* never let auditing break the protocol */ }
  }

  private refuseTerminal(msg: PeerMessage, type: 'terminal.error') {
    const message = 'Interactive terminals are not available on this device; use command execution instead'
    this.audit({ at: Date.now(), kind: 'terminal', detail: 'terminal.create', ok: false })
    this.send({ type, request_id: msg.request_id, message })
  }

  private resolveCwd(policy: DeviceAgentPolicy, requested: string | undefined): string {
    if (requested) return resolveAllowedPath(requested, policy.allowedFolders)
    if (policy.defaultCwd) return resolveAllowedPath(policy.defaultCwd, policy.allowedFolders)
    if (policy.allowedFolders.length > 0) return resolve(policy.allowedFolders[0])
    throw new PathNotAllowedError('(no shared folder)')
  }

  private async exec(msg: PeerMessage): Promise<void> {
    const policy = this.options.policy()
    const command = typeof msg.command === 'string' ? msg.command.trim() : ''
    const args = Array.isArray(msg.args) ? msg.args.filter((arg): arg is string => typeof arg === 'string') : []
    const fail = (message: string) => this.send({ type: 'terminal.exec.error', request_id: msg.request_id, message })
    const label = [command, ...args].join(' ')

    if (!policy.capabilities.includes('exec')) {
      this.audit({ at: Date.now(), kind: 'denied', detail: `exec disabled: ${label}`, ok: false })
      fail('Command execution is disabled on this device')
      return
    }
    if (!command) {
      fail('Missing command')
      return
    }

    let cwd: string
    try {
      cwd = this.resolveCwd(policy, msg.cwd)
      if (!existsSync(cwd)) cwd = resolve(policy.allowedFolders[0] || cwd)
    } catch (err) {
      this.audit({ at: Date.now(), kind: 'denied', detail: `cwd refused: ${msg.cwd}`, ok: false })
      fail(err instanceof Error ? err.message : 'Invalid cwd')
      return
    }

    let approved = false
    try {
      approved = await policy.approveExec({ command, args, cwd })
    } catch {
      approved = false
    }
    if (!approved) {
      this.audit({ at: Date.now(), kind: 'denied', detail: `user denied: ${label}`, ok: false })
      fail('The device owner denied this command')
      return
    }
    if (this.closed) return

    const outputLimit = this.options.execOutputLimit ?? DEFAULT_EXEC_OUTPUT_LIMIT
    const timeoutMs = Math.max(1000, Math.min(Number(msg.timeout_ms) || (this.options.execTimeoutMs ?? DEFAULT_EXEC_TIMEOUT_MS), this.options.maxExecTimeoutMs ?? DEFAULT_MAX_EXEC_TIMEOUT_MS))
    const stdout: Buffer[] = []
    const stderr: Buffer[] = []
    let stdoutLength = 0
    let stderrLength = 0
    let settled = false
    let timedOut = false

    let child: ChildProcess
    try {
      child = spawn(command, args, { cwd, shell: false, windowsHide: true })
    } catch (err) {
      fail(err instanceof Error ? err.message : 'Failed to start command')
      return
    }
    this.children.add(child)

    const append = (chunks: Buffer[], current: number, chunk: Buffer): number => {
      if (current >= outputLimit) return current
      const next = chunk.subarray(0, outputLimit - current)
      chunks.push(next)
      return current + next.length
    }
    const finish = (exitCode: number | null) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      this.children.delete(child)
      this.audit({ at: Date.now(), kind: 'exec', detail: `${label} (cwd ${cwd}) → ${timedOut ? 'timeout' : `exit ${exitCode}`}`, ok: !timedOut && exitCode === 0 })
      this.send({
        type: 'terminal.exec.result',
        request_id: msg.request_id,
        stdout: Buffer.concat(stdout).toString('utf8'),
        stderr: Buffer.concat(stderr).toString('utf8'),
        exit_code: exitCode,
        timed_out: timedOut,
      })
    }
    const timer = setTimeout(() => {
      timedOut = true
      try { child.kill() } catch { /* ignore */ }
      finish(null)
    }, timeoutMs)
    timer.unref?.()

    child.stdout?.on('data', chunk => { stdoutLength = append(stdout, stdoutLength, Buffer.from(chunk)) })
    child.stderr?.on('data', chunk => { stderrLength = append(stderr, stderrLength, Buffer.from(chunk)) })
    child.on('error', err => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      this.children.delete(child)
      this.audit({ at: Date.now(), kind: 'exec', detail: `${label}: ${err.message}`, ok: false })
      fail(err.message)
    })
    child.on('close', code => finish(code))
  }

  private async proxyHttp(msg: PeerMessage): Promise<void> {
    const policy = this.options.policy()
    const fail = (message: string, status = 502) => this.send({ type: 'http.proxy.error', request_id: msg.request_id, status, message })
    if (!policy.capabilities.includes('browser') || !this.options.browserProxy) {
      this.audit({ at: Date.now(), kind: 'denied', detail: 'browser disabled', ok: false })
      fail('The device browser is not shared with the server', 403)
      return
    }
    const path = typeof msg.path === 'string' ? msg.path : ''
    if (!path.startsWith('/v1')) {
      fail('Only browser broker requests can be tunnelled', 403)
      return
    }
    const headers = msg.headers && typeof msg.headers === 'object'
      ? Object.fromEntries(Object.entries(msg.headers as Record<string, unknown>).map(([k, v]) => [k.toLowerCase(), String(v)]))
      : {}
    try {
      const response = await this.options.browserProxy({
        method: typeof msg.method === 'string' ? msg.method : 'POST',
        path,
        headers,
        body: typeof msg.body === 'string' && msg.body ? Buffer.from(msg.body, 'base64') : Buffer.alloc(0),
      })
      if (this.closed) return
      let detail = path
      if (path === '/v1') {
        try { detail = `${path} ${String(JSON.parse(response.body.toString('utf8'))?.method || JSON.parse(Buffer.from(String(msg.body || ''), 'base64').toString('utf8'))?.method || '')}`.trim() } catch { /* keep path */ }
      }
      this.audit({ at: Date.now(), kind: 'browser', detail, ok: response.status < 400 })
      this.send({
        type: 'http.proxy.result',
        request_id: msg.request_id,
        status: response.status,
        headers: response.headers,
        body: response.body.length ? response.body.toString('base64') : '',
      })
    } catch (err) {
      this.audit({ at: Date.now(), kind: 'browser', detail: `${path}: ${err instanceof Error ? err.message : String(err)}`, ok: false })
      fail(err instanceof Error ? err.message : 'Browser request failed')
    }
  }

  private async screenPermitted(msg: PeerMessage, policy: DeviceAgentPolicy): Promise<boolean> {
    const fail = (message: string) => this.send({ type: 'screen.error', request_id: msg.request_id, message })
    if (!policy.capabilities.includes('screen') || !this.options.screen) {
      this.audit({ at: Date.now(), kind: 'denied', detail: `screen disabled: ${msg.type}`, ok: false })
      fail('Screen access is disabled on this device')
      return false
    }
    let approved = true
    if (policy.approveScreen) {
      try { approved = await policy.approveScreen() } catch { approved = false }
    }
    if (!approved) {
      this.audit({ at: Date.now(), kind: 'denied', detail: `user denied screen: ${msg.type}`, ok: false })
      fail('The device owner denied screen access')
      return false
    }
    return !this.closed
  }

  private async captureScreen(msg: PeerMessage): Promise<void> {
    const policy = this.options.policy()
    if (!(await this.screenPermitted(msg, policy))) return
    try {
      const capture = await this.options.screen!.capture({
        displayId: typeof msg.display_id === 'string' ? msg.display_id : undefined,
        maxWidth: typeof msg.max_width === 'number' ? msg.max_width : undefined,
      })
      this.audit({ at: Date.now(), kind: 'screen.capture', detail: `${capture.width}x${capture.height}`, ok: true })
      this.send({ type: 'screen.capture.result', request_id: msg.request_id, ...capture })
    } catch (err) {
      this.audit({ at: Date.now(), kind: 'screen.capture', detail: err instanceof Error ? err.message : String(err), ok: false })
      this.send({ type: 'screen.error', request_id: msg.request_id, message: err instanceof Error ? err.message : 'Screen capture failed' })
    }
  }

  private async screenAction(msg: PeerMessage): Promise<void> {
    const policy = this.options.policy()
    if (!(await this.screenPermitted(msg, policy))) return
    const { type: _type, request_id: _id, ...action } = msg
    const label = `${String(action.action)}${typeof action.x === 'number' ? ` @${action.x},${action.y}` : ''}${typeof action.key === 'string' ? ` ${action.key}` : ''}`
    try {
      await this.options.screen!.action(action)
      this.audit({ at: Date.now(), kind: 'screen.action', detail: label, ok: true })
      this.send({ type: 'screen.action.result', request_id: msg.request_id, ok: true })
    } catch (err) {
      this.audit({ at: Date.now(), kind: 'screen.action', detail: `${label}: ${err instanceof Error ? err.message : String(err)}`, ok: false })
      this.send({ type: 'screen.error', request_id: msg.request_id, message: err instanceof Error ? err.message : 'Screen action failed' })
    }
  }

  private allowedFilePath(msg: PeerMessage, policy: DeviceAgentPolicy): string | null {
    const fileError = (message: string) => this.send({ type: 'file.error', request_id: msg.request_id, transfer_id: msg.transfer_id, message })
    if (!policy.capabilities.includes('files')) {
      this.audit({ at: Date.now(), kind: 'denied', detail: `files disabled: ${msg.path}`, ok: false })
      fileError('File access is disabled on this device')
      return null
    }
    if (!msg.transfer_id || !msg.path) {
      fileError('Missing file path')
      return null
    }
    try {
      return resolveAllowedPath(msg.path, policy.allowedFolders)
    } catch (err) {
      this.audit({ at: Date.now(), kind: 'denied', detail: `path refused: ${msg.path}`, ok: false })
      fileError(err instanceof Error ? err.message : 'Invalid file path')
      return null
    }
  }

  private download(msg: PeerMessage) {
    const filePath = this.allowedFilePath(msg, this.options.policy())
    if (!filePath) return
    let size = 0
    try {
      const info = statSync(filePath)
      if (!info.isFile()) throw new Error('Not a file')
      size = info.size
    } catch (err) {
      this.send({ type: 'file.error', request_id: msg.request_id, transfer_id: msg.transfer_id, message: err instanceof Error ? err.message : 'File not found' })
      return
    }
    // Optional byte range (offset/length) so the server can stream media with
    // HTTP range support without pulling the whole file for every seek.
    const offset = typeof (msg as any).offset === 'number' && Number.isFinite((msg as any).offset) ? Math.max(0, Math.floor((msg as any).offset)) : 0
    const length = typeof (msg as any).length === 'number' && Number.isFinite((msg as any).length) ? Math.max(0, Math.floor((msg as any).length)) : null
    if (offset > size || (length !== null && length === 0 && size > 0 && offset >= size)) {
      this.send({ type: 'file.error', request_id: msg.request_id, transfer_id: msg.transfer_id, message: 'Range not satisfiable' })
      return
    }
    const end = length === null ? size - 1 : Math.min(size - 1, offset + length - 1)
    const chunkSize = this.options.fileChunkSize ?? DEFAULT_FILE_CHUNK_SIZE
    const stream = size === 0 || end < offset
      ? createReadStream(filePath, { highWaterMark: chunkSize, start: 0, end: -1 })
      : createReadStream(filePath, { highWaterMark: chunkSize, start: offset, end })
    this.send({ type: 'file.download.started', request_id: msg.request_id, transfer_id: msg.transfer_id, size, offset, length: end < offset ? 0 : end - offset + 1 })
    stream.on('data', chunk => {
      this.send({ type: 'file.download.chunk', transfer_id: msg.transfer_id, data: Buffer.from(chunk).toString('base64') })
    })
    stream.on('error', err => {
      this.audit({ at: Date.now(), kind: 'file.download', detail: `${filePath}: ${err.message}`, ok: false })
      this.send({ type: 'file.error', transfer_id: msg.transfer_id, message: err.message })
    })
    stream.on('end', () => {
      this.audit({ at: Date.now(), kind: 'file.download', detail: filePath, ok: true })
      this.send({ type: 'file.download.complete', transfer_id: msg.transfer_id })
    })
  }

  private startUpload(msg: PeerMessage) {
    const filePath = this.allowedFilePath(msg, this.options.policy())
    if (!filePath) return
    try {
      mkdirSync(dirname(filePath), { recursive: true })
    } catch (err) {
      this.send({ type: 'file.error', request_id: msg.request_id, transfer_id: msg.transfer_id, message: err instanceof Error ? err.message : 'Cannot create folder' })
      return
    }
    const stream = createWriteStream(filePath)
    const transferId = msg.transfer_id as string
    stream.on('error', err => {
      this.uploads.delete(transferId)
      this.audit({ at: Date.now(), kind: 'file.upload', detail: `${filePath}: ${err.message}`, ok: false })
      this.send({ type: 'file.error', transfer_id: transferId, message: err.message })
    })
    this.uploads.set(transferId, { path: filePath, stream })
    this.send({ type: 'file.upload.ready', request_id: msg.request_id, transfer_id: transferId })
  }

  private writeUploadChunk(msg: PeerMessage) {
    const upload = msg.transfer_id ? this.uploads.get(msg.transfer_id) : null
    if (!upload || typeof msg.data !== 'string') return
    upload.stream.write(Buffer.from(msg.data, 'base64'))
  }

  private completeUpload(msg: PeerMessage) {
    const upload = msg.transfer_id ? this.uploads.get(msg.transfer_id) : null
    if (!upload) return
    upload.stream.end(() => {
      this.uploads.delete(msg.transfer_id as string)
      this.audit({ at: Date.now(), kind: 'file.upload', detail: upload.path, ok: true })
      this.send({ type: 'file.upload.complete', request_id: msg.request_id, transfer_id: msg.transfer_id, path: upload.path })
    })
  }
}
