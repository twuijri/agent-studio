import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

// "Computer apps" lives in the primary page sidebar (next to Device
// connections) and renders inside the chat shell like Models and Agent
// Manager, so it is reachable in the layout users actually see.

describe('Computer apps navigation', () => {
  const nav = readFileSync('packages/client/src/components/layout/PageSidebarNav.vue', 'utf8')
  const router = readFileSync('packages/client/src/router/index.ts', 'utf8')
  const chatView = readFileSync('packages/client/src/views/hermes/ChatView.vue', 'utf8')
  const chatPanel = readFileSync('packages/client/src/components/hermes/chat/ChatPanel.vue', 'utf8')
  const sidebar = readFileSync('packages/client/src/components/layout/AppSidebar.vue', 'utf8')

  it('adds a sidebar tab right after Device connections, only when the route exists', () => {
    expect(nav).toContain("const hasComputerApps = computed(() => router.hasRoute('hermes.appConnections'))")
    expect(nav).toContain('v-if="hasComputerApps"')
    expect(nav.indexOf("t('sidebar.connections')")).toBeLessThan(nav.indexOf("t('sidebar.appConnections')"))
    expect(nav.indexOf("t('sidebar.appConnections')")).toBeLessThan(nav.indexOf("t('sidebar.agentManager')"))
    expect(nav).toContain("void router.push({ name: 'hermes.appConnections' })")
    // The settings drawer (AppSidebar) must not repeat the entry: opening it from there
    // stacked the drawer next to the chat shell's own sidebar.
    expect(sidebar).not.toContain('hermes.appConnections')
  })

  it('uses the page sidebar like Device connections (no settings drawer next to it)', () => {
    const app = readFileSync('packages/client/src/App.vue', 'utf8')
    const list = app.slice(app.indexOf('const usesPageSidebar'), app.indexOf('].includes(route.name as string)'))
    expect(list).toContain('"hermes.appConnections"')
    expect(list).toContain('"hermes.connections"')
  })

  it('renders inside the chat shell as its own content mode', () => {
    expect(router).toMatch(/name: 'hermes\.appConnections',\n\s+component: \(\) => import\('@\/views\/hermes\/ChatView\.vue'\)/)
    expect(chatView).toContain("if (route.name === 'hermes.appConnections') return 'apps'")
    expect(chatPanel).toContain('v-else-if="contentMode === \'apps\'"')
    expect(chatPanel).toContain("contentMode === 'apps' ? 'apps'")
    expect(chatPanel).toContain("import('@/views/hermes/AppConnectionsView.vue')")
  })
})
