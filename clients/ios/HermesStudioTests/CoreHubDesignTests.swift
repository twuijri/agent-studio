import CoreGraphics
import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import HermesStudio

/// M2: design tokens, the spec icon path parser, session grouping, the
/// session-row time formatter, agent avatar mapping and browser prefs.
final class CoreHubDesignTests: XCTestCase {
    // MARK: Tokens

    func testPaletteMatchesDesignSpec() {
        let light = CoreHubTokens.PaletteHex.light
        XCTAssertEqual(light.bgPrimary, 0xFAFAFA)
        XCTAssertEqual(light.bgSidebar, 0xF5F5F5)
        XCTAssertEqual(light.bgCard, 0xFFFFFF)
        XCTAssertEqual(light.border, 0xE0E0E0)
        XCTAssertEqual(light.borderLight, 0xEBEBEB)
        XCTAssertEqual(light.accent, 0x333333)
        XCTAssertEqual(light.accentHover, 0x1A1A1A)
        XCTAssertEqual(light.accentMuted, 0x888888)
        XCTAssertEqual(light.textPrimary, 0x1A1A1A)
        XCTAssertEqual(light.textSecondary, 0x666666)
        XCTAssertEqual(light.textMuted, 0x999999)
        XCTAssertEqual(light.success, 0x2E7D32)
        XCTAssertEqual(light.error, 0xC62828)
        XCTAssertEqual(light.warning, 0xF57F17)
        XCTAssertEqual(light.info, 0x4A90D9)
        XCTAssertEqual(light.msgUser, 0xF5F5F5)
        XCTAssertEqual(light.codeBackground, 0xF4F4F4)
        XCTAssertEqual(light.textOnAccent, 0xFFFFFF)
        XCTAssertEqual(light.splash, 0xF7F7F4)

        let dark = CoreHubTokens.PaletteHex.dark
        XCTAssertEqual(dark.bgPrimary, 0x1A1A1A)
        XCTAssertEqual(dark.bgSecondary, 0x252525)
        XCTAssertEqual(dark.bgSidebar, 0x202020)
        XCTAssertEqual(dark.bgCard, 0x2A2A2A)
        XCTAssertEqual(dark.bgCardHover, 0x333333)
        XCTAssertEqual(dark.bgComposer, 0x333333)
        XCTAssertEqual(dark.border, 0x3A3A3A)
        XCTAssertEqual(dark.accent, 0xE0E0E0)
        XCTAssertEqual(dark.accentHover, 0xF5F5F5)
        XCTAssertEqual(dark.textPrimary, 0xE0E0E0)
        XCTAssertEqual(dark.textSecondary, 0xA0A0A0)
        XCTAssertEqual(dark.textMuted, 0x888888)
        XCTAssertEqual(dark.success, 0x66BB6A)
        XCTAssertEqual(dark.error, 0xEF5350)
        XCTAssertEqual(dark.warning, 0xFFB74D)
        XCTAssertEqual(dark.info, 0x6BA3D6)
        XCTAssertEqual(dark.msgAssistant, 0x292929)
        XCTAssertEqual(dark.codeBackground, 0x1E1E1E)
        XCTAssertEqual(dark.textOnAccent, 0x1A1A1A)
        XCTAssertEqual(dark.splash, 0x1A1A1A)
    }

    func testStateAlphasTypographyRadiiAndShadows() {
        XCTAssertEqual(CoreHubTokens.Alpha.hover, 0.06)
        XCTAssertEqual(CoreHubTokens.Alpha.selected, 0.12)
        XCTAssertEqual(CoreHubTokens.Alpha.inputBorderIdle, 0.18)
        XCTAssertEqual(CoreHubTokens.Alpha.inputBorderHover, 0.32)
        XCTAssertEqual(CoreHubTokens.Alpha.textSelection, 0.30)
        XCTAssertEqual(CoreHubTokens.Alpha.drawerScrim, 0.40)

        XCTAssertEqual(CoreHubTokens.Typography.base, 14)
        XCTAssertEqual(CoreHubTokens.Typography.title, 16)
        XCTAssertEqual(CoreHubTokens.Typography.titleWeight, .semibold)
        XCTAssertEqual(CoreHubTokens.Typography.navItem, 14)
        XCTAssertEqual(CoreHubTokens.Typography.sidebarTab, 13)
        XCTAssertEqual(CoreHubTokens.Typography.sessionTitle, 13)
        XCTAssertEqual(CoreHubTokens.Typography.author, 12)
        XCTAssertEqual(CoreHubTokens.Typography.meta, 11)
        XCTAssertEqual(CoreHubTokens.Typography.groupHeader, 10)
        XCTAssertEqual(CoreHubTokens.Typography.groupHeaderWeight, .semibold)
        XCTAssertEqual(CoreHubTokens.Typography.groupHeaderTracking, 0.5)
        XCTAssertEqual(CoreHubTokens.Typography.categoryTag, 10)
        XCTAssertEqual(CoreHubTokens.Typography.code, 13)
        XCTAssertEqual(CoreHubTokens.Typography.inputMinimum, 16)

        XCTAssertEqual(CoreHubTokens.Radius.button, 6)
        XCTAssertEqual(CoreHubTokens.Radius.control, 8)
        XCTAssertEqual(CoreHubTokens.Radius.bubble, 10)
        XCTAssertEqual(CoreHubTokens.Radius.card, 14)
        XCTAssertEqual(CoreHubTokens.Radius.composer, 18)
        XCTAssertEqual(CoreHubTokens.Radius.pill, 999)
        XCTAssertEqual(CoreHubTokens.Radius.tag, 4)
        XCTAssertEqual(CoreHubTokens.Radius.segment, 5)

        XCTAssertEqual(CoreHubTokens.Shadow.card, CoreHubTokens.ShadowSpec(opacity: 0.10, blur: 24, y: 8))
        XCTAssertEqual(CoreHubTokens.Shadow.composer.opacity, 0.08)
        XCTAssertEqual(CoreHubTokens.Shadow.composerDark.opacity, 0.32)
        XCTAssertEqual(CoreHubTokens.Shadow.focused, CoreHubTokens.ShadowSpec(opacity: 0.11, blur: 32, y: 10))
        XCTAssertEqual(CoreHubTokens.Shadow.card.radius, 12)
        XCTAssertEqual(CoreHubTokens.Motion.fast, 0.15)
        XCTAssertEqual(CoreHubTokens.Motion.normal, 0.25)
        XCTAssertEqual(CoreHubTokens.Layout.sidebarWidth, 240)
        XCTAssertEqual(CoreHubTokens.Layout.headerHeight, 60)
        XCTAssertEqual(CoreHubTokens.Layout.recentDefault, 10)
    }

    func testHexColourComponents() {
        let colour = UIColor(coreHubHex: 0x4A90D9)
        var red: CGFloat = 0, green: CGFloat = 0, blue: CGFloat = 0, alpha: CGFloat = 0
        colour.getRed(&red, green: &green, blue: &blue, alpha: &alpha)
        XCTAssertEqual(red, 0x4A / 255, accuracy: 0.001)
        XCTAssertEqual(green, 0x90 / 255, accuracy: 0.001)
        XCTAssertEqual(blue, 0xD9 / 255, accuracy: 0.001)
        XCTAssertEqual(alpha, 1)
    }

    // MARK: Icon path parser

    func testTokenizerSplitsCompactNegativeNumbers() {
        let tokens = IconPath.tokenize("m20 20-3.5-3.5")
        XCTAssertEqual(tokens, [.letter("m"), .number(20), .number(20), .number(-3.5), .number(-3.5)])
        XCTAssertEqual(IconPath.tokenize("M1.5.5"), [.letter("M"), .number(1.5), .number(0.5)])
        XCTAssertEqual(IconPath.tokenize("H8,V2"), [.letter("H"), .number(8), .letter("V"), .number(2)])
    }

    func testParsesAbsoluteAndRelativeLines() {
        XCTAssertEqual(IconPath.parse("M12 5v14 M5 12h14"), [
            .move(CGPoint(x: 12, y: 5)), .line(CGPoint(x: 12, y: 19)),
            .move(CGPoint(x: 5, y: 12)), .line(CGPoint(x: 19, y: 12)),
        ])
        XCTAssertEqual(IconPath.parse("m20 20-3.5-3.5"), [.move(CGPoint(x: 20, y: 20)), .line(CGPoint(x: 16.5, y: 16.5))])
        XCTAssertEqual(IconPath.parse("M2 14h2M20 14h2"), [
            .move(CGPoint(x: 2, y: 14)), .line(CGPoint(x: 4, y: 14)),
            .move(CGPoint(x: 20, y: 14)), .line(CGPoint(x: 22, y: 14)),
        ])
    }

    /// Relative coordinates accumulate floating-point error (8.2 + 7.6 is not
    /// exactly 15.8), so compare points with a tolerance instead of `==`.
    private func assertCommands(_ actual: [IconPathCommand], _ expected: [IconPathCommand], file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertEqual(actual.count, expected.count, "command count", file: file, line: line)
        for (a, e) in zip(actual, expected) {
            switch (a, e) {
            case let (.move(pa), .move(pe)), let (.line(pa), .line(pe)):
                XCTAssertEqual(pa.x, pe.x, accuracy: 0.001, file: file, line: line)
                XCTAssertEqual(pa.y, pe.y, accuracy: 0.001, file: file, line: line)
            case (.close, .close):
                break
            default:
                XCTFail("command kind mismatch: \(a) vs \(e)", file: file, line: line)
            }
        }
    }

    func testImplicitLineToAfterMoveAndClose() {
        assertCommands(IconPath.parse("M4.9 4.9 7 7"), [.move(CGPoint(x: 4.9, y: 4.9)), .line(CGPoint(x: 7, y: 7))])
        assertCommands(IconPath.parse("m8.2 10.7 7.6-4.4M8.2 13.3l7.6 4.4"), [
            .move(CGPoint(x: 8.2, y: 10.7)), .line(CGPoint(x: 15.8, y: 6.3)),
            .move(CGPoint(x: 8.2, y: 13.3)), .line(CGPoint(x: 15.8, y: 17.7)),
        ])
        let closed = IconPath.parse("M9 3h6l-1 7 3 3H7l3-3z")
        XCTAssertEqual(closed.last, .close)
        XCTAssertEqual(closed.count, 7)
    }

    func testParsesCubicCurvesOfTheCoreHubMark() {
        let commands = IconPath.parse("M302 110H738C847 110 922 180 922 286V425Z")
        XCTAssertEqual(commands[0], .move(CGPoint(x: 302, y: 110)))
        XCTAssertEqual(commands[1], .line(CGPoint(x: 738, y: 110)))
        XCTAssertEqual(commands[2], .curve(to: CGPoint(x: 922, y: 286), control1: CGPoint(x: 847, y: 110), control2: CGPoint(x: 922, y: 180)))
        XCTAssertEqual(commands[3], .line(CGPoint(x: 922, y: 425)))
        XCTAssertEqual(commands[4], .close)
    }

    func testArcEndsAtTheEndpointAndStaysOnTheCircle() throws {
        // Quarter circle of the chat bubble: a2 2 0 0 1-2 2 from (21,15).
        let start = CGPoint(x: 21, y: 15)
        let segments = IconPath.arcToCurves(from: start, to: CGPoint(x: 19, y: 17), rx: 2, ry: 2, rotationDegrees: 0, largeArc: false, sweep: true)
        XCTAssertEqual(segments.count, 1)
        let end = try XCTUnwrap(segments.last).end
        XCTAssertEqual(end.x, 19, accuracy: 0.0001)
        XCTAssertEqual(end.y, 17, accuracy: 0.0001)
        // The centre of this arc is (19,15); control points sit near the circle.
        let center = CGPoint(x: 19, y: 15)
        for segment in segments {
            let mid = cubicPoint(start, segment.control1, segment.control2, segment.end, 0.5)
            XCTAssertEqual(hypot(mid.x - center.x, mid.y - center.y), 2, accuracy: 0.01)
        }
    }

    func testHalfCircleArcSplitsIntoTwoSegments() {
        let segments = IconPath.arcToCurves(from: CGPoint(x: 16, y: 3.13), to: CGPoint(x: 16, y: 10.88), rx: 4, ry: 4, rotationDegrees: 0, largeArc: false, sweep: true)
        XCTAssertEqual(segments.count, 2)
        XCTAssertEqual(segments.last?.end.y ?? 0, 10.88, accuracy: 0.0001)
        XCTAssertTrue(IconPath.arcToCurves(from: .zero, to: .zero, rx: 1, ry: 1, rotationDegrees: 0, largeArc: false, sweep: false).isEmpty)
    }

    func testArcCommandsExpandIntoCurves() {
        let commands = IconPath.parse("M21 15a2 2 0 0 1-2 2H8")
        XCTAssertEqual(commands.first, .move(CGPoint(x: 21, y: 15)))
        guard case let .curve(to, _, _) = commands[1] else { return XCTFail("expected a curve after the arc") }
        XCTAssertEqual(to.x, 19, accuracy: 0.0001)
        XCTAssertEqual(to.y, 17, accuracy: 0.0001)
        XCTAssertEqual(commands.last, .line(CGPoint(x: 8, y: 17)))
    }

    func testEverySpecIconProducesDrawing() {
        for icon in CoreHubIcon.allCases {
            let path = IconPath.path(icon.shapes, in: CGRect(x: 0, y: 0, width: 24, height: 24))
            XCTAssertFalse(path.isEmpty, "\(icon) draws nothing")
            let bounds = path.boundingRect
            XCTAssertGreaterThanOrEqual(bounds.minX, -0.5, "\(icon) leaves the viewBox")
            XCTAssertLessThanOrEqual(bounds.maxX, 24.5, "\(icon) leaves the viewBox")
        }
        XCTAssertTrue(CoreHubIcon.chevronForward.mirrorsInRTL)
        XCTAssertFalse(CoreHubIcon.search.mirrorsInRTL)
    }

    func testPathScalesViewBoxIntoFrame() {
        let path = IconPath.path([.circle(cx: 12, cy: 12, r: 12)], in: CGRect(x: 10, y: 10, width: 48, height: 48))
        let bounds = path.boundingRect
        XCTAssertEqual(bounds.minX, 10, accuracy: 0.001)
        XCTAssertEqual(bounds.width, 48, accuracy: 0.001)
    }

    private func cubicPoint(_ p0: CGPoint, _ p1: CGPoint, _ p2: CGPoint, _ p3: CGPoint, _ t: CGFloat) -> CGPoint {
        let mt = 1 - t
        let x = mt * mt * mt * p0.x + 3 * mt * mt * t * p1.x + 3 * mt * t * t * p2.x + t * t * t * p3.x
        let y = mt * mt * mt * p0.y + 3 * mt * mt * t * p1.y + 3 * mt * t * t * p2.y + t * t * t * p3.y
        return CGPoint(x: x, y: y)
    }

    // MARK: Session time formatter

    func testSessionTimeShowsClockForTodayAndMonthDayOtherwise() {
        let zone = TimeZone(identifier: "UTC")!
        let now = Date(timeIntervalSince1970: 1_789_050_000) // 2026-09-10 14:20:00 UTC
        let earlier = now.addingTimeInterval(-3 * 3600) // same day, 11:20
        let sameDay = SessionTimeFormatter.string(for: earlier, now: now, locale: Locale(identifier: "en_US_POSIX"), timeZone: zone)
        XCTAssertEqual(sameDay, "11:20")
        let yesterday = SessionTimeFormatter.string(for: now.addingTimeInterval(-36 * 3600), now: now, locale: Locale(identifier: "en_US_POSIX"), timeZone: zone)
        XCTAssertEqual(yesterday, "Sep 9")
        XCTAssertEqual(SessionTimeFormatter.string(for: nil, now: now), "")
        XCTAssertEqual(SessionTimeFormatter.string(for: "", now: now), "")
    }

    func testSessionTimeAcceptsStudioTimestamps() {
        let zone = TimeZone(identifier: "UTC")!
        let now = Date(timeIntervalSince1970: 1_789_000_000)
        // Unix seconds and milliseconds from the sessions API.
        let seconds = SessionTimeFormatter.string(for: StudioTimestamp.date(from: "1788000000"), now: now, locale: Locale(identifier: "en_US_POSIX"), timeZone: zone)
        XCTAssertEqual(seconds, "Aug 29")
        let millis = SessionTimeFormatter.string(for: StudioTimestamp.date(from: "1788000000000"), now: now, locale: Locale(identifier: "en_US_POSIX"), timeZone: zone)
        XCTAssertEqual(millis, "Aug 29")
    }

    // MARK: Grouping

    private func session(_ id: String, category: Int? = nil, updated: Double) -> SessionSummary {
        var json: JSON = ["id": id, "title": id, "profile": "main", "last_active": updated]
        if let category { json["category_id"] = category }
        return SessionSummary(json)
    }

    func testGroupsFollowRecentPinnedCategoriesUncategorizedOrder() {
        let sessions = [
            session("a", category: 1, updated: 100),
            session("b", updated: 300),
            session("c", category: 2, updated: 200),
            session("d", category: 99, updated: 50), // unknown category → uncategorized
        ]
        let categories = [SessionCategory(["id": 1, "name": "Work"]), SessionCategory(["id": 2, "name": "Home"]), SessionCategory(["id": 3, "name": "Empty"])]
        let groups = SessionGrouping.groups(sessions: sessions, categories: categories, pinnedIDs: ["c"], recentCount: 2, labels: .init(recent: "Recent", pinned: "Pinned", uncategorized: "Uncategorized"))
        XCTAssertEqual(groups.map(\.id), ["recent", "pinned", "category-1", "category-2", "category-none"])
        XCTAssertEqual(groups[0].sessions.map(\.id), ["b", "c"], "recent = newest first, limited")
        XCTAssertEqual(groups[1].sessions.map(\.id), ["c"])
        XCTAssertEqual(groups[2].sessions.map(\.id), ["a"])
        XCTAssertEqual(groups[3].sessions.map(\.id), ["c"], "recent is a shortcut; sessions stay in their category")
        XCTAssertEqual(groups[4].sessions.map(\.id), ["b", "d"])
        XCTAssertEqual(groups[4].label, "Uncategorized")
    }

    func testRecentCountIsClampedAndEmptyGroupsAreDropped() {
        XCTAssertEqual(SessionBrowserPrefs.clampRecent(0), 1)
        XCTAssertEqual(SessionBrowserPrefs.clampRecent(500), 100)
        XCTAssertEqual(SessionBrowserPrefs.clampRecent(10), 10)
        let groups = SessionGrouping.groups(sessions: [], categories: [SessionCategory(["id": 1, "name": "Work"])], pinnedIDs: ["x"], recentCount: 10, labels: .init(recent: "R", pinned: "P", uncategorized: "U"))
        XCTAssertTrue(groups.isEmpty)
    }

    func testWorkspaceChipUsesLastPathSegment() {
        XCTAssertEqual(WorkspaceChip.label(for: "/home/agent/projects/core-hub"), "core-hub")
        XCTAssertEqual(WorkspaceChip.label(for: "C:\\work\\repo\\"), "repo")
        XCTAssertEqual(WorkspaceChip.label(for: "  "), "")
        XCTAssertEqual(WorkspaceChip.label(for: "repo"), "repo")
    }

    // MARK: Agent avatars (chat-agent-avatar.ts)

    func testAgentAvatarMappingMirrorsTheWebClient() {
        XCTAssertEqual(AgentAvatarAsset.resolve(runtime: "ekko", source: "cli"), .ekko)
        XCTAssertEqual(AgentAvatarAsset.resolve(runtime: "ekko_agent", source: nil), .ekko)
        XCTAssertEqual(AgentAvatarAsset.resolve(runtime: "claude", source: "coding_agent"), .claude)
        XCTAssertEqual(AgentAvatarAsset.resolve(runtime: "Codex", source: "coding_agent"), .codex)
        XCTAssertEqual(AgentAvatarAsset.resolve(runtime: "pi", source: nil), .pi)
        XCTAssertEqual(AgentAvatarAsset.resolve(runtime: "grok", source: nil), .grok)
        XCTAssertEqual(AgentAvatarAsset.resolve(runtime: "dsh", source: nil), .deepseek)
        XCTAssertEqual(AgentAvatarAsset.resolve(runtime: "opencode", source: nil), .opencode)
        XCTAssertEqual(AgentAvatarAsset.resolve(runtime: "unknown", source: "coding_agent"), .claude)
        XCTAssertEqual(AgentAvatarAsset.resolve(runtime: "hermes", source: "cli"), .hermes)
        XCTAssertEqual(AgentAvatarAsset.resolve(runtime: nil, source: nil), .ekko, "no session → Ekko like the web")
        XCTAssertEqual(AgentAvatarAsset.resolve(session: SessionSummary(["id": "s", "coding_agent_id": "claude-code", "source": "coding_agent"])), .claude)
        XCTAssertEqual(AgentAvatarAsset.hermes.rawValue, "Agent-hermes")
        XCTAssertEqual(AgentAvatarAsset.deepseek.label, "DeepSeek Harness")
    }

    // MARK: Browser prefs

    func testBrowserPrefsPinsArePerProfileAndPruned() {
        let defaults = UserDefaults(suiteName: "CoreHubDesignTests-\(UUID().uuidString)")!
        let prefs = SessionBrowserPrefs(defaults: defaults)
        XCTAssertEqual(prefs.recentCount, 10)
        prefs.recentCount = 250
        XCTAssertEqual(prefs.recentCount, 100)
        prefs.togglePin("s1", profile: "main")
        prefs.togglePin("s2", profile: "main")
        XCTAssertEqual(prefs.pinnedIDs(profile: "main"), ["s1", "s2"])
        XCTAssertTrue(prefs.pinnedIDs(profile: "other").isEmpty)
        prefs.togglePin("s1", profile: "main")
        XCTAssertEqual(prefs.pinnedIDs(profile: "main"), ["s2"])
        XCTAssertTrue(prefs.prunePins(existing: ["s9"], profile: "main"))
        XCTAssertTrue(prefs.pinnedIDs(profile: "main").isEmpty)
        XCTAssertFalse(prefs.prunePins(existing: [], profile: "main"))
        prefs.setCollapsed("recent", true)
        XCTAssertTrue(prefs.isCollapsed("recent"))
        prefs.setCollapsed("recent", false)
        XCTAssertFalse(prefs.isCollapsed("recent"))
        prefs.markUnread("s3")
        XCTAssertTrue(prefs.unreadIDs().contains("s3"))
        prefs.markRead("s3")
        XCTAssertFalse(prefs.unreadIDs().contains("s3"))
    }

    // MARK: Navigation model

    func testCurrentUserSuperAdminFlagAndHealthParsing() {
        XCTAssertTrue(CurrentUser(id: 1, username: "root", role: "super_admin", status: "active", avatar: nil).isSuperAdmin)
        XCTAssertFalse(CurrentUser(id: 2, username: "u", role: "admin", status: "active", avatar: nil).isSuperAdmin)
        XCTAssertEqual(ConversationMode.allCases.map(\.rawValue), ["chat", "group", "workflow", "history"])
        XCTAssertEqual(ConversationMode.workflow.icon, .workflow)
    }

    func testContextIndicatorCompactNumbers() {
        XCTAssertEqual(ContextUsageView.compact(45_000), "45.0k")
        XCTAssertEqual(ContextUsageView.compact(256_000), "256.0k")
        XCTAssertEqual(ContextUsageView.compact(1_500_000), "1.5M")
        XCTAssertEqual(ContextUsageView.compact(999), "999")
    }
}
