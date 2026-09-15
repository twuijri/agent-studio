<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { VueDraggable, type DraggableEvent } from 'vue-draggable-plus'
import KanbanTaskCard from './KanbanTaskCard.vue'
import { isKanbanDropTarget } from '@/utils/hermes/kanban-board'
import type { KanbanTask, KanbanTaskStatus } from '@/api/hermes/kanban'
import type { ProfileAvatar } from '@/api/hermes/profiles'

const props = defineProps<{
  status: KanbanTaskStatus
  tasks: KanbanTask[]
  avatars?: Record<string, ProfileAvatar | null>
  /** Status of the card currently being dragged anywhere on the board, or null. */
  draggingStatus?: KanbanTaskStatus | null
  /** Disables dragging cards out of (and into) this column, e.g. while a transition runs. */
  dragDisabled?: boolean
}>()

const emit = defineEmits<{
  taskClick: [taskId: string]
  reorder: [ids: string[]]
  dropped: [payload: { taskId: string; from: KanbanTaskStatus; to: KanbanTaskStatus; index: number }]
  dragStart: [status: KanbanTaskStatus]
  dragEnd: []
}>()

const { t } = useI18n()

// Sortable mutates the bound list directly, so keep a local copy and resync it
// from the store whenever no drag is in flight.
const localTasks = ref<KanbanTask[]>([...props.tasks])

function syncFromProps() {
  localTasks.value = [...props.tasks]
}

watch(() => props.tasks, () => {
  if (!props.draggingStatus) syncFromProps()
})

watch(() => props.draggingStatus, (dragging) => {
  if (!dragging) syncFromProps()
})

const title = computed(() => t(`kanban.columns.${props.status}`, props.status))
const dropBlocked = computed(() => !!props.draggingStatus && !isKanbanDropTarget(props.draggingStatus, props.status))
const dropOpen = computed(() => !!props.draggingStatus && props.draggingStatus !== props.status && !dropBlocked.value)

function statusOf(element: HTMLElement | null | undefined): KanbanTaskStatus | null {
  const value = element?.dataset?.status
  return value ? value as KanbanTaskStatus : null
}

function canMove(event: { from: HTMLElement; to: HTMLElement }): boolean {
  const from = statusOf(event.from)
  const to = statusOf(event.to)
  if (!from || !to) return false
  return isKanbanDropTarget(from, to)
}

function handleStart() {
  emit('dragStart', props.status)
}

function handleEnd() {
  emit('dragEnd')
}

function handleUpdate() {
  emit('reorder', localTasks.value.map(task => task.id))
}

function pointerStatus(event: DraggableEvent<KanbanTask>): KanbanTaskStatus | null {
  const original = (event as { originalEvent?: MouseEvent | TouchEvent }).originalEvent
  if (!original || typeof document.elementFromPoint !== 'function') return null
  const point = 'changedTouches' in original ? original.changedTouches[0] : original
  if (!point) return null
  const column = document.elementFromPoint(point.clientX, point.clientY)?.closest<HTMLElement>('.kanban-column')
  return statusOf(column)
}

function handleAdd(event: DraggableEvent<KanbanTask>) {
  const from = statusOf(event.from)
  const task = event.data
  if (!from || !task) return
  // Sortable leaves the card in the last column that accepted it, so a drop over
  // a refused column would silently land elsewhere. Report the column under the
  // pointer instead; the board reverts when that is not a valid transition.
  const to = pointerStatus(event) || props.status
  emit('dropped', { taskId: task.id, from, to, index: event.newIndex ?? 0 })
}
</script>

<template>
  <section
    :class="['kanban-column', `status-${status}`, { 'drop-blocked': dropBlocked, 'drop-open': dropOpen }]"
    :data-status="status"
    :aria-label="title"
  >
    <header class="column-header">
      <span class="status-dot" aria-hidden="true" />
      <span class="column-title">{{ title }}</span>
      <span class="column-count">{{ localTasks.length }}</span>
    </header>
    <div class="column-body">
      <VueDraggable
        v-model="localTasks"
        class="task-list"
        :data-status="status"
        group="kanban-cards"
        :animation="150"
        :force-fallback="true"
        :fallback-tolerance="6"
        :delay="120"
        :delay-on-touch-only="true"
        :disabled="dragDisabled"
        @move="canMove"
        ghost-class="task-slot-ghost"
        chosen-class="task-slot-chosen"
        drag-class="task-slot-dragging"
        @start="handleStart"
        @end="handleEnd"
        @update="handleUpdate"
        @add="handleAdd"
      >
        <div
          v-for="task in localTasks"
          :key="task.id"
          class="task-slot"
          :data-task-id="task.id"
        >
          <KanbanTaskCard
            :task="task"
            :assignee-avatar="task.assignee ? avatars?.[task.assignee] || null : null"
            @click="emit('taskClick', task.id)"
          />
        </div>
      </VueDraggable>
      <div v-if="localTasks.length === 0" class="column-empty" aria-hidden="true">
        {{ dropBlocked ? t('kanban.dnd.dropNotAllowed') : t('kanban.noTasks') }}
      </div>
      <div v-else-if="dropBlocked" class="column-blocked-hint" aria-hidden="true">
        {{ t('kanban.dnd.dropNotAllowed') }}
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

  &.status-triage { --kanban-status-color: #8b8f95; }
  &.status-todo { --kanban-status-color: #6f7782; }
  &.status-scheduled { --kanban-status-color: #667681; }
  &.status-ready { --kanban-status-color: #a66d23; }
  &.status-running { --kanban-status-color: var(--accent-info); }
  &.status-blocked { --kanban-status-color: var(--error); }
  &.status-review { --kanban-status-color: #7b6f8b; }
  &.status-done { --kanban-status-color: var(--success); }
  &.status-archived { --kanban-status-color: #777b81; }

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
  cursor: grab;
  user-select: none;
  touch-action: none;

  &:active {
    cursor: grabbing;
  }
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
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}

.task-slot {
  flex: 0 0 auto;
  cursor: grab;

  &:active {
    cursor: grabbing;
  }
}

.task-slot-ghost {
  opacity: 0.35;

  :deep(.kanban-task-card) {
    border-style: dashed;
    border-color: var(--kanban-status-color);
  }
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
</style>
