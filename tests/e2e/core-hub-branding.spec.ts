import { expect, test } from '@playwright/test'
import { mockHermesApi } from './fixtures'

for (const locale of ['en', 'ar']) {
  for (const theme of ['light', 'dark']) {
    test(`Core Hub login branding in ${locale}/${theme}`, async ({ page }, testInfo) => {
      await mockHermesApi(page)
      await page.addInitScript(({ locale, theme }) => {
        localStorage.setItem('hermes_locale', locale)
        localStorage.setItem('hermes_brightness', theme)
      }, { locale, theme })
      await page.goto('/')
      await expect(page.getByRole('heading', { name: locale === 'ar' ? 'كور هب' : 'Core Hub', exact: true })).toBeVisible()
      await expect(page).toHaveTitle('Core Hub')
      await expect(page.locator('html')).toHaveAttribute('dir', locale === 'ar' ? 'rtl' : 'ltr')
      const logo = page.getByRole('img', { name: 'Core Hub', exact: true })
      await expect(logo).toBeVisible()
      await expect.poll(() => logo.evaluate((node: HTMLImageElement) => node.complete && node.naturalWidth === 1024)).toBe(true)
      await page.screenshot({ path: testInfo.outputPath(`core-hub-${locale}-${theme}.png`) })
    })
  }
}
