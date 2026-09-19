import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
// @ts-expect-error plain ESM build script without type declarations
import { hasDeveloperIdSignature, shouldAdhocSign } from '../../packages/desktop/scripts/adhoc-sign-macos.mjs'

describe('macOS ad-hoc signing for unsigned personal-fork builds', () => {
  it('ad-hoc signs only macOS builds that have no Developer ID identity', () => {
    expect(shouldAdhocSign({ CSC_IDENTITY_AUTO_DISCOVERY: 'false' }, 'darwin')).toBe(true)
    expect(shouldAdhocSign({}, 'darwin')).toBe(true)
    expect(shouldAdhocSign({ CSC_LINK: 'file:///cert.p12' }, 'darwin')).toBe(false)
    expect(shouldAdhocSign({ MAC_CSC_LINK: 'base64…' }, 'darwin')).toBe(false)
    expect(shouldAdhocSign({ CSC_NAME: 'Developer ID Application: Someone' }, 'darwin')).toBe(false)
    // CI signing exports only CSC_KEYCHAIN (the certificate lives in a temp keychain).
    expect(shouldAdhocSign({ CSC_KEYCHAIN: '/tmp/signing.keychain-db' }, 'darwin')).toBe(false)
    expect(shouldAdhocSign({ CORE_HUB_SKIP_ADHOC_SIGN: '1' }, 'darwin')).toBe(false)
    expect(shouldAdhocSign({}, 'win32')).toBe(false)
    expect(shouldAdhocSign({}, 'linux')).toBe(false)
  })

  it('is wired as the electron-builder afterSign hook and verifies the result', () => {
    const config = readFileSync(resolve('packages/desktop/electron-builder.yml'), 'utf-8')
    const hook = readFileSync(resolve('packages/desktop/scripts/adhoc-sign-macos.mjs'), 'utf-8')
    expect(config).toContain('afterSign: "./scripts/adhoc-sign-macos.mjs"')
    expect(hook).toContain("['--force', '--deep', '--sign', '-', '--timestamp=none', appPath]")
    expect(hook).toContain("['--verify', '--deep', '--strict', '--verbose=2', appPath]")
    expect(hook).toContain('Authority=Developer ID Application')
  })

  it('recognises a Developer ID signature from codesign output (which arrives on stderr)', () => {
    expect(hasDeveloperIdSignature('Executable=/x\nIdentifier=us.i3u\nAuthority=Developer ID Application: Someone (TEAM)\nAuthority=Developer ID Certification Authority')).toBe(true)
    expect(hasDeveloperIdSignature('Signature=adhoc')).toBe(false)
    expect(hasDeveloperIdSignature(undefined)).toBe(false)
  })
})
