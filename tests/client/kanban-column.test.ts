// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { kanbanColumnById } from '@/utils/hermes/kanban-board'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string, params?: unknown) => (params && typeof params === 'object' && 'count' in params ? `${key}:${(params as { count: unknown }).count}` : key) }),
}))

vi.mock('vue-draggable-plus', () => ({
  VueDraggable: defineComponent({
    name: 'VueDraggable',
    props: {
      modelValue: { type: Array, default: () => [] },
      disabled: Boolean,
    },
    emits: ['update:modelValue', 'start', 'end', 'update', 'add'],
    template: '<div class="vue-draggable-stub" :data-disabled="disabled ? \'true\' : \'false\'"><slot /></div>',
  }),
}))

vi.mock('@/components/hermes/kanban/KanbanTaskCard.vue', () => ({
  default: defineComponent({
    name: 'KanbanTaskCard',
    props: { task: { type: Object, required: true }, assigneeAvatar: { type: Object, required: false }, muted: Boolean, pending: Boolean },
    emits: ['click', 'action'],
    template: '<button class="kanban-task-card-stub" :data-pending="pending ? \'true\' : \'false\'" :data-muted="muted ? \'true\' : \'false\'" :data-avatar-seed="assigneeAvatar?.seed || null" @click="$emit(\'click\', task.id)">{{ task.title }}<i class="action-stub" @click.stop="$emit(\'action\', { taskId: task.id, action: \'promote\' })" /></button>',
  }),
}))

import KanbanColumn from '@/components/hermes/kanban/KanbanColumn.vue'

function task(id: string, status: string, assignee: string | null = null) {
  return { id, title: `Title ${id}`, status, assignee, priority: 1, created_at: 1 } as any
}

function listElement(column: string): HTMLElement {
  const element = document.createElement('div')
  element.dataset.column = column
  return element
}

function slotElement(status: string): HTMLElement {
  const element = document.createElement('div')
  element.dataset.status = status
  return element
}

describe('KanbanColumn', () => {
  it('renders ordered task slots with their status, passes avatars, and forwards clicks and quick actions', async () => {
    const wrapper = mount(KanbanColumn, {
      props: {
        column: kanbanColumnById('queue'),
        tasks: [task('t-1', 'ready', 'alice'), task('t-2', 'todo')],
        avatars: { alice: { type: 'generated', seed: 'alice-seed' } as any },
      },
    })

    expect(wrapper.attributes('data-column')).toBe('queue')
    expect(wrapper.find('.column-title').text()).toBe('kanban.board.columns.queue')
    expect(wrapper.findAll('.task-slot').map(slot => [slot.attributes('data-task-id'), slot.attributes('data-status')])).toEqual([['t-1', 'ready'], ['t-2', 'todo']])
    expect(wrapper.find('.column-count').text()).toBe('2')
    expect(wrapper.find('.kanban-task-card-stub').attributes('data-avatar-seed')).toBe('alice-seed')
    expect(wrapper.find('.archive-section').exists()).toBe(false)

    await wrapper.findAll('.kanban-task-card-stub')[1].trigger('click')
    expect(wrapper.emitted('taskClick')).toEqual([['t-2']])
    await wrapper.find('.action-stub').trigger('click')
    expect(wrapper.emitted('taskAction')).toEqual([[{ taskId: 't-1', action: 'promote' }]])
  })

  it('marks columns that cannot accept the dragged card and highlights the ones that can', async () => {
    const wrapper = mount(KanbanColumn, { props: { column: kanbanColumnById('review'), tasks: [], draggingStatus: 'todo' } })
    expect(wrapper.classes()).toContain('drop-blocked')
    expect(wrapper.find('.column-empty').text()).toBe('kanban.dnd.dropNotAllowed')

    await wrapper.setProps({ column: kanbanColumnById('waiting') })
    expect(wrapper.classes()).not.toContain('drop-blocked')
    expect(wrapper.classes()).toContain('drop-open')

    // Dragging inside the column that already holds the status is a plain reorder.
    await wrapper.setProps({ column: kanbanColumnById('queue') })
    expect(wrapper.classes()).not.toContain('drop-open')
    expect(wrapper.classes()).not.toContain('drop-blocked')

    await wrapper.setProps({ draggingStatus: null })
    expect(wrapper.classes()).not.toContain('drop-open')
  })

  it('answers the sortable move guard from the dragged card status and the target column', () => {
    const wrapper = mount(KanbanColumn, { props: { column: kanbanColumnById('review'), tasks: [] } })
    // Sortable reads the guard as its `onMove` option, so it must arrive as a listener, not a prop.
    const move = wrapper.findComponent({ name: 'VueDraggable' }).vm.$attrs.onMove as (event: any) => boolean
    expect(move({ from: listElement('queue'), to: listElement('review'), dragged: slotElement('ready') })).toBe(true)
    expect(move({ from: listElement('queue'), to: listElement('review'), dragged: slotElement('todo') })).toBe(false)
    expect(move({ from: listElement('waiting'), to: listElement('review'), dragged: slotElement('scheduled') })).toBe(false)
    expect(move({ from: listElement('queue'), to: document.createElement('div'), dragged: slotElement('ready') })).toBe(false)
  })

  it('emits drag lifecycle, reorder ids, and drops against the column under the pointer', async () => {
    const wrapper = mount(KanbanColumn, { props: { column: kanbanColumnById('waiting'), tasks: [task('t-1', 'blocked'), task('t-2', 'scheduled')] } })
    const draggable = wrapper.findComponent({ name: 'VueDraggable' })

    draggable.vm.$emit('start', { item: slotElement('blocked'), data: task('t-1', 'blocked') })
    expect(wrapper.emitted('dragStart')).toEqual([['blocked']])

    draggable.vm.$emit('update')
    expect(wrapper.emitted('reorder')).toEqual([[['t-1', 't-2']]])

    draggable.vm.$emit('add', { from: listElement('queue'), to: listElement('waiting'), item: slotElement('ready'), data: task('t-9', 'ready'), newIndex: 1 })
    expect(wrapper.emitted('dropped')![0]).toEqual([{ taskId: 't-9', from: 'ready', toColumn: kanbanColumnById('waiting'), index: 1 }])

    const reviewColumn = document.createElement('section')
    reviewColumn.className = 'kanban-column'
    reviewColumn.dataset.column = 'review'
    const inner = document.createElement('div')
    reviewColumn.appendChild(inner)
    const originalElementFromPoint = document.elementFromPoint
    document.elementFromPoint = vi.fn(() => inner) as any
    try {
      draggable.vm.$emit('add', { from: listElement('queue'), to: listElement('waiting'), item: slotElement('ready'), data: task('t-9', 'ready'), newIndex: 0, originalEvent: { clientX: 10, clientY: 20 } })
    } finally {
      document.elementFromPoint = originalElementFromPoint
    }
    expect(wrapper.emitted('dropped')![1]).toEqual([{ taskId: 't-9', from: 'ready', toColumn: kanbanColumnById('review'), index: 0 }])

    draggable.vm.$emit('end')
    expect(wrapper.emitted('dragEnd')).toHaveLength(1)
  })

  it('keeps the local list stable while a drag is in flight and resyncs afterwards', async () => {
    const wrapper = mount(KanbanColumn, { props: { column: kanbanColumnById('queue'), tasks: [task('t-1', 'todo')], draggingStatus: 'todo' } })
    await wrapper.setProps({ tasks: [task('t-1', 'todo'), task('t-2', 'ready')] })
    expect(wrapper.findAll('.task-slot')).toHaveLength(1)

    await wrapper.setProps({ draggingStatus: null })
    expect(wrapper.findAll('.task-slot')).toHaveLength(2)

    await wrapper.setProps({ tasks: [task('t-2', 'ready')] })
    expect(wrapper.findAll('.task-slot').map(slot => slot.attributes('data-task-id'))).toEqual(['t-2'])

    await wrapper.setProps({ dragDisabled: true })
    expect(wrapper.find('.vue-draggable-stub').attributes('data-disabled')).toBe('true')
  })

  it('keeps archived tasks behind a toggle under the done column, muted and read-only', async () => {
    const wrapper = mount(KanbanColumn, {
      props: { column: kanbanColumnById('done'), tasks: [task('t-1', 'done')], archivedTasks: [task('t-8', 'archived'), task('t-9', 'archived')] },
    })
    const toggle = wrapper.find('.archive-toggle')
    expect(toggle.text()).toBe('kanban.board.showArchived:2')
    expect(wrapper.find('.archive-list').exists()).toBe(false)

    await toggle.trigger('click')
    expect(wrapper.find('.archive-toggle').text()).toBe('kanban.board.hideArchived:2')
    const archived = wrapper.findAll('.archive-list .kanban-task-card-stub')
    expect(archived).toHaveLength(2)
    expect(archived.every(card => card.attributes('data-muted') === 'true')).toBe(true)
    expect(wrapper.findAll('.task-slot')).toHaveLength(1)

    await archived[0].trigger('click')
    expect(wrapper.emitted('taskClick')).toEqual([['t-8']])
  })

  it('collapses a collapsible column while empty and expands for drops or on click', async () => {
    const wrapper = mount(KanbanColumn, { props: { column: kanbanColumnById('waiting'), tasks: [], collapsible: true } })
    expect(wrapper.classes()).toContain('collapsed')
    expect(wrapper.attributes('data-collapsed')).toBe('true')
    expect(wrapper.find('.column-body').attributes('style') || '').toContain('display: none')
    expect(wrapper.find('button.column-header').attributes('aria-expanded')).toBe('false')

    await wrapper.setProps({ draggingStatus: 'ready' })
    expect(wrapper.classes()).not.toContain('collapsed')
    expect(wrapper.find('.column-body').attributes('style') || '').not.toContain('display: none')

    await wrapper.setProps({ draggingStatus: 'todo' })
    expect(wrapper.classes()).not.toContain('collapsed')

    await wrapper.setProps({ draggingStatus: 'done' })
    expect(wrapper.classes()).toContain('collapsed')

    await wrapper.find('button.column-header').trigger('click')
    expect(wrapper.classes()).not.toContain('collapsed')
    expect(wrapper.find('button.column-header').attributes('aria-expanded')).toBe('true')
    await wrapper.find('button.column-header').trigger('click')
    expect(wrapper.classes()).toContain('collapsed')

    // Store updates are deferred while a drag is in flight, so end it first.
    await wrapper.setProps({ draggingStatus: null, tasks: [task('t-1', 'blocked')] })
    expect(wrapper.classes()).not.toContain('collapsed')

    const plain = mount(KanbanColumn, { props: { column: kanbanColumnById('review'), tasks: [] } })
    expect(plain.classes()).not.toContain('collapsed')
    expect(plain.find('header.column-header').exists()).toBe(true)
  })

  it('marks pending tasks as not draggable and shows a loading hint during the first fetch', async () => {
    const wrapper = mount(KanbanColumn, {
      props: { column: kanbanColumnById('queue'), tasks: [task('t-1', 'ready'), task('t-2', 'todo')], pendingTaskIds: { 't-1': 'review' } },
    })
    const slots = wrapper.findAll('.task-slot')
    expect(slots[0].classes()).toContain('task-slot-pending')
    expect(slots[0].attributes('data-pending')).toBe('true')
    expect(slots[1].attributes('data-pending')).toBe('false')
    expect(wrapper.findAll('.kanban-task-card-stub').map(card => card.attributes('data-pending'))).toEqual(['true', 'false'])
    expect(wrapper.findComponent({ name: 'VueDraggable' }).vm.$attrs.filter).toBe('.task-slot-pending')

    const loading = mount(KanbanColumn, { props: { column: kanbanColumnById('review'), tasks: [], loading: true } })
    expect(loading.find('.column-empty').text()).toBe('kanban.board.loadingTasks')
    expect(loading.find('.column-loading-spinner').exists()).toBe(true)
    expect(loading.find('.column-empty').attributes('role')).toBe('status')
    await loading.setProps({ loading: false })
    expect(loading.find('.column-empty').text()).toBe('kanban.noTasks')
  })
})
