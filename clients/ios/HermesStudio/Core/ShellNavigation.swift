import Foundation

/// The 4-segment conversation switch of the drawer (web `PageSidebarNav`).
enum ConversationMode: String, CaseIterable, Identifiable {
    case chat, group, workflow, history
    var id: String { rawValue }

    var title: String {
        switch self {
        case .chat: return String(localized: "Chat")
        case .group: return String(localized: "Group Chat")
        case .workflow: return String(localized: "Workflow")
        case .history: return String(localized: "History")
        }
    }

    var icon: CoreHubIcon {
        switch self {
        case .chat: return .chat
        case .group: return .group
        case .workflow: return .workflow
        case .history: return .history
        }
    }
}

/// Screens reachable from the drawer rail and the settings drawer. They are
/// pushed onto the shell's navigation stack.
enum ShellDestination: String, Hashable, Identifiable {
    case connections, agentManager, models
    case logs, usage, performance, skillsUsage, theme, pets, profiles, settings
    var id: String { rawValue }
}
