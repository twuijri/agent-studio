<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { NButton, NSelect, NSpin, NModal, NInput, NTooltip, useDialog, useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import KanbanColumn from '@/components/hermes/kanban/KanbanColumn.vue'
import KanbanTaskCard, { type KanbanCardQuickAction } from '@/components/hermes/kanban/KanbanTaskCard.vue'
import KanbanTaskDrawer from '@/components/hermes/kanban/KanbanTaskDrawer.vue'
import KanbanCreateForm from '@/components/hermes/kanban/KanbanCreateForm.vue'
import { DEFAULT_KANBAN_BOARD, useKanbanStore } from '@/stores/hermes/kanban'
import { useProfilesStore } from '@/stores/hermes/profiles'
import { withDefaultAssignee } from '@/utils/hermes/kanban-assignees'
import { contentInputProps, technicalInputProps } from '@/utils/content-direction'
import {
  KANBAN_ARCHIVED_STATUS,
  KANBAN_BOARD_STATUSES,
  KANBAN_COLUMNS,
  KANBAN_INBOX_STATUS,
  kanbanColumnDropOptions,
  kanbanColumnForStatus,
  type KanbanColumnDef,
  type KanbanColumnDrop,
  type KanbanTransitionAction,
} from '@/utils/hermes/kanban-board'
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
// A drop that could mean more than one Hermes command (waiting = schedule or block).
const pendingChoice = ref<{ taskId: string; from: KanbanTaskStatus; options: KanbanColumnDrop[] } | null>(null)
const inboxOpen = ref(false)

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

const tasksByColumn = computed(() => {
  const grouped = {} as Record<KanbanColumnDef['id'], KanbanTask[]>
  for (const column of KANBAN_COLUMNS) {
    grouped[column.id] = kanbanStore.orderedTasksForColumn(column.id)
  }
  return grouped
})

const inboxTasks = computed(() => kanbanStore.tasksWithStatus(KANBAN_INBOX_STATUS))
const archivedTasks = computed(() => kanbanStore.tasksWithStatus(KANBAN_ARCHIVED_STATUS))

const activeFilterStatus = computed(() => {
  const status = kanbanStore.filterStatus as KanbanTaskStatus | null
  return status && boardStatuses.includes(status) ? status : null
})

// Columns always follow the Hermes workflow order; only cards move. A status
// filter narrows the board to the one column that shows that status.
const visibleColumns = computed<readonly KanbanColumnDef[]>(() => {
  const status = activeFilterStatus.value
  if (!status) return KANBAN_COLUMNS
  const columnId = kanbanColumnForStatus(status)
  return columnId ? KANBAN_COLUMNS.filter(column => column.id === columnId) : []
})

const inboxVisible = computed(() => !activeFilterStatus.value || activeFilterStatus.value === KANBAN_INBOX_STATUS)
// First fetch of a board: columns say "loading" instead of "no tasks".
const initialLoading = computed(() => kanbanStore.loading && kanbanStore.tasks.length === 0)
const isFiltered = computed(() => activeFilterStatus.value !== null)

watch(activeFilterStatus, (status) => {
  if (status === KANBAN_INBOX_STATUS) inboxOpen.value = true
})

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
  // Profiles only feed avatars; never let that request delay the first board load.
  if (profilesStore.profiles.length === 0) void profilesStore.fetchProfiles()
  await Promise.all([
    kanbanStore.fetchBoards(),
    kanbanStore.fetchCapabilities(),
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

function handleCardsReordered(column: KanbanColumnDef, ids: string[]) {
  kanbanStore.setCardOrder(column.id, ids)
}

async function handleCardAction(payload: { taskId: string; action: KanbanCardQuickAction }) {
  switch (payload.action) {
    case 'promote':
      await runTransition(payload.taskId, 'promote')
      break
    case 'archive':
      confirmArchive(payload.taskId)
      break
    case 'specify':
      // Specification needs the drawer's confirmation flow; open the task there.
      selectedTaskId.value = payload.taskId
      break
  }
}

function handleResetLayout() {
  kanbanStore.resetLayout()
}

/** Puts every column back to what the server reports after a cancelled or failed move. */
async function revertBoard() {
  await kanbanStore.fetchTasks(true)
}

const TRANSITION_TARGET: Record<KanbanTransitionAction, KanbanTaskStatus> = {
  complete: 'done',
  block: 'blocked',
  unblock: 'ready',
  promote: 'ready',
  schedule: 'scheduled',
  requestReview: 'review',
  reopenReview: 'todo',
  archive: 'archived',
}

async function runTransition(taskId: string, action: KanbanTransitionAction, note?: string, expected: KanbanTaskStatus = TRANSITION_TARGET[action]) {
  transitionBusy.value = true
  try {
    await kanbanStore.withPendingTransition(taskId, expected, () => applyTransition(taskId, action, note))
    message.success(t(TRANSITION_MESSAGE_KEY[action]))
  } catch (err: any) {
    message.error(err.message)
    await revertBoard()
  } finally {
    transitionBusy.value = false
  }
}

async function applyTransition(taskId: string, action: KanbanTransitionAction, note?: string) {
  {
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
  }
}

function confirmArchive(taskId: string, onCancel?: () => Promise<void> | void) {
  let decided = false
  dialog.warning({
    title: t('kanban.action.archive'),
    content: t('kanban.action.archiveConfirm'),
    positiveText: t('kanban.action.archive'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      decided = true
      await runTransition(taskId, 'archive')
    },
    onNegativeClick: async () => {
      decided = true
      await onCancel?.()
    },
    onClose: async () => {
      if (!decided) await onCancel?.()
    },
  })
}

async function applyDrop(taskId: string, from: KanbanTaskStatus, drop: KanbanColumnDrop) {
  const { transition, to } = drop
  const pending: PendingDrop = { taskId, from, to, action: transition.action }
  // The card already sits in its new column; keep it there while we ask.
  kanbanStore.beginTransition(taskId, to)
  if (transition.requiresReason) {
    dropReason.value = ''
    pendingDrop.value = pending
    return
  }
  if (transition.confirm) {
    confirmArchive(taskId, async () => {
      kanbanStore.endTransition(taskId)
      await revertBoard()
    })
    return
  }
  await runTransition(taskId, transition.action, undefined, to)
}

async function handleCardDropped(payload: { taskId: string; from: KanbanTaskStatus; toColumn: KanbanColumnDef }) {
  const options = kanbanColumnDropOptions(payload.from, payload.toColumn)
  if (options.length === 0) {
    await revertBoard()
    return
  }
  if (options.length > 1) {
    kanbanStore.beginTransition(payload.taskId, options[0].to)
    pendingChoice.value = { taskId: payload.taskId, from: payload.from, options }
    return
  }
  await applyDrop(payload.taskId, payload.from, options[0])
}

async function choosePendingDrop(drop: KanbanColumnDrop) {
  const choice = pendingChoice.value
  if (!choice) return
  pendingChoice.value = null
  await applyDrop(choice.taskId, choice.from, drop)
}

async function cancelPendingChoice() {
  if (!pendingChoice.value) return
  kanbanStore.endTransition(pendingChoice.value.taskId)
  pendingChoice.value = null
  await revertBoard()
}

const choiceModalVisible = computed({
  get: () => pendingChoice.value !== null,
  set: (visible: boolean) => {
    if (!visible) void cancelPendingChoice()
  },
})

function choiceLabel(drop: KanbanColumnDrop): string {
  if (drop.transition.action === 'schedule') return t('kanban.board.waitingSchedule')
  if (drop.transition.action === 'block') return t('kanban.board.waitingBlock')
  return t(`kanban.columns.${drop.to}`, drop.to)
}

async function confirmPendingDrop() {
  const drop = pendingDrop.value
  const reason = dropReason.value.trim()
  if (!drop || !reason) return
  pendingDrop.value = null
  await runTransition(drop.taskId, drop.action, reason, drop.to)
}

async function cancelPendingDrop() {
  if (!pendingDrop.value) return
  kanbanStore.endTransition(pendingDrop.value.taskId)
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

// Vertical mouse-wheel anywhere on the board scrolls the columns sideways,
// unless the pointer is over a card list that can still scroll vertically.
// Horizontal wheel/trackpad gestures are left to the browser (the lists let
// them chain to the board), so sideways swipes work from any spot.
function handleBoardWheel(event: WheelEvent) {
  if (event.deltaX !== 0 || event.deltaY === 0 || event.shiftKey || event.ctrlKey) return
  const target = event.target as HTMLElement | null
  const list = target?.closest<HTMLElement>('.task-list')
  if (list && list.scrollHeight > list.clientHeight) return
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
      :show="false"
    >
      <div
        class="kanban-board"
        :class="{ filtered: isFiltered, dragging: draggingStatus !== null }"
        :data-busy="transitionBusy ? 'true' : 'false'"
        data-testid="kanban-board"
        @wheel="handleBoardWheel"
      >
        <div class="kanban-columns">
          <!-- Intake strip: triage tasks are specified from the drawer, never dragged. -->
          <aside
            v-if="inboxVisible"
            class="kanban-inbox"
            :class="{ open: inboxOpen }"
            data-testid="kanban-inbox"
            :aria-label="t('kanban.board.columns.inbox')"
          >
            <button type="button" class="inbox-toggle" :aria-expanded="inboxOpen" @click="inboxOpen = !inboxOpen">
              <span class="inbox-count">{{ inboxTasks.length }}</span>
              <span class="inbox-title">{{ t('kanban.board.columns.inbox') }}</span>
            </button>
            <div v-if="inboxOpen" class="inbox-body">
              <p class="inbox-hint">{{ t('kanban.board.inboxHint') }}</p>
              <div v-if="inboxTasks.length === 0" class="inbox-empty">{{ initialLoading ? t('kanban.board.loadingTasks') : t('kanban.noTasks') }}</div>
              <KanbanTaskCard
                v-for="task in inboxTasks"
                :key="task.id"
                :task="task"
                :assignee-avatar="task.assignee ? profileAvatarByName[task.assignee] || null : null"
                @click="handleTaskClick"
                @action="handleCardAction"
              />
            </div>
          </aside>
          <KanbanColumn
            v-for="column in visibleColumns"
            :key="column.id"
            :column="column"
            :tasks="tasksByColumn[column.id]"
            :archived-tasks="column.id === 'done' ? archivedTasks : undefined"
            :avatars="profileAvatarByName"
            :dragging-status="draggingStatus"
            :drag-disabled="transitionBusy"
            :pending-task-ids="kanbanStore.pendingTransitions"
            :loading="initialLoading"
            :collapsible="column.id === 'waiting'"
            @task-click="handleTaskClick"
            @task-action="handleCardAction"
            @reorder="ids => handleCardsReordered(column, ids)"
            @dropped="handleCardDropped"
            @drag-start="handleDragStart"
            @drag-end="handleDragEnd"
          />
        </div>
      </div>
    </NSpin>

    <!-- Task detail drawer -->
    <KanbanTaskDrawer
      :task-id="selectedTaskId"
      @close="handleDrawerClose"
      @updated="handleDrawerUpdated"
      @navigate="handleNavigateTask"
    />

    <!-- Waiting column: the drop can mean schedule or block; let the user pick -->
    <NModal v-model:show="choiceModalVisible" preset="dialog" :title="t('kanban.board.waitingKindTitle')" style="width: 420px;">
      <div class="board-form choice-form" data-testid="kanban-drop-choice">
        <NButton
          v-for="drop in pendingChoice?.options || []"
          :key="drop.to"
          class="choice-button"
          :data-choice="drop.transition.action"
          @click="choosePendingDrop(drop)"
        >
          {{ choiceLabel(drop) }}
        </NButton>
      </div>
      <template #action>
        <NButton @click="cancelPendingChoice">{{ t('common.cancel') }}</NButton>
      </template>
    </NModal>

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

.board-form {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.choice-form .choice-button {
  justify-content: flex-start;
}

.kanban-inbox {
  --kanban-status-color: #8b8f95;
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  width: 44px;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  border: 1px solid $border-light;
  border-radius: $radius-md;
  background: color-mix(in srgb, $bg-secondary 66%, $bg-card);
  transition: width $transition-normal;

  &.open {
    width: var(--kanban-column-width, 300px);
  }
}

.inbox-toggle {
  appearance: none;
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
  min-height: 43px;
  padding: 10px 11px;
  border: 0;
  border-bottom: 1px solid $border-light;
  background: transparent;
  color: $text-primary;
  font: inherit;
  font-size: 12.5px;
  font-weight: 600;
  text-align: start;
  cursor: pointer;

  .kanban-inbox:not(.open) & {
    flex: 1;
    flex-direction: column;
    justify-content: flex-start;
    border-bottom: 0;
  }
}

.kanban-inbox:not(.open) .inbox-title {
  writing-mode: vertical-rl;
  color: $text-muted;
  font-weight: 500;
}

.inbox-count {
  min-width: 22px;
  padding: 2px 6px;
  border: 1px solid $border-light;
  border-radius: 999px;
  color: $text-muted;
  font-size: 10.5px;
  font-weight: 500;
  line-height: 1.2;
  text-align: center;
}

.inbox-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
  padding: 9px;
  overflow-y: auto;
  overscroll-behavior-y: contain;
  overscroll-behavior-x: auto;
}

.inbox-hint,
.inbox-empty {
  margin: 0;
  font-size: 12px;
  color: $text-muted;
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
