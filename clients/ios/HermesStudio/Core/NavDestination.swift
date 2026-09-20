import Foundation
import SwiftUI

/// The one navigation registry of the phone apps (`docs/mobile/NAVIGATION.md`).
/// Android implements the identical shape in Kotlin, and the cross-platform
/// parity test reads both against the contract, so the case names, their
/// order and their `nav_*` keys are part of the contract, not of this file.
///
/// Rule: the text of an entry is the title of the screen it opens, read from
/// the **same** key in both locale files (`labelKey`). A destination that
/// carries a payload (a session, a room, an agent) is resolved from the store
/// when it is pushed, so `ShellDestinationView` stays exhaustive.
enum NavDestination: String, CaseIterable, Hashable, Identifiable {
    // Primary rail (`PageSidebarNav.vue:74-205`).
    case newChat, search, deviceConnections, agentManager, models
    // Segmented switch (`PageSidebarNav.vue:207-288`).
    case chat, groupChat, workflow, history
    // The gear and the Tools section of Settings (`AppSidebar.vue:113-317`).
    case settings, logs, usage, performance, skillsUsage, theme, pets, profiles
    // Root content with a payload.
    case conversation, room, workflowDetail, workflowRun
    // Under the agent (§4): entered from an Agent Manager card only.
    case agentHermes, agentEkko, agentCoding
    case jobs, kanban, channels, skills, plugins, mcp, memory, journey
    case hermesSettings, ekkoSettings, codingAgentSettings
    // No menu entry on the desktop either; reached from search results
    // (`global_agent`) and from a profile card's "Edit config".
    case globalAgent, files

    var id: String { rawValue }

    /// `nav_<snake_case>` — e.g. `.deviceConnections` → `nav_device_connections`.
    var labelKey: String { "nav_" + NavDestination.snakeCase(rawValue) }

    /// The entry text and the screen title, from the same key.
    var label: LocalizedStringKey { LocalizedStringKey(labelKey) }

    /// The same string for APIs that take `String` (`navigationTitle`,
    /// accessibility labels).
    var title: String { String(localized: String.LocalizationValue(labelKey)) }

    /// The drawer icon for the rail and the segmented switch.
    var icon: CoreHubIcon? {
        switch self {
        case .newChat: return .newChat
        case .search: return .search
        case .deviceConnections: return .deviceConnections
        case .agentManager: return .agentManager
        case .models: return .models
        case .chat: return .chat
        case .groupChat: return .group
        case .workflow: return .workflow
        case .history: return .history
        case .settings: return .settings
        default: return nil
        }
    }

    /// The five rail entries, in the desktop order.
    static let rail: [NavDestination] = [.newChat, .search, .deviceConnections, .agentManager, .models]

    /// The four segments, in the desktop order.
    static let segments: [NavDestination] = [.chat, .groupChat, .workflow, .history]

    /// Settings → Tools, in the desktop order (`AppSidebar.vue:113-317`).
    static let tools: [NavDestination] = [.logs, .usage, .performance, .skillsUsage, .theme, .pets, .profiles]

    /// Entries the desktop gates behind super-admin (`router/index.ts:196-201`,
    /// `AppSidebar.vue:56,194`).
    static let superAdminOnly: Set<NavDestination> = [.agentManager, .performance, .profiles]

    /// `camelCase` → `snake_case`, one underscore before every upper-case letter.
    static func snakeCase(_ value: String) -> String {
        var result = ""
        for character in value {
            if character.isUppercase {
                result.append("_")
                result.append(contentsOf: character.lowercased())
            } else {
                result.append(character)
            }
        }
        return result
    }
}

/// The naming table of the contract (English / Arabic), used literally. The
/// unit tests check every term appears in the locale files under its
/// `nav_*` key.
enum NavNamingTable {
    static let english: [NavDestination: String] = [
        .newChat: "New chat", .search: "Search", .deviceConnections: "Device connections",
        .agentManager: "Agent Manager", .models: "Models", .chat: "Chat", .groupChat: "Group Chat",
        .workflow: "Workflow", .history: "History", .settings: "Settings", .logs: "Logs",
        .usage: "Usage", .performance: "Performance", .skillsUsage: "Skills Usage", .theme: "Theme",
        .pets: "Pets", .profiles: "Profiles",
    ]

    static let arabic: [NavDestination: String] = [
        .newChat: "محادثة جديدة", .search: "بحث", .deviceConnections: "اتصالات الأجهزة",
        .agentManager: "مدير الوكلاء", .models: "النماذج", .chat: "محادثة", .groupChat: "الغرف",
        .workflow: "سير العمل", .history: "السجل", .settings: "الإعدادات", .logs: "السجلات",
        .usage: "الاستخدام", .performance: "الأداء", .skillsUsage: "استخدام المهارات", .theme: "السمة",
        .pets: "الحيوانات", .profiles: "الملفات الشخصية",
    ]
}
