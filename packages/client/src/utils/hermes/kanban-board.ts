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

// ─── Browser-local layout (column order and manual card order) ───
//
// Hermes has no column or card ordering fields, so this layout lives only in the
// viewer's browser. It never changes what the dispatcher runs first.

export interface KanbanBoardLayout {
  columns: KanbanTaskStatus[]
  cards: Partial<Record<KanbanTaskStatus, string[]>>
}

export const KANBAN_LAYOUT_STORAGE_PREFIX = 'hermes.kanban.layout.'

export function kanbanLayoutStorageKey(board: string): string {
  return `${KANBAN_LAYOUT_STORAGE_PREFIX}${board}`
}

function isStatus(value: unknown): value is KanbanTaskStatus {
  return typeof value === 'string' && (KANBAN_BOARD_STATUSES as readonly string[]).includes(value)
}

export function emptyKanbanLayout(): KanbanBoardLayout {
  return { columns: [], cards: {} }
}

export function parseKanbanLayout(raw: string | null | undefined): KanbanBoardLayout {
  if (!raw) return emptyKanbanLayout()
  try {
    const parsed = JSON.parse(raw) as { columns?: unknown; cards?: unknown }
    const columns = Array.isArray(parsed?.columns) ? parsed.columns.filter(isStatus) : []
    const cards: KanbanBoardLayout['cards'] = {}
    if (parsed?.cards && typeof parsed.cards === 'object') {
      for (const [status, ids] of Object.entries(parsed.cards as Record<string, unknown>)) {
        if (!isStatus(status) || !Array.isArray(ids)) continue
        const clean = ids.filter((id): id is string => typeof id === 'string' && id.length > 0)
        if (clean.length) cards[status] = clean
      }
    }
    return { columns: [...new Set(columns)], cards }
  } catch {
    return emptyKanbanLayout()
  }
}

export function hasCustomKanbanLayout(layout: KanbanBoardLayout): boolean {
  return layout.columns.length > 0 || Object.keys(layout.cards).length > 0
}

/** Saved order first (unknown statuses dropped), then any status not yet saved in default order. */
export function orderKanbanColumns(saved: readonly KanbanTaskStatus[] | undefined): KanbanTaskStatus[] {
  const seen = new Set<KanbanTaskStatus>()
  const ordered: KanbanTaskStatus[] = []
  for (const status of saved || []) {
    if (isStatus(status) && !seen.has(status)) {
      seen.add(status)
      ordered.push(status)
    }
  }
  for (const status of KANBAN_BOARD_STATUSES) {
    if (!seen.has(status)) ordered.push(status)
  }
  return ordered
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
