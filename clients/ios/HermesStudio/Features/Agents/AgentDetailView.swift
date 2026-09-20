import SwiftUI

/// One agent's own screen — "under the agent" (`NAVIGATION.md` §4), entered
/// from an Agent Manager card only. Capabilities first, Settings last, and
/// back to Agent Manager:
///
/// - Hermes (`HermesConfigSidebar.vue:67-241`): Jobs · Kanban · Channels ·
///   Skills · Plugins · MCP · Memory · Journey · Settings, plus the card's
///   CLI details and "Manage runtime".
/// - Ekko (`EkkoConfigSidebar.vue:54-79`): Memory · Skills · MCP · Settings.
/// - Coding agent (`CodingAgentConfigSidebar.vue:19-24`): [Plugins · Presets
///   for dsh] · Skills · MCP · Settings, with install/update/remove on the card.
///
/// Everything here acts on the **Core Hub host**, never on the iPhone.
struct AgentDetailView: View {
    @EnvironmentObject private var store: AppStore

    let agent: AgentRuntimeStatus
    let tool: CodingAgentTool?
    let policy: AgentUpdatePolicy?
    let reload: () async -> Void

    @State private var working = ""
    @State private var actionNote = ""
    @State private var confirmingRemoval = false
    @State private var autoUpdate = false

    private var family: AgentFamily { AgentFamily(agentID: agent.id) }

    var body: some View {
        List {
            identitySection
            if agent.isCodingAgent {
                if policy != nil { autoUpdateSection }
                actionsSection
            }
            capabilitiesSection
            settingsSection
        }
        .listStyle(.insetGrouped)
        .navigationTitle(agent.name)
        .navigationBarTitleDisplayMode(.inline)
        // The pushed `.skills` / `.mcp` / `.memory` / `.plugins` / `*Settings`
        // screens resolve against the agent that was opened last.
        .onAppear { autoUpdate = policy?.autoUpdate ?? false; store.focusedAgentID = agent.id }
        .confirmationDialog("Remove \(agent.name)?", isPresented: $confirmingRemoval, titleVisibility: .visible) {
            Button("Remove", role: .destructive) { Task { await remove() } }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Core Hub uninstalls the package on the server and stops any session using it.")
        }
    }

    // MARK: Identity and state (the card)

    private var identitySection: some View {
        Section {
            AgentIdentityHeader(agent: agent)
            LabeledContent("State") {
                StatusPill(
                    text: agent.installed ? String(localized: "Installed") : String(localized: "Not installed"),
                    color: agent.installed ? CoreHubTokens.Palette.success : CoreHubTokens.Palette.warning
                )
            }
            LabeledContent("Source") { Text(AgentSourceLabel.text(agent.source)) }
            if agent.installed {
                LabeledContent("Version") { Text(AgentSourceLabel.version(agent.version.nilIfEmpty ?? tool?.version ?? "")) }
            }
            if let command = tool?.command.nilIfEmpty { LabeledContent("Command") { TechnicalText(text: command) } }
            if let package = tool?.packageName.nilIfEmpty { LabeledContent("Package") { TechnicalText(text: package) } }
            if !agent.path.isEmpty { LabeledContent("Path") { TechnicalText(text: agent.path) } }
            if !agent.error.isEmpty {
                Text(agent.error).font(CoreHubTokens.Typography.metaFont).foregroundStyle(CoreHubTokens.Palette.error)
            }
            ForEach(agent.installations, id: \.path) { installation in AgentInstallationRow(installation: installation) }
            if family == .hermes {
                // The Hermes card's "Manage runtime" (`AgentManagerView.vue:517-536`):
                // the only way to the runtime versions.
                NavigationLink { RuntimeVersionsView() } label: { Label("Manage runtime", systemImage: "shippingbox.and.arrow.backward.fill") }
            }
        } footer: {
            Text(footerNote)
        }
    }

    private var footerNote: LocalizedStringKey {
        switch family {
        case .hermes: return "Core Hub installs complete runtime packages; it never installs the Hermes CLI on its own."
        case .ekko: return "Ekko ships with Core Hub, so it is never installed or removed."
        case .coding: return "Everything on this screen runs on the Core Hub server, not on this iPhone."
        }
    }

    // MARK: Automatic updates

    private var autoUpdateSection: some View {
        Section {
            Toggle("Automatic updates", isOn: Binding(
                get: { autoUpdate },
                set: { value in autoUpdate = value; Task { await setAutoUpdate(value) } }
            ))
            .disabled(policy?.autoUpdateSupported == false || !working.isEmpty)
            if let policy {
                LabeledContent("Update status") { Text(AgentDetailView.policyLabel(policy.status)) }
                if let latest = policy.latestVersion.nilIfEmpty {
                    LabeledContent("Latest version") { Text(AgentSourceLabel.version(latest)) }
                }
                if !policy.error.isEmpty {
                    Text(policy.error).font(CoreHubTokens.Typography.metaFont).foregroundStyle(CoreHubTokens.Palette.error)
                }
            }
        } header: { Text("Updates") } footer: {
            if policy?.autoUpdateSupported == false {
                Text("This installation cannot update itself; use the manual update below.")
            }
        }
    }

    // MARK: Install / update / remove

    private var actionsSection: some View {
        Section {
            if agent.installed {
                if let offered = policy?.offeredVersion.nilIfEmpty {
                    actionButton("Update to \(AgentSourceLabel.version(offered))", "arrow.down.circle", key: "install") { await install() }
                } else {
                    actionButton("Check for updates", "magnifyingglass", key: "check") { await checkUpdate() }
                }
                Button(role: .destructive) { confirmingRemoval = true } label: {
                    Label("Remove from the server", systemImage: "trash")
                }
                .disabled(!working.isEmpty)
            } else {
                actionButton("Install on the server", "arrow.down.circle.fill", key: "install") { await install() }
            }
        } header: { Text("Actions") } footer: {
            if !actionNote.isEmpty { Text(actionNote) }
            else { Text("Installing and removing run npm on the Core Hub host and can take several minutes.") }
        }
    }

    private func actionButton(_ title: LocalizedStringKey, _ icon: String, key: String, run: @escaping () async -> Void) -> some View {
        Button { Task { await run() } } label: {
            HStack {
                Label(title, systemImage: icon)
                Spacer()
                if working == key { ProgressView().controlSize(.small) }
            }
        }
        .disabled(!working.isEmpty)
    }

    // MARK: Capabilities (desktop sidebar order) and Settings

    private var capabilitiesSection: some View {
        Section {
            ForEach(AgentDetailView.capabilities(for: family)) { destination in
                NavigationLink(value: destination) {
                    Label(destination.label, systemImage: AgentDetailView.symbol(for: destination))
                }
            }
            if case .coding(let id) = family, id == "dsh" {
                NavigationLink { DshPresetsView() } label: { Label("Presets", systemImage: "square.on.square.dashed") }
            }
        } header: { Text(agent.name) }
    }

    private var settingsSection: some View {
        Section {
            NavigationLink(value: AgentDetailView.settingsDestination(for: family)) {
                Label(NavDestination.settings.label, systemImage: "gearshape")
            }
        } footer: {
            switch family {
            case .hermes: Text("Agent, memory and session settings of the profile \(store.selectedProfile).")
            case .ekko: Text("Runtime, model, compression, tools, modules and advanced settings.")
            case .coding: Text("The agent's own settings files on the server.")
            }
        }
    }

    /// The capability entries per family, in the desktop sidebar order.
    static func capabilities(for family: AgentFamily) -> [NavDestination] {
        switch family {
        case .hermes: return [.jobs, .kanban, .channels, .skills, .plugins, .mcp, .memory, .journey]
        case .ekko: return [.memory, .skills, .mcp]
        case let .coding(id): return (id == "dsh" ? [.plugins] : []) + [.skills, .mcp]
        }
    }

    static func settingsDestination(for family: AgentFamily) -> NavDestination {
        switch family {
        case .hermes: return .hermesSettings
        case .ekko: return .ekkoSettings
        case .coding: return .codingAgentSettings
        }
    }

    static func symbol(for destination: NavDestination) -> String {
        switch destination {
        case .jobs: return "calendar.badge.clock"
        case .kanban: return "rectangle.3.group"
        case .channels: return "antenna.radiowaves.left.and.right"
        case .skills: return "square.stack.3d.up.fill"
        case .plugins: return "puzzlepiece.extension.fill"
        case .mcp: return "server.rack"
        case .memory: return "brain"
        case .journey: return "point.3.filled.connected.trianglepath.dotted"
        default: return "circle"
        }
    }

    // MARK: Work

    static func policyLabel(_ status: String) -> String {
        switch status {
        case "checking": return String(localized: "Checking")
        case "current": return String(localized: "Up to date")
        case "available": return String(localized: "Update available")
        case "waiting": return String(localized: "Waiting to update")
        case "updating": return String(localized: "Updating")
        case "failed": return String(localized: "Last check failed")
        default: return String(localized: "Unknown")
        }
    }

    private func install() async {
        working = "install"
        actionNote = String(localized: "Running npm on the server. This can take several minutes.")
        defer { working = "" }
        do {
            let result = try await store.api.installCodingAgent(agent.id)
            // A failed npm run still answers 200, so the flag decides.
            actionNote = result.success
                ? String(localized: "Installed.")
                : (result.message.nilIfEmpty ?? String(localized: "The install did not finish."))
        } catch {
            actionNote = error.localizedDescription
        }
        await reload()
    }

    private func checkUpdate() async {
        working = "check"
        defer { working = "" }
        do {
            let result = try await store.api.checkCodingAgentUpdate(agent.id)
            actionNote = result.available
                ? String(localized: "Version \(AgentSourceLabel.version(result.latest)) is available.")
                : String(localized: "Already up to date.")
        } catch {
            actionNote = error.localizedDescription
        }
        await reload()
    }

    private func remove() async {
        working = "remove"
        defer { working = "" }
        do {
            try await store.api.deleteCodingAgent(agent.id)
            actionNote = String(localized: "Removed.")
        } catch {
            actionNote = error.localizedDescription
        }
        await reload()
    }

    private func setAutoUpdate(_ value: Bool) async {
        working = "policy"
        defer { working = "" }
        do {
            try await store.api.setAgentAutoUpdate(agent.id, enabled: value)
        } catch {
            autoUpdate = !value
            store.errorMessage = error.localizedDescription
        }
        await reload()
    }
}

/// The agent's logo, name, provider and the id the server uses for it.
struct AgentIdentityHeader: View {
    let agent: AgentRuntimeStatus

    var body: some View {
        HStack(spacing: 13) {
            AgentAvatarView(asset: AgentAvatarAsset.resolve(runtime: agent.id, source: agent.kind == "hermes" ? "cli" : "coding_agent"), size: 44)
            VStack(alignment: .leading, spacing: 3) {
                Text(agent.name).font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.title, weight: .semibold))
                if let provider = agent.provider.nilIfEmpty {
                    Text(provider).font(CoreHubTokens.Typography.metaFont).foregroundStyle(CoreHubTokens.Palette.textMuted)
                }
                TechnicalText(text: agent.id)
            }
        }
        .padding(.vertical, 3)
    }
}

/// One `installations[]` row — the same CLI found twice on the host.
struct AgentInstallationRow: View {
    let installation: AgentInstallation

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(spacing: 6) {
                Label(AgentSourceLabel.text(installation.source), systemImage: AgentSourceLabel.icon(installation.source))
                    .font(CoreHubTokens.Typography.metaFont)
                if installation.selected { StatusPill(text: String(localized: "In use"), color: CoreHubTokens.Palette.success) }
                Spacer(minLength: 0)
                Text(AgentSourceLabel.version(installation.version)).font(CoreHubTokens.Typography.metaFont)
            }
            TechnicalText(text: installation.path)
        }
    }
}
