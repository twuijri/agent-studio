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

export type DeviceAgentCapability = 'exec' | 'files'

export interface DeviceAgentPolicy {
  /** Capabilities the user enabled; anything else is refused. */
  capabilities: DeviceAgentCapability[]
  /** Folders the server may read/write and use as working directories. Empty = none. */
  allowedFolders: string[]
  /** Ask the user before running a command; resolves true to allow. */
  approveExec: (request: { command: string; args: string[]; cwd: string }) => Promise<boolean>
  /** Default working directory when the server does not send one (must be allowed). */
  defaultCwd?: string
}

export interface DeviceAgentAuditEntry {
  at: number
  kind: 'exec' | 'file.download' | 'file.upload' | 'terminal' | 'denied'
  detail: string
  ok: boolean
}

export interface DeviceAgentProtocolOptions {
  send: (payload: Record<string, unknown>) => void
  policy: () => DeviceAgentPolicy
  audit?: (entry: DeviceAgentAuditEntry) => void
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
    try {
      if (!statSync(filePath).isFile()) throw new Error('Not a file')
    } catch (err) {
      this.send({ type: 'file.error', request_id: msg.request_id, transfer_id: msg.transfer_id, message: err instanceof Error ? err.message : 'File not found' })
      return
    }
    const chunkSize = this.options.fileChunkSize ?? DEFAULT_FILE_CHUNK_SIZE
    const stream = createReadStream(filePath, { highWaterMark: chunkSize })
    this.send({ type: 'file.download.started', request_id: msg.request_id, transfer_id: msg.transfer_id })
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
