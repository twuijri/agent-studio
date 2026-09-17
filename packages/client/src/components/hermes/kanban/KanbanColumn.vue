<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { VueDraggable, type DraggableEvent } from 'vue-draggable-plus'
import KanbanTaskCard, { type KanbanCardQuickAction } from './KanbanTaskCard.vue'
import { type KanbanColumnDef, isKanbanColumnDropTarget, kanbanColumnById, isKanbanColumnId } from '@/utils/hermes/kanban-board'
import type { KanbanTask, KanbanTaskStatus } from '@/api/hermes/kanban'
import type { ProfileAvatar } from '@/api/hermes/profiles'

const props = defineProps<{
  column: KanbanColumnDef
  tasks: KanbanTask[]
  /** Archived tasks shown under the done column behind a toggle. */
  archivedTasks?: KanbanTask[]
  avatars?: Record<string, ProfileAvatar | null>
  /** Status of the card currently being dragged anywhere on the board, or null. */
  draggingStatus?: KanbanTaskStatus | null
  /** Disables dragging cards out of (and into) this column, e.g. while a transition runs. */
  dragDisabled?: boolean
  /** Collapse to a narrow strip while empty; expands for drops and on click. */
  collapsible?: boolean
  /** Tasks whose Hermes command is still running; shown with a saving marker and not draggable. */
  pendingTaskIds?: Record<string, unknown>
  /** First board load still running: show a loading hint instead of "no tasks". */
  loading?: boolean
}>()

const emit = defineEmits<{
  taskClick: [taskId: string]
  taskAction: [payload: { taskId: string; action: KanbanCardQuickAction }]
  reorder: [ids: string[]]
  dropped: [payload: { taskId: string; from: KanbanTaskStatus; toColumn: KanbanColumnDef; index: number }]
  dragStart: [status: KanbanTaskStatus]
  dragEnd: []
}>()

const { t } = useI18n()

// Sortable mutates the bound list directly, so keep a local copy. Store updates
// that arrive mid-drag are deferred until the drag ends; a drop keeps its
// optimistic position until the store answers (every drop triggers a refresh).
const localTasks = ref<KanbanTask[]>([...props.tasks])
let syncPending = false

function syncFromProps() {
  syncPending = false
  localTasks.value = [...props.tasks]
}

watch(() => props.tasks, () => {
  if (props.draggingStatus) syncPending = true
  else syncFromProps()
})

watch(() => props.draggingStatus, (dragging) => {
  if (!dragging && syncPending) syncFromProps()
})

const columnLabel = computed(() => t(`kanban.board.columns.${props.column.id}`, props.column.id))
const dropBlocked = computed(() => !!props.draggingStatus && !isKanbanColumnDropTarget(props.draggingStatus, props.column))
const dropOpen = computed(() => !!props.draggingStatus && !props.column.statuses.includes(props.draggingStatus) && !dropBlocked.value)

const showArchived = ref(false)
const archivedCount = computed(() => props.archivedTasks?.length || 0)

// A collapsible column only takes a full slot when it has cards, when a card
// that may land here is being dragged, or after the user opened it by hand.
const expandedByUser = ref(false)
const collapsed = computed(() => !!props.collapsible && localTasks.value.length === 0 && !dropOpen.value && !expandedByUser.value)

function toggleExpanded() {
  if (!props.collapsible) return
  expandedByUser.value = !expandedByUser.value
}

function columnOf(element: HTMLElement | null | undefined): KanbanColumnDef | null {
  const value = element?.dataset?.column
  return isKanbanColumnId(value) ? kanbanColumnById(value) : null
}

function statusOf(element: HTMLElement | null | undefined): KanbanTaskStatus | null {
  const value = element?.dataset?.status
  return value ? value as KanbanTaskStatus : null
}

function canMove(event: { from: HTMLElement; to: HTMLElement; dragged: HTMLElement }): boolean {
  const from = statusOf(event.dragged)
  const to = columnOf(event.to)
  if (!from || !to) return false
  return isKanbanColumnDropTarget(from, to)
}

function handleStart(event: DraggableEvent<KanbanTask>) {
  const status = statusOf(event.item) || event.data?.status
  if (status) emit('dragStart', status)
}

function handleEnd() {
  emit('dragEnd')
}

function handleUpdate() {
  emit('reorder', localTasks.value.map(task => task.id))
}

function pointerColumn(event: DraggableEvent<KanbanTask>): KanbanColumnDef | null {
  const original = (event as { originalEvent?: MouseEvent | TouchEvent }).originalEvent
  if (!original || typeof document.elementFromPoint !== 'function') return null
  const point = 'changedTouches' in original ? original.changedTouches[0] : original
  if (!point) return null
  const column = document.elementFromPoint(point.clientX, point.clientY)?.closest<HTMLElement>('.kanban-column')
  return columnOf(column)
}

function handleAdd(event: DraggableEvent<KanbanTask>) {
  const task = event.data
  const from = statusOf(event.item) || task?.status
  if (!from || !task) return
  // Sortable leaves the card in the last column that accepted it, so a drop over
  // a refused column would silently land elsewhere. Report the column under the
  // pointer instead; the board reverts when that is not a valid transition.
  const toColumn = pointerColumn(event) || props.column
  emit('dropped', { taskId: task.id, from, toColumn, index: event.newIndex ?? 0 })
}
</script>

<template>
  <section
    :class="['kanban-column', `column-${column.id}`, { 'drop-blocked': dropBlocked, 'drop-open': dropOpen, collapsed, collapsible }]"
    :data-column="column.id"
    :data-collapsed="collapsed ? 'true' : 'false'"
    :aria-label="columnLabel"
  >
    <component
      :is="collapsible ? 'button' : 'header'"
      :type="collapsible ? 'button' : undefined"
      class="column-header"
      :aria-expanded="collapsible ? String(!collapsed) : undefined"
      @click="toggleExpanded"
    >
      <span class="status-dot" aria-hidden="true" />
      <span class="column-title">{{ columnLabel }}</span>
      <span class="column-count">{{ localTasks.length }}</span>
    </component>
    <div v-show="!collapsed" class="column-body">
      <VueDraggable
        v-model="localTasks"
        class="task-list"
        :data-column="column.id"
        group="kanban-cards"
        :animation="150"
        :force-fallback="true"
        :fallback-tolerance="6"
        :delay="120"
        :delay-on-touch-only="true"
        :disabled="dragDisabled"
        ghost-class="task-slot-ghost"
        chosen-class="task-slot-chosen"
        drag-class="task-slot-dragging"
        filter=".task-slot-pending"
        :prevent-on-filter="false"
        @move="canMove"
        @start="handleStart"
        @end="handleEnd"
        @update="handleUpdate"
        @add="handleAdd"
      >
        <div
          v-for="task in localTasks"
          :key="task.id"
          class="task-slot"
          :class="{ 'task-slot-pending': !!pendingTaskIds?.[task.id] }"
          :data-task-id="task.id"
          :data-status="task.status"
          :data-pending="pendingTaskIds?.[task.id] ? 'true' : 'false'"
        >
          <KanbanTaskCard
            :task="task"
            :pending="!!pendingTaskIds?.[task.id]"
            :assignee-avatar="task.assignee ? avatars?.[task.assignee] || null : null"
            @click="emit('taskClick', task.id)"
            @action="payload => emit('taskAction', payload)"
          />
        </div>
      </VueDraggable>
      <div v-if="localTasks.length === 0 && !showArchived" class="column-empty" :class="{ loading }" :aria-hidden="loading ? undefined : 'true'" :role="loading ? 'status' : undefined">
        <span v-if="loading" class="column-loading-spinner" aria-hidden="true" />
        {{ loading ? t('kanban.board.loadingTasks') : dropBlocked ? t('kanban.dnd.dropNotAllowed') : t('kanban.noTasks') }}
      </div>
      <div v-else-if="dropBlocked" class="column-blocked-hint" aria-hidden="true">
        {{ t('kanban.dnd.dropNotAllowed') }}
      </div>
      <div v-if="archivedTasks" class="archive-section">
        <button
          type="button"
          class="archive-toggle"
          :aria-expanded="showArchived"
          @click="showArchived = !showArchived"
        >
          {{ showArchived ? t('kanban.board.hideArchived', { count: archivedCount }) : t('kanban.board.showArchived', { count: archivedCount }) }}
        </button>
        <div v-if="showArchived" class="archive-list" data-testid="kanban-archive-list">
          <div v-if="archivedCount === 0" class="archive-empty">{{ t('kanban.noTasks') }}</div>
          <KanbanTaskCard
            v-for="task in archivedTasks"
            :key="task.id"
            :task="task"
            muted
            :assignee-avatar="task.assignee ? avatars?.[task.assignee] || null : null"
            @click="emit('taskClick', task.id)"
          />
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.kanban-column {
  --kanban-status-color: #7f858d;
  display: flex;
  flex: 0 0 var(--kanban-column-width, 300px);
  flex-direction: column;
  width: var(--kanban-column-width, 300px);
  max-width: 100%;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  border: 1px solid $border-light;
  border-radius: $radius-md;
  background: color-mix(in srgb, $bg-secondary 66%, $bg-card);
  transition: border-color $transition-fast, opacity $transition-fast, box-shadow $transition-fast;

  &.column-queue { --kanban-status-color: #a66d23; }
  &.column-waiting { --kanban-status-color: #b8860b; }
  &.column-review { --kanban-status-color: #7b5fb3; }
  &.column-done { --kanban-status-color: var(--success); }

  &.drop-open {
    border-color: color-mix(in srgb, var(--kanban-status-color) 55%, $border-color);
    box-shadow: 0 0 0 2px color-mix(in srgb, var(--kanban-status-color) 22%, transparent);
  }

  &.drop-blocked {
    opacity: 0.55;

    .task-list {
      cursor: not-allowed;
    }
  }

  &.collapsible {
    transition: border-color $transition-fast, opacity $transition-fast, box-shadow $transition-fast, flex-basis $transition-normal, width $transition-normal;
  }

  // Empty collapsible column: a narrow strip with a vertical title.
  &.collapsed {
    flex-basis: 44px;
    width: 44px;

    .column-header {
      flex: 1;
      flex-direction: column;
      justify-content: flex-start;
      border-bottom: 0;
    }

    .column-title {
      writing-mode: vertical-rl;
      color: $text-muted;
      font-weight: 500;
    }

    .column-count {
      margin-inline-start: 0;
    }
  }
}

.column-header {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 43px;
  padding: 10px 11px;
  border-bottom: 1px solid $border-light;
  color: $text-primary;
  font-weight: 600;
  user-select: none;
}

button.column-header {
  appearance: none;
  width: 100%;
  background: transparent;
  border-inline: 0;
  border-top: 0;
  font: inherit;
  font-weight: 600;
  text-align: start;
  cursor: pointer;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: var(--kanban-status-color);
  flex-shrink: 0;
}

.column-title {
  min-width: 0;
  overflow: hidden;
  font-size: 12.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.column-count {
  min-width: 22px;
  margin-inline-start: auto;
  padding: 2px 6px;
  border: 1px solid $border-light;
  border-radius: 999px;
  color: $text-muted;
  font-size: 10.5px;
  font-weight: 500;
  line-height: 1.2;
  text-align: center;
}

.column-body {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.task-list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;
  min-height: 96px;
  padding: 9px;
  overflow-x: hidden;
  overflow-y: auto;
  // Keep vertical overscroll inside the list, but let horizontal wheel and
  // trackpad gestures chain to the board so it scrolls sideways from anywhere.
  overscroll-behavior-y: contain;
  overscroll-behavior-x: auto;
  scrollbar-gutter: stable;
}

.task-slot {
  flex: 0 0 auto;
  cursor: grab;

  &:active {
    cursor: grabbing;
  }

  &.task-slot-pending {
    cursor: progress;
  }
}

.task-slot-ghost {
  opacity: 0.35;
}

.task-slot-chosen :deep(.kanban-task-card) {
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.12);
}

.task-slot-dragging {
  opacity: 0.95;
  transform: rotate(1.5deg);
}

.column-empty,
.column-blocked-hint {
  position: absolute;
  inset: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px;
  border: 1px dashed $border-light;
  border-radius: $radius-sm;
  font-size: 12px;
  color: $text-muted;
  text-align: center;
  pointer-events: none;
}

.column-blocked-hint {
  inset: auto 9px 9px;
  background: color-mix(in srgb, $bg-card 85%, transparent);
}

@keyframes kanban-column-loading-spin {
  to { transform: rotate(360deg); }
}

.column-empty.loading {
  gap: 8px;
  border-style: solid;
  border-color: transparent;
}

.column-loading-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid $border-color;
  border-top-color: $text-secondary;
  border-radius: 999px;
  animation: kanban-column-loading-spin 0.9s linear infinite;
}

@media (prefers-reduced-motion: reduce) {
  .column-loading-spinner {
    animation: none;
  }
}

.archive-section {
  flex: 0 0 auto;
  max-height: 55%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-top: 1px dashed $border-light;
}

.archive-toggle {
  appearance: none;
  padding: 8px 11px;
  border: 0;
  background: transparent;
  color: $text-muted;
  font: inherit;
  font-size: 11.5px;
  text-align: start;
  cursor: pointer;

  &:hover,
  &:focus-visible {
    color: $text-primary;
  }
}

.archive-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
  padding: 0 9px 9px;
  overflow-y: auto;
  overscroll-behavior-y: contain;
  overscroll-behavior-x: auto;
}

.archive-empty {
  padding: 8px;
  font-size: 12px;
  color: $text-muted;
  text-align: center;
}
</style>
