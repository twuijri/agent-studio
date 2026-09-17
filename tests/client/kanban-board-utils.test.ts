import { describe, expect, it } from 'vitest'
import {
  KANBAN_BOARD_STATUSES,
  hasCustomKanbanLayout,
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

describe('kanban browser-local layout', () => {
  it('parses stored layouts defensively and drops the retired column order', () => {
    expect(parseKanbanLayout(null)).toEqual({ cards: {} })
    expect(parseKanbanLayout('not json')).toEqual({ cards: {} })
    expect(parseKanbanLayout(JSON.stringify({
      columns: ['done', 'todo'],
      cards: { todo: ['a', '', 3, 'b'], nope: ['x'], ready: [] },
    }))).toEqual({ cards: { todo: ['a', 'b'] } })
    expect(hasCustomKanbanLayout({ cards: {} })).toBe(false)
    expect(hasCustomKanbanLayout({ cards: { todo: ['a'] } })).toBe(true)
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
