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

async function scrollColumnToStart(page: Page, status: string) {
  await page.evaluate((columnStatus) => {
    const board = document.querySelector('[data-testid="kanban-board"]') as HTMLElement
    const column = document.querySelector(`.kanban-column[data-status="${columnStatus}"]`) as HTMLElement
    board.scrollLeft += column.getBoundingClientRect().left - board.getBoundingClientRect().left - 20
  }, status)
}

async function dragCardToColumn(page: Page, taskId: string, targetStatus: string) {
  const card = page.locator(`.task-slot[data-task-id="${taskId}"]`)
  const sourceStatus = await card.evaluate(element => element.closest('.kanban-column')!.getAttribute('data-status'))
  // Keep both the source card and the target column inside the viewport so the
  // pointer path never leaves the board.
  await scrollColumnToStart(page, sourceStatus!)
  const target = page.locator(`.task-list[data-status="${targetStatus}"]`)
  const cardBox = (await card.boundingBox())!
  const targetBox = (await target.boundingBox())!
  const startX = cardBox.x + cardBox.width / 2
  const startY = cardBox.y + Math.min(24, cardBox.height / 2)
  await page.mouse.move(startX, startY)
  await page.mouse.down()
  // Sortable's fallback needs to pass its tolerance before the drag begins.
  await page.mouse.move(startX + 12, startY + 12, { steps: 4 })
  await page.mouse.move(targetBox.x + targetBox.width / 2, targetBox.y + Math.min(60, targetBox.height / 2), { steps: 16 })
  await page.mouse.up()
}

test('scrolls the board sideways, keeps columns scrollable, and opens cards', async ({ page }) => {
  await authenticate(page, TEST_ACCESS_KEY, 'research')
  const api = await mockHermesApi(page)
  const tasks = Array.from({ length: 12 }, (_, index) => makeTask(index + 1, 'todo'))
  await mockKanbanBoard(page, tasks)

  await page.goto('/#/hermes/kanban')

  const board = page.getByTestId('kanban-board')
  await expect(board).toBeVisible()
  await expect(page.locator('.kanban-column')).toHaveCount(9)
  expect(await page.locator('.kanban-column').evaluateAll(columns => columns.map(column => column.getAttribute('data-status')))).toEqual([
    'triage', 'todo', 'scheduled', 'ready', 'running', 'blocked', 'review', 'done', 'archived',
  ])
  const boardMetrics = await board.evaluate(element => ({ scrollWidth: element.scrollWidth, clientWidth: element.clientWidth }))
  expect(boardMetrics.scrollWidth).toBeGreaterThan(boardMetrics.clientWidth)

  const taskList = page.locator('.status-todo .task-list')
  const dimensions = await taskList.evaluate(element => ({ clientHeight: element.clientHeight, scrollHeight: element.scrollHeight }))
  expect(dimensions.scrollHeight).toBeGreaterThan(dimensions.clientHeight)

  await page.locator('.kanban-column.status-archived').scrollIntoViewIfNeeded()
  await expect.poll(() => board.evaluate(element => Math.abs(element.scrollLeft))).toBeGreaterThan(0)
  await page.locator('.kanban-column.status-triage').scrollIntoViewIfNeeded()

  await page.getByRole('button', { name: 'Board task 12' }).click()
  await expect(page.locator('.n-drawer').getByText('Board task 12', { exact: true })).toBeVisible()
  expect(api.unexpectedRequests).toEqual([])
})

test('moves cards between columns through the Hermes transition bridge', async ({ page }) => {
  await page.setViewportSize({ width: 1600, height: 900 })
  await authenticate(page, TEST_ACCESS_KEY, 'research')
  const api = await mockHermesApi(page)
  const tasks = [makeTask(1, 'todo', 'Promote me'), makeTask(2, 'ready', 'Block me')]
  const { transitions } = await mockKanbanBoard(page, tasks)

  await page.goto('/#/hermes/kanban')
  await expect(page.locator('.task-slot[data-task-id="task-1"]')).toBeVisible()

  // todo -> running is not a manual Hermes transition, so the drop is refused.
  await dragCardToColumn(page, 'task-1', 'running')
  await expect(page.locator('.task-list[data-status="todo"] .task-slot[data-task-id="task-1"]')).toBeVisible()
  expect(transitions).toEqual([])

  // todo -> ready promotes through the CLI bridge.
  await dragCardToColumn(page, 'task-1', 'ready')
  await expect.poll(() => transitions.map(call => call.path)).toEqual(['/api/hermes/kanban/task-1/promote'])
  await expect(page.locator('.task-list[data-status="ready"] .task-slot[data-task-id="task-1"]')).toBeVisible()
  await expect(page.locator('.task-list[data-status="todo"] .task-slot')).toHaveCount(0)

  // ready -> blocked needs a reason before the CLI runs.
  await dragCardToColumn(page, 'task-2', 'blocked')
  const reason = page.getByTestId('kanban-drop-reason').locator('input')
  await expect(reason).toBeVisible()
  expect(transitions).toHaveLength(1)
  await reason.fill('waiting for credentials')
  await page.getByRole('button', { name: 'OK' }).click()
  await expect.poll(() => transitions.map(call => call.path)).toEqual([
    '/api/hermes/kanban/task-1/promote',
    '/api/hermes/kanban/task-2/block',
  ])
  expect(transitions[1].body).toEqual({ reason: 'waiting for credentials' })
  await expect(page.locator('.task-list[data-status="blocked"] .task-slot[data-task-id="task-2"]')).toBeVisible()
  expect(api.unexpectedRequests).toEqual([])
})

test('keeps manual column order in the browser only', async ({ page }) => {
  await authenticate(page, TEST_ACCESS_KEY, 'research')
  const api = await mockHermesApi(page)
  const { transitions } = await mockKanbanBoard(page, [makeTask(1, 'todo')])

  await page.goto('/#/hermes/kanban')
  const todoHeader = page.locator('.kanban-column.status-todo .column-header')
  const triageHeader = page.locator('.kanban-column.status-triage .column-header')
  await expect(todoHeader).toBeVisible()
  const from = (await todoHeader.boundingBox())!
  const to = (await triageHeader.boundingBox())!
  await page.mouse.move(from.x + from.width / 2, from.y + from.height / 2)
  await page.mouse.down()
  await page.mouse.move(from.x + from.width / 2 - 12, from.y + from.height / 2, { steps: 4 })
  await page.mouse.move(to.x + 12, to.y + to.height / 2, { steps: 16 })
  await page.mouse.up()

  await expect.poll(() => page.locator('.kanban-column').evaluateAll(columns => columns.slice(0, 2).map(column => column.getAttribute('data-status')))).toEqual(['todo', 'triage'])
  await expect(page.getByRole('button', { name: 'Reset layout' })).toBeVisible()
  const stored = await page.evaluate(() => window.localStorage.getItem('hermes.kanban.layout.default'))
  expect(JSON.parse(stored || '{}').columns.slice(0, 2)).toEqual(['todo', 'triage'])
  expect(transitions).toEqual([])

  await page.reload()
  await expect.poll(() => page.locator('.kanban-column').evaluateAll(columns => columns.slice(0, 2).map(column => column.getAttribute('data-status')))).toEqual(['todo', 'triage'])

  await page.getByRole('button', { name: 'Reset layout' }).click()
  await expect.poll(() => page.locator('.kanban-column').evaluateAll(columns => columns.slice(0, 2).map(column => column.getAttribute('data-status')))).toEqual(['triage', 'todo'])
  expect(await page.evaluate(() => window.localStorage.getItem('hermes.kanban.layout.default'))).toBeNull()
  expect(api.unexpectedRequests).toEqual([])
})
