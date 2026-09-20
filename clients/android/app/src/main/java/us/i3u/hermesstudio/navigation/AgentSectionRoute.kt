package us.i3u.hermesstudio.navigation

import us.i3u.hermesstudio.AgentDefinition
import us.i3u.hermesstudio.AgentKind
import us.i3u.hermesstudio.DSH_AGENT_ID
import us.i3u.hermesstudio.Screen

/**
 * Where one row under an agent card lands (NAVIGATION.md §4), decided without
 * the view-model so a unit test can walk every capability of every agent kind.
 *
 * The same destination is a different screen per agent: Skills is the Hermes
 * skills screen filtered by a `target` for Hermes and the coding agents but
 * Ekko's own screen for Ekko; MCP is one screen whose `agentId` is `null` for
 * Hermes and the agent id for a coding agent; Plugins is dsh's inventory for
 * dsh and the Hermes plugins otherwise. `AppViewModel.openAgentSection` maps
 * the resolved [screen] to its opener and passes [skillsTarget] / [mcpAgentId]
 * through, which `NavigationStructureTest` pins at the source level.
 */
data class AgentSectionRoute(
    val screen: Screen,
    /** The `target` SkillsView.vue filters by; only for [Screen.Skills]. */
    val skillsTarget: String? = null,
    /** The coding agent whose `/api/coding-agents/{id}/mcp` the MCP screen edits; only for [Screen.Mcp]. */
    val mcpAgentId: String? = null,
) {
    companion object {
        /** `claude` for Claude Code (its skills are filed under that name), otherwise the agent id. */
        fun skillsTarget(definition: AgentDefinition): String =
            if (definition.id == "claude-code") "claude" else definition.id

        /** `null` when the destination is not a section of that agent. */
        fun resolve(definition: AgentDefinition, destination: NavDestination): AgentSectionRoute? {
            if (destination !in NavDestination.agentSections(definition.kind, definition.id)) return null
            val ekko = definition.kind == AgentKind.BuiltIn
            return when (destination) {
                NavDestination.jobs -> AgentSectionRoute(Screen.CronJobs)
                NavDestination.kanban -> AgentSectionRoute(Screen.Kanban)
                NavDestination.channels -> AgentSectionRoute(Screen.Channels)
                NavDestination.plugins -> AgentSectionRoute(if (definition.id == DSH_AGENT_ID) Screen.DshPlugins else Screen.Plugins)
                NavDestination.presets -> AgentSectionRoute(Screen.DshPresets)
                NavDestination.journey -> AgentSectionRoute(Screen.Journey)
                NavDestination.hermesSettings -> AgentSectionRoute(Screen.HermesSettings)
                NavDestination.ekkoSettings -> AgentSectionRoute(Screen.EkkoSettings)
                NavDestination.codingAgentSettings -> AgentSectionRoute(Screen.AgentSettings)
                NavDestination.skills ->
                    if (ekko) AgentSectionRoute(Screen.EkkoSkills)
                    else AgentSectionRoute(Screen.Skills, skillsTarget = skillsTarget(definition))
                NavDestination.mcp -> when (definition.kind) {
                    AgentKind.BuiltIn -> AgentSectionRoute(Screen.EkkoMcp)
                    AgentKind.Coding -> AgentSectionRoute(Screen.Mcp, mcpAgentId = definition.id)
                    AgentKind.Hermes -> AgentSectionRoute(Screen.Mcp, mcpAgentId = null)
                }
                NavDestination.memory -> AgentSectionRoute(if (ekko) Screen.EkkoMemory else Screen.Memory)
                else -> null
            }
        }
    }
}
