package us.i3u.hermesstudio

/**
 * Which agents the Agent Manager lists, and what shape a listed agent has.
 *
 * The phone used to list whatever `GET /api/agents/status` happened to return,
 * which is why agents went missing: an older server, or one that answers 403
 * because the snapshot route is super-admin only, simply produced a shorter
 * list with no explanation. The catalogue below is the fixed set the server
 * source declares, so the screen always shows the same rows and says, per row,
 * what the server reported — including "this server does not know this agent".
 *
 * Sources, all in `packages/server/src`:
 *  - `modules/studio/public/agent-status-registry.ts` — `AGENT_ORDER` and the
 *    per-agent defaults (name, provider, kind) for all eight ids.
 *  - `modules/coding-agents/services/index.ts` — `TOOL_DEFINITIONS`, which adds
 *    the command and the npm package for the six coding agents.
 *  - `modules/coding-agents/services/mcp-manager.ts` — `CODING_AGENT_IDS`.
 */
enum class AgentKind { Hermes, BuiltIn, Coding }

/**
 * The DeepSeek Harness: the one coding agent whose card also lists Plugins
 * and Presets (`CodingAgentConfigSidebar.vue:19-24`).
 */
const val DSH_AGENT_ID = "dsh"

/** A row of the catalogue before any server state is folded in. */
data class AgentDefinition(
    val id: String,
    val name: String,
    val provider: String,
    val kind: AgentKind,
    /** The CLI the server probes with `--version`; empty for non-coding agents. */
    val command: String = "",
    /** The npm package install and delete act on; empty for non-coding agents. */
    val packageName: String = "",
    /**
     * The two config files the web's per-agent settings page edits, as
     * `CodingAgentConfigView.vue` maps them: a preference file and a
     * configuration file.
     */
    val preferenceKey: String = "",
    val configurationKey: String = "",
)

/** How the phone should read one catalogue row right now. */
enum class AgentPresence {
    /** The server reported this agent and it is installed. */
    Installed,

    /** The server reported this agent and it is not installed. */
    NotInstalled,

    /**
     * The server answered, but never mentioned this agent — an older build, or
     * one that does not carry it. Shown rather than hidden.
     */
    Unsupported,

    /** Nothing has been asked yet, or the request failed. */
    Unknown,
}

/** A catalogue row with whatever the server said about it folded in. */
data class AgentCard(
    val definition: AgentDefinition,
    val presence: AgentPresence,
    val version: String = "",
    /** `managed-runtime`, `user-cli`, `built-in` or `not-installed`. */
    val source: String = "",
    val path: String = "",
    val error: String = "",
    val autoUpdate: Boolean = false,
    val autoUpdateSupported: Boolean = false,
    /** The newer version `check-update` found, when there is one. */
    val updateVersion: String = "",
    val updateStatus: String = "",
) {
    val id: String get() = definition.id
    val installed: Boolean get() = presence == AgentPresence.Installed

    /** Only the coding agents have an npm package the server can install. */
    val installable: Boolean get() = definition.kind == AgentKind.Coding &&
        presence != AgentPresence.Unsupported

    /** Only the coding agents have the two config files the web page edits. */
    val hasSettings: Boolean get() = definition.preferenceKey.isNotBlank() &&
        presence != AgentPresence.Unsupported
}

object AgentCatalog {

    /**
     * `AGENT_ORDER` from `agent-status-registry.ts`, with the commands and npm
     * packages from `TOOL_DEFINITIONS` and the settings-file keys from
     * `CodingAgentConfigView.vue`'s `settingsKeys` table.
     */
    val ALL: List<AgentDefinition> = listOf(
        AgentDefinition("hermes", "Hermes", "Nous Research", AgentKind.Hermes),
        AgentDefinition("ekko-agent", "Ekko", "Core Hub", AgentKind.BuiltIn),
        AgentDefinition(
            id = "claude-code",
            name = "Claude Code",
            provider = "Anthropic",
            kind = AgentKind.Coding,
            command = "claude",
            packageName = "@anthropic-ai/claude-code",
            preferenceKey = "memory",
            configurationKey = "settings",
        ),
        AgentDefinition(
            id = "codex",
            name = "Codex",
            provider = "OpenAI",
            kind = AgentKind.Coding,
            command = "codex",
            packageName = "@openai/codex",
            preferenceKey = "agents",
            configurationKey = "config",
        ),
        AgentDefinition(
            id = "pi",
            name = "Pi",
            provider = "Pi",
            kind = AgentKind.Coding,
            command = "pi",
            packageName = "@earendil-works/pi-coding-agent",
            preferenceKey = "agents",
            configurationKey = "settings",
        ),
        AgentDefinition(
            id = "grok",
            name = "Grok",
            provider = "xAI",
            kind = AgentKind.Coding,
            command = "grok",
            packageName = "@xai-official/grok",
            preferenceKey = "agents",
            configurationKey = "settings",
        ),
        AgentDefinition(
            id = "opencode",
            name = "OpenCode",
            provider = "OpenCode",
            kind = AgentKind.Coding,
            command = "opencode",
            packageName = "opencode-ai",
            preferenceKey = "memory",
            configurationKey = "settings",
        ),
        AgentDefinition(
            id = DSH_AGENT_ID,
            name = "DeepSeek Harness",
            provider = "DeepSeek",
            kind = AgentKind.Coding,
            command = "dsh",
            packageName = "@deepseek-ai/dsh",
            preferenceKey = "memory",
            configurationKey = "settings",
        ),
    )

    fun definition(id: String): AgentDefinition? = ALL.firstOrNull { it.id == canonicalId(id) }

    /** The alias map `resolveAgentStatusId` uses on the web. */
    fun canonicalId(raw: String): String = when (raw.trim().lowercase()) {
        "ekko", "ekko_agent", "ekko-agent" -> "ekko-agent"
        "claude", "claude-code", "claude_code" -> "claude-code"
        else -> raw.trim().lowercase()
    }

    /**
     * Folds the two server views into the fixed catalogue.
     *
     * `GET /api/coding-agents` is the better source for the six coding agents:
     * it is authenticated-user reachable and carries the real version string.
     * `GET /api/agents/status` covers Hermes and Ekko as well but is
     * super-admin only, so it may be absent entirely. An agent neither
     * reported is marked [AgentPresence.Unsupported] when at least one of the
     * two calls succeeded, and [AgentPresence.Unknown] when neither did.
     */
    fun merge(
        tools: List<AgentToolStatus>,
        snapshot: List<AgentToolStatus>,
        policies: Map<String, AgentUpdatePolicy>,
        anyServerAnswer: Boolean,
    ): List<AgentCard> {
        val byId = HashMap<String, AgentToolStatus>()
        snapshot.forEach { byId[canonicalId(it.id)] = it }
        // The coding-agents endpoint wins where both answered: same fields,
        // higher fidelity, and it is the one install and delete act on.
        tools.forEach { byId[canonicalId(it.id)] = it }

        return ALL.map { definition ->
            val reported = byId[definition.id]
            val policy = policies[definition.id]
            val presence = when {
                reported == null && !anyServerAnswer -> AgentPresence.Unknown
                reported == null -> AgentPresence.Unsupported
                reported.installed -> AgentPresence.Installed
                else -> AgentPresence.NotInstalled
            }
            AgentCard(
                definition = definition,
                presence = presence,
                version = reported?.version.orEmpty(),
                source = reported?.source.orEmpty(),
                path = reported?.path.orEmpty(),
                error = reported?.error.orEmpty(),
                autoUpdate = policy?.autoUpdate ?: false,
                autoUpdateSupported = policy?.autoUpdateSupported ?: false,
                updateVersion = policy?.latestVersion
                    ?.takeIf { it.isNotBlank() && policy.status == "available" }
                    .orEmpty(),
                updateStatus = policy?.status.orEmpty(),
            )
        }
    }
}
