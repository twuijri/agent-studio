import { expect, test, type Locator, type Page } from '@playwright/test'
import { authenticate, mockHermesApi, TEST_ACCESS_KEY } from './fixtures'

async function select(page: Page, control: Locator, label: string) {
  await control.click()
  await page.locator('.n-base-select-option:visible').filter({ hasText: label }).click()
}

function silentWav() {
  const audio = Buffer.alloc(44 + 1600)
  audio.write('RIFF', 0)
  audio.writeUInt32LE(audio.length - 8, 4)
  audio.write('WAVEfmt ', 8)
  audio.writeUInt32LE(16, 16)
  audio.writeUInt16LE(1, 20)
  audio.writeUInt16LE(1, 22)
  audio.writeUInt32LE(16000, 24)
  audio.writeUInt32LE(32000, 28)
  audio.writeUInt16LE(2, 32)
  audio.writeUInt16LE(16, 34)
  audio.write('data', 36)
  audio.writeUInt32LE(1600, 40)
  return audio
}

for (const locale of ['en', 'ar']) {
  test(`Groq TTS saves, restores and tests model-specific voices in ${locale}`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width: 1440, height: 1000 })
    await authenticate(page, TEST_ACCESS_KEY)
    await page.addInitScript(value => {
      localStorage.setItem('hermes_locale', value)
      // No speaker output or external synthesis in this settings test.
      HTMLMediaElement.prototype.play = async function () {}
      HTMLMediaElement.prototype.pause = function () {}
    }, locale)
    await mockHermesApi(page)
    let row: any = null
    const saves: any[] = []
    let synthesis: any
    await page.route('**/api/studio/tts/settings', route => route.fulfill({ json: { settings: row ? [row] : [], activeProvider: row ? 'groq' : 'edge' } }))
    await page.route('**/api/studio/tts/settings/active', async route => {
      expect(route.request().postDataJSON()).toEqual({ provider: 'groq' })
      await route.fulfill({ json: { activeProvider: 'groq' } })
    })
    await page.route('**/api/studio/tts/settings/groq', async route => {
      const body = route.request().postDataJSON()
      saves.push(body)
      row = { provider: 'groq', settings: body.settings, secrets: { apiKey: '[stored]' }, updatedAt: 1 }
      await route.fulfill({ json: { setting: row, activeProvider: 'groq' } })
    })
    await page.route('**/api/studio/stt/settings', route => route.fulfill({ json: { settings: [], activeProvider: 'browser' } }))
    await page.route('**/api/studio/tts/synthesize', async route => {
      synthesis = route.request().postDataJSON()
      await route.fulfill({ contentType: 'audio/wav', body: silentWav() })
    })
    await page.goto('/#/hermes/models?tab=tts')
    await page.getByRole('button', { name: locale === 'ar' ? 'إضافة API لتحويل النص إلى كلام' : 'Add TTS API', exact: true }).click()
    await select(page, page.getByTestId('voice-provider-select'), 'Groq TTS')
    await expect(page.getByTestId('voice-provider-model')).toContainText('canopylabs/orpheus-v1-english')
    await expect(page.getByTestId('voice-provider-voice')).toContainText('troy')
    await page.getByTestId('voice-provider-api-key').locator('input').fill('fake-groq-key')
    await expect(page.getByTestId('voice-provider-base-url').locator('input')).toHaveAttribute('dir', 'ltr')
    await select(page, page.getByTestId('voice-provider-model'), 'canopylabs/orpheus-arabic-saudi')
    await expect(page.getByTestId('voice-provider-voice')).toContainText('abdullah')
    await page.getByTestId('voice-provider-voice').click()
    const options = page.locator('.n-base-select-option:visible')
    await expect(options).toHaveCount(6)
    await expect(options.filter({ hasText: 'troy' })).toHaveCount(0)
    await expect(options.filter({ hasText: 'noura' }).locator('.content-text')).toHaveCSS('direction', 'ltr')
    await expect(page.locator('.n-base-select-menu:visible')).toHaveCSS('opacity', '1')
    await page.screenshot({ path: testInfo.outputPath(`groq-voices-${locale}.png`) })
    await options.filter({ hasText: 'noura' }).click()
    await page.getByTestId('voice-provider-save').click()
    await expect.poll(() => saves.length).toBe(1)
    expect(saves[0]).toMatchObject({ activeProvider: 'groq', settings: { model: 'canopylabs/orpheus-arabic-saudi', voice: 'noura' }, secrets: { apiKey: 'fake-groq-key' } })
    await expect(page.getByTestId('voice-provider-save')).not.toBeVisible()

    await page.reload()
    const card = page.locator('.voice-api-card').filter({ hasText: 'Groq TTS' })
    await expect(card).toContainText('noura')
    await card.getByRole('button', { name: locale === 'ar' ? 'تعديل' : 'Edit', exact: true }).click()
    await expect(page.getByTestId('groq-edit-voice')).toContainText('noura')
    await select(page, page.getByTestId('groq-edit-model'), 'canopylabs/orpheus-v1-english')
    await expect(page.getByTestId('groq-edit-voice')).toContainText('troy')
    await page.getByTestId('groq-edit-voice').click()
    await expect(options).toHaveCount(6)
    await expect(options.filter({ hasText: 'noura' })).toHaveCount(0)
    await options.filter({ hasText: 'hannah' }).click()
    await page.locator('.n-drawer').getByRole('button', { name: locale === 'ar' ? 'حفظ' : 'Save', exact: true }).click()
    await expect.poll(() => saves.length).toBe(2)
    expect(saves[1].settings).toMatchObject({ model: 'canopylabs/orpheus-v1-english', voice: 'hannah' })
    expect(saves[1].secrets).toBeUndefined()
    await expect(page.getByTestId('groq-edit-model')).not.toBeVisible()
    await page.getByTestId('tts-test-text').locator('input').fill('Hello from Studio')
    await page.getByTestId('voice-card-test-tts-groq').click()
    await expect.poll(() => synthesis).toMatchObject({ provider: 'groq', text: 'Hello from Studio', options: { model: 'canopylabs/orpheus-v1-english', voice: 'hannah' } })
    expect(synthesis.options.apiKey).toBeUndefined()
    await expect(page.locator('html')).toHaveAttribute('dir', locale === 'ar' ? 'rtl' : 'ltr')
  })
}
