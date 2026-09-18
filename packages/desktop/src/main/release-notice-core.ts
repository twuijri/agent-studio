import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname } from 'node:path'

// "New version available" notice for the personal fork. This is NOT an
// updater: it only reads the public release list of this fork and tells the
// user that a newer build exists. Nothing is downloaded or installed; the
// user opens the release page and installs the new build themselves.
// Pure module (no Electron) so the decision logic is unit-testable.

export const DEFAULT_RELEASE_API_URL = 'https://api.github.com/repos/twuijri/core-hub/releases/latest'
export const RELEASE_NOTICE_FILE = 'release-notice.json'

export interface ReleaseInfo {
  version: string
  tag: string
  url: string
  publishedAt: string | null
}

export interface ReleaseNoticeState {
  /** Automatic checks on (default) or off. */
  enabled: boolean
  /** Version the user chose to skip; the automatic notice stays quiet for it. */
  skippedVersion: string | null
  /** Version the automatic notice was already shown for (once per version). */
  noticedVersion: string | null
  lastCheckedAt: string | null
}

export type ReleaseCheckResult =
  | { status: 'available'; release: ReleaseInfo }
  | { status: 'skipped'; release: ReleaseInfo }
  | { status: 'up-to-date'; release: ReleaseInfo | null }
  | { status: 'error'; error: string }

export const DEFAULT_RELEASE_NOTICE_STATE: ReleaseNoticeState = {
  enabled: true,
  skippedVersion: null,
  noticedVersion: null,
  lastCheckedAt: null,
}

export function parseVersion(value: string): { parts: number[]; prerelease: string | null } | null {
  const match = String(value || '').trim().match(/^v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$/)
  if (!match) return null
  return { parts: [Number(match[1]), Number(match[2]), Number(match[3])], prerelease: match[4] || null }
}

/** Semver-style compare: negative when a < b, positive when a > b, 0 when equal or unparseable. */
export function compareVersions(a: string, b: string): number {
  const left = parseVersion(a)
  const right = parseVersion(b)
  if (!left || !right) return 0
  for (let i = 0; i < 3; i++) {
    if (left.parts[i] !== right.parts[i]) return left.parts[i] - right.parts[i]
  }
  // A release without a pre-release suffix is newer than the same numbers with one.
  if (left.prerelease && !right.prerelease) return -1
  if (!left.prerelease && right.prerelease) return 1
  if (left.prerelease && right.prerelease) return left.prerelease.localeCompare(right.prerelease)
  return 0
}

/** Accepts the GitHub "latest release" payload; drafts and pre-releases are ignored. */
export function parseLatestRelease(payload: unknown): ReleaseInfo | null {
  if (!payload || typeof payload !== 'object') return null
  const release = payload as Record<string, unknown>
  if (release.draft === true || release.prerelease === true) return null
  const tag = typeof release.tag_name === 'string' ? release.tag_name.trim() : ''
  const parsed = parseVersion(tag)
  if (!tag || !parsed) return null
  const url = typeof release.html_url === 'string' && /^https:\/\/github\.com\//.test(release.html_url) ? release.html_url : ''
  if (!url) return null
  return {
    version: tag.replace(/^v/, ''),
    tag,
    url,
    publishedAt: typeof release.published_at === 'string' ? release.published_at : null,
  }
}

export function parseReleaseNoticeState(raw: unknown): ReleaseNoticeState {
  const value = raw && typeof raw === 'object' ? raw as Record<string, unknown> : {}
  const version = (input: unknown) => typeof input === 'string' && parseVersion(input) ? input.replace(/^v/, '') : null
  return {
    enabled: value.enabled !== false,
    skippedVersion: version(value.skippedVersion),
    noticedVersion: version(value.noticedVersion),
    lastCheckedAt: typeof value.lastCheckedAt === 'string' ? value.lastCheckedAt : null,
  }
}

export function readReleaseNoticeState(filePath: string): ReleaseNoticeState {
  if (!existsSync(filePath)) return { ...DEFAULT_RELEASE_NOTICE_STATE }
  try {
    return parseReleaseNoticeState(JSON.parse(readFileSync(filePath, 'utf8')))
  } catch {
    return { ...DEFAULT_RELEASE_NOTICE_STATE }
  }
}

export function writeReleaseNoticeState(filePath: string, state: ReleaseNoticeState): ReleaseNoticeState {
  mkdirSync(dirname(filePath), { recursive: true })
  writeFileSync(filePath, `${JSON.stringify(state, null, 2)}\n`, 'utf8')
  return state
}

export interface ReleaseCheckOptions {
  currentVersion: string
  state: ReleaseNoticeState
  fetchJson: (url: string) => Promise<unknown>
  /** Manual checks ignore the skipped version so the user can always see the latest build. */
  manual?: boolean
  apiUrl?: string
}

export async function checkForNewRelease(options: ReleaseCheckOptions): Promise<ReleaseCheckResult> {
  let payload: unknown
  try {
    payload = await options.fetchJson(options.apiUrl || DEFAULT_RELEASE_API_URL)
  } catch (err) {
    return { status: 'error', error: err instanceof Error ? err.message : String(err) }
  }
  const release = parseLatestRelease(payload)
  if (!release) return { status: 'up-to-date', release: null }
  if (compareVersions(release.version, options.currentVersion) <= 0) return { status: 'up-to-date', release }
  if (!options.manual && options.state.skippedVersion === release.version) return { status: 'skipped', release }
  return { status: 'available', release }
}
