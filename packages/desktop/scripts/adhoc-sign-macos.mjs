import { execFileSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import { join } from 'node:path'

// electron-builder afterSign hook.
//
// When no Developer ID certificate is configured (personal-fork builds
// without an Apple Developer account), electron-builder skips signing
// entirely. The packaged app then still carries Electron's own ad-hoc
// signature, whose resource seal no longer matches after the bundle was
// renamed and the asar was added. Gatekeeper reports such a broken
// signature as "“Core Hub” is damaged and can’t be opened", which is far
// worse than the normal unsigned-app flow: there is no "Open Anyway"
// button, and the only way out is `xattr -d com.apple.quarantine`.
//
// Re-signing the finished bundle ad hoc restores a valid signature, so
// macOS shows the usual "Apple could not verify…" message and offers
// "Open Anyway" in System Settings → Privacy & Security, like any other
// unsigned app downloaded from GitHub. Nothing about a real Developer ID
// build changes: the hook does nothing when a proper identity signed the app.

export function shouldAdhocSign(env = process.env, platform = 'darwin') {
  if (platform !== 'darwin') return false
  if (env.CORE_HUB_SKIP_ADHOC_SIGN === '1') return false
  const hasIdentity = Boolean((env.CSC_LINK || env.MAC_CSC_LINK || env.CSC_NAME || '').trim())
  if (hasIdentity) return false
  return env.CSC_IDENTITY_AUTO_DISCOVERY === 'false' || !hasIdentity
}

function run(args) {
  return execFileSync('codesign', args, { stdio: ['ignore', 'pipe', 'pipe'], encoding: 'utf8' })
}

function isSignedByDeveloperId(appPath) {
  try {
    const info = execFileSync('codesign', ['-dv', '--verbose=2', appPath], { stdio: ['ignore', 'pipe', 'pipe'], encoding: 'utf8' })
    return /Authority=Developer ID Application/.test(info)
  } catch {
    // Either unsigned or broken signature; both need the ad-hoc re-sign.
    return false
  }
}

export default async function adhocSignMacOs(context) {
  if (!shouldAdhocSign(process.env, context.electronPlatformName)) return
  const appPath = join(context.appOutDir, `${context.packager.appInfo.productFilename}.app`)
  if (!existsSync(appPath)) {
    console.warn(`[adhoc-sign] app bundle not found at ${appPath}; skipping`)
    return
  }
  if (isSignedByDeveloperId(appPath)) {
    console.log('[adhoc-sign] app already carries a Developer ID signature; nothing to do')
    return
  }
  console.log(`[adhoc-sign] no Developer ID identity configured; ad-hoc signing ${appPath}`)
  // --deep is fine for ad-hoc signing: every nested helper and framework gets
  // the same anonymous identity, which is all Gatekeeper needs to consider the
  // bundle intact.
  run(['--force', '--deep', '--sign', '-', '--timestamp=none', appPath])
  run(['--verify', '--deep', '--strict', '--verbose=2', appPath])
  console.log('[adhoc-sign] signature verified')
}
