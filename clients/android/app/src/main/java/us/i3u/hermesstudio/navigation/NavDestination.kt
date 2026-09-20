package us.i3u.hermesstudio.navigation

import androidx.annotation.StringRes
import us.i3u.hermesstudio.AgentKind
import us.i3u.hermesstudio.R
import us.i3u.hermesstudio.Screen

/**
 * The one navigation registry both phones implement (`docs/mobile/NAVIGATION.md`).
 *
 * Every place a person can go has exactly one case here, and the case names
 * are shared verbatim with `clients/ios/HermesStudio/Core/NavDestination.swift`
 * — `NavigationParityTest` reads both files and fails on any drift. The label
 * key is `nav_<snake_case of the case>`; the entry that opens a destination
 * and the title of the screen it opens both read that same key, which is what
 * stops "I press Theme and something called Appearance opens".
 *
 * [screens] lists the `Screen` values a destination lands on (a capability
 * such as Skills exists once per agent, so it may own more than one screen).
 * A destination with no screen is a sheet or a draft over another screen.
 */
enum class NavDestination(@StringRes val labelKey: Int, val screens: Set<Screen> = emptySet()) {
    newChat(R.string.nav_new_chat),
    search(R.string.nav_search),
    deviceConnections(R.string.nav_device_connections, setOf(Screen.Connections)),
    agentManager(R.string.nav_agent_manager, setOf(Screen.AgentManager)),
    models(R.string.nav_models, setOf(Screen.Models)),
    chat(R.string.nav_chat, setOf(Screen.Chats)),
    groupChat(R.string.nav_group_chat, setOf(Screen.Groups)),
    workflow(R.string.nav_workflow, setOf(Screen.Workflows)),
    history(R.string.nav_history, setOf(Screen.History)),
    settings(R.string.nav_settings, setOf(Screen.Settings)),
    logs(R.string.nav_logs, setOf(Screen.Logs)),
    usage(R.string.nav_usage, setOf(Screen.Usage)),
    performance(R.string.nav_performance, setOf(Screen.Performance)),
    skillsUsage(R.string.nav_skills_usage, setOf(Screen.SkillsUsage)),
    theme(R.string.nav_theme, setOf(Screen.Theme)),
    pets(R.string.nav_pets, setOf(Screen.Pets)),
    profiles(R.string.nav_profiles, setOf(Screen.Profiles)),
    conversation(R.string.nav_conversation, setOf(Screen.Conversation)),
    room(R.string.nav_room, setOf(Screen.Room)),
    workflowDetail(R.string.nav_workflow_detail, setOf(Screen.Workflow)),
    workflowRun(R.string.nav_workflow_run, setOf(Screen.WorkflowRun)),
    agentHermes(R.string.nav_agent_hermes, setOf(Screen.Agent)),
    agentEkko(R.string.nav_agent_ekko, setOf(Screen.Agent)),
    agentCoding(R.string.nav_agent_coding, setOf(Screen.Agent)),
    jobs(R.string.nav_jobs, setOf(Screen.CronJobs)),
    kanban(R.string.nav_kanban, setOf(Screen.Kanban)),
    channels(R.string.nav_channels, setOf(Screen.Channels)),
    skills(R.string.nav_skills, setOf(Screen.Skills, Screen.EkkoSkills)),
    plugins(R.string.nav_plugins, setOf(Screen.Plugins)),
    mcp(R.string.nav_mcp, setOf(Screen.Mcp, Screen.EkkoMcp)),
    memory(R.string.nav_memory, setOf(Screen.Memory, Screen.EkkoMemory)),
    journey(R.string.nav_journey, setOf(Screen.Journey)),
    hermesSettings(R.string.nav_hermes_settings, setOf(Screen.HermesSettings)),
    ekkoSettings(R.string.nav_ekko_settings, setOf(Screen.EkkoSettings)),
    codingAgentSettings(R.string.nav_coding_agent_settings, setOf(Screen.AgentSettings)),
    globalAgent(R.string.nav_global_agent, setOf(Screen.GlobalAgent)),
    files(R.string.nav_files, setOf(Screen.Files)),
    ;

    companion object {
        /** The drawer's primary rail, in `PageSidebarNav.vue:74-205` order. */
        val rail: List<NavDestination> = listOf(newChat, search, deviceConnections, agentManager, models)

        /** The four-segment switch (`PageSidebarNav.vue:207-288`). */
        val segments: List<NavDestination> = listOf(chat, groupChat, workflow, history)

        /** Settings › Tools, in `AppSidebar.vue:113-317` order. */
        val settingsTools: List<NavDestination> = listOf(logs, usage, performance, skillsUsage, theme, pets, profiles)

        /**
         * What lives under one agent's card (§4): capabilities first, settings
         * last. Hermes follows `HermesConfigSidebar.vue:67-241`, Ekko
         * `EkkoConfigSidebar.vue:54-79`, a coding agent
         * `CodingAgentConfigSidebar.vue:19-24`.
         */
        fun agentSections(kind: AgentKind): List<NavDestination> = when (kind) {
            AgentKind.Hermes -> listOf(jobs, kanban, channels, skills, plugins, mcp, memory, journey, hermesSettings)
            AgentKind.BuiltIn -> listOf(memory, skills, mcp, ekkoSettings)
            AgentKind.Coding -> listOf(skills, mcp, codingAgentSettings)
        }

        /** The destination an agent card itself opens. */
        fun agentCard(kind: AgentKind): NavDestination = when (kind) {
            AgentKind.Hermes -> agentHermes
            AgentKind.BuiltIn -> agentEkko
            AgentKind.Coding -> agentCoding
        }
    }
}
