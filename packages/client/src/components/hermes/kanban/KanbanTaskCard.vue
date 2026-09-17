<script setup lang="ts">
import ContentText from '@/components/common/ContentText.vue'
import { computed } from 'vue'
import { NTooltip } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import ProfileAvatar from '@/components/hermes/profiles/ProfileAvatar.vue'
import type { KanbanTask } from '@/api/hermes/kanban'
import type { ProfileAvatar as ProfileAvatarData } from '@/api/hermes/profiles'

export type KanbanCardQuickAction = 'promote' | 'archive' | 'specify'

const props = defineProps<{
  task: KanbanTask
  assigneeAvatar?: ProfileAvatarData | null
  /** Renders the card muted and without quick actions (archive list). */
  muted?: boolean
  /** A Hermes command for this task is still running. */
  pending?: boolean
}>()

const emit = defineEmits<{
  click: [taskId: string]
  action: [payload: { taskId: string; action: KanbanCardQuickAction }]
}>()

const { t } = useI18n()

const timeAgo = computed(() => {
  const diff = Date.now() / 1000 - props.task.created_at
  if (diff < 60) return t('kanban.card.timeAgo.justNow')
  if (diff < 3600) return t('kanban.card.timeAgo.minutes', { count: Math.floor(diff / 60) })
  if (diff < 86400) return t('kanban.card.timeAgo.hours', { count: Math.floor(diff / 3600) })
  return t('kanban.card.timeAgo.days', { count: Math.floor(diff / 86400) })
})

const priorityLabel = computed(() => {
  if (props.task.priority >= 3) return 'high'
  if (props.task.priority === 2) return 'medium'
  return 'low'
})

const priorityText = computed(() => {
  return t(`kanban.card.priority.${priorityLabel.value}`)
})

// Status is readable on the card itself because board columns group several
// statuses (queue = todo + ready, waiting = scheduled + blocked).
const statusBadge = computed(() => {
  switch (props.task.status) {
    case 'ready':
    case 'running':
    case 'scheduled':
    case 'blocked':
    case 'review':
    case 'done':
    case 'archived':
      return t(`kanban.columns.${props.task.status}`, props.task.status)
    default:
      return null
  }
})

const quickAction = computed<KanbanCardQuickAction | null>(() => {
  if (props.muted || props.pending) return null
  switch (props.task.status) {
    case 'todo': return 'promote'
    case 'done': return 'archive'
    case 'triage': return 'specify'
    default: return null
  }
})

const quickActionLabel = computed(() => {
  switch (quickAction.value) {
    case 'promote': return t('kanban.action.promote')
    case 'archive': return t('kanban.action.archive')
    case 'specify': return t('kanban.action.specify')
    default: return ''
  }
})

function handleQuickAction() {
  if (quickAction.value) emit('action', { taskId: props.task.id, action: quickAction.value })
}

// The card body is a role=button element (not <button>) so the quick action can
// be a real nested button without invalid markup.
function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    emit('click', props.task.id)
  }
}
</script>

<template>
  <div
    class="kanban-task-card"
    :class="[`status-${task.status}`, { muted, pending }]"
    :data-status="task.status"
    :aria-busy="pending ? 'true' : undefined"
  >
    <div
      class="card-main"
      role="button"
      tabindex="0"
      :aria-label="task.title"
      @click="emit('click', task.id)"
      @keydown="handleKeydown"
    >
      <span class="card-heading">
        <span class="status-marker" aria-hidden="true" />
        <span class="task-id">{{ task.id }}</span>
        <span v-if="statusBadge" class="status-badge">
          <svg v-if="task.status === 'scheduled'" class="status-icon" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="2"/><path d="M12 7v5l3 2" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
          <svg v-else-if="task.status === 'blocked'" class="status-icon" viewBox="0 0 24 24" aria-hidden="true"><rect x="6" y="5" width="4" height="14" rx="1" fill="currentColor"/><rect x="14" y="5" width="4" height="14" rx="1" fill="currentColor"/></svg>
          <svg v-else-if="task.status === 'review'" class="status-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z" fill="none" stroke="currentColor" stroke-width="2"/><circle cx="12" cy="12" r="3" fill="currentColor"/></svg>
          <svg v-else-if="task.status === 'done' || task.status === 'archived'" class="status-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M5 13l4 4L19 7" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/></svg>
          {{ statusBadge }}
        </span>
        <span v-if="pending" class="pending-badge" role="status">
          <span class="pending-spinner" aria-hidden="true" />
          {{ t('kanban.card.syncing') }}
        </span>
        <span v-if="task.priority >= 2" class="priority" :class="priorityLabel">
          <span class="priority-dot" aria-hidden="true" />
          {{ priorityText }}
        </span>
      </span>
      <ContentText as="span" class="card-title">{{ task.title }}</ContentText>
      <span class="card-footer">
        <NTooltip v-if="task.assignee" trigger="hover">
          <template #trigger>
            <span class="assignee">
              <ProfileAvatar
                class="assignee-profile-avatar"
                :name="task.assignee"
                :avatar="assigneeAvatar"
                :size="18"
                aria-hidden="true"
              />
              <span class="assignee-name">{{ task.assignee }}</span>
            </span>
          </template>
          {{ t('kanban.card.assigneeTooltip') }}
        </NTooltip>
        <button
          v-if="quickAction"
          type="button"
          class="card-quick-action"
          :data-action="quickAction"
          @click.stop="handleQuickAction"
          @keydown.stop
        >
          {{ quickActionLabel }}
        </button>
        <span class="card-time">{{ timeAgo }}</span>
      </span>
    </div>
  </div>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

@keyframes kanban-ring-spin {
  to { transform: rotate(360deg); }
}

@keyframes kanban-pending-spin {
  to { transform: rotate(360deg); }
}

@keyframes kanban-marker-pulse {
  0%, 100% { box-shadow: 0 0 0 0 color-mix(in srgb, var(--kanban-card-status-color) 55%, transparent); }
  70% { box-shadow: 0 0 0 6px transparent; }
}

.kanban-task-card {
  --kanban-card-status-color: #7f858d;
  --kanban-ring-width: 1px;
  position: relative;
  isolation: isolate;
  display: flex;
  flex-direction: column;
  width: 100%;
  padding: var(--kanban-ring-width);
  overflow: hidden;
  border-radius: $radius-md;
  background: $border-color;
  transition: box-shadow $transition-fast, transform $transition-fast;

  &.status-triage { --kanban-card-status-color: #8b8f95; }
  &.status-todo { --kanban-card-status-color: #6f7782; }
  &.status-scheduled { --kanban-card-status-color: #b8860b; }
  &.status-ready { --kanban-card-status-color: #a66d23; }
  &.status-running { --kanban-card-status-color: var(--success); }
  &.status-blocked { --kanban-card-status-color: var(--error); }
  &.status-review { --kanban-card-status-color: #7b5fb3; }
  &.status-done { --kanban-card-status-color: var(--success); }
  &.status-archived { --kanban-card-status-color: #777b81; }

  // Static coloured ring for states that must be visible from afar.
  &.status-blocked,
  &.status-review,
  &.status-scheduled,
  &.status-running {
    --kanban-ring-width: 2px;
    background: var(--kanban-card-status-color);
  }

  // Scheduled: waiting on time, a dashed ring instead of a solid one.
  &.status-scheduled {
    background: repeating-linear-gradient(45deg, var(--kanban-card-status-color) 0 6px, transparent 6px 10px);
  }

  // Running: a green sweep travels around the card while the worker owns it.
  &.status-running {
    background: color-mix(in srgb, var(--kanban-card-status-color) 35%, $border-color);

    &::before {
      content: '';
      position: absolute;
      inset: -60%;
      z-index: -1;
      background: conic-gradient(from 0deg, transparent 0 55%, var(--kanban-card-status-color) 85%, transparent 100%);
      animation: kanban-ring-spin 2.4s linear infinite;
    }

    .status-marker {
      animation: kanban-marker-pulse 1.6s ease-out infinite;
    }
  }

  &.status-done .card-main,
  &.muted .card-main {
    opacity: 0.72;
  }

  &.pending .card-main {
    opacity: 0.85;
  }

  &:hover {
    box-shadow: 0 6px 16px rgba(0, 0, 0, 0.07);
    transform: translateY(-1px);
  }

  &:hover .card-quick-action,
  &:focus-within .card-quick-action {
    opacity: 1;
  }
}

@media (prefers-reduced-motion: reduce) {
  .kanban-task-card.status-running::before {
    animation: none;
    background: var(--kanban-card-status-color);
  }

  .kanban-task-card.status-running .status-marker {
    animation: none;
  }
}

.card-main {
  appearance: none;
  display: flex;
  flex-direction: column;
  width: 100%;
  padding: 12px 13px;
  border: 0;
  border-radius: calc(#{$radius-md} - 1px);
  background-color: $bg-card;
  color: inherit;
  font: inherit;
  text-align: start;
  cursor: pointer;
  transition: background-color $transition-fast;

  &:hover {
    background-color: $bg-card-hover;
  }

  &:focus-visible {
    outline: 2px solid color-mix(in srgb, var(--kanban-card-status-color) 72%, transparent);
    outline-offset: -3px;
  }
}

.card-heading {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 7px;
  min-height: 16px;
}

.status-marker {
  width: 6px;
  height: 6px;
  flex: 0 0 auto;
  border-radius: 999px;
  background: var(--kanban-card-status-color);
}

.task-id {
  min-width: 0;
  overflow: hidden;
  color: $text-muted;
  font-family: $font-code;
  font-size: 10.5px;
  line-height: 1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 6px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--kanban-card-status-color) 14%, transparent);
  color: color-mix(in srgb, var(--kanban-card-status-color) 80%, $text-primary);
  font-size: 10.5px;
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
}

.status-icon {
  width: 11px;
  height: 11px;
  flex: 0 0 auto;
}

.pending-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-inline-start: auto;
  color: $text-muted;
  font-size: 10.5px;
  line-height: 1;
  white-space: nowrap;
}

.pending-spinner {
  width: 9px;
  height: 9px;
  border: 1.5px solid $border-color;
  border-top-color: $text-secondary;
  border-radius: 999px;
  animation: kanban-pending-spin 0.9s linear infinite;
}

@media (prefers-reduced-motion: reduce) {
  .pending-spinner {
    animation: none;
  }
}

.card-title {
  display: -webkit-box;
  margin-top: 8px;
  overflow: hidden;
  color: $text-primary;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.45;
  word-break: break-word;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

.priority {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-inline-start: auto;
  color: $text-secondary;
  font-size: 10.5px;
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;

  &.high .priority-dot {
    background: $error;
  }

  &.medium .priority-dot {
    background: $warning;
  }
}

.priority-dot {
  width: 5px;
  height: 5px;
  border-radius: 999px;
  background: $text-muted;
}

.card-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  min-height: 20px;
  margin-top: 12px;
}

.assignee {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  max-width: calc(100% - 64px);
  gap: 6px;
  color: $text-secondary;
  font-size: 11px;
  line-height: 1;
}

.assignee-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.assignee-profile-avatar {
  flex: 0 0 auto;
  box-shadow: 0 0 0 1px $border-light;
}

.card-time {
  flex: 0 0 auto;
  margin-inline-start: auto;
  color: $text-muted;

  .card-quick-action + & {
    margin-inline-start: 8px;
  }
  font-size: 11px;
  white-space: nowrap;
}

.card-quick-action {
  appearance: none;
  flex: 0 0 auto;
  margin-inline-start: auto;
  padding: 3px 8px;
  border: 1px solid $border-light;
  border-radius: 999px;
  background: $bg-card;
  color: $text-secondary;
  font: inherit;
  font-size: 11px;
  line-height: 1.2;
  cursor: pointer;
  opacity: 0;
  transition: opacity $transition-fast, color $transition-fast, border-color $transition-fast;

  &:hover,
  &:focus-visible {
    color: $text-primary;
    border-color: var(--kanban-card-status-color);
    opacity: 1;
  }
}

@media (hover: none) {
  .card-quick-action {
    opacity: 1;
  }
}
</style>
