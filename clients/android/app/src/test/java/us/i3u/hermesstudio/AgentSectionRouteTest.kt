package us.i3u.hermesstudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import us.i3u.hermesstudio.navigation.AgentSectionRoute
import us.i3u.hermesstudio.navigation.NavDestination

/**
 * Every capability row of every agent in the catalogue lands on a screen, the
 * right one, with the right agent in scope. On iOS build 41 the rows did
 * nothing (a SwiftUI navigation-registration slip); Android resolves them in
 * [AgentSectionRoute], which `AppViewModel.openAgentSection` dispatches on, so
 * the decision is checked here for all 8 agents rather than by tapping.
 */
class AgentSectionRouteTest {

    private val hermes = AgentCatalog.definition("hermes")!!
    private val ekko = AgentCatalog.definition("ekko-agent")!!
    private val coding = AgentCatalog.ALL.filter { it.kind == AgentKind.Coding }
    private val dsh = AgentCatalog.definition(DSH_AGENT_ID)!!

    private fun route(agent: AgentDefinition, destination: NavDestination) =
        AgentSectionRoute.resolve(agent, destination) ?: error("${agent.id} › ${destination.name} resolves to nothing")

    @Test
    fun hermesRowsLandOnTheHermesScreens() {
        assertEquals(
            listOf(Screen.CronJobs, Screen.Kanban, Screen.Channels, Screen.Skills, Screen.Plugins, Screen.Mcp, Screen.Memory, Screen.Journey, Screen.HermesSettings),
            NavDestination.agentSections(AgentKind.Hermes).map { route(hermes, it).screen },
        )
        assertEquals("hermes", route(hermes, NavDestination.skills).skillsTarget)
        assertNull("Hermes MCP is the server's own, not a coding agent's", route(hermes, NavDestination.mcp).mcpAgentId)
    }

    @Test
    fun ekkoRowsLandOnEkkosOwnFourScreens() {
        assertEquals(
            listOf(Screen.EkkoMemory, Screen.EkkoSkills, Screen.EkkoMcp, Screen.EkkoSettings),
            NavDestination.agentSections(AgentKind.BuiltIn).map { route(ekko, it).screen },
        )
        assertNull(route(ekko, NavDestination.skills).skillsTarget)
        assertNull(route(ekko, NavDestination.mcp).mcpAgentId)
    }

    @Test
    fun everyCodingAgentRowLandsWithThatAgentInScope() {
        assertEquals(6, coding.size)
        coding.forEach { agent ->
            val skills = route(agent, NavDestination.skills)
            assertEquals(Screen.Skills, skills.screen)
            assertEquals(if (agent.id == "claude-code") "claude" else agent.id, skills.skillsTarget)
            val mcp = route(agent, NavDestination.mcp)
            assertEquals(Screen.Mcp, mcp.screen)
            assertEquals("MCP for ${agent.id} must edit /api/coding-agents/${agent.id}/mcp", agent.id, mcp.mcpAgentId)
            assertEquals(Screen.AgentSettings, route(agent, NavDestination.codingAgentSettings).screen)
        }
    }

    @Test
    fun onlyDshHasPluginsAndPresetsAndTheyAreItsOwn() {
        assertEquals(
            listOf(Screen.DshPlugins, Screen.DshPresets, Screen.Skills, Screen.Mcp, Screen.AgentSettings),
            NavDestination.agentSections(AgentKind.Coding, dsh.id).map { route(dsh, it).screen },
        )
        coding.filter { it.id != DSH_AGENT_ID }.forEach { agent ->
            assertEquals(listOf(Screen.Skills, Screen.Mcp, Screen.AgentSettings), NavDestination.agentSections(agent.kind, agent.id).map { route(agent, it).screen })
            assertNull("${agent.id} has no Plugins row", AgentSectionRoute.resolve(agent, NavDestination.plugins))
            assertNull("${agent.id} has no Presets row", AgentSectionRoute.resolve(agent, NavDestination.presets))
        }
        assertEquals("Hermes Plugins are the Hermes plugins, not dsh's", Screen.Plugins, route(hermes, NavDestination.plugins).screen)
    }

    @Test
    fun everyResolvedScreenIsOneTheRegistryDeclaresForThatDestination() {
        AgentCatalog.ALL.forEach { agent ->
            NavDestination.agentSections(agent.kind, agent.id).forEach { destination ->
                val resolved = route(agent, destination)
                assertTrue("${agent.id} › ${destination.name} lands on ${resolved.screen}, which the registry does not list", resolved.screen in destination.screens)
                assertTrue(resolved.skillsTarget == null || resolved.screen == Screen.Skills)
                assertTrue(resolved.mcpAgentId == null || resolved.screen == Screen.Mcp)
            }
        }
        // A row that is not one of the agent's sections is refused, not sent somewhere odd.
        assertNull(AgentSectionRoute.resolve(ekko, NavDestination.jobs))
        assertNull(AgentSectionRoute.resolve(hermes, NavDestination.ekkoSettings))
        assertNull(AgentSectionRoute.resolve(dsh, NavDestination.hermesSettings))
    }
}
