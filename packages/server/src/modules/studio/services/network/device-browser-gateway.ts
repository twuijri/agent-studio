import { createServer, type IncomingMessage, type Server, type ServerResponse } from 'http'
import { randomBytes, randomUUID, timingSafeEqual } from 'crypto'
import { chmod, mkdir, rm, writeFile } from 'fs/promises'
import { join } from 'path'
import { config } from '../../public/config'
import { logger } from '../../public/logging'
import { getLanPeerSocketManager, type LanPeerConnectionInfo } from './lan-peer-socket'
import { getDeviceBinding, isDeviceAllowedForProfile } from '../devices/device-bindings'

// Device Browser Gateway (phase 3 of docs/DESKTOP-SERVER-MODE.md).
//
// The bundled `ekko-studio-mcp browser` toolset only knows how to talk to a
// Desktop Browser Broker on 127.0.0.1 described by
// `<app home>/desktop-browser/broker.json`. On a server there is no desktop,
// so this gateway impersonates that broker: it writes a compatible descriptor
// while at least one controllable device with the `browser` capability is
// connected, and tunnels every `/v1` request over that device's peer socket
// to the real broker inside the desktop app. Hermes therefore drives the
// user's desktop browser without any change to the browser toolset.
//
// Routing: the `X-Hermes-Profile` header (sent by the MCP toolset) selects a
// device bound to that profile; otherwise the first browser-capable device.
// Session tokens issued by a device broker are remembered so later calls
// always reach the device that issued them.

const SESSION_PATH = '/v1/session'
const OPERATION_PATH = '/v1'
const MAX_BODY_BYTES = 8 * 1024 * 1024
const SESSION_TTL_MS = 12 * 60 * 60 * 1000

type GatewaySession = { connectionId: string; deviceId: string; expiresAt: number }

export type GatewayProxyResponse = { status: number; headers: Record<string, string>; body: Buffer }

function safeEqual(a: string, b: string): boolean {
  const left = Buffer.from(a)
  const right = Buffer.from(b)
  return left.length === right.length && timingSafeEqual(left, right)
}

function readBody(req: IncomingMessage): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = []
    let size = 0
    req.on('data', chunk => {
      size += chunk.length
      if (size > MAX_BODY_BYTES) {
        reject(new Error('Request body too large'))
        req.destroy()
        return
      }
      chunks.push(Buffer.from(chunk))
    })
    req.on('end', () => resolve(Buffer.concat(chunks)))
    req.on('error', reject)
  })
}

export class DeviceBrowserGateway {
  private server: Server | null = null
  private token = ''
  private endpoint = ''
  private instanceId = ''
  private descriptorWritten = false
  private readonly sessions = new Map<string, GatewaySession>()
  private unsubscribe: (() => void) | null = null
  private refreshing: Promise<void> | null = null

  constructor(private readonly options: { appHome?: () => string } = {}) {}

  private descriptorDir(): string {
    return join((this.options.appHome ?? (() => config.appHome))(), 'desktop-browser')
  }

  private descriptorPath(): string {
    return join(this.descriptorDir(), 'broker.json')
  }

  isRunning(): boolean {
    return !!this.server
  }

  descriptorInfo(): { endpoint: string; token: string; instanceId: string; written: boolean } {
    return { endpoint: this.endpoint, token: this.token, instanceId: this.instanceId, written: this.descriptorWritten }
  }

  async start(): Promise<void> {
    if (this.server) return
    this.token = randomBytes(32).toString('base64url')
    this.instanceId = randomUUID()
    const server = createServer((req, res) => { void this.handle(req, res) })
    server.on('clientError', (_error, socket) => socket.destroy())
    await new Promise<void>((resolve, reject) => {
      server.once('error', reject)
      server.listen(0, '127.0.0.1', () => resolve())
    })
    const address = server.address()
    if (!address || typeof address === 'string') {
      server.close()
      throw new Error('Device browser gateway did not bind a TCP port')
    }
    this.server = server
    this.endpoint = `http://127.0.0.1:${address.port}${OPERATION_PATH}`
    this.unsubscribe = getLanPeerSocketManager().subscribe(() => { void this.refresh() })
    await this.refresh()
  }

  async stop(): Promise<void> {
    this.unsubscribe?.()
    this.unsubscribe = null
    const server = this.server
    this.server = null
    this.sessions.clear()
    if (server) {
      await new Promise<void>(resolve => {
        server.close(() => resolve())
        server.closeAllConnections?.()
      })
    }
    await this.removeDescriptor()
  }

  browserDevices(): LanPeerConnectionInfo[] {
    return getLanPeerSocketManager().listConnections().filter(connection => connection.controllable && connection.capabilities.includes('browser'))
  }

  /** Write or remove the broker descriptor depending on connected browser-capable devices. */
  async refresh(): Promise<void> {
    if (this.refreshing) await this.refreshing
    this.refreshing = (async () => {
      if (!this.server) return
      const available = this.browserDevices().length > 0
      if (available && !this.descriptorWritten) await this.writeDescriptor()
      if (!available && this.descriptorWritten) await this.removeDescriptor()
      for (const [token, session] of this.sessions) {
        if (session.expiresAt < Date.now() || !getLanPeerSocketManager().getConnection(session.connectionId)) this.sessions.delete(token)
      }
    })().catch(error => logger.warn(error, '[device-browser-gateway] refresh failed')).finally(() => { this.refreshing = null })
    await this.refreshing
  }

  private async writeDescriptor(): Promise<void> {
    const dir = this.descriptorDir()
    await mkdir(dir, { recursive: true, mode: 0o700 })
    await chmod(dir, 0o700).catch(() => undefined)
    const descriptor = {
      schema: 1,
      desktopPid: process.pid,
      endpoint: this.endpoint,
      token: this.token,
      instanceId: this.instanceId,
      createdAt: new Date().toISOString(),
      gateway: 'device-browser',
    }
    const path = this.descriptorPath()
    await writeFile(path, JSON.stringify(descriptor, null, 2), { encoding: 'utf8', mode: 0o600 })
    await chmod(path, 0o600).catch(() => undefined)
    this.descriptorWritten = true
    logger.info({ endpoint: this.endpoint }, '[device-browser-gateway] descriptor published for connected device browsers')
  }

  private async removeDescriptor(): Promise<void> {
    if (!this.descriptorWritten) return
    this.descriptorWritten = false
    await rm(this.descriptorPath(), { force: true }).catch(() => undefined)
    logger.info('[device-browser-gateway] descriptor removed; no device browser connected')
  }

  private selectDevice(profile: string): LanPeerConnectionInfo | null {
    const devices = this.browserDevices()
    if (devices.length === 0) return null
    const allowed = devices.filter(device => isDeviceAllowedForProfile(device.device_id, profile))
    // A device explicitly bound to the profile wins over devices open to everyone.
    const bound = profile ? allowed.filter(device => getDeviceBinding(device.device_id).includes(profile)) : []
    return bound[0] || allowed[0] || null
  }

  private send(res: ServerResponse, status: number, payload: unknown): void {
    const body = Buffer.from(JSON.stringify(payload))
    res.writeHead(status, { 'content-type': 'application/json', 'content-length': String(body.length) })
    res.end(body)
  }

  private async handle(req: IncomingMessage, res: ServerResponse): Promise<void> {
    try {
      const path = (req.url || '').split('?')[0]
      if (req.method !== 'POST' || (path !== SESSION_PATH && path !== OPERATION_PATH)) {
        this.send(res, 404, { error: 'Not found' })
        return
      }
      const authorization = String(req.headers.authorization || '')
      const provided = authorization.startsWith('Bearer ') ? authorization.slice(7) : ''
      const profile = String(req.headers['x-hermes-profile'] || '').trim()
      const body = await readBody(req)
      const manager = getLanPeerSocketManager()

      let connectionId: string
      if (path === SESSION_PATH) {
        if (!provided || !safeEqual(provided, this.token)) {
          this.send(res, 401, { error: 'Unauthorized' })
          return
        }
        const device = this.selectDevice(profile)
        if (!device) {
          this.send(res, 503, { error: profile ? `No linked device with a browser is available to profile "${profile}"` : 'No linked device with a browser is connected' })
          return
        }
        connectionId = device.id
      } else {
        const session = provided ? this.sessions.get(provided) : undefined
        if (!session || session.expiresAt < Date.now()) {
          this.send(res, 401, { error: 'Unknown browser session; open a new session' })
          return
        }
        connectionId = session.connectionId
      }

      const connection = manager.getConnection(connectionId)
      if (!connection || !connection.info().controllable) {
        this.send(res, 503, { error: 'The linked device disconnected' })
        return
      }

      const forwardHeaders: Record<string, string> = {}
      for (const [name, value] of Object.entries(req.headers)) {
        if (name === 'host' || name === 'content-length' || name === 'connection' || value === undefined) continue
        forwardHeaders[name] = Array.isArray(value) ? value.join(', ') : String(value)
      }
      const upstream = await connection.proxyHttp({ method: 'POST', path, headers: forwardHeaders, body })

      if (path === SESSION_PATH && upstream.status >= 200 && upstream.status < 300) {
        try {
          const payload = JSON.parse(upstream.body.toString('utf8'))
          if (typeof payload?.session_token === 'string' && payload.session_token) {
            this.sessions.set(payload.session_token, { connectionId, deviceId: connection.deviceId, expiresAt: Date.now() + SESSION_TTL_MS })
          }
        } catch {
          // The device broker answered with something unexpected; pass it through untouched.
        }
      }

      const headers: Record<string, string> = { 'content-length': String(upstream.body.length) }
      const contentType = upstream.headers['content-type']
      if (contentType) headers['content-type'] = contentType
      res.writeHead(upstream.status, headers)
      res.end(upstream.body)
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
      logger.warn({ err: error }, '[device-browser-gateway] proxy request failed')
      if (!res.headersSent) this.send(res, 502, { error: message })
      else res.end()
    }
  }
}

let singleton: DeviceBrowserGateway | null = null

export function getDeviceBrowserGateway(): DeviceBrowserGateway {
  if (!singleton) singleton = new DeviceBrowserGateway()
  return singleton
}
