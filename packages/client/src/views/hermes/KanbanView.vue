<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { NButton, NSelect, NSpin, NModal, NInput, NTooltip, useDialog, useMessage } from 'naive-ui'
import { VueDraggable } from 'vue-draggable-plus'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import KanbanColumn from '@/components/hermes/kanban/KanbanColumn.vue'
import KanbanTaskDrawer from '@/components/hermes/kanban/KanbanTaskDrawer.vue'
import KanbanCreateForm from '@/components/hermes/kanban/KanbanCreateForm.vue'
import { DEFAULT_KANBAN_BOARD, useKanbanStore } from '@/stores/hermes/kanban'
import { useProfilesStore } from '@/stores/hermes/profiles'
import { withDefaultAssignee } from '@/utils/hermes/kanban-assignees'
import { contentInputProps, technicalInputProps } from '@/utils/content-direction'
import { KANBAN_BOARD_STATUSES, resolveKanbanTransition, type KanbanTransitionAction } from '@/utils/hermes/kanban-board'
import type { KanbanTask, KanbanTaskStatus } from '@/api/hermes/kanban'
import type { ProfileAvatar } from '@/api/hermes/profiles'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const kanbanStore = useKanbanStore()
const profilesStore = useProfilesStore()

const showCreateForm = ref(false)
const showCreateBoardForm = ref(false)
const selectedTaskId = ref<string | null>(null)
const newBoardSlug = ref('')
const newBoardName = ref('')
const boardActionLoading = ref(false)
const refreshTimer = ref<ReturnType<typeof setInterval> | null>(null)
const routeReady = ref(false)

const boardStatuses = KANBAN_BOARD_STATUSES

interface PendingDrop {
  taskId: string
  from: KanbanTaskStatus
  to: KanbanTaskStatus
  action: KanbanTransitionAction
}

const draggingStatus = ref<KanbanTaskStatus | null>(null)
const transitionBusy = ref(false)
const pendingDrop = ref<PendingDrop | null>(null)
const dropReason = ref('')

const TRANSITION_MESSAGE_KEY: Record<KanbanTransitionAction, string> = {
  complete: 'kanban.message.taskCompleted',
  block: 'kanban.message.taskBlocked',
  unblock: 'kanban.message.taskUnblocked',
  promote: 'kanban.message.taskPromoted',
  schedule: 'kanban.message.taskScheduled',
  requestReview: 'kanban.message.reviewRequested',
  reopenReview: 'kanban.message.reviewReopened',
  archive: 'kanban.message.taskArchived',
}

function firstQueryString(value: unknown): string | null {
  if (Array.isArray(value)) return typeof value[0] === 'string' ? value[0] : null
  return typeof value === 'string' ? value : null
}

function routeBoard(): string | null {
  return firstQueryString(route.query.board)
}

async function replaceRouteBoard(board: string) {
  if (routeBoard() === board) return
  await router.replace({ query: { ...route.query, board } })
}

async function applyBoardSelection(candidate: string | null, notify = true, forceRefresh = false) {
  const previousBoard = kanbanStore.selectedBoard
  const { board, recovered } = kanbanStore.recoverSelectedBoard(candidate || kanbanStore.selectedBoard || DEFAULT_KANBAN_BOARD)
  selectedTaskId.value = null
  showCreateForm.value = false
  showCreateBoardForm.value = false
  if (notify && recovered && kanbanStore.boardWarning) message.warning(kanbanStore.boardWarning)
  await replaceRouteBoard(board)
  if (forceRefresh || board !== previousBoard) {
    await kanbanStore.refreshAll()
  }
}

function taskCountLabel(count: number): string {
  return `${t('kanban.stats.tasks')}: ${count}`
}

const boardOptions = computed(() => kanbanStore.activeBoards.map(board => {
  const count = typeof board.total === 'number' ? board.total : 0
  return {
    label: `${t('kanban.title')}: ${board.icon ? `${board.icon} ` : ''}${board.name || board.slug} · ${taskCountLabel(count)}`,
    value: board.slug,
  }
}))

const selectedBoardValue = computed({
  get: () => kanbanStore.selectedBoard,
  set: (value: string) => {
    void applyBoardSelection(value || DEFAULT_KANBAN_BOARD)
  },
})

const tasksByStatus = computed(() => {
  const grouped: Record<string, KanbanTask[]> = {}
  for (const status of boardStatuses) {
    grouped[status] = kanbanStore.orderedTasksForStatus(status)
  }
  return grouped
})

const visibleBoardStatuses = computed(() => {
  const status = kanbanStore.filterStatus as KanbanTaskStatus | null
  return status && boardStatuses.includes(status) ? [status] : kanbanStore.columnOrder
})

// Sortable mutates the bound array, so the column order lives in a local copy
// that is written back to the store (and the browser) after each drag.
const columns = ref<KanbanTaskStatus[]>([...visibleBoardStatuses.value])
watch(visibleBoardStatuses, (next) => {
  columns.value = [...next]
})

const isFiltered = computed(() => visibleBoardStatuses.value.length === 1)

const visibleAssignees = computed(() => withDefaultAssignee(kanbanStore.assignees, kanbanStore.stats?.by_assignee || {}))

const profileAvatarByName = computed<Record<string, ProfileAvatar | null>>(() => {
  return Object.fromEntries(profilesStore.profiles.map(profile => [profile.name, profile.avatar || null]))
})

const statusFilterOptions = computed(() => [
  { label: t('kanban.allStatuses'), value: '' },
  ...boardStatuses.map(s => ({ label: t(`kanban.columns.${s}`, s), value: s })),
])

const assigneeFilterOptions = computed(() => [
  { label: t('kanban.allAssignees'), value: '' },
  ...visibleAssignees.value.map(a => ({ label: a.name, value: a.name })),
])

const filterStatusValue = computed({
  get: () => kanbanStore.filterStatus || '',
  set: (v: string) => kanbanStore.setFilter('status', v || null),
})

const filterAssigneeValue = computed({
  get: () => kanbanStore.filterAssignee || '',
  set: (v: string) => kanbanStore.setFilter('assignee', v || null),
})

watch(() => route.query.board, async () => {
  if (!routeReady.value) return
  await applyBoardSelection(routeBoard(), false)
})

onMounted(async () => {
  await Promise.all([
    kanbanStore.fetchBoards(),
    kanbanStore.fetchCapabilities(),
    profilesStore.profiles.length === 0 ? profilesStore.fetchProfiles() : Promise.resolve(),
  ])
  await applyBoardSelection(routeBoard(), true, true)
  kanbanStore.startEventStream()
  routeReady.value = true
  refreshTimer.value = setInterval(() => {
    if (document.visibilityState === 'visible') {
      void Promise.all([kanbanStore.fetchBoards(), kanbanStore.fetchTasks(true), kanbanStore.fetchStats()])
    }
  }, 15000)
})

onUnmounted(() => {
  kanbanStore.stopEventStream()
  if (refreshTimer.value) clearInterval(refreshTimer.value)
})

function handleTaskClick(taskId: string) {
  selectedTaskId.value = taskId
}

function handleDrawerClose() {
  selectedTaskId.value = null
}

async function handleDrawerUpdated() {
  await Promise.all([kanbanStore.fetchTasks(), kanbanStore.fetchStats()])
}

function handleNavigateTask(taskId: string) {
  selectedTaskId.value = taskId
}

async function handleApplyFilter() {
  await kanbanStore.fetchTasks()
}

async function handleStatusChipClick(status: KanbanTaskStatus | null) {
  kanbanStore.setFilter('status', status)
  await kanbanStore.fetchTasks()
}

async function handleTaskCreated() {
  await Promise.all([kanbanStore.fetchTasks(), kanbanStore.fetchStats(), kanbanStore.fetchBoards()])
}

// ─── Drag and drop ───────────────────────────────────────────────

function handleDragStart(status: KanbanTaskStatus) {
  draggingStatus.value = status
}

function handleDragEnd() {
  draggingStatus.value = null
}

function handleColumnsReordered() {
  if (isFiltered.value) return
  kanbanStore.setColumnOrder([...columns.value])
}

function handleCardsReordered(status: KanbanTaskStatus, ids: string[]) {
  kanbanStore.setCardOrder(status, ids)
}

function handleResetLayout() {
  kanbanStore.resetLayout()
}

/** Puts every column back to what the server reports after a cancelled or failed move. */
async function revertBoard() {
  await kanbanStore.fetchTasks(true)
}

async function runTransition(taskId: string, action: KanbanTransitionAction, note?: string) {
  transitionBusy.value = true
  try {
    switch (action) {
      case 'complete':
        await kanbanStore.completeTasks([taskId])
        break
      case 'block':
        await kanbanStore.blockTask(taskId, note || '')
        break
      case 'unblock':
        await kanbanStore.unblockTasks([taskId])
        break
      case 'promote':
        await kanbanStore.promoteTask(taskId, note)
        break
      case 'schedule':
        await kanbanStore.scheduleTask(taskId, note)
        break
      case 'requestReview':
        await kanbanStore.requestReview(taskId, note)
        break
      case 'reopenReview':
        await kanbanStore.reopenReview(taskId, note)
        break
      case 'archive':
        await kanbanStore.archiveTasks([taskId])
        break
    }
    message.success(t(TRANSITION_MESSAGE_KEY[action]))
  } catch (err: any) {
    message.error(err.message)
    await revertBoard()
  } finally {
    transitionBusy.value = false
  }
}

async function handleCardDropped(payload: { taskId: string; from: KanbanTaskStatus; to: KanbanTaskStatus }) {
  const transition = resolveKanbanTransition(payload.from, payload.to)
  if (!transition) {
    await revertBoard()
    return
  }
  const drop: PendingDrop = { ...payload, action: transition.action }
  if (transition.requiresReason) {
    dropReason.value = ''
    pendingDrop.value = drop
    return
  }
  if (transition.confirm) {
    let decided = false
    dialog.warning({
      title: t('kanban.action.archive'),
      content: t('kanban.action.archiveConfirm'),
      positiveText: t('kanban.action.archive'),
      negativeText: t('common.cancel'),
      onPositiveClick: async () => {
        decided = true
        await runTransition(drop.taskId, drop.action)
      },
      onNegativeClick: async () => {
        decided = true
        await revertBoard()
      },
      onClose: async () => {
        if (!decided) await revertBoard()
      },
    })
    return
  }
  await runTransition(drop.taskId, drop.action)
}

async function confirmPendingDrop() {
  const drop = pendingDrop.value
  const reason = dropReason.value.trim()
  if (!drop || !reason) return
  pendingDrop.value = null
  await runTransition(drop.taskId, drop.action, reason)
}

async function cancelPendingDrop() {
  if (!pendingDrop.value) return
  pendingDrop.value = null
  dropReason.value = ''
  await revertBoard()
}

const reasonModalVisible = computed({
  get: () => pendingDrop.value !== null,
  set: (visible: boolean) => {
    if (!visible) void cancelPendingDrop()
  },
})

// Vertical wheel over the board background scrolls the columns sideways so
// mouse users can reach every column without a horizontal scrollbar.
function handleBoardWheel(event: WheelEvent) {
  if (event.deltaX !== 0 || event.deltaY === 0 || event.shiftKey) return
  const target = event.target as HTMLElement | null
  if (target?.closest('.task-list')) return
  const board = event.currentTarget as HTMLElement
  if (board.scrollWidth <= board.clientWidth) return
  event.preventDefault()
  const direction = document.documentElement.dir === 'rtl' ? -1 : 1
  board.scrollLeft += event.deltaY * direction
}

async function handleCreateBoard() {
  const slug = newBoardSlug.value.trim()
  if (!slug) {
    message.warning(t('kanban.board.slugRequired'))
    return
  }
  boardActionLoading.value = true
  try {
    const board = await kanbanStore.createBoard({
      slug,
      name: newBoardName.value.trim() || undefined,
    })
    newBoardSlug.value = ''
    newBoardName.value = ''
    showCreateBoardForm.value = false
    await replaceRouteBoard(board.slug)
    message.success(t('kanban.board.created'))
  } catch (err: any) {
    message.error(err.message)
  } finally {
    boardActionLoading.value = false
  }
}

function handleArchiveSelectedBoard() {
  if (kanbanStore.selectedBoard === DEFAULT_KANBAN_BOARD) return
  const board = kanbanStore.selectedBoard
  dialog.warning({
    title: t('kanban.board.archive'),
    content: t('kanban.board.archiveConfirm'),
    positiveText: t('kanban.board.archive'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      if (kanbanStore.selectedBoard !== board) return
      boardActionLoading.value = true
      try {
        await kanbanStore.archiveSelectedBoard()
        await replaceRouteBoard(DEFAULT_KANBAN_BOARD)
        message.success(t('kanban.board.archived'))
      } catch (err: any) {
        message.error(err.message)
      } finally {
        boardActionLoading.value = false
      }
    },
  })
}

async function handleDispatch() {
  boardActionLoading.value = true
  try {
    await kanbanStore.dispatch()
    await kanbanStore.refreshAll()
    message.success(t('kanban.message.dispatchNudged'))
  } catch (err: any) {
    message.error(err.message)
  } finally {
    boardActionLoading.value = false
  }
}
</script>

<template>
  <div class="kanban-view">
    <header class="page-header">
      <h2 class="header-title">{{ t('kanban.title') }}</h2>
      <div class="header-actions">
        <NSelect
          v-model:value="selectedBoardValue"
          :options="boardOptions"
          :loading="kanbanStore.boardsLoading"
          size="small"
          style="width: 260px;"
        />
        <NButton size="small" :loading="boardActionLoading" @click="showCreateBoardForm = true">
          {{ t('common.add') }}
        </NButton>
        <NTooltip trigger="hover" :disabled="kanbanStore.selectedBoard !== DEFAULT_KANBAN_BOARD">
          <template #trigger>
            <span class="archive-board-trigger">
              <NButton
                size="small"
                secondary
                :disabled="kanbanStore.selectedBoard === DEFAULT_KANBAN_BOARD"
                :loading="boardActionLoading"
                @click="handleArchiveSelectedBoard"
              >
                {{ t('kanban.board.archive') }}
              </NButton>
            </span>
          </template>
          {{ t('kanban.board.defaultArchiveUnavailable') }}
        </NTooltip>
        <NButton size="small" secondary :loading="boardActionLoading" @click="handleDispatch">
          {{ t('kanban.action.dispatch') }}
        </NButton>
        <NSelect
          v-model:value="filterStatusValue"
          :options="statusFilterOptions"
          size="small"
          style="width: 150px;"
          @update:value="handleApplyFilter"
        />
        <NSelect
          v-model:value="filterAssigneeValue"
          :options="assigneeFilterOptions"
          size="small"
          style="width: 170px;"
          @update:value="handleApplyFilter"
        />
        <NTooltip v-if="kanbanStore.hasCustomLayout" trigger="hover">
          <template #trigger>
            <NButton size="small" tertiary class="reset-layout-button" @click="handleResetLayout">
              {{ t('kanban.dnd.resetLayout') }}
            </NButton>
          </template>
          {{ t('kanban.dnd.layoutHint') }}
        </NTooltip>
        <NButton type="primary" size="small" @click="showCreateForm = true">
          <template #icon>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          </template>
          {{ t('kanban.createTask') }}
        </NButton>
      </div>
    </header>

    <!-- Stats bar -->
    <div v-if="kanbanStore.stats" class="stats-bar">
      <button
        type="button"
        class="stat-chip total"
        :class="{ active: !kanbanStore.filterStatus }"
        :aria-pressed="!kanbanStore.filterStatus"
        @click="handleStatusChipClick(null)"
      >
        <span class="stat-count">{{ kanbanStore.stats.total }}</span>
        <span class="stat-label">{{ t('kanban.stats.total') }}</span>
      </button>
      <button
        v-for="status in boardStatuses"
        :key="status"
        type="button"
        class="stat-chip"
        :class="[status, { active: kanbanStore.filterStatus === status }]"
        :aria-pressed="kanbanStore.filterStatus === status"
        @click="handleStatusChipClick(status)"
      >
        <span class="stat-indicator" aria-hidden="true" />
        <span class="stat-count">{{ kanbanStore.stats.by_status[status] || 0 }}</span>
        <span class="stat-label">{{ t(`kanban.columns.${status}`, status) }}</span>
      </button>
    </div>

    <!-- Board -->
    <NSpin
      class="kanban-board-spin"
      :show="kanbanStore.loading && kanbanStore.tasks.length === 0"
    >
      <div
        class="kanban-board"
        :class="{ filtered: isFiltered, dragging: draggingStatus !== null }"
        :data-busy="transitionBusy ? 'true' : 'false'"
        data-testid="kanban-board"
        @wheel="handleBoardWheel"
      >
        <VueDraggable
          v-model="columns"
          class="kanban-columns"
          handle=".column-header"
          :animation="150"
          :force-fallback="true"
          :fallback-tolerance="6"
          :disabled="isFiltered || transitionBusy"
          ghost-class="kanban-column-ghost"
          @update="handleColumnsReordered"
        >
          <KanbanColumn
            v-for="status in columns"
            :key="status"
            :status="status"
            :tasks="tasksByStatus[status]"
            :avatars="profileAvatarByName"
            :dragging-status="draggingStatus"
            :drag-disabled="transitionBusy"
            @task-click="handleTaskClick"
            @reorder="ids => handleCardsReordered(status, ids)"
            @dropped="handleCardDropped"
            @drag-start="handleDragStart"
            @drag-end="handleDragEnd"
          />
        </VueDraggable>
      </div>
    </NSpin>

    <!-- Task detail drawer -->
    <KanbanTaskDrawer
      :task-id="selectedTaskId"
      @close="handleDrawerClose"
      @updated="handleDrawerUpdated"
      @navigate="handleNavigateTask"
    />

    <!-- Reason prompt for drops that Hermes requires a reason for (block) -->
    <NModal v-model:show="reasonModalVisible" preset="dialog" :title="t('kanban.action.block')" style="width: 420px;">
      <div class="board-form">
        <NInput
          v-model:value="dropReason"
          :input-props="contentInputProps"
          :placeholder="t('kanban.action.blockReason')"
          data-testid="kanban-drop-reason"
          @keyup.enter="confirmPendingDrop"
        />
      </div>
      <template #action>
        <NButton @click="cancelPendingDrop">{{ t('common.cancel') }}</NButton>
        <NButton type="primary" :disabled="!dropReason.trim()" :loading="transitionBusy" @click="confirmPendingDrop">
          {{ t('common.ok') }}
        </NButton>
      </template>
    </NModal>

    <!-- Board management -->
    <NModal v-model:show="showCreateBoardForm" preset="dialog" :title="t('kanban.board.create')" style="width: 420px;">
      <div class="board-form">
        <NInput v-model:value="newBoardSlug" :input-props="technicalInputProps" :placeholder="t('kanban.board.slugPlaceholder')" />
        <NInput v-model:value="newBoardName" :input-props="contentInputProps" :placeholder="t('kanban.board.namePlaceholder')" />
      </div>
      <template #action>
        <NButton @click="showCreateBoardForm = false">{{ t('common.cancel') }}</NButton>
        <NButton type="primary" :loading="boardActionLoading" @click="handleCreateBoard">{{ t('common.create') }}</NButton>
      </template>
    </NModal>

    <!-- Create form -->
    <KanbanCreateForm
      v-if="showCreateForm"
      @close="showCreateForm = false"
      @created="handleTaskCreated"
    />
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.kanban-view {
  height: calc(100 * var(--vh));
  display: flex;
  flex-direction: column;
}

.archive-board-trigger {
  display: inline-flex;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.stats-bar {
  display: flex;
  gap: 4px;
  padding: 8px 20px;
  overflow-x: auto;
  overscroll-behavior-inline: contain;
  border-bottom: 1px solid $border-light;
  flex-shrink: 0;
  flex-wrap: nowrap;
  scrollbar-width: none;
  -webkit-overflow-scrolling: touch;

  &::-webkit-scrollbar {
    display: none;
  }
}

.stat-chip {
  --kanban-status-color: #7f858d;
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 5px;
  padding: 5px 8px;
  border-radius: $radius-sm;
  font-size: 12px;
  border: 1px solid transparent;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font: inherit;
  line-height: inherit;
  white-space: nowrap;

  &.triage { --kanban-status-color: #8b8f95; }
  &.todo { --kanban-status-color: #6f7782; }
  &.scheduled { --kanban-status-color: #667681; }
  &.ready { --kanban-status-color: #a66d23; }
  &.running { --kanban-status-color: var(--accent-info); }
  &.blocked { --kanban-status-color: var(--error); }
  &.review { --kanban-status-color: #7b6f8b; }
  &.done { --kanban-status-color: var(--success); }
  &.archived { --kanban-status-color: #777b81; }
  &.total { --kanban-status-color: $text-muted; }

  &:hover,
  &.active {
    border-color: $border-color;
    background-color: $bg-secondary;
  }

  &:focus-visible {
    outline: 2px solid $accent-primary;
    outline-offset: 2px;
  }
}

.stat-indicator {
  width: 5px;
  height: 5px;
  flex: 0 0 auto;
  border-radius: 999px;
  background: var(--kanban-status-color);
}

.stat-count {
  font-weight: 600;
  color: $text-primary;
}

.stat-label {
  color: $text-muted;
}

.kanban-board-spin {
  flex: 1;
  min-height: 0;
  overflow: hidden;

  :deep(.n-spin-container),
  :deep(.n-spin-content) {
    height: 100%;
    min-height: 0;
  }

  :deep(.n-spin-content) {
    display: flex;
    flex-direction: column;
  }
}

.kanban-board {
  --kanban-column-width: 300px;
  flex: 1;
  height: 100%;
  min-height: 0;
  overflow-x: auto;
  overflow-y: hidden;
  overscroll-behavior-inline: contain;
  background: $bg-primary;
  scrollbar-width: thin;

  &.filtered {
    --kanban-column-width: min(520px, 100%);
  }
}

.kanban-columns {
  display: flex;
  align-items: stretch;
  gap: 16px;
  box-sizing: border-box;
  width: max-content;
  min-width: 100%;
  height: 100%;
  padding: 16px 20px;
}

.kanban-column-ghost {
  opacity: 0.4;
}

.board-form {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

@media (max-width: $breakpoint-mobile) {
  .page-header {
    padding: 16px 12px 16px 52px;
    position: sticky;
    top: 0;
    z-index: 20;
    flex-direction: column;
    align-items: flex-start;
    background: $bg-primary;
    gap: 10px;
  }

  .header-actions {
    flex-wrap: wrap;
    width: 100%;
  }

  .stats-bar {
    gap: 6px;
    padding-inline: 12px;
  }

  .kanban-board {
    --kanban-column-width: min(280px, calc(100vw - 40px));
    scroll-snap-type: x proximity;
  }

  .kanban-columns {
    gap: 12px;
    padding: 12px;
  }

  .kanban-columns > :deep(.kanban-column) {
    scroll-snap-align: start;
  }
}
</style>
