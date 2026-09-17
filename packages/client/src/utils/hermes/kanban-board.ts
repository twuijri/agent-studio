import type { KanbanTask, KanbanTaskStatus } from '@/api/hermes/kanban'

export const KANBAN_BOARD_STATUSES: readonly KanbanTaskStatus[] = [
  'triage',
  'todo',
  'scheduled',
  'ready',
  'running',
  'blocked',
  'review',
  'done',
  'archived',
]

export type KanbanTransitionAction =
  | 'complete'
  | 'block'
  | 'unblock'
  | 'promote'
  | 'schedule'
  | 'requestReview'
  | 'reopenReview'
  | 'archive'

export interface KanbanTransition {
  action: KanbanTransitionAction
  /** The Hermes CLI requires a reason for this transition, so the UI must ask before running it. */
  requiresReason: boolean
  /** Terminal transition; confirm before running it. */
  confirm: boolean
}

function transition(action: KanbanTransitionAction, opts: Partial<Omit<KanbanTransition, 'action'>> = {}): KanbanTransition {
  return { action, requiresReason: false, confirm: false, ...opts }
}

// Mirrors the source-status guards in the server controller so a drop is only
// offered when Hermes can actually apply it. `triage` and `running` are owned by
// the intake flow and the worker, so nothing can be dropped there.
const DROP_RULES: Partial<Record<KanbanTaskStatus, Partial<Record<KanbanTaskStatus, KanbanTransition>>>> = {
  todo: {
    blocked: transition('unblock'),
    scheduled: transition('unblock'),
    review: transition('reopenReview'),
  },
  scheduled: {
    todo: transition('schedule'),
    ready: transition('schedule'),
    running: transition('schedule'),
    blocked: transition('schedule'),
  },
  ready: {
    todo: transition('promote'),
    blocked: transition('unblock'),
    scheduled: transition('unblock'),
    review: transition('reopenReview'),
  },
  blocked: {
    running: transition('block', { requiresReason: true }),
    ready: transition('block', { requiresReason: true }),
  },
  review: {
    running: transition('requestReview'),
    ready: transition('requestReview'),
  },
  done: {
    running: transition('complete'),
    ready: transition('complete'),
    blocked: transition('complete'),
  },
  archived: {
    done: transition('archive', { confirm: true }),
  },
}

export function resolveKanbanTransition(from: KanbanTaskStatus, to: KanbanTaskStatus): KanbanTransition | null {
  if (from === to) return null
  return DROP_RULES[to]?.[from] || null
}

export function isKanbanDropTarget(from: KanbanTaskStatus, to: KanbanTaskStatus): boolean {
  return from === to || resolveKanbanTransition(from, to) !== null
}

// ─── Board columns ───────────────────────────────────────────────
//
// Hermes keeps nine task statuses; the board shows them as an intake strip plus
// four workflow columns. A column groups statuses; the card itself shows which
// stage it is in: the queue goes todo -> ready (badge) -> running (green ring)
// in place, waiting distinguishes scheduled from blocked, and so on.

export type KanbanColumnId = 'queue' | 'waiting' | 'review' | 'done'

export interface KanbanColumnDef {
  id: KanbanColumnId
  statuses: readonly KanbanTaskStatus[]
}

export const KANBAN_INBOX_STATUS: KanbanTaskStatus = 'triage'
export const KANBAN_ARCHIVED_STATUS: KanbanTaskStatus = 'archived'

export const KANBAN_COLUMNS: readonly KanbanColumnDef[] = [
  { id: 'queue', statuses: ['todo', 'ready', 'running'] },
  { id: 'waiting', statuses: ['scheduled', 'blocked'] },
  { id: 'review', statuses: ['review'] },
  { id: 'done', statuses: ['done'] },
]

export const KANBAN_COLUMN_IDS: readonly KanbanColumnId[] = KANBAN_COLUMNS.map(column => column.id)

export function isKanbanColumnId(value: unknown): value is KanbanColumnId {
  return typeof value === 'string' && (KANBAN_COLUMN_IDS as readonly string[]).includes(value)
}

export function kanbanColumnById(id: KanbanColumnId): KanbanColumnDef {
  return KANBAN_COLUMNS.find(column => column.id === id)!
}

/** Column that shows a status as a card, or null for the intake strip and the archive. */
export function kanbanColumnForStatus(status: KanbanTaskStatus): KanbanColumnId | null {
  if (status === KANBAN_ARCHIVED_STATUS) return 'done'
  return KANBAN_COLUMNS.find(column => column.statuses.includes(status))?.id || null
}

export interface KanbanColumnDrop {
  /** Target status inside the column that Hermes will move the task to. */
  to: KanbanTaskStatus
  transition: KanbanTransition
}

/**
 * Every Hermes transition a drop into `column` could mean for a card coming from
 * `from`. Same-column moves are reorders and never yield a transition. One
 * result runs directly; several (e.g. schedule vs block) need the user to pick.
 */
export function kanbanColumnDropOptions(from: KanbanTaskStatus, column: KanbanColumnDef): KanbanColumnDrop[] {
  if (column.statuses.includes(from)) return []
  const seen = new Set<KanbanTransitionAction>()
  const options: KanbanColumnDrop[] = []
  for (const to of column.statuses) {
    const transition = resolveKanbanTransition(from, to)
    if (!transition || seen.has(transition.action)) continue
    seen.add(transition.action)
    options.push({ to, transition })
  }
  return options
}

export function isKanbanColumnDropTarget(from: KanbanTaskStatus, column: KanbanColumnDef): boolean {
  return column.statuses.includes(from) || kanbanColumnDropOptions(from, column).length > 0
}

// ─── Browser-local layout (manual card order inside a column) ───
//
// Hermes has no card ordering field, so this layout lives only in the viewer's
// browser. It never changes what the dispatcher runs first. Columns always keep
// the Hermes workflow order and cannot be rearranged.

export interface KanbanBoardLayout {
  cards: Partial<Record<KanbanColumnId, string[]>>
}

export const KANBAN_LAYOUT_STORAGE_PREFIX = 'hermes.kanban.layout.'

export function kanbanLayoutStorageKey(board: string): string {
  return `${KANBAN_LAYOUT_STORAGE_PREFIX}${board}`
}

export function emptyKanbanLayout(): KanbanBoardLayout {
  return { cards: {} }
}

/** Older layouts keyed cards by status or stored a column order; both are ignored and dropped on the next save. */
export function parseKanbanLayout(raw: string | null | undefined): KanbanBoardLayout {
  if (!raw) return emptyKanbanLayout()
  try {
    const parsed = JSON.parse(raw) as { cards?: unknown }
    const cards: KanbanBoardLayout['cards'] = {}
    if (parsed?.cards && typeof parsed.cards === 'object') {
      for (const [column, ids] of Object.entries(parsed.cards as Record<string, unknown>)) {
        if (!isKanbanColumnId(column) || !Array.isArray(ids)) continue
        const clean = ids.filter((id): id is string => typeof id === 'string' && id.length > 0)
        if (clean.length) cards[column] = clean
      }
    }
    return { cards }
  } catch {
    return emptyKanbanLayout()
  }
}

export function hasCustomKanbanLayout(layout: KanbanBoardLayout): boolean {
  return Object.keys(layout.cards).length > 0
}

function defaultCardOrder(tasks: readonly KanbanTask[]): KanbanTask[] {
  return [...tasks].sort((a, b) => b.created_at - a.created_at)
}

/**
 * Tasks not covered by the saved order (new arrivals) stay on top in default
 * order so they remain visible; saved tasks keep the viewer's manual order.
 */
export function orderKanbanCards(tasks: readonly KanbanTask[], savedIds: readonly string[] | undefined): KanbanTask[] {
  const sorted = defaultCardOrder(tasks)
  if (!savedIds?.length) return sorted
  const byId = new Map(sorted.map(task => [task.id, task]))
  const saved: KanbanTask[] = []
  for (const id of savedIds) {
    const task = byId.get(id)
    if (task) {
      saved.push(task)
      byId.delete(id)
    }
  }
  return [...sorted.filter(task => byId.has(task.id)), ...saved]
}
