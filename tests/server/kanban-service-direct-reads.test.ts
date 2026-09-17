import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const mockExecFileAsync = vi.hoisted(() => vi.fn())
const mockSpawnHermes = vi.hoisted(() => vi.fn())
const mockLoggerError = vi.hoisted(() => vi.fn())
const mockLoggerWarn = vi.hoisted(() => vi.fn())
const dbState = vi.hoisted(() => ({
  available: true,
  tasks: [{ id: 'task-1', title: 'Direct', status: 'ready', assignee: null, priority: 0, created_at: 1 }],
  listCalls: 0,
}))

vi.mock('../../packages/server/src/modules/hermes/services/runtime/process', () => ({
  execHermes: (args: string[], options: unknown) => mockExecFileAsync('hermes', args, options),
  spawnHermes: mockSpawnHermes,
}))

vi.mock('../../packages/server/src/modules/studio/public/logging', () => ({
  logger: { error: mockLoggerError, warn: mockLoggerWarn },
}))

vi.mock('../../packages/server/src/modules/hermes/services/kanban/kanban-db', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../packages/server/src/modules/hermes/services/kanban/kanban-db')>()
  const unavailable = () => new actual.KanbanDbUnavailableError('Kanban database not found: /tmp/none')
  return {
    ...actual,
    listTasks: vi.fn(async () => {
      dbState.listCalls += 1
      if (!dbState.available) throw unavailable()
      return dbState.tasks
    }),
    getStats: vi.fn(async () => {
      if (!dbState.available) throw unavailable()
      return { by_status: { ready: 1 }, by_assignee: { default: 1 }, total: 1 }
    }),
    getTask: vi.fn(async (_board: string, taskId: string) => {
      if (!dbState.available) throw unavailable()
      return taskId === 'task-1'
        ? { task: dbState.tasks[0], latest_summary: null, parents: [], children: [], comments: [{ id: 3, task_id: 'task-1', author: 'han', body: 'hi', created_at: 2 }], events: [], runs: [] }
        : null
    }),
    listBoards: vi.fn(async () => {
      if (!dbState.available) throw unavailable()
      return [{ slug: 'default', name: 'Default', counts: { ready: 1 }, total: 1, is_current: true, archived: false }]
    }),
    listAssignees: vi.fn(async () => {
      if (!dbState.available) throw unavailable()
      return [{ name: 'research', on_disk: true, counts: {} }]
    }),
    listAttachments: vi.fn(async (_board: string, taskId: string) => {
      if (!dbState.available) throw unavailable()
      return taskId === 'task-1' ? [] : null
    }),
  }
})

import * as service from '../../packages/server/src/modules/hermes/services/kanban/kanban-service'
import { resetKanbanReadCache } from '../../packages/server/src/modules/hermes/services/kanban/kanban-read-cache'

describe('kanban service direct reads', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    resetKanbanReadCache()
    dbState.available = true
    dbState.listCalls = 0
    delete process.env.HERMES_WEB_UI_KANBAN_DIRECT_READS
  })

  afterEach(() => {
    delete process.env.HERMES_WEB_UI_KANBAN_DIRECT_READS
  })

  it('serves reads from the board database without spawning the CLI', async () => {
    await expect(service.listTasks({ board: 'project-a' })).resolves.toEqual(dbState.tasks)
    await expect(service.getStats({ board: 'project-a' })).resolves.toEqual({ by_status: { ready: 1 }, by_assignee: { default: 1 }, total: 1 })
    await expect(service.listBoards()).resolves.toMatchObject([{ slug: 'default' }])
    await expect(service.getAssignees({ board: 'project-a' })).resolves.toEqual([{ name: 'research', on_disk: true, counts: {} }])
    await expect(service.listAttachments('task-1', { board: 'project-a' })).resolves.toEqual([])
    const detail = await service.getTask('task-1', { board: 'project-a' })
    expect(detail?.comments).toEqual([{ id: 3, task_id: 'task-1', author: 'han', body: 'hi', created_at: 2 }])
    await expect(service.getTask('missing', { board: 'project-a' })).resolves.toBeNull()
    expect(mockExecFileAsync).not.toHaveBeenCalled()
  })

  it('coalesces identical concurrent reads and caches them briefly per board', async () => {
    const [a, b] = await Promise.all([service.listTasks({ board: 'project-a' }), service.listTasks({ board: 'project-a' })])
    expect(a).toBe(b)
    await service.listTasks({ board: 'project-a' })
    expect(dbState.listCalls).toBe(1)

    await service.listTasks({ board: 'project-a', status: 'done' })
    await service.listTasks({ board: 'other' })
    expect(dbState.listCalls).toBe(3)
  })

  it('drops cached reads for a board after a mutation and after a Hermes event', async () => {
    mockExecFileAsync.mockResolvedValue({ stdout: '' })
    await service.listTasks({ board: 'project-a' })
    await service.listTasks({ board: 'other' })
    expect(dbState.listCalls).toBe(2)

    await service.blockTask('task-1', 'waiting', { board: 'project-a' })
    await service.listTasks({ board: 'project-a' })
    await service.listTasks({ board: 'other' })
    expect(dbState.listCalls).toBe(3)

    service.invalidateBoardReads('other')
    await service.listTasks({ board: 'other' })
    expect(dbState.listCalls).toBe(4)
  })

  it('falls back to the Hermes CLI when the database is unavailable, warning once', async () => {
    dbState.available = false
    mockExecFileAsync
      .mockResolvedValueOnce({ stdout: JSON.stringify([{ id: 'cli-1' }]) })
      .mockResolvedValueOnce({ stdout: JSON.stringify({ by_status: { ready: 1 }, by_assignee: {} }) })
      .mockResolvedValueOnce({ stdout: JSON.stringify([]) })

    await expect(service.listTasks({ board: 'project-a' })).resolves.toEqual([{ id: 'cli-1' }])
    await expect(service.getStats({ board: 'project-a' })).resolves.toEqual({ by_status: { ready: 1, archived: 0 }, by_assignee: {}, total: 1 })
    expect(mockExecFileAsync.mock.calls[0][1]).toEqual(['kanban', '--board', 'project-a', 'list', '--json'])
    expect(mockExecFileAsync.mock.calls[1][1]).toEqual(['kanban', '--board', 'project-a', 'stats', '--json'])
    expect(mockLoggerWarn).toHaveBeenCalledTimes(2)

    resetKanbanReadCache()
    mockExecFileAsync.mockResolvedValueOnce({ stdout: JSON.stringify([{ id: 'cli-2' }]) })
    await expect(service.listTasks({ board: 'project-a' })).resolves.toEqual([{ id: 'cli-2' }])
    expect(mockLoggerWarn).toHaveBeenCalledTimes(2)
  })

  it('honours the environment switch that forces the CLI path', async () => {
    process.env.HERMES_WEB_UI_KANBAN_DIRECT_READS = '0'
    mockExecFileAsync.mockResolvedValueOnce({ stdout: JSON.stringify([{ id: 'cli-only' }]) })
    await expect(service.listTasks({ board: 'project-a' })).resolves.toEqual([{ id: 'cli-only' }])
    expect(dbState.listCalls).toBe(0)
    expect(mockLoggerWarn).not.toHaveBeenCalled()
  })
})
