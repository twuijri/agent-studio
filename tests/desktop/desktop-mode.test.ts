import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  DESKTOP_MODE_ENV,
  DESKTOP_SERVER_URL_ENV,
  isDesktopModeLockedByEnv,
  normalizeStudioServerUrl,
  parseDesktopModeConfig,
  probeStudioServer,
  readDesktopModeConfig,
  resolveDesktopMode,
  writeDesktopModeConfig,
} from '../../packages/desktop/src/main/desktop-mode'

const tempDirs: string[] = []

function tempFile(): string {
  const dir = mkdtempSync(join(tmpdir(), 'desktop-mode-'))
  tempDirs.push(dir)
  return join(dir, 'nested', 'desktop-mode.json')
}

afterEach(() => {
  for (const dir of tempDirs.splice(0)) rmSync(dir, { recursive: true, force: true })
})

describe('normalizeStudioServerUrl', () => {
  it('accepts bare hosts, ports and path prefixes and strips hash, query and trailing slashes', () => {
    expect(normalizeStudioServerUrl('studio.example.com')).toBe('https://studio.example.com')
    expect(normalizeStudioServerUrl(' http://10.0.0.5:6060/ ')).toBe('http://10.0.0.5:6060')
    expect(normalizeStudioServerUrl('https://ai.example.com/studio/#/hermes/chat?x=1')).toBe('https://ai.example.com/studio')
  })

  it('rejects non-http schemes, credentials and garbage', () => {
    expect(normalizeStudioServerUrl('file:///tmp/x')).toBeNull()
    expect(normalizeStudioServerUrl('javascript:alert(1)')).toBeNull()
    expect(normalizeStudioServerUrl('https://user:pw@host')).toBeNull()
    expect(normalizeStudioServerUrl('')).toBeNull()
    expect(normalizeStudioServerUrl(42)).toBeNull()
  })
})

describe('desktop mode config file', () => {
  it('defaults to local and tolerates a missing or corrupt file', () => {
    const file = tempFile()
    expect(readDesktopModeConfig(file)).toEqual({ mode: 'local', serverUrl: null })
    writeFileSync(join(file, '..', '..', 'garbage.json'), '{not json', 'utf8')
    expect(readDesktopModeConfig(join(file, '..', '..', 'garbage.json'))).toEqual({ mode: 'local', serverUrl: null })
  })

  it('round-trips server mode and keeps the url for the chooser when falling back to local', () => {
    const file = tempFile()
    expect(writeDesktopModeConfig(file, { mode: 'server', serverUrl: 'test-ai.example.com/' }))
      .toEqual({ mode: 'server', serverUrl: 'https://test-ai.example.com' })
    expect(JSON.parse(readFileSync(file, 'utf8'))).toMatchObject({ version: 1, mode: 'server', serverUrl: 'https://test-ai.example.com' })
    expect(readDesktopModeConfig(file)).toEqual({ mode: 'server', serverUrl: 'https://test-ai.example.com' })

    writeDesktopModeConfig(file, { mode: 'local', serverUrl: 'https://test-ai.example.com' })
    expect(readDesktopModeConfig(file)).toEqual({ mode: 'local', serverUrl: 'https://test-ai.example.com' })
  })

  it('refuses to persist server mode without a valid address', () => {
    const file = tempFile()
    expect(() => writeDesktopModeConfig(file, { mode: 'server', serverUrl: '' })).toThrow(/server address/i)
    expect(parseDesktopModeConfig({ mode: 'server', serverUrl: 'ftp://x' })).toEqual({ mode: 'local', serverUrl: null })
  })
})

describe('resolveDesktopMode', () => {
  it('uses the saved file when no environment override is present', () => {
    const file = tempFile()
    expect(resolveDesktopMode({}, file)).toEqual({ mode: 'local', serverUrl: null, source: 'default' })
    writeDesktopModeConfig(file, { mode: 'server', serverUrl: 'https://studio.example.com' })
    expect(resolveDesktopMode({}, file)).toEqual({ mode: 'server', serverUrl: 'https://studio.example.com', source: 'file' })
  })

  it('lets environment variables pin the mode for automation and ignores a server override without a url', () => {
    const file = tempFile()
    writeDesktopModeConfig(file, { mode: 'server', serverUrl: 'https://studio.example.com' })
    expect(resolveDesktopMode({ [DESKTOP_MODE_ENV]: 'local' }, file)).toMatchObject({ mode: 'local', source: 'env' })
    expect(resolveDesktopMode({ [DESKTOP_MODE_ENV]: 'server', [DESKTOP_SERVER_URL_ENV]: 'http://127.0.0.1:6060' }, null))
      .toEqual({ mode: 'server', serverUrl: 'http://127.0.0.1:6060', source: 'env' })
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined)
    expect(resolveDesktopMode({ [DESKTOP_MODE_ENV]: 'server' }, file)).toMatchObject({ mode: 'server', source: 'file' })
    expect(warn).toHaveBeenCalledOnce()
    warn.mockRestore()
    expect(isDesktopModeLockedByEnv({ [DESKTOP_MODE_ENV]: 'server' })).toBe(false)
    expect(isDesktopModeLockedByEnv({ [DESKTOP_MODE_ENV]: 'local' })).toBe(true)
  })
})

describe('probeStudioServer', () => {
  it('reports a reachable server through the readiness endpoint and falls back to /health', async () => {
    const calls: string[] = []
    const fetchImpl = vi.fn(async (url: string) => {
      calls.push(url)
      return { ok: url.endsWith('/health'), status: url.endsWith('/health') ? 200 : 404 }
    })
    await expect(probeStudioServer('studio.example.com', { fetchImpl })).resolves.toEqual({ ok: true, url: 'https://studio.example.com', status: 200 })
    expect(calls).toEqual(['https://studio.example.com/health/ready', 'https://studio.example.com/health'])
  })

  it('surfaces network errors, http failures and invalid input without throwing', async () => {
    await expect(probeStudioServer('nope://x')).resolves.toMatchObject({ ok: false, error: 'invalid-url' })
    const failing = vi.fn(async () => { throw new Error('ECONNREFUSED') })
    await expect(probeStudioServer('http://127.0.0.1:1', { fetchImpl: failing })).resolves.toMatchObject({ ok: false, error: 'ECONNREFUSED' })
    const gone = vi.fn(async () => ({ ok: false, status: 502 }))
    await expect(probeStudioServer('https://x.example', { fetchImpl: gone })).resolves.toMatchObject({ ok: false, status: 502, error: 'http-502' })
  })
})

describe('desktop main wiring for the linked-server mode', () => {
  const mainSource = readFileSync(resolve(process.cwd(), 'packages/desktop/src/main/index.ts'), 'utf8')
  const preloadSource = readFileSync(resolve(process.cwd(), 'packages/desktop/src/preload/index.ts'), 'utf8')

  it('resolves the mode before any window is created and skips the local runtime when linked', () => {
    const ready = mainSource.slice(mainSource.indexOf('app.whenReady()'), mainSource.indexOf("app.on('activate'"))
    expect(ready.indexOf('desktopMode = resolveDesktopMode(process.env, desktopModeFilePath())')).toBeGreaterThan(-1)
    expect(ready.indexOf('desktopMode = resolveDesktopMode')).toBeLessThan(ready.indexOf('await createWindow()'))

    const bootstrap = mainSource.slice(mainSource.indexOf('async function bootstrap('), mainSource.indexOf('async function loadServiceFailurePage'))
    expect(bootstrap.indexOf('if (isServerLinkedMode())')).toBeGreaterThan(-1)
    expect(bootstrap.indexOf('if (isServerLinkedMode())')).toBeLessThan(bootstrap.indexOf('migratePendingLegacyWindowsData'))
    expect(bootstrap.indexOf('if (isServerLinkedMode())')).toBeLessThan(bootstrap.indexOf('await startWebUiServer(PORT)'))

    const linked = mainSource.slice(mainSource.indexOf('async function bootstrapServerLinkedMode'), mainSource.indexOf('function connectionModeHtml'))
    expect(linked).not.toContain('startWebUiServer')
    expect(linked).not.toContain('ensureDesktopRuntime')
    expect(linked).toContain('serverUrl = url')
    expect(linked).toContain('await loadServiceFailurePage(err)')
  })

  it('never hands the local auth token to a linked server page and keeps the mode IPC main-window only', () => {
    expect(mainSource).toContain("ipcMain.handle('hermes-desktop:get-token', () => (isServerLinkedMode() ? '' : getToken()))")
    const setMode = mainSource.slice(mainSource.indexOf("ipcMain.handle('hermes-desktop:set-desktop-mode'"))
    expect(setMode).toContain("requireMainWindowSender(event, 'Changing the connection mode')")
    expect(setMode).toContain('if (isDesktopModeLockedByEnv(process.env)) throw')
    expect(setMode).toContain('writeDesktopModeConfig(desktopModeFilePath()')
    expect(setMode).toContain('return scheduleAppRestart(300)')
    expect(mainSource).toContain("ipcMain.on('hermes-desktop:get-desktop-mode'")
    expect(mainSource).toContain("label: t('tray.connectionMode')")
  })

  it('withholds local-only preload behaviour when the window shows a linked server', () => {
    expect(preloadSource).toContain("ipcRenderer.sendSync('hermes-desktop:get-desktop-mode')")
    expect(preloadSource).toContain('if (!serverLinked) installFetchPatch()')
    expect(preloadSource).toContain('...(serverLinked ? {} : {')
    expect(preloadSource).toContain("ipcRenderer.invoke('hermes-desktop:select-runtime-directory'")
    expect(preloadSource).toContain('mode: desktopModeSnapshot.mode')
    expect(preloadSource).toContain("openSettings: (): Promise<boolean> => ipcRenderer.invoke('hermes-desktop:open-desktop-mode-settings')")
  })
})
