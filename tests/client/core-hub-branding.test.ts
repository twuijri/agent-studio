import { readFileSync, readdirSync } from 'node:fs'
import { createHash } from 'node:crypto'
import { describe, expect, it } from 'vitest'
import { parse } from 'yaml'
import sharp from 'sharp'

const read = (file: string) => readFileSync(file, 'utf8')

describe('Core Hub branding without a data or deployment migration', () => {
  it('keeps the original license and installed desktop identity', () => {
    expect(createHash('sha256').update(readFileSync('LICENSE')).digest('hex'))
      .toBe('34817b1cbee88f09a2307e761b32aa237392db74385835770cd0032e23e33a69')
    expect(read('NOTICE.personal.md')).toContain('EKKOLearnAI')
    expect(JSON.parse(read('packages/desktop/package.json')).name).toBe('hermes-studio')
    const desktop = parse(read('packages/desktop/electron-builder.yml'))
    expect(desktop.appId).toBe('us.i3u.agentstudio')
    expect(desktop.productName).toBe('Core Hub')
    const migration = read('packages/desktop/src/main/login-item-migration.ts')
    expect(migration).toContain("['Agent Studio.exe', 'Ekko Studio.exe', 'Hermes Studio.exe']")
    for (const platform of ['mac', 'win', 'linux']) {
      expect(desktop[platform].artifactName).toBe('Core.Hub-${version}-${arch}.${ext}')
    }
  })

  it('uses the approved display name in every locale without rewriting upstream history', () => {
    for (const locale of readdirSync('packages/client/src/i18n/locales').filter(name => name.endsWith('.ts'))) {
      const source = read(`packages/client/src/i18n/locales/${locale}`)
      expect(source).toContain(locale === 'ar.ts' ? "title: 'كور هب'" : "title: 'Core Hub'")
      expect(source).not.toContain('Agent Studio')
      // Upstream release notes are historical evidence, not our product identity.
      expect(source).toContain("new_0_7_19_1:")
      expect(source).toContain('Ekko Studio')
    }
  })

  it('uses the new image package before and after the repo rename without changing release or storage guards', () => {
    const text = read('.github/workflows/personal-image.yml')
    const workflow = parse(text)
    expect(workflow.jobs.image.if).toBe("github.repository == 'twuijri/agent-studio' || github.repository == 'twuijri/core-hub'")
    expect(workflow.name).toBe('Personal Core Hub image')
    expect(text).toContain('process.env.GITHUB_REPOSITORY')
    expect(text).toContain("['Build', 'Playwright', 'Personal fork license']")
    // The source repository is public (2026-09-18) and so is the image package (2026-09-19):
    // the workflow reports visibility but never refuses to publish because of it.
    expect(text).not.toContain('(await repo.json()).private !== true')
    expect(text).not.toContain("visibility !== 'private'")
    expect(text).toContain('Report the image package visibility')
    expect(text).toContain("const expected = '${{ inputs.image_repo }}' === 'core-hub' ? 'public' : 'private';")
    expect(text).toContain('if (visibility !== expected) console.log(`::warning::')
    // The package name is an input (core-hub = public deployment image, core-hub-test = private test track).
    expect(text).toContain('ghcr.io/twuijri/${{ inputs.image_repo }}:latest')
    expect(text).toContain('Refuse to promote the test-track package to latest')
    expect(text).not.toContain('ghcr.io/twuijri/agent-studio:')
    expect(text).not.toContain('packages/container/agent-studio')
    expect(text.match(/packages\/container\/core-hub/g)).toHaveLength(2)
    const imageReferences = text.match(/ghcr\.io\/twuijri\/[a-z-]+/g) || []
    expect(imageReferences.length).toBeGreaterThan(5)
    expect(new Set(imageReferences)).toEqual(new Set(['ghcr.io/twuijri/core-hub']))
    expect(text).toContain('--install-agents')
    expect(text).toContain('node scripts/check-personal-license.mjs')
    expect(read('compose.personal.yml')).toContain('agent-studio-hermes:/home/agent/.hermes')
    expect(read('compose.personal.yml')).toContain('agent-studio-state:/home/agent/.hermes-web-ui')
    const compose = parse(read('compose.personal.yml'))
    expect(compose.services['agent-studio'].image).toBe('core-hub:personal')
    expect(Object.keys(compose.volumes).sort()).toEqual(['agent-studio-hermes', 'agent-studio-state'])
  })

  it('ships matching web/desktop marks and real, correctly sized installable icons', async () => {
    expect(readFileSync('packages/client/public/logo.png')).toEqual(readFileSync('packages/desktop/build/icon.png'))
    const manifest = JSON.parse(read('packages/client/public/manifest.webmanifest'))
    expect(manifest.start_url).toBe('/#/hermes/chat')
    expect(manifest.icons).toHaveLength(2)
    for (const icon of manifest.icons) {
      const data = await sharp(`packages/client/public${icon.src}`).metadata()
      expect(`${data.width}x${data.height}`).toBe(icon.sizes)
    }
    const { data, info } = await sharp('packages/client/public/logo.png').removeAlpha().raw().toBuffer({ resolveWithObject: true })
    const pixel = (x: number, y: number) => data[(y * info.width + x) * info.channels]
    expect(pixel(0, 0)).toBe(16)
    expect(pixel(512, 512)).toBe(255) // central core
    expect(pixel(700, 512)).toBe(16) // open C gap
    expect(pixel(512, 250)).toBe(255) // outer C
  })
})
