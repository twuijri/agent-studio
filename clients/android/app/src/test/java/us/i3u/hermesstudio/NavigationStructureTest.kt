package us.i3u.hermesstudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Keeps the mobile information architecture equal to the web app's
 * (docs/mobile/DESIGN-SPEC.md): an off-canvas drawer with the primary rail,
 * the four-segment conversation switch, the session list and the footer; a
 * settings drawer; and a Settings page with the web's tab order. It also makes
 * sure the tools that used to live in the old bottom tabs are still reachable.
 */
class NavigationStructureTest {

    private val src = File("src/main/java/us/i3u/hermesstudio")
    private val viewModel = File(src, "AppViewModel.kt").readText()
    private val activity = File(src, "MainActivity.kt").readText()
    private val drawer = File(src, "ui/navigation/CoreHubDrawer.kt").readText()
    private val shell = File(src, "ui/navigation/HomeShell.kt").readText()
    private val settingsDrawer = File(src, "ui/navigation/SettingsDrawerScreen.kt").readText()
    private val settingsPage = File(src, "ui/settings/SettingsPageScreen.kt").readText()
    private val sessionList = File(src, "ui/sessions/SessionList.kt").readText()
    private val chat = File(src, "ui/chat/ConversationScreen.kt").readText()
    private val kanban = File(src, "KanbanScreens.kt").readText()

    @Test
    fun theRootIsTheWebsFourSegmentSwitchNotThreeBottomTabs() {
        assertTrue(viewModel.contains("enum class Tab { Chat, Group, Workflow, History }"))
        assertFalse("the old bottom navigation bar must be gone", activity.contains("fun StudioTabs"))
        src.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            assertFalse("${file.name} still mounts the old bottom tabs", file.readText().contains("bottomBar = { StudioTabs"))
        }
        listOf(Tab.Chat, Tab.Group, Tab.Workflow, Tab.History).forEach { tab ->
            assertTrue("drawer switch must offer $tab", drawer.contains("Tab.$tab to R.string.segment_"))
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
    fun theDrawerFollowsTheSpecFromTopToBottom() {
        val rail = listOf("CoreHubIcons.NewChat", "CoreHubIcons.Search", "CoreHubIcons.DeviceConnections", "CoreHubIcons.AgentManager", "CoreHubIcons.Models")
        val positions = rail.map { icon -> drawer.indexOf("RailItem($icon") }
        positions.forEachIndexed { index, position -> assertTrue("rail is missing ${rail[index]}", position >= 0) }
        assertEquals("rail order must be New Chat, Search, Device connections, Agent Manager, Models", positions, positions.sorted())
        assertTrue("Agent Manager is super-admin only", Regex("""if \(state\.isSuperAdmin\) \{\s*RailItem\(CoreHubIcons\.AgentManager""").containsMatchIn(drawer))
        assertFalse("Computer apps is desktop-only and must not appear on phones", drawer.contains("ComputerApps"))

        // The rail and the switch are the header items of the session list's scroll,
        // so on screen the order is rail → switch → sessions → footer.
        val switchAt = drawer.indexOf("ConversationSwitch(")
        val listAt = drawer.indexOf("SessionListPane(")
        val headerAt = drawer.indexOf("header = {", listAt)
        val footerAt = drawer.indexOf("DrawerFooter(state")
        assertTrue("rail and switch live in the session list header", listAt < headerAt && headerAt < positions.first())
        assertTrue("rail, then switch, then sessions, then footer", positions.last() < switchAt && switchAt < footerAt)

        listOf(
            "viewModel.selectProfile(profile.name)",
            "viewModel.selectModel(option)",
            "R.string.action_sign_out",
            "state.account?.takeIf { it.isNotBlank() }",
            "if (state.connected) R.string.connected else R.string.disconnected",
            "R.string.footer_version, state.serverVersion ?: BuildConfig.VERSION_NAME",
            "LanguageAction(state, viewModel)",
            "viewModel.openSettings()",
        ).forEach { needle -> assertTrue("footer lost $needle", drawer.contains(needle)) }
        assertTrue("250 ms slide", drawer.contains("tween(CoreHubTokens.Metrics.drawerSlideMs)"))
        assertTrue("40 % scrim", drawer.contains("CoreHubTokens.Metrics.scrimAlpha"))
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
    fun settingsDrawerAndSettingsPageKeepTheWebOrder() {
        val entries = listOf(
            "R.string.settings_entry_logs", "R.string.settings_entry_usage", "R.string.settings_entry_performance",
            "R.string.settings_entry_skills_usage", "R.string.settings_entry_theme", "R.string.settings_entry_pets",
            "R.string.settings_entry_profiles", "R.string.settings_entry_settings",
        ).map { settingsDrawer.indexOf(it) }
        entries.forEach { assertTrue(it >= 0) }
        assertEquals("settings drawer order", entries, entries.sorted())
        assertTrue(settingsDrawer.contains("R.string.settings_entry_performance, superAdminOnly = true"))
        assertTrue(settingsDrawer.contains("R.string.settings_entry_profiles, superAdminOnly = true"))

        val tabs = listOf(
            "SettingsGroup.Account", "SettingsGroup.Users", "SettingsGroup.Webhooks", "SettingsGroup.Display",
            "SettingsGroup.Proxy", "SettingsGroup.Compression", "SettingsGroup.Privacy", "SettingsGroup.Models",
        ).map { settingsPage.indexOf("SettingsTab($it,") }
        tabs.forEach { assertTrue(it >= 0) }
        assertEquals("settings page tab order", tabs, tabs.sorted())
        assertFalse("Settings-in-Settings must not come back", activity.contains("openMoreSettings()"))
        assertFalse(viewModel.contains("Screen.MoreSettings"))
    }

    @Test
    fun everyAgentToolIsStillReachableFromTheAgentManager() {
        val hub = activity.substringAfter("private fun AgentHubScreen")
            .substringBefore("@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nprivate fun InsightsScreen")
        listOf(
            "openCronJobs()", "openChannels()", "SettingsGroup.Memory", "SettingsGroup.Models", "SettingsGroup.Agent",
            "SettingsGroup.Sessions", "SettingsGroup.Compression", "openKanban()", "openSkills()", "openPlugins()",
            "openMcp()", "openAgentRuntimes()", "showTab(Tab.Workflow)", "openGlobalAgent()", "openEkkoHub()",
            "openFiles()", "openLogs()", "openConnections()", "openJourney()", "openWebhooks()", "openInsights()",
            "openRuntimeVersions()", "openAppearance()",
        ).forEach { destination -> assertTrue("Agent Manager lost $destination", hub.contains(destination)) }
        assertTrue("Agent Manager is opened from the drawer rail", drawer.contains("viewModel.openAgentManager()"))
        assertFalse("Agent tools must never open the website", hub.contains("ACTION_VIEW"))
        val skills = File(src, "AgentToolScreens.kt").readText()
        assertTrue("Skills must expose pending approvals", skills.contains("pendingWrites"))
        assertTrue("Skills approvals must offer approve and reject", skills.contains("resolvePendingSkillWrite"))
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
    fun profilesReturnsToTheScreenThatOpenedIt() {
        assertTrue(viewModel.contains("val profilesReturnScreen: Screen"))
        assertTrue(viewModel.contains("Screen.Profiles -> state.profilesReturnScreen"))
        assertTrue(viewModel.contains("fun openProfiles()"))
        assertFalse("callers must not bypass origin tracking", activity.contains("show(Screen.Profiles)"))
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
