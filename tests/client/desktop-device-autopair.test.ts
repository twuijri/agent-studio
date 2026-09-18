// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'

const { fetchDevicePairingLink } = vi.hoisted(() => ({ fetchDevicePairingLink: vi.fn() }))
vi.mock('@/api/studio/devices', () => ({ fetchDevicePairingLink }))

import { DESKTOP_AUTOPAIR_ATTEMPTED_KEY, maybeAutoPairDesktopDevice } from '@/utils/desktop-device-autopair'

type BridgeWindow = Window & typeof globalThis & { hermesDesktop?: unknown }

function memoryStorage(): Storage {
  const map = new Map<string, string>()
  return {
    getItem: key => map.get(key) ?? null,
    setItem: (key, value) => { map.set(key, String(value)) },
    removeItem: key => { map.delete(key) },
    clear: () => map.clear(),
    key: () => null,
    get length() { return map.size },
  } as Storage
}

afterEach(() => {
  delete (window as BridgeWindow).hermesDesktop
  fetchDevicePairingLink.mockReset()
})

describe('automatic device pairing from the linked desktop app', () => {
  it('sends one pairing request per app session when the device is linked but unpaired', async () => {
    const pair = vi.fn(async () => ({ status: 'pending' }))
    ;(window as BridgeWindow).hermesDesktop = {
      isDesktop: true,
      mode: 'server',
      deviceAgent: { getState: async () => ({ linked: true, status: 'unpaired' }), openSettings: async () => true, pair },
    }
    fetchDevicePairingLink.mockResolvedValue({ code: 'abc', link: 'https://studio.example.com/#/hermes/devices?pairing_code=abc' })
    const storage = memoryStorage()

    await expect(maybeAutoPairDesktopDevice({ storage })).resolves.toBe('sent')
    expect(pair).toHaveBeenCalledWith('https://studio.example.com/#/hermes/devices?pairing_code=abc')
    expect(storage.getItem(DESKTOP_AUTOPAIR_ATTEMPTED_KEY)).toBe('1')

    await expect(maybeAutoPairDesktopDevice({ storage })).resolves.toBe('skipped')
    expect(pair).toHaveBeenCalledTimes(1)
  })

  it('does nothing in a browser, in local mode, or when already paired', async () => {
    await expect(maybeAutoPairDesktopDevice({ storage: memoryStorage() })).resolves.toBe('skipped')

    const pair = vi.fn()
    ;(window as BridgeWindow).hermesDesktop = { isDesktop: true, mode: 'local', deviceAgent: { getState: async () => ({ linked: false, status: 'unpaired' }), openSettings: async () => true, pair } }
    await expect(maybeAutoPairDesktopDevice({ storage: memoryStorage() })).resolves.toBe('skipped')

    ;(window as BridgeWindow).hermesDesktop = { isDesktop: true, mode: 'server', deviceAgent: { getState: async () => ({ linked: true, status: 'connected' }), openSettings: async () => true, pair } }
    await expect(maybeAutoPairDesktopDevice({ storage: memoryStorage() })).resolves.toBe('skipped')
    expect(pair).not.toHaveBeenCalled()
    expect(fetchDevicePairingLink).not.toHaveBeenCalled()
  })

  it('reports failure without throwing when the server refuses the pairing link', async () => {
    ;(window as BridgeWindow).hermesDesktop = { isDesktop: true, mode: 'server', deviceAgent: { getState: async () => ({ linked: true, status: 'unpaired' }), openSettings: async () => true, pair: vi.fn() } }
    fetchDevicePairingLink.mockRejectedValue(new Error('403'))
    await expect(maybeAutoPairDesktopDevice({ storage: memoryStorage() })).resolves.toBe('failed')
  })
})
