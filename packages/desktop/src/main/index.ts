import {
  app,
  BrowserWindow,
  desktopCapturer,
  dialog,
  ipcMain,
  Menu,
  nativeImage,
  Notification,
  screen,
  session,
  shell,
  systemPreferences,
  Tray,
  type MessageBoxOptions,
  type OpenDialogOptions,
  type IpcMainInvokeEvent,
  type WebContents,
} from 'electron'
import { existsSync } from 'node:fs'
import { mkdir } from 'node:fs/promises'
import { join } from 'node:path'
import {
  getToken,
  setWebUiRestartRequestHandler,
  setWebUiUnexpectedExitHandler,
  startWebUiServer,
  stopWebUiServer,
} from './webui-server'
import { bundledNode, desktopIcon, desktopLinuxTrayIcon, desktopMacTrayIcon, desktopRuntimeVersion, desktopWindowsTrayIcon, runtimeStorageRoot, webuiDir, webUiHome } from './paths'
import { checkForDesktopUpdates, initAutoUpdater } from './updater'
import { isRtlDesktopLocale, t } from './desktop-i18n'
import {
  DESKTOP_MODE_FILE_NAME,
  LOCAL_DESKTOP_MODE,
  isDesktopMode,
  isDesktopModeLockedByEnv,
  normalizeStudioServerUrl,
  probeStudioServer,
  resolveDesktopMode,
  writeDesktopModeConfig,
  type ResolvedDesktopMode,
} from './desktop-mode'
import { DeviceAgent, type DeviceAgentState, type ExecApprovalDecision } from './device-agent/agent'
import type { DeviceAgentProxyRequest, DeviceAgentProxyResponse, DeviceAgentScreenCapture } from './device-agent/protocol'
import { parseScreenAction, runScreenAction } from './device-agent/screen-input'
import { discoverMcpApps } from './device-agent/mcp-discovery'
import { parseSharedMcpApps } from './device-agent/store'
import type { SharedAppDefinition } from './device-agent/protocol'
import {
  DEVICE_AGENT_AUDIT_FILE_NAME,
  DEVICE_AGENT_CONFIG_FILE_NAME,
  DEVICE_AGENT_IDENTITY_FILE_NAME,
  appendDeviceAgentAudit,
  readDeviceAgentAudit,
  readDeviceAgentConfig,
  writeDeviceAgentConfig,
  type DeviceAgentConfig,
} from './device-agent/store'
import { resetDesktopDefaultLogin } from './desktop-login-reset'
import { installHermesStudioCliShim, installHermesStudioMcpShim } from './cli-shim'
import { parseHermesCliArgs, runBundledHermesCli } from './hermes-cli'
import { installSelectionContextMenu } from './selection-context-menu'
import { groupChatAgentLinkPopupResponse } from './group-chat-agent-popup'
import { isTrustedDesktopAppUrl, normalizeExternalHttpUrl } from './window-open-policy'
import {
  ensureDesktopRuntime,
  isDesktopRuntimeReady,
  migratePendingRuntimeRoot,
  repairUpdatedDesktopRuntimeLaunchers,
  writeActiveRuntimeVersion,
  type RuntimeDownloadSource,
  type RuntimeProgress,
} from './runtime-manager'
import { BrowserManager } from './browser/browser-manager'
import { BrowserBroker } from './browser/browser-broker'
import type { BrowserBounds } from './browser/browser-types'
import { migratePendingLegacyWindowsData } from './legacy-windows-data-migration'
import { createDesktopAppLifecycle } from './app-lifecycle'
import { configureDesktopIdentity } from './desktop-identity'
import { migrateWindowsLoginItem } from './login-item-migration'

configureDesktopIdentity(app)

const PORT = Number(process.env.HERMES_DESKTOP_PORT) || 8748
const START_HIDDEN = process.argv.includes('--hidden')
const QUIT_EXISTING = process.argv.includes('--quit')
const APP_USER_MODEL_ID = 'com.hermeswebui.studio'
const PET_WINDOW_DEFAULT_WIDTH = 300
const PET_WINDOW_DEFAULT_HEIGHT = 320
const PET_WINDOW_MIN_SIZE = 72
const PET_WINDOW_MAX_SIZE = 1200
const PET_WINDOW_REFRESH_CHANNEL = 'hermes-desktop:pet-window-refresh'
const WINDOW_STATE_CHANGE_CHANNEL = 'hermes-desktop:window-state-change'
const BROWSER_STATE_CHANGE_CHANNEL = 'hermes-desktop:browser-state-change'
const BROWSER_ANNOTATION_REQUEST_CHANNEL = 'hermes-desktop:browser-annotation-request'
const DESKTOP_DISABLED_CHROMIUM_FEATURES = ['CompressionDictionaryTransport', 'CompressionDictionaryTransportBackend']
const FAILURE_RECOVERY_WINDOW_MS = 60_000
type WindowControlAction = 'minimize' | 'toggle-maximize' | 'close'
type DesktopWindowBounds = { x: number; y: number; width: number; height: number }

let mainWindow: BrowserWindow | null = null
let petWindow: BrowserWindow | null = null
let petWindowLoadPromise: Promise<void> | null = null
const chatWindows = new Map<string, BrowserWindow>()
let serverUrl: string | null = null
// Connection mode (local runtime vs. linked Studio server). Resolved once
// Electron is ready, before any window loads; see desktop-mode.ts.
let desktopMode: ResolvedDesktopMode = LOCAL_DESKTOP_MODE
// Device Agent: lets the linked Studio server operate this machine (phase 2 of
// docs/DESKTOP-SERVER-MODE.md). Only created in the linked-server mode.
let deviceAgent: DeviceAgent | null = null
let screenControlOverlay: BrowserWindow | null = null
const DEVICE_AGENT_STATE_CHANNEL = 'hermes-desktop:device-agent-state'
let tray: Tray | null = null
let appShutdownPromise: Promise<void> | null = null
let isBootstrapping = false
let isResettingLogin = false
let windowFadeTimer: NodeJS.Timeout | null = null
let browserManager: BrowserManager | null = null
let browserBroker: BrowserBroker | null = null
const activeNotifications = new Set<Notification>()
let unexpectedWebUiExitCount = 0
let unexpectedWebUiExitWindowStartedAt = 0
let rendererRecoveryCount = 0
let rendererRecoveryWindowStartedAt = 0
const appLifecycle = createDesktopAppLifecycle(app)

// Custom Session paths do not need Chromium's optional compression-dictionary
// disk cache; disabling it leaves the normal HTTP cache enabled and isolated.
const existingDisabledFeatures = app.commandLine.getSwitchValue('disable-features')
  .split(',')
  .map(value => value.trim())
  .filter(Boolean)
app.commandLine.appendSwitch('disable-features', [...new Set([...existingDisabledFeatures, ...DESKTOP_DISABLED_CHROMIUM_FEATURES])].join(','))

if (process.platform === 'win32') {
  app.setAppUserModelId(APP_USER_MODEL_ID)
}

function cancelWindowFade() {
  if (windowFadeTimer) {
    clearInterval(windowFadeTimer)
    windowFadeTimer = null
  }
}

function showWindowWithFade(focus = true) {
  if (!mainWindow || mainWindow.isDestroyed()) return
  if (mainWindow.isMinimized()) mainWindow.restore()

  cancelWindowFade()
  if (process.platform !== 'win32' || mainWindow.isVisible()) {
    mainWindow.setOpacity(1)
    mainWindow.show()
    if (focus) mainWindow.focus()
    return
  }

  const durationMs = 180
  const startedAt = Date.now()
  mainWindow.setOpacity(0)
  mainWindow.show()
  if (focus) mainWindow.focus()
  windowFadeTimer = setInterval(() => {
    if (!mainWindow || mainWindow.isDestroyed()) {
      cancelWindowFade()
      return
    }
    const progress = Math.min(1, (Date.now() - startedAt) / durationMs)
    mainWindow.setOpacity(progress)
    if (progress >= 1) {
      mainWindow.setOpacity(1)
      cancelWindowFade()
    }
  }, 16)
}

function showMainWindow() {
  if (!mainWindow) {
    void createWindow()
  }
  if (!mainWindow) return
  showWindowWithFade(true)
}

function quitApp() {
  appLifecycle.quit()
}

function scheduleAppRestart(delayMs = 100): boolean {
  return appLifecycle.scheduleRestart(delayMs)
}

async function prepareAppShutdown(): Promise<void> {
  appLifecycle.prepareShutdown()
  if (!appShutdownPromise) {
    appShutdownPromise = (async () => {
      cancelWindowFade()
      await showShutdownSplash()
      await browserBroker?.stop().catch(() => undefined)
      await browserManager?.destroy().catch(() => undefined)
      browserBroker = null
      browserManager = null
      deviceAgent?.stop()
      updateScreenControlOverlay(false)
      await stopWebUiServer().catch(() => undefined)
    })()
  }
  await appShutdownPromise
}

function defaultPetWindowBounds(): DesktopWindowBounds {
  const { workArea } = screen.getPrimaryDisplay()
  return {
    x: Math.round(workArea.x + workArea.width - PET_WINDOW_DEFAULT_WIDTH - 28),
    y: Math.round(workArea.y + workArea.height - PET_WINDOW_DEFAULT_HEIGHT - 28),
    width: PET_WINDOW_DEFAULT_WIDTH,
    height: PET_WINDOW_DEFAULT_HEIGHT,
  }
}

function petWindowState() {
  const target = petWindow && !petWindow.isDestroyed() ? petWindow : null
  return {
    bounds: target?.getBounds() || defaultPetWindowBounds(),
    visible: !!target?.isVisible(),
  }
}

function sanitizePetWindowBounds(input: unknown): DesktopWindowBounds | null {
  if (!input || typeof input !== 'object') return null
  const value = input as Partial<DesktopWindowBounds>
  const x = Number(value.x)
  const y = Number(value.y)
  const width = Number(value.width)
  const height = Number(value.height)
  if (![x, y, width, height].every(Number.isFinite)) return null
  return {
    x: Math.round(x),
    y: Math.round(y),
    width: Math.round(Math.min(PET_WINDOW_MAX_SIZE, Math.max(PET_WINDOW_MIN_SIZE, width))),
    height: Math.round(Math.min(PET_WINDOW_MAX_SIZE, Math.max(PET_WINDOW_MIN_SIZE, height))),
  }
}

function webUiHashUrl(hashPath: string): string | null {
  if (!serverUrl) return null
  const normalizedHash = hashPath.startsWith('/') ? hashPath : `/${hashPath}`
  return `${serverUrl.replace(/#.*$/, '').replace(/\/$/, '')}/#${normalizedHash}`
}

function mainRouteUrl(): string | null {
  return webUiHashUrl('/hermes/chat')
}

function petRouteUrl(): string | null {
  return webUiHashUrl('/desktop-pet')
}

function chatRouteUrl(sessionId: string, profile?: string): string | null {
  const query = profile ? `?profile=${encodeURIComponent(profile)}` : ''
  return webUiHashUrl(`/desktop-chat/${encodeURIComponent(sessionId)}${query}`)
}

function ensurePetWindow(): BrowserWindow {
  if (petWindow && !petWindow.isDestroyed()) return petWindow

  petWindow = new BrowserWindow({
    ...defaultPetWindowBounds(),
    title: 'Hermes Pet',
    frame: false,
    transparent: true,
    backgroundColor: '#00000000',
    hasShadow: false,
    ...(process.platform === 'darwin' ? { roundedCorners: false } : {}),
    ...(process.platform === 'win32' ? { thickFrame: false } : {}),
    resizable: false,
    maximizable: false,
    minimizable: false,
    fullscreenable: false,
    skipTaskbar: true,
    show: false,
    acceptFirstMouse: true,
    autoHideMenuBar: true,
    alwaysOnTop: true,
    webPreferences: {
      preload: join(__dirname, '..', 'preload', 'index.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
      additionalArguments: ['--hermes-window-kind=pet'],
    },
  })
  petWindow.setBackgroundColor('#00000000')
  petWindow.setHasShadow(false)
  petWindow.setAlwaysOnTop(true, process.platform === 'darwin' ? 'floating' : 'normal')
  if (process.platform === 'darwin') {
    petWindow.setVisibleOnAllWorkspaces(true, { visibleOnFullScreen: false })
  }
  petWindow.on('closed', () => {
    petWindow = null
    petWindowLoadPromise = null
  })
  petWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (isTrustedDesktopAppUrl(url, serverUrl)) {
      return { action: 'allow' }
    }
    shell.openExternal(url).catch(() => undefined)
    return { action: 'deny' }
  })
  return petWindow
}

async function loadPetWindowRoute(): Promise<void> {
  const url = petRouteUrl()
  if (!url) return
  const target = ensurePetWindow()
  if (target.webContents.getURL() === url) return
  if (!petWindowLoadPromise) {
    petWindowLoadPromise = target.loadURL(url)
      .catch(err => {
        console.warn('[desktop-pet] failed to load pet window:', err)
      })
      .finally(() => {
        petWindowLoadPromise = null
      })
  }
  await petWindowLoadPromise
}

function windowState(target: BrowserWindow | null = mainWindow) {
  return {
    isMaximized: !!target && !target.isDestroyed() && target.isMaximized(),
  }
}

function notifyWindowStateChanged(target: BrowserWindow | null) {
  if (!target || target.isDestroyed()) return
  target.webContents.send(WINDOW_STATE_CHANGE_CHANNEL, windowState(target))
}

function handleWindowControl(target: BrowserWindow | null, action: WindowControlAction) {
  if (!target || target.isDestroyed()) return windowState(target)
  if (action === 'minimize') {
    target.minimize()
  } else if (action === 'toggle-maximize') {
    if (target.isMaximized()) target.unmaximize()
    else target.maximize()
  } else if (action === 'close') {
    target.close()
  }
  return windowState(target)
}

function hasQuitRequest(data: unknown): boolean {
  return typeof data === 'object'
    && data !== null
    && (data as { quit?: unknown }).quit === true
}

function loginItemOptions() {
  return {
    path: process.execPath,
    args: ['--hidden'],
  }
}

function getOpenAtLogin(): boolean {
  return app.getLoginItemSettings(loginItemOptions()).openAtLogin
}

function setOpenAtLogin(openAtLogin: boolean) {
  app.setLoginItemSettings({
    ...loginItemOptions(),
    openAtLogin,
    openAsHidden: true,
  })
}

async function clearWebLoginSession() {
  if (!mainWindow || mainWindow.isDestroyed()) return
  await mainWindow.webContents.executeJavaScript(`
    try {
      localStorage.removeItem('hermes_api_key');
      sessionStorage.clear();
      window.location.hash = '#/login';
    } catch {
      window.location.hash = '#/login';
    }
  `).catch(() => undefined)
}

function showDesktopMessageBox(options: MessageBoxOptions) {
  if (mainWindow && !mainWindow.isDestroyed()) return dialog.showMessageBox(mainWindow, options)
  return dialog.showMessageBox(options)
}

async function handleResetDefaultLogin() {
  if (isResettingLogin || (isBootstrapping && !serverUrl)) return

  const choice = await showDesktopMessageBox({
    type: 'warning',
    buttons: [t('tray.resetLogin'), t('common.cancel')],
    defaultId: 0,
    cancelId: 1,
    title: t('loginReset.confirmTitle'),
    message: t('loginReset.confirmMessage'),
    detail: t('loginReset.confirmDetail'),
  })
  if (choice.response !== 0) return

  isResettingLogin = true
  updateTrayMenu()
  showMainWindow()

  try {
    await clearWebLoginSession()
    if (mainWindow && !mainWindow.isDestroyed()) {
      await mainWindow.loadURL(splashHtml(t('loginReset.resetting')))
    }
    await stopWebUiServer()
    serverUrl = null
    const credentials = await resetDesktopDefaultLogin()
    const url = await startWebUiServer(PORT)
    serverUrl = url
    if (mainWindow && !mainWindow.isDestroyed()) await mainWindow.loadURL(url)
    await loadPetWindowRoute()
    await clearWebLoginSession()
    await showDesktopMessageBox({
      type: 'info',
      buttons: [t('common.ok')],
      defaultId: 0,
      title: t('loginReset.successTitle'),
      message: t('loginReset.successMessage', credentials),
    })
  } catch (err) {
    console.error('[desktop-login-reset] failed:', err)
    await showDesktopMessageBox({
      type: 'error',
      buttons: [t('common.ok')],
      defaultId: 0,
      title: t('loginReset.failedTitle'),
      message: t('loginReset.failedMessage'),
      detail: err instanceof Error ? err.message : String(err),
    })
  } finally {
    isResettingLogin = false
    updateTrayMenu()
  }
}

function updateTrayMenu() {
  if (!tray) return
  const isVisible = !!mainWindow && mainWindow.isVisible()
  const menu = Menu.buildFromTemplate([
    {
      label: isVisible ? t('tray.hide') : t('tray.show'),
      click: () => {
        if (mainWindow?.isVisible()) {
          mainWindow.hide()
        } else {
          showMainWindow()
        }
        updateTrayMenu()
      },
    },
    {
      label: t('tray.checkForUpdates'),
      click: () => {
        checkForDesktopUpdates(true).catch(err => {
          console.error('[tray] update check failed:', err)
        })
      },
    },
    {
      label: t('tray.connectionMode'),
      click: () => {
        openConnectionModePage().catch(err => {
          console.error('[tray] failed to open the connection mode page:', err)
        })
      },
    },
    ...(isServerLinkedMode() ? [{
      label: t('tray.deviceAccess'),
      click: () => {
        openDeviceAgentPage().catch(err => {
          console.error('[tray] failed to open the device access page:', err)
        })
      },
    }] : []),
    ...(isServerLinkedMode() ? [] : [{
      label: isResettingLogin ? t('loginReset.resetting') : t('tray.resetLogin'),
      enabled: !isResettingLogin && (!isBootstrapping || !!serverUrl),
      click: () => {
        handleResetDefaultLogin().catch(err => {
          console.error('[tray] reset login failed:', err)
        })
      },
    }]),
    {
      label: t('tray.openAtLogin'),
      type: 'checkbox',
      checked: getOpenAtLogin(),
      click: (item) => {
        setOpenAtLogin(item.checked)
        updateTrayMenu()
      },
    },
    { type: 'separator' },
    {
      label: t('tray.quit'),
      click: quitApp,
    },
  ])
  tray.setContextMenu(menu)
}

function createTray() {
  if (tray) return
  const source = process.platform === 'darwin'
    ? desktopMacTrayIcon()
    : process.platform === 'win32'
      ? desktopWindowsTrayIcon()
      : desktopLinuxTrayIcon()
  const sourceIcon = nativeImage.createFromPath(source)
  if (process.platform === 'darwin') sourceIcon.setTemplateImage(true)
  const icon = process.platform === 'darwin'
    ? sourceIcon
    : sourceIcon.resize({
        width: process.platform === 'win32' ? 24 : 16,
        height: process.platform === 'win32' ? 24 : 16,
        quality: 'best',
      })
  tray = new Tray(icon)
  tray.setToolTip('Core Hub')
  tray.on('click', () => {
    showMainWindow()
    updateTrayMenu()
  })
  updateTrayMenu()
}

async function createWindow(): Promise<void> {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 769,
    minHeight: 600,
    title: 'Core Hub',
    backgroundColor: '#1a1a1a',
    autoHideMenuBar: true,
    show: false,
    ...(process.platform === 'darwin'
      ? {
          titleBarStyle: 'hiddenInset' as const,
          trafficLightPosition: { x: 20, y: 16 },
        }
      : process.platform === 'win32'
        ? {
            frame: false,
          }
        : {}),
    ...(process.platform === 'linux' ? { icon: desktopIcon() } : {}),
    webPreferences: {
      preload: join(__dirname, '..', 'preload', 'index.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
    },
  })

  mainWindow.once('ready-to-show', () => {
    if (!START_HIDDEN) showWindowWithFade(true)
  })

  mainWindow.webContents.on('render-process-gone', (_event, details) => {
    if (appLifecycle.isQuitting || !mainWindow || mainWindow.isDestroyed()) return
    console.error(`[desktop] main renderer exited reason=${details.reason} code=${details.exitCode}`)
    const now = Date.now()
    if (now - rendererRecoveryWindowStartedAt > FAILURE_RECOVERY_WINDOW_MS) {
      rendererRecoveryWindowStartedAt = now
      rendererRecoveryCount = 0
    }
    rendererRecoveryCount += 1
    if (rendererRecoveryCount > 1) {
      void loadServiceFailurePage(new Error(`Desktop renderer repeatedly exited (${details.reason})`))
      return
    }
    setTimeout(() => {
      if (!mainWindow || mainWindow.isDestroyed() || appLifecycle.isQuitting) return
      mainWindow.reload()
    }, 250).unref?.()
  })

  mainWindow.on('close', (event) => {
    if (appLifecycle.isQuitting) return
    event.preventDefault()
    cancelWindowFade()
    mainWindow?.hide()
    updateTrayMenu()
  })

  mainWindow.webContents.on('did-fail-load', (_event, errorCode, errorDescription, validatedUrl, isMainFrame) => {
    // Only the linked-server mode has a remote page that can fail to load;
    // local mode reports startup failures from bootstrap(). -3 is ERR_ABORTED.
    if (!isMainFrame || errorCode === -3 || !isServerLinkedMode()) return
    console.error(`[desktop] failed to load ${validatedUrl}: ${errorDescription} (${errorCode})`)
    void loadServiceFailurePage(new Error(`${validatedUrl}: ${errorDescription} (${errorCode})`))
  })

  mainWindow.on('show', updateTrayMenu)
  mainWindow.on('hide', updateTrayMenu)
  mainWindow.on('maximize', () => notifyWindowStateChanged(mainWindow))
  mainWindow.on('unmaximize', () => notifyWindowStateChanged(mainWindow))
  mainWindow.on('restore', () => notifyWindowStateChanged(mainWindow))

  installSelectionContextMenu(mainWindow)

  // External links → system browser
  mainWindow.webContents.setWindowOpenHandler(({ url, frameName }) => {
    const agentLinkPopup = groupChatAgentLinkPopupResponse(url, frameName)
    if (agentLinkPopup) return agentLinkPopup
    if (isTrustedDesktopAppUrl(url, serverUrl)) {
      return { action: 'allow' }
    }
    shell.openExternal(url).catch(() => undefined)
    return { action: 'deny' }
  })

  // If the Web UI server is already up (re-opening window after close on
  // macOS), go straight to it. Otherwise show a loading splash; bootstrap()
  // will swap in the real URL once the server is ready.
  if (serverUrl) {
    await mainWindow.loadURL(mainRouteUrl() || serverUrl)
  } else {
    await mainWindow.loadURL(splashHtml(t('runtime.checking')))
  }
  updateTrayMenu()
}

function normalizeChatWindowValue(value: unknown, maxLength: number): string | null {
  if (typeof value !== 'string') return null
  const normalized = value.trim()
  if (!normalized || normalized.length > maxLength || /[\u0000-\u001f\u007f]/.test(normalized)) return null
  return normalized
}

async function openChatWindow(sessionIdInput: unknown, profileInput?: unknown): Promise<void> {
  const sessionId = normalizeChatWindowValue(sessionIdInput, 512)
  if (!sessionId) throw new Error('Invalid chat session ID')
  const profile = profileInput == null ? undefined : normalizeChatWindowValue(profileInput, 200) || undefined
  const windowKey = `${profile || ''}\u0000${sessionId}`

  const existing = chatWindows.get(windowKey)
  if (existing && !existing.isDestroyed()) {
    if (existing.isMinimized()) existing.restore()
    existing.show()
    existing.focus()
    return
  }

  const url = chatRouteUrl(sessionId, profile)
  if (!url) throw new Error('Desktop Web UI is not ready')

  const chatWindow = new BrowserWindow({
    width: 620,
    height: 760,
    minWidth: 620,
    minHeight: 480,
    title: 'Core Hub',
    backgroundColor: '#1a1a1a',
    autoHideMenuBar: true,
    show: false,
    ...(process.platform === 'darwin'
      ? {
          titleBarStyle: 'hiddenInset' as const,
          trafficLightPosition: { x: 20, y: 16 },
        }
      : process.platform === 'win32'
        ? {
            titleBarStyle: 'hidden' as const,
            titleBarOverlay: { height: 46 },
          }
        : {}),
    ...(process.platform === 'linux' ? { icon: desktopIcon() } : {}),
    webPreferences: {
      preload: join(__dirname, '..', 'preload', 'index.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
      additionalArguments: ['--hermes-window-kind=chat'],
    },
  })
  chatWindows.set(windowKey, chatWindow)

  chatWindow.once('ready-to-show', () => {
    if (chatWindow.isDestroyed()) return
    chatWindow.show()
    chatWindow.focus()
  })
  chatWindow.on('maximize', () => notifyWindowStateChanged(chatWindow))
  chatWindow.on('unmaximize', () => notifyWindowStateChanged(chatWindow))
  chatWindow.on('restore', () => notifyWindowStateChanged(chatWindow))
  chatWindow.on('closed', () => {
    if (chatWindows.get(windowKey) === chatWindow) chatWindows.delete(windowKey)
  })

  installSelectionContextMenu(chatWindow)
  chatWindow.webContents.setWindowOpenHandler(({ url: targetUrl, frameName }) => {
    const agentLinkPopup = groupChatAgentLinkPopupResponse(targetUrl, frameName)
    if (agentLinkPopup) return agentLinkPopup
    if (/^(https?:|mailto:)/i.test(targetUrl)) {
      shell.openExternal(targetUrl).catch(() => undefined)
    }
    return { action: 'deny' }
  })

  await chatWindow.loadURL(url)
}

async function initializeDesktopBrowser(): Promise<void> {
  if (!mainWindow || mainWindow.isDestroyed() || browserManager) return
  const root = join(webUiHome(), 'desktop-browser')
  const manager = new BrowserManager(mainWindow, root, {
    selectElementLabel: t('browser.selectElement'),
    selectRegionLabel: t('browser.selectRegion'),
    onAnnotationRequest: (tabId, mode) => {
      browserBroker?.revokeTab(tabId)
      if (mainWindow && !mainWindow.isDestroyed()) {
        mainWindow.webContents.send(BROWSER_ANNOTATION_REQUEST_CHANNEL, { tabId, mode })
      }
    },
  })
  const broker = new BrowserBroker(manager, root)
  try {
    await manager.initialize()
    manager.onStateChange(state => {
      if (mainWindow && !mainWindow.isDestroyed()) mainWindow.webContents.send(BROWSER_STATE_CHANGE_CHANNEL, state)
    })
    await broker.start()
    browserManager = manager
    browserBroker = broker
  } catch (error) {
    await broker.stop().catch(() => undefined)
    await manager.destroy()
    throw error
  }
}

function installMicrophonePermissionHandler() {
  session.defaultSession.setPermissionRequestHandler((webContents, permission, callback, details) => {
    const isMainRenderer = !!mainWindow
      && !mainWindow.isDestroyed()
      && webContents === mainWindow.webContents
    const isChatRenderer = [...chatWindows.values()].some(window => (
      !window.isDestroyed() && webContents === window.webContents
    ))
    const isTrustedRenderer = isMainRenderer || isChatRenderer
    if (permission !== 'media') {
      callback(isTrustedRenderer)
      return
    }

    const mediaTypes = ('mediaTypes' in details ? details.mediaTypes : undefined) ?? []
    const requestsAudio = mediaTypes.length ? mediaTypes.includes('audio') : true

    if (!isTrustedRenderer || !requestsAudio) {
      callback(false)
      return
    }

    if (process.platform !== 'darwin') {
      callback(true)
      return
    }

    const status = systemPreferences.getMediaAccessStatus('microphone')
    if (status === 'granted') {
      callback(true)
      return
    }
    if (status === 'denied' || status === 'restricted') {
      callback(false)
      return
    }
    void systemPreferences.askForMediaAccess('microphone')
      .then(granted => callback(granted))
      .catch(() => callback(false))
  })
}

function splashHtml(label = t('desktop.startingLocalServices')): string {
  const startingLabel = escapeHtml(label)
  const pageBackground = process.platform === 'win32' ? 'transparent' : '#1a1a1a'
  const html = `<!doctype html><html><head><meta charset="utf-8"><title>Core Hub</title>
<style>
  html,body{margin:0;height:100%;background:${pageBackground};color:#e5e5e5;font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Helvetica,Arial,sans-serif;-webkit-app-region:drag;}
  .surface{height:100%;background:#1a1a1a}
  .wrap{display:flex;flex-direction:column;align-items:center;justify-content:center;height:100%;gap:20px}
  .dot{width:10px;height:10px;border-radius:50%;background:#888;animation:pulse 1.2s ease-in-out infinite}
  @keyframes pulse{0%,100%{opacity:.3}50%{opacity:1}}
  .row{display:flex;gap:8px}
  .row .dot:nth-child(2){animation-delay:.2s}.row .dot:nth-child(3){animation-delay:.4s}
  .label{font-size:14px;color:#b8b8b8}
  .detail{min-height:18px;font-size:12px;color:#7f7f7f}
  .progress{width:320px;height:6px;border-radius:999px;background:#2b2b2b;overflow:hidden}
  .bar{width:0;height:100%;background:#d8d8d8;transition:width .18s ease}
  .bar.indeterminate{width:40%;animation:progress 1.2s ease-in-out infinite;transition:none}
  @keyframes progress{0%{transform:translateX(-110%)}100%{transform:translateX(360%)}}
  h1{font-weight:500;margin:0;font-size:18px}
</style></head><body><main class="surface"><div class="wrap">
<h1>Core Hub</h1>
<div class="row"><div class="dot"></div><div class="dot"></div><div class="dot"></div></div>
<div id="label" class="label">${startingLabel}</div>
<div class="progress"><div id="bar" class="bar indeterminate"></div></div>
<div id="detail" class="detail"></div>
</div></main></body></html>`
  return 'data:text/html;charset=utf-8,' + encodeURIComponent(html)
}

async function showShutdownSplash() {
  if (!mainWindow || mainWindow.isDestroyed()) return
  cancelWindowFade()
  try {
    await mainWindow.loadURL(splashHtml(t('desktop.shuttingDown')))
    if (mainWindow.isMinimized()) mainWindow.restore()
    mainWindow.setOpacity(1)
    mainWindow.show()
    updateTrayMenu()
  } catch {
    /* best effort during app shutdown */
  }
}

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, char => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  }[char] || char))
}

function resolveRuntimeSourceLogo(): string {
  const candidates = [
    join(webuiDir(), 'dist', 'client', 'logo.png'),
    join(webuiDir(), 'packages', 'client', 'public', 'logo.png'),
    join(webuiDir(), 'logo.png'),
    desktopIcon(),
  ]
  return candidates.find(candidate => existsSync(candidate)) || desktopIcon()
}

function runtimeSourceLogoDataUri(): string {
  const logoPath = resolveRuntimeSourceLogo()
  try {
    const image = nativeImage.createFromPath(logoPath)
    if (image.isEmpty()) return ''
    return image.resize({ width: 68, height: 68, quality: 'best' }).toDataURL()
  } catch {
    return ''
  }
}

function runtimeSourceHtml(errorMessage?: string): string {
  const safeError = errorMessage ? escapeHtml(errorMessage) : ''
  const logoUrl = runtimeSourceLogoDataUri()
  const pageBackground = process.platform === 'win32' ? 'transparent' : '#191919'
  const errorBlock = safeError
    ? `<section class="error" aria-live="polite">
        <div class="error-title">${escapeHtml(t('desktop.downloadFailed'))}</div>
        <pre>${safeError}</pre>
       </section>`
    : ''
  const html = `<!doctype html><html><head><meta charset="utf-8"><title>Core Hub</title>
<style>
  :root{color-scheme:dark}
  *{box-sizing:border-box}
  html,body{margin:0;width:100%;height:100%;background:${pageBackground};color:#f1f1f1;font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Helvetica,Arial,sans-serif;}
  body{min-height:100%;-webkit-app-region:drag;}
  .surface{width:100%;height:100%;display:grid;place-items:center;padding:32px;background:#191919}
  .wrap{width:min(720px,100%);display:flex;flex-direction:column;align-items:center;gap:22px;text-align:center}
  .brand{display:flex;align-items:center;gap:10px;color:#f6f6f6}
  .mark{width:34px;height:34px;border-radius:8px;object-fit:contain;display:block}
  h1{font-weight:560;margin:0;font-size:22px;line-height:1.25}
  .label{max-width:520px;font-size:14px;line-height:1.6;color:#b9b9b9;margin:0}
  .actions{width:100%;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}
  button{min-height:86px;border:1px solid #4c4c4c;border-radius:8px;background:#242424;color:#f2f2f2;cursor:pointer;padding:16px;text-align:left;display:flex;flex-direction:column;gap:7px;transition:background .14s ease,border-color .14s ease,transform .14s ease;-webkit-app-region:no-drag}
  button:hover{background:#2d2d2d;border-color:#747474;transform:translateY(-1px)}
  button:active{transform:translateY(0)}
  button:focus-visible{outline:2px solid #dcdcdc;outline-offset:3px}
  .button-title{font-size:15px;font-weight:650;line-height:1.2}
  .button-detail{font-size:12px;line-height:1.45;color:#aaaaaa}
  .error{width:100%;text-align:left;background:#241b1b;border:1px solid #6b3939;border-radius:8px;padding:14px}
  .error-title{font-size:13px;font-weight:650;color:#ffc3c3;margin-bottom:8px}
  pre{width:100%;max-height:180px;overflow:auto;white-space:pre-wrap;margin:0;color:#ffaaaa;font:12px/1.5 ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;-webkit-app-region:no-drag}
  @media (max-width:560px){
    .surface{padding:24px}
    .actions{grid-template-columns:1fr}
    button{min-height:78px}
  }
</style></head><body><main class="surface"><div class="wrap">
<div class="brand">${logoUrl ? `<img class="mark" src="${logoUrl}" alt="Core Hub">` : ''}<h1>Core Hub</h1></div>
<p class="label">${escapeHtml(t('desktop.selectRuntimeSource'))}</p>
${errorBlock}
<div class="actions">
  <button id="cf">
    <span class="button-title">${escapeHtml(t('desktop.downloadCloudflareTitle'))}</span>
    <span class="button-detail">${escapeHtml(t('desktop.downloadCloudflareDetail'))}</span>
  </button>
  <button id="github">
    <span class="button-title">${escapeHtml(t('desktop.downloadGithubTitle'))}</span>
    <span class="button-detail">${escapeHtml(t('desktop.downloadGithubDetail'))}</span>
  </button>
</div>
<script>
  document.getElementById('cf')?.addEventListener('click', () => {
    window.hermesDesktop?.retryBootstrap?.('cf')
  })
  document.getElementById('github')?.addEventListener('click', () => {
    window.hermesDesktop?.retryBootstrap?.('github')
  })
</script>
</div></main></body></html>`
  return 'data:text/html;charset=utf-8,' + encodeURIComponent(html)
}

function envRuntimeDownloadSource(): RuntimeDownloadSource | undefined {
  const source = process.env.HERMES_DESKTOP_RUNTIME_SOURCE?.trim().toLowerCase()
  return source === 'cf' || source === 'github' ? source : undefined
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  const units = ['KB', 'MB', 'GB']
  let value = bytes / 1024
  let unit = units[0]
  for (let i = 1; i < units.length && value >= 1024; i += 1) {
    value /= 1024
    unit = units[i]
  }
  return `${value.toFixed(value >= 100 ? 0 : 1)} ${unit}`
}

function updateSplash(progress: RuntimeProgress) {
  if (!mainWindow || mainWindow.isDestroyed()) return
  const label = progress.message
  const percent = typeof progress.percent === 'number' ? Math.round(progress.percent) : null
  let detail = progress.detail || ''
  if (progress.receivedBytes && progress.totalBytes) {
    detail = `${formatBytes(progress.receivedBytes)} / ${formatBytes(progress.totalBytes)}`
    if (percent !== null) detail += ` (${percent}%)`
  } else if (percent !== null) {
    detail = `${percent}%`
  }

  mainWindow.webContents.executeJavaScript(`
    {
      const label = document.getElementById('label');
      const detail = document.getElementById('detail');
      const bar = document.getElementById('bar');
      if (label) label.textContent = ${JSON.stringify(label)};
      if (detail) detail.textContent = ${JSON.stringify(detail)};
      if (bar) {
        bar.classList.toggle('indeterminate', ${JSON.stringify(percent === null)});
        bar.style.width = ${JSON.stringify(percent === null ? '' : `${percent}%`)};
      }
    }
  `).catch(() => undefined)
}

async function installPackagedCommandShims(): Promise<void> {
  if (!app.isPackaged) return

  const installs = [
    installHermesStudioCliShim({
      nodePath: bundledNode(),
      runtimeVersion: desktopRuntimeVersion(),
      webUiScriptPath: join(webuiDir(), 'bin', 'hermes-web-ui.mjs'),
    }),
    installHermesStudioMcpShim({
      nodePath: bundledNode(),
      scriptPath: join(webuiDir(), 'bin', 'ekko-studio-mcp.mjs'),
      webUiUrl: `http://127.0.0.1:${PORT}`,
    }),
  ]
  const results = await Promise.allSettled(installs)
  for (const result of results) {
    if (result.status === 'rejected') {
      console.warn(
        `[cli-shim] failed to install Core Hub command: `
        + `${result.reason instanceof Error ? result.reason.message : String(result.reason)}`,
      )
      continue
    }
    if (result.value.status === 'skipped') {
      console.warn(`[cli-shim] ${result.value.reason}: ${result.value.shimPath}`)
    }
  }
}

async function bootstrap(source?: RuntimeDownloadSource) {
  if (isBootstrapping) return
  isBootstrapping = true

  if (isServerLinkedMode()) {
    await bootstrapServerLinkedMode()
    return
  }

  try {
    const legacyMigration = await migratePendingLegacyWindowsData()
    if (legacyMigration.completed) {
      console.log('[desktop] migrated legacy Windows Hermes data before starting local services')
    } else if (legacyMigration.retryPending) {
      console.warn(`[desktop] legacy Windows Hermes data migration will retry on the next launch: ${legacyMigration.error || 'unknown error'}`)
    }
    await migratePendingRuntimeRoot(updateSplash)
    repairUpdatedDesktopRuntimeLaunchers()
    const selectedSource = source || envRuntimeDownloadSource()
    const runtimeUrlOverride = !!process.env.HERMES_DESKTOP_RUNTIME_URL?.trim()
    const manifestOverride = !!process.env.HERMES_DESKTOP_RUNTIME_MANIFEST_URL?.trim()
    const forceUpdate = !!process.env.HERMES_DESKTOP_RUNTIME_FORCE_UPDATE
    const runtimeReady = isDesktopRuntimeReady()
    const needsRuntimeWork = !runtimeReady || forceUpdate || runtimeUrlOverride || manifestOverride
    const explicitRuntimeRequest = !!selectedSource || forceUpdate || runtimeUrlOverride || manifestOverride

    // Runtime setup is managed in Studio now. A normal desktop launch must
    // reach the Web UI first so it can detect an existing CLI before offering
    // the Runtime manager. Preserve explicit automation overrides for builds
    // and unattended installations.
    if (needsRuntimeWork && explicitRuntimeRequest) {
      await ensureDesktopRuntime(updateSplash, selectedSource)
    }
    if (isDesktopRuntimeReady()) {
      writeActiveRuntimeVersion()
      await installPackagedCommandShims()
    }
  } catch (err) {
    console.error('Failed to prepare Hermes runtime:', err)
    // Keep Studio available so Runtime recovery can happen from Agent Manager.
  }

  try {
    updateSplash({ stage: 'resolve', message: t('desktop.startingLocalServices') })
    const url = await startWebUiServer(PORT)
    serverUrl = url
    updateTrayMenu()
    if (mainWindow) await mainWindow.loadURL(mainRouteUrl() || url)
    await loadPetWindowRoute()
  } catch (err) {
    console.error('Failed to start Web UI server:', err)
    serverUrl = null
    await loadServiceFailurePage(err)
  } finally {
    isBootstrapping = false
  }
}

async function loadServiceFailurePage(error: unknown): Promise<void> {
  if (!mainWindow || mainWindow.isDestroyed()) return
  const msg = escapeHtml(String(error instanceof Error ? error.message : error))
  const pageBackground = process.platform === 'win32' ? 'transparent' : '#1a1a1a'
  const html = `<html><body style="margin:0;font-family:system-ui;background:${pageBackground};color:#eee">
    <main style="min-height:100vh;padding:32px;background:#1a1a1a;box-sizing:border-box">
      <h2>${escapeHtml(t(isServerLinkedMode() ? 'desktop.failedReachServer' : 'desktop.failedStartServices'))}</h2>
      <pre style="white-space:pre-wrap;color:#f88">${msg}</pre>
      <div style="display:flex;gap:10px;flex-wrap:wrap">
        <button id="retry" style="padding:8px 14px;cursor:pointer">${escapeHtml(t('common.retry'))}</button>
        <button id="mode" style="padding:8px 14px;cursor:pointer">${escapeHtml(t('desktop.changeConnectionMode'))}</button>
      </div>
      <script>
        document.getElementById('retry').addEventListener('click', async function () {
          this.disabled = true
          try { await window.hermesDesktop.retryBootstrap() } finally { this.disabled = false }
        })
        document.getElementById('mode').addEventListener('click', function () {
          window.hermesDesktop?.desktopMode?.openSettings?.()
        })
      </script>
    </main>
  </body></html>`
  await mainWindow.loadURL('data:text/html;charset=utf-8,' + encodeURIComponent(html)).catch(loadError => {
    console.error('[desktop] failed to display Web UI failure page:', loadError)
  })
}

async function recoverUnexpectedWebUiExit(details: { code: number | null; signal: NodeJS.Signals | null }): Promise<void> {
  if (appLifecycle.isQuitting) return
  serverUrl = null
  updateTrayMenu()

  const now = Date.now()
  if (now - unexpectedWebUiExitWindowStartedAt > FAILURE_RECOVERY_WINDOW_MS) {
    unexpectedWebUiExitWindowStartedAt = now
    unexpectedWebUiExitCount = 0
  }
  unexpectedWebUiExitCount += 1
  const error = new Error(`Web UI server exited unexpectedly code=${details.code} signal=${details.signal}`)
  if (unexpectedWebUiExitCount > 1 || isBootstrapping) {
    await loadServiceFailurePage(error)
    return
  }

  console.warn('[desktop] restarting Web UI once after an unexpected exit')
  await new Promise(resolveDelay => setTimeout(resolveDelay, 500))
  await bootstrap()
  if (!serverUrl) await loadServiceFailurePage(error)
}

ipcMain.handle('hermes-desktop:get-token', () => (isServerLinkedMode() ? '' : getToken()))
ipcMain.handle('hermes-desktop:restart-app', event => {
  if (!mainWindow || mainWindow.isDestroyed() || event.sender !== mainWindow.webContents) {
    throw new Error('Desktop restart can only be requested from the main window')
  }
  return scheduleAppRestart()
})
ipcMain.handle('hermes-desktop:open-chat-window', (event, sessionId?: unknown, profile?: unknown) => {
  if (!mainWindow || mainWindow.isDestroyed() || event.sender !== mainWindow.webContents) {
    throw new Error('Chat windows can only be opened from the main window')
  }
  return openChatWindow(sessionId, profile)
})

function isTrustedDesktopWindowSender(sender: WebContents): boolean {
  const windows = [mainWindow, petWindow, ...chatWindows.values()]
  return windows.some(window => window && !window.isDestroyed() && window.webContents === sender)
}

ipcMain.handle('hermes-desktop:open-external-url', async (event, url?: unknown) => {
  if (!isTrustedDesktopWindowSender(event.sender)) {
    throw new Error('External URLs can only be opened from a Hermes desktop window')
  }
  const externalUrl = normalizeExternalHttpUrl(url)
  if (!externalUrl) return false

  try {
    await shell.openExternal(externalUrl)
    return true
  } catch {
    return false
  }
})

function browserForEvent(event: IpcMainInvokeEvent): BrowserManager {
  if (!mainWindow || mainWindow.isDestroyed() || event.sender !== mainWindow.webContents) throw new Error('Desktop browser IPC is only available to the main window')
  if (!browserManager) throw new Error('Desktop browser is still starting')
  return browserManager
}

ipcMain.handle('hermes-desktop:browser-get-state', event => browserForEvent(event).state())
ipcMain.handle('hermes-desktop:browser-set-viewport', (event, bounds?: unknown, visible?: unknown) => {
  const input = bounds && typeof bounds === 'object' ? bounds as Partial<BrowserBounds> : {}
  const sanitized: BrowserBounds = {
    x: Number.isFinite(input.x) ? Number(input.x) : 0,
    y: Number.isFinite(input.y) ? Number(input.y) : 0,
    width: Number.isFinite(input.width) ? Number(input.width) : 1,
    height: Number.isFinite(input.height) ? Number(input.height) : 1,
  }
  return browserForEvent(event).setViewport(sanitized, visible === true)
})
ipcMain.handle('hermes-desktop:browser-create-tab', (event, url?: unknown, activate?: unknown) => browserForEvent(event).createTab(typeof url === 'string' ? url : 'about:blank', activate !== false))
ipcMain.handle('hermes-desktop:browser-create-html-preview-tab', (event, html?: unknown, title?: unknown, activate?: unknown) => (
  browserForEvent(event).createHtmlPreviewTab(
    typeof html === 'string' ? html : '',
    typeof title === 'string' ? title : '',
    activate !== false,
  )
))
ipcMain.handle('hermes-desktop:browser-close-tab', (event, tabId?: unknown) => browserForEvent(event).closeTab(String(tabId || '')))
ipcMain.handle('hermes-desktop:browser-activate-tab', (event, tabId?: unknown) => browserForEvent(event).activateTab(String(tabId || '')))
ipcMain.handle('hermes-desktop:browser-navigate', (event, tabId?: unknown, url?: unknown) => {
  const manager = browserForEvent(event)
  browserBroker?.revokeTab(String(tabId || ''))
  return manager.navigate(String(tabId || ''), String(url || ''))
})
ipcMain.handle('hermes-desktop:browser-navigation-action', (event, tabId?: unknown, action?: unknown) => {
  if (action !== 'back' && action !== 'forward' && action !== 'reload' && action !== 'stop') throw new Error('Invalid browser navigation action')
  const manager = browserForEvent(event)
  browserBroker?.revokeTab(String(tabId || ''))
  return manager.navigationAction(String(tabId || ''), action)
})
ipcMain.handle('hermes-desktop:browser-create-profile', (event, input?: unknown) => {
  const value = input && typeof input === 'object' ? input as Record<string, unknown> : {}
  const proxyMode = value.proxyMode === 'system' || value.proxyMode === 'fixed_servers' ? value.proxyMode : 'direct'
  return browserForEvent(event).createProfile({
    name: String(value.name || ''),
    rootDirectory: String(value.rootDirectory || ''),
    proxyMode,
    proxyRules: String(value.proxyRules || ''),
  })
})
ipcMain.handle('hermes-desktop:browser-choose-profile-root-directory', (event, defaultPath?: unknown) => (
  browserForEvent(event).chooseProfileRootDirectory(typeof defaultPath === 'string' ? defaultPath : undefined)
))
ipcMain.handle('hermes-desktop:browser-rename-profile', (event, profileId?: unknown, name?: unknown) => browserForEvent(event).renameProfile(String(profileId || ''), String(name || '')))
ipcMain.handle('hermes-desktop:browser-profile-switch-impact', event => browserForEvent(event).profileSwitchImpact())
ipcMain.handle('hermes-desktop:browser-switch-profile', (event, profileId?: unknown, force?: unknown) => {
  const manager = browserForEvent(event)
  browserBroker?.revokeAll()
  return manager.switchProfile(String(profileId || ''), force === true)
})
ipcMain.handle('hermes-desktop:browser-update-profile', (event, profileId?: unknown, input?: unknown) => {
  const value = input && typeof input === 'object' ? input as Record<string, unknown> : {}
  return browserForEvent(event).updateProfile(String(profileId || ''), {
    ...(typeof value.rootDirectory === 'string' ? { rootDirectory: value.rootDirectory } : {}),
    ...(value.proxyMode === 'direct' || value.proxyMode === 'system' || value.proxyMode === 'fixed_servers'
      ? { proxyMode: value.proxyMode }
      : {}),
    ...(typeof value.proxyRules === 'string' ? { proxyRules: value.proxyRules } : {}),
    ...(typeof value.askBeforeDownload === 'boolean' ? { askBeforeDownload: value.askBeforeDownload } : {}),
    ...(value.downloadConflictPolicy === 'ask' || value.downloadConflictPolicy === 'uniquify'
      ? { downloadConflictPolicy: value.downloadConflictPolicy }
      : {}),
  })
})
ipcMain.handle('hermes-desktop:browser-delete-profile', (event, profileId?: unknown) => browserForEvent(event).deleteProfile(String(profileId || '')))
ipcMain.handle('hermes-desktop:browser-clear-profile-data', (event, profileId?: unknown, kind?: unknown) => {
  if (kind !== 'cache' && kind !== 'site-data' && kind !== 'permission-audit') throw new Error('Invalid browser data type')
  const manager = browserForEvent(event)
  browserBroker?.revokeAll()
  return manager.clearProfileData(String(profileId || ''), kind)
})
ipcMain.handle('hermes-desktop:browser-cancel-download', (event, downloadId?: unknown) => (
  browserForEvent(event).cancelDownload(String(downloadId || ''))
))
ipcMain.handle('hermes-desktop:browser-take-over', (event, tabId?: unknown) => {
  browserForEvent(event)
  browserBroker?.revokeTab(String(tabId || ''))
  return true
})
ipcMain.handle('hermes-desktop:browser-annotate', (event, tabId?: unknown, mode?: unknown) => {
  if (mode !== 'element' && mode !== 'region') throw new Error('Invalid browser annotation mode')
  const manager = browserForEvent(event)
  browserBroker?.revokeTab(String(tabId || ''))
  return manager.annotate(String(tabId || ''), mode)
})
ipcMain.handle('hermes-desktop:browser-cancel-annotation', (event, tabId?: unknown) => browserForEvent(event).cancelAnnotation(String(tabId || '')))
ipcMain.handle('hermes-desktop:browser-update-annotation-note', (event, tabId?: unknown, marker?: unknown, note?: unknown) => {
  const value = Number(marker)
  if (!Number.isSafeInteger(value) || value < 1) throw new Error('Invalid browser annotation marker')
  return browserForEvent(event).updateAnnotationNote(String(tabId || ''), value, String(note || '').slice(0, 500))
})
ipcMain.handle('hermes-desktop:browser-capture-annotations', (event, tabId?: unknown) => browserForEvent(event).captureAnnotations(String(tabId || '')))
ipcMain.handle('hermes-desktop:browser-clear-annotations', (event, tabId?: unknown) => browserForEvent(event).clearAnnotations(String(tabId || '')))
ipcMain.handle('hermes-desktop:browser-remove-annotation', (event, tabId?: unknown, marker?: unknown) => browserForEvent(event).removeAnnotation(String(tabId || ''), Number(marker)))
ipcMain.handle('hermes-desktop:select-runtime-directory', async (_event, defaultPath?: unknown) => {
  const options: OpenDialogOptions = {
    properties: ['openDirectory'],
    defaultPath: typeof defaultPath === 'string' && defaultPath.trim()
      ? defaultPath.trim()
      : runtimeStorageRoot(),
  }
  const result = mainWindow && !mainWindow.isDestroyed()
    ? await dialog.showOpenDialog(mainWindow, options)
    : await dialog.showOpenDialog(options)
  return result.canceled ? null : result.filePaths[0] || null
})
ipcMain.handle('hermes-desktop:get-window-state', event => {
  return windowState(BrowserWindow.fromWebContents(event.sender))
})
ipcMain.handle('hermes-desktop:window-control', (event, action?: unknown) => {
  const target = BrowserWindow.fromWebContents(event.sender)
  if (action !== 'minimize' && action !== 'toggle-maximize' && action !== 'close') return windowState(target)
  return handleWindowControl(target, action)
})
ipcMain.handle('hermes-desktop:get-pet-window-state', () => petWindowState())
ipcMain.handle('hermes-desktop:set-pet-window-bounds', (_event, bounds?: unknown) => {
  const nextBounds = sanitizePetWindowBounds(bounds)
  if (!nextBounds) return petWindowState()
  const target = ensurePetWindow()
  target.setBounds(nextBounds, false)
  return petWindowState()
})
ipcMain.handle('hermes-desktop:set-pet-window-visible', async (_event, visible?: unknown) => {
  if (visible === false) {
    if (!petWindow || petWindow.isDestroyed()) return petWindowState()
    petWindow.hide()
    return petWindowState()
  }
  const fromPetWindow = !!petWindow && !petWindow.isDestroyed() && _event.sender === petWindow.webContents
  await loadPetWindowRoute()
  const target = ensurePetWindow()
  if (!fromPetWindow) target.webContents.send(PET_WINDOW_REFRESH_CHANNEL)
  target.showInactive()
  return petWindowState()
})
function resolveNotificationIcon(icon: unknown): string {
  if (typeof icon !== 'string') return desktopIcon()
  const normalized = icon.trim().replace(/^\/+/, '')
  if (!normalized || normalized.includes('..')) return desktopIcon()

  const candidates = [
    join(webuiDir(), 'dist', 'client', normalized),
    join(webuiDir(), 'dist', normalized),
    join(webuiDir(), 'packages', 'client', 'public', normalized),
    join(webuiDir(), normalized),
  ]
  return candidates.find(candidate => existsSync(candidate)) || desktopIcon()
}

function safeNotificationClickUrl(value: unknown): string | null {
  if (typeof value !== 'string') return null
  return value.startsWith('/hermes/') && !value.includes('..') && !value.includes('\\') ? value : null
}

ipcMain.handle('hermes-desktop:notify-completion', (_event, payload?: { title?: unknown; body?: unknown; icon?: unknown; tag?: unknown; clickUrl?: unknown }) => {
  const supported = Notification.isSupported()
  if (!supported) {
    console.warn('[desktop-notification] Electron notifications are not supported on this system')
    return false
  }

  const title = typeof payload?.title === 'string' && payload.title.trim()
    ? payload.title.trim()
    : 'Core Hub'
  const body = typeof payload?.body === 'string' ? payload.body.trim().slice(0, 240) : ''
  const icon = resolveNotificationIcon(payload?.icon)
  const clickUrl = safeNotificationClickUrl(payload?.clickUrl)
  const notification = new Notification({
    title,
    body,
    icon,
    silent: false,
  })
  activeNotifications.add(notification)
  const releaseNotification = () => {
    activeNotifications.delete(notification)
  }
  notification.on('click', () => {
    releaseNotification()
    if (clickUrl && mainWindow && !mainWindow.isDestroyed()) {
      const target = webUiHashUrl(clickUrl)
      if (target) {
        void mainWindow.loadURL(target)
          .catch(error => console.warn('[desktop-notification] failed to open notification target', error))
          .finally(showMainWindow)
      } else showMainWindow()
      return
    }
    showMainWindow()
  })
  notification.on('close', releaseNotification)
  notification.on('failed', (_event, error) => {
    console.warn('[desktop-notification] notification failed', error)
    releaseNotification()
  })
  notification.show()
  return true
})
ipcMain.handle('hermes-desktop:retry-bootstrap', async (_event, source?: RuntimeDownloadSource) => {
  if (serverUrl) {
    await mainWindow?.loadURL(mainRouteUrl() || serverUrl)
    return
  }
  const selectedSource = source === 'cf' || source === 'github' ? source : undefined
  await mainWindow?.loadURL(splashHtml(t('runtime.downloading')))
  await bootstrap(selectedSource)
})

// ---------------------------------------------------------------------------
// Connection mode: local runtime vs. linked Studio server.
// ---------------------------------------------------------------------------

function desktopModeFilePath(): string {
  return join(app.getPath('userData'), DESKTOP_MODE_FILE_NAME)
}

function isServerLinkedMode(): boolean {
  return desktopMode.mode === 'server' && !!desktopMode.serverUrl
}

function desktopModeSnapshot() {
  return {
    mode: desktopMode.mode,
    serverUrl: desktopMode.serverUrl,
    source: desktopMode.source,
    locked: isDesktopModeLockedByEnv(process.env),
  }
}

async function bootstrapServerLinkedMode(): Promise<void> {
  const url = desktopMode.serverUrl as string
  try {
    updateSplash({ stage: 'resolve', message: t('desktop.connectingToServer', { url }) })
    serverUrl = url
    updateTrayMenu()
    if (mainWindow && !mainWindow.isDestroyed()) await mainWindow.loadURL(mainRouteUrl() || url)
    await loadPetWindowRoute()
    void ensureDeviceAgent().start().catch(err => console.error('[device-agent] failed to start:', err))
  } catch (err) {
    // did-fail-load already swapped in the failure page for a main-frame
    // error; the superseded loadURL then rejects with ERR_ABORTED.
    if (err instanceof Error && err.message.includes('ERR_ABORTED')) return
    console.error('[desktop-mode] failed to open the linked Studio server:', err)
    await loadServiceFailurePage(err)
  } finally {
    isBootstrapping = false
  }
}

function connectionModeHtml(): string {
  const snapshot = desktopModeSnapshot()
  const logoUrl = runtimeSourceLogoDataUri()
  const pageBackground = process.platform === 'win32' ? 'transparent' : '#191919'
  const strings = {
    title: t('mode.title'),
    intro: t('mode.intro'),
    localTitle: t('mode.localTitle'),
    localDetail: t('mode.localDetail'),
    serverTitle: t('mode.serverTitle'),
    serverDetail: t('mode.serverDetail'),
    serverUrlLabel: t('mode.serverUrlLabel'),
    serverUrlPlaceholder: t('mode.serverUrlPlaceholder'),
    testConnection: t('mode.testConnection'),
    testing: t('mode.testing'),
    testOk: t('mode.testOk'),
    testFailed: t('mode.testFailed'),
    apply: t('mode.apply'),
    cancel: t('common.cancel'),
    restarting: t('mode.restarting'),
    lockedByEnv: t('mode.lockedByEnv'),
    currentLocal: t('mode.currentLocal'),
    currentServer: t('mode.currentServer'),
  }
  const current = snapshot.mode === 'server' && snapshot.serverUrl
    ? strings.currentServer.replace('{url}', snapshot.serverUrl)
    : strings.currentLocal
  const dir = isRtlDesktopLocale() ? 'rtl' : 'ltr'
  const html = `<!doctype html><html dir="${dir}"><head><meta charset="utf-8"><title>Core Hub</title>
<style>
  :root{color-scheme:dark}
  *{box-sizing:border-box}
  html,body{margin:0;width:100%;height:100%;background:${pageBackground};color:#f1f1f1;font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Helvetica,Arial,sans-serif;}
  body{min-height:100%;-webkit-app-region:drag;}
  .surface{width:100%;min-height:100%;display:grid;place-items:center;padding:32px;background:#191919}
  .wrap{width:min(680px,100%);display:flex;flex-direction:column;gap:18px}
  .brand{display:flex;align-items:center;gap:10px;color:#f6f6f6;justify-content:center}
  .mark{width:34px;height:34px;border-radius:8px;object-fit:contain;display:block}
  h1{font-weight:560;margin:0;font-size:22px;line-height:1.25;text-align:center}
  p{margin:0;font-size:14px;line-height:1.6;color:#b9b9b9;text-align:center}
  .current{font-size:12px;color:#8f8f8f;text-align:center}
  .options{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}
  label.option{display:flex;flex-direction:column;gap:7px;min-height:96px;border:1px solid #4c4c4c;border-radius:10px;background:#242424;padding:16px;cursor:pointer;-webkit-app-region:no-drag;transition:border-color .14s ease,background .14s ease}
  label.option:hover{background:#2d2d2d;border-color:#747474}
  label.option.selected{border-color:#dcdcdc;background:#2a2a2a}
  label.option input{position:absolute;opacity:0;pointer-events:none}
  .option-title{font-size:15px;font-weight:650;line-height:1.2}
  .option-detail{font-size:12px;line-height:1.45;color:#aaaaaa}
  .server-form{display:flex;flex-direction:column;gap:10px;border:1px solid #333;border-radius:10px;padding:16px;background:#202020;-webkit-app-region:no-drag}
  .server-form[hidden]{display:none}
  .field{display:flex;flex-direction:column;gap:6px;font-size:13px;color:#cfcfcf}
  input[type=url]{width:100%;padding:10px 12px;border-radius:8px;border:1px solid #4c4c4c;background:#151515;color:#f1f1f1;font-size:14px;direction:ltr;text-align:left}
  input[type=url]:focus{outline:2px solid #dcdcdc;outline-offset:1px}
  .row{display:flex;gap:10px;align-items:center;flex-wrap:wrap}
  .status{font-size:12px;min-height:16px;color:#aaaaaa}
  .status.ok{color:#9be29b}.status.err{color:#ffaaaa}
  .actions{display:flex;gap:10px;justify-content:flex-end;-webkit-app-region:no-drag}
  button{padding:9px 16px;border:1px solid #4c4c4c;border-radius:8px;background:#242424;color:#f2f2f2;cursor:pointer;font-size:13px;-webkit-app-region:no-drag}
  button:hover{background:#2d2d2d;border-color:#747474}
  button:disabled{opacity:.5;cursor:default}
  button.primary{background:#e8e8e8;color:#111;border-color:#e8e8e8;font-weight:600}
  button.primary:hover{background:#ffffff}
  .locked{font-size:12px;color:#ffc27a;text-align:center}
  @media (max-width:560px){.surface{padding:24px}.options{grid-template-columns:1fr}}
</style></head><body><main class="surface"><div class="wrap">
<div class="brand">${logoUrl ? `<img class="mark" src="${logoUrl}" alt="Core Hub">` : ''}<h1>${escapeHtml(strings.title)}</h1></div>
<p>${escapeHtml(strings.intro)}</p>
<div class="current">${escapeHtml(current)}</div>
${snapshot.locked ? `<div class="locked">${escapeHtml(strings.lockedByEnv)}</div>` : ''}
<div class="options" role="radiogroup">
  <label class="option" id="opt-local"><input type="radio" name="mode" value="local">
    <span class="option-title">${escapeHtml(strings.localTitle)}</span>
    <span class="option-detail">${escapeHtml(strings.localDetail)}</span>
  </label>
  <label class="option" id="opt-server"><input type="radio" name="mode" value="server">
    <span class="option-title">${escapeHtml(strings.serverTitle)}</span>
    <span class="option-detail">${escapeHtml(strings.serverDetail)}</span>
  </label>
</div>
<div class="server-form" id="server-form" hidden>
  <label class="field" for="server-url">${escapeHtml(strings.serverUrlLabel)}
    <input id="server-url" type="url" autocomplete="off" spellcheck="false" placeholder="${escapeHtml(strings.serverUrlPlaceholder)}">
  </label>
  <div class="row">
    <button id="test" type="button">${escapeHtml(strings.testConnection)}</button>
    <span class="status" id="status" aria-live="polite"></span>
  </div>
</div>
<div class="actions">
  ${isServerLinkedMode() ? `<button id="device-access" type="button" style="margin-inline-end:auto">${escapeHtml(t('tray.deviceAccess'))}</button>` : ''}
  <button id="cancel" type="button">${escapeHtml(strings.cancel)}</button>
  <button id="apply" type="button" class="primary">${escapeHtml(strings.apply)}</button>
</div>
<script>
  const STRINGS = ${JSON.stringify(strings)}
  const SNAPSHOT = ${JSON.stringify(snapshot)}
  document.getElementById('device-access')?.addEventListener('click', () => { window.hermesDesktop?.deviceAgent?.openSettings?.() })
  const api = window.hermesDesktop && window.hermesDesktop.desktopMode
  const form = document.getElementById('server-form')
  const urlInput = document.getElementById('server-url')
  const status = document.getElementById('status')
  const apply = document.getElementById('apply')
  const radios = Array.from(document.querySelectorAll('input[name=mode]'))
  function selectedMode() { const r = radios.find(item => item.checked); return r ? r.value : 'local' }
  function render() {
    const mode = selectedMode()
    document.getElementById('opt-local').classList.toggle('selected', mode === 'local')
    document.getElementById('opt-server').classList.toggle('selected', mode === 'server')
    form.hidden = mode !== 'server'
    apply.disabled = SNAPSHOT.locked || (mode === 'server' && !urlInput.value.trim())
  }
  radios.forEach(r => r.addEventListener('change', render))
  urlInput.addEventListener('input', () => { status.textContent = ''; status.className = 'status'; render() })
  const initial = radios.find(r => r.value === SNAPSHOT.mode) || radios[0]
  initial.checked = true
  if (SNAPSHOT.serverUrl) urlInput.value = SNAPSHOT.serverUrl
  render()
  document.getElementById('test').addEventListener('click', async function () {
    this.disabled = true
    status.className = 'status'
    status.textContent = STRINGS.testing
    try {
      const result = await api.probe(urlInput.value)
      if (result.ok) { status.className = 'status ok'; status.textContent = STRINGS.testOk; if (result.url) urlInput.value = result.url }
      else { status.className = 'status err'; status.textContent = STRINGS.testFailed.replace('{error}', result.error || 'unknown') }
    } catch (error) {
      status.className = 'status err'
      status.textContent = STRINGS.testFailed.replace('{error}', String(error && error.message || error))
    } finally { this.disabled = false }
  })
  apply.addEventListener('click', async function () {
    this.disabled = true
    try {
      await api.apply({ mode: selectedMode(), serverUrl: urlInput.value })
    } catch (error) {
      status.className = 'status err'
      status.textContent = String(error && error.message || error)
      this.disabled = false
    }
  })
  document.getElementById('cancel').addEventListener('click', () => { api.close() })
</script>
</div></main></body></html>`
  return 'data:text/html;charset=utf-8,' + encodeURIComponent(html)
}

async function openConnectionModePage(): Promise<void> {
  if (!mainWindow || mainWindow.isDestroyed()) await createWindow()
  showMainWindow()
  await mainWindow?.loadURL(connectionModeHtml())
}

async function returnToCurrentUi(): Promise<void> {
  if (!mainWindow || mainWindow.isDestroyed()) return
  if (serverUrl) {
    await mainWindow.loadURL(mainRouteUrl() || serverUrl)
    return
  }
  await mainWindow.loadURL(splashHtml())
  void bootstrap()
}

function requireMainWindowSender(event: IpcMainInvokeEvent, action: string): void {
  if (!mainWindow || mainWindow.isDestroyed() || event.sender !== mainWindow.webContents) {
    throw new Error(`${action} can only be requested from the main window`)
  }
}

// Synchronous variant so the preload can learn the mode before the page runs.
ipcMain.on('hermes-desktop:get-desktop-mode', event => {
  event.returnValue = desktopModeSnapshot()
})
ipcMain.handle('hermes-desktop:get-desktop-mode', () => desktopModeSnapshot())
ipcMain.handle('hermes-desktop:probe-studio-server', async (event, url?: unknown) => {
  if (!isTrustedDesktopWindowSender(event.sender)) throw new Error('Server probes can only be requested from a Hermes desktop window')
  return probeStudioServer(url)
})
ipcMain.handle('hermes-desktop:open-desktop-mode-settings', async event => {
  if (!isTrustedDesktopWindowSender(event.sender)) throw new Error('Connection settings can only be opened from a Hermes desktop window')
  await openConnectionModePage()
  return true
})
ipcMain.handle('hermes-desktop:close-desktop-mode-settings', async event => {
  requireMainWindowSender(event, 'Closing connection settings')
  await returnToCurrentUi()
  return true
})
ipcMain.handle('hermes-desktop:set-desktop-mode', async (event, input?: unknown) => {
  requireMainWindowSender(event, 'Changing the connection mode')
  if (isDesktopModeLockedByEnv(process.env)) throw new Error(t('mode.lockedByEnv'))
  const record = (input && typeof input === 'object' ? input : {}) as Record<string, unknown>
  const mode = isDesktopMode(record.mode) ? record.mode : 'local'
  const serverUrlInput = normalizeStudioServerUrl(record.serverUrl)
  if (mode === 'server' && !serverUrlInput) throw new Error('A valid http(s) server address is required')
  const saved = writeDesktopModeConfig(desktopModeFilePath(), { mode, serverUrl: serverUrlInput })
  console.log(`[desktop-mode] saved mode=${saved.mode}${saved.serverUrl ? ` server=${saved.serverUrl}` : ''}; restarting`)
  if (mainWindow && !mainWindow.isDestroyed()) {
    await mainWindow.loadURL(splashHtml(t('mode.restarting'))).catch(() => undefined)
  }
  return scheduleAppRestart(300)
})

// ---------------------------------------------------------------------------
// Device Agent: the linked server operates this machine (commands + files).
// ---------------------------------------------------------------------------

function deviceAgentFile(name: string): string {
  return join(app.getPath('userData'), name)
}

async function askExecApproval(request: { command: string; args: string[]; cwd: string }): Promise<ExecApprovalDecision> {
  const commandLine = [request.command, ...request.args].join(' ')
  showMainWindow()
  const options: MessageBoxOptions = {
    type: 'question',
    buttons: [t('agent.allow'), t('agent.allowSession'), t('agent.deny')],
    defaultId: 2,
    cancelId: 2,
    noLink: true,
    title: t('agent.approveTitle'),
    message: t('agent.approveMessage'),
    detail: t('agent.approveDetail', { command: commandLine.slice(0, 2000), cwd: request.cwd }),
  }
  const result = await showDesktopMessageBox(options)
  if (result.response === 0) return 'allow'
  if (result.response === 1) return 'allow-session'
  return 'deny'
}

async function askScreenApproval(): Promise<boolean> {
  showMainWindow()
  const result = await showDesktopMessageBox({
    type: 'warning',
    buttons: [t('agent.allowSession'), t('agent.deny')],
    defaultId: 1,
    cancelId: 1,
    noLink: true,
    title: t('agent.screenApproveTitle'),
    message: t('agent.screenApproveMessage'),
    detail: t('agent.screenApproveDetail'),
  })
  return result.response === 0
}

async function askAppApproval(appDef: SharedAppDefinition): Promise<boolean> {
  showMainWindow()
  const result = await showDesktopMessageBox({
    type: 'question',
    buttons: [t('agent.allowSession'), t('agent.deny')],
    defaultId: 1,
    cancelId: 1,
    noLink: true,
    title: t('agent.appApproveTitle'),
    message: t('agent.appApproveMessage'),
    detail: t('agent.appApproveDetail', { app: appDef.name }),
  })
  return result.response === 0
}

async function proxyToDesktopBrowserBroker(request: DeviceAgentProxyRequest): Promise<DeviceAgentProxyResponse> {
  const descriptor = browserBroker?.currentDescriptor()
  if (!descriptor) throw new Error('The desktop agent browser is not running')
  const base = new URL(descriptor.endpoint)
  base.pathname = request.path
  base.search = ''
  const headers: Record<string, string> = { ...request.headers }
  // The server gateway authenticates with its own descriptor token; the local
  // broker expects this app's token for session creation.
  if (request.path === '/v1/session') headers.authorization = `Bearer ${descriptor.token}`
  delete headers.host
  delete headers['content-length']
  const response = await fetch(base, {
    method: request.method || 'POST',
    headers,
    body: request.body.length ? new Uint8Array(request.body) : undefined,
    signal: AbortSignal.timeout(50_000),
  })
  const body = Buffer.from(await response.arrayBuffer())
  const responseHeaders: Record<string, string> = {}
  response.headers.forEach((value, name) => { responseHeaders[name] = value })
  return { status: response.status, headers: responseHeaders, body }
}

async function captureDeviceScreen(options: { displayId?: string; maxWidth?: number }): Promise<DeviceAgentScreenCapture> {
  const displays = screen.getAllDisplays()
  const primary = screen.getPrimaryDisplay()
  const target = (options.displayId && displays.find(display => String(display.id) === options.displayId)) || primary
  const maxWidth = options.maxWidth && options.maxWidth > 0 ? Math.min(4096, Math.floor(options.maxWidth)) : 1600
  const scale = Math.min(1, maxWidth / target.size.width)
  const sources = await desktopCapturer.getSources({
    types: ['screen'],
    thumbnailSize: { width: Math.round(target.size.width * scale), height: Math.round(target.size.height * scale) },
  })
  const source = sources.find(item => item.display_id === String(target.id)) || sources[0]
  if (!source) throw new Error('No screen is available to capture (check Screen Recording permission)')
  const image = source.thumbnail
  const size = image.getSize()
  if (size.width === 0 || size.height === 0) throw new Error('Screen capture is empty (Screen Recording permission may be missing)')
  return {
    media_type: 'image/png',
    data: image.toPNG().toString('base64'),
    width: size.width,
    height: size.height,
    display_id: String(target.id),
    displays: displays.map(display => ({
      id: String(display.id),
      width: display.size.width,
      height: display.size.height,
      scale: display.scaleFactor,
      primary: display.id === primary.id,
    })),
  }
}

async function performDeviceScreenAction(raw: Record<string, unknown>): Promise<void> {
  const action = parseScreenAction(raw)
  if (!action) throw new Error('Invalid screen action')
  if ('x' in action && 'y' in action) {
    // Coordinates arrive in screenshot pixels; map them to the display when the shot was scaled.
    const width = typeof raw.image_width === 'number' ? raw.image_width : 0
    const display = screen.getPrimaryDisplay()
    if (width > 0 && width !== display.size.width) {
      const factor = display.size.width / width
      action.x = Math.round(action.x * factor)
      action.y = Math.round(action.y * factor)
    }
  }
  await runScreenAction(action)
}

function screenControlOverlayHtml(): string {
  const dir = isRtlDesktopLocale() ? 'rtl' : 'ltr'
  return 'data:text/html;charset=utf-8,' + encodeURIComponent(`<!doctype html><html dir="${dir}"><head><meta charset="utf-8"><style>
  html,body{margin:0;background:transparent;font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Helvetica,Arial,sans-serif;-webkit-app-region:drag;overflow:hidden}
  .bar{display:flex;align-items:center;gap:12px;padding:8px 14px;background:#b91c1c;color:#fff;border-radius:10px;font-size:13px;font-weight:600;box-shadow:0 4px 18px rgba(0,0,0,.35)}
  .dot{width:10px;height:10px;border-radius:50%;background:#fff;animation:blink 1s ease-in-out infinite}
  @keyframes blink{0%,100%{opacity:1}50%{opacity:.25}}
  button{-webkit-app-region:no-drag;border:1px solid rgba(255,255,255,.6);background:rgba(255,255,255,.15);color:#fff;border-radius:6px;padding:4px 10px;font-size:12px;cursor:pointer}
  button:hover{background:rgba(255,255,255,.3)}
</style></head><body><div class="bar"><span class="dot"></span><span>${escapeHtml(t('agent.overlayLabel'))}</span>
<button id="stop">${escapeHtml(t('agent.overlayStop'))}</button></div>
<script>document.getElementById('stop').addEventListener('click', () => { window.hermesDesktop?.deviceAgent?.stopScreen?.() })</script></body></html>`)
}

function updateScreenControlOverlay(active: boolean): void {
  if (!active) {
    if (screenControlOverlay && !screenControlOverlay.isDestroyed()) screenControlOverlay.close()
    screenControlOverlay = null
    return
  }
  if (screenControlOverlay && !screenControlOverlay.isDestroyed()) return
  const display = screen.getPrimaryDisplay()
  const width = 360
  const height = 44
  const overlay = new BrowserWindow({
    width,
    height,
    x: Math.round(display.workArea.x + (display.workArea.width - width) / 2),
    y: display.workArea.y + 8,
    frame: false,
    transparent: true,
    resizable: false,
    movable: true,
    minimizable: false,
    maximizable: false,
    fullscreenable: false,
    skipTaskbar: true,
    alwaysOnTop: true,
    focusable: false,
    show: false,
    webPreferences: {
      preload: join(__dirname, '..', 'preload', 'index.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
    },
  })
  overlay.setAlwaysOnTop(true, 'screen-saver')
  overlay.setVisibleOnAllWorkspaces(true, { visibleOnFullScreen: true })
  overlay.on('closed', () => { if (screenControlOverlay === overlay) screenControlOverlay = null })
  overlay.once('ready-to-show', () => overlay.showInactive())
  void overlay.loadURL(screenControlOverlayHtml())
  screenControlOverlay = overlay
}

function ensureDeviceAgent(): DeviceAgent {
  if (deviceAgent) return deviceAgent
  const agent = new DeviceAgent({
    identityFile: deviceAgentFile(DEVICE_AGENT_IDENTITY_FILE_NAME),
    loadConfig: () => readDeviceAgentConfig(deviceAgentFile(DEVICE_AGENT_CONFIG_FILE_NAME)),
    saveConfig: config => writeDeviceAgentConfig(deviceAgentFile(DEVICE_AGENT_CONFIG_FILE_NAME), config),
    serverUrl: () => (isServerLinkedMode() ? desktopMode.serverUrl : null),
    approveExec: askExecApproval,
    approveScreen: askScreenApproval,
    approveApp: askAppApproval,
    browserProxy: proxyToDesktopBrowserBroker,
    screen: { capture: captureDeviceScreen, action: performDeviceScreenAction },
    audit: entry => appendDeviceAgentAudit(deviceAgentFile(DEVICE_AGENT_AUDIT_FILE_NAME), entry),
    appVersion: app.getVersion(),
    log: message => console.log(message),
  })
  agent.on('state', (state: DeviceAgentState) => {
    if (mainWindow && !mainWindow.isDestroyed()) mainWindow.webContents.send(DEVICE_AGENT_STATE_CHANNEL, deviceAgentSnapshot(state))
    updateScreenControlOverlay(state.screenSessionApproved && state.status === 'connected')
  })
  deviceAgent = agent
  return agent
}

function deviceAgentSnapshot(state: DeviceAgentState = ensureDeviceAgent().getState()) {
  return {
    linked: isServerLinkedMode(),
    ...state,
    audit: readDeviceAgentAudit(deviceAgentFile(DEVICE_AGENT_AUDIT_FILE_NAME), 20),
  }
}

function deviceAgentHtml(): string {
  const logoUrl = runtimeSourceLogoDataUri()
  const pageBackground = process.platform === 'win32' ? 'transparent' : '#191919'
  const dir = isRtlDesktopLocale() ? 'rtl' : 'ltr'
  const strings = {
    title: t('agent.title'),
    intro: t('agent.intro'),
    notLinked: t('agent.notLinked'),
    statusLabel: t('agent.statusLabel'),
    status: {
      disabled: t('agent.status.disabled'),
      unpaired: t('agent.status.unpaired'),
      pending: t('agent.status.pending'),
      rejected: t('agent.status.rejected'),
      blocked: t('agent.status.blocked'),
      connecting: t('agent.status.connecting'),
      connected: t('agent.status.connected'),
      offline: t('agent.status.offline'),
      error: t('agent.status.error'),
    },
    pairingLabel: t('agent.pairingLabel'),
    pairingHint: t('agent.pairingHint'),
    pairingPlaceholder: t('agent.pairingPlaceholder'),
    sendPairing: t('agent.sendPairing'),
    pendingHint: t('agent.pendingHint'),
    enabled: t('agent.enabled'),
    capExec: t('agent.capExec'),
    capFiles: t('agent.capFiles'),
    capBrowser: t('agent.capBrowser'),
    capScreen: t('agent.capScreen'),
    approval: t('agent.approval'),
    approvalAsk: t('agent.approvalAsk'),
    approvalAlways: t('agent.approvalAlways'),
    folders: t('agent.folders'),
    foldersHint: t('agent.foldersHint'),
    addFolder: t('agent.addFolder'),
    remove: t('agent.remove'),
    noFolders: t('agent.noFolders'),
    audit: t('agent.audit'),
    noAudit: t('agent.noAudit'),
    unlink: t('agent.unlink'),
    back: t('agent.back'),
  }
  const html = `<!doctype html><html dir="${dir}"><head><meta charset="utf-8"><title>Core Hub</title>
<style>
  :root{color-scheme:dark}
  *{box-sizing:border-box}
  html,body{margin:0;width:100%;min-height:100%;background:${pageBackground};color:#f1f1f1;font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Helvetica,Arial,sans-serif;}
  body{-webkit-app-region:drag;}
  .surface{width:100%;min-height:100vh;display:flex;justify-content:center;padding:32px;background:#191919;overflow:auto}
  .wrap{width:min(720px,100%);display:flex;flex-direction:column;gap:16px;-webkit-app-region:no-drag}
  .brand{display:flex;align-items:center;gap:10px;justify-content:center}
  .mark{width:34px;height:34px;border-radius:8px;object-fit:contain}
  h1{font-weight:560;margin:0;font-size:22px;line-height:1.25;text-align:center}
  p{margin:0;font-size:14px;line-height:1.6;color:#b9b9b9}
  .card{border:1px solid #333;border-radius:10px;padding:16px;background:#202020;display:flex;flex-direction:column;gap:10px}
  .card h2{margin:0;font-size:15px;font-weight:650}
  .status{display:flex;gap:10px;align-items:center;font-size:14px}
  .pill{padding:3px 10px;border-radius:999px;background:#333;font-size:12px}
  .pill.ok{background:#1f4d2b;color:#9be29b}.pill.warn{background:#4d3d1f;color:#ffc27a}.pill.err{background:#4d1f1f;color:#ffaaaa}
  .err{font-size:12px;color:#ffaaaa;white-space:pre-wrap}
  .hint{font-size:12px;color:#8f8f8f}
  input[type=text]{width:100%;padding:10px 12px;border-radius:8px;border:1px solid #4c4c4c;background:#151515;color:#f1f1f1;font-size:14px;direction:ltr;text-align:left}
  label.row{display:flex;gap:10px;align-items:center;font-size:14px;cursor:pointer}
  select{padding:8px 10px;border-radius:8px;border:1px solid #4c4c4c;background:#151515;color:#f1f1f1;font-size:13px}
  ul{list-style:none;margin:0;padding:0;display:flex;flex-direction:column;gap:6px}
  li{display:flex;justify-content:space-between;gap:10px;align-items:center;font-size:13px;background:#171717;border-radius:8px;padding:8px 10px}
  li code{direction:ltr;unicode-bidi:isolate;word-break:break-all;font:12px ui-monospace,SFMono-Regular,Menlo,Consolas,monospace}
  .audit li{font:12px ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;direction:ltr;text-align:left;justify-content:flex-start;gap:12px}
  .audit .bad{color:#ffaaaa}.audit .good{color:#9be29b}
  button{padding:8px 14px;border:1px solid #4c4c4c;border-radius:8px;background:#242424;color:#f2f2f2;cursor:pointer;font-size:13px}
  button:hover{background:#2d2d2d;border-color:#747474}
  button:disabled{opacity:.5;cursor:default}
  button.primary{background:#e8e8e8;color:#111;border-color:#e8e8e8;font-weight:600}
  button.danger{color:#ffaaaa;border-color:#6b3939}
  .actions{display:flex;gap:10px;justify-content:space-between}
  [hidden]{display:none !important}
</style></head><body><main class="surface"><div class="wrap">
<div class="brand">${logoUrl ? `<img class="mark" src="${logoUrl}" alt="Core Hub">` : ''}<h1>${escapeHtml(strings.title)}</h1></div>
<p>${escapeHtml(strings.intro)}</p>
<div class="card" id="not-linked" hidden><p>${escapeHtml(strings.notLinked)}</p></div>
<div class="card" id="status-card">
  <div class="status"><span>${escapeHtml(strings.statusLabel)}</span><span class="pill" id="status-pill"></span><span class="hint" id="server-url"></span></div>
  <div class="err" id="last-error"></div>
  <label class="row"><input type="checkbox" id="enabled"><span>${escapeHtml(strings.enabled)}</span></label>
</div>
<div class="card" id="pair-card" hidden>
  <h2>${escapeHtml(strings.pairingLabel)}</h2>
  <p class="hint">${escapeHtml(strings.pairingHint)}</p>
  <input type="text" id="pairing" autocomplete="off" spellcheck="false" placeholder="${escapeHtml(strings.pairingPlaceholder)}">
  <div><button id="send-pairing" class="primary" type="button">${escapeHtml(strings.sendPairing)}</button></div>
</div>
<div class="card" id="pending-card" hidden><p>${escapeHtml(strings.pendingHint)}</p></div>
<div class="card">
  <h2>${escapeHtml(strings.folders)}</h2>
  <p class="hint">${escapeHtml(strings.foldersHint)}</p>
  <ul id="folders"></ul>
  <div><button id="add-folder" type="button">${escapeHtml(strings.addFolder)}</button></div>
</div>
<div class="card">
  <label class="row"><input type="checkbox" id="cap-exec"><span>${escapeHtml(strings.capExec)}</span></label>
  <label class="row"><input type="checkbox" id="cap-files"><span>${escapeHtml(strings.capFiles)}</span></label>
  <label class="row"><input type="checkbox" id="cap-browser"><span>${escapeHtml(strings.capBrowser)}</span></label>
  <label class="row"><input type="checkbox" id="cap-screen"><span>${escapeHtml(strings.capScreen)}</span></label>
  <label class="row"><span>${escapeHtml(strings.approval)}</span>
    <select id="approval"><option value="ask">${escapeHtml(strings.approvalAsk)}</option><option value="always">${escapeHtml(strings.approvalAlways)}</option></select>
  </label>
</div>
<div class="card audit">
  <h2>${escapeHtml(strings.audit)}</h2>
  <ul id="audit"></ul>
</div>
<div class="actions">
  <button id="unlink" type="button" class="danger">${escapeHtml(strings.unlink)}</button>
  <button id="back" type="button">${escapeHtml(strings.back)}</button>
</div>
<script>
  const STRINGS = ${JSON.stringify(strings)}
  const api = window.hermesDesktop && window.hermesDesktop.deviceAgent
  const el = id => document.getElementById(id)
  const esc = value => String(value == null ? '' : value)
  let current = null
  function pillClass(status) {
    if (status === 'connected') return 'pill ok'
    if (status === 'pending' || status === 'connecting' || status === 'offline') return 'pill warn'
    if (status === 'rejected' || status === 'blocked' || status === 'error') return 'pill err'
    return 'pill'
  }
  function render(state) {
    current = state
    el('not-linked').hidden = state.linked
    el('status-card').hidden = !state.linked
    el('status-pill').textContent = STRINGS.status[state.status] || state.status
    el('status-pill').className = pillClass(state.status)
    el('server-url').textContent = state.serverUrl || ''
    el('last-error').textContent = state.lastError || ''
    el('enabled').checked = !!state.config.enabled
    const paired = !!state.config.pairedServerUrl && state.status !== 'unpaired' && state.status !== 'rejected' && state.status !== 'blocked'
    el('pair-card').hidden = !state.linked || paired
    el('pending-card').hidden = state.status !== 'pending'
    el('cap-exec').checked = !!state.config.capabilities.exec
    el('cap-files').checked = !!state.config.capabilities.files
    el('cap-browser').checked = !!state.config.capabilities.browser
    el('cap-screen').checked = !!state.config.capabilities.screen
    el('approval').value = state.config.approvalMode
    el('unlink').hidden = !state.config.pairedServerUrl
    const folders = el('folders')
    folders.innerHTML = ''
    if (!state.config.allowedFolders.length) {
      const li = document.createElement('li'); li.className = 'hint'; li.textContent = STRINGS.noFolders; folders.appendChild(li)
    }
    for (const folder of state.config.allowedFolders) {
      const li = document.createElement('li')
      const code = document.createElement('code'); code.textContent = folder
      const btn = document.createElement('button'); btn.type = 'button'; btn.textContent = STRINGS.remove
      btn.addEventListener('click', () => api.setConfig({ allowedFolders: state.config.allowedFolders.filter(item => item !== folder) }).then(render))
      li.appendChild(code); li.appendChild(btn); folders.appendChild(li)
    }
    const audit = el('audit')
    audit.innerHTML = ''
    if (!state.audit || !state.audit.length) {
      const li = document.createElement('li'); li.className = 'hint'; li.textContent = STRINGS.noAudit; audit.appendChild(li)
    }
    for (const entry of state.audit || []) {
      const li = document.createElement('li')
      const time = document.createElement('span'); time.textContent = new Date(entry.at).toLocaleTimeString()
      const kind = document.createElement('span'); kind.className = entry.ok ? 'good' : 'bad'; kind.textContent = entry.kind
      const detail = document.createElement('span'); detail.textContent = esc(entry.detail)
      li.appendChild(time); li.appendChild(kind); li.appendChild(detail); audit.appendChild(li)
    }
  }
  function save(patch) { api.setConfig(patch).then(render).catch(err => { el('last-error').textContent = String(err && err.message || err) }) }
  el('enabled').addEventListener('change', e => save({ enabled: e.target.checked }))
  el('cap-exec').addEventListener('change', e => save({ capabilities: { exec: e.target.checked } }))
  el('cap-files').addEventListener('change', e => save({ capabilities: { files: e.target.checked } }))
  el('cap-browser').addEventListener('change', e => save({ capabilities: { browser: e.target.checked } }))
  el('cap-screen').addEventListener('change', e => save({ capabilities: { screen: e.target.checked } }))
  el('approval').addEventListener('change', e => save({ approvalMode: e.target.value }))
  el('add-folder').addEventListener('click', () => api.addFolder().then(state => state && render(state)))
  el('send-pairing').addEventListener('click', async function () {
    this.disabled = true
    el('last-error').textContent = ''
    try { render(await api.pair(el('pairing').value)) }
    catch (err) { el('last-error').textContent = String(err && err.message || err) }
    finally { this.disabled = false }
  })
  el('unlink').addEventListener('click', () => api.unpair().then(render))
  el('back').addEventListener('click', () => api.close())
  api.onState(render)
  api.getState().then(render)
</script>
</div></main></body></html>`
  return 'data:text/html;charset=utf-8,' + encodeURIComponent(html)
}

async function openDeviceAgentPage(): Promise<void> {
  if (!mainWindow || mainWindow.isDestroyed()) await createWindow()
  showMainWindow()
  await mainWindow?.loadURL(deviceAgentHtml())
}

function sanitizeDeviceAgentConfigPatch(input: unknown): Partial<Pick<DeviceAgentConfig, 'enabled' | 'capabilities' | 'allowedFolders' | 'approvalMode'>> {
  const record = (input && typeof input === 'object' ? input : {}) as Record<string, unknown>
  const patch: Partial<Pick<DeviceAgentConfig, 'enabled' | 'capabilities' | 'allowedFolders' | 'approvalMode'>> = {}
  if (typeof record.enabled === 'boolean') patch.enabled = record.enabled
  if (record.capabilities && typeof record.capabilities === 'object') {
    const caps = record.capabilities as Record<string, unknown>
    const current = ensureDeviceAgent().getState().config.capabilities
    patch.capabilities = {
      exec: typeof caps.exec === 'boolean' ? caps.exec : current.exec,
      files: typeof caps.files === 'boolean' ? caps.files : current.files,
      browser: typeof caps.browser === 'boolean' ? caps.browser : current.browser,
      screen: typeof caps.screen === 'boolean' ? caps.screen : current.screen,
      apps: typeof caps.apps === 'boolean' ? caps.apps : current.apps,
    }
  }
  if (Array.isArray(record.allowedFolders)) {
    patch.allowedFolders = record.allowedFolders.filter((value): value is string => typeof value === 'string' && value.trim().length > 0)
  }
  if (record.approvalMode === 'ask' || record.approvalMode === 'always') patch.approvalMode = record.approvalMode
  return patch
}

ipcMain.handle('hermes-desktop:device-agent-get-state', event => {
  if (!isTrustedDesktopWindowSender(event.sender)) throw new Error('Device agent state can only be read from a Hermes desktop window')
  return deviceAgentSnapshot()
})
ipcMain.handle('hermes-desktop:device-agent-open-settings', async event => {
  if (!isTrustedDesktopWindowSender(event.sender)) throw new Error('Device access settings can only be opened from a Hermes desktop window')
  await openDeviceAgentPage()
  return true
})
ipcMain.handle('hermes-desktop:device-agent-close-settings', async event => {
  requireMainWindowSender(event, 'Closing device access settings')
  await returnToCurrentUi()
  return true
})
// First pairing without any shared folder: give Hermes one workspace folder
// ("~/Core Hub") so its files land in one place, and enable commands + files
// (each command still asks for approval). The user can change all of it later.
export const DEFAULT_DEVICE_WORKSPACE_DIR_NAME = 'Core Hub'

async function ensureDefaultDeviceWorkspace(agent: DeviceAgent): Promise<void> {
  const config = agent.getState().config
  if (config.allowedFolders.length > 0) return
  const workspace = join(app.getPath('home'), DEFAULT_DEVICE_WORKSPACE_DIR_NAME)
  await mkdir(workspace, { recursive: true }).catch(() => undefined)
  await agent.setConfig({
    allowedFolders: [workspace],
    capabilities: { ...config.capabilities, exec: true, files: true },
  })
}

ipcMain.handle('hermes-desktop:device-agent-pair', async (event, input?: unknown) => {
  requireMainWindowSender(event, 'Pairing this device')
  const agent = ensureDeviceAgent()
  await ensureDefaultDeviceWorkspace(agent)
  const state = await agent.pair(typeof input === 'string' ? input : '')
  return deviceAgentSnapshot(state)
})
ipcMain.handle('hermes-desktop:device-agent-unpair', event => {
  requireMainWindowSender(event, 'Unpairing this device')
  return deviceAgentSnapshot(ensureDeviceAgent().unpair())
})
ipcMain.handle('hermes-desktop:device-agent-set-config', async (event, input?: unknown) => {
  requireMainWindowSender(event, 'Changing device access settings')
  const state = await ensureDeviceAgent().setConfig(sanitizeDeviceAgentConfigPatch(input))
  return deviceAgentSnapshot(state)
})
ipcMain.handle('hermes-desktop:device-agent-discover-apps', event => {
  if (!isTrustedDesktopWindowSender(event.sender)) throw new Error('App discovery can only be requested from a Hermes desktop window')
  return discoverMcpApps({ platform: process.platform, homeDir: app.getPath('home'), env: process.env })
})
ipcMain.handle('hermes-desktop:device-agent-set-apps', async (event, input?: unknown) => {
  requireMainWindowSender(event, 'Changing shared apps')
  const state = await ensureDeviceAgent().setSharedApps(parseSharedMcpApps(input))
  return deviceAgentSnapshot(state)
})
ipcMain.handle('hermes-desktop:device-agent-stop-screen', event => {
  const fromOverlay = !!screenControlOverlay && !screenControlOverlay.isDestroyed() && event.sender === screenControlOverlay.webContents
  if (!fromOverlay && !isTrustedDesktopWindowSender(event.sender)) throw new Error('Screen sharing can only be stopped from a Hermes desktop window')
  ensureDeviceAgent().revokeScreenSession()
  return true
})
ipcMain.handle('hermes-desktop:device-agent-add-folder', async event => {
  requireMainWindowSender(event, 'Sharing a folder')
  if (!mainWindow || mainWindow.isDestroyed()) return null
  const result = await dialog.showOpenDialog(mainWindow, { properties: ['openDirectory', 'createDirectory'] })
  if (result.canceled || result.filePaths.length === 0) return null
  const agent = ensureDeviceAgent()
  const folders = [...new Set([...agent.getState().config.allowedFolders, ...result.filePaths])]
  return deviceAgentSnapshot(await agent.setConfig({ allowedFolders: folders }))
})

function runDesktopApp() {
  setWebUiRestartRequestHandler(() => {
    // Leave enough time for the authenticated relay HTTP request to flush its 202 response.
    scheduleAppRestart(250)
  })
  setWebUiUnexpectedExitHandler(details => {
    void recoverUnexpectedWebUiExit(details)
  })
  const gotLock = app.requestSingleInstanceLock(QUIT_EXISTING ? { quit: true } : undefined)
  if (!gotLock) {
    quitApp()
    return
  }

  app.on('second-instance', (_event, argv, _workingDirectory, additionalData) => {
    if (argv.includes('--quit') || hasQuitRequest(additionalData)) {
      quitApp()
      return
    }
    showMainWindow()
  })

  app.whenReady().then(async () => {
    if (QUIT_EXISTING) {
      quitApp()
      return
    }

    // Drop the default File/Edit/View/Window menu on Windows/Linux. The web
    // UI provides its own in-page controls, so the native menu bar is just
    // visual clutter. macOS keeps a menu (system requirement) but Electron's
    // default is fine there.
    if (process.platform !== 'darwin') Menu.setApplicationMenu(null)
    try {
      migrateWindowsLoginItem(app, APP_USER_MODEL_ID)
    } catch (error) {
      console.warn('[desktop] failed to migrate the Windows login item:', error)
    }
    installMicrophonePermissionHandler()
    desktopMode = resolveDesktopMode(process.env, desktopModeFilePath())
    if (desktopMode.mode === 'server') {
      console.log(`[desktop-mode] linked to ${desktopMode.serverUrl} (${desktopMode.source})`)
    }
    createTray()
    await createWindow()
    await initializeDesktopBrowser().catch(error => {
      console.error('[desktop-browser] failed to initialize:', error)
    })
    void bootstrap()
    initAutoUpdater({ beforeQuitAndInstall: prepareAppShutdown })
    app.on('activate', () => {
      if (BrowserWindow.getAllWindows().length === 0) {
        void createWindow()
      } else if (mainWindow) {
        showMainWindow()
      }
    })
  }).catch(error => {
    console.error('[desktop] failed during Electron startup:', error)
    dialog.showErrorBox('Core Hub', String(error instanceof Error ? error.message : error))
    quitApp()
  })

  app.on('window-all-closed', () => {
    if (appLifecycle.isQuitting && process.platform !== 'darwin') app.quit()
  })

  app.on('before-quit', async (e) => {
    if (!appLifecycle.isQuitting && process.platform !== 'darwin') {
      e.preventDefault()
      mainWindow?.hide()
      updateTrayMenu()
      return
    }
    e.preventDefault()
    try {
      await prepareAppShutdown()
    } finally {
      appLifecycle.finalizeExit(0)
    }
  })
}

const hermesCliArgs = parseHermesCliArgs(process.argv)
if (hermesCliArgs) {
  runBundledHermesCli(hermesCliArgs)
    .then(code => app.exit(code))
    .catch(err => {
      console.error(`Failed to run bundled Hermes CLI: ${err instanceof Error ? err.message : String(err)}`)
      app.exit(1)
    })
} else {
  runDesktopApp()
}
