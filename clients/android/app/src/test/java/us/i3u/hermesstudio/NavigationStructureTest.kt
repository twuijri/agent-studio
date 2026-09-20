package us.i3u.hermesstudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the phone's information architecture equal to the web app's, as
 * `docs/mobile/NAVIGATION.md` writes it down: an off-canvas drawer with the
 * primary rail, the four-segment switch, the session list and the footer; one
 * Settings screen with the web's tabs and a Tools section; and every agent
 * tool under its agent's card. `NavigationParityTest` checks the registry
 * itself; this file checks the screens are wired to it the way the contract
 * says, so "I press X and something with a similar name opens" cannot return.
 */
class NavigationStructureTest {

    private val src = File("src/main/java/us/i3u/hermesstudio")
    private val viewModel = File(src, "AppViewModel.kt").readText()
    private val activity = File(src, "MainActivity.kt").readText()
    private val drawer = File(src, "ui/navigation/CoreHubDrawer.kt").readText()
    private val shell = File(src, "ui/navigation/HomeShell.kt").readText()
    private val search = File(src, "ui/navigation/SessionSearchSheet.kt").readText()
    private val settings = File(src, "ui/settings/SettingsScreen.kt").readText()
    private val agentManager = File(src, "ui/agents/AgentManagerScreen.kt").readText()
    private val agentScreen = File(src, "ui/agents/AgentScreen.kt").readText()
    private val hermes = File(src, "ui/agents/HermesScreens.kt").readText()
    private val sessionList = File(src, "ui/sessions/SessionList.kt").readText()
    private val history = File(src, "ui/sessions/HistoryScreen.kt").readText()
    private val chat = File(src, "ui/chat/ConversationScreen.kt").readText()
    private val kanban = File(src, "KanbanScreens.kt").readText()
    private val tools = File(src, "AgentToolScreens.kt").readText()
    private val insights = File(src, "StudioInsightsScreens.kt").readText()
    private val parity = File(src, "StudioParityScreens.kt").readText()
    private val workspace = File(src, "StudioWorkspaceScreens.kt").readText()

    @Test
    fun theRootIsTheWebsFourSegmentSwitchNotThreeBottomTabs() {
        assertTrue(viewModel.contains("enum class Tab { Chat, Group, Workflow, History }"))
        assertFalse("the old bottom navigation bar must be gone", activity.contains("fun StudioTabs"))
        src.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            assertFalse("${file.name} still mounts the old bottom tabs", file.readText().contains("bottomBar = { StudioTabs"))
        }
        // Each segment is an icon, a label from the registry, and the tab it selects.
        listOf(Tab.Chat to "chat", Tab.Group to "groupChat", Tab.Workflow to "workflow", Tab.History to "history").forEach { (tab, destination) ->
            assertTrue("drawer switch must offer $tab", Regex("""Triple\(Tab\.$tab, CoreHubIcons\.\w+, NavDestination\.$destination\.labelKey\)""").containsMatchIn(drawer))
        }
        // Every section is rendered inside the drawer shell with a hamburger.
        listOf(
            "Screen.Chats, Screen.Conversation -> HomeShell(state, viewModel) { openDrawer -> ConversationScreen(state, viewModel, onMenu = openDrawer) }",
            "Screen.Groups -> HomeShell(state, viewModel) { openDrawer -> GroupsScreen(state, viewModel, onMenu = openDrawer) }",
            "Screen.Workflows -> HomeShell(state, viewModel) { openDrawer -> WorkflowsScreen(state, viewModel, onMenu = openDrawer) }",
            "Screen.History -> HomeShell(state, viewModel) { openDrawer -> HistoryScreen(state, viewModel, onMenu = openDrawer) }",
        ).forEach { line -> assertTrue("AppContent lost: $line", activity.contains(line)) }
        assertTrue(shell.contains("CoreHubDrawerHost("))
    }

    @Test
    fun theDrawerFollowsTheContractFromTopToBottom() {
        // The rail is the registry's list, so its order cannot drift from the contract.
        assertTrue(drawer.contains("NavDestination.rail"))
        assertTrue("Agent Manager is super-admin only", drawer.contains(".filter { it != NavDestination.agentManager || state.isSuperAdmin }"))
        assertTrue("every rail row opens through the registry", drawer.contains("viewModel.openRailDestination(destination)"))
        assertFalse("Computer apps is desktop-only and must not appear on phones", drawer.contains("ComputerApps"))
        // Search is a sheet over the drawer, never the History page.
        assertTrue(drawer.contains("if (destination == NavDestination.search) viewModel.openRailDestination(destination)"))
        assertFalse(drawer.contains("showTab(Tab.History) }"))
        assertTrue(viewModel.contains("NavDestination.search -> openSearch()"))
        assertTrue(viewModel.contains("NavDestination.deviceConnections -> openConnections()"))
        assertTrue(viewModel.contains("NavDestination.models -> openModels()"))

        // The rail and the switch are the header items of the selected
        // segment's own scroll, so on screen the order is rail → switch →
        // list → footer, whichever list the segment is showing.
        val headerAt = drawer.indexOf("val drawerHeader: LazyListScope.() -> Unit = {")
        val railAt = drawer.indexOf("NavDestination.rail")
        val switchAt = drawer.indexOf("ConversationSwitch(")
        val footerAt = drawer.indexOf("DrawerFooter(state")
        assertTrue("rail and switch live in one shared list header", headerAt in 0 until railAt)
        assertTrue("rail, then switch, then the list, then footer", railAt < switchAt && switchAt < footerAt)
        listOf("SessionListPane(", "DrawerRoomList(", "DrawerWorkflowList(").forEach { list ->
            assertTrue("every segment's list takes the same header", drawer.contains(list) && drawer.indexOf("drawerHeader", switchAt) > switchAt)
        }

        // Footer: profile chip switches only (no manage link, as ProfileSelector.vue), gear → Settings.
        listOf(
            "viewModel.selectProfile(profile.name)",
            "viewModel.selectModel(option)",
            "R.string.action_sign_out",
            "state.account?.takeIf { it.isNotBlank() }",
            "if (state.connected) R.string.connected else R.string.disconnected",
            "R.string.footer_version, state.serverVersion ?: BuildConfig.VERSION_NAME",
            "DrawerLanguageSwitch(state, viewModel)",
            "DrawerThemeSwitch(state, viewModel)",
            "viewModel.openSettings()",
        ).forEach { needle -> assertTrue("footer lost $needle", drawer.contains(needle)) }
        assertFalse("the profile chip must not manage profiles", drawer.contains("openProfiles"))
        assertTrue("250 ms slide", drawer.contains("tween(CoreHubTokens.Metrics.drawerSlideMs)"))
        assertTrue("40 % scrim", drawer.contains("CoreHubTokens.Metrics.scrimAlpha"))
    }

    @Test
    fun openingASessionKeepsTheSelectedSegment() {
        assertTrue(drawer.contains("go { viewModel.openSession(session) }"))
        assertTrue(history.contains("onOpen = { session -> viewModel.openSession(session) }"))
        src.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            assertFalse("${file.name} flips the segment when opening a session", file.readText().contains("showTab(Tab.Chat); viewModel.openSession"))
        }
    }

    @Test
    fun searchIsASheetOverTheSessions() {
        assertTrue("the field takes focus on open", search.contains("LaunchedEffect(Unit) { focus.requestFocus() }"))
        assertTrue("recent sessions while the query is empty", search.contains("if (sheet.query.isBlank()) sheet.recent else sheet.results"))
        assertTrue("a hit opens its conversation", search.contains("viewModel.openSearchResult(session)"))
        assertTrue("the sheet is hosted by the shell", shell.contains("SessionSearchSheet(state, viewModel)"))
        assertTrue("the web's search route, ten hits", viewModel.contains("api.searchSessions(query.trim(), profile, limit = 10)"))
        assertTrue("global_agent hits open the Global Agent conversation", viewModel.contains("val global = session.source == \"global_agent\""))
        assertTrue("the Global Agent has no menu entry; the pending banner reaches it", shell.contains("fun GlobalAgentBanner"))
    }

    @Test
    fun theSessionListIsTheWebs() {
        val groups = File(src, "ui/sessions/SessionGroups.kt").readText()
        listOf("SessionGroupKind.Recent", "SessionGroupKind.Pinned", "SessionGroupKind.Category", "SessionGroupKind.Uncategorized")
            .forEach { kind -> assertTrue(groups.contains(kind)) }
        listOf(
            "CoreHubTokens.Metrics.longPressMs",
            "R.string.action_rename",
            "R.string.session_category",
            "R.string.session_archive",
            "R.string.action_delete",
            "CoreHubTokens.Alpha.DELETE_AFFORDANCE",
            "AgentAvatar(ChatAgentAvatars.forSession(session)",
            "CoreHubTextStyles.groupHeader",
            "group.label.uppercase()",
            "onRecentCount",
            "TextDirection.Content",
        ).forEach { needle -> assertTrue("session list lost $needle", sessionList.contains(needle)) }
    }

    @Test
    fun settingsIsOneScreenWithTheWebTabsAndAToolsSection() {
        val tabs = listOf(
            "SettingsGroup.Account", "SettingsGroup.Users", "SettingsGroup.Webhooks", "SettingsGroup.Display",
            "SettingsGroup.Proxy", "SettingsGroup.Compression", "SettingsGroup.Privacy", "SettingsGroup.Models",
            "SettingsGroup.Device", "SettingsGroup.About",
        ).map { settings.indexOf("SettingsTab($it,") }
        tabs.forEach { assertTrue(it >= 0) }
        assertEquals("settings tab order", tabs, tabs.sorted())
        assertTrue(settings.contains("SettingsTab(SettingsGroup.Users, R.string.settings_tab_account_management, superAdminOnly = true)"))
        assertTrue(settings.contains("SettingsTab(SettingsGroup.Webhooks, R.string.settings_tab_webhooks, superAdminOnly = true)"))
        // Settings-in-Settings must not come back, in any spelling.
        assertFalse(File(src, "ui/navigation/SettingsDrawerScreen.kt").exists())
        assertFalse(File(src, "ui/settings/SettingsPageScreen.kt").exists())
        assertFalse(viewModel.contains("Screen.SettingsPage") || viewModel.contains("Screen.MoreSettings") || viewModel.contains("Screen.SettingsGroup"))
        assertTrue("the title is the entry's own key", settings.contains("title = stringResource(NavDestination.settings.labelKey)"))
        // Tools: the registry's list, Performance and Profiles gated.
        assertTrue(settings.contains("NavDestination.settingsTools.filter"))
        assertTrue(settings.contains("setOf(NavDestination.performance, NavDestination.profiles)"))
        assertTrue(settings.contains("viewModel.openTool(destination)"))
        // Models here is the key form and says so, with a way to the Models page.
        assertTrue(settings.contains("R.string.settings_models_provider_keys"))
        assertTrue(settings.contains("R.string.settings_open_models_page"))
        assertTrue(settings.contains("ModelProvidersSettings(state, viewModel)"))
        // Webhooks are a tab body, not a screen behind a button.
        assertTrue(settings.contains("fun WebhooksSettingsBody"))
        assertFalse(settings.contains("openWebhooks"))
        // Hermes' own groups never show in the app's Settings.
        assertTrue(settings.contains("SettingsGroup.Agent, SettingsGroup.Memory, SettingsGroup.Sessions -> Unit"))
    }

    @Test
    fun eachToolIsItsOwnScreenTitledLikeItsEntry() {
        listOf(
            "NavDestination.logs -> openLogs()",
            "NavDestination.usage -> openUsage()",
            "NavDestination.performance -> openPerformance()",
            "NavDestination.skillsUsage -> openSkillsUsage()",
            "NavDestination.theme -> openTheme()",
            "NavDestination.pets -> openPets()",
            "NavDestination.profiles -> openProfiles()",
        ).forEach { branch -> assertTrue("openTool lost $branch", viewModel.contains(branch)) }
        // Usage and Performance load different things and show different screens.
        assertTrue(viewModel.contains("Screen.Usage") && viewModel.contains("api.usageStats(days)"))
        assertTrue(viewModel.contains("Screen.Performance") && viewModel.contains("api.runtimePerformance()"))
        assertTrue(insights.contains("fun UsageScreen") && insights.contains("InsightsScaffold(NavDestination.usage"))
        assertTrue(insights.contains("fun PerformanceScreen") && insights.contains("InsightsScaffold(NavDestination.performance"))
        assertTrue("Performance lists the runtime processes", insights.contains("items(runtime.workers"))
        // Skills Usage and Journey are two screens over two APIs.
        assertTrue(insights.contains("InsightsScaffold(NavDestination.skillsUsage") && viewModel.contains("api.skillUsage(days)"))
        assertTrue(parity.contains("fun JourneyScreen") && parity.contains("NavDestination.journey.labelKey"))
        assertTrue(viewModel.contains("fun refreshJourney() = launchWork(work = { api.journey() }"))
        // Theme and Pets are titled by the same key as their rows.
        assertTrue(parity.contains("fun ThemeScreen") && parity.contains("NavDestination.theme.labelKey"))
        assertTrue(tools.contains("stringResource(R.string.nav_pets)"))
        assertFalse(tools.contains("pets_title") || parity.contains("appearance_title"))
        // Device connections: its own title, App (Direct / Message push) and Devices for a super-admin.
        assertTrue(workspace.contains("NavDestination.deviceConnections.labelKey"))
        assertTrue(workspace.contains("listOf(R.string.connections_tab_app) + if (state.isSuperAdmin) listOf(R.string.connections_tab_devices)"))
        assertTrue(workspace.contains("listOf(R.string.connections_app_direct, R.string.connections_app_push)"))
    }

    @Test
    fun everyAgentToolLivesUnderItsAgentsCard() {
        // The Agent Manager is cards, and a card is the only way under an agent.
        assertFalse("the Hermes tools grab-bag must not come back", activity.contains("HermesToolsSection") || agentManager.contains("hermesTools"))
        assertTrue(agentManager.contains("modifier = Modifier.fillMaxWidth().clickable(onClick = openAgent)"))
        // The runtime installer is the desktop app's; the phone keeps CLI details only.
        assertFalse("the runtime-versions sheet must not come back", agentManager.contains("RuntimeManagerSheet") || agentScreen.contains("RuntimeManagerSheet"))
        assertFalse(agentManager.contains("agent_manage_runtime") || viewModel.contains("loadRuntimeVersions") || viewModel.contains("RuntimeVersions"))
        assertTrue(agentManager.contains("R.string.agent_cli_details") && agentScreen.contains("fun HermesCliDetailsDialog"))
        // The update offer is one compact button under the state pill; the version is on the meta line.
        assertTrue(agentManager.contains("if (offered != null) UpdateButton(enabled = !busy)"))
        assertTrue(agentManager.contains("offered?.let { stringResource(R.string.agent_update_offered, it) }"))
        assertTrue(agentManager.contains("maxLines = 1,\n            softWrap = false,"))
        assertFalse("the standalone Agents (runtimes) entry is gone", viewModel.contains("Screen.AgentRuntimes") || activity.contains("AgentRuntimeScreen"))
        assertTrue("Agent Manager is opened from the drawer rail", viewModel.contains("NavDestination.agentManager -> openAgentManager()"))
        // The section list is the registry's, and every section resolves to a screen.
        assertTrue(agentScreen.contains("NavDestination.agentSections(agent.kind, agent.id)"))
        assertTrue(agentScreen.contains("viewModel.openAgentSection(agent, destination)"))
        // `AgentSectionRouteTest` walks every row of every agent kind through the
        // resolver; here we pin that the view-model calls it and that every screen
        // it can resolve to has an opener that actually sets that screen.
        assertTrue(viewModel.contains("val route = AgentSectionRoute.resolve(definition, destination) ?: return"))
        listOf(
            "Screen.CronJobs -> openCronJobs()", "Screen.Kanban -> openKanban()", "Screen.Channels -> openChannels()",
            "Screen.Plugins -> openPlugins()", "Screen.DshPlugins -> openDshPlugins()", "Screen.DshPresets -> openDshPresets()",
            "Screen.Journey -> openJourney()", "Screen.HermesSettings -> openHermesSettings()", "Screen.EkkoSettings -> openEkkoSettings()",
            "Screen.AgentSettings -> openAgentSettings(definition)", "Screen.Skills -> openSkills(route.skillsTarget",
            "Screen.EkkoSkills -> openEkkoSkills()", "Screen.Mcp -> openMcp(route.mcpAgentId)", "Screen.EkkoMcp -> openEkkoMcp()",
            "Screen.Memory -> openHermesMemory()", "Screen.EkkoMemory -> openEkkoMemory()",
        ).forEach { branch -> assertTrue("openAgentSection lost $branch", viewModel.contains(branch)) }
        mapOf(
            "fun openCronJobs()" to "screen = Screen.CronJobs", "fun openKanban()" to "screen = Screen.Kanban",
            "fun openChannels()" to "screen = Screen.Channels", "fun openPlugins()" to "screen = Screen.Plugins",
            "fun openDshPlugins()" to "screen = Screen.DshPlugins", "fun openDshPresets()" to "screen = Screen.DshPresets",
            "fun openJourney()" to "screen = Screen.Journey", "fun openHermesSettings(" to "screen = Screen.HermesSettings",
            "fun openEkkoSettings()" to "screen = Screen.EkkoSettings", "fun openAgentSettings(" to "screen = Screen.AgentSettings",
            "fun openSkills(" to "screen = Screen.Skills, skillsUi = it.skillsUi.copy(target = target",
            "fun openMcp(" to "screen = Screen.Mcp, mcpUi = it.mcpUi.copy(agentId = agentId",
            "fun openHermesMemory()" to "screen = Screen.Memory",
        ).forEach { (opener, effect) ->
            val body = viewModel.substringAfter(opener).substringBefore("\n    fun ")
            assertTrue("$opener does not set $effect", body.contains(effect))
        }
        listOf("fun openEkkoMemory() = openEkko(Screen.EkkoMemory)", "fun openEkkoSkills() = openEkko(Screen.EkkoSkills)", "fun openEkkoMcp() = openEkko(Screen.EkkoMcp)")
            .forEach { assertTrue(viewModel.contains(it)) }
        assertTrue(viewModel.substringAfter("private fun openEkko(screen: Screen)").substringBefore("\n    fun ").contains("screen = screen"))
        val route = File(src, "navigation/AgentSectionRoute.kt").readText()
        assertTrue(route.contains("if (ekko) AgentSectionRoute(Screen.EkkoSkills)") && route.contains("AgentSectionRoute(Screen.Skills, skillsTarget = skillsTarget(definition))"))
        assertTrue(route.contains("AgentSectionRoute(if (ekko) Screen.EkkoMemory else Screen.Memory)"))
        // Hermes › Settings: Agent (+ gateway auto-start), Memory, Session (approvals, skill approvals, reset).
        assertTrue(hermes.contains("SettingsGroup.Agent -> AgentSettings(state, viewModel)"))
        assertTrue(hermes.contains("SettingsGroup.Memory -> MemoryStudioSettings(state, viewModel)"))
        assertTrue(hermes.contains("else -> SessionStudioSettings(state, viewModel)"))
        assertTrue(activity.contains("internal fun AgentSettings(") && activity.contains("val policy = state.autoStart"))
        val session = File(src, "StudioSettings.kt").readText().substringAfter("internal fun SessionStudioSettings").substringBefore("internal fun PrivacyStudioSettings")
        assertTrue(session.contains("\"approvals\", \"mode\"") && session.contains("\"skills\", \"write_approval\"") && session.contains("\"session_reset\", \"mode\""))
        // Skills still expose pending approvals with approve and reject.
        assertTrue(tools.contains("pendingWrites") && tools.contains("resolvePendingSkillWrite"))
        // Agent tools never open the website.
        assertFalse(agentScreen.contains("ACTION_VIEW") || agentManager.contains("ACTION_VIEW"))
    }

    /**
     * `CodingAgentConfigSidebar.vue:19-24`: Plugins and Presets exist for the
     * DeepSeek Harness alone, each its own screen titled by its row's key,
     * and each says on screen what stays on the desktop.
     */
    @Test
    fun theDeepSeekHarnessAloneListsPluginsAndPresets() {
        val dsh = File(src, "ui/agents/DshScreens.kt").readText()
        assertTrue(viewModel.contains("Screen.DshPlugins") && viewModel.contains("Screen.DshPresets"))
        assertTrue(activity.contains("Screen.DshPlugins -> DshPluginsScreen(state, viewModel)"))
        assertTrue(activity.contains("Screen.DshPresets -> DshPresetsScreen(state, viewModel)"))
        assertTrue("Plugins is titled by its row", dsh.contains("stringResource(NavDestination.plugins.labelKey)"))
        assertTrue("Presets is titled by its row", dsh.contains("stringResource(NavDestination.presets.labelKey)"))
        // Read/select first: the roster, the default, one file, and choosing the default.
        assertTrue(viewModel.contains("api.dshAgentPresets()") && viewModel.contains("api.setDefaultDshAgentPreset(preset.id)"))
        assertTrue(viewModel.contains("api.readDshAgentPreset(preset.id)") && viewModel.contains("api.dshPluginInventory()"))
        assertTrue(dsh.contains("viewModel.selectDshPreset(preset)") && dsh.contains("viewModel.viewDshPreset(preset)"))
        // What the phone cannot do is stated, not hidden.
        assertTrue(dsh.contains("R.string.dsh_plugins_web_note") && dsh.contains("R.string.dsh_presets_note"))
        assertFalse("no web-package install on the phone", viewModel.contains("web-plugins") || viewModel.contains("ui-session"))
        // Hermes' own Plugins screen is untouched; the dsh row is the only way to the inventory.
        assertTrue(File(src, "AgentToolScreens.kt").readText().contains("fun PluginsScreen(state: UiState, viewModel: AppViewModel)"))
    }

    @Test
    fun profilesHasOneEntryAndFilesIsBehindAProfileCard() {
        val ui = src.walkTopDown().filter { it.extension == "kt" && it.name != "AppViewModel.kt" }.map { it.readText() }.toList()
        assertEquals("Profiles is opened through Settings › Tools only", 0, ui.count { it.contains("viewModel.openProfiles()") })
        assertEquals("Files is opened from a profile card's Edit config only", 1, ui.count { it.contains("viewModel.openProfileConfig(") })
        assertTrue(activity.contains("R.string.profile_edit_config"))
        assertFalse(activity.contains("show(Screen.Profiles)"))
    }

    @Test
    fun backFollowsTheVisitHistoryNotAFixedTable() {
        assertFalse(viewModel.contains("toolReturnScreen") || viewModel.contains("profilesReturnScreen"))
        val back = viewModel.substringAfter("    fun back() {").substringBefore("    fun show(screen: Screen)")
        assertTrue(back.contains("navigationHistory.removeLastOrNull()"))
        assertTrue(back.contains("val target = visitedTarget ?: state.tab.rootScreen("))
        assertFalse("no static parent table", back.contains("-> Screen.Settings") || back.contains("-> Screen.AgentManager"))
        // The unreachable copy-pasted branches are gone with the table they served.
        assertFalse(viewModel.contains("Screen.SettingsPage -> Screen.SettingsPage"))
    }

    @Test
    fun chatHeaderCarriesTitleWorkspaceChipAndActions() {
        val header = chat.substringAfter("internal fun ChatHeader").substringBefore("private fun HeaderChip")
        assertTrue(header.contains("MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Content)"))
        assertTrue(header.contains("workspaceChipLabel(session?.workspace)"))
        assertTrue(header.contains("CoreHubIcons.More"))
        assertTrue(header.contains("navigationIcon = { MenuButton(onMenu) }"))
        val bubble = chat.substringAfter("internal fun MessageBubble").substringBefore("internal fun quoteForReply")
        assertTrue(bubble.contains("palette.msgUser"))
        assertTrue(bubble.contains("palette.msgAssistant"))
        assertTrue(bubble.contains("CoreHubTokens.Radius.bubble"))
        val composer = File(src, "ui/chat/Composer.kt").readText()
        assertTrue(composer.contains("CoreHubTokens.Radius.composer"))
        assertTrue("composer text never below 16 sp", composer.contains("textStyle = CoreHubTextStyles.input.copy("))
        assertTrue(composer.contains("CoreHubTokens.Radius.pill"))
    }

    @Test
    fun kanbanHasNativeAccessibleMovementInBothDirections() {
        assertTrue(kanban.contains("detectDragGesturesAfterLongPress"))
        assertTrue(kanban.contains("LocalLayoutDirection.current"))
        assertTrue(kanban.contains("graphicsLayer { translationX = dragX }"))
        assertTrue(kanban.contains("DropdownMenuItem"))
    }

    @Test
    fun conversationDoesNotReserveSystemBarAboveTheKeyboard() {
        val conversation = chat.substringAfter("fun ConversationScreen")
            .substringBefore("internal fun ChatHeader")
        assertTrue(conversation.contains("contentWindowInsets = WindowInsets(0, 0, 0, 0)"))
        assertFalse(conversation.contains("bottomBar = { StudioTabs(state, viewModel) }"))
        assertTrue(conversation.contains(".imePadding()"))
    }
}
