import SwiftUI

/// Hermes → Settings (`HermesSettingsView.vue:66-76`): tab `Agent` renders
/// `AgentSettings` plus `GatewayAutoStartSettings`, tab `Memory` the memory
/// settings, tab `Session` the session reset with approvals and skill
/// approvals (`SessionSettings.vue`). Agent runtime settings live here, not
/// under the app's Settings.
struct HermesSettingsView: View {
    @State private var tab = "agent"

    private static let tabs = [
        TabStripItem(id: "agent", title: String(localized: "Agent")),
        TabStripItem(id: "memory", title: String(localized: "Memory")),
        TabStripItem(id: "session", title: String(localized: "Session")),
    ]

    var body: some View {
        VStack(spacing: 0) {
            TabStrip(items: Self.tabs, selection: $tab)
            StudioSectionsForm(sections: HermesSettingsView.sections(for: tab)).id(tab)
        }
        .background(CoreHubTokens.Palette.bgPrimary)
        .navigationTitle(NavDestination.hermesSettings.title)
        .navigationBarTitleDisplayMode(.inline)
    }

    /// Which config sections each tab edits. Gateway auto-start sits with
    /// the agent tab, exactly as the desktop composes it.
    static func sections(for tab: String) -> [StudioSettingsSection] {
        switch tab {
        case "memory": return [.memory]
        case "session": return [.session, .approvals, .skills]
        default: return [.agent, .gateway]
        }
    }
}
