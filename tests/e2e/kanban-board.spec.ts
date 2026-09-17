import { expect, test, type Page } from '@playwright/test'
import { authenticate, mockHermesApi, TEST_ACCESS_KEY } from './fixtures'

const now = Math.floor(Date.now() / 1000)

interface MockTask {
  id: string
  title: string
  status: string
  created_at: number
  [key: string]: unknown
}

function makeTask(index: number, status: string, title?: string): MockTask {
  return {
    id: `task-${index}`,
    title: title || `Board task ${index}`,
    body: `Task body ${index}`,
    assignee: 'research',
    status,
    priority: 2,
    created_by: null,
    created_at: now + index,
    started_at: null,
    completed_at: null,
    workspace_kind: 'local',
    workspace_path: null,
    tenant: null,
    result: null,
    skills: null,
  }
}

interface TransitionCall {
  path: string
  body: Record<string, unknown>
}

async function mockKanbanBoard(page: Page, tasks: MockTask[]) {
  const transitions: TransitionCall[] = []

  await page.route(/\/api\/hermes\/kanban(?:\/|\?|$)/, async (route) => {
    const request = route.request()
    const pathname = new URL(request.url()).pathname
    const json = (body: unknown) => route.fulfill({ contentType: 'application/json', body: JSON.stringify(body) })
    const counts = () => tasks.reduce<Record<string, number>>((acc, task) => {
      acc[task.status] = (acc[task.status] || 0) + 1
      return acc
    }, {})

    if (pathname === '/api/hermes/kanban/boards') {
      await json({
        boards: [{
          slug: 'default',
          name: 'Default',
          description: '',
          icon: '',
          color: '',
          created_at: now,
          archived: false,
          is_current: true,
          counts: counts(),
          total: tasks.length,
        }],
      })
      return
    }

    if (pathname === '/api/hermes/kanban/capabilities') {
      await json({
        capabilities: {
          source: 'hermes-cli',
          supports: { promote: true, schedule: true, requestReview: true, reopenReview: true, bulk: true },
          missing: [],
        },
      })
      return
    }

    if (pathname === '/api/hermes/kanban/stats') {
      await json({ stats: { by_status: counts(), by_assignee: { research: tasks.length }, total: tasks.length } })
      return
    }

    if (pathname === '/api/hermes/kanban/assignees') {
      await json({ assignees: [{ name: 'research', on_disk: true, counts: counts() }] })
      return
    }

    const transition = pathname.match(/^\/api\/hermes\/kanban\/(task-\d+)\/(promote|schedule|request-review|reopen-review|block)$/)
    if (transition && request.method() === 'POST') {
      const task = tasks.find(item => item.id === transition[1])
      const targetByAction: Record<string, string> = {
        promote: 'ready',
        schedule: 'scheduled',
        'request-review': 'review',
        'reopen-review': 'ready',
        block: 'blocked',
      }
      if (task) task.status = targetByAction[transition[2]]
      transitions.push({ path: pathname, body: request.postDataJSON() || {} })
      await json({ ok: true })
      return
    }

    if (/^\/api\/hermes\/kanban\/task-\d+\/attachments$/.test(pathname)) {
      await json({ attachments: [] })
      return
    }

    const taskMatch = pathname.match(/^\/api\/hermes\/kanban\/(task-\d+)$/)
    if (taskMatch) {
      const task = tasks.find(item => item.id === taskMatch[1])
      await json({ task, latest_summary: null, comments: [], events: [], runs: [], parents: [], children: [] })
      return
    }

    if (pathname === '/api/hermes/kanban') {
      await json({ tasks })
      return
    }

    await route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: JSON.stringify({ error: `Unexpected Kanban route: ${request.method()} ${pathname}` }),
    })
  })

  return { transitions }
}

async function scrollColumnToStart(page: Page, columnId: string) {
  await page.evaluate((id) => {
    const board = document.querySelector('[data-testid="kanban-board"]') as HTMLElement
    const column = document.querySelector(`.kanban-column[data-column="${id}"]`) as HTMLElement
    board.scrollLeft += column.getBoundingClientRect().left - board.getBoundingClientRect().left - 20
  }, columnId)
}

async function dragCardToColumn(page: Page, taskId: string, targetColumn: string) {
  // Never start a drag while a previous transition is still refreshing the board,
  // and let Sortable's 150ms reorder animation settle: it ignores a press on an
  // item that is still animating.
  await expect(page.getByTestId('kanban-board')).toHaveAttribute('data-busy', 'false')
  await page.waitForTimeout(250)
  const card = page.locator(`.task-slot[data-task-id="${taskId}"]`)
  const sourceColumn = await card.evaluate(element => element.closest('.kanban-column')!.getAttribute('data-column'))
  // Keep both the source card and the target column inside the viewport so the
  // pointer path never leaves the board.
  await scrollColumnToStart(page, sourceColumn!)
  const target = page.locator(`.task-list[data-column="${targetColumn}"]`)
  const cardBox = (await card.boundingBox())!
  const targetBox = (await target.boundingBox())!
  const startX = cardBox.x + cardBox.width / 2
  const startY = cardBox.y + Math.min(24, cardBox.height / 2)
  await page.mouse.move(startX, startY)
  await page.mouse.down()
  // Sortable's fallback needs to pass its tolerance before the drag begins.
  await page.mouse.move(startX + 12, startY + 12, { steps: 4 })
  await expect(page.getByTestId('kanban-board')).toHaveClass(/dragging/)
  await page.mouse.move(targetBox.x + targetBox.width / 2, targetBox.y + Math.min(60, targetBox.height / 2), { steps: 16 })
  await page.mouse.up()
  await expect(page.getByTestId('kanban-board')).not.toHaveClass(/dragging/)
}

test('scrolls the board sideways, keeps columns scrollable, and opens cards', async ({ page }) => {
  await authenticate(page, TEST_ACCESS_KEY, 'research')
  const api = await mockHermesApi(page)
  const tasks = Array.from({ length: 12 }, (_, index) => makeTask(index + 1, 'todo'))
  await mockKanbanBoard(page, tasks)

  await page.goto('/#/hermes/kanban')

  const board = page.getByTestId('kanban-board')
  await expect(board).toBeVisible()
  await expect(page.locator('.kanban-column')).toHaveCount(4)
  expect(await page.locator('.kanban-column').evaluateAll(columns => columns.map(column => column.getAttribute('data-column')))).toEqual([
    'queue', 'waiting', 'review', 'done',
  ])
  await expect(page.getByTestId('kanban-inbox')).toBeVisible()
  const boardMetrics = await board.evaluate(element => ({ scrollWidth: element.scrollWidth, clientWidth: element.clientWidth }))
  expect(boardMetrics.scrollWidth).toBeGreaterThan(boardMetrics.clientWidth)

  const taskList = page.locator('.task-list[data-column="queue"]')
  const dimensions = await taskList.evaluate(element => ({ clientHeight: element.clientHeight, scrollHeight: element.scrollHeight }))
  expect(dimensions.scrollHeight).toBeGreaterThan(dimensions.clientHeight)

  await page.locator('.kanban-column.column-done').scrollIntoViewIfNeeded()
  await expect.poll(() => board.evaluate(element => Math.abs(element.scrollLeft))).toBeGreaterThan(0)
  await page.locator('.kanban-column.column-queue').scrollIntoViewIfNeeded()

  await page.getByRole('button', { name: 'Board task 12' }).click()
  await expect(page.locator('.n-drawer').getByText('Board task 12', { exact: true })).toBeVisible()
  expect(api.unexpectedRequests).toEqual([])
})

test('moves cards between columns through the Hermes transition bridge', async ({ page }) => {
  await page.setViewportSize({ width: 1600, height: 900 })
  await authenticate(page, TEST_ACCESS_KEY, 'research')
  const api = await mockHermesApi(page)
  const tasks = [makeTask(1, 'todo', 'Promote me'), makeTask(2, 'ready', 'Review me'), makeTask(3, 'ready', 'Block me')]
  const { transitions } = await mockKanbanBoard(page, tasks)

  await page.goto('/#/hermes/kanban')
  await expect(page.locator('.task-slot[data-task-id="task-1"]')).toBeVisible()

  // todo -> review is not a manual Hermes transition, so the drop is refused.
  await dragCardToColumn(page, 'task-1', 'review')
  await expect(page.locator('.task-list[data-column="queue"] .task-slot[data-task-id="task-1"]')).toBeVisible()
  expect(transitions).toEqual([])

  // todo and ready share the queue column, so promotion is the card's quick action.
  await page.locator('.task-slot[data-task-id="task-1"]').hover()
  await page.locator('.task-slot[data-task-id="task-1"] .card-quick-action[data-action="promote"]').click()
  await expect.poll(() => transitions.map(call => call.path)).toEqual(['/api/hermes/kanban/task-1/promote'])
  await expect(page.locator('.task-slot[data-task-id="task-1"][data-status="ready"]')).toBeVisible()

  // ready -> review requests a review through the CLI bridge.
  await dragCardToColumn(page, 'task-2', 'review')
  await expect.poll(() => transitions.map(call => call.path)).toEqual([
    '/api/hermes/kanban/task-1/promote',
    '/api/hermes/kanban/task-2/request-review',
  ])
  await expect(page.locator('.task-list[data-column="review"] .task-slot[data-task-id="task-2"]')).toBeVisible()

  // ready -> waiting asks whether to schedule or block; blocking needs a reason.
  await dragCardToColumn(page, 'task-3', 'waiting')
  const choice = page.getByTestId('kanban-drop-choice')
  await expect(choice).toBeVisible()
  expect(transitions).toHaveLength(2)
  await choice.locator('[data-choice="block"]').click()
  const reason = page.getByTestId('kanban-drop-reason').locator('input')
  await expect(reason).toBeVisible()
  await reason.fill('waiting for credentials')
  await page.getByRole('button', { name: 'OK' }).click()
  await expect.poll(() => transitions.map(call => call.path)).toEqual([
    '/api/hermes/kanban/task-1/promote',
    '/api/hermes/kanban/task-2/request-review',
    '/api/hermes/kanban/task-3/block',
  ])
  expect(transitions[2].body).toEqual({ reason: 'waiting for credentials' })
  await expect(page.locator('.task-list[data-column="waiting"] .task-slot[data-task-id="task-3"][data-status="blocked"]')).toBeVisible()
  expect(api.unexpectedRequests).toEqual([])
})

test('keeps manual card order in the browser only and never moves columns', async ({ page }) => {
  await authenticate(page, TEST_ACCESS_KEY, 'research')
  const api = await mockHermesApi(page)
  const { transitions } = await mockKanbanBoard(page, [makeTask(1, 'todo', 'Older card'), makeTask(2, 'todo', 'Newer card')])

  await page.goto('/#/hermes/kanban')
  const todoSlots = page.locator('.task-list[data-column="queue"] .task-slot')
  await expect(todoSlots).toHaveCount(2)
  const order = () => todoSlots.evaluateAll(slots => slots.map(slot => slot.getAttribute('data-task-id')))
  expect(await order()).toEqual(['task-2', 'task-1'])

  // Column headers are not drag handles: dragging one leaves the workflow order intact.
  const queueHeader = page.locator('.kanban-column.column-queue .column-header')
  const waitingHeader = page.locator('.kanban-column.column-waiting .column-header')
  const from = (await queueHeader.boundingBox())!
  const to = (await waitingHeader.boundingBox())!
  await page.mouse.move(from.x + from.width / 2, from.y + from.height / 2)
  await page.mouse.down()
  await page.mouse.move(from.x + from.width / 2 + 12, from.y + from.height / 2, { steps: 4 })
  await page.mouse.move(to.x + to.width - 12, to.y + to.height / 2, { steps: 16 })
  await page.mouse.up()
  expect(await page.locator('.kanban-column').evaluateAll(columns => columns.slice(0, 2).map(column => column.getAttribute('data-column')))).toEqual(['queue', 'waiting'])

  // Reordering cards inside a column is a browser-only preference.
  await expect(page.getByTestId('kanban-board')).toHaveAttribute('data-busy', 'false')
  const lower = (await page.locator('.task-slot[data-task-id="task-1"]').boundingBox())!
  const upper = (await page.locator('.task-slot[data-task-id="task-2"]').boundingBox())!
  await page.mouse.move(lower.x + lower.width / 2, lower.y + 20)
  await page.mouse.down()
  await page.mouse.move(lower.x + lower.width / 2, lower.y + 34, { steps: 4 })
  await page.mouse.move(upper.x + upper.width / 2, upper.y + 8, { steps: 16 })
  await page.mouse.up()
  await expect.poll(order).toEqual(['task-1', 'task-2'])
  await expect(page.getByRole('button', { name: 'Reset layout' })).toBeVisible()
  const stored = await page.evaluate(() => window.localStorage.getItem('hermes.kanban.layout.default'))
  expect(JSON.parse(stored || '{}')).toEqual({ cards: { queue: ['task-1', 'task-2'] } })
  expect(transitions).toEqual([])

  await page.reload()
  await expect.poll(order).toEqual(['task-1', 'task-2'])

  await page.getByRole('button', { name: 'Reset layout' }).click()
  await expect.poll(order).toEqual(['task-2', 'task-1'])
  expect(await page.evaluate(() => window.localStorage.getItem('hermes.kanban.layout.default'))).toBeNull()
  expect(api.unexpectedRequests).toEqual([])
})

test('scrolls sideways with a vertical wheel over a short card list', async ({ page }) => {
  await authenticate(page, TEST_ACCESS_KEY, 'research')
  await mockHermesApi(page)
  await mockKanbanBoard(page, [makeTask(1, 'todo')])

  await page.goto('/#/hermes/kanban')
  const board = page.getByTestId('kanban-board')
  await expect(page.locator('.task-slot[data-task-id="task-1"]')).toBeVisible()
  expect(await board.evaluate(element => element.scrollLeft)).toBe(0)

  const list = page.locator('.task-list[data-column="queue"]')
  const box = (await list.boundingBox())!
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
  await page.mouse.wheel(0, 240)
  await expect.poll(() => board.evaluate(element => element.scrollLeft)).toBeGreaterThan(0)
})

test('keeps triage in the inbox strip and archived tasks behind the done column toggle', async ({ page }) => {
  await authenticate(page, TEST_ACCESS_KEY, 'research')
  const api = await mockHermesApi(page)
  await mockKanbanBoard(page, [makeTask(1, 'triage', 'Needs a spec'), makeTask(2, 'done', 'Shipped'), makeTask(3, 'archived', 'Old work')])

  await page.goto('/#/hermes/kanban')
  await expect(page.locator('.task-slot[data-task-id="task-2"]')).toBeVisible()
  await expect(page.locator('.task-slot[data-task-id="task-1"]')).toHaveCount(0)
  await expect(page.locator('.task-slot[data-task-id="task-3"]')).toHaveCount(0)

  const inbox = page.getByTestId('kanban-inbox')
  await expect(inbox.locator('.inbox-count')).toHaveText('1')
  await inbox.locator('.inbox-toggle').click()
  await expect(inbox.getByRole('button', { name: 'Needs a spec' })).toBeVisible()

  const done = page.locator('.kanban-column.column-done')
  await expect(done.locator('.archive-toggle')).toHaveText('Show archived (1)')
  await done.locator('.archive-toggle').click()
  await expect(done.getByTestId('kanban-archive-list').getByRole('button', { name: 'Old work' })).toBeVisible()
  await done.getByTestId('kanban-archive-list').getByRole('button', { name: 'Old work' }).click()
  await expect(page.locator('.n-drawer').getByText('Old work', { exact: true })).toBeVisible()
  expect(api.unexpectedRequests).toEqual([])
})
