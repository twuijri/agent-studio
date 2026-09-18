// @vitest-environment jsdom
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { afterEach, describe, expect, it } from 'vitest'
import {
  canOpenDesktopConnectionSettings,
  desktopConnectionMode,
  isDesktopLocalRuntime,
  isDesktopServerLinked,
  isDesktopShell,
} from '@/utils/desktop-bridge'

type BridgeWindow = Window & typeof globalThis & { hermesDesktop?: unknown }

function setBridge(bridge: unknown) {
  ;(window as BridgeWindow).hermesDesktop = bridge
}

afterEach(() => {
  delete (window as BridgeWindow).hermesDesktop
})

describe('desktop connection mode helpers', () => {
  it('reports no mode outside the desktop shell', () => {
    expect(desktopConnectionMode()).toBeNull()
    expect(isDesktopShell()).toBe(false)
    expect(isDesktopLocalRuntime()).toBe(false)
    expect(isDesktopServerLinked()).toBe(false)
    expect(canOpenDesktopConnectionSettings()).toBe(false)
  })

  it('treats a shell without a mode field as the historical local runtime', () => {
    setBridge({ isDesktop: true, platform: 'darwin' })
    expect(desktopConnectionMode()).toBe('local')
    expect(isDesktopLocalRuntime()).toBe(true)
    expect(isDesktopServerLinked()).toBe(false)
  })

  it('recognises a shell linked to a Studio server', () => {
    setBridge({ isDesktop: true, platform: 'darwin', mode: 'server', desktopMode: { openSettings: () => Promise.resolve(true) } })
    expect(desktopConnectionMode()).toBe('server')
    expect(isDesktopServerLinked()).toBe(true)
    expect(isDesktopLocalRuntime()).toBe(false)
    expect(isDesktopShell()).toBe(true)
    expect(canOpenDesktopConnectionSettings()).toBe(true)
  })
})

describe('client surfaces that only apply to the local desktop runtime', () => {
  const read = (path: string) => readFileSync(resolve(process.cwd(), path), 'utf8')

  it('lets a linked shell log in and see the default-password prompt like a browser', () => {
    const router = read('packages/client/src/router/index.ts')
    expect(router).toContain("bridge?.isDesktop === true && bridge.mode !== 'server'")
    expect(router).toContain("if (to.name === 'login' && hasApiKey() && !isDesktopLocalShell())")

    const login = read('packages/client/src/views/LoginView.vue')
    expect(login).toContain('const desktopShell = isDesktopLocalRuntime();')

    const prompt = read('packages/client/src/components/auth/DefaultCredentialPrompt.vue')
    expect(prompt).toContain('bridge?.isDesktop === true && bridge.mode !== "server"')
    expect(prompt).toContain('if (isDesktopLocalShell()) {')
  })

  it('hides the local agent browser entry and offers the connection-mode setting', () => {
    const sidebar = read('packages/client/src/components/layout/AppSidebar.vue')
    expect(sidebar).toContain('bridge?.isDesktop === true && bridge.mode !== "server"')

    const display = read('packages/client/src/components/hermes/settings/DisplaySettings.vue')
    expect(display).toContain('v-if="connectionModeAvailable"')
    expect(display).toContain('data-testid="connection-mode-change"')
    expect(display).toContain("t('settings.display.connectionModeServer', { url: connectionModeServerUrl.value || '' })")
  })
})
