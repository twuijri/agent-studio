package us.i3u.hermesstudio

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The two screens rebuilt around the web's own pages read the server through
 * these parsers, so the parsers are what the tests pin: the provider and model
 * shape behind the Models page, and the agent shape behind the Agent Manager.
 *
 * The response bodies are the shapes the server actually sends — see
 * `packages/server/src/modules/hermes/controllers/models.ts` for the models
 * envelope and `packages/server/src/modules/coding-agents/services/index.ts`
 * plus `modules/studio/public/agent-status-registry.ts` for the agent records.
 */
class ModelsAndAgentsParseTest {

    private lateinit var server: MockWebServer
    private lateinit var api: HermesApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = HermesApi(server.url("/").toString().trimEnd('/'), "token")
    }

    @After
    fun tearDown() = server.shutdown()

    private fun enqueue(body: String, code: Int = 200) {
        server.enqueue(
            MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body),
        )
    }

    // ---- providers and models ---------------------------------------------

    private val catalogBody = """
        {
          "default": "anthropic/claude-sonnet-4",
          "default_provider": "openrouter",
          "groups": [
            {
              "provider": "openrouter",
              "label": "OpenRouter",
              "base_url": "https://openrouter.ai/api/v1",
              "api_key": "sk-or-secret",
              "api_mode": "chat_completions",
              "builtin": true,
              "models": ["anthropic/claude-sonnet-4", "openai/gpt-5"],
              "available_models": ["anthropic/claude-sonnet-4", "openai/gpt-5", "meta/llama-4"],
              "model_refreshable": true,
              "model_restore_available": true,
              "model_meta": {
                "meta/llama-4": { "disabled": true },
                "openai/gpt-5": { "preview": true, "alias": "GPT 5" }
              }
            },
            {
              "provider": "custom:subrouter.ai",
              "label": "Subrouter",
              "base_url": "https://subrouter.ai/v1",
              "api_key": "",
              "builtin": false,
              "provider_source": "custom_providers",
              "provider_key": "subrouter",
              "provider_editable": true,
              "catalog_status": "loading",
              "models": ["sub/fast"]
            },
            {
              "provider": "moa",
              "label": "Mixture of agents",
              "base_url": "",
              "api_key": "",
              "models": ["blend"]
            }
          ],
          "model_aliases": { "openrouter": { "anthropic/claude-sonnet-4": "Sonnet" } },
          "model_visibility": {
            "openrouter": { "mode": "include", "models": ["anthropic/claude-sonnet-4", "openai/gpt-5"] }
          },
          "custom_models": { "openrouter": ["local/experiment"] }
        }
    """.trimIndent()

    @Test
    fun `the catalogue keeps the default model and its provider`() {
        enqueue(catalogBody)
        val catalog = api.modelCatalog("manager")

        assertEquals("anthropic/claude-sonnet-4", catalog.defaultModel)
        assertEquals("openrouter", catalog.defaultProvider)
        assertEquals("/api/hermes/available-models?profile=manager", server.takeRequest().path)
    }

    @Test
    fun `the virtual moa group is not a provider`() {
        enqueue(catalogBody)
        val providers = api.modelCatalog("manager").providers

        assertEquals(listOf("openrouter", "custom:subrouter.ai"), providers.map { it.id })
    }

    @Test
    fun `a provider carries its identity, credential state and capabilities`() {
        enqueue(catalogBody)
        val providers = api.modelCatalog("manager").providers

        val builtin = providers.first { it.id == "openrouter" }
        assertEquals("OpenRouter", builtin.label)
        assertEquals("https://openrouter.ai/api/v1", builtin.baseUrl)
        assertEquals("chat_completions", builtin.apiMode)
        assertTrue("a saved api_key means the credential is configured", builtin.configured)
        assertTrue(builtin.builtin)
        assertTrue(builtin.refreshable)
        assertTrue(builtin.restoreAvailable)
        // The count the card shows is the visible one, not the full catalogue.
        assertEquals(2, builtin.modelCount)

        val custom = providers.first { it.id == "custom:subrouter.ai" }
        assertFalse(custom.builtin)
        assertFalse("an empty api_key is an unconfigured credential", custom.configured)
        assertTrue("a config-backed provider can be deleted outright", custom.deletable)
        assertEquals("custom_providers", custom.providerSource)
        assertEquals("subrouter", custom.providerKey)
        assertEquals("loading", custom.catalogStatus)
    }

    @Test
    fun `a provider lists its whole catalogue, custom models included`() {
        enqueue(catalogBody)
        val provider = api.modelCatalog("manager").providers.first { it.id == "openrouter" }

        assertEquals(
            listOf("anthropic/claude-sonnet-4", "openai/gpt-5", "meta/llama-4", "local/experiment"),
            provider.models.map { it.id },
        )
    }

    @Test
    fun `visibility, aliases, previews and custom origin land on the model`() {
        enqueue(catalogBody)
        val models = api.modelCatalog("manager").providers.first { it.id == "openrouter" }.models
            .associateBy { it.id }

        val sonnet = models.getValue("anthropic/claude-sonnet-4")
        assertEquals("Sonnet", sonnet.alias)
        assertTrue(sonnet.visible)
        assertFalse(sonnet.custom)

        // An include rule hides everything it does not name.
        assertFalse(models.getValue("meta/llama-4").visible)
        assertTrue("model_meta marks a model the catalogue lost", models.getValue("meta/llama-4").disabled)

        // model_meta supplies an alias when the Web UI has none of its own.
        assertEquals("GPT 5", models.getValue("openai/gpt-5").alias)
        assertTrue(models.getValue("openai/gpt-5").preview)

        assertTrue(models.getValue("local/experiment").custom)
    }

    @Test
    fun `a provider with no visibility rule shows every model`() {
        enqueue(catalogBody)
        val provider = api.modelCatalog("manager").providers.first { it.id == "custom:subrouter.ai" }

        assertEquals(listOf("sub/fast"), provider.models.map { it.id })
        assertTrue(provider.models.single().visible)
    }

    @Test
    fun `the fallback chain keeps its order and drops incomplete links`() {
        enqueue(
            """
            {"fallback_providers":[
              {"provider":"openrouter","model":"openai/gpt-5"},
              {"provider":"","model":"orphan"},
              {"provider":"custom:subrouter.ai","model":"sub/fast"}
            ]}
            """.trimIndent(),
        )

        val chain = api.fallbackProviders("manager")

        assertEquals(
            listOf(
                FallbackEntry("openrouter", "openai/gpt-5"),
                FallbackEntry("custom:subrouter.ai", "sub/fast"),
            ),
            chain,
        )
    }

    // ---- agents -----------------------------------------------------------

    @Test
    fun `coding agents are read from the tools array`() {
        enqueue(
            """
            {"tools":[
              {"id":"claude-code","name":"Claude Code","provider":"Anthropic","command":"claude",
               "packageName":"@anthropic-ai/claude-code","installed":true,"version":"1.2.3",
               "rawVersion":"1.2.3 (Claude Code)","source":"user-cli","path":"/usr/bin/claude"},
              {"id":"grok","name":"Grok","provider":"xAI","command":"grok","packageName":"@xai-official/grok",
               "installed":false,"version":"","rawVersion":"","source":"not-installed","path":"",
               "error":"Node/npm environment was not detected."}
            ]}
            """.trimIndent(),
        )

        val tools = api.codingAgents().associateBy { it.id }

        assertEquals(setOf("claude-code", "grok"), tools.keys)
        assertTrue(tools.getValue("claude-code").installed)
        assertEquals("1.2.3", tools.getValue("claude-code").version)
        assertEquals("user-cli", tools.getValue("claude-code").source)
        assertEquals("/usr/bin/claude", tools.getValue("claude-code").path)
        assertFalse(tools.getValue("grok").installed)
        assertEquals("Node/npm environment was not detected.", tools.getValue("grok").error)
    }

    /** `/api/agents/status` uses `claude` where the tools endpoint uses `claude-code`. */
    @Test
    fun `the status snapshot ids are folded onto the catalogue ids`() {
        enqueue(
            """
            {"revision":9,"updatedAt":"2026-09-20T00:00:00.000Z","agents":[
              {"id":"hermes","name":"Hermes","provider":"Nous Research","kind":"hermes","installed":true,
               "version":"0.21.3","source":"user-cli","path":"/usr/local/bin/hermes","error":""},
              {"id":"ekko-agent","name":"Ekko","provider":"Core Hub","kind":"built-in","installed":true,
               "version":"1.0.2","source":"built-in","path":"","error":""}
            ]}
            """.trimIndent(),
        )

        val snapshot = api.agentStatusSnapshot()

        assertEquals(listOf("hermes", "ekko-agent"), snapshot.map { it.id })
        assertEquals("0.21.3", snapshot.first().version)
        assertEquals("built-in", snapshot.last().source)
    }

    @Test
    fun `update policies are read per agent`() {
        enqueue(
            """
            {"agents":{
              "claude-code":{"autoUpdate":true,"autoUpdateSupported":true,"status":"available",
                             "currentVersion":"1.2.3","latestVersion":"1.3.0","checkedAt":"now"},
              "codex":{"autoUpdate":false,"autoUpdateSupported":false,"status":"current",
                       "currentVersion":"0.9.0","latestVersion":"0.9.0","checkedAt":"now"}
            }}
            """.trimIndent(),
        )

        val policies = api.agentUpdatePolicies()

        assertTrue(policies.getValue("claude-code").autoUpdate)
        assertEquals("1.3.0", policies.getValue("claude-code").latestVersion)
        assertFalse(policies.getValue("codex").autoUpdateSupported)
    }

    /** npm failures come back as HTTP 200 with `success: false`. */
    @Test
    fun `a failed install is read out of the body, not the status code`() {
        enqueue(
            """
            {"success":false,"code":"npm_missing","message":"Node/npm environment was not detected.",
             "tool":{"id":"pi","installed":false},"tools":[{"id":"pi","installed":false,"source":"not-installed"}]}
            """.trimIndent(),
        )

        val result = api.installCodingAgent("pi")

        assertFalse(result.success)
        assertEquals("npm_missing", result.code)
        assertEquals("Node/npm environment was not detected.", result.message)
        assertEquals(listOf("pi"), result.tools.map { it.id })
    }

    @Test
    fun `a config file keeps its path, language and content`() {
        enqueue(
            """
            {"key":"settings","path":"~/.claude/settings.json","absolutePath":"/home/agent/.claude/settings.json",
             "language":"json","content":"{\n  \"model\": \"sonnet\"\n}","exists":true,"size":28,
             "profile":"default","provider":"anthropic","rootDir":"/home/agent"}
            """.trimIndent(),
        )

        val file = api.codingAgentConfigFile("claude-code", "settings")

        assertEquals("~/.claude/settings.json", file.path)
        assertEquals("json", file.language)
        assertTrue(file.exists)
        assertTrue(file.content.contains("sonnet"))
    }

    // ---- the catalogue merge ----------------------------------------------

    private fun tool(id: String, installed: Boolean, version: String = "", source: String = "user-cli") =
        AgentToolStatus(id, id, "", installed, version, source, "", "")

    @Test
    fun `the catalogue is every agent the server source declares`() {
        assertEquals(
            listOf("hermes", "ekko-agent", "claude-code", "codex", "pi", "grok", "opencode", "dsh"),
            AgentCatalog.ALL.map { it.id },
        )
        assertEquals(6, AgentCatalog.ALL.count { it.kind == AgentKind.Coding })
    }

    /**
     * The whole point of the merge: a short answer from the server must not
     * shorten the list. It used to, which is why only some agents appeared.
     */
    @Test
    fun `an agent the server never mentioned is listed as unsupported`() {
        val cards = AgentCatalog.merge(
            tools = listOf(tool("claude-code", true, "1.2.3"), tool("codex", false)),
            snapshot = emptyList(),
            policies = emptyMap(),
            anyServerAnswer = true,
        ).associateBy { it.id }

        assertEquals(8, cards.size)
        assertEquals(AgentPresence.Installed, cards.getValue("claude-code").presence)
        assertEquals(AgentPresence.NotInstalled, cards.getValue("codex").presence)
        for (missing in listOf("grok", "opencode", "dsh", "hermes", "ekko-agent")) {
            assertEquals(missing, AgentPresence.Unsupported, cards.getValue(missing).presence)
        }
    }

    @Test
    fun `nothing from the server means unknown, not unsupported`() {
        val cards = AgentCatalog.merge(emptyList(), emptyList(), emptyMap(), anyServerAnswer = false)

        assertEquals(8, cards.size)
        assertTrue(cards.all { it.presence == AgentPresence.Unknown })
    }

    @Test
    fun `the coding-agents endpoint wins over the status snapshot`() {
        val cards = AgentCatalog.merge(
            tools = listOf(tool("claude-code", true, "2.0.0", source = "user-cli")),
            snapshot = listOf(
                tool("claude-code", true, "1.0.0", source = "managed-runtime"),
                tool("hermes", true, "0.21.3", source = "user-cli"),
            ),
            policies = emptyMap(),
            anyServerAnswer = true,
        ).associateBy { it.id }

        assertEquals("2.0.0", cards.getValue("claude-code").version)
        assertEquals("user-cli", cards.getValue("claude-code").source)
        // Hermes only ever comes from the snapshot, so it survives the merge.
        assertEquals(AgentPresence.Installed, cards.getValue("hermes").presence)
        assertEquals("0.21.3", cards.getValue("hermes").version)
    }

    @Test
    fun `an available update becomes the card's update version`() {
        val policies = mapOf(
            "claude-code" to AgentUpdatePolicy(true, true, "available", "1.2.3", "1.3.0", ""),
            "codex" to AgentUpdatePolicy(false, true, "current", "0.9.0", "0.9.0", ""),
        )
        val cards = AgentCatalog.merge(
            tools = listOf(tool("claude-code", true, "1.2.3"), tool("codex", true, "0.9.0")),
            snapshot = emptyList(),
            policies = policies,
            anyServerAnswer = true,
        ).associateBy { it.id }

        assertEquals("1.3.0", cards.getValue("claude-code").updateVersion)
        assertTrue(cards.getValue("claude-code").autoUpdate)
        // "current" is not an offer to update, so no version is advertised.
        assertEquals("", cards.getValue("codex").updateVersion)
        assertTrue(cards.getValue("codex").autoUpdateSupported)
    }

    @Test
    fun `only the coding agents are installable and only they have settings`() {
        val cards = AgentCatalog.merge(
            tools = AgentCatalog.ALL.filter { it.kind == AgentKind.Coding }.map { tool(it.id, false) },
            snapshot = listOf(tool("hermes", true), tool("ekko-agent", true, source = "built-in")),
            policies = emptyMap(),
            anyServerAnswer = true,
        ).associateBy { it.id }

        assertFalse(cards.getValue("hermes").installable)
        assertFalse(cards.getValue("hermes").hasSettings)
        assertFalse(cards.getValue("ekko-agent").installable)
        for (id in listOf("claude-code", "codex", "pi", "grok", "opencode", "dsh")) {
            assertTrue(id, cards.getValue(id).installable)
            assertTrue(id, cards.getValue(id).hasSettings)
            assertNotNull(id, AgentCatalog.definition(id)?.packageName?.takeIf { it.isNotBlank() })
        }
    }

    @Test
    fun `an unsupported agent offers nothing to press`() {
        val card = AgentCatalog.merge(emptyList(), emptyList(), emptyMap(), anyServerAnswer = true)
            .first { it.id == "dsh" }

        assertEquals(AgentPresence.Unsupported, card.presence)
        assertFalse(card.installable)
        assertFalse(card.hasSettings)
    }

    @Test
    fun `the web's agent id aliases resolve to catalogue ids`() {
        assertEquals("claude-code", AgentCatalog.canonicalId("claude"))
        assertEquals("claude-code", AgentCatalog.canonicalId("Claude-Code"))
        assertEquals("ekko-agent", AgentCatalog.canonicalId("ekko"))
        assertEquals("ekko-agent", AgentCatalog.canonicalId("ekko_agent"))
        assertEquals("dsh", AgentCatalog.canonicalId(" DSH "))
    }
}
