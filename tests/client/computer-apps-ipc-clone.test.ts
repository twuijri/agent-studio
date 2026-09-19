import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

// Toggling an app sends the shared-app list over Electron IPC. Vue hands the
// view reactive proxies, and structured clone rejects them ("An object could
// not be cloned"), so both sides must serialise to plain JSON first.

describe('Computer apps IPC payload', () => {
  it('serialises the shared-app list to plain JSON before crossing the bridge', () => {
    const view = readFileSync('packages/client/src/views/hermes/AppConnectionsView.vue', 'utf8')
    const preload = readFileSync('packages/desktop/src/preload/index.ts', 'utf8')
    expect(view).toContain('bridge.setApps(JSON.parse(JSON.stringify(next)))')
    expect(preload).toContain("ipcRenderer.invoke('hermes-desktop:device-agent-set-apps', JSON.parse(JSON.stringify(apps ?? [])))")
  })
})
