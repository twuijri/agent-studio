import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('electron', () => ({ app: { getAppPath: () => '/nonexistent' } }))

const dirs: string[] = []
afterEach(() => { for (const dir of dirs.splice(0)) rmSync(dir, { recursive: true, force: true }) })

describe('desktop build channel', () => {
  it('reads the channel baked into the packaged package.json and defaults to stable', async () => {
    const { readDesktopChannel } = await import('../../packages/desktop/src/main/channel')
    const dir = mkdtempSync(join(tmpdir(), 'core-hub-channel-'))
    dirs.push(dir)
    const pkg = join(dir, 'package.json')
    writeFileSync(pkg, JSON.stringify({ name: 'hermes-studio', productName: 'Core Hub Test', corehubChannel: 'test' }))
    expect(readDesktopChannel(pkg)).toBe('test')
    writeFileSync(pkg, JSON.stringify({ name: 'hermes-studio', productName: 'Core Hub' }))
    expect(readDesktopChannel(pkg)).toBe('stable')
    expect(readDesktopChannel(join(dir, 'missing.json'))).toBe('stable')
  })

  it('keeps the test app apart from the stable one and silences its release notice', () => {
    const { readFileSync } = require('node:fs') as typeof import('node:fs')
    const main = readFileSync('packages/desktop/src/main/index.ts', 'utf8')
    const notice = readFileSync('packages/desktop/src/main/release-notice.ts', 'utf8')
    const workflow = readFileSync('.github/workflows/test-track.yml', 'utf8')
    expect(main).toContain("isTestChannel() ? 'com.hermeswebui.studio.test' : 'com.hermeswebui.studio'")
    expect(main).toContain('tray.setToolTip(app.getName())')
    expect(notice).toContain('if (isTestChannel()) return')
    // Overrides travel in a config file (CLI values with spaces break on Windows).
    expect(workflow).toContain('extends: "./electron-builder.yml"')
    expect(workflow).toContain('appId: "us.i3u.agentstudio.test"')
    expect(workflow).toContain('extraMetadata: { productName: "Core Hub Test", version, corehubChannel: "test" }')
    expect(workflow).toContain('--config electron-builder.test.json')
  })
})
