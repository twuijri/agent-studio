import SwiftUI

/// The Agents screen, rebuilt around the web's `views/hermes/AgentManagerView.vue`
/// and drawn like the Android `AgentManagerScreen.kt`, the design the owner
/// chose after comparing the two phones: an intro line, the "runs on the
/// server" note, then `Hermes runtime` · `Built in` · `Coding agents`, each
/// agent a card with its avatar, name and vendor, state pill, meta line,
/// package and path, error, the `Update automatically` switch and the
/// actions row (`Agent settings`, `CLI details`, `Reinstall` / `Install`,
/// `Check for an update`, `Delete`).
///
/// The list is **the server's**, not the app's: `GET /api/agents/status`
/// returns all eight agents in its own order (`hermes`, `ekko-agent`,
/// `claude-code`, `codex`, `pi`, `grok`, `opencode`, `dsh`) and the phone
/// renders whatever it gets, including ids it has never seen.
///
/// Installing, updating, checking and removing run npm on the Core Hub
/// host, from the card — exactly as Android's card does — so the agent
/// screen is left with the CLI details, the capabilities and the settings.
struct AgentManagerView: View {
    @EnvironmentObject private var store: AppStore

    @State private var agents: [AgentRuntimeStatus] = []
    @State private var tools: [String: CodingAgentTool] = [:]
    @State private var policies: [String: AgentUpdatePolicy] = [:]
    @State private var loading = true
    /// Shown when the inventory had to be rebuilt from a narrower endpoint.
    @State private var inventoryNote = ""
    /// Agents whose install, update, check or removal runs on the server now.
    @State private var busy: Set<String> = []
    @State private var removing: AgentRuntimeStatus?
    @State private var cliDetails: AgentRuntimeStatus?

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: CoreHubTokens.Layout.agentListGap) {
                if loading { LoadingRowView() }
                Text("Every agent Core Hub knows, with what this server reports about it.")
                    .font(CoreHubTokens.Typography.authorFont)
                    .foregroundStyle(CoreHubTokens.Palette.textSecondary)
                AgentNoteBox(text: "Installing, updating and removing an agent runs on the Core Hub server, not on this phone.")
                if !inventoryNote.isEmpty {
                    Text(inventoryNote).font(CoreHubTokens.Typography.authorFont).foregroundStyle(CoreHubTokens.Palette.warning)
                }
                group("Hermes runtime", kind: "hermes")
                group("Built in", kind: "built-in")
                group("Coding agents", kind: "coding-agent")
                otherGroup
            }
            .padding(.horizontal, CoreHubTokens.Layout.screenPaddingH)
            .padding(.top, CoreHubTokens.Layout.agentListPaddingTop)
            .padding(.bottom, CoreHubTokens.Layout.agentListPaddingBottom)
        }
        .hermesBackground()
        .navigationTitle(NavDestination.agentManager.title)
        .navigationBarTitleDisplayMode(.inline)
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
        .confirmationDialog(Text("Remove \(removing?.name ?? "")?"), isPresented: removingShown, titleVisibility: .visible, presenting: removing) { agent in
            Button("Delete", role: .destructive) { Task { await remove(agent) } }
            Button("Cancel", role: .cancel) {}
        } message: { agent in
            Text("The server will uninstall \(tools[agent.id]?.packageName.nilIfEmpty ?? agent.name) globally. Its settings files stay where they are.")
        }
        .alert(Text("CLI details"), isPresented: cliDetailsShown, presenting: cliDetails) { _ in
            Button("Dismiss", role: .cancel) {}
        } message: { agent in
            Text(verbatim: AgentCardText.cliDetails(agent: agent, tool: tools[agent.id]))
        }
    }

    private var removingShown: Binding<Bool> {
        Binding(get: { removing != nil }, set: { if !$0 { removing = nil } })
    }

    private var cliDetailsShown: Binding<Bool> {
        Binding(get: { cliDetails != nil }, set: { if !$0 { cliDetails = nil } })
    }

    // MARK: Sections

    @ViewBuilder
    private func group(_ title: LocalizedStringKey, kind: String) -> some View {
        let rows = agents.filter { $0.kind == kind }
        if !rows.isEmpty {
            AgentSectionHeader(title: title)
            ForEach(rows) { card($0) }
        }
    }

    /// Anything the registry adds later with a kind this build does not know.
    @ViewBuilder
    private var otherGroup: some View {
        let known = ["built-in", "hermes", "coding-agent"]
        let rows = agents.filter { !known.contains($0.kind) }
        if !rows.isEmpty {
            AgentSectionHeader(title: "Other agents")
            Text("Core Hub reports these agents but this version of the app has no screen for them yet.")
                .font(CoreHubTokens.Typography.authorFont)
                .foregroundStyle(CoreHubTokens.Palette.textSecondary)
            ForEach(rows) { card($0) }
        }
    }

    /// The card pushes by value (`store.openAgent` → `.agentHermes` /
    /// `.agentEkko` / `.agentCoding`, loaded by `AgentScreenLoader`). A
    /// view-destination link here put the agent screen outside `store.path`,
    /// and its capability rows — value links — stopped working; see the rule
    /// in `RootShell.swift`.
    private func card(_ agent: AgentRuntimeStatus) -> some View {
        AgentCardView(
            agent: agent,
            tool: tools[agent.id],
            policy: policies[agent.id],
            busy: busy.contains(agent.id),
            actions: AgentCardActions(
                install: { await install(agent) },
                checkUpdate: { await checkUpdate(agent) },
                remove: { removing = agent },
                cliDetails: { cliDetails = agent },
                setAutoUpdate: { value in Task { await setAutoUpdate(agent, value) } }
            )
        )
    }

    // MARK: Server-side work (the card's actions)

    /// Marks the agent busy, runs the call, shows its note or its error,
    /// then re-reads the inventory so the card reflects the new state.
    private func run(_ agent: AgentRuntimeStatus, _ work: () async throws -> String?) async {
        busy.insert(agent.id)
        do {
            if let note = try await work() { store.notify(note) }
        } catch {
            store.errorMessage = error.localizedDescription
        }
        await load()
        busy.remove(agent.id)
    }

    /// Install, reinstall and the card's `Update`: the same `npm install -g`
    /// on the Core Hub host. A failed run still answers HTTP 200, so the
    /// flag decides (`AgentInstallOutcome`).
    private func install(_ agent: AgentRuntimeStatus) async {
        await run(agent) {
            let result = try await store.api.installCodingAgent(agent.id)
            let note = AgentInstallOutcome.note(success: result.success, message: result.message)
            if result.success { return note }
            store.errorMessage = note
            return nil
        }
    }

    private func checkUpdate(_ agent: AgentRuntimeStatus) async {
        await run(agent) {
            let result = try await store.api.checkCodingAgentUpdate(agent.id)
            return result.available
                ? String(localized: "Version \(AgentSourceLabel.version(result.latest)) is available.")
                : String(localized: "Already up to date.")
        }
    }

    private func remove(_ agent: AgentRuntimeStatus) async {
        await run(agent) {
            try await store.api.deleteCodingAgent(agent.id)
            return String(localized: "Removed.")
        }
    }

    private func setAutoUpdate(_ agent: AgentRuntimeStatus, _ value: Bool) async {
        await run(agent) {
            try await store.api.setAgentAutoUpdate(agent.id, enabled: value)
            return nil
        }
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

/// What a card can ask the Agent Manager to do on the server.
struct AgentCardActions {
    let install: () async -> Void
    let checkUpdate: () async -> Void
    let remove: () -> Void
    let cliDetails: () -> Void
    let setAutoUpdate: (Bool) -> Void
}

/// One agent's card (`AgentRow` in `AgentManagerScreen.kt`). Tapping the
/// card opens the agent through `store.openAgent` (path-tracked); the
/// buttons inside it are targets of their own, so a tap on `Update` stays
/// on `Update`. Everything mirrors in Arabic: the avatar and the names at
/// the start edge, the pill column at the end, the actions filling from
/// the start.
struct AgentCardView: View {
    @EnvironmentObject private var store: AppStore

    let agent: AgentRuntimeStatus
    let tool: CodingAgentTool?
    let policy: AgentUpdatePolicy?
    let busy: Bool
    let actions: AgentCardActions

    private var offered: String? { agent.installed ? policy?.offeredVersion.nilIfEmpty : nil }
    private var showsCliDetails: Bool { agent.kind == "hermes" && agent.installed && agent.source == "user-cli" }

    var body: some View {
        AgentCardSurface {
            header
            metaLines
            if agent.isCodingAgent, policy?.autoUpdateSupported == true { autoUpdateRow }
            if busy { LoadingRowView() }
            actionsRow
            if agent.isCodingAgent && agent.installed {
                // The one action the phone genuinely cannot offer: the web
                // asks the server to open a native terminal.
                Text("Opening a native terminal is a desktop action; it is not available from the phone.")
                    .font(CoreHubTokens.Typography.metaFont)
                    .foregroundStyle(CoreHubTokens.Palette.textMuted)
            }
        }
        .contentShape(RoundedRectangle(cornerRadius: CoreHubTokens.Radius.agentCard, style: .continuous))
        .onTapGesture { store.openAgent(agent.id) }
    }

    /// Avatar · name over vendor · the state pill with `Update` under it.
    private var header: some View {
        HStack(spacing: CoreHubTokens.Layout.agentCardAvatarGap) {
            AgentAvatarView(asset: AgentAvatarAsset.resolve(runtime: agent.id, source: agent.kind == "hermes" ? "cli" : "coding_agent"), size: CoreHubTokens.Layout.agentCardAvatar)
            VStack(alignment: .leading, spacing: 0) {
                Text(agent.name)
                    .font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.navItem, weight: .bold))
                    .foregroundStyle(CoreHubTokens.Palette.textPrimary)
                    .lineLimit(1)
                Text(agent.provider.nilIfEmpty ?? agent.id)
                    .font(CoreHubTokens.Typography.authorFont)
                    .foregroundStyle(CoreHubTokens.Palette.textSecondary)
            }
            Spacer(minLength: 0)
            // `.trailing` follows the layout direction: the column hugs the
            // left edge in Arabic, like Android's `Alignment.End`.
            VStack(alignment: .trailing, spacing: CoreHubTokens.Layout.agentCardTrailingGap) {
                AgentStatePill(text: AgentCardText.stateLabel(agent), color: AgentCardText.stateColor(agent))
                if let offered {
                    AgentUpdateButton(offeredVersion: offered, working: busy) { await actions.install() }
                }
            }
        }
    }

    /// `Local CLI · v0.154.0 · v0.155.1 available`, the package, the path,
    /// the error — each only when there is one.
    @ViewBuilder
    private var metaLines: some View {
        if let detail = AgentCardText.meta(agent: agent, tool: tool, offered: offered) {
            Text(detail).font(CoreHubTokens.Typography.authorFont).foregroundStyle(CoreHubTokens.Palette.textMuted)
        }
        if let package = tool?.packageName.nilIfEmpty { TechnicalText(text: package) }
        if !agent.path.isEmpty { TechnicalText(text: agent.path) }
        if !agent.error.isEmpty {
            Text(agent.error).font(CoreHubTokens.Typography.authorFont).foregroundStyle(CoreHubTokens.Palette.error)
        }
    }

    private var autoUpdateRow: some View {
        HStack(spacing: CoreHubTokens.Layout.agentCardGap) {
            Text("Update automatically")
                .font(CoreHubTokens.Typography.sidebarTabFont)
                .foregroundStyle(CoreHubTokens.Palette.textPrimary)
            Spacer(minLength: 0)
            Toggle("Update automatically", isOn: Binding(get: { policy?.autoUpdate ?? false }, set: { actions.setAutoUpdate($0) }))
                .labelsHidden()
                .disabled(busy)
        }
    }

    /// `Agent settings` (outlined, with the gear) first, then the text
    /// buttons that apply to this agent.
    private var actionsRow: some View {
        AgentActionsFlow {
            AgentOutlinedButton(title: "Agent settings", icon: .settings, enabled: !busy) { store.openAgent(agent.id) }
            if showsCliDetails {
                AgentTextButton(title: "CLI details", enabled: !busy, action: actions.cliDetails)
            }
            if agent.isCodingAgent {
                AgentTextButton(title: agent.installed ? LocalizedStringKey("Reinstall") : LocalizedStringKey("Install"), enabled: !busy) {
                    Task { await actions.install() }
                }
                AgentTextButton(title: "Check for an update", enabled: !busy) { Task { await actions.checkUpdate() } }
                if agent.installed {
                    AgentTextButton(title: "Delete", color: CoreHubTokens.Palette.error, enabled: !busy, action: actions.remove)
                }
            }
        }
    }
}

/// The card's `Update` (`UpdateButton`): a pill on info @ 16 % (8 % while
/// it runs), 12 × 5 of padding, 11 semibold, the verb alone — the offered
/// version is spoken by accessibility, not drawn, so it never wraps.
struct AgentUpdateButton: View {
    let offeredVersion: String
    let working: Bool
    let run: () async -> Void

    var body: some View {
        Button { Task { await run() } } label: {
            Group {
                if working {
                    ProgressView().controlSize(.mini)
                } else {
                    Text("Update").lineLimit(1).fixedSize()
                }
            }
            .font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.meta, weight: .semibold))
            .foregroundStyle(CoreHubTokens.Palette.info)
            .padding(.horizontal, CoreHubTokens.Layout.agentUpdatePaddingH)
            .padding(.vertical, CoreHubTokens.Layout.agentPillPaddingV)
            .background(CoreHubTokens.Palette.info.opacity(working ? CoreHubTokens.Alpha.agentPillDisabled : CoreHubTokens.Alpha.agentPill), in: Capsule())
        }
        .buttonStyle(.plain)
        .disabled(working)
        .accessibilityLabel(Text("Update to \(AgentSourceLabel.version(offeredVersion))"))
    }
}

/// The card's and the agent screen's text, pure so the tests can read it.
enum AgentCardText {
    static func stateLabel(_ agent: AgentRuntimeStatus) -> String {
        agent.installed ? String(localized: "Installed") : String(localized: "Not installed")
    }

    static func stateColor(_ agent: AgentRuntimeStatus) -> Color {
        agent.installed ? CoreHubTokens.Palette.success : CoreHubTokens.Palette.warning
    }

    /// The card's meta line (`AgentRow`'s `detail`): the source, the
    /// version and the offered version, joined by ` · `; nil when the card
    /// has none of them (a coding agent that is not installed).
    static func meta(agent: AgentRuntimeStatus, tool: CodingAgentTool?, offered: String?) -> String? {
        var parts: [String] = []
        if agent.source != "not-installed", let source = agent.source.nilIfEmpty { parts.append(AgentSourceLabel.text(source)) }
        if let version = agent.version.nilIfEmpty ?? tool?.version.nilIfEmpty { parts.append(AgentSourceLabel.version(version)) }
        if let offered { parts.append(String(localized: "\(AgentSourceLabel.version(offered)) available")) }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    /// The agent screen's header line (`AgentScreen.kt`): version, then source.
    static func screenMeta(agent: AgentRuntimeStatus, tool: CodingAgentTool?) -> String? {
        var parts: [String] = []
        if let version = agent.version.nilIfEmpty ?? tool?.version.nilIfEmpty { parts.append(AgentSourceLabel.version(version)) }
        if agent.source != "not-installed", let source = agent.source.nilIfEmpty { parts.append(AgentSourceLabel.text(source)) }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    /// `HermesCliDetailsDialog`: what the server found on its own PATH —
    /// the version, the path and the error, one per line, `—` when empty.
    static func cliDetails(agent: AgentRuntimeStatus, tool: CodingAgentTool?) -> String {
        var lines = [
            (agent.version.nilIfEmpty ?? tool?.version.nilIfEmpty).map(AgentSourceLabel.version) ?? "—",
            agent.path.nilIfEmpty ?? "—",
        ]
        if !agent.error.isEmpty { lines.append(agent.error) }
        return lines.joined(separator: "\n")
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
