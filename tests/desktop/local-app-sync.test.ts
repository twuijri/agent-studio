import { createServer, type Server } from 'node:http'
import { afterEach, describe, expect, it } from 'vitest'
import { localAppSyncPayload, syncLocalSharedApps } from '../../packages/desktop/src/main/local-app-sync'

// Local desktop mode: shared apps are handed to the local Core Hub server.

const servers: Server[] = []
afterEach(() => { for (const server of servers.splice(0)) server.close() })

function app(overrides: Record<string, unknown> = {}) {
  return { id: 'claude-extension:abc', name: 'DaVinci Resolve', source: 'claude-extension', command: '/Applications/DaVinci.app/mcp', args: ['--stdio'], env: { RESOLVE_SCRIPT_API: '/x' }, cwd: '/tmp/davinci', enabled: true, ...overrides }
}

describe('local app sync (desktop → local Core Hub server)', () => {
  it('sends only enabled apps that have a command, with their full launch definition', () => {
    const payload = localAppSyncPayload('My Mac', [app(), app({ id: 'off', enabled: false }), app({ id: 'nocmd', command: '' })])
    expect(payload.computerName).toBe('My Mac')
    expect(payload.apps).toEqual([{ id: 'claude-extension:abc', name: 'DaVinci Resolve', source: 'claude-extension', command: '/Applications/DaVinci.app/mcp', args: ['--stdio'], env: { RESOLVE_SCRIPT_API: '/x' }, cwd: '/tmp/davinci' }])
  })

  it('PUTs the payload to the loopback route with the desktop bearer token', async () => {
    const received: Array<{ method?: string; url?: string; auth?: string; body: string }> = []
    const server = createServer((req, res) => {
      let body = ''
      req.on('data', chunk => { body += chunk })
      req.on('end', () => {
        received.push({ method: req.method, url: req.url, auth: req.headers.authorization, body })
        res.setHeader('content-type', 'application/json')
        res.end(JSON.stringify({ ok: true, changed: true, apps: ['claude-extension:abc'] }))
      })
    })
    servers.push(server)
    await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve))
    const port = (server.address() as { port: number }).port

    const result = await syncLocalSharedApps({ port, token: 'secret-token', computerName: 'My Mac' }, [app()])
    expect(result).toEqual({ ok: true, changed: true, apps: ['claude-extension:abc'], status: 200 })
    expect(received).toHaveLength(1)
    expect(received[0]).toMatchObject({ method: 'PUT', url: '/api/desktop/local-apps', auth: 'Bearer secret-token' })
    expect(JSON.parse(received[0].body).apps[0].command).toBe('/Applications/DaVinci.app/mcp')
  })

  it('throws on a non-2xx response so the caller can log it', async () => {
    const server = createServer((_req, res) => { res.statusCode = 401; res.end('{}') })
    servers.push(server)
    await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve))
    const port = (server.address() as { port: number }).port
    await expect(syncLocalSharedApps({ port, token: 'x', computerName: 'pc' }, [app()])).rejects.toThrow('HTTP 401')
  })
})
