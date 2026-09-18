<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { NButton, NSpin, NSwitch, NTag, useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { desktopDeviceAgentBridge, type DesktopDeviceAgentSnapshot, type DesktopDiscoveredApp, type DesktopSharedApp } from '@/utils/desktop-bridge'

// Apps on this computer that expose an MCP server (registered with Claude
// Desktop, Claude Code, Codex, Cursor, Windsurf, or installed as a Claude
// extension). Sharing one lets Hermes on the linked server use it through
// the Device Agent; the server wires it into the allowed profiles itself.

const { t } = useI18n()
const message = useMessage()
const bridge = desktopDeviceAgentBridge()

const loading = ref(false)
const saving = ref('')
const discovered = ref<DesktopDiscoveredApp[]>([])
const agent = ref<DesktopDeviceAgentSnapshot | null>(null)
let stopUpdates: (() => void) | null = null

const sharedIds = computed(() => new Set((agent.value?.config.sharedApps || []).filter(app => app.enabled).map(app => app.id)))
const available = computed(() => bridge !== null)
const linked = computed(() => !!agent.value?.linked)
const connected = computed(() => agent.value?.status === 'connected')

function sourceLabel(source: string): string {
  const key = `appConnections.sources.${source.replace(/-/g, '_')}`
  const label = t(key)
  return label === key ? source : label
}

async function refresh() {
  if (!bridge?.discoverApps) return
  loading.value = true
  try {
    const [apps, state] = await Promise.all([bridge.discoverApps(), bridge.getState()])
    discovered.value = apps.filter(app => app.transport === 'stdio')
    agent.value = state
  } catch (err: any) {
    message.error(err?.message || t('appConnections.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function toggle(app: DesktopDiscoveredApp, enabled: boolean) {
  if (!bridge?.setApps || !agent.value) return
  saving.value = app.id
  try {
    const current = (agent.value.config.sharedApps || []).filter(item => item.id !== app.id)
    const next: DesktopSharedApp[] = enabled
      ? [...current, { id: app.id, name: app.name, source: app.source, command: app.command || '', args: app.args, env: app.env, cwd: app.cwd, enabled: true }]
      : current
    agent.value = await bridge.setApps(next)
    message.success(enabled ? t('appConnections.shared', { name: app.name }) : t('appConnections.unshared', { name: app.name }))
  } catch (err: any) {
    message.error(err?.message || t('appConnections.saveFailed'))
  } finally {
    saving.value = ''
  }
}

onMounted(() => {
  void refresh()
  if (bridge?.onState) stopUpdates = bridge.onState(state => { agent.value = state })
})
onBeforeUnmount(() => { stopUpdates?.() })
</script>

<template>
  <div class="app-connections">
    <header class="page-header">
      <div class="header-heading">
        <h2 class="header-title">{{ t('appConnections.title') }}</h2>
        <p>{{ t('appConnections.subtitle') }}</p>
      </div>
      <div class="header-actions">
        <NTag v-if="available" size="small" :type="connected ? 'success' : 'warning'" round>
          {{ connected ? t('appConnections.deviceConnected') : t('appConnections.deviceNotConnected') }}
        </NTag>
        <NButton size="small" :loading="loading" :disabled="!available" @click="refresh">{{ t('appConnections.rescan') }}</NButton>
      </div>
    </header>

    <div v-if="!available" class="empty-state" data-testid="app-connections-unavailable">
      <p>{{ t('appConnections.desktopOnly') }}</p>
    </div>

    <NSpin v-else :show="loading">
      <div v-if="!linked" class="empty-state">
        <p>{{ t('appConnections.notLinked') }}</p>
      </div>
      <div v-else-if="discovered.length === 0 && !loading" class="empty-state" data-testid="app-connections-empty">
        <p>{{ t('appConnections.empty') }}</p>
        <p class="hint">{{ t('appConnections.emptyHint') }}</p>
      </div>
      <div v-else class="app-grid">
        <article v-for="app in discovered" :key="app.id" class="app-card" :data-testid="`app-card-${app.id}`">
          <div class="app-card-header">
            <div>
              <div class="app-name">{{ app.name }}</div>
              <div class="app-meta">{{ sourceLabel(app.source) }}</div>
            </div>
            <NSwitch
              :value="sharedIds.has(app.id)"
              :loading="saving === app.id"
              :disabled="app.unresolved.length > 0"
              @update:value="value => toggle(app, value)"
            />
          </div>
          <code class="app-command">{{ [app.command, ...app.args].join(' ') }}</code>
          <div v-if="app.unresolved.length > 0" class="app-warning">
            {{ t('appConnections.needsConfig', { keys: app.unresolved.join(', ') }) }}
          </div>
          <div class="app-origin">{{ app.origin }}</div>
        </article>
      </div>
      <p v-if="linked" class="hint footer-hint">{{ t('appConnections.howItWorks') }}</p>
    </NSpin>
  </div>
</template>

<style scoped>
.app-connections {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 20px 24px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
}

.header-title {
  margin: 0 0 4px;
  font-size: 20px;
}

.header-heading p {
  margin: 0;
  color: var(--text-secondary, #888);
  font-size: 13px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.empty-state {
  padding: 40px 16px;
  text-align: center;
  color: var(--text-secondary, #888);
}

.hint {
  font-size: 12px;
  color: var(--text-secondary, #888);
}

.footer-hint {
  margin: 12px 0 0;
}

.app-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 14px;
}

.app-card {
  border: 1px solid var(--border-color, rgba(128, 128, 128, 0.25));
  border-radius: 10px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.app-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.app-name {
  font-weight: 600;
  font-size: 14px;
}

.app-meta {
  font-size: 12px;
  color: var(--text-secondary, #888);
}

.app-command {
  direction: ltr;
  unicode-bidi: isolate;
  font-size: 11px;
  word-break: break-all;
  color: var(--text-secondary, #888);
}

.app-warning {
  font-size: 12px;
  color: #e0a04a;
}

.app-origin {
  direction: ltr;
  unicode-bidi: isolate;
  font-size: 11px;
  color: var(--text-secondary, #888);
  word-break: break-all;
}
</style>
