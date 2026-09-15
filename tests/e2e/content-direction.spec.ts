import { expect, test, type Locator, type Page } from '@playwright/test'
import { authenticate, mockHermesApi, TEST_ACCESS_KEY } from './fixtures'

async function setup(page: Page, locale: string) {
  await authenticate(page, TEST_ACCESS_KEY, 'research')
  await page.addInitScript(value => localStorage.setItem('hermes_locale', value), locale)
  return mockHermesApi(page)
}

async function direction(element: Locator, expected: 'rtl' | 'ltr') {
  await expect(element).toBeVisible()
  await expect(element).toHaveCSS('direction', expected)
  await expect(element).toHaveCSS('text-align', 'start')
}

for (const locale of ['en', 'ar']) {
  test(`clarification content chooses its direction in ${locale} UI and sends the original answer`, async ({ page }, testInfo) => {
    await setup(page, locale)
    await page.goto('/#/hermes/chat')
    const composer = page.locator('.chat-input-area textarea')
    await composer.fill('اختبار اتجاه السؤال')
    await composer.press('Enter')
    const runHandle = await page.waitForFunction(() => (window as any).__PW_CHAT_SOCKET__?.emitted.find((item: any) => item.event === 'run')?.payload)
    const run = await runHandle.jsonValue()
    await page.evaluate(sid => {
      const socket = (window as any).__PW_CHAT_SOCKET__.latest
      socket.__trigger('run.started', { event: 'run.started', session_id: sid, run_id: 'direction-run' })
      socket.__trigger('clarify.requested', {
        event: 'clarify.requested', session_id: sid, run_id: 'direction-run', clarify_id: 'direction-question',
        question: 'أي مجلد نحدّث في API الإصدار 2؟', choices: ['المشروع الحالي', 'Another project'],
        timeout_ms: 300_000, remaining_timeout_ms: 300_000,
      })
    }, run.session_id)
    const panel = page.locator('.approval-float-panel')
    await direction(panel.locator('.approval-float-desc'), 'rtl')
    await direction(panel.locator('.approval-float-actions .content-text').nth(0), 'rtl')
    await direction(panel.locator('.approval-float-actions .content-text').nth(1), 'ltr')
    const answer = panel.locator('input')
    await answer.fill('راجع src/main.ts أولًا')
    await direction(answer, 'rtl')
    await answer.fill('Review src/main.ts ثم أكمل')
    await direction(answer, 'ltr')
    await expect(page.locator('html')).toHaveAttribute('dir', locale === 'ar' ? 'rtl' : 'ltr')
    if (locale === 'ar') await page.screenshot({ path: testInfo.outputPath('clarification-ar.png') })
    await panel.locator('.clarify-float-input-row button').click()
    await expect.poll(() => page.evaluate(() => (window as any).__PW_CHAT_SOCKET__.emitted.find((item: any) => item.event === 'clarify.respond')?.payload.response)).toBe('Review src/main.ts ثم أكمل')
  })

  test(`scheduled job names and inputs are independent from ${locale} UI`, async ({ page }, testInfo) => {
    await setup(page, locale)
    const job = { id: 'rtl-job', job_id: 'rtl-job', name: 'تقرير API اليومي', prompt: 'راجع النتائج ثم اكتب التقرير.', schedule: '*/7 * * * *', schedule_display: '*/7 * * * *', enabled: true, state: 'scheduled', deliver: 'local', skills: [], provider: '', model: '', created_at: '2026-09-15T00:00:00Z' }
    await page.route(/\/api\/hermes\/jobs(?:\/|\?|$)/, async route => {
      const path = new URL(route.request().url()).pathname
      await route.fulfill({ json: path.endsWith('/delivery-targets') ? { targets: [] } : path.endsWith('/rtl-job') ? { job } : { jobs: [job] } })
    })
    await page.goto('/#/hermes/jobs')
    await direction(page.locator('.job-name'), 'rtl')
    await direction(page.locator('.job-card code'), 'ltr')
    await page.locator('.job-card').getByRole('button', { name: locale === 'ar' ? 'تعديل' : 'Edit', exact: true }).click()
    const modal = page.locator('.n-modal')
    const title = modal.locator('input[maxlength="200"]')
    const prompt = modal.locator('textarea')
    await expect(title).toHaveValue(job.name)
    await expect(prompt).toHaveValue(job.prompt)
    await direction(title, 'rtl')
    await direction(prompt, 'rtl')
    await title.fill('Daily تقرير')
    await direction(title, 'ltr')
    await prompt.fill('English first.\nثم العربية.')
    await expect(prompt).toHaveValue('English first.\nثم العربية.')
    await direction(modal.locator('[data-testid="job-schedule-custom"] input'), 'ltr')
    if (locale === 'ar') await page.screenshot({ path: testInfo.outputPath('scheduled-job-ar.png') })
    await expect(page.locator('html')).toHaveAttribute('dir', locale === 'ar' ? 'rtl' : 'ltr')
  })

  test(`kanban card, drawer and comments follow their content in ${locale} UI`, async ({ page }, testInfo) => {
    await setup(page, locale)
    const task = { id: 'rtl-task', title: 'مراجعة API الإصدار 2', body: 'راجع الملف ثم اختبره.', status: 'todo', assignee: 'research', priority: 2, created_at: 1, workspace_kind: 'local' }
    await page.route(/\/api\/hermes\/kanban(?:\/|\?|$)/, async route => {
      const path = new URL(route.request().url()).pathname
      const responses: Record<string, unknown> = {
        '/api/hermes/kanban': { tasks: [task] },
        '/api/hermes/kanban/boards': { boards: [{ slug: 'default', name: 'Default', is_current: true, counts: { todo: 1 }, total: 1 }] },
        '/api/hermes/kanban/capabilities': { capabilities: { source: 'hermes-cli', supports: {}, missing: [] } },
        '/api/hermes/kanban/stats': { stats: { by_status: { todo: 1 }, by_assignee: { research: 1 }, total: 1 } },
        '/api/hermes/kanban/assignees': { assignees: [{ name: 'research', on_disk: true, counts: { todo: 1 } }] },
        '/api/hermes/kanban/rtl-task/attachments': { attachments: [] },
        '/api/hermes/kanban/rtl-task': { task, latest_summary: null, comments: [{ id: 'c1', body: 'English comment ثم عربي', author: 'مراجع', created_at: 1 }], events: [], runs: [], parents: [], children: [] },
      }
      if (!(path in responses)) throw new Error(`Unexpected kanban route: ${path}`)
      await route.fulfill({ json: responses[path] })
    })
    await page.goto('/#/hermes/kanban')
    await direction(page.locator('.card-title'), 'rtl')
    await page.locator('.card-title').click()
    const drawer = page.locator('.n-drawer')
    // Wait for the slide-in transition and verify geometry, not just visibility.
    await expect.poll(() => drawer.evaluate(element => {
      const box = element.getBoundingClientRect()
      return box.left >= -1 && box.right <= innerWidth + 1
    })).toBe(true)
    await direction(drawer.locator('.n-drawer-header .content-text'), 'rtl')
    await direction(drawer.locator('.detail-body'), 'rtl')
    await direction(drawer.locator('.comment-body'), 'ltr')
    const comment = drawer.locator('.comment-input textarea')
    await comment.fill('تمت المراجعة؟')
    await direction(comment, 'rtl')
    if (locale === 'ar') await page.screenshot({ path: testInfo.outputPath('kanban-ar.png') })
    await expect(page.locator('html')).toHaveAttribute('dir', locale === 'ar' ? 'rtl' : 'ltr')
  })
}
