package us.i3u.hermesstudio

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * The Models page and the Agent Manager against `tools/mock-studio.py`,
 * through the real [HermesApi].
 *
 * The parse tests pin the shapes; this pins the wire — that the paths the two
 * screens call exist, that the round trips actually change something, and
 * that the honest failure path (an install the server refuses) comes back as
 * a message rather than an exception. Skipped when the machine has no python3.
 */
class MockStudioModelsAgentsTest {

    private val mock = File("../tools/mock-studio.py")
    private var process: Process? = null
    private lateinit var api: HermesApi

    @Before
    fun startMock() {
        assumeTrue("python3 is required to run the mock server", python3() != null)
        assumeTrue("tools/mock-studio.py must be next to the app module", mock.isFile)
        val started = ProcessBuilder(python3()!!, mock.absolutePath, "0")
            .directory(mock.parentFile)
            .redirectErrorStream(true)
            .start()
        process = started
        val banner = started.inputStream.bufferedReader().readLine().orEmpty()
        val port = Regex(":(\\d+)").find(banner)?.groupValues?.get(1)
        assumeTrue("the mock did not report a port: $banner", port != null)
        api = HermesApi("http://127.0.0.1:$port", "mock-token")
        api.activeProfile = "manager"
    }

    @After
    fun stopMock() {
        process?.destroy()
        process?.waitFor()
    }

    // ---- Models page ------------------------------------------------------

    @Test
    fun `the models page gets providers with their catalogues`() {
        val catalog = api.modelCatalog("manager")

        assertEquals("claude-opus-5", catalog.defaultModel)
        assertEquals("anthropic", catalog.defaultProvider)
        assertTrue(
            "every configured provider should be listed",
            catalog.providers.map { it.id }.containsAll(
                listOf("anthropic", "openai", "custom:subrouter.ai", "opencode-free"),
            ),
        )

        val anthropic = catalog.providers.first { it.id == "anthropic" }
        assertTrue(anthropic.configured)
        assertEquals("Opus", anthropic.models.first { it.id == "claude-opus-5" }.alias)
        assertTrue("a custom model should be part of the catalogue", anthropic.models.any { it.custom })
        assertFalse("a model outside the include rule is hidden", anthropic.models.first { it.id == "claude-haiku-5" }.visible)

        val openai = catalog.providers.first { it.id == "openai" }
        assertFalse("an empty api_key is not a configured credential", openai.configured)

        assertEquals("loading", catalog.providers.first { it.id == "opencode-free" }.catalogStatus)
    }

    @Test
    fun `the fallback chain survives a round trip`() {
        val original = api.fallbackProviders("manager")
        assertEquals(2, original.size)

        api.saveFallbackProviders("manager", original.reversed())

        assertEquals(original.reversed(), api.fallbackProviders("manager"))
    }

    @Test
    fun `the model cache can be refreshed from the page header`() {
        api.refreshModelCache()
    }

    // ---- Agent Manager ----------------------------------------------------

    @Test
    fun `all six coding agents are listed, not a subset`() {
        val tools = api.codingAgents()

        assertEquals(
            listOf("claude-code", "codex", "pi", "grok", "opencode", "dsh"),
            tools.map { it.id },
        )
    }

    @Test
    fun `the merged catalogue covers every agent Core Hub knows`() {
        val cards = AgentCatalog.merge(
            tools = api.codingAgents(),
            snapshot = api.agentStatusSnapshot(),
            policies = api.agentUpdatePolicies(),
            anyServerAnswer = true,
        )

        assertEquals(8, cards.size)
        assertTrue("nothing should be missing from the server", cards.none { it.presence == AgentPresence.Unsupported })
        val byId = cards.associateBy { it.id }
        assertEquals("0.21.3", byId.getValue("hermes").version)
        assertEquals(AgentPresence.Installed, byId.getValue("ekko-agent").presence)
        assertEquals("1.3.0", byId.getValue("claude-code").updateVersion)
        assertEquals(AgentPresence.NotInstalled, byId.getValue("dsh").presence)
    }

    @Test
    fun `installing an agent changes what the server then reports`() {
        assertFalse(api.codingAgents().first { it.id == "opencode" }.installed)

        val result = api.installCodingAgent("opencode")

        assertTrue(result.message, result.success)
        assertTrue(api.codingAgents().first { it.id == "opencode" }.installed)
    }

    /** The server answers 200 with `success: false` when npm is unavailable. */
    @Test
    fun `an install the server refuses is a message, not an exception`() {
        val result = api.installCodingAgent("grok")

        assertFalse(result.success)
        assertEquals("npm_missing", result.code)
        assertTrue(result.message.contains("Node/npm"))
        assertFalse(api.codingAgents().first { it.id == "grok" }.installed)
    }

    @Test
    fun `deleting an agent puts it back to not installed`() {
        assertTrue(api.codingAgents().first { it.id == "codex" }.installed)

        val result = api.deleteCodingAgent("codex")

        assertTrue(result.success)
        val codex = api.codingAgents().first { it.id == "codex" }
        assertFalse(codex.installed)
        assertEquals("not-installed", codex.source)
    }

    @Test
    fun `checking for an update reports the version the policy advertises`() {
        val check = api.checkCodingAgentUpdate("claude-code")

        assertTrue(check.success)
        assertTrue(check.updateAvailable)
        assertEquals("1.3.0", check.latestVersion)

        assertFalse(api.checkCodingAgentUpdate("codex").updateAvailable)
    }

    @Test
    fun `the auto-update switch is written back`() {
        assertFalse(api.agentUpdatePolicies().getValue("codex").autoUpdate)

        api.setAgentAutoUpdate("codex", true)

        assertTrue(api.agentUpdatePolicies().getValue("codex").autoUpdate)
    }

    // ---- per-agent settings -----------------------------------------------

    @Test
    fun `an agent's two settings files are readable and writable`() {
        val definition = AgentCatalog.definition("claude-code")!!

        val instructions = api.codingAgentConfigFile(definition.id, definition.preferenceKey)
        assertEquals("~/.claude/CLAUDE.md", instructions.path)
        assertEquals("markdown", instructions.language)
        assertTrue(instructions.exists)

        val configuration = api.codingAgentConfigFile(definition.id, definition.configurationKey)
        assertEquals("json", configuration.language)
        assertTrue(configuration.content.contains("sonnet"))

        val edited = api.saveCodingAgentConfigFile(
            definition.id,
            definition.preferenceKey,
            "# Project notes\n\nأجب بالعربية دائمًا.\n",
        )
        assertTrue("the edit must survive the round trip", edited.content.contains("أجب بالعربية"))
        assertNotEquals(instructions.content, api.codingAgentConfigFile(definition.id, definition.preferenceKey).content)
    }

    @Test
    fun `a settings file that does not exist yet still opens`() {
        val definition = AgentCatalog.definition("dsh")!!

        val file = api.codingAgentConfigFile(definition.id, definition.configurationKey)

        assertFalse(file.exists)
        assertEquals("", file.content)
    }

    private fun python3(): String? = sequenceOf("/usr/bin/python3", "python3")
        .firstOrNull { candidate ->
            runCatching {
                ProcessBuilder(candidate, "--version").redirectErrorStream(true).start().waitFor() == 0
            }.getOrDefault(false)
        }
}
