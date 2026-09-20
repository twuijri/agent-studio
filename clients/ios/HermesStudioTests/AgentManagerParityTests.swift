import XCTest
@testable import HermesStudio

/// The Agent Manager, the agent screen and Hermes › Settings have to look
/// the same on both phones, and here Android is the reference: the owner
/// compared them and chose its design. `CoreHubTokens.swift` holds the
/// numbers and the `Features/Agents` views spend them; what is checked is
/// that every value iOS writes down is the value the Kotlin writes down —
/// read out of `AgentManagerScreen.kt`, `AgentScreen.kt`, `DesignSystem.kt`
/// and `MainActivity.kt`, not copied into this file — so that moving one
/// side without the other fails the build.
///
/// The mirror of Android's `DrawerParityTest`, which reads the Swift.
final class AgentManagerParityTests: XCTestCase {

    // MARK: - Sources

    /// `clients/android/app/src/main/java/us/i3u/hermesstudio`, next to the
    /// iOS tree; the Android tree may be absent from a partial checkout.
    static let android = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent()           // HermesStudioTests
        .deletingLastPathComponent()           // clients/ios
        .deletingLastPathComponent()           // clients
        .appendingPathComponent("android/app/src/main/java/us/i3u/hermesstudio")

    private func kotlin(_ path: String) throws -> String {
        let url = Self.android.appendingPathComponent(path)
        guard let text = try? String(contentsOf: url, encoding: .utf8) else { throw XCTSkip("no Android source at \(url.path)") }
        return text
    }

    private func swift(_ path: String) throws -> String {
        try String(contentsOf: SwiftSources.root.appendingPathComponent(path), encoding: .utf8)
    }

    private var tokens: String { get throws { try swift("Theme/CoreHubTokens.swift") } }

    /// `static let name: CGFloat = 34` / `static let name = 0.16` out of the Swift.
    private func ios(_ name: String) throws -> Double {
        guard let value = try capture(tokens, #"static let \#(name)(?::\s*\w+)?\s*=\s*(-?[\d.]+)"#).first else {
            XCTFail("iOS has no token named \(name)"); return .nan
        }
        return value
    }

    /// The first capture group of every match, as numbers.
    private func captures(_ source: String, _ pattern: String) throws -> [[Double]] {
        let regex = try NSRegularExpression(pattern: pattern)
        let range = NSRange(source.startIndex..., in: source)
        return regex.matches(in: source, range: range).map { match in
            (1..<match.numberOfRanges).compactMap { index in
                Range(match.range(at: index), in: source).flatMap { Double(source[$0]) }
            }
        }
    }

    private func capture(_ source: String, _ pattern: String) throws -> [Double] {
        try captures(source, pattern).first ?? []
    }

    private func same(_ pattern: String, in source: String, _ names: String..., file: StaticString = #filePath, line: UInt = #line) throws {
        let found = try capture(source, pattern)
        XCTAssertEqual(found.count, names.count, "\(pattern) should capture \(names)", file: file, line: line)
        for (value, name) in zip(found, names) {
            XCTAssertEqual(value, try ios(name), accuracy: 0.0001, "\(name) must equal Android's \(pattern)", file: file, line: line)
        }
    }

    // MARK: - The Agent Manager card (`AgentRow`)

    func testTheCardMatchesTheAndroidCard() throws {
        let manager = try kotlin("ui/agents/AgentManagerScreen.kt")
        try same(#"AgentAvatar\([^\n]*size = ([\d.]+)\.dp\)"#, in: manager, "agentCardAvatar")
        try same(#"AgentAvatar\([^\n]*\)\n\s*Spacer\(Modifier\.width\(([\d.]+)\.dp\)\)"#, in: manager, "agentCardAvatarGap")
        try same(#"Column\(Modifier\.padding\(([\d.]+)\.dp\), verticalArrangement = Arrangement\.spacedBy\(([\d.]+)\.dp\)\)"#, in: manager, "agentCardPadding", "agentCardGap")
        try same(#"border = BorderStroke\(([\d.]+)\.dp, palette\.border\)"#, in: manager, "agentCardBorder")
        try same(#"Column\(horizontalAlignment = Alignment\.End, verticalArrangement = Arrangement\.spacedBy\(([\d.]+)\.dp\)\)"#, in: manager, "agentCardTrailingGap")
        // The card's radius and the note's, wherever they sit in the file.
        let radii = try captures(manager, #"RoundedCornerShape\(([\d.]+)\.dp\)"#).compactMap(\.first)
        XCTAssertTrue(radii.contains(try ios("agentCard")), "card radius \(radii)")
        XCTAssertTrue(radii.contains(try ios("agentNote")), "note radius \(radii)")
        // The state pill is 10 × 5, the `Update` pill 12 × 5, both on their colour at 16 %.
        let paddings = try captures(manager, #"Modifier\.padding\(horizontal = ([\d.]+)\.dp, vertical = ([\d.]+)\.dp\)"#)
        XCTAssertTrue(paddings.contains([try ios("agentPillPaddingH"), try ios("agentPillPaddingV")]), "state pill \(paddings)")
        XCTAssertTrue(paddings.contains([try ios("agentUpdatePaddingH"), try ios("agentPillPaddingV")]), "update pill \(paddings)")
        try same(#"color = color\.copy\(alpha = ([\d.]+)f\)"#, in: manager, "agentPill")
        try same(#"alpha = if \(enabled\) ([\d.]+)f else ([\d.]+)f"#, in: manager, "agentPill", "agentPillDisabled")
        // The gear on `Agent settings`, and the gap between the actions.
        try same(#"Icon\(Icons\.Filled\.Settings, null, Modifier\.size\(([\d.]+)\.dp\)\)\n\s*Spacer\(Modifier\.width\(([\d.]+)\.dp\)\)"#, in: manager, "agentActionIcon", "agentActionIconGap")
        try same(#"FlowRow\(horizontalArrangement = Arrangement\.spacedBy\(([\d.]+)\.dp\)\)"#, in: manager, "agentActionGap")
    }

    /// The list around the cards: gutters, spacing, the intro note.
    func testTheListMatchesTheAndroidList() throws {
        let manager = try kotlin("ui/agents/AgentManagerScreen.kt")
        try same(#"PaddingValues\(StudioHorizontalPadding, ([\d.]+)\.dp, StudioHorizontalPadding, ([\d.]+)\.dp\)"#, in: manager, "agentListPaddingTop", "agentListPaddingBottom")
        try same(#"verticalArrangement = Arrangement\.spacedBy\(([\d.]+)\.dp\),\n\s*\) \{"#, in: manager, "agentListGap")
        try same(#"Modifier\.padding\(top = ([\d.]+)\.dp\)"#, in: manager, "agentSectionTop")
        try same(#"color = palette\.info\.copy\(alpha = ([\d.]+)f\)"#, in: manager, "agentNoteFill")
        try same(#"Modifier\.padding\(([\d.]+)\.dp\),\n\s*style = MaterialTheme\.typography\.bodySmall"#, in: manager, "agentNotePadding")
        try same(#"StudioHorizontalPadding = ([\d.]+)\.dp"#, in: try kotlin("DesignSystem.kt"), "screenPaddingH")
        // The sections come in Android's `AgentKind` order.
        let catalog = try kotlin("AgentCatalog.kt")
        XCTAssertTrue(catalog.contains("enum class AgentKind { Hermes, BuiltIn, Coding }"))
        let view = SwiftSources.code(of: try swift("Features/Agents/AgentManagerView.swift"))
        let hermes = try XCTUnwrap(view.range(of: #"group("Hermes runtime""#))
        let builtIn = try XCTUnwrap(view.range(of: #"group("Built in""#))
        let coding = try XCTUnwrap(view.range(of: #"group("Coding agents""#))
        XCTAssertTrue(hermes.lowerBound < builtIn.lowerBound && builtIn.lowerBound < coding.lowerBound)
    }

    // MARK: - The agent screen (`AgentScreen` + `StudioDestinationRow`)

    func testTheAgentScreenMatchesTheAndroidScreen() throws {
        let screen = try kotlin("ui/agents/AgentScreen.kt")
        try same(#"AgentAvatar\([^\n]*size = ([\d.]+)\.dp\)"#, in: screen, "agentScreenAvatar")
        try same(#"AgentAvatar\([^\n]*\)\n\s*Spacer\(Modifier\.width\(([\d.]+)\.dp\)\)"#, in: screen, "agentCardAvatarGap")
        try same(#"verticalArrangement = Arrangement\.spacedBy\(([\d.]+)\.dp\)"#, in: screen, "agentScreenGap")
        let design = try kotlin("DesignSystem.kt")
        try same(#"fun StudioCardDivider\(startIndent: Int = (\d+)\)"#, in: design, "agentRowDividerInset")
        try same(#"modifier = Modifier\.size\(([\d.]+)\.dp\),\n\s*shape = RoundedCornerShape\(CoreHubTokens\.Radius\.bubble\)"#, in: design, "agentRowIconTile")
        try same(#"tint = color, modifier = Modifier\.size\(([\d.]+)\.dp\)"#, in: design, "agentRowIcon")
        try same(#"\.padding\(horizontal = ([\d.]+)\.dp, vertical = ([\d.]+)\.dp\),\n\s*verticalAlignment = Alignment\.CenterVertically,\n\s*\) \{\n\s*StudioIconTile"#, in: design, "agentRowPaddingH", "agentRowPaddingV")
        try same(#"StudioIconTile\(icon, color\)\n\s*Spacer\(Modifier\.width\(([\d.]+)\.dp\)\)"#, in: design, "agentRowIconGap")
        XCTAssertTrue(design.contains("containerColor = color.copy(alpha = 0.16f)"), "the icon tile sits on its colour at 16 %")
        XCTAssertEqual(try ios("agentIconTile"), 0.16, accuracy: 0.0001)
        // The rows, in both clients: capabilities first, settings last, the same glyph set.
        XCTAssertTrue(screen.contains("NavDestination.hermesSettings, NavDestination.ekkoSettings, NavDestination.codingAgentSettings -> CoreHubIcons.Settings"))
        XCTAssertEqual(AgentDetailView.symbol(for: .hermesSettings), "settings")
        XCTAssertEqual(AgentDetailView.symbol(for: .codingAgentSettings), "settings")
        for destination in AgentDetailView.capabilities(for: .hermes) + AgentDetailView.capabilities(for: .coding(id: "dsh")) {
            XCTAssertNotEqual(AgentDetailView.symbol(for: destination), "settings", "\(destination) has a glyph of its own")
        }
    }

    // MARK: - Hermes › Settings (`SettingsRowContent`, `SettingsSection`, `TabRow`)

    func testTheSettingsRowsMatchTheAndroidRows() throws {
        let activity = try kotlin("MainActivity.kt")
        let row = try block(activity, from: "private fun SettingsRowContent(")
        try same(#"padding\(horizontal = StudioHorizontalPadding, vertical = ([\d.]+)\.dp\)"#, in: row, "settingsRowOuterV")
        try same(#"\.clip\(RoundedCornerShape\(([\d.]+)\.dp\)\)"#, in: row, "agentCard")
        try same(#"\.padding\(horizontal = ([\d.]+)\.dp, vertical = ([\d.]+)\.dp\),\n\s*verticalAlignment"#, in: row, "settingsRowPaddingH", "settingsRowPaddingV")
        try same(#"horizontalArrangement = Arrangement\.spacedBy\(([\d.]+)\.dp\)"#, in: row, "settingsRowGap")
        XCTAssertTrue(row.contains("Text(label, style = MaterialTheme.typography.bodyLarge)"))
        XCTAssertTrue(row.contains("style = MaterialTheme.typography.labelSmall,\n                color = MaterialTheme.colorScheme.onSurfaceVariant,\n                maxLines = 2"))
        let section = try block(activity, from: "internal fun SettingsSection(")
        try same(#"padding\(start = ([\d.]+)\.dp, end = ([\d.]+)\.dp, top = ([\d.]+)\.dp, bottom = ([\d.]+)\.dp\)"#, in: section, "settingsSectionPaddingH", "settingsSectionPaddingH", "settingsSectionPaddingTop", "settingsSectionPaddingBottom")
        let loading = try block(activity, from: "internal fun LoadingRow(")
        try same(#"\.padding\(([\d.]+)\.dp\)"#, in: loading, "loadingRowPadding")
        try same(#"\.height\(([\d.]+)\.dp\)"#, in: loading, "loadingRowSpinner")
        try same(#"agent_autostart_note\),[\s\S]*?padding\(horizontal = ([\d.]+)\.dp, vertical = ([\d.]+)\.dp\)"#, in: activity, "settingsHintPaddingH", "settingsHintPaddingV")
        // Every change is its own PUT with one key, and "Saved" follows it.
        let viewModel = try kotlin("AppViewModel.kt")
        let setter = "fun setAgentValue(key: String, value: Any)"
        XCTAssertTrue(activity.contains(setter) || viewModel.contains(setter))
        let rows = SwiftSources.code(of: try swift("Features/Agents/HermesSettingsView.swift"))
        XCTAssertTrue(rows.contains("values: [edit.field.key: value]"), "one key per PUT")
        XCTAssertTrue(rows.contains(#"store.notify(String(localized: "Saved"))"#))
        XCTAssertTrue(rows.contains("if loading || saving { LoadingRowView() }"))
        // The three tabs, in the web's order, on both phones.
        let hermes = try kotlin("ui/agents/HermesScreens.kt")
        XCTAssertTrue(hermes.contains("SettingsGroup.Agent to R.string.hermes_settings_tab_agent,\n    SettingsGroup.Memory to R.string.hermes_settings_tab_memory,\n    SettingsGroup.Sessions to R.string.hermes_settings_tab_session,"))
        XCTAssertEqual(HermesSettingsView.sections(for: "agent"), [.agent, .gateway])
        XCTAssertEqual(HermesSettingsView.sections(for: "memory"), [.memory])
        XCTAssertEqual(HermesSettingsView.sections(for: "session"), [.session, .approvals, .skills])
        XCTAssertTrue(HermesSettingsView.showsLabel(.gateway) && !HermesSettingsView.showsLabel(.approvals))
    }

    /// The Kotlin body of one top-level function, from its signature to the
    /// first line that is a lone `}`.
    private func block(_ source: String, from signature: String) throws -> String {
        let start = try XCTUnwrap(source.range(of: signature), "missing \(signature)")
        let rest = source[start.lowerBound...]
        let end = rest.range(of: "\n}\n") ?? rest.endIndex..<rest.endIndex
        return String(rest[..<end.lowerBound])
    }

    // MARK: - Type ramp

    /// Android spends Material roles; `CoreHubTheme.kt` maps each role to a
    /// token size, and iOS spends the same token sizes.
    func testTheTypeRampMatches() throws {
        let theme = try kotlin("ui/theme/CoreHubTheme.kt")
        let kotlinTokens = try kotlin("ui/theme/CoreHubTokens.kt")
        func android(_ name: String) throws -> Double {
            try XCTUnwrap(try capture(kotlinTokens, #"val \#(name): TextUnit = ([\d.]+)\.sp"#).first, name)
        }
        XCTAssertEqual(try android("navItem"), CoreHubTokens.Typography.navItem)
        XCTAssertEqual(try android("author"), CoreHubTokens.Typography.author)
        XCTAssertEqual(try android("meta"), CoreHubTokens.Typography.meta)
        XCTAssertEqual(try android("navTab"), CoreHubTokens.Typography.sidebarTab)
        XCTAssertEqual(try android("base"), CoreHubTokens.Typography.base)
        // The roles the agent screens use, and where iOS spends the same size.
        XCTAssertTrue(theme.contains("titleMedium = defaults.titleMedium.copy(fontFamily = sans, fontSize = navItem"))
        XCTAssertTrue(theme.contains("bodySmall = defaults.bodySmall.copy(fontFamily = sans, fontSize = author"))
        XCTAssertTrue(theme.contains("labelSmall = defaults.labelSmall.copy(fontFamily = sans, fontSize = meta"))
        XCTAssertTrue(theme.contains("labelLarge = defaults.labelLarge.copy(fontFamily = sans, fontSize = navTab"))
        XCTAssertTrue(theme.contains("bodyLarge = defaults.bodyLarge.copy(fontFamily = sans, fontSize = base"))
        let manager = SwiftSources.code(of: try swift("Features/Agents/AgentManagerView.swift"))
        let chrome = SwiftSources.code(of: try swift("Features/Agents/AgentCardViews.swift"))
        let rows = SwiftSources.code(of: try swift("Features/Settings/SettingsRowViews.swift"))
        XCTAssertTrue(manager.contains("CoreHubTokens.Typography.navItem, weight: .bold"), "the name is titleMedium bold")
        XCTAssertTrue(manager.contains(".font(CoreHubTokens.Typography.authorFont)"), "vendor and meta lines are bodySmall")
        XCTAssertTrue(chrome.contains("CoreHubTokens.Typography.meta, weight: .semibold"), "the pill is labelSmall semibold")
        XCTAssertTrue(chrome.contains("CoreHubTokens.Typography.sidebarTab, weight: .medium"), "buttons are labelLarge")
        XCTAssertTrue(chrome.contains("CoreHubTokens.Typography.base, weight: .medium"), "a capability row's title is bodyLarge medium")
        XCTAssertTrue(rows.contains("Text(title).font(CoreHubTokens.Typography.bodyFont)"), "a settings row's label is bodyLarge")
        XCTAssertTrue(rows.contains("value.font(CoreHubTokens.Typography.metaFont)"), "its value is labelSmall")
    }

    // MARK: - Material constants

    /// Values Android takes from Material rather than writing down:
    /// `ButtonDefaults.MinHeight` (40) with its content padding (24 outlined,
    /// 12 text), the 48 dp tab row with a 2 dp indicator, the 24 dp icon.
    func testTheMaterialConstantsArePinned() throws {
        XCTAssertEqual(try ios("agentActionHeight"), 40)
        XCTAssertEqual(try ios("agentOutlinedPaddingH"), 24)
        XCTAssertEqual(try ios("agentTextButtonPaddingH"), 12)
        XCTAssertEqual(try ios("tabRowHeight"), 48)
        XCTAssertEqual(try ios("tabIndicator"), 2)
        XCTAssertEqual(try ios("settingsRowIcon"), 24)
        // Android's outlined button, text buttons and tab row are Material's.
        let manager = try kotlin("ui/agents/AgentManagerScreen.kt")
        XCTAssertTrue(manager.contains("OutlinedButton(onClick = openAgent, enabled = !busy)"))
        XCTAssertTrue(manager.contains("TextButton(onClick = onCliDetails, enabled = !busy)"))
        XCTAssertTrue((try kotlin("ui/agents/HermesScreens.kt")).contains("TabRow("))
    }

    // MARK: - Tokens only

    /// Nothing in the agent screens carries its own colour, size or radius.
    func testTheScreensAreBuiltFromTokens() throws {
        let files = [
            "Features/Agents/AgentManagerView.swift": [
                "CoreHubTokens.Layout.agentListGap", "CoreHubTokens.Layout.screenPaddingH", "CoreHubTokens.Layout.agentListPaddingTop",
                "CoreHubTokens.Layout.agentListPaddingBottom", "CoreHubTokens.Layout.agentCardAvatar", "CoreHubTokens.Layout.agentCardAvatarGap",
                "CoreHubTokens.Layout.agentCardTrailingGap", "CoreHubTokens.Layout.agentUpdatePaddingH", "CoreHubTokens.Alpha.agentPillDisabled",
                "CoreHubTokens.Radius.agentCard",
            ],
            "Features/Agents/AgentCardViews.swift": [
                "CoreHubTokens.Layout.agentCardPadding", "CoreHubTokens.Layout.agentCardGap", "CoreHubTokens.Layout.agentCardBorder",
                "CoreHubTokens.Layout.agentSectionTop", "CoreHubTokens.Layout.agentNotePadding", "CoreHubTokens.Alpha.agentNoteFill",
                "CoreHubTokens.Layout.agentPillPaddingH", "CoreHubTokens.Layout.agentPillPaddingV", "CoreHubTokens.Alpha.agentPill",
                "CoreHubTokens.Layout.agentActionHeight", "CoreHubTokens.Layout.agentActionIcon", "CoreHubTokens.Layout.agentActionIconGap",
                "CoreHubTokens.Layout.agentOutlinedPaddingH", "CoreHubTokens.Layout.agentTextButtonPaddingH", "CoreHubTokens.Layout.agentActionGap",
                "CoreHubTokens.Layout.agentRowIconTile", "CoreHubTokens.Layout.agentRowIcon", "CoreHubTokens.Layout.agentRowIconGap",
                "CoreHubTokens.Layout.agentRowPaddingH", "CoreHubTokens.Layout.agentRowPaddingV", "CoreHubTokens.Layout.agentRowDividerInset",
                "CoreHubTokens.Layout.loadingRowSpinner", "CoreHubTokens.Layout.loadingRowPadding", "CoreHubTokens.Alpha.agentIconTile",
            ],
            "Features/Agents/AgentDetailView.swift": ["CoreHubTokens.Layout.agentScreenGap", "CoreHubTokens.Layout.agentScreenAvatar"],
            "Features/Settings/SettingsRowViews.swift": [
                "CoreHubTokens.Layout.tabRowHeight", "CoreHubTokens.Layout.tabIndicator", "CoreHubTokens.Layout.settingsRowGap",
                "CoreHubTokens.Layout.settingsRowIcon", "CoreHubTokens.Layout.settingsRowPaddingH", "CoreHubTokens.Layout.settingsRowPaddingV",
                "CoreHubTokens.Layout.settingsRowOuterV", "CoreHubTokens.Layout.settingsSectionPaddingH", "CoreHubTokens.Layout.settingsSectionPaddingTop",
                "CoreHubTokens.Layout.settingsSectionPaddingBottom", "CoreHubTokens.Layout.settingsHintPaddingH", "CoreHubTokens.Layout.settingsHintPaddingV",
            ],
        ]
        for (path, names) in files {
            let code = SwiftSources.code(of: try swift(path))
            XCTAssertFalse(code.contains("Color(red:") || code.contains("Color(uiColor:") || code.contains("UIColor("), "\(path) draws its own colour")
            for name in names { XCTAssertTrue(code.contains(name), "\(path) should use \(name)") }
        }
    }

    // MARK: - Words

    /// Every string the redesigned screens surface exists in both languages.
    func testEveryAgentStringExistsInBothLocales() throws {
        let english = try LocalizableStrings.load(locale: "en")
        let arabic = try LocalizableStrings.load(locale: "ar")
        let keys = [
            "Every agent Core Hub knows, with what this server reports about it.",
            "Installing, updating and removing an agent runs on the Core Hub server, not on this phone.",
            "Hermes runtime", "Built in", "Coding agents", "Installed", "Not installed",
            "Agent settings", "CLI details", "Update", "Update automatically", "Reinstall", "Install", "Check for an update", "Delete",
            "%@ available", "Capabilities first, settings last. Back returns to the Agent Manager.",
            "Opening a native terminal is a desktop action; it is not available from the phone.",
            "Remove %@?", "The server will uninstall %@ globally. Its settings files stay where they are.", "Dismiss",
            "State", "Source", "Version", "Path", "Package", "Command", "In use",
            "Agent", "Memory", "Session", "Saved", "Save", "Cancel",
            "Maximum turns", "Gateway timeout", "Restart drain timeout", "Tool use",
            "Memory enabled", "User profile memory", "Memory character limit", "User context limit", "Approve memory writes",
            "Reset mode", "Idle minutes", "Daily reset hour", "Approval mode", "Approve skill changes",
            "Start gateways automatically", "Management", "Gateway auto-start",
            "Interaction rounds per conversation", "Let the agent keep durable memories", "Inactivity before a new session",
            "Server local time, from 0 to 23", "Ask before changing installed skills",
        ]
        for key in keys {
            XCTAssertNotNil(english[key], "missing English \(key)")
            XCTAssertNotNil(arabic[key], "missing Arabic \(key)")
        }
        // The detail screen's install/update/remove section left with its strings.
        for key in ["Install on the server", "Remove from the server", "Check for updates", "Update status", "Latest version"] {
            XCTAssertNil(english[key], key)
            XCTAssertNil(arabic[key], key)
        }
    }

    // MARK: - The card's text

    func testTheMetaLineReadsLikeAndroids() {
        let codex = AgentRuntimeStatus(["id": "codex", "kind": "coding-agent", "installed": true, "source": "user-cli", "version": "0.154.0"])
        XCTAssertEqual(AgentCardText.meta(agent: codex, tool: nil, offered: nil), "Local CLI · v0.154.0")
        XCTAssertEqual(AgentCardText.meta(agent: codex, tool: nil, offered: "0.155.1"), "Local CLI · v0.154.0 · v0.155.1 available")
        let claude = AgentRuntimeStatus(["id": "claude-code", "kind": "coding-agent", "installed": false])
        XCTAssertNil(AgentCardText.meta(agent: claude, tool: nil, offered: nil), "a card without source or version has no meta line")
        XCTAssertEqual(AgentCardText.screenMeta(agent: codex, tool: nil), "v0.154.0 · Local CLI")
        let hermes = AgentRuntimeStatus(["id": "hermes", "kind": "hermes", "installed": true, "source": "user-cli", "version": "0.21.3", "path": "/opt/hermes/.venv/bin/hermes"])
        XCTAssertEqual(AgentCardText.cliDetails(agent: hermes, tool: nil), "v0.21.3\n/opt/hermes/.venv/bin/hermes")
        XCTAssertEqual(AgentCardText.cliDetails(agent: claude, tool: nil), "—\n—")
        XCTAssertEqual(HermesSettingEdit.optionLabel("per_profile"), "Per profile")
        XCTAssertEqual(HermesSettingEdit.parse(" 42 ", kind: .number) as? Int, 42)
        XCTAssertEqual(HermesSettingEdit.parse("0.5", kind: .decimal) as? Double, 0.5)
    }
}
