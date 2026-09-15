<script setup lang="ts">
import { h, onMounted, onUnmounted, ref, watch } from 'vue'
import { NAlert, NButton, NDataTable, NModal, NPopconfirm, NTabPane, NTabs, useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import QRCode from 'qrcode'
import { createLanAppAuthorization, deleteAppConnection, fetchAppConnections, type AppConnection } from '@/api/studio/app-connections'
import SocialMessagesView from '@/views/social-messages/SocialMessagesView.vue'

const { t } = useI18n()
const message = useMessage()
const connections = ref<AppConnection[]>([])
const loading = ref(false)
const authorizing = ref(false)
const qr = ref('')
const showQr = ref(false)
let expiry: ReturnType<typeof setTimeout> | undefined
const serverUrl = window.location.origin
const route = useRoute()
const selectedView = ref(route.query.view === 'messages' ? 'messages' : 'direct')
watch(() => route.query.view, view => { selectedView.value = view === 'messages' ? 'messages' : 'direct' })
const columns = [
  { title: () => t('connections.app.deviceName'), key: 'device_name' },
  { title: () => t('connections.app.authorizedUser'), key: 'username' },
  { title: () => t('connections.app.connectionType'), key: 'connection_type' },
  { title: () => t('connections.app.actions'), key: 'actions', render: (row: AppConnection) => h(NPopconfirm, {
    onPositiveClick: () => remove(row.id),
  }, { default: () => t('connections.app.deleteConfirm', { name: row.device_name }),
    trigger: () => h(NButton, { size: 'small', type: 'error', quaternary: true }, () => t('connections.app.delete')) }) },
]

async function refresh() {
  loading.value = true
  try { connections.value = (await fetchAppConnections()).connections }
  catch (error) { message.error(String(error)) }
  finally { loading.value = false }
}
async function remove(id: number) {
  try { await deleteAppConnection(id); await refresh() }
  catch (error) { message.error(String(error)) }
}
async function authorize() {
  if (authorizing.value) return
  authorizing.value = true
  try {
    const auth = await createLanAppAuthorization()
    qr.value = await QRCode.toDataURL(auth.qr_payload)
    showQr.value = true
    clearTimeout(expiry)
    expiry = setTimeout(() => { showQr.value = false; qr.value = '' }, Math.max(0, auth.expires_at * 1000 - Date.now()))
  } catch (error) { message.error(String(error)) }
  finally { authorizing.value = false }
}
onMounted(refresh)
onUnmounted(() => clearTimeout(expiry))
</script>

<template>
  <NTabs v-model:value="selectedView" type="line" class="personal-connections">
    <NTabPane name="direct" :tab="t('personalFork.direct')">
      <NAlert type="info" :bordered="false" :title="t('personalFork.title')">
        <p>{{ t('personalFork.description') }}</p>
        <code dir="ltr">{{ serverUrl }}</code>
        <p>{{ t('personalFork.mobile') }}</p>
        <a href="/LICENSE" target="_blank" rel="noopener noreferrer">LICENSE</a>
      </NAlert>
      <div class="actions">
        <NButton :loading="authorizing" @click="authorize">{{ t('connections.app.scanToAdd') }}</NButton>
        <NButton :loading="loading" @click="refresh">{{ t('mcuDevices.refresh') }}</NButton>
      </div>
      <NDataTable :columns="columns" :data="connections" :loading="loading" :row-key="(row: AppConnection) => row.id" />
      <NModal v-model:show="showQr" preset="card" :title="t('personalFork.direct')" style="width: min(380px, 90vw)" @after-leave="qr = ''">
        <img v-if="qr" :src="qr" :alt="t('personalFork.direct')" class="qr" />
        <p>{{ t('personalFork.qrHint') }}</p>
      </NModal>
    </NTabPane>
    <NTabPane name="messages" :tab="t('connections.app.viewMessages')" display-directive="if">
      <SocialMessagesView embedded />
    </NTabPane>
  </NTabs>
</template>

<style scoped lang="scss">
.personal-connections { padding: 0 20px 20px; box-sizing: border-box; overflow: auto; }
.actions { display: flex; gap: 8px; margin: 16px 0; }
.qr { display: block; width: 256px; max-width: 100%; margin: auto; }
</style>
