import { app } from 'electron'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

// Build channel of this desktop app. Release builds are "stable" (Core Hub);
// the automatic builds from the `test` branch are "test" (Core Hub Test): a
// separate product name and app id, so the test app installs next to the
// stable one with its own data folder and never touches it. The channel is
// baked into the packaged package.json by electron-builder's extraMetadata.

export type DesktopChannel = 'stable' | 'test'

let cached: DesktopChannel | null = null

export function readDesktopChannel(packageJsonPath = join(app.getAppPath(), 'package.json')): DesktopChannel {
  try {
    const pkg = JSON.parse(readFileSync(packageJsonPath, 'utf8')) as { corehubChannel?: unknown }
    return pkg.corehubChannel === 'test' ? 'test' : 'stable'
  } catch {
    return 'stable'
  }
}

export function desktopChannel(): DesktopChannel {
  if (!cached) cached = readDesktopChannel()
  return cached
}

export function isTestChannel(): boolean {
  return desktopChannel() === 'test'
}
