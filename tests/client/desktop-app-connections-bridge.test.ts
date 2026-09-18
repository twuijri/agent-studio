// @vitest-environment jsdom
import { afterEach, describe, expect, it } from 'vitest'
import { desktopAppConnectionsBridge, desktopDeviceAgentBridge } from '@/utils/desktop-bridge'

type BridgeWindow = Window & typeof globalThis & { hermesDesktop?: unknown }
afterEach(() => { delete (window as BridgeWindow).hermesDesktop })

const agent = { getState: async () => ({}), openSettings: async () => true, discoverApps: async () => [], setApps: async () => ({}) }

describe('App connections bridge gate', () => {
  it('is available in local mode as well as linked-server mode', () => {
    ;(window as BridgeWindow).hermesDesktop = { isDesktop: true, mode: 'local', deviceAgent: agent }
    expect(desktopAppConnectionsBridge()).toBe(agent)
    expect(desktopDeviceAgentBridge()).toBeNull() // device access itself stays a linked-mode feature

    ;(window as BridgeWindow).hermesDesktop = { isDesktop: true, mode: 'server', deviceAgent: agent }
    expect(desktopAppConnectionsBridge()).toBe(agent)
    expect(desktopDeviceAgentBridge()).toBe(agent)
  })

  it('is hidden in a browser and on desktop builds that cannot discover apps', () => {
    expect(desktopAppConnectionsBridge()).toBeNull()
    ;(window as BridgeWindow).hermesDesktop = { isDesktop: true, mode: 'server', deviceAgent: { getState: async () => ({}), openSettings: async () => true } }
    expect(desktopAppConnectionsBridge()).toBeNull()
  })
})
