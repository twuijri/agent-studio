// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'

const routeState = vi.hoisted(() => ({
  query: { board: 'project-a' } as Record<string, string>,
}))

const routerReplace = vi.hoisted(() => vi.fn())

const storeState = vi.hoisted(() => ({
  tasks: [] as Array<{ id: string; title: string; status: string; created_at: number; assignee?: string | null }>,
  stats: { by_status: { todo: 1, done: 0 }, by_assignee: {}, total: 1 } as Record<string, any>,
  assignees: [] as Array<{ name: string; counts: Record<string, number> | null }>,
  activeBoards: [] as Array<{ slug: string; name: string; icon?: string; total?: number }>,
  loading: false,
  boardsLoading: false,
  selectedBoard: 'default',
  boardWarning: null as string | null,
  capabilities: null as Record<string, any> | null,
  filterStatus: null as string | null,
  filterAssignee: null as string | null,
  hasCustomLayout: false,
}))

const mockFetchBoards = vi.hoisted(() => vi.fn())
const mockFetchCapabilities = vi.hoisted(() => vi.fn())
const mockRefreshAll = vi.hoisted(() => vi.fn())
const mockFetchTasks = vi.hoisted(() => vi.fn())
const mockFetchStats = vi.hoisted(() => vi.fn())
const mockSetFilter = vi.hoisted(() => vi.fn())
const mockRecoverSelectedBoard = vi.hoisted(() => vi.fn())
const mockCreateBoard = vi.hoisted(() => vi.fn())
const mockArchiveSelectedBoard = vi.hoisted(() => vi.fn())
const mockDispatch = vi.hoisted(() => vi.fn())
const mockDialogWarning = vi.hoisted(() => vi.fn())
const mockStartEventStream = vi.hoisted(() => vi.fn())
const mockStopEventStream = vi.hoisted(() => vi.fn())
const mockFetchProfiles = vi.hoisted(() => vi.fn())
const mockSetCardOrder = vi.hoisted(() => vi.fn())
const mockResetLayout = vi.hoisted(() => vi.fn())
const mockCompleteTasks = vi.hoisted(() => vi.fn())
const mockBlockTask = vi.hoisted(() => vi.fn())
const mockUnblockTasks = vi.hoisted(() => vi.fn())
const mockArchiveTasks = vi.hoisted(() => vi.fn())
const mockPromoteTask = vi.hoisted(() => vi.fn())
const mockScheduleTask = vi.hoisted(() => vi.fn())
const mockRequestReview = vi.hoisted(() => vi.fn())
const mockReopenReview = vi.hoisted(() => vi.fn())
const mockMessageError = vi.hoisted(() => vi.fn())
const mockMessageSuccess = vi.hoisted(() => vi.fn())
const profilesState = vi.hoisted(() => ({
  profiles: [] as Array<{ name: string; avatar?: Record<string, any> | null }>,
}))

vi.mock('vue-router', () => ({
  useRoute: () => routeState,
  useRouter: () => ({ replace: routerReplace }),
}))

vi.mock('vue-i18n', () => ({
  useI18n: () => ({
    t: (key: string) => key,
  }),
}))

vi.mock('@/stores/hermes/kanban', () => ({
  DEFAULT_KANBAN_BOARD: 'default',
  useKanbanStore: () => ({
    ...storeState,
    fetchBoards: mockFetchBoards,
    fetchCapabilities: mockFetchCapabilities,
    refreshAll: mockRefreshAll,
    fetchTasks: mockFetchTasks,
    fetchStats: mockFetchStats,
    setFilter: mockSetFilter,
    recoverSelectedBoard: mockRecoverSelectedBoard,
    createBoard: mockCreateBoard,
    archiveSelectedBoard: mockArchiveSelectedBoard,
    dispatch: mockDispatch,
    startEventStream: mockStartEventStream,
    stopEventStream: mockStopEventStream,
    orderedTasksForColumn: (column: string) => {
      const statuses: Record<string, string[]> = { queue: ['todo', 'ready', 'running'], waiting: ['scheduled', 'blocked'], review: ['review'], done: ['done'] }
      return storeState.tasks.filter(task => statuses[column]?.includes(task.status)).sort((a, b) => b.created_at - a.created_at)
    },
    tasksWithStatus: (status: string) => storeState.tasks.filter(task => task.status === status),
    setCardOrder: mockSetCardOrder,
    resetLayout: mockResetLayout,
    completeTasks: mockCompleteTasks,
    blockTask: mockBlockTask,
    unblockTasks: mockUnblockTasks,
    archiveTasks: mockArchiveTasks,
    promoteTask: mockPromoteTask,
    scheduleTask: mockScheduleTask,
    requestReview: mockRequestReview,
    reopenReview: mockReopenReview,
  }),
}))

vi.mock('@/stores/hermes/profiles', () => ({
  useProfilesStore: () => ({
    profiles: profilesState.profiles,
    fetchProfiles: mockFetchProfiles,
  }),
}))

vi.mock('@/components/hermes/kanban/KanbanColumn.vue', () => ({
  default: defineComponent({
    name: 'KanbanColumn',
    props: {
      column: { type: Object, required: true },
      tasks: { type: Array, default: () => [] },
      archivedTasks: { type: Array, required: false },
      avatars: { type: Object, required: false },
      draggingStatus: { type: String, required: false },
      dragDisabled: { type: Boolean, default: false },
      collapsible: { type: Boolean, default: false },
    },
    emits: ['taskClick', 'taskAction', 'reorder', 'dropped', 'dragStart', 'dragEnd'],
    template: `
      <section class="kanban-column" :data-column="column.id" :data-collapsible="collapsible ? 'true' : 'false'" :data-archived="archivedTasks ? archivedTasks.length : ''" :data-dragging="draggingStatus || ''" :data-drag-disabled="dragDisabled ? 'true' : 'false'">
        <button
          v-for="task in tasks"
          :key="task.id"
          class="kanban-task-card-stub"
          :data-avatar-seed="task.assignee ? avatars?.[task.assignee]?.seed || null : null"
          @click="$emit('taskClick', task.id)"
        >{{ task.title }}</button>
      </section>
    `,
  }),
}))

vi.mock('@/components/hermes/kanban/KanbanTaskCard.vue', () => ({
  default: defineComponent({
    name: 'KanbanTaskCard',
    props: { task: { type: Object, required: true }, assigneeAvatar: { type: Object, required: false }, muted: Boolean },
    emits: ['click', 'action'],
    template: '<button class="inbox-card-stub" @click="$emit(\'click\', task.id)">{{ task.title }}</button>',
  }),
}))

vi.mock('@/components/hermes/kanban/KanbanTaskDrawer.vue', () => ({
  default: defineComponent({
    name: 'KanbanTaskDrawer',
    props: { taskId: { type: String, required: false } },
    emits: ['updated', 'close'],
    template: '<button class="drawer-updated" :data-task-id="taskId || null" @click="$emit(\'updated\')">drawer</button>',
  }),
}))

vi.mock('@/components/hermes/kanban/KanbanCreateForm.vue', () => ({
  default: defineComponent({
    name: 'KanbanCreateForm',
    emits: ['created', 'close'],
    template: '<button class="form-created" @click="$emit(\'created\')">form</button>',
  }),
}))

vi.mock('naive-ui', () => ({
  useDialog: () => ({ warning: mockDialogWarning }),
  useMessage: () => ({ warning: vi.fn(), error: mockMessageError, success: mockMessageSuccess }),
  NButton: defineComponent({
    name: 'NButton',
    props: { disabled: Boolean },
    emits: ['click'],
    template: '<button class="n-button-stub" :disabled="disabled" @click="$emit(\'click\')"><slot /><slot name="icon" /></button>',
  }),
  NTooltip: defineComponent({
    name: 'NTooltip',
    props: { disabled: Boolean },
    template: '<span class="n-tooltip-stub"><slot name="trigger" /><span v-if="!disabled" class="n-tooltip-content"><slot /></span></span>',
  }),
  NSelect: defineComponent({
    name: 'NSelect',
    props: { value: null, options: { type: Array, default: () => [] }, loading: Boolean },
    emits: ['update:value'],
    template: '<button class="n-select-stub" @click="$emit(\'update:value\', options[1]?.value || value)"><span v-for="option in options" :key="option.value">{{ option.label }}</span>{{ value }}</button>',
  }),
  NInput: defineComponent({
    name: 'NInput',
    props: { value: { type: String, default: '' }, placeholder: { type: String, required: false } },
    emits: ['update:value'],
    template: '<input class="n-input-stub" :placeholder="placeholder" :value="value" @input="$emit(\'update:value\', $event.target.value)" />',
  }),
  NModal: defineComponent({
    name: 'NModal',
    props: { show: Boolean },
    emits: ['update:show', 'close'],
    template: '<div v-if="show" class="n-modal-stub"><slot /><slot name="action" /></div>',
  }),
  NSpin: defineComponent({
    name: 'NSpin',
    template: '<div class="n-spin-stub"><slot /></div>',
  }),
}))

import KanbanView from '@/views/hermes/KanbanView.vue'

describe('KanbanView', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    routeState.query = { board: 'project-a' }
    routerReplace.mockResolvedValue(undefined)
    storeState.tasks = [
      { id: 'task-1', title: 'Task one', status: 'todo', created_at: 10 },
      { id: 'task-2', title: 'Task two', status: 'done', created_at: 20 },
    ]
    storeState.stats = {
      by_status: { triage: 0, todo: 1, ready: 0, running: 0, blocked: 0, done: 1, archived: 0 },
      by_assignee: {},
      total: 2,
    }
    storeState.assignees = []
    storeState.activeBoards = [
      { slug: 'default', name: 'Default', total: 0 },
      { slug: 'project-a', name: 'Project A', total: 2 },
    ]
    storeState.loading = false
    storeState.boardsLoading = false
    storeState.selectedBoard = 'default'
    storeState.boardWarning = null
    storeState.capabilities = null
    storeState.filterStatus = null
    storeState.filterAssignee = null
    storeState.hasCustomLayout = false
    mockPromoteTask.mockResolvedValue(undefined)
    mockCompleteTasks.mockResolvedValue(undefined)
    mockBlockTask.mockResolvedValue(undefined)
    mockArchiveTasks.mockResolvedValue(undefined)
    profilesState.profiles = []
    mockFetchBoards.mockResolvedValue(undefined)
    mockFetchCapabilities.mockResolvedValue(undefined)
    mockRefreshAll.mockResolvedValue(undefined)
    mockFetchTasks.mockResolvedValue(undefined)
    mockFetchStats.mockResolvedValue(undefined)
    mockFetchProfiles.mockResolvedValue(undefined)
    mockCreateBoard.mockResolvedValue({ slug: 'new-board' })
    mockArchiveSelectedBoard.mockResolvedValue(undefined)
    mockRecoverSelectedBoard.mockImplementation((candidate: string) => {
      storeState.selectedBoard = candidate || 'default'
      return { board: storeState.selectedBoard, recovered: false }
    })
    mockSetFilter.mockImplementation((key: 'status' | 'assignee', value: string | null) => {
      if (key === 'status') storeState.filterStatus = value
      else storeState.filterAssignee = value
    })
    mockDispatch.mockResolvedValue({ spawned: 1 })
    Object.defineProperty(document, 'visibilityState', {
      configurable: true,
      get: () => 'visible',
    })
  })

  it('initializes board from route query and refreshes stats alongside tasks', async () => {
    const wrapper = mount(KanbanView)
    await flushPromises()

    expect(mockFetchBoards).toHaveBeenCalledOnce()
    expect(mockFetchCapabilities).toHaveBeenCalledOnce()
    expect(mockFetchProfiles).toHaveBeenCalledOnce()
    expect(mockRecoverSelectedBoard).toHaveBeenCalledWith('project-a')
    expect(mockRefreshAll).toHaveBeenCalledOnce()
    expect(routerReplace).not.toHaveBeenCalled()
    expect(wrapper.findAll('.kanban-column')).toHaveLength(4)
    expect(wrapper.findAll('.kanban-column').map(column => column.attributes('data-column'))).toEqual([
      'queue',
      'waiting',
      'review',
      'done',
    ])
    expect(wrapper.find('[data-testid="kanban-inbox"]').exists()).toBe(true)
    expect(wrapper.find('.kanban-column[data-column="done"]').attributes('data-archived')).toBe('0')
    expect(wrapper.findAll('.kanban-column').map(column => column.attributes('data-collapsible'))).toEqual(['false', 'true', 'false', 'false'])
    expect(wrapper.find('.kanban-board').exists()).toBe(true)
    expect(wrapper.find('.kanban-columns').exists()).toBe(true)

    await wrapper.find('.drawer-updated').trigger('click')
    expect(mockFetchTasks).toHaveBeenCalledTimes(1)
    expect(mockFetchStats).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(15000)
    await flushPromises()

    expect(mockFetchBoards).toHaveBeenCalledTimes(2)
    expect(mockFetchTasks).toHaveBeenCalledTimes(2)
    expect(mockFetchStats).toHaveBeenCalledTimes(2)
  })

  it('renders board count labels and compact assignee profile labels', async () => {
    storeState.assignees = [{ name: 'alice', counts: { todo: 2, done: 1 } }]
    const wrapper = mount(KanbanView)
    await flushPromises()

    expect(wrapper.text()).toContain('kanban.title: Default · kanban.stats.tasks: 0')
    expect(wrapper.text()).toContain('kanban.title: Project A · kanban.stats.tasks: 2')
    const assigneeSelect = wrapper.findAll('.n-select-stub')[2]
    expect(assigneeSelect.text()).toContain('alice')
    expect(assigneeSelect.text()).not.toContain('default')
    expect(wrapper.text()).not.toContain('kanban.detail.assignee: alice')
    expect(wrapper.text()).not.toContain('alice · kanban.stats.tasks')
  })

  it('passes matching profile avatars to task cards', async () => {
    storeState.tasks = [{ id: 'task-1', title: 'Task one', status: 'todo', created_at: 10, assignee: 'alice' }]
    profilesState.profiles = [{ name: 'alice', avatar: { type: 'generated', seed: 'alice-seed' } }]

    const wrapper = mount(KanbanView)
    await flushPromises()

    expect(wrapper.find('.kanban-task-card-stub').attributes('data-avatar-seed')).toBe('alice-seed')
  })

  it('opens the drawer from a card and always keeps the Hermes workflow column order', async () => {
    const wrapper = mount(KanbanView)
    await flushPromises()

    expect(wrapper.findAll('.kanban-column').slice(0, 2).map(column => column.attributes('data-column'))).toEqual(['queue', 'waiting'])
    expect(wrapper.findAllComponents({ name: 'VueDraggable' })).toHaveLength(0)
    await wrapper.find('.kanban-task-card-stub').trigger('click')
    expect(wrapper.find('.drawer-updated').attributes('data-task-id')).toBe('task-1')
  })

  it('runs the bridged Hermes transition for a dropped card and reloads unsupported drops', async () => {
    storeState.tasks = [
      { id: 'task-1', title: 'Task one', status: 'ready', created_at: 10 },
      { id: 'task-2', title: 'Task two', status: 'done', created_at: 20 },
    ]
    const wrapper = mount(KanbanView)
    await flushPromises()
    const columns = wrapper.findAllComponents({ name: 'KanbanColumn' })
    const column = (id: string) => columns.find(item => (item.props('column') as any).id === id)!

    column('review').vm.$emit('dragStart', 'ready')
    await flushPromises()
    expect(wrapper.find('.kanban-board').classes()).toContain('dragging')
    expect(column('review').attributes('data-dragging')).toBe('ready')

    column('review').vm.$emit('dropped', { taskId: 'task-1', from: 'ready', toColumn: column('review').props('column'), index: 0 })
    column('review').vm.$emit('dragEnd')
    await flushPromises()
    expect(mockRequestReview).toHaveBeenCalledWith('task-1', undefined)
    expect(mockMessageSuccess).toHaveBeenCalledWith('kanban.message.reviewRequested')
    expect(wrapper.find('.kanban-board').classes()).not.toContain('dragging')

    column('done').vm.$emit('dropped', { taskId: 'task-1', from: 'ready', toColumn: column('done').props('column'), index: 0 })
    await flushPromises()
    expect(mockCompleteTasks).toHaveBeenCalledWith(['task-1'])

    column('queue').vm.$emit('dropped', { taskId: 'task-1', from: 'blocked', toColumn: column('queue').props('column'), index: 0 })
    await flushPromises()
    expect(mockUnblockTasks).toHaveBeenCalledWith(['task-1'])

    column('review').vm.$emit('dropped', { taskId: 'task-1', from: 'todo', toColumn: column('review').props('column'), index: 0 })
    await flushPromises()
    expect(mockFetchTasks).toHaveBeenCalledWith(true)
    expect(mockScheduleTask).not.toHaveBeenCalled()

    mockRequestReview.mockRejectedValueOnce(new Error('hermes said no'))
    mockFetchTasks.mockClear()
    column('review').vm.$emit('dropped', { taskId: 'task-1', from: 'ready', toColumn: column('review').props('column'), index: 0 })
    await flushPromises()
    expect(mockMessageError).toHaveBeenCalledWith('hermes said no')
    expect(mockFetchTasks).toHaveBeenCalledWith(true)
  })

  it('asks whether a card dropped on waiting is scheduled or blocked, then asks for the block reason', async () => {
    const wrapper = mount(KanbanView)
    await flushPromises()
    const waiting = wrapper.findAllComponents({ name: 'KanbanColumn' }).find(item => (item.props('column') as any).id === 'waiting')!

    waiting.vm.$emit('dropped', { taskId: 'task-1', from: 'ready', toColumn: waiting.props('column'), index: 0 })
    await flushPromises()
    const choice = wrapper.find('[data-testid="kanban-drop-choice"]')
    expect(choice.exists()).toBe(true)
    expect(choice.findAll('.n-button-stub').map(node => node.text())).toEqual(['kanban.board.waitingSchedule', 'kanban.board.waitingBlock'])
    expect(mockBlockTask).not.toHaveBeenCalled()
    expect(mockScheduleTask).not.toHaveBeenCalled()

    await choice.findAll('.n-button-stub')[1].trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="kanban-drop-choice"]').exists()).toBe(false)
    const modal = wrapper.find('.n-modal-stub')
    expect(modal.exists()).toBe(true)
    await modal.find('.n-input-stub').setValue('waiting on api key')
    await modal.findAll('.n-button-stub').find(node => node.text() === 'common.ok')!.trigger('click')
    await flushPromises()
    expect(mockBlockTask).toHaveBeenCalledWith('task-1', 'waiting on api key')

    waiting.vm.$emit('dropped', { taskId: 'task-1', from: 'ready', toColumn: waiting.props('column'), index: 0 })
    await flushPromises()
    await wrapper.find('[data-testid="kanban-drop-choice"]').findAll('.n-button-stub')[0].trigger('click')
    await flushPromises()
    expect(mockScheduleTask).toHaveBeenCalledWith('task-1', undefined)

    mockFetchTasks.mockClear()
    waiting.vm.$emit('dropped', { taskId: 'task-1', from: 'ready', toColumn: waiting.props('column'), index: 0 })
    await flushPromises()
    await wrapper.findAll('.n-modal-stub .n-button-stub').find(node => node.text() === 'common.cancel')!.trigger('click')
    await flushPromises()
    expect(mockFetchTasks).toHaveBeenCalledWith(true)
  })

  it('runs quick actions from cards: promote from the queue, archive from done with confirmation, specify opens the drawer', async () => {
    storeState.tasks = [
      { id: 'task-1', title: 'Task one', status: 'todo', created_at: 10 },
      { id: 'task-2', title: 'Task two', status: 'done', created_at: 20 },
      { id: 'task-3', title: 'Intake', status: 'triage', created_at: 30 },
    ]
    const wrapper = mount(KanbanView)
    await flushPromises()
    const queue = wrapper.findAllComponents({ name: 'KanbanColumn' }).find(item => (item.props('column') as any).id === 'queue')!

    queue.vm.$emit('taskAction', { taskId: 'task-1', action: 'promote' })
    await flushPromises()
    expect(mockPromoteTask).toHaveBeenCalledWith('task-1', undefined)

    queue.vm.$emit('taskAction', { taskId: 'task-2', action: 'archive' })
    await flushPromises()
    expect(mockDialogWarning).toHaveBeenCalledWith(expect.objectContaining({ title: 'kanban.action.archive' }))
    expect(mockArchiveTasks).not.toHaveBeenCalled()
    await mockDialogWarning.mock.calls[0][0].onPositiveClick()
    expect(mockArchiveTasks).toHaveBeenCalledWith(['task-2'])

    // Triage tasks live in the inbox strip, not in a column.
    expect(wrapper.find('[data-testid="kanban-inbox"] .inbox-count').text()).toBe('1')
    await wrapper.find('[data-testid="kanban-inbox"] .inbox-toggle').trigger('click')
    expect(wrapper.find('.inbox-card-stub').text()).toBe('Intake')
    queue.vm.$emit('taskAction', { taskId: 'task-3', action: 'specify' })
    await flushPromises()
    expect(wrapper.find('.drawer-updated').attributes('data-task-id')).toBe('task-3')
  })

  it('confirms before archiving a dropped card', async () => {
    const wrapper = mount(KanbanView)
    await flushPromises()
    // Archiving happens from the done card's quick action; a done card dropped on done is a reorder.
    const doneColumn = wrapper.findAllComponents({ name: 'KanbanColumn' }).find(column => (column.props('column') as any).id === 'done')!

    doneColumn.vm.$emit('taskAction', { taskId: 'task-2', action: 'archive' })
    await flushPromises()
    expect(mockDialogWarning).toHaveBeenCalledWith(expect.objectContaining({
      title: 'kanban.action.archive',
      content: 'kanban.action.archiveConfirm',
    }))
    expect(mockArchiveTasks).not.toHaveBeenCalled()

    await mockDialogWarning.mock.calls[0][0].onPositiveClick()
    expect(mockArchiveTasks).toHaveBeenCalledWith(['task-2'])
  })

  it('persists manual card order through the store and offers a reset', async () => {
    const wrapper = mount(KanbanView)
    await flushPromises()
    const queueColumn = wrapper.findAllComponents({ name: 'KanbanColumn' }).find(column => (column.props('column') as any).id === 'queue')!

    queueColumn.vm.$emit('reorder', ['task-9', 'task-1'])
    expect(mockSetCardOrder).toHaveBeenCalledWith('queue', ['task-9', 'task-1'])
    expect(wrapper.findAll('.n-button-stub').some(node => node.text() === 'kanban.dnd.resetLayout')).toBe(false)

    storeState.hasCustomLayout = true
    const customWrapper = mount(KanbanView)
    await flushPromises()
    const reset = customWrapper.findAll('.n-button-stub').find(node => node.text() === 'kanban.dnd.resetLayout')
    expect(reset).toBeDefined()
    expect(customWrapper.text()).toContain('kanban.dnd.layoutHint')
    await reset!.trigger('click')
    expect(mockResetLayout).toHaveBeenCalledOnce()
  })

  it('filters the visible board columns from stats chips', async () => {
    storeState.filterStatus = 'done'

    const wrapper = mount(KanbanView)
    await flushPromises()

    const columns = wrapper.findAll('.kanban-column')
    expect(wrapper.find('.kanban-board').classes()).toContain('filtered')
    expect(columns).toHaveLength(1)
    expect(columns[0].attributes('data-column')).toBe('done')
    expect(wrapper.find('[data-testid="kanban-inbox"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Task two')
    expect(wrapper.text()).not.toContain('Task one')

    await wrapper.find('.stat-chip.todo').trigger('click')
    await flushPromises()

    expect(mockSetFilter).toHaveBeenCalledWith('status', 'todo')
    expect(mockFetchTasks).toHaveBeenCalledTimes(1)

    await wrapper.find('.stat-chip.total').trigger('click')
    await flushPromises()

    expect(mockSetFilter).toHaveBeenCalledWith('status', null)
    expect(mockFetchTasks).toHaveBeenCalledTimes(2)
  })

  it('creates and archives boards from the board toolbar', async () => {
    storeState.selectedBoard = 'project-a'
    const wrapper = mount(KanbanView)
    await flushPromises()

    await wrapper.findAll('.n-button-stub')[0].trigger('click')
    await flushPromises()
    const inputs = wrapper.findAll('.n-input-stub')
    await inputs[0].setValue('new-board')
    await inputs[1].setValue('New Board')
    await wrapper.findAll('.n-button-stub').at(-1)!.trigger('click')
    await flushPromises()

    expect(mockCreateBoard).toHaveBeenCalledWith({ slug: 'new-board', name: 'New Board' })
    expect(routerReplace).toHaveBeenCalledWith({ query: { board: 'new-board' } })

    await wrapper.findAll('.n-button-stub')[1].trigger('click')
    await flushPromises()

    expect(mockDialogWarning).toHaveBeenCalledWith(expect.objectContaining({
      title: 'kanban.board.archive',
      content: 'kanban.board.archiveConfirm',
      positiveText: 'kanban.board.archive',
      negativeText: 'common.cancel',
    }))
    await mockDialogWarning.mock.calls[0][0].onPositiveClick()
    await flushPromises()

    expect(mockArchiveSelectedBoard).toHaveBeenCalled()
    expect(routerReplace).toHaveBeenCalledWith({ query: { board: 'default' } })
  })

  it('explains why the default board cannot be archived', async () => {
    storeState.selectedBoard = 'default'
    const wrapper = mount(KanbanView)
    await flushPromises()

    expect(wrapper.find('.n-tooltip-content').text()).toBe('kanban.board.defaultArchiveUnavailable')
    const archiveButton = wrapper.findAll('.n-button-stub')
      .find(node => node.text() === 'kanban.board.archive')
    expect(archiveButton?.attributes('disabled')).toBeDefined()
  })

  it('makes default board explicit when route query is absent', async () => {
    routeState.query = {}
    mockRecoverSelectedBoard.mockImplementation(() => {
      storeState.selectedBoard = 'default'
      return { board: 'default', recovered: false }
    })

    mount(KanbanView)
    await flushPromises()

    expect(routerReplace).toHaveBeenCalledWith({ query: { board: 'default' } })
    expect(mockRefreshAll).toHaveBeenCalledOnce()
  })
})
