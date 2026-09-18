import { app, dialog, net, Notification, shell } from 'electron'
import { join } from 'node:path'
import { t } from './desktop-i18n'
import {
  checkForNewRelease,
  DEFAULT_RELEASE_API_URL,
  readReleaseNoticeState,
  RELEASE_NOTICE_FILE,
  writeReleaseNoticeState,
  type ReleaseInfo,
  type ReleaseNoticeState,
} from './release-notice-core'

// Electron wiring for the "new version available" notice. Automatic checks
// run shortly after startup and every few hours; a newer release is announced
// once per version with a system notification (click opens the release page)
// and stays visible in the tray menu until the user updates or skips it.
// The user can always check manually from the tray. Nothing is downloaded.

const STARTUP_DELAY_MS = 30_000
const CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000
const REQUEST_TIMEOUT_MS = 15_000

let timer: ReturnType<typeof setInterval> | null = null
let startupTimer: ReturnType<typeof setTimeout> | null = null
let checking = false
let available: ReleaseInfo | null = null
const listeners = new Set<() => void>()

function stateFile(): string {
  return join(app.getPath('userData'), RELEASE_NOTICE_FILE)
}

function releaseApiUrl(): string {
  const override = process.env.CORE_HUB_RELEASE_API_URL
  return override && /^https:\/\//.test(override) ? override : DEFAULT_RELEASE_API_URL
}

function readState(): ReleaseNoticeState {
  return readReleaseNoticeState(stateFile())
}

function saveState(patch: Partial<ReleaseNoticeState>): ReleaseNoticeState {
  return writeReleaseNoticeState(stateFile(), { ...readState(), ...patch })
}

function notifyListeners(): void {
  for (const listener of listeners) {
    try { listener() } catch (err) { console.warn('[release-notice] listener failed:', err) }
  }
}

async function fetchJson(url: string): Promise<unknown> {
  const response = await net.fetch(url, {
    headers: { Accept: 'application/vnd.github+json', 'User-Agent': `core-hub-desktop/${app.getVersion()}` },
    signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
  })
  if (!response.ok) throw new Error(`HTTP ${response.status}`)
  return response.json()
}

function openReleasePage(release: ReleaseInfo): void {
  shell.openExternal(release.url).catch(err => console.warn('[release-notice] failed to open the release page:', err))
}

/** The newest release seen by the last check when it is newer than this build, else null. */
export function availableRelease(): ReleaseInfo | null {
  return available
}

export function releaseNoticesEnabled(): boolean {
  return readState().enabled
}

export function setReleaseNoticesEnabled(enabled: boolean): void {
  saveState({ enabled })
  if (enabled) scheduleAutomaticChecks()
  else stopAutomaticChecks()
  notifyListeners()
}

export function onReleaseNoticeChange(listener: () => void): () => void {
  listeners.add(listener)
  return () => { listeners.delete(listener) }
}

export function skipAvailableRelease(): void {
  if (!available) return
  saveState({ skippedVersion: available.version })
  available = null
  notifyListeners()
}

async function showAvailableDialog(release: ReleaseInfo): Promise<void> {
  const { response } = await dialog.showMessageBox({
    type: 'info',
    title: t('release.availableTitle'),
    message: t('release.availableMessage', { version: release.version, current: app.getVersion() }),
    detail: t('release.availableDetail'),
    buttons: [t('release.open'), t('release.later'), t('release.skip')],
    defaultId: 0,
    cancelId: 1,
  })
  if (response === 0) openReleasePage(release)
  else if (response === 2) skipAvailableRelease()
}

function showAvailableNotification(release: ReleaseInfo): boolean {
  if (!Notification.isSupported()) return false
  const notification = new Notification({
    title: t('release.availableTitle'),
    body: t('release.notificationBody', { version: release.version }),
  })
  notification.on('click', () => openReleasePage(release))
  notification.show()
  return true
}

/**
 * Checks the fork's latest release. Manual checks always report the result in
 * a dialog; automatic checks announce a new version once (notification, or a
 * dialog where notifications are unsupported) and stay silent otherwise.
 */
export async function runReleaseNoticeCheck(manual: boolean): Promise<void> {
  if (checking) return
  checking = true
  try {
    const state = readState()
    const result = await checkForNewRelease({
      currentVersion: app.getVersion(),
      state,
      fetchJson,
      manual,
      apiUrl: releaseApiUrl(),
    })
    saveState({ lastCheckedAt: new Date().toISOString() })
    if (result.status === 'error') {
      console.warn(`[release-notice] check failed: ${result.error}`)
      if (manual) {
        await dialog.showMessageBox({
          type: 'warning',
          title: t('update.failedTitle'),
          message: t('release.failedMessage', { error: result.error }),
          buttons: [t('common.ok')],
        })
      }
      return
    }
    if (result.status !== 'available') {
      if (result.status === 'up-to-date') { available = null; notifyListeners() }
      if (manual) {
        await dialog.showMessageBox({
          type: 'info',
          title: t('update.upToDateTitle'),
          message: t('release.upToDateMessage', { current: app.getVersion() }),
          buttons: [t('common.ok')],
        })
      }
      return
    }
    available = result.release
    notifyListeners()
    if (manual) {
      await showAvailableDialog(result.release)
      return
    }
    if (state.noticedVersion === result.release.version) return
    saveState({ noticedVersion: result.release.version })
    if (!showAvailableNotification(result.release)) await showAvailableDialog(result.release)
  } finally {
    checking = false
  }
}

function scheduleAutomaticChecks(): void {
  if (timer) return
  timer = setInterval(() => {
    runReleaseNoticeCheck(false).catch(err => console.warn('[release-notice] automatic check failed:', err))
  }, CHECK_INTERVAL_MS)
  timer.unref?.()
}

function stopAutomaticChecks(): void {
  if (timer) clearInterval(timer)
  timer = null
  if (startupTimer) clearTimeout(startupTimer)
  startupTimer = null
}

/** Starts the automatic checks for packaged builds when the user has not turned them off. */
export function startReleaseNoticeChecks(): void {
  if (!app.isPackaged && !process.env.CORE_HUB_RELEASE_API_URL) return
  if (!releaseNoticesEnabled()) return
  startupTimer = setTimeout(() => {
    startupTimer = null
    runReleaseNoticeCheck(false).catch(err => console.warn('[release-notice] startup check failed:', err))
  }, STARTUP_DELAY_MS)
  startupTimer.unref?.()
  scheduleAutomaticChecks()
}

export function stopReleaseNoticeChecks(): void {
  stopAutomaticChecks()
}
