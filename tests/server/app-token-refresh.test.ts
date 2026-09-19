import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

// The mobile App renews its device-bound token silently so the QR is scanned
// once; revoking the connection still cuts the device off.

describe('POST /api/auth/app-refresh', () => {
  let db: any = null

  beforeEach(async () => {
    vi.resetModules()
    vi.stubEnv('AUTH_JWT_SECRET', 'test-secret')
    const { DatabaseSync } = await import('node:sqlite')
    db = new DatabaseSync(':memory:')
    vi.doMock('../../packages/server/src/modules/studio/infrastructure/database/index', () => ({ getDb: () => db, getStoragePath: () => ':memory:' }))
    vi.doMock('../../packages/server/src/modules/studio/public/profile-config', () => ({ listProfileNamesFromDisk: () => ['default'], getActiveProfileName: () => 'default' }))
  })

  afterEach(() => {
    db?.close()
    db = null
    vi.doUnmock('../../packages/server/src/modules/studio/infrastructure/database/index')
    vi.doUnmock('../../packages/server/src/modules/studio/public/profile-config')
    vi.unstubAllEnvs()
    vi.resetModules()
  })

  function ctxWith(token: string, user: any) {
    const headers: Record<string, string> = { authorization: `Bearer ${token}` }
    return {
      state: { user },
      query: {},
      headers,
      request: { body: {}, headers },
      status: 200,
      body: undefined as any,
      get: (name: string) => headers[name.toLowerCase()] || '',
      cookies: { get: () => undefined },
      req: { socket: { remoteAddress: '127.0.0.1' } },
      ip: '127.0.0.1',
    }
  }

  it('issues a fresh device-bound token, retires the old one, and refuses revoked connections', async () => {
    const schemas = await import('../../packages/server/src/modules/studio/infrastructure/database/schemas')
    schemas.initAllHermesTables()
    const users = await import('../../packages/server/src/modules/studio/repositories/users-store')
    const auth = await import('../../packages/server/src/modules/studio/middleware/auth')
    const store = await import('../../packages/server/src/modules/studio/repositories/app-connections-store')
    const ctrl = await import('../../packages/server/src/modules/studio/controllers/auth')
    const user = users.createUser({ username: 'owner', password: 'pw-owner-1', role: 'super_admin' } as any)
    const first = await auth.issueAppJwt(user, 'phone-1', 'lan')
    const now = Math.floor(Date.now() / 1000)
    store.upsertAppConnection({ deviceCode: 'phone-1', deviceName: 'iPhone', deviceBrand: 'Apple', deviceModel: 'iPhone16,1', connectionType: 'lan', userId: user.id, token: first, tokenExpiresAt: now + 3600, now })
    const authenticated = auth.toAuthenticatedUser(user)

    const ctx = ctxWith(first, authenticated)
    await ctrl.appRefresh(ctx as any)
    expect(ctx.status).toBe(200)
    expect(typeof ctx.body.token).toBe('string')
    expect(ctx.body.token).not.toBe(first)
    expect(ctx.body.token_expires_at).toBeGreaterThan(now + 3600)
    expect(ctx.body.appConnection).toMatchObject({ device_code: 'phone-1', device_name: 'iPhone', connection_type: 'lan' })

    // The old token is retired at once; the new one is the active credential.
    expect(store.isAppConnectionTokenActive('phone-1', 'lan', first, user.id)).toBe(false)
    expect(store.isAppConnectionTokenActive('phone-1', 'lan', ctx.body.token, user.id)).toBe(true)

    // A plain user token cannot be turned into an App token.
    const userToken = await auth.issueUserJwt(user)
    const wrong = ctxWith(userToken, authenticated)
    await ctrl.appRefresh(wrong as any)
    expect(wrong.status).toBe(401)
    expect(wrong.body.code).toBe('app_token_required')

    // Once the owner revokes the connection, renewal stops.
    store.revokeAppConnection(ctx.body.appConnection.id)
    const revoked = ctxWith(ctx.body.token, authenticated)
    await ctrl.appRefresh(revoked as any)
    expect(revoked.status).toBe(401)
    expect(revoked.body.code).toBe('app_connection_inactive')
  })
})
