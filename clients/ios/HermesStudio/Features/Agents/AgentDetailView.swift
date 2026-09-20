import SwiftUI

/// One agent's own screen — "under the agent" (`NAVIGATION.md` §4), entered
/// from an Agent Manager card only, drawn like the Android `AgentScreen.kt`:
/// the header (a 40 pt avatar, the name, `vX · source`), the note, the CLI
/// details block, then one grouped card with the capabilities first and
/// Settings last. Back returns to the Agent Manager:
///
/// - Hermes (`HermesConfigSidebar.vue:67-241`): Jobs · Kanban · Channels ·
///   Skills · Plugins · MCP · Memory · Journey · Settings. The web's "Manage
///   runtime" (the runtime installer) is not on the phone: the runtime ships
///   in the server image (Docker) and the installer is the desktop app's.
/// - Ekko (`EkkoConfigSidebar.vue:54-79`): Memory · Skills · MCP · Settings.
/// - Coding agent (`CodingAgentConfigSidebar.vue:19-24`): [Plugins · Presets
///   for dsh] · Skills · MCP · Settings. Install, update, check and remove
///   live on the Agent Manager card, as on Android.
///
/// This screen is reached by value only (`AgentScreenLoader` for the
/// `.agent*` cases; the Agent Manager card calls `store.openAgent`). Its
/// rows are `NavigationLink(value:)`, and those resolve only from a screen
/// that is in `store.path` — see the rule in `RootShell.swift`.
struct AgentDetailView: View {
    @EnvironmentObject private var store: AppStore

    let agent: AgentRuntimeStatus
    let tool: CodingAgentTool?
    let policy: AgentUpdatePolicy?
    let reload: () async -> Void

    private var family: AgentFamily { AgentFamily(agentID: agent.id) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: CoreHubTokens.Layout.agentScreenGap) {
                AgentIdentityHeader(agent: agent, tool: tool)
                Text("Capabilities first, settings last. Back returns to the Agent Manager.")
                    .font(CoreHubTokens.Typography.authorFont)
                    .foregroundStyle(CoreHubTokens.Palette.textSecondary)
                cliDetails
                sectionsCard
            }
            .padding(.horizontal, CoreHubTokens.Layout.screenPaddingH)
            .padding(.top, CoreHubTokens.Layout.agentListPaddingTop)
            .padding(.bottom, CoreHubTokens.Layout.agentListPaddingBottom)
        }
        .hermesBackground()
        .navigationTitle(agent.name)
        .navigationBarTitleDisplayMode(.inline)
        // The pushed `.skills` / `.mcp` / `.memory` / `.plugins` / `*Settings`
        // screens resolve against the agent that was opened last.
        .onAppear { store.focusedAgentID = agent.id }
    }

    // MARK: CLI details (the card's dialog on Android, a block here)

    private var cliDetails: some View {
        AgentGroupedCard {
            AgentDetailRow(title: "State") {
                AgentStatePill(text: AgentCardText.stateLabel(agent), color: AgentCardText.stateColor(agent))
            }
            detailDivider
            AgentDetailRow(title: "Source") { detailValue(AgentSourceLabel.text(agent.source)) }
            if agent.installed {
                detailDivider
                AgentDetailRow(title: "Version") { detailValue(AgentSourceLabel.version(agent.version.nilIfEmpty ?? tool?.version ?? "")) }
            }
            technicalRows
            if !agent.error.isEmpty {
                detailDivider
                Text(agent.error)
                    .font(CoreHubTokens.Typography.authorFont)
                    .foregroundStyle(CoreHubTokens.Palette.error)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, CoreHubTokens.Layout.agentRowPaddingH)
                    .padding(.vertical, CoreHubTokens.Layout.agentRowPaddingV)
            }
            ForEach(agent.installations, id: \.path) { installation in
                detailDivider
                AgentInstallationRow(installation: installation)
                    .padding(.horizontal, CoreHubTokens.Layout.agentRowPaddingH)
                    .padding(.vertical, CoreHubTokens.Layout.agentRowPaddingV)
            }
        }
    }

    /// Path, package and command: technical text, always left-to-right.
    @ViewBuilder
    private var technicalRows: some View {
        if !agent.path.isEmpty {
            detailDivider
            AgentDetailRow(title: "Path") { TechnicalText(text: agent.path) }
        }
        if let package = tool?.packageName.nilIfEmpty {
            detailDivider
            AgentDetailRow(title: "Package") { TechnicalText(text: package) }
        }
        if let command = tool?.command.nilIfEmpty {
            detailDivider
            AgentDetailRow(title: "Command") { TechnicalText(text: command) }
        }
    }

    private var detailDivider: some View { AgentCardDivider(inset: CoreHubTokens.Layout.agentRowPaddingH) }

    private func detailValue(_ text: String) -> some View {
        Text(text)
            .font(CoreHubTokens.Typography.authorFont)
            .foregroundStyle(CoreHubTokens.Palette.textMuted)
            .lineLimit(1)
    }

    // MARK: Capabilities (desktop sidebar order) and Settings, last

    private var sectionsCard: some View {
        let rows = AgentDetailView.capabilities(for: family) + [AgentDetailView.settingsDestination(for: family)]
        return AgentGroupedCard {
            ForEach(Array(rows.enumerated()), id: \.element) { index, destination in
                NavigationLink(value: destination) {
                    AgentCapabilityRow(title: Text(destination.label), symbol: AgentDetailView.symbol(for: destination))
                }
                .buttonStyle(.plain)
                if index != rows.count - 1 { AgentCardDivider() }
            }
        }
    }

    /// The capability entries per family, in the desktop sidebar order.
    static func capabilities(for family: AgentFamily) -> [NavDestination] {
        switch family {
        case .hermes: return [.jobs, .kanban, .channels, .skills, .plugins, .mcp, .memory, .journey]
        case .ekko: return [.memory, .skills, .mcp]
        case let .coding(id): return (id == "dsh" ? [.plugins, .presets] : []) + [.skills, .mcp]
        }
    }

    static func settingsDestination(for family: AgentFamily) -> NavDestination {
        switch family {
        case .hermes: return .hermesSettings
        case .ekko: return .ekkoSettings
        case .coding: return .codingAgentSettings
        }
    }

    /// The row glyphs, the SF Symbol nearest each Material icon Android's
    /// `sectionIcon` draws (Schedule, ViewKanban, Forum, School, Extension,
    /// Tune, Cable, Memory, AccountTree); `settings` is the Core Hub gear on
    /// both phones.
    static func symbol(for destination: NavDestination) -> String {
        switch destination {
        case .jobs: return "clock"
        case .kanban: return "rectangle.split.3x1"
        case .channels: return "bubble.left.and.bubble.right"
        case .skills: return "graduationcap"
        case .plugins: return "puzzlepiece.extension"
        case .presets: return "slider.horizontal.3"
        case .mcp: return "cable.connector"
        case .memory: return "memorychip"
        case .journey: return "point.3.connected.trianglepath.dotted"
        case .hermesSettings, .ekkoSettings, .codingAgentSettings: return "settings"
        default: return "slider.horizontal.3"
        }
    }
}

/// The note after `POST /api/coding-agents/{id}/install`. A failed npm run
/// still answers HTTP 200, so the flag decides, and the server's message
/// wins over the generic text. Used by the card's Install / Reinstall /
/// `Update` (`AgentManagerView`).
enum AgentInstallOutcome {
    static func note(success: Bool, message: String) -> String {
        if success { return String(localized: "Installed.") }
        return message.nilIfEmpty ?? String(localized: "The install did not finish.")
    }
}

/// The agent screen's header (`AgentScreen.kt`): a 40 pt avatar, 12 to the
/// name (14 bold) over `vX · source` (12 muted).
struct AgentIdentityHeader: View {
    let agent: AgentRuntimeStatus
    let tool: CodingAgentTool?

    var body: some View {
        HStack(spacing: CoreHubTokens.Layout.agentCardAvatarGap) {
            AgentAvatarView(asset: AgentAvatarAsset.resolve(runtime: agent.id, source: agent.kind == "hermes" ? "cli" : "coding_agent"), size: CoreHubTokens.Layout.agentScreenAvatar)
            VStack(alignment: .leading, spacing: 0) {
                Text(agent.name)
                    .font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.navItem, weight: .bold))
                    .foregroundStyle(CoreHubTokens.Palette.textPrimary)
                    .lineLimit(1)
                if let detail = AgentCardText.screenMeta(agent: agent, tool: tool) {
                    Text(detail).font(CoreHubTokens.Typography.authorFont).foregroundStyle(CoreHubTokens.Palette.textMuted)
                }
            }
            Spacer(minLength: 0)
        }
    }
}

/// One `installations[]` row — the same CLI found twice on the host.
struct AgentInstallationRow: View {
    let installation: AgentInstallation

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(spacing: CoreHubTokens.Layout.agentCardTrailingGap) {
                Label(AgentSourceLabel.text(installation.source), systemImage: AgentSourceLabel.icon(installation.source))
                    .font(CoreHubTokens.Typography.authorFont)
                if installation.selected { AgentStatePill(text: String(localized: "In use"), color: CoreHubTokens.Palette.success) }
                Spacer(minLength: 0)
                Text(AgentSourceLabel.version(installation.version)).font(CoreHubTokens.Typography.authorFont)
            }
            TechnicalText(text: installation.path)
        }
    }
}
