// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string, params?: Record<string, number>) => {
      if (key === 'kanban.card.timeAgo.justNow') return '刚刚'
      if (key === 'kanban.card.timeAgo.minutes') return `${params?.count}分钟前`
      if (key === 'kanban.card.timeAgo.hours') return `${params?.count}小时前`
      if (key === 'kanban.card.timeAgo.days') return `${params?.count}天前`
      if (key === 'kanban.card.priority.high') return '高'
      if (key === 'kanban.card.priority.medium') return '中'
      if (key === 'kanban.card.priority.low') return '低'
      if (key === 'kanban.card.assigneeTooltip') return '负责人'
      return key
    },
  }),
}))

vi.mock('naive-ui', () => ({
  NTooltip: defineComponent({
    name: 'NTooltip',
    template: '<div class="n-tooltip-stub"><slot name="trigger" /><div class="tooltip-content"><slot /></div></div>',
  }),
}))

vi.mock('@/components/hermes/profiles/ProfileAvatar.vue', () => ({
  default: defineComponent({
    name: 'ProfileAvatar',
    props: { name: { type: String, required: true }, avatar: { type: Object, required: false }, size: { type: Number, required: false } },
    template: '<span class="assignee-profile-avatar-stub" :data-name="name" :data-avatar-type="avatar?.type || null" :data-avatar-seed="avatar?.seed || null"></span>',
  }),
}))

import KanbanTaskCard from '@/components/hermes/kanban/KanbanTaskCard.vue'

describe('KanbanTaskCard i18n', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  it('renders localized priority, tooltip, and relative time', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-05-08T03:00:00Z'))

    const wrapper = mount(KanbanTaskCard, {
      props: {
        assigneeAvatar: { type: 'generated', seed: 'alice-seed' },
        task: {
          id: 'task-1',
          title: 'Ship kanban i18n',
          body: 'Body preview content',
          assignee: 'alice',
          status: 'todo',
          priority: 3,
          created_by: null,
          created_at: Math.floor(new Date('2026-05-08T02:58:00Z').getTime() / 1000),
          started_at: null,
          completed_at: null,
          workspace_kind: 'local',
          workspace_path: null,
          tenant: null,
          result: null,
          skills: null,
        },
      },
    })

    expect(wrapper.text()).toContain('高')
    expect(wrapper.text()).toContain('2分钟前')
    expect(wrapper.text()).toContain('负责人')
    expect(wrapper.text()).toContain('task-1')
    expect(wrapper.text()).not.toContain('Body preview content')
    expect(wrapper.element.tagName).toBe('DIV')
    expect(wrapper.classes()).toContain('status-todo')
    expect(wrapper.find('.card-main').attributes('role')).toBe('button')
    expect(wrapper.find('.card-main').attributes('aria-label')).toBe('Ship kanban i18n')
    const avatar = wrapper.find('.assignee-profile-avatar-stub')
    expect(avatar.attributes('data-name')).toBe('alice')
    expect(avatar.attributes('data-avatar-type')).toBe('generated')
    expect(avatar.attributes('data-avatar-seed')).toBe('alice-seed')
  })

  it('shows the status on the card and offers the matching quick action', async () => {
    const base = {
      id: 'task-2',
      title: 'Card states',
      body: null,
      assignee: null,
      priority: 1,
      created_by: null,
      created_at: Math.floor(Date.now() / 1000),
      started_at: null,
      completed_at: null,
      workspace_kind: 'local',
      workspace_path: null,
      tenant: null,
      result: null,
      skills: null,
    }
    const wrapper = mount(KanbanTaskCard, { props: { task: { ...base, status: 'todo' } as any } })
    expect(wrapper.find('.status-badge').exists()).toBe(false)
    expect(wrapper.find('.card-quick-action').attributes('data-action')).toBe('promote')
    await wrapper.find('.card-quick-action').trigger('click')
    expect(wrapper.emitted('action')).toEqual([[{ taskId: 'task-2', action: 'promote' }]])
    expect(wrapper.emitted('click')).toBeUndefined()

    await wrapper.find('.card-main').trigger('keydown', { key: 'Enter' })
    expect(wrapper.emitted('click')).toEqual([['task-2']])

    await wrapper.setProps({ task: { ...base, status: 'running' } as any })
    expect(wrapper.classes()).toContain('status-running')
    expect(wrapper.find('.status-badge').text()).toBe('kanban.columns.running')
    expect(wrapper.find('.card-quick-action').exists()).toBe(false)

    await wrapper.setProps({ task: { ...base, status: 'blocked' } as any })
    expect(wrapper.find('.status-badge .status-icon').exists()).toBe(true)

    await wrapper.setProps({ task: { ...base, status: 'done' } as any })
    expect(wrapper.find('.card-quick-action').attributes('data-action')).toBe('archive')

    await wrapper.setProps({ task: { ...base, status: 'triage' } as any })
    expect(wrapper.find('.card-quick-action').attributes('data-action')).toBe('specify')

    await wrapper.setProps({ task: { ...base, status: 'archived' } as any, muted: true })
    expect(wrapper.classes()).toContain('muted')
    expect(wrapper.find('.card-quick-action').exists()).toBe(false)
  })
})
