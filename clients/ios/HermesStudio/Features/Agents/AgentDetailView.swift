import SwiftUI

/// One agent's own screen — what the web reaches with the card's **Settings**
/// button (`/studio/agents/{id}/settings`), plus the install state the card
/// itself shows. Tapping an agent used to do nothing on the phone.
///
/// Everything here acts on the **Core Hub host**, never on the iPhone: an
/// install is `npm install -g` on the server, a config file is a file in the
/// server's home directory.
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

    var body: some View {
        List {
            identitySection
            if agent.isCodingAgent {
                if policy != nil { autoUpdateSection }
                actionsSection
                configurationSection
                mcpSection
            }
            if agent.kind == "built-in" { ekkoSection }
            if agent.kind == "hermes" { hermesSection }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(agent.name)
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { autoUpdate = policy?.autoUpdate ?? false }
        .confirmationDialog("Remove \(agent.name)?", isPresented: $confirmingRemoval, titleVisibility: .visible) {
            Button("Remove", role: .destructive) { Task { await remove() } }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Core Hub uninstalls the package on the server and stops any session using it.")
        }
    }

    // MARK: Identity and state

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
            if let command = tool?.command.nilIfEmpty {
                LabeledContent("Command") { TechnicalText(text: command) }
            }
            if let package = tool?.packageName.nilIfEmpty {
                LabeledContent("Package") { TechnicalText(text: package) }
            }
            if !agent.path.isEmpty {
                LabeledContent("Path") { TechnicalText(text: agent.path) }
            }
            if !agent.error.isEmpty {
                Text(agent.error).font(CoreHubTokens.Typography.metaFont).foregroundStyle(CoreHubTokens.Palette.error)
            }
            ForEach(agent.installations, id: \.path) { installation in
                AgentInstallationRow(installation: installation)
            }
        } footer: {
            Text("Everything on this screen runs on the Core Hub server, not on this iPhone.")
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

    // MARK: The agent's own files

    private var configurationSection: some View {
        Section {
            ForEach(AgentConfigFiles.editors(for: agent.id), id: \.key) { editor in
                NavigationLink {
                    AgentConfigFileView(agentID: agent.id, agentName: agent.name, editor: editor)
                } label: {
                    Label(editor.title, systemImage: editor.key == "preference" ? "text.book.closed" : "gearshape")
                }
            }
        } header: { Text("Settings") } footer: {
            Text("The agent's own files on the server, the same two the web's agent settings page edits.")
        }
    }

    private var mcpSection: some View {
        Section {
            NavigationLink {
                AgentMcpServersView(agentID: agent.id, agentName: agent.name)
            } label: {
                Label("MCP servers", systemImage: "server.rack")
            }
        }
    }

    // MARK: Ekko and Hermes

    private var ekkoSection: some View {
        Section {
            NavigationLink { EkkoConfigurationView() } label: { Label("Configuration", systemImage: "slider.horizontal.3") }
            NavigationLink { EkkoMemoryView() } label: { Label("Ekko memory", systemImage: "brain") }
            NavigationLink { EkkoSkillsView() } label: { Label("Ekko skills", systemImage: "square.stack.3d.up") }
            NavigationLink { EkkoMCPView() } label: { Label("Ekko MCP", systemImage: "server.rack") }
        } header: { Text("Ekko") } footer: {
            Text("Ekko ships with Core Hub, so it is never installed or removed.")
        }
    }

    /// Complaint: "the Hermes side is unreachable". These screens already
    /// existed under Settings → Workspace tools; what was missing was a way
    /// in from the agent itself, which is where the web puts them.
    private var hermesSection: some View {
        Group {
            Section {
                NavigationLink { RuntimeVersionsView() } label: { Label("Runtime versions", systemImage: "shippingbox.and.arrow.backward.fill") }
                NavigationLink { StudioSectionSettings(section: .agent) } label: { Label("Agent settings", systemImage: "sparkles") }
                NavigationLink { HermesMemoryView() } label: { Label("Memory", systemImage: "brain") }
                NavigationLink { StudioSectionSettings(section: .memory) } label: { Label("Memory settings", systemImage: "lightbulb.max.fill") }
            } header: { Text("Hermes runtime") } footer: {
                Text("Core Hub installs complete runtime packages; it never installs the Hermes CLI on its own.")
            }
            Section {
                NavigationLink { SkillsView() } label: { Label("Skills", systemImage: "square.stack.3d.up.fill") }
                NavigationLink { SkillUsageView() } label: { Label("Skills usage", systemImage: "chart.bar") }
                NavigationLink { MCPView() } label: { Label("MCP", systemImage: "server.rack") }
                NavigationLink { PluginsView() } label: { Label("Plugins", systemImage: "puzzlepiece.extension.fill") }
            } header: { Text("Skills and tools") }
            Section {
                NavigationLink { KanbanView() } label: { Label("Kanban", systemImage: "rectangle.3.group") }
                NavigationLink { CronJobsView() } label: { Label("Scheduled Jobs", systemImage: "calendar.badge.clock") }
                NavigationLink { JourneyView() } label: { Label("Journey", systemImage: "point.3.filled.connected.trianglepath.dotted") }
                NavigationLink { ChannelsView() } label: { Label("Channels", systemImage: "antenna.radiowaves.left.and.right") }
                NavigationLink { GlobalAgentView() } label: { Label("Global Agent", systemImage: "globe.desk.fill") }
                NavigationLink { StudioFilesView() } label: { Label("Files", systemImage: "folder.fill") }
            } header: { Text("Hermes workspace") } footer: {
                Text("The same screens as Settings → Workspace tools, reachable from the agent they belong to.")
            }
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
