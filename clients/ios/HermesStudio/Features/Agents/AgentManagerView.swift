import SwiftUI

/// The Agents screen, rebuilt around the web's `views/hermes/AgentManagerView.vue`.
///
/// The list is **the server's**, not the app's: `GET /api/agents/status`
/// returns all eight agents in its own order (`hermes`, `ekko-agent`,
/// `claude-code`, `codex`, `pi`, `grok`, `opencode`, `dsh`) and the phone
/// renders whatever it gets, including ids it has never seen. The previous
/// screen hardcoded five of them, which is why Grok, OpenCode and DeepSeek
/// Harness never appeared.
struct AgentManagerView: View {
    @EnvironmentObject private var store: AppStore

    @State private var agents: [AgentRuntimeStatus] = []
    @State private var tools: [String: CodingAgentTool] = [:]
    @State private var policies: [String: AgentUpdatePolicy] = [:]
    @State private var loading = true
    /// Shown when the inventory had to be rebuilt from a narrower endpoint.
    @State private var inventoryNote = ""
    /// Agents whose card `Update` is running on the server right now.
    @State private var updating: Set<String> = []

    var body: some View {
        List {
            if !inventoryNote.isEmpty {
                Section { Text(inventoryNote).font(CoreHubTokens.Typography.metaFont).foregroundStyle(CoreHubTokens.Palette.warning) }
            }
            group("Built in", kind: "built-in")
            group("Hermes runtime", kind: "hermes")
            group("Coding agents", kind: "coding-agent")
            otherGroup
        }
        .listStyle(.insetGrouped)
        .navigationTitle(NavDestination.agentManager.title)
        .navigationBarTitleDisplayMode(.inline)
        .overlay { if loading && agents.isEmpty { ProgressView() } }
        .refreshable { await reprobe() }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { Task { await reprobe() } } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .disabled(loading)
                .accessibilityLabel("Refresh")
            }
        }
        // `onAppear`, not `task`: it fires again when an agent screen pops
        // back, so an install or update done there shows on the cards.
        .onAppear { Task { await load() } }
    }

    // MARK: Sections

    @ViewBuilder
    private func group(_ title: LocalizedStringKey, kind: String) -> some View {
        let rows = agents.filter { $0.kind == kind }
        if !rows.isEmpty {
            Section(title) { ForEach(rows) { row($0) } }
        }
    }

    /// Anything the registry adds later with a kind this build does not know.
    @ViewBuilder
    private var otherGroup: some View {
        let known = ["built-in", "hermes", "coding-agent"]
        let rows = agents.filter { !known.contains($0.kind) }
        if !rows.isEmpty {
            Section {
                ForEach(rows) { row($0) }
            } header: { Text("Other agents") } footer: {
                Text("Core Hub reports these agents but this version of the app has no screen for them yet.")
            }
        }
    }

    /// The card pushes by value (`store.openAgent` → `.agentHermes` /
    /// `.agentEkko` / `.agentCoding`, loaded by `AgentScreenLoader`). A
    /// view-destination link here put the agent screen outside `store.path`,
    /// and its capability rows — value links — stopped working; see the rule
    /// in `RootShell.swift`.
    private func row(_ agent: AgentRuntimeStatus) -> some View {
        Button { store.openAgent(agent.id) } label: {
            AgentSummaryRow(agent: agent, tool: tools[agent.id], policy: policies[agent.id], updating: updating.contains(agent.id)) {
                await update(agent)
            }
        }
        .tint(CoreHubTokens.Palette.textPrimary)
    }

    /// The card's `Update`: the same `npm install -g` on the Core Hub host
    /// that the detail screen's "Update to vX" runs.
    private func update(_ agent: AgentRuntimeStatus) async {
        updating.insert(agent.id)
        do {
            let result = try await store.api.installCodingAgent(agent.id)
            let note = AgentInstallOutcome.note(success: result.success, message: result.message)
            if result.success { store.notify(note) } else { store.errorMessage = note }
        } catch {
            store.errorMessage = error.localizedDescription
        }
        await load()
        updating.remove(agent.id)
    }

    // MARK: Loading

    /// The plain read. `/api/agents/status` needs super-admin; when it is
    /// refused the six coding agents still come from `/api/coding-agents`,
    /// and the screen says what is missing instead of showing a short list
    /// as if it were complete.
    private func load() async {
        loading = true
        defer { loading = false }

        async let statusRequest = store.api.agentStatuses()
        async let toolRequest = store.api.codingAgents()
        async let policyRequest = store.api.agentUpdatePolicies()
        // Each of the three is allowed to fail on its own: the inventory
        // needs super-admin, the policies need admin, the tool list needs
        // neither.
        let statuses = (try? await statusRequest) ?? []
        let toolList = (try? await toolRequest) ?? []
        policies = (try? await policyRequest) ?? [:]
        tools = Dictionary(uniqueKeysWithValues: toolList.map { ($0.id, $0) })

        if !statuses.isEmpty {
            agents = statuses
            inventoryNote = ""
        } else {
            agents = toolList.map(AgentManagerView.status(from:))
            inventoryNote = agents.isEmpty
                ? String(localized: "Core Hub did not return an agent inventory.")
                : String(localized: "Showing the coding agents only. The full inventory, including Hermes and Ekko, needs a super-admin sign-in.")
        }
    }

    /// The web's Refresh: make the server re-probe the CLIs and the runtime
    /// first — the status registry is only populated as a side effect of
    /// those two calls — and then read the inventory back.
    private func reprobe() async {
        loading = true
        _ = try? await store.api.codingAgents()
        try? await store.api.probeHermesRuntime()
        loading = false
        await load()
    }

    /// A `/api/coding-agents` row seen through the registry's shape.
    static func status(from tool: CodingAgentTool) -> AgentRuntimeStatus {
        AgentRuntimeStatus([
            "id": tool.id,
            "name": tool.name,
            "provider": tool.provider,
            "kind": "coding-agent",
            "installed": tool.installed,
            "source": tool.source,
            "path": tool.path,
            "version": tool.version,
            "error": tool.error,
        ])
    }
}

/// One agent in the list: logo, name, provider, the one line of detail that
/// matters (source and version, or the error), and at the trailing edge the
/// state pill with the compact `Update` button under it. The row is the
/// label of the card's button, so nothing here navigates on its own; the
/// `Update` button is `.bordered`, which is what makes a tap on it stay on
/// the button instead of opening the card.
struct AgentSummaryRow: View {
    let agent: AgentRuntimeStatus
    let tool: CodingAgentTool?
    let policy: AgentUpdatePolicy?
    let updating: Bool
    let update: () async -> Void

    var body: some View {
        HStack(spacing: 12) {
            AgentAvatarView(asset: AgentAvatarAsset.resolve(runtime: agent.id, source: agent.kind == "hermes" ? "cli" : "coding_agent"), size: 34)
            VStack(alignment: .leading, spacing: 3) {
                Text(agent.name).font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.base, weight: .semibold))
                Text(agent.provider.nilIfEmpty ?? agent.id)
                    .font(CoreHubTokens.Typography.metaFont)
                    .foregroundStyle(CoreHubTokens.Palette.textMuted)
                detail
            }
            Spacer(minLength: 0)
            trailing
            CoreHubIconView(icon: .chevronForward, size: 14).foregroundStyle(CoreHubTokens.Palette.textMuted)
        }
        .padding(.vertical, 3)
    }

    /// The version line, unchanged: `Local CLI · v0.154.0`, or the error.
    /// The offered version no longer sits here as a pill; that is the
    /// `Update` button's job, and the version stays on one line.
    @ViewBuilder
    private var detail: some View {
        if !agent.error.isEmpty {
            Text(agent.error).font(CoreHubTokens.Typography.metaFont).foregroundStyle(CoreHubTokens.Palette.error).lineLimit(2)
        } else if agent.installed {
            HStack(spacing: 5) {
                Text(AgentSourceLabel.text(agent.source))
                Text(verbatim: "·")
                Text(AgentSourceLabel.version(agent.version.nilIfEmpty ?? tool?.version ?? ""))
            }
            .font(CoreHubTokens.Typography.metaFont)
            .foregroundStyle(CoreHubTokens.Palette.textMuted)
        }
    }

    /// A trailing-aligned column: the state pill, and under it `Update` when
    /// the policy offers a newer version. `.trailing` follows the layout
    /// direction, so in Arabic the column hugs the left edge with the chevron
    /// beside it, mirroring the rest of the row.
    private var trailing: some View {
        VStack(alignment: .trailing, spacing: 6) {
            StatusPill(
                text: agent.installed ? String(localized: "Installed") : String(localized: "Not installed"),
                color: agent.installed ? CoreHubTokens.Palette.success : CoreHubTokens.Palette.warning
            )
            if agent.installed, let offered = policy?.offeredVersion.nilIfEmpty {
                AgentUpdateButton(offeredVersion: offered, working: updating, run: update)
            }
        }
    }
}

/// The compact update control of a card. Label only — the offered version
/// is spoken by accessibility, not drawn — so the control never wraps
/// (`lineLimit(1)` + `fixedSize`); build 41 drew "Update v0.155.1" as a pill
/// that broke over three lines inside a circle.
struct AgentUpdateButton: View {
    let offeredVersion: String
    let working: Bool
    let run: () async -> Void

    var body: some View {
        Button { Task { await run() } } label: {
            if working {
                ProgressView().controlSize(.mini)
            } else {
                Text("Update").lineLimit(1).fixedSize()
            }
        }
        .buttonStyle(.bordered)
        .controlSize(.small)
        .tint(CoreHubTokens.Palette.info)
        .font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.meta, weight: .semibold))
        .disabled(working)
        .accessibilityLabel(Text("Update to \(AgentSourceLabel.version(offeredVersion))"))
    }
}

/// Wording shared by the list and the detail screen.
enum AgentSourceLabel {
    static func text(_ source: String) -> String {
        switch source {
        case "managed-runtime": return String(localized: "Managed runtime")
        case "built-in": return String(localized: "Built in")
        case "user-cli": return String(localized: "Local CLI")
        case "not-installed": return String(localized: "Not installed")
        default: return source
        }
    }

    static func icon(_ source: String) -> String {
        switch source {
        case "managed-runtime": return "shippingbox.fill"
        case "built-in": return "checkmark.seal.fill"
        default: return "terminal.fill"
        }
    }

    /// `formatVersion` in the web view: prefix `v` unless there is one.
    static func version(_ raw: String) -> String {
        let value = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return String(localized: "Unknown version") }
        guard let first = value.first, first == "v" || first == "V",
              let second = value.dropFirst().first, second.isNumber else { return "v\(value)" }
        return value
    }
}
