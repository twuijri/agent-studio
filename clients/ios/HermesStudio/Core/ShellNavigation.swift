import Foundation
import SwiftUI

/// The 4-segment conversation switch of the drawer (web `PageSidebarNav`).
/// A thin projection of the registry: each mode *is* a `NavDestination`.
enum ConversationMode: String, CaseIterable, Identifiable {
    case chat, group, workflow, history
    var id: String { rawValue }

    var destination: NavDestination {
        switch self {
        case .chat: return .chat
        case .group: return .groupChat
        case .workflow: return .workflow
        case .history: return .history
        }
    }

    var label: LocalizedStringKey { destination.label }
    var title: String { destination.title }
    var icon: CoreHubIcon { destination.icon ?? .chat }
}

/// Which agent a pushed "under the agent" destination (Skills, MCP, Memory,
/// Plugins, Settings) belongs to. Derived from the id of the agent card that
/// was opened last, because `.skills` is one registry case for every agent.
enum AgentFamily: Equatable {
    case hermes
    case ekko
    case coding(id: String)

    init(agentID: String) {
        switch AgentIdentity.canonicalID(agentID) {
        case "hermes": self = .hermes
        case "ekko-agent": self = .ekko
        default: self = .coding(id: AgentIdentity.canonicalID(agentID))
        }
    }

    var agentID: String {
        switch self {
        case .hermes: return "hermes"
        case .ekko: return "ekko-agent"
        case let .coding(id): return id
        }
    }

    /// The registry case that opens this agent's screen (`AgentScreenLoader`);
    /// the three cases carry no payload, so `store.focusedAgentID` is set
    /// alongside (`AppStore.openAgent`).
    var destination: NavDestination {
        switch self {
        case .hermes: return .agentHermes
        case .ekko: return .agentEkko
        case .coding: return .agentCoding
        }
    }

    /// The `target` query of `/api/hermes/skills` (`CodingAgentConfigView.vue`).
    var skillTarget: String {
        switch self {
        case .hermes, .ekko: return "hermes"
        case let .coding(id): return id == "claude-code" ? "claude" : id
        }
    }
}
