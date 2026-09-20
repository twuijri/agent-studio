import XCTest
@testable import HermesStudio

/// The rule behind the Hermes rows that did nothing on TestFlight build 41
/// (`RootShell.swift` header): a `NavigationStack(path:)` honours
/// `NavigationLink(value:)` only from a screen that is itself in the path,
/// so a screen that contains value links is never pushed by a
/// view-destination link. The pure parts are tested directly; the two
/// source-tree rules read the Swift files next to this test so that the
/// pattern cannot come back unnoticed.
final class NavigationRulesTests: XCTestCase {

    // MARK: - The card's push

    /// `store.openAgent` pushes the family's registry case; the loader then
    /// opens the same `AgentDetailView` for the id in `focusedAgentID`.
    func testEveryAgentFamilyOpensItsOwnRegistryCase() {
        XCTAssertEqual(AgentFamily(agentID: "hermes").destination, .agentHermes)
        XCTAssertEqual(AgentFamily(agentID: "ekko-agent").destination, .agentEkko)
        XCTAssertEqual(AgentFamily(agentID: "ekko").destination, .agentEkko)
        XCTAssertEqual(AgentFamily(agentID: "codex").destination, .agentCoding)
        XCTAssertEqual(AgentFamily(agentID: "dsh").destination, .agentCoding)
        XCTAssertEqual(AgentFamily(agentID: "never-seen").destination, .agentCoding)
        // Every family lands on a case the shell has a screen for.
        for destination in [NavDestination.agentHermes, .agentEkko, .agentCoding] {
            XCTAssertTrue(NavDestination.allCases.contains(destination))
        }
    }

    // MARK: - The card's Update

    /// A failed npm run answers HTTP 200 with `success: false`; the server's
    /// message wins, the generic text is the fallback.
    func testInstallNoteFollowsTheFlagNotTheStatusCode() {
        XCTAssertEqual(AgentInstallOutcome.note(success: false, message: "npm ERR! 403"), "npm ERR! 403")
        XCTAssertEqual(AgentInstallOutcome.note(success: false, message: ""), String(localized: "The install did not finish."))
        XCTAssertEqual(AgentInstallOutcome.note(success: true, message: "ignored"), String(localized: "Installed."))
    }

    func testUpdateWordingExistsInBothLocales() throws {
        let english = try LocalizableStrings.load(locale: "en")
        let arabic = try LocalizableStrings.load(locale: "ar")
        XCTAssertEqual(english["Update"], "Update")
        XCTAssertEqual(arabic["Update"], "تحديث")
        XCTAssertNotNil(english["Update to %@"])
        XCTAssertNotNil(arabic["Update to %@"])
        // The runtime installer left the phone with its strings.
        for key in ["Manage runtime", "Runtime Versions", "Update %@"] {
            XCTAssertNil(english[key], key)
            XCTAssertNil(arabic[key], key)
        }
    }

    // MARK: - Source-tree rules

    /// `AgentDetailView` is constructed by `AgentScreenLoader` alone, i.e.
    /// reached through a registry case in `store.path`; a card must not
    /// build it inside a view-destination link again.
    func testAgentDetailViewIsConstructedByTheLoaderAlone() throws {
        let builders = try SwiftSources.files(containing: "AgentDetailView(")
        XCTAssertEqual(builders, ["AgentScreens.swift"], "AgentDetailView( appears in \(builders)")
        XCTAssertFalse(try SwiftSources.files(containing: "NavigationLink {").contains("AgentManagerView.swift"))
    }

    /// Value links live only in screens that are always in the path:
    /// `AgentDetailView` (through the loader) and `SettingsView` (through
    /// `store.show(.settings)`). A new screen with value links must be
    /// added here together with the path-tracked route that reaches it.
    func testValueLinksLiveOnlyInPathTrackedScreens() throws {
        XCTAssertEqual(try SwiftSources.files(containing: "NavigationLink(value:"), ["AgentDetailView.swift", "SettingsView.swift"])
    }

    /// The runtime installer is the desktop's; nothing on the phone refers to it.
    func testTheRuntimeInstallerIsGoneFromThePhone() throws {
        XCTAssertEqual(try SwiftSources.files(containing: "RuntimeVersionsView"), [])
        XCTAssertEqual(try SwiftSources.files(containing: "runtime-versions/"), [])
    }
}

/// The app's Swift sources, read from the source tree (the test file's own
/// path), like `LocalizableStrings`.
enum SwiftSources {
    static var root: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()           // HermesStudioTests
            .deletingLastPathComponent()           // clients/ios
            .appendingPathComponent("HermesStudio")
    }

    /// The file names (sorted) whose **code** contains `needle`; comment
    /// lines are skipped, so a header that explains the rule does not trip it.
    static func files(containing needle: String) throws -> [String] {
        guard let enumerator = FileManager.default.enumerator(at: root, includingPropertiesForKeys: nil) else {
            throw LocalizableStrings.Error(description: "cannot enumerate \(root.path)")
        }
        var result: [String] = []
        for case let url as URL in enumerator where url.pathExtension == "swift" {
            let text = try String(contentsOf: url, encoding: .utf8)
            if code(of: text).contains(needle) { result.append(url.lastPathComponent) }
        }
        return result.sorted()
    }

    /// The source without its `//` comment lines.
    static func code(of text: String) -> String {
        text.split(separator: "\n", omittingEmptySubsequences: false)
            .filter { !$0.trimmingCharacters(in: .whitespaces).hasPrefix("//") }
            .joined(separator: "\n")
    }
}
