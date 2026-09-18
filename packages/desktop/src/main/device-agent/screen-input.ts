import { execFile } from 'node:child_process'

// Best-effort desktop input for the Device Agent's `screen` capability.
// The desktop app bundles no native input module, so actions are delegated to
// tools that ship with the OS: AppleScript (System Events) on macOS,
// PowerShell + user32 on Windows, and xdotool on Linux when installed.
// macOS requires Accessibility permission for the app; the first action
// triggers the system prompt.

export type ScreenAction =
  | { action: 'click' | 'double_click' | 'right_click' | 'move'; x: number; y: number }
  | { action: 'type'; text: string }
  | { action: 'key'; key: string; modifiers?: string[] }
  | { action: 'scroll'; x: number; y: number; dx?: number; dy?: number }

export interface ScreenCommand {
  file: string
  args: string[]
}

export function parseScreenAction(raw: unknown): ScreenAction | null {
  if (!raw || typeof raw !== 'object') return null
  const record = raw as Record<string, unknown>
  const num = (value: unknown) => (typeof value === 'number' && Number.isFinite(value) ? Math.round(value) : null)
  switch (record.action) {
    case 'click':
    case 'double_click':
    case 'right_click':
    case 'move': {
      const x = num(record.x)
      const y = num(record.y)
      if (x === null || y === null) return null
      return { action: record.action, x, y }
    }
    case 'type':
      return typeof record.text === 'string' && record.text.length > 0 && record.text.length <= 10_000 ? { action: 'type', text: record.text } : null
    case 'key': {
      if (typeof record.key !== 'string' || !/^[a-z0-9_+-]{1,32}$/i.test(record.key)) return null
      const modifiers = Array.isArray(record.modifiers)
        ? record.modifiers.filter((value): value is string => typeof value === 'string' && /^(cmd|command|ctrl|control|alt|option|shift)$/i.test(value)).map(value => value.toLowerCase())
        : []
      return { action: 'key', key: record.key, modifiers }
    }
    case 'scroll': {
      const x = num(record.x)
      const y = num(record.y)
      if (x === null || y === null) return null
      return { action: 'scroll', x, y, dx: num(record.dx) ?? 0, dy: num(record.dy) ?? 0 }
    }
    default:
      return null
  }
}

const MAC_KEY_CODES: Record<string, number> = {
  enter: 36, return: 36, tab: 48, space: 49, delete: 51, backspace: 51, escape: 53, esc: 53,
  left: 123, right: 124, down: 125, up: 126, home: 115, end: 119, pageup: 116, pagedown: 121,
  f1: 122, f2: 120, f3: 99, f4: 118, f5: 96, f6: 97, f7: 98, f8: 100, f9: 101, f10: 109, f11: 103, f12: 111,
}

function appleScriptString(value: string): string {
  return `"${value.replace(/\\/g, '\\\\').replace(/"/g, '\\"')}"`
}

function macModifiers(modifiers: string[] = []): string {
  const names = modifiers.map(modifier => {
    if (modifier === 'cmd' || modifier === 'command') return 'command down'
    if (modifier === 'ctrl' || modifier === 'control') return 'control down'
    if (modifier === 'alt' || modifier === 'option') return 'option down'
    return 'shift down'
  })
  return names.length ? ` using {${names.join(', ')}}` : ''
}

function macScript(action: ScreenAction): string {
  switch (action.action) {
    case 'move':
      return `tell application "System Events" to set the position of the mouse to {${action.x}, ${action.y}}`
    case 'click':
      return `tell application "System Events" to click at {${action.x}, ${action.y}}`
    case 'double_click':
      return `tell application "System Events"\nclick at {${action.x}, ${action.y}}\nclick at {${action.x}, ${action.y}}\nend tell`
    case 'right_click':
      return `tell application "System Events" to tell (click at {${action.x}, ${action.y}}) to perform action "AXShowMenu"`
    case 'type':
      return `tell application "System Events" to keystroke ${appleScriptString(action.text)}`
    case 'key': {
      const lower = action.key.toLowerCase()
      const code = MAC_KEY_CODES[lower]
      if (code !== undefined) return `tell application "System Events" to key code ${code}${macModifiers(action.modifiers)}`
      return `tell application "System Events" to keystroke ${appleScriptString(action.key.length === 1 ? action.key : lower)}${macModifiers(action.modifiers)}`
    }
    case 'scroll':
      return `tell application "System Events" to scroll at {${action.x}, ${action.y}} by {${action.dx || 0}, ${action.dy || 0}}`
  }
}

function xdotoolArgs(action: ScreenAction): string[][] {
  switch (action.action) {
    case 'move': return [['mousemove', String(action.x), String(action.y)]]
    case 'click': return [['mousemove', String(action.x), String(action.y), 'click', '1']]
    case 'double_click': return [['mousemove', String(action.x), String(action.y), 'click', '--repeat', '2', '1']]
    case 'right_click': return [['mousemove', String(action.x), String(action.y), 'click', '3']]
    case 'type': return [['type', '--delay', '10', action.text]]
    case 'key': {
      const modifiers = (action.modifiers || []).map(modifier => {
        if (modifier === 'cmd' || modifier === 'command') return 'super'
        if (modifier === 'ctrl' || modifier === 'control') return 'ctrl'
        if (modifier === 'alt' || modifier === 'option') return 'alt'
        return 'shift'
      })
      const key = action.key.toLowerCase() === 'enter' ? 'Return' : action.key
      return [['key', [...modifiers, key].join('+')]]
    }
    case 'scroll': {
      const commands: string[][] = [['mousemove', String(action.x), String(action.y)]]
      const dy = action.dy || 0
      const dx = action.dx || 0
      if (dy) commands.push(['click', '--repeat', String(Math.min(50, Math.abs(dy))), dy > 0 ? '5' : '4'])
      if (dx) commands.push(['click', '--repeat', String(Math.min(50, Math.abs(dx))), dx > 0 ? '7' : '6'])
      return commands
    }
  }
}

function powershellScript(action: ScreenAction): string {
  const prelude = `Add-Type -AssemblyName System.Windows.Forms
Add-Type @"
using System;using System.Runtime.InteropServices;
public static class U32{[DllImport("user32.dll")]public static extern bool SetCursorPos(int x,int y);[DllImport("user32.dll")]public static extern void mouse_event(uint f,uint x,uint y,uint d,UIntPtr e);}
"@
`
  const click = (down: number, up: number) => `[U32]::mouse_event(${down},0,0,0,[UIntPtr]::Zero);[U32]::mouse_event(${up},0,0,0,[UIntPtr]::Zero)`
  const psString = (value: string) => `'${value.replace(/'/g, "''")}'`
  switch (action.action) {
    case 'move': return `${prelude}[U32]::SetCursorPos(${action.x},${action.y})`
    case 'click': return `${prelude}[U32]::SetCursorPos(${action.x},${action.y});${click(2, 4)}`
    case 'double_click': return `${prelude}[U32]::SetCursorPos(${action.x},${action.y});${click(2, 4)};${click(2, 4)}`
    case 'right_click': return `${prelude}[U32]::SetCursorPos(${action.x},${action.y});${click(8, 16)}`
    case 'type': return `${prelude}[System.Windows.Forms.SendKeys]::SendWait(${psString(action.text.replace(/[+^%~(){}[\]]/g, match => `{${match}}`))})`
    case 'key': {
      const modifiers = (action.modifiers || []).map(modifier => (modifier === 'ctrl' || modifier === 'control' || modifier === 'cmd' || modifier === 'command') ? '^' : (modifier === 'alt' || modifier === 'option') ? '%' : '+').join('')
      const key = action.key.length === 1 ? action.key : `{${action.key.toUpperCase()}}`
      return `${prelude}[System.Windows.Forms.SendKeys]::SendWait(${psString(`${modifiers}${key}`)})`
    }
    case 'scroll': return `${prelude}[U32]::SetCursorPos(${action.x},${action.y});[U32]::mouse_event(2048,0,0,${-(action.dy || 0) * 120},[UIntPtr]::Zero)`
  }
}

/** Build the OS command(s) for an action; exported for tests. */
export function buildScreenActionCommands(platform: NodeJS.Platform, action: ScreenAction): ScreenCommand[] {
  if (platform === 'darwin') return [{ file: 'osascript', args: ['-e', macScript(action)] }]
  if (platform === 'win32') return [{ file: 'powershell.exe', args: ['-NoProfile', '-NonInteractive', '-Command', powershellScript(action)] }]
  return xdotoolArgs(action).map(args => ({ file: 'xdotool', args }))
}

export async function runScreenAction(action: ScreenAction, platform: NodeJS.Platform = process.platform): Promise<void> {
  for (const command of buildScreenActionCommands(platform, action)) {
    await new Promise<void>((resolve, reject) => {
      execFile(command.file, command.args, { timeout: 15_000, windowsHide: true }, (error, _stdout, stderr) => {
        if (!error) return resolve()
        const code = (error as NodeJS.ErrnoException).code
        if (code === 'ENOENT') return reject(new Error(`${command.file} is not available on this device`))
        reject(new Error(String(stderr || error.message).trim()))
      })
    })
  }
}
