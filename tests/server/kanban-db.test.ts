import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const mockListProfileNamesFromDisk = vi.hoisted(() => vi.fn(() => ['research', 'ops']))

vi.mock('../../packages/server/src/modules/hermes/services/profiles/profile', () => ({
  listProfileNamesFromDisk: mockListProfileNamesFromDisk,
}))

vi.mock('../../packages/server/src/modules/hermes/services/runtime/path', () => ({
  detectHermesRootHome: () => '/nonexistent/hermes-root',
}))

import * as kanbanDb from '../../packages/server/src/modules/hermes/services/kanban/kanban-db'

// Copied from hermes_cli/kanban_db.py so the adapter is exercised against the
// same table shapes Hermes creates (only the columns the adapter reads matter).
const SCHEMA = `
CREATE TABLE tasks (
  id TEXT PRIMARY KEY, title TEXT NOT NULL, body TEXT, assignee TEXT, status TEXT NOT NULL,
  priority INTEGER DEFAULT 0, created_by TEXT, created_at INTEGER NOT NULL, started_at INTEGER,
  completed_at INTEGER, workspace_kind TEXT NOT NULL DEFAULT 'scratch', workspace_path TEXT,
  branch_name TEXT, project_id TEXT, claim_lock TEXT, claim_expires INTEGER, tenant TEXT, result TEXT,
  idempotency_key TEXT, consecutive_failures INTEGER NOT NULL DEFAULT 0, worker_pid INTEGER,
  last_failure_error TEXT, max_runtime_seconds INTEGER, last_heartbeat_at INTEGER, current_run_id INTEGER,
  workflow_template_id TEXT, current_step_key TEXT, skills TEXT, model_override TEXT, provider_override TEXT,
  session_id TEXT, max_retries INTEGER
);
CREATE TABLE task_links (parent_id TEXT NOT NULL, child_id TEXT NOT NULL, PRIMARY KEY (parent_id, child_id));
CREATE TABLE task_comments (id INTEGER PRIMARY KEY AUTOINCREMENT, task_id TEXT NOT NULL, author TEXT NOT NULL, body TEXT NOT NULL, created_at INTEGER NOT NULL);
CREATE TABLE task_events (id INTEGER PRIMARY KEY AUTOINCREMENT, task_id TEXT NOT NULL, run_id INTEGER, kind TEXT NOT NULL, payload TEXT, created_at INTEGER NOT NULL);
CREATE TABLE task_runs (id INTEGER PRIMARY KEY AUTOINCREMENT, task_id TEXT NOT NULL, profile TEXT, step_key TEXT, status TEXT NOT NULL, claim_lock TEXT, claim_expires INTEGER, worker_pid INTEGER, max_runtime_seconds INTEGER, last_heartbeat_at INTEGER, started_at INTEGER NOT NULL, ended_at INTEGER, outcome TEXT, summary TEXT, metadata TEXT, error TEXT);
CREATE TABLE task_attachments (id INTEGER PRIMARY KEY AUTOINCREMENT, task_id TEXT NOT NULL, filename TEXT NOT NULL, stored_path TEXT NOT NULL, content_type TEXT, size INTEGER NOT NULL DEFAULT 0, uploaded_by TEXT, created_at INTEGER NOT NULL);
`

async function createBoardDb(path: string, seed: (db: any) => void) {
  const { DatabaseSync } = await import('node:sqlite')
  const db = new DatabaseSync(path)
  db.exec('PRAGMA journal_mode = WAL')
  db.exec(SCHEMA)
  seed(db)
  db.close()
}

function insertTask(db: any, task: Record<string, unknown>) {
  const columns = Object.keys(task)
  db.prepare(`INSERT INTO tasks (${columns.join(', ')}) VALUES (${columns.map(() => '?').join(', ')})`).run(...columns.map(column => task[column]))
}

describe('kanban-db (read-only Hermes board adapter)', () => {
  let root: string

  beforeEach(async () => {
    root = mkdtempSync(join(tmpdir(), 'kanban-db-'))
    process.env.HERMES_KANBAN_HOME = root
    delete process.env.HERMES_KANBAN_BOARD
    await createBoardDb(join(root, 'kanban.db'), (db) => {
      insertTask(db, { id: 'task-1', title: 'Older', assignee: 'research', status: 'todo', priority: 1, created_at: 100, skills: '["web","browser"]' })
      insertTask(db, { id: 'task-2', title: 'Urgent', assignee: 'ops', status: 'ready', priority: 3, created_at: 200 })
      insertTask(db, { id: 'task-3', title: 'Hidden', assignee: null, status: 'archived', priority: 0, created_at: 300 })
      insertTask(db, { id: 'task-4', title: 'Tenant', assignee: 'Research', status: 'done', priority: 0, created_at: 400, tenant: 'acme' })
      db.prepare('INSERT INTO task_links (parent_id, child_id) VALUES (?, ?)').run('task-1', 'task-2')
      db.prepare('INSERT INTO task_comments (task_id, author, body, created_at) VALUES (?, ?, ?, ?)').run('task-2', 'han', 'looks good', 210)
      db.prepare('INSERT INTO task_events (task_id, run_id, kind, payload, created_at) VALUES (?, ?, ?, ?, ?)').run('task-2', 7, 'claimed', '{"worker":"ops"}', 205)
      db.prepare('INSERT INTO task_events (task_id, run_id, kind, payload, created_at) VALUES (?, ?, ?, ?, ?)').run('task-2', null, 'note', 'not json', 206)
      db.prepare('INSERT INTO task_runs (task_id, profile, status, started_at, ended_at, outcome, summary, metadata) VALUES (?, ?, ?, ?, ?, ?, ?, ?)').run('task-2', 'ops', 'done', 201, 209, 'completed', 'first pass', '{"tests":3}')
      db.prepare('INSERT INTO task_runs (task_id, profile, status, started_at, ended_at, outcome, summary, metadata) VALUES (?, ?, ?, ?, ?, ?, ?, ?)').run('task-2', 'ops', 'done', 220, 230, 'completed', 'second pass', null)
      db.prepare('INSERT INTO task_attachments (task_id, filename, stored_path, content_type, size, uploaded_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)').run('task-2', 'report.pdf', '/data/report.pdf', 'application/pdf', 42, 'ops', 240)
    })
    mkdirSync(join(root, 'kanban', 'boards', 'project-a'), { recursive: true })
    writeFileSync(join(root, 'kanban', 'boards', 'project-a', 'board.json'), JSON.stringify({ slug: 'ignored', name: 'Project A', icon: '🚀', archived: false }))
    await createBoardDb(join(root, 'kanban', 'boards', 'project-a', 'kanban.db'), (db) => {
      insertTask(db, { id: 'pa-1', title: 'Board task', assignee: 'ops', status: 'running', priority: 0, created_at: 500 })
    })
    mkdirSync(join(root, 'kanban', 'boards', 'old-board'), { recursive: true })
    writeFileSync(join(root, 'kanban', 'boards', 'old-board', 'board.json'), JSON.stringify({ name: 'Old', archived: true }))
    mkdirSync(join(root, 'kanban', 'boards', 'junk dir'), { recursive: true })
    writeFileSync(join(root, 'kanban', 'current'), 'project-a\n')
  })

  afterEach(() => {
    delete process.env.HERMES_KANBAN_HOME
    rmSync(root, { recursive: true, force: true })
  })

  it('resolves board paths the way Hermes does', () => {
    expect(kanbanDb.kanbanDbPath()).toBe(join(root, 'kanban.db'))
    expect(kanbanDb.kanbanDbPath('Project-A')).toBe(join(root, 'kanban', 'boards', 'project-a', 'kanban.db'))
    expect(() => kanbanDb.kanbanDbPath('bad slug')).toThrow('Invalid kanban board slug')
  })

  it('lists boards with metadata, counts, archived filtering and the CLI current marker', async () => {
    const boards = await kanbanDb.listBoards()
    expect(boards.map(board => board.slug)).toEqual(['default', 'project-a'])
    expect(boards[0]).toMatchObject({ name: 'Default', is_current: false, counts: { todo: 1, ready: 1, archived: 1, done: 1 }, total: 4, db_path: join(root, 'kanban.db') })
    expect(boards[1]).toMatchObject({ name: 'Project A', icon: '🚀', is_current: true, counts: { running: 1 }, total: 1 })

    const all = await kanbanDb.listBoards({ includeArchived: true })
    // Hermes lists default first, then the rest alphabetically.
    expect(all.map(board => board.slug)).toEqual(['default', 'old-board', 'project-a'])
    expect(all[1]).toMatchObject({ archived: true, counts: {}, total: 0 })
  })

  it('lists tasks in Hermes order with the same filters and archived rules', async () => {
    const tasks = await kanbanDb.listTasks('default')
    expect(tasks.map(task => task.id)).toEqual(['task-2', 'task-1', 'task-4'])
    expect(tasks[1]).toMatchObject({ id: 'task-1', skills: ['web', 'browser'], priority: 1, assignee: 'research', workspace_kind: 'scratch', body: null })
    expect(tasks[0].skills).toEqual([])

    expect((await kanbanDb.listTasks('default', { includeArchived: true })).map(task => task.id)).toEqual(['task-2', 'task-1', 'task-3', 'task-4'])
    expect((await kanbanDb.listTasks('default', { status: 'archived' })).map(task => task.id)).toEqual(['task-3'])
    expect((await kanbanDb.listTasks('default', { assignee: 'OPS' })).map(task => task.id)).toEqual(['task-2'])
    expect((await kanbanDb.listTasks('default', { tenant: 'acme' })).map(task => task.id)).toEqual(['task-4'])
    expect((await kanbanDb.listTasks('project-a')).map(task => task.id)).toEqual(['pa-1'])
  })

  it('returns task detail in the CLI show --json shape, parsing JSON columns defensively', async () => {
    const detail = await kanbanDb.getTask('default', 'task-2')
    expect(detail).not.toBeNull()
    expect(detail!.task).toMatchObject({ id: 'task-2', status: 'ready' })
    expect(detail!.latest_summary).toBe('second pass')
    expect(detail!.parents).toEqual(['task-1'])
    expect(detail!.children).toEqual([])
    expect(detail!.comments).toEqual([{ id: 1, task_id: 'task-2', author: 'han', body: 'looks good', created_at: 210 }])
    expect(detail!.events).toEqual([
      { id: 1, task_id: 'task-2', kind: 'claimed', payload: { worker: 'ops' }, created_at: 205, run_id: 7 },
      { id: 2, task_id: 'task-2', kind: 'note', payload: 'not json', created_at: 206, run_id: null },
    ])
    expect(detail!.runs.map(run => [run.id, run.summary, run.metadata])).toEqual([[1, 'first pass', { tests: 3 }], [2, 'second pass', null]])
    expect(await kanbanDb.getTask('default', 'missing')).toBeNull()
  })

  it('computes stats and assignees without shelling out', async () => {
    expect(await kanbanDb.getStats('default')).toEqual({
      by_status: { todo: 1, ready: 1, archived: 1, done: 1 },
      by_assignee: { research: 1, ops: 1, default: 1, Research: 1 },
      total: 4,
    })
    expect(await kanbanDb.listAssignees('default')).toEqual([
      { name: 'Research', on_disk: false, counts: { done: 1 } },
      { name: 'ops', on_disk: true, counts: { ready: 1 } },
      { name: 'research', on_disk: true, counts: { todo: 1 } },
    ])
  })

  it('lists attachments and distinguishes a missing task from an empty list', async () => {
    expect(await kanbanDb.listAttachments('default', 'task-2')).toEqual([
      { id: 1, filename: 'report.pdf', content_type: 'application/pdf', size: 42, uploaded_by: 'ops', stored_path: '/data/report.pdf', created_at: 240 },
    ])
    expect(await kanbanDb.listAttachments('default', 'task-1')).toEqual([])
    expect(await kanbanDb.listAttachments('default', 'missing')).toBeNull()
  })

  it('throws a typed error when the board database does not exist so callers can fall back', async () => {
    await expect(kanbanDb.listTasks('nope')).rejects.toBeInstanceOf(kanbanDb.KanbanDbUnavailableError)
    process.env.HERMES_KANBAN_HOME = join(root, 'empty')
    await expect(kanbanDb.getStats('default')).rejects.toBeInstanceOf(kanbanDb.KanbanDbUnavailableError)
    expect((await kanbanDb.listBoards()).map(board => board.slug)).toEqual(['default'])
  })
})
