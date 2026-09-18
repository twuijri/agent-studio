import { fetchDevicePairingLink } from '@/api/studio/devices'
import { desktopDeviceAgentBridge } from '@/utils/desktop-bridge'

// When the desktop app is linked to this server and the user is signed in, the
// device asks to be paired automatically; the owner only has to approve it on
// the server (Devices → Requests). Attempted once per app session.

export const DESKTOP_AUTOPAIR_ATTEMPTED_KEY = 'core_hub_device_autopair_attempted'

export async function maybeAutoPairDesktopDevice(options: { storage?: Storage } = {}): Promise<'sent' | 'skipped' | 'failed'> {
  const bridge = desktopDeviceAgentBridge()
  if (!bridge?.pair) return 'skipped'
  const storage = options.storage ?? (typeof sessionStorage !== 'undefined' ? sessionStorage : undefined)
  try {
    if (storage?.getItem(DESKTOP_AUTOPAIR_ATTEMPTED_KEY)) return 'skipped'
  } catch { /* storage unavailable */ }
  let state
  try {
    state = await bridge.getState()
  } catch {
    return 'failed'
  }
  if (!state?.linked || state.status !== 'unpaired') return 'skipped'
  try { storage?.setItem(DESKTOP_AUTOPAIR_ATTEMPTED_KEY, '1') } catch { /* ignore */ }
  try {
    const { link } = await fetchDevicePairingLink()
    await bridge.pair(link)
    return 'sent'
  } catch {
    return 'failed'
  }
}
