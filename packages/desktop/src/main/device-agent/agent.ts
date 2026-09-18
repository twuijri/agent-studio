import { EventEmitter } from 'node:events'
import { randomUUID } from 'node:crypto'
import { arch, hostname, platform, release, type } from 'node:os'
import { loadOrCreateDeviceIdentity, signDeviceChallenge, type DeviceIdentity } from './identity'
import { DeviceAgentProtocol, type DeviceAgentAuditEntry, type DeviceAgentCapability, type DeviceAgentPolicy, type DeviceAgentProxyRequest, type DeviceAgentProxyResponse, type DeviceAgentScreenCapture } from './protocol'
import type { DeviceAgentConfig } from './store'

// The desktop Device Agent: pairs this machine with the linked Studio server
// (owner approval on the server's Devices page), then keeps an outbound
// WebSocket to the server's peer socket declaring `controllable=1`, so a
// server-side Hermes can run commands and exchange files here through the
// existing `ekko_studio_devices` toolset. The device never listens on a port.

export type DeviceAgentStatus =
  | 'disabled'
  | 'unpaired'
  | 'pending'
  | 'rejected'
  | 'blocked'
  | 'connecting'
  | 'connected'
  | 'offline'
  | 'error'

export type ExecApprovalDecision = 'allow' | 'allow-session' | 'deny'

export interface DeviceAgentState {
  status: DeviceAgentStatus
  serverUrl: string | null
  deviceId: string
  computerName: string
  config: DeviceAgentConfig
  lastError: string | null
  connectedAt: number | null
  reconnectAttempt: number
  sessionApprovedAll: boolean
  /** The user allowed screen access for this connection session. */
  screenSessionApproved: boolean
}

export interface DeviceAgentDependencies {
  identityFile: string
  loadConfig: () => DeviceAgentConfig
  saveConfig: (config: DeviceAgentConfig) => DeviceAgentConfig
  /** Studio server the desktop is linked to (connection mode); null when local. */
  serverUrl: () => string | null
  approveExec: (request: { command: string; args: string[]; cwd: string }) => Promise<ExecApprovalDecision>
  /** Ask once per session before the server may capture or drive the screen. */
  approveScreen?: () => Promise<boolean>
  browserProxy?: (request: DeviceAgentProxyRequest) => Promise<DeviceAgentProxyResponse>
  screen?: {
    capture: (options: { displayId?: string; maxWidth?: number }) => Promise<DeviceAgentScreenCapture>
    action: (action: Record<string, unknown>) => Promise<void>
  }
  audit: (entry: DeviceAgentAuditEntry) => void
  appVersion: string
  fetchImpl?: typeof fetch
  webSocketImpl?: typeof WebSocket
  log?: (message: string) => void
  /** Delay for reconnect/poll timers (tests shorten it). */
  timing?: { pollMs?: number; reconnectBaseMs?: number; reconnectMaxMs?: number }
}

type LinkStatus = 'none' | 'pending' | 'approved' | 'rejected' | 'blocked'

const DEFAULT_POLL_MS = 5000
const DEFAULT_RECONNECT_BASE_MS = 2000
const DEFAULT_RECONNECT_MAX_MS = 60_000

/** Accept a pairing link copied from the server's Devices page, or the bare code. */
export function extractPairingCode(input: string): string {
  const raw = input.trim()
  if (!raw) return ''
  if (!/^https?:\/\//i.test(raw) && !raw.includes('pairing_code=')) return raw
  try {
    const url = new URL(raw.includes('://') ? raw : `https://${raw}`)
    const direct = url.searchParams.get('pairing_code') || url.searchParams.get('pairingCode') || url.searchParams.get('code')
    if (direct) return direct.trim()
    const hashQuery = url.hash.includes('?') ? url.hash.slice(url.hash.indexOf('?') + 1) : ''
    const hashParams = new URLSearchParams(hashQuery)
    return (hashParams.get('pairing_code') || hashParams.get('pairingCode') || hashParams.get('code') || '').trim()
  } catch {
    const match = raw.match(/pairing_code=([^&#\s]+)/)
    return match ? decodeURIComponent(match[1]) : raw
  }
}

export function peerSocketUrl(serverUrl: string): string {
  const url = new URL(serverUrl)
  url.protocol = url.protocol === 'http:' ? 'ws:' : 'wss:'
  url.pathname = `${url.pathname.replace(/\/$/, '')}/api/devices/peer-socket`
  url.search = ''
  url.hash = ''
  return url.toString()
}

export class DeviceAgent extends EventEmitter {
  private identity: DeviceIdentity | null = null
  private ws: WebSocket | null = null
  private protocol: DeviceAgentProtocol | null = null
  private pollTimer: NodeJS.Timeout | null = null
  private reconnectTimer: NodeJS.Timeout | null = null
  private stopped = true
  private state: DeviceAgentState

  constructor(private readonly deps: DeviceAgentDependencies) {
    super()
    this.state = {
      status: 'disabled',
      serverUrl: deps.serverUrl(),
      deviceId: '',
      computerName: hostname(),
      config: deps.loadConfig(),
      lastError: null,
      connectedAt: null,
      reconnectAttempt: 0,
      sessionApprovedAll: false,
      screenSessionApproved: false,
    }
  }

  /** Withdraw screen access for the rest of this session (the on-screen "stop" control). */
  revokeScreenSession(): void {
    if (!this.state.screenSessionApproved) return
    this.setState({ screenSessionApproved: false })
    this.deps.audit({ at: Date.now(), kind: 'denied', detail: 'screen session stopped by the user', ok: false })
  }

  getState(): DeviceAgentState {
    return { ...this.state, config: { ...this.state.config, capabilities: { ...this.state.config.capabilities }, allowedFolders: [...this.state.config.allowedFolders] } }
  }

  private setState(patch: Partial<DeviceAgentState>) {
    this.state = { ...this.state, ...patch }
    this.emit('state', this.getState())
  }

  private log(message: string) {
    this.deps.log?.(`[device-agent] ${message}`)
  }

  private ensureIdentity(): DeviceIdentity {
    if (!this.identity) {
      this.identity = loadOrCreateDeviceIdentity(this.deps.identityFile)
      this.state.deviceId = this.identity.device_id
    }
    return this.identity
  }

  private signedFields() {
    const identity = this.ensureIdentity()
    const timestamp = Date.now()
    const nonce = randomUUID()
    return {
      device_id: identity.device_id,
      device_public_key: identity.device_public_key,
      timestamp,
      nonce,
      signature: signDeviceChallenge(identity, nonce, timestamp),
    }
  }

  private capabilityList(): DeviceAgentCapability[] {
    const list: DeviceAgentCapability[] = []
    if (this.state.config.capabilities.exec) list.push('exec')
    if (this.state.config.capabilities.files) list.push('files')
    if (this.state.config.capabilities.browser && this.deps.browserProxy) list.push('browser')
    if (this.state.config.capabilities.screen && this.deps.screen) list.push('screen')
    return list
  }

  private policy(): DeviceAgentPolicy {
    return {
      capabilities: this.capabilityList(),
      allowedFolders: [...this.state.config.allowedFolders],
      defaultCwd: this.state.config.allowedFolders[0],
      approveExec: async request => {
        if (this.state.config.approvalMode === 'always' || this.state.sessionApprovedAll) return true
        const decision = await this.deps.approveExec(request)
        if (decision === 'allow-session') this.setState({ sessionApprovedAll: true })
        return decision !== 'deny'
      },
      approveScreen: async () => {
        if (this.state.screenSessionApproved) return true
        if (this.state.config.approvalMode === 'always') {
          this.setState({ screenSessionApproved: true })
          return true
        }
        const approved = this.deps.approveScreen ? await this.deps.approveScreen() : false
        if (approved) this.setState({ screenSessionApproved: true })
        return approved
      },
    }
  }

  /** Apply the saved configuration and connect when paired and enabled. */
  async start(): Promise<void> {
    this.stopped = false
    this.ensureIdentity()
    const serverUrl = this.deps.serverUrl()
    this.setState({ serverUrl, config: this.deps.loadConfig(), lastError: null })
    if (!serverUrl) {
      this.setState({ status: 'disabled' })
      return
    }
    if (!this.state.config.enabled) {
      this.setState({ status: 'disabled' })
      return
    }
    if (!this.state.config.pairedServerUrl || this.state.config.pairedServerUrl !== serverUrl) {
      this.setState({ status: 'unpaired' })
      return
    }
    await this.refreshLinkStatus()
  }

  stop(): void {
    this.stopped = true
    this.clearTimers()
    this.closeSocket()
    this.setState({ status: this.state.config.enabled ? 'offline' : 'disabled', connectedAt: null, sessionApprovedAll: false, screenSessionApproved: false })
  }

  async setConfig(patch: Partial<Pick<DeviceAgentConfig, 'enabled' | 'capabilities' | 'allowedFolders' | 'approvalMode'>>): Promise<DeviceAgentState> {
    const next = this.deps.saveConfig({
      ...this.state.config,
      ...patch,
      capabilities: { ...this.state.config.capabilities, ...(patch.capabilities || {}) },
      allowedFolders: patch.allowedFolders ? [...patch.allowedFolders] : this.state.config.allowedFolders,
    })
    const capabilitiesChanged = JSON.stringify(next.capabilities) !== JSON.stringify(this.state.config.capabilities)
    this.setState({ config: next })
    if (!next.enabled) {
      this.stop()
      return this.getState()
    }
    if (this.stopped || capabilitiesChanged || this.state.status === 'disabled') {
      // Capabilities are declared at handshake time, so reconnect to publish them.
      this.clearTimers()
      this.closeSocket()
      await this.start()
    }
    return this.getState()
  }

  /** Send a pairing request to the linked server using a pairing link or code. */
  async pair(codeOrLink: string): Promise<DeviceAgentState> {
    const serverUrl = this.deps.serverUrl()
    if (!serverUrl) throw new Error('The app is not linked to a Studio server')
    const pairingCode = extractPairingCode(codeOrLink)
    if (!pairingCode) throw new Error('Enter the pairing link or code shown on the server')
    this.stopped = false
    this.clearTimers()
    this.closeSocket()

    const body = {
      ...this.signedFields(),
      computer_name: this.state.computerName,
      os: { type: type(), platform: platform(), release: release(), arch: arch() },
      hermes_agent_version: '',
      hermes_web_ui_version: `desktop ${this.deps.appVersion}`,
      endpoint_kind: 'desktop',
      controllable: true,
      capabilities: this.capabilityList(),
      pairing_code: pairingCode,
    }
    const response = await this.postJson(`${serverUrl}/api/devices/link-request`, body)
    if (response.status === 409) {
      // Duplicate pending request: keep waiting for the owner's decision.
    } else if (!response.ok) {
      const message = response.data?.error || `Pairing request failed (${response.status})`
      this.setState({ status: 'error', lastError: message })
      throw new Error(message)
    }
    const saved = this.deps.saveConfig({ ...this.state.config, enabled: true, pairedServerUrl: serverUrl, pairedAt: Date.now() })
    this.setState({ serverUrl, config: saved, lastError: null })
    const status: LinkStatus = response.status === 409 ? 'pending' : (response.data?.status as LinkStatus) || 'pending'
    this.applyLinkStatus(status)
    return this.getState()
  }

  /** Forget the pairing on this side; the server keeps its record until the owner deletes it. */
  unpair(): DeviceAgentState {
    this.stop()
    const saved = this.deps.saveConfig({ ...this.state.config, enabled: false, pairedServerUrl: null, pairedAt: null })
    this.setState({ config: saved, status: 'unpaired', lastError: null })
    return this.getState()
  }

  private async postJson(url: string, body: Record<string, unknown>): Promise<{ ok: boolean; status: number; data: any }> {
    const fetchImpl = this.deps.fetchImpl ?? fetch
    const response = await fetchImpl(url, {
      method: 'POST',
      headers: { 'content-type': 'application/json', accept: 'application/json' },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(15_000),
    })
    let data: any = null
    try { data = await response.json() } catch { data = null }
    return { ok: response.ok, status: response.status, data }
  }

  private async refreshLinkStatus(): Promise<void> {
    const serverUrl = this.state.serverUrl
    if (!serverUrl || this.stopped) return
    try {
      const response = await this.postJson(`${serverUrl}/api/devices/link-status`, this.signedFields())
      if (!response.ok) throw new Error(response.data?.error || `Status check failed (${response.status})`)
      this.applyLinkStatus((response.data?.status as LinkStatus) || 'none')
    } catch (err) {
      this.setState({ status: 'offline', lastError: err instanceof Error ? err.message : String(err) })
      this.schedulePoll()
    }
  }

  private applyLinkStatus(status: LinkStatus) {
    switch (status) {
      case 'approved':
        this.clearTimers()
        void this.connect()
        return
      case 'pending':
        this.setState({ status: 'pending', lastError: null })
        this.schedulePoll()
        return
      case 'rejected':
        this.setState({ status: 'rejected' })
        return
      case 'blocked':
        this.setState({ status: 'blocked' })
        return
      default:
        // The server has no record (deleted by the owner): pairing must be redone.
        this.setState({ status: 'unpaired' })
    }
  }

  private schedulePoll() {
    if (this.stopped || this.pollTimer) return
    this.pollTimer = setTimeout(() => {
      this.pollTimer = null
      void this.refreshLinkStatus()
    }, this.deps.timing?.pollMs ?? DEFAULT_POLL_MS)
    this.pollTimer.unref?.()
  }

  private clearTimers() {
    if (this.pollTimer) clearTimeout(this.pollTimer)
    if (this.reconnectTimer) clearTimeout(this.reconnectTimer)
    this.pollTimer = null
    this.reconnectTimer = null
  }

  private closeSocket() {
    this.protocol?.close()
    this.protocol = null
    const ws = this.ws
    this.ws = null
    if (ws) {
      try { ws.close() } catch { /* ignore */ }
    }
  }

  private async connect(): Promise<void> {
    if (this.stopped || !this.state.serverUrl) return
    const identity = this.ensureIdentity()
    const signed = this.signedFields()
    const url = new URL(peerSocketUrl(this.state.serverUrl))
    url.searchParams.set('device_id', identity.device_id)
    url.searchParams.set('device_public_key', identity.device_public_key)
    url.searchParams.set('computer_name', this.state.computerName)
    url.searchParams.set('timestamp', String(signed.timestamp))
    url.searchParams.set('nonce', signed.nonce)
    url.searchParams.set('signature', signed.signature)
    url.searchParams.set('controllable', '1')
    url.searchParams.set('capabilities', this.capabilityList().join(','))
    if (this.state.config.allowedFolders[0]) url.searchParams.set('workspace', this.state.config.allowedFolders[0])

    this.closeSocket()
    this.setState({ status: 'connecting', lastError: null })
    const WebSocketImpl = this.deps.webSocketImpl ?? WebSocket
    let ws: WebSocket
    try {
      ws = new WebSocketImpl(url.toString())
    } catch (err) {
      this.setState({ status: 'error', lastError: err instanceof Error ? err.message : String(err) })
      this.scheduleReconnect()
      return
    }
    this.ws = ws
    const protocol = new DeviceAgentProtocol({
      send: payload => {
        if (this.ws === ws && ws.readyState === 1) ws.send(JSON.stringify(payload))
      },
      policy: () => this.policy(),
      audit: entry => this.deps.audit(entry),
      browserProxy: this.deps.browserProxy,
      screen: this.deps.screen,
    })
    this.protocol = protocol

    ws.addEventListener('open', () => {
      if (this.ws !== ws) return
      this.log(`connected to ${this.state.serverUrl}`)
      this.setState({ status: 'connected', connectedAt: Date.now(), reconnectAttempt: 0, lastError: null })
    })
    ws.addEventListener('message', event => {
      if (this.ws !== ws) return
      const data = typeof event.data === 'string' ? event.data : Buffer.from(event.data as ArrayBuffer)
      protocol.handleRaw(data)
    })
    ws.addEventListener('error', () => {
      if (this.ws !== ws) return
      this.setState({ lastError: 'WebSocket error' })
    })
    ws.addEventListener('close', event => {
      if (this.ws !== ws) return
      protocol.close()
      this.ws = null
      this.protocol = null
      this.log(`disconnected (${event.code})`)
      this.setState({ status: 'offline', connectedAt: null, sessionApprovedAll: false, screenSessionApproved: false })
      if (!this.stopped) this.scheduleReconnect()
    })
  }

  private scheduleReconnect() {
    if (this.stopped || this.reconnectTimer) return
    const attempt = this.state.reconnectAttempt + 1
    const base = this.deps.timing?.reconnectBaseMs ?? DEFAULT_RECONNECT_BASE_MS
    const max = this.deps.timing?.reconnectMaxMs ?? DEFAULT_RECONNECT_MAX_MS
    const delay = Math.min(max, base * 2 ** Math.min(attempt - 1, 10))
    this.setState({ reconnectAttempt: attempt })
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null
      // Re-check approval first: a revoked device must not hammer the socket.
      void this.refreshLinkStatus()
    }, delay)
    this.reconnectTimer.unref?.()
  }
}
