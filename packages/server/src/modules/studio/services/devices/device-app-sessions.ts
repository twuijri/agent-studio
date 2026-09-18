import { WebSocketServer, type WebSocket } from 'ws'
import type { Server as HttpServer, IncomingMessage } from 'http'
import type { Duplex } from 'stream'
import { config } from '../../public/config'
import { logger } from '../../public/logging'
import { authenticateUserToken, isAuthEnabled } from '../../public/auth'
import { parseUpgradeRequestUrl, shouldRejectUpgradeOrigin, writeBadUpgradeRequest, writeForbiddenOrigin } from '../../middleware/security'
import { getLanPeerSocketManager } from '../network/lan-peer-socket'
import { isDeviceAllowedForProfile } from './device-bindings'

// WebSocket endpoint that gives an agent's MCP client a raw stdio pipe to an
// app shared by a linked device. The bridge script
// (bin/core-hub-device-mcp.mjs) connects here on behalf of the agent; bytes
// are forwarded verbatim to the app process running on the device.
//
//   GET /api/devices/app-session?device=<deviceId>&app=<appId>&profile=<p>&token=<jwt>
//
// Frames: binary frames carry stdio bytes; text frames are JSON control
// messages ({type:'ready'|'exit'|'stderr'|'error', ...}).

export const DEVICE_APP_SESSION_PATH = '/api/devices/app-session'

export class DeviceAppSessionServer {
  private readonly wss = new WebSocketServer({ noServer: true })
  private readonly handlers = new Map<HttpServer, (req: IncomingMessage, socket: Duplex, head: Buffer) => void>()
  private readonly sockets = new Set<WebSocket>()

  setupServer(httpServers: HttpServer | HttpServer[]): void {
    const servers = Array.isArray(httpServers) ? httpServers : [httpServers]
    for (const httpServer of servers) {
      if (this.handlers.has(httpServer)) continue
      const onUpgrade = async (req: IncomingMessage, socket: Duplex, head: Buffer) => {
        const url = parseUpgradeRequestUrl(req)
        if (!url) {
          writeBadUpgradeRequest(socket)
          return
        }
        if (url.pathname !== DEVICE_APP_SESSION_PATH) return
        if (shouldRejectUpgradeOrigin(req, config.corsOrigins)) {
          writeForbiddenOrigin(socket)
          return
        }
        const deviceId = (url.searchParams.get('device') || '').trim()
        const appId = (url.searchParams.get('app') || '').trim()
        const profile = (url.searchParams.get('profile') || '').trim()
        if (!deviceId || !appId) {
          socket.write('HTTP/1.1 400 Bad Request\r\n\r\n')
          socket.destroy()
          return
        }
        if (await isAuthEnabled()) {
          const user = await authenticateUserToken(url.searchParams.get('token') || '')
          if (!user) {
            socket.write('HTTP/1.1 401 Unauthorized\r\n\r\n')
            socket.destroy()
            return
          }
        }
        if (!isDeviceAllowedForProfile(deviceId, profile)) {
          socket.write('HTTP/1.1 403 Forbidden\r\n\r\n')
          socket.destroy()
          return
        }
        this.wss.handleUpgrade(req, socket, head, ws => {
          void this.bridge(ws, deviceId, appId, profile)
        })
      }
      this.handlers.set(httpServer, onUpgrade)
      httpServer.on('upgrade', onUpgrade)
    }
  }

  private async bridge(ws: WebSocket, deviceId: string, appId: string, profile: string): Promise<void> {
    this.sockets.add(ws)
    const sendControl = (payload: Record<string, unknown>) => {
      if (ws.readyState === ws.OPEN) ws.send(JSON.stringify(payload))
    }
    const connection = getLanPeerSocketManager().findControllableConnection(deviceId)
    if (!connection) {
      sendControl({ type: 'error', message: 'The linked device is offline' })
      ws.close(4404, 'device offline')
      this.sockets.delete(ws)
      return
    }
    let session
    try {
      session = await connection.openAppSession(appId)
    } catch (err) {
      sendControl({ type: 'error', message: err instanceof Error ? err.message : String(err) })
      ws.close(4403, 'app unavailable')
      this.sockets.delete(ws)
      return
    }
    logger.info({ deviceId, appId, profile }, '[device-apps] app session opened')
    session.onData(data => {
      if (ws.readyState === ws.OPEN) ws.send(data, { binary: true })
    })
    session.onStderr(text => sendControl({ type: 'stderr', data: text }))
    session.onExit(info => {
      sendControl({ type: 'exit', code: info.code, message: info.message })
      if (ws.readyState === ws.OPEN) ws.close(1000, 'app exited')
    })
    ws.on('message', (raw, isBinary) => {
      if (isBinary) {
        session.write(Buffer.isBuffer(raw) ? raw : Buffer.from(raw as ArrayBuffer))
        return
      }
      // Text frames from the bridge are treated as stdio too (newline-delimited JSON-RPC).
      session.write(Buffer.from(String(raw)))
    })
    ws.on('close', () => {
      this.sockets.delete(ws)
      session.close()
    })
    ws.on('error', () => {
      this.sockets.delete(ws)
      session.close()
    })
    sendControl({ type: 'ready', session_id: session.id })
  }

  forceClose(): void {
    for (const [httpServer, handler] of this.handlers) httpServer.off('upgrade', handler)
    this.handlers.clear()
    for (const ws of this.sockets) {
      try { ws.terminate() } catch { /* ignore */ }
    }
    this.sockets.clear()
  }

  async close(): Promise<void> {
    this.forceClose()
    await new Promise<void>(resolve => this.wss.close(() => resolve()))
  }
}

let singleton: DeviceAppSessionServer | null = null

export function getDeviceAppSessionServer(): DeviceAppSessionServer {
  if (!singleton) singleton = new DeviceAppSessionServer()
  return singleton
}
