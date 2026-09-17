import { expect, test } from '@playwright/test'
import { authenticate, mockHermesApi, TEST_ACCESS_KEY } from './fixtures'

test('offers direct personal-server access without a paid App download', async ({ page }) => {
  await authenticate(page, TEST_ACCESS_KEY, 'research')
  const api = await mockHermesApi(page)

  await page.goto('/#/hermes/connections?view=download')

  await expect(page.getByText('Core Hub · personal server')).toBeVisible()
  await expect(page.getByRole('link', { name: 'View plans' })).toHaveCount(0)
  await expect(page.getByText('Official direct', { exact: true })).toHaveCount(0)
  await expect(page.locator('.n-tabs-tab--active').filter({ hasText: 'Direct connection' })).toBeVisible()
  await page.screenshot({ path: '/tmp/agent-studio-direct-en.png', fullPage: true })
  expect(api.unexpectedRequests).toEqual([])
})

test('shows the personal connection page in Arabic with RTL and an LTR server address', async ({ page }) => {
  await authenticate(page)
  await page.addInitScript(() => localStorage.setItem('hermes_locale', 'ar'))
  const api = await mockHermesApi(page)
  await page.goto('/#/hermes/connections')
  await expect(page.getByText('كور هب · سيرفرك الشخصي', { exact: true })).toBeVisible()
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl')
  await expect(page.locator('.personal-connections code')).toHaveAttribute('dir', 'ltr')
  await page.screenshot({ path: '/tmp/agent-studio-direct-ar.png', fullPage: true })
  expect(api.unexpectedRequests).toEqual([])
})
