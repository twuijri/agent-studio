import { mkdtempSync, readFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { afterEach, describe, expect, it } from 'vitest'
import {
  checkForNewRelease,
  compareVersions,
  DEFAULT_RELEASE_NOTICE_STATE,
  parseLatestRelease,
  readReleaseNoticeState,
  writeReleaseNoticeState,
} from '../../packages/desktop/src/main/release-notice-core'

const dirs: string[] = []
afterEach(() => { for (const dir of dirs.splice(0)) rmSync(dir, { recursive: true, force: true }) })

function latest(tag: string, extra: Record<string, unknown> = {}) {
  return { tag_name: tag, html_url: `https://github.com/twuijri/agent-studio/releases/tag/${tag}`, published_at: '2026-09-18T20:00:00Z', draft: false, prerelease: false, ...extra }
}

describe('release notice (new version available, no updater)', () => {
  it('compares versions semver-style, with and without the v prefix', () => {
    expect(compareVersions('1.0.1', 'v1.0.0')).toBeGreaterThan(0)
    expect(compareVersions('v1.0.0', '1.0.0')).toBe(0)
    expect(compareVersions('1.0.0', '1.10.0')).toBeLessThan(0)
    expect(compareVersions('2.0.0-rc.1', '2.0.0')).toBeLessThan(0)
    expect(compareVersions('garbage', '1.0.0')).toBe(0)
  })

  it('parses the GitHub latest-release payload and ignores drafts and pre-releases', () => {
    expect(parseLatestRelease(latest('v1.0.1'))).toEqual({ version: '1.0.1', tag: 'v1.0.1', url: 'https://github.com/twuijri/agent-studio/releases/tag/v1.0.1', publishedAt: '2026-09-18T20:00:00Z' })
    expect(parseLatestRelease(latest('v1.0.1', { prerelease: true }))).toBeNull()
    expect(parseLatestRelease(latest('v1.0.1', { draft: true }))).toBeNull()
    expect(parseLatestRelease(latest('v1.0.1', { html_url: 'http://evil.example/x' }))).toBeNull()
    expect(parseLatestRelease(latest('nightly'))).toBeNull()
    expect(parseLatestRelease(null)).toBeNull()
  })

  it('reports a newer release, stays quiet for a skipped version unless the check is manual', async () => {
    const fetchJson = async () => latest('v1.0.1')
    await expect(checkForNewRelease({ currentVersion: '1.0.0', state: DEFAULT_RELEASE_NOTICE_STATE, fetchJson })).resolves.toMatchObject({ status: 'available', release: { version: '1.0.1' } })
    await expect(checkForNewRelease({ currentVersion: '1.0.1', state: DEFAULT_RELEASE_NOTICE_STATE, fetchJson })).resolves.toMatchObject({ status: 'up-to-date' })
    await expect(checkForNewRelease({ currentVersion: '1.2.0', state: DEFAULT_RELEASE_NOTICE_STATE, fetchJson })).resolves.toMatchObject({ status: 'up-to-date' })
    const skipped = { ...DEFAULT_RELEASE_NOTICE_STATE, skippedVersion: '1.0.1' }
    await expect(checkForNewRelease({ currentVersion: '1.0.0', state: skipped, fetchJson })).resolves.toMatchObject({ status: 'skipped' })
    await expect(checkForNewRelease({ currentVersion: '1.0.0', state: skipped, fetchJson, manual: true })).resolves.toMatchObject({ status: 'available' })
  })

  it('turns network failures into an error result instead of throwing', async () => {
    const calls: string[] = []
    const result = await checkForNewRelease({
      currentVersion: '1.0.0',
      state: DEFAULT_RELEASE_NOTICE_STATE,
      apiUrl: 'https://api.example.test/latest',
      fetchJson: async url => { calls.push(url); throw new Error('offline') },
    })
    expect(result).toEqual({ status: 'error', error: 'offline' })
    expect(calls).toEqual(['https://api.example.test/latest'])
  })

  it('persists the notice state and tolerates a missing or corrupt file', () => {
    const dir = mkdtempSync(join(tmpdir(), 'core-hub-release-notice-'))
    dirs.push(dir)
    const file = join(dir, 'nested', 'release-notice.json')
    expect(readReleaseNoticeState(file)).toEqual(DEFAULT_RELEASE_NOTICE_STATE)
    writeReleaseNoticeState(file, { enabled: false, skippedVersion: 'v1.0.1', noticedVersion: null, lastCheckedAt: '2026-09-18T20:00:00Z' })
    expect(JSON.parse(readFileSync(file, 'utf8'))).toMatchObject({ enabled: false })
    expect(readReleaseNoticeState(file)).toEqual({ enabled: false, skippedVersion: '1.0.1', noticedVersion: null, lastCheckedAt: '2026-09-18T20:00:00Z' })
    writeReleaseNoticeState(file, JSON.parse('{"enabled":"yes","skippedVersion":42}') as never)
    expect(readReleaseNoticeState(file)).toEqual({ ...DEFAULT_RELEASE_NOTICE_STATE })
  })

  it('never re-enables the auto-updater: the notice only opens the release page', () => {
    const notice = readFileSync(resolve('packages/desktop/src/main/release-notice.ts'), 'utf-8')
    const updater = readFileSync(resolve('packages/desktop/src/main/updater.ts'), 'utf-8')
    const main = readFileSync(resolve('packages/desktop/src/main/index.ts'), 'utf-8')
    expect(notice).not.toContain('electron-updater')
    expect(notice).not.toContain('downloadUpdate')
    expect(notice).not.toContain('quitAndInstall')
    expect(notice).toContain('shell.openExternal(release.url)')
    expect(updater).toContain('const PERSONAL_FORK = true')
    expect(updater).toContain('await runReleaseNoticeCheck(manual)')
    expect(main).toContain('startReleaseNoticeChecks()')
    expect(main).toContain("t('tray.releaseNotices')")
  })
})
