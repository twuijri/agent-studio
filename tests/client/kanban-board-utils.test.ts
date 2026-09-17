import { describe, expect, it } from 'vitest'
import {
  KANBAN_BOARD_STATUSES,
  KANBAN_COLUMNS,
  hasCustomKanbanLayout,
  isKanbanColumnDropTarget,
  kanbanColumnById,
  kanbanColumnDropOptions,
  kanbanColumnForStatus,
  isKanbanDropTarget,
  kanbanLayoutStorageKey,
  orderKanbanCards,
  parseKanbanLayout,
  resolveKanbanTransition,
} from '@/utils/hermes/kanban-board'
import type { KanbanTask, KanbanTaskStatus } from '@/api/hermes/kanban'

function task(id: string, status: KanbanTaskStatus, createdAt: number): KanbanTask {
  return {
    id,
    title: id,
    body: null,
    assignee: null,
    status,
    priority: 1,
    created_by: null,
    created_at: createdAt,
    started_at: null,
    completed_at: null,
    workspace_kind: 'local',
    workspace_path: null,
    tenant: null,
    result: null,
    skills: null,
  }
}

describe('kanban board transitions', () => {
  it('maps drops to the Hermes commands the server bridges, mirroring its source guards', () => {
    expect(resolveKanbanTransition('todo', 'ready')).toEqual({ action: 'promote', requiresReason: false, confirm: false })
    expect(resolveKanbanTransition('blocked', 'ready')).toEqual({ action: 'unblock', requiresReason: false, confirm: false })
    expect(resolveKanbanTransition('scheduled', 'todo')).toEqual({ action: 'unblock', requiresReason: false, confirm: false })
    expect(resolveKanbanTransition('review', 'todo')).toEqual({ action: 'reopenReview', requiresReason: false, confirm: false })
    expect(resolveKanbanTransition('running', 'scheduled')).toEqual({ action: 'schedule', requiresReason: false, confirm: false })
    expect(resolveKanbanTransition('ready', 'blocked')).toEqual({ action: 'block', requiresReason: true, confirm: false })
    expect(resolveKanbanTransition('running', 'review')).toEqual({ action: 'requestReview', requiresReason: false, confirm: false })
    expect(resolveKanbanTransition('blocked', 'done')).toEqual({ action: 'complete', requiresReason: false, confirm: false })
    expect(resolveKanbanTransition('done', 'archived')).toEqual({ action: 'archive', requiresReason: false, confirm: true })
  })

  it('never offers triage or running as drop targets and rejects unsupported source statuses', () => {
    for (const from of KANBAN_BOARD_STATUSES) {
      expect(resolveKanbanTransition(from, 'triage')).toBeNull()
      expect(resolveKanbanTransition(from, 'running')).toBeNull()
    }
    expect(resolveKanbanTransition('todo', 'blocked')).toBeNull()
    expect(resolveKanbanTransition('todo', 'done')).toBeNull()
    expect(resolveKanbanTransition('done', 'ready')).toBeNull()
    expect(resolveKanbanTransition('archived', 'done')).toBeNull()
    expect(resolveKanbanTransition('triage', 'todo')).toBeNull()
  })

  it('treats same-column moves as allowed reorders', () => {
    expect(isKanbanDropTarget('todo', 'todo')).toBe(true)
    expect(isKanbanDropTarget('todo', 'ready')).toBe(true)
    expect(isKanbanDropTarget('todo', 'running')).toBe(false)
  })
})

describe('kanban board columns', () => {
  it('maps every Hermes status to the intake strip, a workflow column, or the archive under done', () => {
    expect(KANBAN_COLUMNS.map(column => column.id)).toEqual(['queue', 'waiting', 'review', 'done'])
    expect(kanbanColumnForStatus('triage')).toBeNull()
    expect(kanbanColumnForStatus('todo')).toBe('queue')
    expect(kanbanColumnForStatus('ready')).toBe('queue')
    expect(kanbanColumnForStatus('running')).toBe('queue')
    expect(kanbanColumnForStatus('scheduled')).toBe('waiting')
    expect(kanbanColumnForStatus('blocked')).toBe('waiting')
    expect(kanbanColumnForStatus('review')).toBe('review')
    expect(kanbanColumnForStatus('done')).toBe('done')
    expect(kanbanColumnForStatus('archived')).toBe('done')
    const covered = new Set(KANBAN_COLUMNS.flatMap(column => column.statuses))
    expect([...covered].sort()).toEqual(['blocked', 'done', 'ready', 'review', 'running', 'scheduled', 'todo'])
  })

  it('derives drop options per column from the status transitions and treats same-column moves as reorders', () => {
    const actions = (from: KanbanTaskStatus, column: string) => kanbanColumnDropOptions(from, kanbanColumnById(column as any)).map(option => `${option.transition.action}->${option.to}`)
    expect(actions('todo', 'queue')).toEqual([])
    expect(actions('ready', 'queue')).toEqual([])
    expect(actions('blocked', 'queue')).toEqual(['unblock->todo'])
    expect(actions('review', 'queue')).toEqual(['reopenReview->todo'])
    expect(actions('running', 'queue')).toEqual([])
    expect(actions('running', 'review')).toEqual(['requestReview->review'])
    expect(actions('ready', 'waiting')).toEqual(['schedule->scheduled', 'block->blocked'])
    expect(actions('todo', 'waiting')).toEqual(['schedule->scheduled'])
    expect(actions('running', 'review')).toEqual(['requestReview->review'])
    expect(actions('todo', 'review')).toEqual([])
    expect(actions('blocked', 'done')).toEqual(['complete->done'])
    expect(actions('done', 'queue')).toEqual([])
    expect(isKanbanColumnDropTarget('scheduled', kanbanColumnById('waiting'))).toBe(true)
    expect(isKanbanColumnDropTarget('todo', kanbanColumnById('review'))).toBe(false)
    expect(isKanbanColumnDropTarget('ready', kanbanColumnById('review'))).toBe(true)
  })
})

describe('kanban browser-local layout', () => {
  it('parses stored layouts defensively and drops the retired column order', () => {
    expect(parseKanbanLayout(null)).toEqual({ cards: {} })
    expect(parseKanbanLayout('not json')).toEqual({ cards: {} })
    expect(parseKanbanLayout(JSON.stringify({
      columns: ['done', 'todo'],
      cards: { queue: ['a', '', 3, 'b'], todo: ['legacy'], nope: ['x'], review: [] },
    }))).toEqual({ cards: { queue: ['a', 'b'] } })
    expect(hasCustomKanbanLayout({ cards: {} })).toBe(false)
    expect(hasCustomKanbanLayout({ cards: { queue: ['a'] } })).toBe(true)
    expect(kanbanLayoutStorageKey('project-a')).toBe('hermes.kanban.layout.project-a')
  })

  it('keeps manual card order and shows unsaved arrivals first, newest on top', () => {
    const tasks = [task('a', 'todo', 1), task('b', 'todo', 2), task('c', 'todo', 3)]
    expect(orderKanbanCards(tasks, undefined).map(t => t.id)).toEqual(['c', 'b', 'a'])
    expect(orderKanbanCards(tasks, ['a', 'c', 'b']).map(t => t.id)).toEqual(['a', 'c', 'b'])
    expect(orderKanbanCards(tasks, ['a', 'missing', 'b']).map(t => t.id)).toEqual(['c', 'a', 'b'])
    expect(orderKanbanCards([...tasks, task('d', 'todo', 4), task('e', 'todo', 0)], ['b', 'a']).map(t => t.id)).toEqual(['d', 'c', 'e', 'b', 'a'])
  })
})
