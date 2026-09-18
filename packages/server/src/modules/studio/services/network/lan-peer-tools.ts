import { getLanPeerSocketManager, validateLanPeerPath, type LanPeerExecResult, type LanPeerProxyRequest, type LanPeerProxyResponse, type LanPeerScreenCapture, type LanPeerTerminalInfo, type LanPeerTerminalList, type LanPeerTerminalReadResult } from './lan-peer-socket'
import { readFile, writeFile } from 'fs/promises'

export type PeerToolUploadInput = {
  connectionId: string
  localPath: string
  remotePath: string
  timeoutMs?: number
}

export type PeerToolDownloadInput = {
  connectionId: string
  remotePath: string
  localPath: string
  timeoutMs?: number
}

export type PeerToolExecInput = {
  connectionId: string
  command: string
  args?: string[]
  cwd?: string
  timeoutMs?: number
}

export type PeerToolTerminalInput = {
  connectionId: string
  terminalId: string
}

export class LanPeerToolsService {
  listConnections() {
    return getLanPeerSocketManager().listConnections()
  }

  disconnect(connectionId: string): boolean {
    return getLanPeerSocketManager().disconnect(connectionId)
  }

  async createTerminal(connectionId: string, options: { shell?: string; cols?: number; rows?: number } = {}): Promise<LanPeerTerminalInfo> {
    return this.requireClientConnection(connectionId).createRemoteTerminal(options)
  }

  listTerminals(connectionId: string): LanPeerTerminalList {
    return this.requireClientConnection(connectionId).listTerminals()
  }

  writeTerminal(input: PeerToolTerminalInput & { data: string }) {
    this.requireClientConnection(input.connectionId).writeRemoteTerminal(input.terminalId, input.data)
    return { ok: true }
  }

  resizeTerminal(input: PeerToolTerminalInput & { cols: number; rows: number }) {
    this.requireClientConnection(input.connectionId).resizeRemoteTerminal(input.terminalId, input.cols, input.rows)
    return { ok: true }
  }

  closeTerminal(input: PeerToolTerminalInput) {
    this.requireClientConnection(input.connectionId).closeRemoteTerminal(input.terminalId)
    return { ok: true }
  }

  readTerminal(input: PeerToolTerminalInput): LanPeerTerminalReadResult {
    return this.requireClientConnection(input.connectionId).readRemoteTerminal(input.terminalId)
  }

  exec(input: PeerToolExecInput): Promise<LanPeerExecResult> {
    return this.requireClientConnection(input.connectionId).execRemoteCommand({
      command: input.command,
      args: input.args,
      cwd: input.cwd,
      timeoutMs: input.timeoutMs,
    })
  }

  async downloadFile(input: PeerToolDownloadInput) {
    const localPath = validateLanPeerPath(input.localPath)
    const data = await this.requireClientConnection(input.connectionId).downloadFileToBuffer(input.remotePath, input.timeoutMs)
    await writeFile(localPath, data)
    return {
      remote_path: input.remotePath,
      local_path: localPath,
      size: data.length,
    }
  }

  async uploadFile(input: PeerToolUploadInput) {
    const localPath = validateLanPeerPath(input.localPath)
    const data = await readFile(localPath)
    const result = await this.requireClientConnection(input.connectionId).uploadFileFromBuffer(
      input.remotePath,
      data,
      input.timeoutMs,
    )
    return {
      ...result,
      local_path: localPath,
      remote_path: input.remotePath,
    }
  }

  captureScreen(connectionId: string, options: { displayId?: string; maxWidth?: number } = {}): Promise<LanPeerScreenCapture> {
    return this.requireCapability(connectionId, 'screen').captureScreen(options)
  }

  screenAction(connectionId: string, action: Record<string, unknown>): Promise<{ ok: true }> {
    return this.requireCapability(connectionId, 'screen').screenAction(action)
  }

  proxyHttp(connectionId: string, request: LanPeerProxyRequest): Promise<LanPeerProxyResponse> {
    return this.requireCapability(connectionId, 'browser').proxyHttp(request)
  }

  private requireCapability(connectionId: string, capability: 'screen' | 'browser') {
    const connection = this.requireClientConnection(connectionId)
    const info = connection.info()
    if (!info.controllable || !info.capabilities.includes(capability)) {
      throw Object.assign(new Error(`The device did not enable the "${capability}" capability`), { status: 403 })
    }
    return connection
  }

  private requireConnection(connectionId: string) {
    const connection = getLanPeerSocketManager().getConnection(connectionId)
    if (!connection) throw Object.assign(new Error('Peer connection not found'), { status: 404 })
    return connection
  }

  private requireClientConnection(connectionId: string) {
    const connection = this.requireConnection(connectionId)
    const info = connection.info()
    // Remote tools run against peers this Studio dialled ('client' role) or
    // against peers that dialled in and declared themselves controllable
    // (the desktop Device Agent). Passive inbound peers stay off limits.
    if (info.role !== 'client' && !info.controllable) {
      throw Object.assign(new Error('Peer connection is not authorized for remote tools'), { status: 403 })
    }
    return connection
  }
}

let singleton: LanPeerToolsService | null = null

export function getLanPeerToolsService(): LanPeerToolsService {
  if (!singleton) singleton = new LanPeerToolsService()
  return singleton
}
