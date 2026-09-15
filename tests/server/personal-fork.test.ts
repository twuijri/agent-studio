import { describe, expect, it, vi } from 'vitest'
import { PERSONAL_FORK, isOfficialStudioService } from '../../packages/server/src/modules/studio/public/personal-fork'
import { config, isAppEntitlementRequired } from '../../packages/server/src/modules/studio/public/config'
import { fetchStudioAnnouncements } from '../../packages/server/src/modules/studio/services/notifications/announcements'
import { StudioHealthService } from '../../packages/server/src/modules/studio/services/health'

describe('personal fork policy', () => {
  it('uses local authentication without requiring an official cloud entitlement', () => {
    expect(PERSONAL_FORK).toBe(true)
    expect(isAppEntitlementRequired({})).toBe(false)
    expect(isAppEntitlementRequired({ HERMES_APP_ENTITLEMENT_REQUIRED: 'true' })).toBe(true)
    expect(config.appRelay.url).toBe('')
  })
  it('identifies official relay hosts without blocking user-owned servers', () => {
    for (const url of ['https://api.ekkostudio.xyz', 'https://cn.hermes-studio.ai', 'https://api.ekkostudio.xyz.']) {
      expect(isOfficialStudioService(url)).toBe(true)
    }
    for (const url of ['https://studio.example.com', 'http://127.0.0.1:6061', 'https://ekkostudio.xyz.example.org']) {
      expect(isOfficialStudioService(url)).toBe(false)
    }
  })
  it('refuses official relay connections and cloud authorization without network access', async () => {
    const { startAppRelayClient } = await import('../../packages/server/src/modules/studio/services/app-relay/client')
    const { ensureAppRelayHostClient } = await import('../../packages/server/src/modules/studio/services/app-relay/connection')
    const { startOutboundRelayClient } = await import('../../packages/server/src/modules/studio/services/global-agent/outbound-relay-client')
    const { createCloudAppAuthorizationCodeController } = await import('../../packages/server/src/modules/studio/controllers/app-connections')
    const fetchMock = vi.fn(() => { throw new Error('Unexpected network request') })
    vi.stubGlobal('fetch', fetchMock)
    try {
      expect(await ensureAppRelayHostClient()).toBeNull()
      expect(startAppRelayClient({ relayUrl: 'https://api.ekkostudio.xyz', machineId: 'test', publicKey: 'test' })).toBeNull()
      expect(startOutboundRelayClient({ relayUrl: 'https://api.ekkostudio.xyz' })).toBeNull()
      const ctx = { status: 200, body: undefined } as any
      await createCloudAppAuthorizationCodeController(ctx)
      expect(ctx.status).toBe(410)
      expect(fetchMock).not.toHaveBeenCalled()
    } finally { vi.unstubAllGlobals() }
  })
  it('rejects upstream installs, including preview entry points', async () => {
    const controllers = await import('../../packages/server/src/modules/studio/controllers/update')
    for (const action of ['handleUpdate', 'previewTags', 'preparePreview', 'installPreview', 'startPreview'] as const) {
      const ctx = { status: 200, body: undefined } as any
      await controllers[action](ctx)
      expect(ctx.status).toBe(409)
      expect(ctx.body.error).toBe('manual_fork_update_required')
    }
    const runtime = await import('../../packages/server/src/modules/hermes/controllers/runtime-versions')
    for (const action of ['downloadWebUi', 'activateWebUi'] as const) {
      const ctx = { status: 200, body: undefined } as any
      await runtime[action](ctx)
      expect(ctx.status).toBe(409)
      expect(ctx.body.error).toBe('manual_fork_update_required')
    }
  })
  it('does not fetch paid announcements or official package updates', async () => {
    const fetchMock = vi.fn(() => { throw new Error('Unexpected network request') })
    vi.stubGlobal('fetch', fetchMock)
    try {
      expect(await fetchStudioAnnouncements('ar')).toEqual({ ok: true, platform: 'desktop', list: [] })
      const service = new StudioHealthService({} as never)
      await service.checkLatestVersion()
      expect(fetchMock).not.toHaveBeenCalled()
    } finally { vi.unstubAllGlobals() }
  })
})
