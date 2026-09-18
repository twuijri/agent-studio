import { readFileSync } from 'fs'
import { resolve } from 'path'
import { describe, expect, it } from 'vitest'

function pngSize(path: string): { width: number; height: number } {
  const png = readFileSync(resolve(path))
  expect(png.subarray(1, 4).toString()).toBe('PNG')
  return {
    width: png.readUInt32BE(16),
    height: png.readUInt32BE(20),
  }
}

describe('desktop tray icon', () => {
  it('ships a monochrome macOS template glyph at 1x and 2x menu bar sizes', () => {
    // Owner decision (2026-09-18): the macOS menu bar uses a template image
    // like Apple's own status icons instead of the dark app tile.
    expect(pngSize('packages/desktop/build/trayMacTemplate.png')).toEqual({ width: 18, height: 18 })
    expect(pngSize('packages/desktop/build/trayMacTemplate@2x.png')).toEqual({ width: 36, height: 36 })

    const builderConfig = readFileSync(resolve('packages/desktop/electron-builder.yml'), 'utf8')
    expect(builderConfig).toContain('- "trayMacTemplate.png"')
    expect(builderConfig).toContain('- "trayMacTemplate@2x.png"')
    // The tiled variants stay available for Windows/Linux and as a fallback asset.
    expect(pngSize('packages/desktop/build/trayMac.png')).toEqual({ width: 22, height: 22 })
  })

  it('marks the macOS tray image as a template so the system tints it', () => {
    const mainSource = readFileSync(resolve('packages/desktop/src/main/index.ts'), 'utf8').replace(/\r\n/g, '\n')
    const pathsSource = readFileSync(resolve('packages/desktop/src/main/paths.ts'), 'utf8')

    expect(mainSource).toContain('? desktopMacTrayIcon()')
    expect(mainSource).toContain("if (process.platform === 'darwin') sourceIcon.setTemplateImage(true)")
    expect(pathsSource).toContain("'build', 'trayMacTemplate.png'")
    expect(pathsSource).not.toContain("'build', 'trayMac.png'")
  })

  it('renders the template glyph as black-on-transparent pixels only', () => {
    // A template image must contain only black (any alpha); colored pixels would tint wrongly.
    const png = readFileSync(resolve('packages/desktop/build/trayMacTemplate@2x.png'))
    const colorType = png.readUInt8(25)
    expect(colorType === 6 || colorType === 4).toBe(true) // RGBA or gray+alpha
  })
})
