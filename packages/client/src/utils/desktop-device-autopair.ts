import { fetchDevicePairingLink } from '@/api/studio/devices'
import { desktopDeviceAgentBridge } from '@/utils/desktop-bridge'

// When the desktop app is linked to this server and the user is signed in, the
// device asks to be paired automatically; the owner only has to approve it on
// the server (Devices → Requests). Attempted once per app session. The Device
// Agent learns that it is unpaired asynchronously (link-status check on start,
// or after the owner deleted the device record), so the sidebar keeps watching
// its state instead of checking only once at mount.

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

/**
 * Runs the automatic pairing attempt now and again whenever the Device Agent
 * reports a new state, until one request has been sent this session.
 * Returns a function that stops watching.
 */
export function watchDesktopDeviceAutoPair(options: { storage?: Storage } = {}): () => void {
  const bridge = desktopDeviceAgentBridge()
  if (!bridge?.pair) return () => {}
  let stopped = false
  let unsubscribe: (() => void) | null = null
  let inFlight: Promise<unknown> | null = null
  const attempt = () => {
    if (stopped || inFlight) return
    inFlight = maybeAutoPairDesktopDevice(options)
      .then(result => { if (result === 'sent') stop() })
      .catch(() => undefined)
      .finally(() => { inFlight = null })
  }
  const stop = () => {
    stopped = true
    unsubscribe?.()
    unsubscribe = null
  }
  if (typeof bridge.onState === 'function') {
    try {
      unsubscribe = bridge.onState(state => {
        if (state?.linked && state.status === 'unpaired') attempt()
      })
    } catch { unsubscribe = null }
  }
  attempt()
  return stop
}
