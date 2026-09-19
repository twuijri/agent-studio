import XCTest
@testable import HermesStudio

/// Decoding for the two screens rebuilt in M5: the Models screen's provider
/// payload and the Agents screen's inventory. Both had bugs that only showed
/// up as "the wrong things are on screen", so they are pinned here.
final class AgentsAndModelsTests: XCTestCase {

    // MARK: - Providers and models

    /// `groups` is what the profile actually has credentials for;
    /// `allProviders` is the server's full preset catalogue and must not be
    /// merged into the visible list. Merging them was why the phone listed
    /// every provider Core Hub has ever heard of.
    func testCatalogSeparatesConfiguredProvidersFromThePresetCatalogue() {
        let catalog = ModelCatalog([
            "default": "gpt-5",
            "default_provider": "openai",
            "groups": [
                ["provider": "openai", "label": "OpenAI", "models": ["gpt-5", "gpt-4o"], "api_key": "sk-x", "builtin": true],
            ],
            "allProviders": [
                ["provider": "openai", "label": "OpenAI", "models": ["gpt-5", "gpt-4o", "o3"]],
                ["provider": "zai", "label": "zai", "models": ["glm-4"]],
                ["provider": "anthropic", "label": "Anthropic", "models": ["claude"]],
            ],
        ])
        XCTAssertEqual(catalog.groups.map(\.id), ["openai"], "only the configured provider is listed")
        XCTAssertEqual(catalog.presets.count, 3)
        XCTAssertEqual(catalog.defaultProvider, "openai")
    }

    /// `moa` is a virtual Hermes routing provider; the web store drops it and
    /// so must the phone, or it shows up as a credential-less provider.
    func testCatalogDropsTheVirtualMoaProvider() {
        let catalog = ModelCatalog([
            "groups": [
                ["provider": "moa", "label": "MoA", "models": ["mix"]],
                ["provider": "zai", "label": "zai", "models": ["glm-4"]],
            ],
        ])
        XCTAssertEqual(catalog.groups.map(\.id), ["zai"])
    }

    func testProviderGroupReadsTheWebCardFields() {
        let catalog = ModelCatalog([
            "groups": [[
                "provider": "custom:subrouter.ai",
                "label": "subrouter.ai",
                "base_url": "https://subrouter.ai/v1",
                "models": ["a", "*", "b"],
                "available_models": ["a", "b", "c"],
                "api_key": "sk-y",
                "api_mode": "chat_completions",
                "builtin": false,
                "provider_source": "custom_providers",
                "provider_key": "subrouter",
                "provider_editable": true,
                "model_refreshable": true,
                "model_restore_available": true,
                "model_refresh_reason": "",
                "catalog_status": "ready",
            ]],
        ])
        let group = catalog.groups.first
        XCTAssertEqual(group?.label, "subrouter.ai")
        XCTAssertEqual(group?.models, ["a", "b"], "the `*` wildcard is not a model")
        XCTAssertEqual(group?.availableModels, ["a", "b", "c"])
        XCTAssertEqual(group?.apiMode, "chat_completions")
        XCTAssertEqual(group?.providerSource, "custom_providers")
        XCTAssertTrue(group?.restoreAvailable == true)
        XCTAssertTrue(group?.isCustom == true)
        XCTAssertTrue(group?.isConfigBacked == true, "deleting removes the pool, not just the key")
    }

    /// A built-in provider without `provider_source` only has its credentials
    /// cleared, which is the web's `clearProviderCredentials` branch.
    func testBuiltInProviderIsNotConfigBacked() {
        let catalog = ModelCatalog(["groups": [["provider": "anthropic", "label": "Anthropic", "builtin": true, "models": ["claude"]]]])
        XCTAssertFalse(catalog.groups[0].isCustom)
        XCTAssertFalse(catalog.groups[0].isConfigBacked)
    }

    /// `credential_configured` is the editor's flag and `api_key` the list's;
    /// either one means a key is stored.
    func testApiKeyConfiguredAcceptsBothServerSpellings() {
        let viaKey = ModelCatalog(["groups": [["provider": "a", "api_key": "sk"]]]).groups[0]
        let viaFlag = ModelCatalog(["groups": [["provider": "b", "credential_configured": true]]]).groups[0]
        let neither = ModelCatalog(["groups": [["provider": "c"]]]).groups[0]
        XCTAssertTrue(viaKey.apiKeyConfigured)
        XCTAssertTrue(viaFlag.apiKeyConfigured)
        XCTAssertFalse(neither.apiKeyConfigured)
    }

    func testModelCountLabelShowsTheVisibleFractionOnlyWhenFiltered() {
        let filtered = ModelCatalog([
            "groups": [["provider": "openai", "models": ["a"], "available_models": ["a", "b", "c"]]],
            "model_visibility": ["openai": ["mode": "include", "models": ["a"]]],
        ])
        XCTAssertEqual(filtered.modelCountLabel(of: filtered.groups[0]), "1/3")

        let unfiltered = ModelCatalog(["groups": [["provider": "openai", "models": ["a", "b", "c"]]]])
        XCTAssertEqual(unfiltered.modelCountLabel(of: unfiltered.groups[0]), "3")
    }

    /// When a provider reports no catalogue of its own, the preset entry
    /// widens it — the web's `sourceProvider` fallback in `ProviderCard`.
    func testAllModelsFallsBackToThePresetCatalogueAndAddsCustomModels() {
        let catalog = ModelCatalog([
            "groups": [["provider": "zai", "models": []]],
            "allProviders": [["provider": "zai", "models": ["glm-4", "glm-4-air"]]],
            "custom_models": ["zai": ["glm-private"]],
        ])
        XCTAssertEqual(catalog.allModels(of: catalog.groups[0]), ["glm-4", "glm-4-air", "glm-private"])
    }

    func testIsDefaultNeedsBothTheModelAndItsProvider() {
        let catalog = ModelCatalog([
            "default": "gpt-5",
            "default_provider": "openai",
            "groups": [["provider": "openai", "models": ["gpt-5"]], ["provider": "azure", "models": ["gpt-5"]]],
        ])
        XCTAssertTrue(catalog.isDefault(provider: "openai", model: "gpt-5"))
        XCTAssertFalse(catalog.isDefault(provider: "azure", model: "gpt-5"),
                       "the same model id under another provider is not the default")
    }

    // MARK: - Agent status

    /// The registry always answers with all eight agents. Grok, OpenCode and
    /// DeepSeek Harness used to be normalised into `hermes` on the way in,
    /// which merged four rows into one and is why the phone showed a subset.
    func testAgentStatusKeepsEveryServerIdDistinct() {
        let ids = ["hermes", "ekko-agent", "claude-code", "codex", "pi", "grok", "opencode", "dsh"]
        let decoded = ids.map { AgentRuntimeStatus(["id": $0, "kind": $0 == "hermes" ? "hermes" : ($0 == "ekko-agent" ? "built-in" : "coding-agent")]) }
        XCTAssertEqual(decoded.map(\.id), ids)
        XCTAssertEqual(decoded.filter(\.isCodingAgent).map(\.id), ["claude-code", "codex", "pi", "grok", "opencode", "dsh"])
    }

    func testAgentStatusReadsTheRegistryRecord() {
        let agent = AgentRuntimeStatus([
            "id": "grok",
            "name": "Grok",
            "provider": "xAI",
            "kind": "coding-agent",
            "installed": true,
            "version": "0.4.1",
            "source": "user-cli",
            "path": "/usr/local/bin/grok",
            "error": "",
            "installations": [
                ["path": "/usr/local/bin/grok", "version": "0.4.1", "source": "user-cli", "selected": true],
                ["path": "/opt/hermes/bin/grok", "version": "0.4.0", "source": "managed-runtime", "selected": false, "managedRuntimeVersion": "1.2.0"],
            ],
        ])
        XCTAssertEqual(agent.name, "Grok")
        XCTAssertEqual(agent.provider, "xAI")
        XCTAssertTrue(agent.isCodingAgent)
        XCTAssertEqual(agent.installations.count, 2)
        XCTAssertTrue(agent.installations[0].selected)
        XCTAssertEqual(agent.installations[1].managedRuntimeVersion, "1.2.0")
    }

    /// An id this build has never seen must still render with a readable
    /// name instead of being folded into Hermes.
    func testUnknownAgentIdSurvivesWithAReadableName() {
        let agent = AgentRuntimeStatus(["id": "future-agent", "kind": "coding-agent"])
        XCTAssertEqual(agent.id, "future-agent")
        XCTAssertEqual(agent.name, "Future Agent")
        XCTAssertEqual(agent.kind, "coding-agent")
    }

    /// `kind` is absent from `/api/coding-agents`, so the fallback has to put
    /// each agent in the right section anyway.
    func testKindFallsBackFromTheIdWhenTheServerOmitsIt() {
        XCTAssertEqual(AgentRuntimeStatus(["id": "hermes"]).kind, "hermes")
        XCTAssertEqual(AgentRuntimeStatus(["id": "ekko-agent"]).kind, "built-in")
        XCTAssertEqual(AgentRuntimeStatus(["id": "dsh"]).kind, "coding-agent")
    }

    func testCodingAgentToolKeepsItsIdAndCarriesTheInstallCommand() {
        let tool = CodingAgentTool([
            "id": "opencode",
            "name": "OpenCode",
            "provider": "OpenCode",
            "command": "opencode",
            "packageName": "opencode-ai",
            "installed": false,
            "source": "not-installed",
            "version": "",
            "rawVersion": "",
        ])
        XCTAssertEqual(tool.id, "opencode")
        XCTAssertEqual(tool.packageName, "opencode-ai")
        XCTAssertFalse(tool.installed)
        XCTAssertEqual(AgentManagerView.status(from: tool).kind, "coding-agent")
    }

    func testUpdatePolicyDefaultsToSupportedAndOffersOnlyARealUpdate() {
        let unknown = AgentUpdatePolicy(id: "codex", ["status": "unknown", "autoUpdate": false])
        XCTAssertTrue(unknown.autoUpdateSupported, "an absent flag means supported")
        XCTAssertEqual(unknown.offeredVersion, "")

        let available = AgentUpdatePolicy(id: "codex", ["status": "available", "latestVersion": "1.4.0", "autoUpdate": true])
        XCTAssertEqual(available.offeredVersion, "1.4.0")

        let current = AgentUpdatePolicy(id: "codex", ["status": "current", "latestVersion": "1.4.0"])
        XCTAssertEqual(current.offeredVersion, "", "up to date offers nothing even though a latest version is known")

        let unsupported = AgentUpdatePolicy(id: "pi", ["autoUpdateSupported": false])
        XCTAssertFalse(unsupported.autoUpdateSupported)
    }

    /// `formatVersion` in the web view: a `v` is added unless one is there.
    func testVersionLabelMatchesTheWebFormatting() {
        XCTAssertEqual(AgentSourceLabel.version("1.2.3"), "v1.2.3")
        XCTAssertEqual(AgentSourceLabel.version("v1.2.3"), "v1.2.3")
        XCTAssertEqual(AgentSourceLabel.version("  1.2.3 "), "v1.2.3")
        XCTAssertEqual(AgentSourceLabel.version("vNext"), "vvNext", "only `v` + digit counts as already prefixed")
        XCTAssertEqual(AgentSourceLabel.version(""), String(localized: "Unknown version"))
    }

    /// The MCP list sends JSON `null` for "no error"; `JSONSerialization`
    /// hands that back as `NSNull`, not a missing key.
    func testMcpServerTreatsJsonNullErrorAsNoError() {
        let clean = CodingAgentMcpServer([
            "name": "hermes-studio-api",
            "transport": "stdio",
            "connected": true,
            "tools": 7,
            "tool_names": ["a", "b"],
            "managed": true,
            "error": NSNull(),
        ])
        XCTAssertEqual(clean.error, "")
        XCTAssertEqual(clean.id, "hermes-studio-api")
        XCTAssertEqual(clean.tools, 7)
        XCTAssertTrue(clean.managed)

        let broken = CodingAgentMcpServer(["name": "x", "error": "spawn failed"])
        XCTAssertEqual(broken.error, "spawn failed")
        XCTAssertEqual(broken.transport, "stdio", "the transport defaults rather than staying empty")
    }

    func testConfigFileEditorsMatchTheWebKeyMap() {
        XCTAssertEqual(AgentConfigFiles.editors(for: "claude-code").map(\.fileKey), ["memory", "settings"])
        XCTAssertEqual(AgentConfigFiles.editors(for: "codex").map(\.fileKey), ["agents", "config"])
        XCTAssertEqual(AgentConfigFiles.editors(for: "grok").map(\.fileKey), ["agents", "settings"])
        XCTAssertEqual(AgentConfigFiles.editors(for: "dsh").map(\.fileKey), ["memory", "settings"])
        XCTAssertTrue(AgentConfigFiles.editors(for: "hermes").isEmpty, "no guessed keys for an agent without an editor")
        XCTAssertTrue(AgentConfigFiles.editors(for: "future-agent").isEmpty)
    }

    func testAgentIdentityCoversEveryServerId() {
        XCTAssertEqual(AgentIdentity.canonicalID("grok"), "grok")
        XCTAssertEqual(AgentIdentity.canonicalID("opencode"), "opencode")
        XCTAssertEqual(AgentIdentity.canonicalID("dsh"), "dsh")
        XCTAssertEqual(AgentIdentity.canonicalID("deepseek"), "dsh")
        XCTAssertEqual(AgentIdentity.displayName(for: "dsh"), "DeepSeek Harness")
        XCTAssertEqual(AgentIdentity.displayName(for: "opencode"), "OpenCode")
        XCTAssertEqual(AgentIdentity.knownIDs.count, 8)
        // An empty or unrecognised runtime still means Hermes for a session,
        // which is what the chat payload builder relies on.
        XCTAssertEqual(AgentIdentity.canonicalID(""), "hermes")
    }
}
