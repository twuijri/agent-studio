import { describe, expect, it, vi } from 'vitest'
import { DeviceAgentProtocol, type DeviceAgentAuditEntry, type DeviceAgentPolicy } from '../../packages/desktop/src/main/device-agent/protocol'
import { buildScreenActionCommands, parseScreenAction } from '../../packages/desktop/src/main/device-agent/screen-input'
import { parseDeviceAgentConfig } from '../../packages/desktop/src/main/device-agent/store'

const wait = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))

function harness(options: {
  capabilities?: DeviceAgentPolicy['capabilities']
  approveScreen?: () => Promise<boolean>
  browserProxy?: Parameters<typeof DeviceAgentProtocol.prototype.constructor>[0]['browserProxy']
  screen?: Parameters<typeof DeviceAgentProtocol.prototype.constructor>[0]['screen']
}) {
  const sent: Record<string, unknown>[] = []
  const audit: DeviceAgentAuditEntry[] = []
  const protocol = new DeviceAgentProtocol({
    send: payload => sent.push(payload),
    audit: entry => audit.push(entry),
    browserProxy: options.browserProxy,
    screen: options.screen,
    policy: () => ({
      capabilities: options.capabilities ?? ['browser', 'screen'],
      allowedFolders: [],
      approveExec: async () => true,
      approveScreen: options.approveScreen,
    }),
  })
  const waitFor = async (type: string) => {
    const deadline = Date.now() + 3000
    while (Date.now() < deadline) {
      const found = sent.find(message => message.type === type)
      if (found) return found
      await wait(5)
    }
    throw new Error(`no ${type}; got ${sent.map(m => m.type).join(',')}`)
  }
  return { protocol, sent, audit, waitFor }
}

describe('device agent: browser tunnel', () => {
  it('forwards broker requests and returns status, headers and body', async () => {
    const browserProxy = vi.fn(async (request: { path: string; headers: Record<string, string>; body: Buffer }) => ({
      status: 200,
      headers: { 'content-type': 'application/json' },
      body: Buffer.from(JSON.stringify({ echoed: request.path, auth: request.headers.authorization, method: JSON.parse(request.body.toString('utf8')).method })),
    }))
    const { protocol, waitFor, audit } = harness({ browserProxy })
    protocol.handle({
      type: 'http.proxy', request_id: 'p1', method: 'POST', path: '/v1',
      headers: { Authorization: 'Bearer s1', 'Content-Type': 'application/json' },
      body: Buffer.from(JSON.stringify({ method: 'tabs.list' })).toString('base64'),
    })
    const result = await waitFor('http.proxy.result')
    expect(result).toMatchObject({ request_id: 'p1', status: 200, headers: { 'content-type': 'application/json' } })
    expect(JSON.parse(Buffer.from(String(result.body), 'base64').toString('utf8'))).toEqual({ echoed: '/v1', auth: 'Bearer s1', method: 'tabs.list' })
    expect(browserProxy).toHaveBeenCalledWith(expect.objectContaining({ path: '/v1', headers: expect.objectContaining({ authorization: 'Bearer s1' }) }))
    expect(audit.at(-1)).toMatchObject({ kind: 'browser', ok: true, detail: '/v1 tabs.list' })
  })

  it('refuses the tunnel when the browser is not shared or the path is not the broker', async () => {
    const disabled = harness({ capabilities: ['exec'], browserProxy: async () => ({ status: 200, headers: {}, body: Buffer.alloc(0) }) })
    disabled.protocol.handle({ type: 'http.proxy', request_id: 'p2', path: '/v1' })
    expect(await disabled.waitFor('http.proxy.error')).toMatchObject({ request_id: 'p2', status: 403 })

    const wrongPath = harness({ browserProxy: async () => ({ status: 200, headers: {}, body: Buffer.alloc(0) }) })
    wrongPath.protocol.handle({ type: 'http.proxy', request_id: 'p3', path: '/etc/passwd' })
    expect(await wrongPath.waitFor('http.proxy.error')).toMatchObject({ request_id: 'p3', status: 403 })
  })
})

describe('device agent: screen', () => {
  const screen = {
    capture: vi.fn(async () => ({ media_type: 'image/png', data: 'iVBOR', width: 1440, height: 900, display_id: '1', displays: [{ id: '1', width: 1440, height: 900, scale: 2, primary: true }] })),
    action: vi.fn(async () => undefined),
  }

  it('asks once per session, then captures and performs actions', async () => {
    const approveScreen = vi.fn(async () => true)
    const { protocol, waitFor, sent } = harness({ screen, approveScreen })
    protocol.handle({ type: 'screen.capture', request_id: 's1', max_width: 800 })
    expect(await waitFor('screen.capture.result')).toMatchObject({ request_id: 's1', data: 'iVBOR', width: 1440, display_id: '1' })
    expect(screen.capture).toHaveBeenCalledWith({ displayId: undefined, maxWidth: 800 })

    protocol.handle({ type: 'screen.action', request_id: 's2', action: 'click', x: 10, y: 20 })
    expect(await waitFor('screen.action.result')).toMatchObject({ request_id: 's2', ok: true })
    expect(screen.action).toHaveBeenCalledWith({ action: 'click', x: 10, y: 20 })
    expect(sent.filter(message => message.type === 'screen.error')).toHaveLength(0)
    // The protocol delegates session memory to the policy; here every call asks.
    expect(approveScreen).toHaveBeenCalledTimes(2)
  })

  it('refuses when the capability is off or the user denies', async () => {
    const off = harness({ capabilities: ['exec'], screen })
    off.protocol.handle({ type: 'screen.capture', request_id: 's3' })
    expect(await off.waitFor('screen.error')).toMatchObject({ request_id: 's3', message: expect.stringMatching(/disabled/) })

    const denied = harness({ screen, approveScreen: async () => false })
    denied.protocol.handle({ type: 'screen.action', request_id: 's4', action: 'type', text: 'hi' })
    expect(await denied.waitFor('screen.error')).toMatchObject({ request_id: 's4', message: expect.stringMatching(/denied/) })
    expect(denied.audit.at(-1)).toMatchObject({ kind: 'denied' })
  })
})

describe('screen input commands', () => {
  it('validates actions', () => {
    expect(parseScreenAction({ action: 'click', x: 10.4, y: 20 })).toEqual({ action: 'click', x: 10, y: 20 })
    expect(parseScreenAction({ action: 'click', x: 'a', y: 20 })).toBeNull()
    expect(parseScreenAction({ action: 'type', text: '' })).toBeNull()
    expect(parseScreenAction({ action: 'key', key: 'enter', modifiers: ['cmd', 'bogus'] })).toEqual({ action: 'key', key: 'enter', modifiers: ['cmd'] })
    expect(parseScreenAction({ action: 'key', key: 'rm -rf' })).toBeNull()
    expect(parseScreenAction({ action: 'scroll', x: 1, y: 2, dy: -3 })).toEqual({ action: 'scroll', x: 1, y: 2, dx: 0, dy: -3 })
    expect(parseScreenAction({ action: 'launch' })).toBeNull()
  })

  it('builds platform commands without shell string execution', () => {
    const click = buildScreenActionCommands('darwin', { action: 'click', x: 5, y: 6 })
    expect(click).toEqual([{ file: 'osascript', args: ['-e', 'tell application "System Events" to click at {5, 6}'] }])
    const typed = buildScreenActionCommands('darwin', { action: 'type', text: 'say "hi" \\ done' })
    expect(typed[0].args[1]).toBe('tell application "System Events" to keystroke "say \\"hi\\" \\\\ done"')
    const key = buildScreenActionCommands('darwin', { action: 'key', key: 'enter', modifiers: ['cmd'] })
    expect(key[0].args[1]).toBe('tell application "System Events" to key code 36 using {command down}')

    expect(buildScreenActionCommands('linux', { action: 'double_click', x: 1, y: 2 })).toEqual([{ file: 'xdotool', args: ['mousemove', '1', '2', 'click', '--repeat', '2', '1'] }])
    expect(buildScreenActionCommands('linux', { action: 'key', key: 'enter', modifiers: ['ctrl'] })).toEqual([{ file: 'xdotool', args: ['key', 'ctrl+Return'] }])
    expect(buildScreenActionCommands('linux', { action: 'scroll', x: 1, y: 2, dy: 3 })).toEqual([
      { file: 'xdotool', args: ['mousemove', '1', '2'] },
      { file: 'xdotool', args: ['click', '--repeat', '3', '5'] },
    ])

    const win = buildScreenActionCommands('win32', { action: 'type', text: 'a+b' })
    expect(win[0].file).toBe('powershell.exe')
    expect(win[0].args.at(-1)).toContain("SendWait('a{+}b')")
  })

  it('parses the new capabilities in the config', () => {
    expect(parseDeviceAgentConfig({ capabilities: { exec: true, browser: true, screen: 'yes' } }).capabilities)
      .toEqual({ exec: true, files: false, browser: true, screen: false })
  })
})
