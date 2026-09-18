import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { appIdFor, discoverMcpApps, parseCodexMcpServers } from '../../packages/desktop/src/main/device-agent/mcp-discovery'
import { DeviceAgentProtocol, type DeviceAgentAuditEntry, type DeviceAgentPolicy } from '../../packages/desktop/src/main/device-agent/protocol'
import { parseDeviceAgentConfig, parseSharedMcpApps } from '../../packages/desktop/src/main/device-agent/store'

const wait = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))
const tempDirs: string[] = []
function tempDir(): string {
  const dir = mkdtempSync(join(tmpdir(), 'mcp-apps-'))
  tempDirs.push(dir)
  return dir
}
afterEach(() => {
  for (const dir of tempDirs.splice(0)) rmSync(dir, { recursive: true, force: true })
})

describe('MCP app discovery', () => {
  it('reads Claude Desktop, Claude extensions, Claude Code, Codex, Cursor and Windsurf on macOS paths', () => {
    const home = tempDir()
    const claude = join(home, 'Library', 'Application Support', 'Claude')
    mkdirSync(join(claude, 'Claude Extensions', 'com.blackmagic.resolve'), { recursive: true })
    writeFileSync(join(claude, 'claude_desktop_config.json'), JSON.stringify({ mcpServers: { figma: { command: 'npx', args: ['-y', 'figma-mcp'], env: { TOKEN: 'x' } }, disabledOne: { command: 'x', disabled: true } } }))
    writeFileSync(join(claude, 'Claude Extensions', 'com.blackmagic.resolve', 'manifest.json'), JSON.stringify({
      name: 'davinci-resolve', display_name: 'DaVinci Resolve Studio',
      server: { type: 'python', entry_point: 'server/main.py', mcp_config: { command: '${__dirname}/server/python', args: ['${__dirname}/server/main.py', '--home', '${HOME}', '--key', '${user_config.api_key}'], env: { RESOLVE_HOME: '${__dirname}' } } },
    }))
    writeFileSync(join(home, '.claude.json'), JSON.stringify({ mcpServers: { github: { command: 'gh-mcp', args: [] }, figma: { command: 'npx', args: ['-y', 'figma-mcp'] } } }))
    mkdirSync(join(home, '.codex'))
    writeFileSync(join(home, '.codex', 'config.toml'), `model = "o3"\n[mcp_servers.blender]\ncommand = "uvx"\nargs = ["blender-mcp"]\n[mcp_servers.blender.env]\nBLENDER_PORT = "9876"\n[mcp_servers.remote]\nurl = "https://mcp.example.com/sse"\n`)
    mkdirSync(join(home, '.cursor'))
    writeFileSync(join(home, '.cursor', 'mcp.json'), JSON.stringify({ mcpServers: { cursorOnly: { command: 'cursor-mcp' } } }))
    mkdirSync(join(home, '.codeium', 'windsurf'), { recursive: true })
    writeFileSync(join(home, '.codeium', 'windsurf', 'mcp_config.json'), JSON.stringify({ mcpServers: { wind: { command: 'wind-mcp', args: ['a'] } } }))

    const apps = discoverMcpApps({ platform: 'darwin', homeDir: home, env: {} })
    const names = apps.map(app => `${app.source}:${app.name}`)
    expect(names).toEqual(expect.arrayContaining([
      'claude-desktop:figma', 'claude-extension:DaVinci Resolve Studio', 'claude-code:github', 'codex:blender', 'codex:remote', 'cursor:cursorOnly', 'windsurf:wind',
    ]))
    expect(names).not.toContain('claude-desktop:disabledOne')
    // The same figma definition in two assistants is listed once, with both origins noted.
    expect(apps.filter(app => app.name === 'figma')).toHaveLength(1)
    expect(apps.find(app => app.name === 'figma')?.origin).toContain('.claude.json')

    const resolve = apps.find(app => app.source === 'claude-extension')!
    const extensionDir = join(claude, 'Claude Extensions', 'com.blackmagic.resolve')
    expect(resolve.command).toBe(join(extensionDir, 'server/python'))
    expect(resolve.args).toEqual([join(extensionDir, 'server/main.py'), '--home', home, '--key', ''])
    expect(resolve.env).toEqual({ RESOLVE_HOME: extensionDir })
    expect(resolve.cwd).toBe(extensionDir)
    expect(resolve.unresolved).toEqual(['user_config.api_key'])
    expect(resolve.id).toBe(appIdFor('claude-extension', 'davinci-resolve'))

    const blender = apps.find(app => app.name === 'blender')!
    expect(blender).toMatchObject({ transport: 'stdio', command: 'uvx', args: ['blender-mcp'], env: { BLENDER_PORT: '9876' } })
    expect(apps.find(app => app.name === 'remote')).toMatchObject({ transport: 'remote', url: 'https://mcp.example.com/sse' })
  })

  it('uses APPDATA on Windows and XDG config on Linux for Claude Desktop', () => {
    const home = tempDir()
    const appData = join(home, 'Roaming')
    mkdirSync(join(appData, 'Claude'), { recursive: true })
    writeFileSync(join(appData, 'Claude', 'claude_desktop_config.json'), JSON.stringify({ mcpServers: { win: { command: 'win.exe' } } }))
    expect(discoverMcpApps({ platform: 'win32', homeDir: home, env: { APPDATA: appData } }).map(app => app.name)).toEqual(['win'])

    const linuxHome = tempDir()
    mkdirSync(join(linuxHome, '.config', 'Claude'), { recursive: true })
    writeFileSync(join(linuxHome, '.config', 'Claude', 'claude_desktop_config.json'), JSON.stringify({ mcpServers: { lin: { command: 'lin' } } }))
    expect(discoverMcpApps({ platform: 'linux', homeDir: linuxHome, env: {} }).map(app => app.name)).toEqual(['lin'])
    expect(discoverMcpApps({ platform: 'linux', homeDir: tempDir(), env: {} })).toEqual([])
  })

  it('parses the Codex TOML subset including inline env tables and comments', () => {
    const servers = parseCodexMcpServers(`# comment\n[mcp_servers."my app"]\ncommand = "node" # trailing\nargs = ["a", "b c"]\nenv = { A = "1", B = "2" }\n[other]\nx = 1\n`)
    expect(servers).toEqual({ 'my app': { command: 'node', args: ['a', 'b c'], env: { A: '1', B: '2' } } })
  })

  it('normalizes shared apps in the config', () => {
    expect(parseSharedMcpApps([{ id: 'a', name: 'A', command: 'x', args: ['1', 2], env: { K: 'v', N: 1 } }, { id: 'a', name: 'dup', command: 'y' }, { id: '', name: 'no', command: 'z' }]))
      .toEqual([{ id: 'a', name: 'A', source: 'manual', command: 'x', args: ['1'], env: { K: 'v' }, cwd: undefined, enabled: true }])
    expect(parseDeviceAgentConfig({ sharedApps: [{ id: 'b', name: 'B', command: 'c', enabled: false }] }).sharedApps[0].enabled).toBe(false)
    expect(parseDeviceAgentConfig({}).capabilities.apps).toBe(false)
  })
})

describe('MCP app sessions over the device channel', () => {
  function harness(options: { apps?: DeviceAgentPolicy['apps']; approveApp?: DeviceAgentPolicy['approveApp']; capabilities?: DeviceAgentPolicy['capabilities'] }) {
    const sent: Record<string, unknown>[] = []
    const audit: DeviceAgentAuditEntry[] = []
    const protocol = new DeviceAgentProtocol({
      send: payload => sent.push(payload),
      audit: entry => audit.push(entry),
      policy: () => ({ capabilities: options.capabilities ?? ['apps'], allowedFolders: [], approveExec: async () => true, apps: options.apps ?? [], approveApp: options.approveApp }),
    })
    const waitFor = async (predicate: (message: Record<string, unknown>) => boolean) => {
      const deadline = Date.now() + 5000
      while (Date.now() < deadline) {
        const found = sent.find(predicate)
        if (found) return found
        await wait(10)
      }
      throw new Error(`message not seen; got ${sent.map(m => m.type).join(',')}`)
    }
    return { protocol, sent, audit, waitFor }
  }

  // A stand-in MCP app: echoes each stdin line back prefixed, exits on "quit".
  const echoApp = {
    id: 'echo', name: 'Echo App', command: process.execPath,
    args: ['-e', "process.stdin.setEncoding('utf8');process.stdin.on('data',d=>{for(const l of d.split('\\n')){if(!l)continue;if(l==='quit')process.exit(3);process.stdout.write('echo:'+l+'\\n')}})"],
    env: {},
  }

  it('starts a shared app, pipes data both ways, and reports exit', async () => {
    const approveApp = vi.fn(async () => true)
    const { protocol, waitFor, audit } = harness({ apps: [echoApp], approveApp })
    protocol.handle({ type: 'mcp.open', request_id: 'o1', app_id: 'echo', session_id: 's1' })
    expect(await waitFor(m => m.type === 'mcp.opened')).toMatchObject({ request_id: 'o1', session_id: 's1' })
    expect(protocol.openAppSessions).toBe(1)

    protocol.handle({ type: 'mcp.data', session_id: 's1', data: Buffer.from('{"jsonrpc":"2.0"}\n').toString('base64') })
    const reply = await waitFor(m => m.type === 'mcp.data')
    expect(Buffer.from(String(reply.data), 'base64').toString('utf8')).toBe('echo:{"jsonrpc":"2.0"}\n')

    protocol.handle({ type: 'mcp.data', session_id: 's1', data: Buffer.from('quit\n').toString('base64') })
    expect(await waitFor(m => m.type === 'mcp.exit')).toMatchObject({ session_id: 's1', code: 3 })
    expect(protocol.openAppSessions).toBe(0)
    expect(approveApp).toHaveBeenCalledTimes(1)
    expect(audit.some(entry => entry.kind === 'app' && entry.ok)).toBe(true)
  })

  it('refuses apps that are not shared, denied by the user, or when the capability is off', async () => {
    const notShared = harness({ apps: [echoApp] })
    notShared.protocol.handle({ type: 'mcp.open', request_id: 'o2', app_id: 'other', session_id: 's2' })
    expect(await notShared.waitFor(m => m.type === 'mcp.error')).toMatchObject({ request_id: 'o2', message: expect.stringMatching(/not shared/) })

    const denied = harness({ apps: [echoApp], approveApp: async () => false })
    denied.protocol.handle({ type: 'mcp.open', request_id: 'o3', app_id: 'echo', session_id: 's3' })
    expect(await denied.waitFor(m => m.type === 'mcp.error')).toMatchObject({ message: expect.stringMatching(/denied/) })

    const off = harness({ apps: [echoApp], capabilities: ['exec'] })
    off.protocol.handle({ type: 'mcp.open', request_id: 'o4', app_id: 'echo', session_id: 's4' })
    expect(await off.waitFor(m => m.type === 'mcp.error')).toMatchObject({ request_id: 'o4' })
  })

  it('closes running apps when the connection closes', async () => {
    const { protocol, waitFor } = harness({ apps: [echoApp] })
    protocol.handle({ type: 'mcp.open', request_id: 'o5', app_id: 'echo', session_id: 's5' })
    await waitFor(m => m.type === 'mcp.opened')
    protocol.close()
    expect(protocol.openAppSessions).toBe(0)
  })
})
