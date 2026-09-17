import { expect, test } from '@playwright/test'
import { authenticate, mockHermesApi, TEST_ACCESS_KEY } from './fixtures'

for (const locale of ['en', 'ar']) {
  test(`image provider groups preserve routes and content direction in ${locale}`, async ({ page }, testInfo) => {
    await authenticate(page, TEST_ACCESS_KEY)
    await page.addInitScript(value => localStorage.setItem('hermes_locale', value), locale)
    await mockHermesApi(page, { modelGroups: [{
      provider: 'custom:proxy', label: 'وسيط الصور', models: ['image-custom'],
      base_url: 'https://images.example.test/v1', api_key: '', builtin: false,
    }] })
    let saved: any
    await page.route('**/api/hermes/config/fallback-providers', route => route.fulfill({ json: { fallback_providers: [] } }))
    await page.route('**/api/hermes/config/auxiliary-models', async route => {
      if (route.request().method() === 'PUT') {
        saved = route.request().postDataJSON()
        await route.fulfill({ json: { success: true, auxiliary: saved.auxiliary } })
      } else await route.fulfill({ json: {
        tasks: [{ key: 'image_generation', default_timeout: 600 }],
        auxiliary: { image_generation: { provider: 'custom:proxy', model: 'image-custom', timeout: 600 } },
      } })
    })
    await page.route('**/api/studio/media/image-providers', route => route.fulfill({ json: {
      ok: true, providers: [
        { name: 'new-provider', display_name: 'Future Images', available: true, default_model: 'future-image', models: [{ id: 'future-image' }], capabilities: { modalities: ['text'] } },
        { name: 'unconfigured', display_name: 'Needs setup', available: false, models: [], capabilities: { modalities: ['text'] } },
      ],
    } }))
    await page.goto('/#/hermes/models?tab=auxiliary')
    await page.locator('.auxiliary-row').nth(1).getByRole('button').first().click()
    const provider = page.getByTestId('auxiliary-provider')
    await expect(provider).toContainText('وسيط الصور')
    await provider.click()
    await expect(page.getByText(locale === 'ar' ? 'مدعوم في هرمز' : 'Supported in Hermes', { exact: true })).toBeVisible()
    await expect(page.getByText(locale === 'ar' ? 'مزوّدون مخصصون' : 'Custom providers', { exact: true })).toBeVisible()
    const options = page.locator('.n-base-select-option')
    await expect(options.filter({ hasText: 'Needs setup' })).toHaveClass(/disabled/)
    await expect(options.filter({ hasText: 'وسيط الصور' }).locator('.content-text')).toHaveCSS('direction', 'rtl')
    await expect(options.filter({ hasText: 'Future Images' }).locator('.content-text')).toHaveCSS('direction', 'ltr')
    await expect(page.locator('.n-base-select-menu')).toHaveCSS('opacity', '1')
    await page.screenshot({ path: testInfo.outputPath(`image-provider-groups-${locale}.png`) })
    await provider.locator('input').fill('Future')
    await expect(options.filter({ hasText: 'وسيط الصور' })).toHaveCount(0)
    await options.filter({ hasText: 'Future Images' }).click()
    await expect(page.getByTestId('auxiliary-model')).toContainText('future-image')
    await page.getByTestId('auxiliary-save').click()
    await expect.poll(() => saved?.auxiliary?.image_generation).toEqual({ provider: 'image:new-provider', model: 'future-image', timeout: 600 })
    await expect(page.locator('html')).toHaveAttribute('dir', locale === 'ar' ? 'rtl' : 'ltr')
  })
}
