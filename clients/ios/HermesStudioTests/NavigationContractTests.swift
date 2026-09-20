import SwiftUI
import XCTest
@testable import HermesStudio

/// The navigation registry against `docs/mobile/NAVIGATION.md`: the case
/// list shared with Android, one `nav_*` key per destination in **both**
/// locale files, the naming table's terms, and a screen for every case.
final class NavigationContractTests: XCTestCase {

    // MARK: - The registry shape (identical on Android)

    func testRegistryHasTheAgreedCasesInOrder() {
        XCTAssertEqual(NavDestination.allCases.map(\.rawValue), [
            "newChat", "search", "deviceConnections", "agentManager", "models",
            "chat", "groupChat", "workflow", "history",
            "settings", "logs", "usage", "performance", "skillsUsage", "theme", "pets", "profiles",
            "conversation", "room", "workflowDetail", "workflowRun",
            "agentHermes", "agentEkko", "agentCoding",
            "jobs", "kanban", "channels", "skills", "plugins", "mcp", "memory", "journey",
            "hermesSettings", "ekkoSettings", "codingAgentSettings",
            "globalAgent", "files",
        ])
    }

    func testLabelKeysAreSnakeCasedWithTheNavPrefix() {
        XCTAssertEqual(NavDestination.deviceConnections.labelKey, "nav_device_connections")
        XCTAssertEqual(NavDestination.newChat.labelKey, "nav_new_chat")
        XCTAssertEqual(NavDestination.codingAgentSettings.labelKey, "nav_coding_agent_settings")
        XCTAssertEqual(NavDestination.mcp.labelKey, "nav_mcp")
        XCTAssertEqual(NavDestination.snakeCase("skillsUsage"), "skills_usage")
        XCTAssertEqual(NavDestination.snakeCase("mcp"), "mcp")
    }

    func testRailSegmentsAndToolsFollowTheDesktopOrder() {
        XCTAssertEqual(NavDestination.rail, [.newChat, .search, .deviceConnections, .agentManager, .models])
        XCTAssertEqual(NavDestination.segments, [.chat, .groupChat, .workflow, .history])
        XCTAssertEqual(NavDestination.tools, [.logs, .usage, .performance, .skillsUsage, .theme, .pets, .profiles])
        XCTAssertEqual(NavDestination.superAdminOnly, [.agentManager, .performance, .profiles])
        XCTAssertEqual(ConversationMode.allCases.map(\.destination), NavDestination.segments)
    }

    // MARK: - Locale files

    func testEveryDestinationHasItsKeyInBothLocaleFiles() throws {
        let english = try LocalizableStrings.load(locale: "en")
        let arabic = try LocalizableStrings.load(locale: "ar")
        for destination in NavDestination.allCases {
            XCTAssertNotNil(english[destination.labelKey], "en.lproj lacks \(destination.labelKey)")
            XCTAssertNotNil(arabic[destination.labelKey], "ar.lproj lacks \(destination.labelKey)")
            XCTAssertFalse((english[destination.labelKey] ?? "").isEmpty, destination.labelKey)
            XCTAssertFalse((arabic[destination.labelKey] ?? "").isEmpty, destination.labelKey)
        }
    }

    func testNamingTableTermsAreUsedLiterally() throws {
        let english = try LocalizableStrings.load(locale: "en")
        let arabic = try LocalizableStrings.load(locale: "ar")
        for (destination, term) in NavNamingTable.english {
            XCTAssertEqual(english[destination.labelKey], term, destination.labelKey)
        }
        for (destination, term) in NavNamingTable.arabic {
            XCTAssertEqual(arabic[destination.labelKey], term, destination.labelKey)
        }
        // The two tables cover the same destinations.
        XCTAssertEqual(Set(NavNamingTable.english.keys), Set(NavNamingTable.arabic.keys))
    }

    func testLocaleKeySetsMatchExceptTheBundleDisplayName() throws {
        let english = try LocalizableStrings.load(locale: "en")
        let arabic = try LocalizableStrings.load(locale: "ar")
        let onlyEnglish = Set(english.keys).subtracting(arabic.keys)
        let onlyArabic = Set(arabic.keys).subtracting(english.keys)
        XCTAssertEqual(onlyEnglish, ["CFBundleDisplayName"], "keys only in en.lproj: \(onlyEnglish.sorted())")
        XCTAssertEqual(onlyArabic, [], "keys only in ar.lproj: \(onlyArabic.sorted())")
    }

    func testLocaleFilesHaveNoDuplicateKeys() throws {
        for locale in ["en", "ar"] {
            let duplicates = try LocalizableStrings.duplicateKeys(locale: locale)
            XCTAssertEqual(duplicates, [], "\(locale).lproj repeats \(duplicates)")
        }
    }

    // MARK: - A screen for every case

    /// `ShellDestinationView.body` is an exhaustive `switch`, so a new case
    /// without a screen does not compile; this pins the list the switch covers.
    func testShellDestinationViewCoversEveryDestination() {
        let covered = NavDestination.allCases.map { ShellDestinationView(destination: $0).destination }
        XCTAssertEqual(covered, NavDestination.allCases)
        XCTAssertEqual(NavDestination.allCases.count, 36)
    }

    func testAgentFamilyResolvesTheAgentThatWasOpenedLast() {
        XCTAssertEqual(AgentFamily(agentID: "hermes"), .hermes)
        XCTAssertEqual(AgentFamily(agentID: "ekko"), .ekko)
        XCTAssertEqual(AgentFamily(agentID: "claude-code"), .coding(id: "claude-code"))
        XCTAssertEqual(AgentFamily(agentID: "claude-code").skillTarget, "claude")
        XCTAssertEqual(AgentFamily(agentID: "dsh").skillTarget, "dsh")
        XCTAssertEqual(AgentFamily(agentID: "hermes").skillTarget, "hermes")
    }

    func testAgentScreensListTheDesktopSidebarOrder() {
        XCTAssertEqual(AgentDetailView.capabilities(for: .hermes), [.jobs, .kanban, .channels, .skills, .plugins, .mcp, .memory, .journey])
        XCTAssertEqual(AgentDetailView.capabilities(for: .ekko), [.memory, .skills, .mcp])
        XCTAssertEqual(AgentDetailView.capabilities(for: .coding(id: "codex")), [.skills, .mcp])
        XCTAssertEqual(AgentDetailView.capabilities(for: .coding(id: "dsh")), [.plugins, .skills, .mcp])
        XCTAssertEqual(AgentDetailView.settingsDestination(for: .hermes), .hermesSettings)
        XCTAssertEqual(AgentDetailView.settingsDestination(for: .ekko), .ekkoSettings)
        XCTAssertEqual(AgentDetailView.settingsDestination(for: .coding(id: "pi")), .codingAgentSettings)
    }

    /// `HermesSettingsView.vue:66-76`: gateway auto-start sits with the
    /// Agent tab; approvals and skill approvals with Session.
    func testHermesSettingsTabsComposeTheDesktopSections() {
        XCTAssertEqual(HermesSettingsView.sections(for: "agent"), [.agent, .gateway])
        XCTAssertEqual(HermesSettingsView.sections(for: "memory"), [.memory])
        XCTAssertEqual(HermesSettingsView.sections(for: "session"), [.session, .approvals, .skills])
    }
}

/// Reads `Resources/<locale>.lproj/Localizable.strings` from the source
/// tree (the test file's own path), so the check does not depend on which
/// resources the simulator bundle happened to compile.
enum LocalizableStrings {
    struct Error: Swift.Error, CustomStringConvertible { let description: String }

    static func url(locale: String) -> URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()           // HermesStudioTests
            .deletingLastPathComponent()           // clients/ios
            .appendingPathComponent("HermesStudio/Resources/\(locale).lproj/Localizable.strings")
    }

    static func entries(locale: String) throws -> [(key: String, value: String)] {
        let text: String
        do {
            text = try String(contentsOf: url(locale: locale), encoding: .utf8)
        } catch {
            throw Error(description: "cannot read \(url(locale: locale).path): \(error)")
        }
        // One `"key" = "value";` per line; comments and blank lines are skipped.
        let pattern = #"^\s*"((?:[^"\\]|\\.)*)"\s*=\s*"((?:[^"\\]|\\.)*)"\s*;\s*$"#
        let regex = try NSRegularExpression(pattern: pattern)
        var result: [(String, String)] = []
        for line in text.split(separator: "\n", omittingEmptySubsequences: false) {
            let string = String(line)
            let range = NSRange(string.startIndex..., in: string)
            guard let match = regex.firstMatch(in: string, range: range),
                  let keyRange = Range(match.range(at: 1), in: string),
                  let valueRange = Range(match.range(at: 2), in: string) else { continue }
            result.append((unescape(String(string[keyRange])), unescape(String(string[valueRange]))))
        }
        return result
    }

    static func load(locale: String) throws -> [String: String] {
        var table: [String: String] = [:]
        for entry in try entries(locale: locale) where table[entry.key] == nil { table[entry.key] = entry.value }
        return table
    }

    static func duplicateKeys(locale: String) throws -> [String] {
        var seen: Set<String> = []
        var duplicates: [String] = []
        for entry in try entries(locale: locale) {
            if !seen.insert(entry.key).inserted { duplicates.append(entry.key) }
        }
        return duplicates.sorted()
    }

    private static func unescape(_ value: String) -> String {
        value.replacingOccurrences(of: "\\\"", with: "\"").replacingOccurrences(of: "\\\\", with: "\\")
    }
}
