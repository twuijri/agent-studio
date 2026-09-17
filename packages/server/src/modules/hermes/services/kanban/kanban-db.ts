/**
 * Read-only adapter for the Hermes Kanban SQLite databases.
 *
 * Every board is a small SQLite file that Hermes owns and writes through its
 * CLI. Reading it directly (read-only, WAL) answers list/stats/detail queries
 * in milliseconds instead of spawning a Python process per request. Nothing in
 * this module writes; mutations stay on the CLI so Hermes keeps all guards.
 *
 * Paths mirror `hermes_cli/kanban_db.py`: the default board lives at
 * `<root>/kanban.db`, other boards at `<root>/kanban/boards/<slug>/kanban.db`,
 * metadata in `board.json`, and the CLI's current board in `<root>/kanban/current`.
 * Callers must fall back to the CLI when this adapter throws (schema drift,
 * missing files, unsupported Node).
 */
import { existsSync, readFileSync, readdirSync, statSync } from 'fs'
import { join, resolve } from 'path'
import { detectHermesRootHome } from '../runtime/path'
import { listProfileNamesFromDisk } from '../profiles/profile'

export const DEFAULT_BOARD = 'default'
const BOARD_SLUG_RE = /^[a-z0-9][a-z0-9_-]{0,63}$/
const NODE_SQLITE_AVAILABLE = (() => {
  const [major, minor] = process.versions.node.split('.').map(Number)
  return major > 22 || (major === 22 && minor >= 5)
})()

export class KanbanDbUnavailableError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'KanbanDbUnavailableError'
  }
}

export interface KanbanDbTaskRow {
  id: string
  title: string
  body: string | null
  assignee: string | null
  status: string
  priority: number
  tenant: string | null
  workspace_kind: string
  workspace_path: string | null
  branch_name: string | null
  project_id: string | null
  created_by: string | null
  created_at: number
  started_at: number | null
  completed_at: number | null
  result: string | null
  skills: string[]
  max_retries: number | null
  model_override: string | null
  provider_override: string | null
  session_id: string | null
  workflow_template_id: string | null
  current_step_key: string | null
}

export interface KanbanDbBoardMeta {
  slug: string
  name: string
  description: string
  icon: string
  color: string
  default_workdir: string | null
  project_id: string | null
  created_at: number | null
  archived: boolean
  db_path: string
  is_current: boolean
  counts: Record<string, number>
  total: number
}

export interface KanbanDbTaskDetail {
  task: KanbanDbTaskRow
  latest_summary: string | null
  parents: string[]
  children: string[]
  comments: Array<{ id: number; task_id: string; author: string; body: string; created_at: number }>
  events: Array<{ id: number; task_id: string; kind: string; payload: unknown; created_at: number; run_id: number | null }>
  runs: Array<{
    id: number
    task_id: string
    profile: string | null
    step_key: string | null
    status: string
    outcome: string | null
    summary: string | null
    error: string | null
    metadata: unknown
    worker_pid: number | null
    started_at: number
    ended_at: number | null
  }>
}

export interface KanbanDbAttachment {
  id: number
  filename: string
  content_type: string | null
  size: number
  uploaded_by: string | null
  stored_path: string
  created_at: number
}

export interface KanbanDbListOptions {
  status?: string
  assignee?: string
  tenant?: string
  includeArchived?: boolean
}

// ─── Paths ────────────────────────────────────────────────────────

export function kanbanHome(): string {
  const override = process.env.HERMES_KANBAN_HOME?.trim()
  return resolve(override || detectHermesRootHome())
}

function normalizeSlug(board?: string | null): string {
  const slug = (board ?? '').trim().toLowerCase() || DEFAULT_BOARD
  if (!BOARD_SLUG_RE.test(slug)) throw new Error('Invalid kanban board slug')
  return slug
}

export function boardDir(board?: string | null): string {
  const slug = normalizeSlug(board)
  return join(kanbanHome(), 'kanban', 'boards', slug)
}

export function kanbanDbPath(board?: string | null): string {
  const slug = normalizeSlug(board)
  // The default board keeps the pre-boards location for back-compat.
  if (slug === DEFAULT_BOARD) return join(kanbanHome(), 'kanban.db')
  return join(boardDir(slug), 'kanban.db')
}

function defaultDisplayName(slug: string): string {
  return slug.replace(/_/g, '-').split('-').filter(Boolean).map(part => part[0].toUpperCase() + part.slice(1)).join(' ') || slug
}

function readBoardMetadata(slug: string): Omit<KanbanDbBoardMeta, 'is_current' | 'counts' | 'total'> {
  const meta: Omit<KanbanDbBoardMeta, 'is_current' | 'counts' | 'total'> = {
    slug,
    name: defaultDisplayName(slug),
    description: '',
    icon: '',
    color: '',
    default_workdir: null,
    project_id: null,
    created_at: null,
    archived: false,
    db_path: kanbanDbPath(slug),
  }
  try {
    const path = join(boardDir(slug), 'board.json')
    if (existsSync(path)) {
      const raw = JSON.parse(readFileSync(path, 'utf8'))
      if (raw && typeof raw === 'object' && !Array.isArray(raw)) {
        Object.assign(meta, raw)
        meta.slug = slug
        meta.db_path = kanbanDbPath(slug)
      }
    }
  } catch {
    // Malformed metadata falls back to the synthesised entry, like Hermes.
  }
  meta.archived = Boolean(meta.archived)
  return meta
}

function currentBoard(existing: Set<string>): string {
  const env = process.env.HERMES_KANBAN_BOARD?.trim().toLowerCase()
  if (env && existing.has(env)) return env
  try {
    const path = join(kanbanHome(), 'kanban', 'current')
    if (existsSync(path)) {
      const slug = readFileSync(path, 'utf8').trim().toLowerCase()
      if (slug && existing.has(slug)) return slug
    }
  } catch {
    // Unreadable marker means the default board.
  }
  return DEFAULT_BOARD
}

// ─── Connection helpers ──────────────────────────────────────────

type SqliteDb = {
  prepare(sql: string): { all(...params: unknown[]): unknown[]; get(...params: unknown[]): unknown }
  close(): void
}

async function openReadOnly(dbPath: string): Promise<SqliteDb> {
  if (!NODE_SQLITE_AVAILABLE) {
    throw new KanbanDbUnavailableError(`node:sqlite requires Node >= 22.5, current: ${process.versions.node}`)
  }
  if (!existsSync(dbPath)) {
    throw new KanbanDbUnavailableError(`Kanban database not found: ${dbPath}`)
  }
  const { DatabaseSync } = await import('node:sqlite')
  const db = new DatabaseSync(dbPath, { open: true, readOnly: true }) as unknown as SqliteDb
  try {
    // Hermes opens the board in WAL mode; readers must not block its writers.
    db.prepare('PRAGMA busy_timeout = 2000').get()
  } catch {
    // busy_timeout is advisory; keep going.
  }
  return db
}

async function withDb<T>(board: string | null | undefined, run: (db: SqliteDb) => T): Promise<T> {
  const db = await openReadOnly(kanbanDbPath(board))
  try {
    return run(db)
  } finally {
    db.close()
  }
}

function parseJson(value: unknown): unknown {
  if (typeof value !== 'string' || !value) return value ?? null
  try {
    return JSON.parse(value)
  } catch {
    return value
  }
}

function toNumberOrNull(value: unknown): number | null {
  if (value === null || value === undefined) return null
  const n = Number(value)
  return Number.isFinite(n) ? n : null
}

function toStringOrNull(value: unknown): string | null {
  return value === null || value === undefined ? null : String(value)
}

function rowToTask(row: Record<string, unknown>): KanbanDbTaskRow {
  const skills = parseJson(row.skills)
  return {
    id: String(row.id),
    title: String(row.title ?? ''),
    body: toStringOrNull(row.body),
    assignee: toStringOrNull(row.assignee),
    status: String(row.status),
    priority: toNumberOrNull(row.priority) ?? 0,
    tenant: toStringOrNull(row.tenant),
    workspace_kind: String(row.workspace_kind ?? 'scratch'),
    workspace_path: toStringOrNull(row.workspace_path),
    branch_name: toStringOrNull(row.branch_name),
    project_id: toStringOrNull(row.project_id),
    created_by: toStringOrNull(row.created_by),
    created_at: toNumberOrNull(row.created_at) ?? 0,
    started_at: toNumberOrNull(row.started_at),
    completed_at: toNumberOrNull(row.completed_at),
    result: toStringOrNull(row.result),
    skills: Array.isArray(skills) ? skills.map(String) : [],
    max_retries: toNumberOrNull(row.max_retries),
    model_override: toStringOrNull(row.model_override),
    provider_override: toStringOrNull(row.provider_override),
    session_id: toStringOrNull(row.session_id),
    workflow_template_id: toStringOrNull(row.workflow_template_id),
    current_step_key: toStringOrNull(row.current_step_key),
  }
}

function countsByStatus(db: SqliteDb, excludeArchived: boolean): Record<string, number> {
  const rows = db.prepare(
    `SELECT status, COUNT(*) AS n FROM tasks ${excludeArchived ? "WHERE status != 'archived' " : ''}GROUP BY status`,
  ).all() as Array<{ status: string; n: number }>
  const counts: Record<string, number> = {}
  for (const row of rows) counts[String(row.status)] = Number(row.n)
  return counts
}

function countsByAssigneeAndStatus(db: SqliteDb): Record<string, Record<string, number>> {
  const rows = db.prepare(
    "SELECT assignee, status, COUNT(*) AS n FROM tasks WHERE status != 'archived' AND assignee IS NOT NULL GROUP BY assignee, status",
  ).all() as Array<{ assignee: string; status: string; n: number }>
  const counts: Record<string, Record<string, number>> = {}
  for (const row of rows) {
    const key = String(row.assignee)
    counts[key] = counts[key] || {}
    counts[key][String(row.status)] = Number(row.n)
  }
  return counts
}

// ─── Public reads (mirror the Hermes CLI `--json` shapes) ────────

export async function listBoards(opts?: { includeArchived?: boolean }): Promise<KanbanDbBoardMeta[]> {
  const entries: Array<Omit<KanbanDbBoardMeta, 'is_current' | 'counts' | 'total'>> = [readBoardMetadata(DEFAULT_BOARD)]
  const seen = new Set<string>([DEFAULT_BOARD])
  const root = join(kanbanHome(), 'kanban', 'boards')
  if (existsSync(root) && statSync(root).isDirectory()) {
    const children = readdirSync(root, { withFileTypes: true })
      .filter(entry => entry.isDirectory())
      .map(entry => entry.name)
      .sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase()))
    for (const name of children) {
      const slug = name.trim().toLowerCase()
      if (!BOARD_SLUG_RE.test(slug) || seen.has(slug)) continue
      const dir = join(root, name)
      if (!existsSync(join(dir, 'kanban.db')) && !existsSync(join(dir, 'board.json'))) continue
      const meta = readBoardMetadata(slug)
      if (meta.archived && !opts?.includeArchived) continue
      entries.push(meta)
      seen.add(slug)
    }
  }
  const current = currentBoard(seen)
  const boards: KanbanDbBoardMeta[] = []
  for (const meta of entries) {
    let counts: Record<string, number> = {}
    if (existsSync(meta.db_path)) {
      try {
        counts = await withDb(meta.slug, db => countsByStatus(db, false))
      } catch (err) {
        if (err instanceof KanbanDbUnavailableError) throw err
        counts = {}
      }
    }
    boards.push({
      ...meta,
      is_current: meta.slug === current,
      counts,
      total: Object.values(counts).reduce((total, n) => total + n, 0),
    })
  }
  return boards
}

export async function listTasks(board: string | null | undefined, opts: KanbanDbListOptions = {}): Promise<KanbanDbTaskRow[]> {
  return withDb(board, (db) => {
    let sql = 'SELECT * FROM tasks WHERE 1=1'
    const params: unknown[] = []
    if (opts.assignee !== undefined) {
      sql += ' AND assignee = ?'
      params.push(opts.assignee.trim().toLowerCase())
    }
    if (opts.status !== undefined) {
      sql += ' AND status = ?'
      params.push(opts.status)
    }
    if (opts.tenant !== undefined) {
      sql += ' AND tenant = ?'
      params.push(opts.tenant)
    }
    if (!opts.includeArchived && opts.status !== 'archived') sql += " AND status != 'archived'"
    sql += ' ORDER BY priority DESC, created_at ASC, id ASC'
    return (db.prepare(sql).all(...params) as Record<string, unknown>[]).map(rowToTask)
  })
}

export async function getTask(board: string | null | undefined, taskId: string): Promise<KanbanDbTaskDetail | null> {
  return withDb(board, (db) => {
    const row = db.prepare('SELECT * FROM tasks WHERE id = ?').get(taskId) as Record<string, unknown> | undefined
    if (!row) return null
    const task = rowToTask(row)
    const comments = (db.prepare('SELECT id, task_id, author, body, created_at FROM task_comments WHERE task_id = ? ORDER BY created_at ASC, id ASC').all(taskId) as Record<string, unknown>[])
      .map(comment => ({
        id: Number(comment.id),
        task_id: String(comment.task_id),
        author: String(comment.author ?? ''),
        body: String(comment.body ?? ''),
        created_at: toNumberOrNull(comment.created_at) ?? 0,
      }))
    const events = (db.prepare('SELECT id, task_id, run_id, kind, payload, created_at FROM task_events WHERE task_id = ? ORDER BY created_at ASC, id ASC').all(taskId) as Record<string, unknown>[])
      .map(event => ({
        id: Number(event.id),
        task_id: String(event.task_id),
        kind: String(event.kind ?? ''),
        payload: parseJson(event.payload),
        created_at: toNumberOrNull(event.created_at) ?? 0,
        run_id: toNumberOrNull(event.run_id),
      }))
    const runs = (db.prepare('SELECT * FROM task_runs WHERE task_id = ? ORDER BY started_at ASC, id ASC').all(taskId) as Record<string, unknown>[])
      .map(run => ({
        id: Number(run.id),
        task_id: String(run.task_id),
        profile: toStringOrNull(run.profile),
        step_key: toStringOrNull(run.step_key),
        status: String(run.status ?? ''),
        outcome: toStringOrNull(run.outcome),
        summary: toStringOrNull(run.summary),
        error: toStringOrNull(run.error),
        metadata: parseJson(run.metadata),
        worker_pid: toNumberOrNull(run.worker_pid),
        started_at: toNumberOrNull(run.started_at) ?? 0,
        ended_at: toNumberOrNull(run.ended_at),
      }))
    const parents = (db.prepare('SELECT parent_id FROM task_links WHERE child_id = ? ORDER BY parent_id').all(taskId) as Array<{ parent_id: string }>).map(link => String(link.parent_id))
    const children = (db.prepare('SELECT child_id FROM task_links WHERE parent_id = ? ORDER BY child_id').all(taskId) as Array<{ child_id: string }>).map(link => String(link.child_id))
    const summaryRow = db.prepare(
      "SELECT summary FROM task_runs WHERE task_id = ? AND summary IS NOT NULL AND summary != '' ORDER BY COALESCE(ended_at, started_at) DESC, id DESC LIMIT 1",
    ).get(taskId) as { summary?: string } | undefined
    return {
      task,
      latest_summary: summaryRow?.summary ?? null,
      parents,
      children,
      comments,
      events,
      runs,
    }
  })
}

export interface KanbanDbStats {
  by_status: Record<string, number>
  by_assignee: Record<string, number>
  total: number
}

/** Matches the Web UI's stats contract: archived counted, assignee totals flattened. */
export async function getStats(board: string | null | undefined): Promise<KanbanDbStats> {
  return withDb(board, (db) => {
    const byStatus = countsByStatus(db, false)
    const rows = db.prepare('SELECT assignee, COUNT(*) AS n FROM tasks GROUP BY assignee').all() as Array<{ assignee: string | null; n: number }>
    const byAssignee: Record<string, number> = {}
    for (const row of rows) {
      const name = (row.assignee ?? '').trim() || DEFAULT_BOARD
      byAssignee[name] = (byAssignee[name] || 0) + Number(row.n)
    }
    return {
      by_status: byStatus,
      by_assignee: byAssignee,
      total: Object.values(byStatus).reduce((total, n) => total + n, 0),
    }
  })
}

export interface KanbanDbAssignee {
  name: string
  on_disk: boolean
  counts: Record<string, number>
}

export async function listAssignees(board: string | null | undefined): Promise<KanbanDbAssignee[]> {
  const onDisk = new Set(listProfileNamesFromDisk())
  const counts = await withDb(board, countsByAssigneeAndStatus)
  const names = [...new Set([...onDisk, ...Object.keys(counts)])].sort()
  return names.map(name => ({ name, on_disk: onDisk.has(name), counts: counts[name] || {} }))
}

export async function listAttachments(board: string | null | undefined, taskId: string): Promise<KanbanDbAttachment[] | null> {
  return withDb(board, (db) => {
    const exists = db.prepare('SELECT 1 FROM tasks WHERE id = ?').get(taskId)
    if (!exists) return null
    return (db.prepare('SELECT * FROM task_attachments WHERE task_id = ? ORDER BY created_at ASC, id ASC').all(taskId) as Record<string, unknown>[])
      .map(row => ({
        id: Number(row.id),
        filename: String(row.filename ?? ''),
        content_type: toStringOrNull(row.content_type),
        size: toNumberOrNull(row.size) ?? 0,
        uploaded_by: toStringOrNull(row.uploaded_by),
        stored_path: String(row.stored_path ?? ''),
        created_at: toNumberOrNull(row.created_at) ?? 0,
      }))
  })
}
