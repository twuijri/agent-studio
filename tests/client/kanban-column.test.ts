// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({ t: (key: string) => key }),
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
    props: { task: { type: Object, required: true }, assigneeAvatar: { type: Object, required: false } },
    emits: ['click'],
    template: '<button class="kanban-task-card-stub" :data-avatar-seed="assigneeAvatar?.seed || null" @click="$emit(\'click\', task.id)">{{ task.title }}</button>',
  }),
}))

import KanbanColumn from '@/components/hermes/kanban/KanbanColumn.vue'

function task(id: string, status: string, assignee: string | null = null) {
  return { id, title: `Title ${id}`, status, assignee, priority: 1, created_at: 1 } as any
}

function listElement(status: string): HTMLElement {
  const element = document.createElement('div')
  element.dataset.status = status
  return element
}

describe('KanbanColumn', () => {
  it('renders ordered task slots, passes avatars, and forwards card clicks', async () => {
    const wrapper = mount(KanbanColumn, {
      props: {
        status: 'todo',
        tasks: [task('t-1', 'todo', 'alice'), task('t-2', 'todo')],
        avatars: { alice: { type: 'generated', seed: 'alice-seed' } as any },
      },
    })

    expect(wrapper.attributes('data-status')).toBe('todo')
    expect(wrapper.findAll('.task-slot').map(slot => slot.attributes('data-task-id'))).toEqual(['t-1', 't-2'])
    expect(wrapper.find('.column-count').text()).toBe('2')
    expect(wrapper.find('.kanban-task-card-stub').attributes('data-avatar-seed')).toBe('alice-seed')
    expect(wrapper.find('.column-empty').exists()).toBe(false)

    await wrapper.findAll('.kanban-task-card-stub')[1].trigger('click')
    expect(wrapper.emitted('taskClick')).toEqual([['t-2']])
  })

  it('marks columns that cannot accept the dragged card and highlights the ones that can', async () => {
    const wrapper = mount(KanbanColumn, { props: { status: 'running', tasks: [], draggingStatus: 'todo' } })
    expect(wrapper.classes()).toContain('drop-blocked')
    expect(wrapper.find('.column-empty').text()).toBe('kanban.dnd.dropNotAllowed')

    await wrapper.setProps({ status: 'ready' })
    expect(wrapper.classes()).not.toContain('drop-blocked')
    expect(wrapper.classes()).toContain('drop-open')
    expect(wrapper.find('.column-empty').text()).toBe('kanban.noTasks')

    await wrapper.setProps({ draggingStatus: null })
    expect(wrapper.classes()).not.toContain('drop-open')
  })

  it('answers the sortable move guard from the transition map', () => {
    const wrapper = mount(KanbanColumn, { props: { status: 'ready', tasks: [] } })
    // Sortable reads the guard as its `onMove` option, so it must arrive as a listener, not a prop.
    const move = wrapper.findComponent({ name: 'VueDraggable' }).vm.$attrs.onMove as (event: any) => boolean
    expect(move({ from: listElement('todo'), to: listElement('ready') })).toBe(true)
    expect(move({ from: listElement('todo'), to: listElement('running') })).toBe(false)
    expect(move({ from: document.createElement('div'), to: listElement('ready') })).toBe(false)
  })

  it('emits drag lifecycle, reorder ids, and cross-column drops with the source status', async () => {
    const wrapper = mount(KanbanColumn, { props: { status: 'ready', tasks: [task('t-1', 'ready'), task('t-2', 'ready')] } })
    const draggable = wrapper.findComponent({ name: 'VueDraggable' })

    draggable.vm.$emit('start')
    expect(wrapper.emitted('dragStart')).toEqual([['ready']])

    draggable.vm.$emit('update')
    expect(wrapper.emitted('reorder')).toEqual([[['t-1', 't-2']]])

    draggable.vm.$emit('add', { from: listElement('todo'), to: listElement('ready'), data: task('t-9', 'todo'), newIndex: 1 })
    expect(wrapper.emitted('dropped')).toEqual([[{ taskId: 't-9', from: 'todo', to: 'ready', index: 1 }]])

    // Sortable inserts into the last column that accepted the card; the drop is
    // reported against the column under the pointer so refused targets revert.
    const runningColumn = document.createElement('section')
    runningColumn.className = 'kanban-column'
    runningColumn.dataset.status = 'running'
    const inner = document.createElement('div')
    runningColumn.appendChild(inner)
    const originalElementFromPoint = document.elementFromPoint
    document.elementFromPoint = vi.fn(() => inner) as any
    try {
      draggable.vm.$emit('add', { from: listElement('todo'), to: listElement('ready'), data: task('t-9', 'todo'), newIndex: 0, originalEvent: { clientX: 10, clientY: 20 } })
    } finally {
      document.elementFromPoint = originalElementFromPoint
    }
    expect(wrapper.emitted('dropped')![1]).toEqual([{ taskId: 't-9', from: 'todo', to: 'running', index: 0 }])
    expect(document.elementFromPoint).toBe(originalElementFromPoint)

    draggable.vm.$emit('end')
    expect(wrapper.emitted('dragEnd')).toHaveLength(1)
  })

  it('keeps the local list stable while a drag is in flight and resyncs afterwards', async () => {
    const wrapper = mount(KanbanColumn, { props: { status: 'todo', tasks: [task('t-1', 'todo')], draggingStatus: 'todo' } })
    await wrapper.setProps({ tasks: [task('t-1', 'todo'), task('t-2', 'todo')] })
    expect(wrapper.findAll('.task-slot')).toHaveLength(1)

    await wrapper.setProps({ draggingStatus: null })
    expect(wrapper.findAll('.task-slot')).toHaveLength(2)

    await wrapper.setProps({ tasks: [task('t-2', 'todo')] })
    expect(wrapper.findAll('.task-slot').map(slot => slot.attributes('data-task-id'))).toEqual(['t-2'])

    await wrapper.setProps({ dragDisabled: true })
    expect(wrapper.find('.vue-draggable-stub').attributes('data-disabled')).toBe('true')
  })
})
