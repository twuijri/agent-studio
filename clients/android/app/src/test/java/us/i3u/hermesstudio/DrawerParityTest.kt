package us.i3u.hermesstudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The drawer has to look the same on both phones.
 *
 * iOS is the reference: `clients/ios/HermesStudio/Theme/CoreHubTokens.swift`
 * holds the numbers and `Features/SidebarDrawer.swift` spends them. Nothing
 * here renders anything, so what is checked is that every value Android
 * writes down is the value iOS wrote down — read out of the Swift, not copied
 * into this file, so that moving one side without the other fails the build.
 *
 * Follows `AttachmentSheetTest`, which does the same for the composer sheet.
 */
class DrawerParityTest {

    private val tokens = File("src/main/java/us/i3u/hermesstudio/ui/theme/CoreHubTokens.kt").readText()
    private val theme = File("src/main/java/us/i3u/hermesstudio/ui/theme/CoreHubTheme.kt").readText()
    private val icons = File("src/main/java/us/i3u/hermesstudio/ui/theme/CoreHubIcons.kt").readText()
    private val drawer = File("src/main/java/us/i3u/hermesstudio/ui/navigation/CoreHubDrawer.kt").readText()
    private val iosTokensFile = File("../../ios/HermesStudio/Theme/CoreHubTokens.swift")
    private val iosDrawerFile = File("../../ios/HermesStudio/Features/SidebarDrawer.swift")
    private val iosIconsFile = File("../../ios/HermesStudio/Theme/CoreHubIcons.swift")

    private val iosTokens: String? = iosTokensFile.takeIf { it.isFile }?.readText()
    private val iosDrawer: String? = iosDrawerFile.takeIf { it.isFile }?.readText()

    /** `static let name: CGFloat = 300` / `static let name = 0.40` out of the Swift. */
    private fun ios(name: String): Double {
        val swift = requireNotNull(iosTokens)
        val match = Regex("""static let $name(?::\s*\w+)?\s*=\s*(-?[\d.]+)""").find(swift)
        assertTrue("iOS has no token named $name", match != null)
        return match!!.groupValues[1].toDouble()
    }

    /** `val name: Dp = 300.dp`, `const val name = 0.84f` or `val name: TextUnit = 10.sp`. */
    private fun android(name: String): Double {
        val match = Regex("""val $name(?::\s*\w+)?\s*=\s*(-?[\d.]+)[fdp]?(?:\.(?:dp|sp))?""").find(tokens)
        assertTrue("Android has no token named $name", match != null)
        return match!!.groupValues[1].toDouble()
    }

    private fun same(androidName: String, iosName: String) =
        assertEquals("$androidName must equal iOS $iosName", ios(iosName), android(androidName), 0.0001)

    /** The sheet itself: how wide, how dark behind it, how long it takes. */
    @Test
    fun theDrawerShellMatchesTheIosNumbers() {
        if (iosTokens == null) return
        same("drawerMaxWidth", "drawerMaxWidth")
        same("drawerWidthFraction", "drawerWidthFraction")
        same("edgeSwipeWidth", "edgeSwipeWidth")
        same("headerHeight", "headerHeight")
        same("sidebarWidth", "sidebarWidth")
        same("breakpoint", "breakpoint")
        assertEquals("scrim", ios("drawerScrim"), android("scrimAlpha"), 0.0001)
        // iOS states motion in seconds, Android in milliseconds.
        assertEquals(ios("normal") * 1000, android("drawerSlideMs"), 0.0001)
        assertEquals(ios("fast") * 1000, android("transitionFastMs"), 0.0001)
        assertTrue("the drawer is capped at a fraction of a narrow screen", drawer.contains("maxWidth * CoreHubTokens.Metrics.drawerWidthFraction"))
    }

    /** Header, rail and the list rows under them. */
    @Test
    fun theHeaderAndRailMatchTheIosNumbers() {
        if (iosTokens == null || iosDrawer == null) return
        same("railIcon", "railIcon")
        same("sessionRowPaddingV", "sessionRowVertical")
        same("sessionRowPaddingH", "sessionRowHorizontal")
        same("agentAvatar", "sessionAvatar")
        same("groupChevron", "groupChevron")
        // Values iOS keeps in the view rather than in the token file.
        assertTrue("app mark 26", iosDrawer.contains("AppMark(size: 26)"))
        assertEquals(26.0, android("drawerMark"), 0.0001)
        assertTrue("close glyph 20 in a 34 pt target", iosDrawer.contains("icon: .close, size: 20)") && iosDrawer.contains("frame(width: 34, height: 34)"))
        assertEquals(20.0, android("drawerCloseIcon"), 0.0001)
        assertEquals(34.0, android("drawerCloseButton"), 0.0001)
        assertTrue("rail rows are 36 tall", iosDrawer.contains(".frame(height: 36)"))
        assertEquals(36.0, android("railRowHeight"), 0.0001)
        // An unselected rail row is secondary, not primary, on both clients.
        assertTrue(iosDrawer.contains("selected ? CoreHubTokens.Palette.textPrimary : CoreHubTokens.Palette.textSecondary"))
        assertTrue(drawer.contains("if (selected) palette.textPrimary else palette.textSecondary"))
    }

    /**
     * The segmented bar, value by value. This is the control the two phones
     * differed on most, so it is pinned hardest.
     */
    @Test
    fun theSegmentedBarMatchesTheIosNumbers() {
        if (iosTokens == null || iosDrawer == null) return
        same("segmentHeight", "segmentHeight")
        assertEquals("track fill", ios("segmentTrack"), android("SEGMENT_TRACK"), 0.0001)

        // iOS: item height = segmentHeight + 12, thumb radius = segment,
        // track radius = segment + 2, 2 pt of padding, 2 pt between segments.
        assertTrue("item height", iosDrawer.contains("CoreHubTokens.Layout.segmentHeight + 12"))
        assertEquals(12.0, android("segmentIconExtra"), 0.0001)
        assertTrue("thumb radius", iosDrawer.contains("cornerRadius: CoreHubTokens.Radius.segment,"))
        assertTrue("track radius", iosDrawer.contains("cornerRadius: CoreHubTokens.Radius.segment + 2"))
        assertTrue(tokens.contains("val segmentTrack: Dp = segment + 2.dp"))
        assertTrue("track padding", iosDrawer.contains(".padding(2)"))
        assertEquals(2.0, android("segmentTrackPadding"), 0.0001)
        assertTrue("gap between segments", iosDrawer.contains("HStack(spacing: 2) {"))
        assertEquals(2.0, android("segmentGap"), 0.0001)
        assertTrue("an icon above the label", iosDrawer.contains("CoreHubIconView(icon: mode.icon, size: 16)"))
        assertEquals(16.0, android("segmentIcon"), 0.0001)
        assertTrue("icon over label", iosDrawer.contains("VStack(spacing: 2) {"))
        assertEquals(2.0, android("segmentLabelGap"), 0.0001)
        assertTrue("the label is the group-header size", iosDrawer.contains("CoreHubTokens.Typography.groupHeader, weight: active ? .semibold : .regular"))
        assertTrue(tokens.contains("val segmentLabel: TextUnit = groupHeader"))
        assertTrue(theme.contains("fontSize = CoreHubTokens.Type.segmentLabel"))
        assertTrue("selected weight", drawer.contains("if (active) CoreHubTokens.Type.groupHeaderWeight else FontWeight.Normal"))
        assertTrue("the thumb sits on bg.card", iosDrawer.contains("active ? CoreHubTokens.Palette.bgCard : Color.clear"))
        assertTrue(drawer.contains("background(palette.bgCard, RoundedCornerShape(CoreHubTokens.Radius.segment))"))
        assertTrue("the outer gutter", iosDrawer.contains(".padding(.horizontal, 12)") || iosDrawer.contains("padding(.horizontal, 12)"))
        assertEquals(12.0, android("segmentPaddingH"), 0.0001)
        assertEquals(8.0, android("segmentPaddingV"), 0.0001)
        // The selection moves; it does not jump.
        assertTrue("the thumb animates", drawer.contains("animateFloatAsState(") && drawer.contains("label = \"segment\""))
        assertTrue("at the quick duration iOS uses", iosDrawer.contains("withAnimation(CoreHubTokens.Motion.quick) { store.switchMode(mode) }"))
        assertTrue(drawer.contains("tween(CoreHubTokens.Metrics.transitionFastMs)"))
    }

    /** Both clients draw the four segment icons from the same path data. */
    @Test
    fun bothClientsUseTheSameSegmentIconPaths() {
        if (!iosIconsFile.isFile) return
        val swift = iosIconsFile.readText()
        listOf(
            "M21 15a2 2 0 0 1-2 2H8l-5 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z",
            "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2",
            "M22 21v-2a4 4 0 0 0-3-3.87",
            "M16 3.13a4 4 0 0 1 0 7.75",
            "M8 12h3a4 4 0 0 0 4-4V6",
            "M8 12h3a4 4 0 0 1 4 4v2",
            "M12 7v5l3 2",
        ).forEach { path ->
            assertTrue("Android is missing a segment icon path", icons.contains(path))
            assertTrue("iOS is missing the same path", swift.contains(path))
        }
        // The circles inside those icons, written as arguments on both sides.
        listOf(
            "circle(9f, 7f, 4f)" to "circle(cx: 9, cy: 7, r: 4)",
            "circle(5f, 12f, 3f)" to "circle(cx: 5, cy: 12, r: 3)",
            "circle(19f, 6f, 3f)" to "circle(cx: 19, cy: 6, r: 3)",
            "circle(19f, 18f, 3f)" to "circle(cx: 19, cy: 18, r: 3)",
            "circle(12f, 12f, 9f)" to "circle(cx: 12, cy: 12, r: 9)",
        ).forEach { (kotlin, ios) ->
            assertTrue("Android is missing $kotlin", icons.contains(kotlin))
            assertTrue("iOS is missing $ios", swift.contains(ios))
        }
    }

    /** Footer chips, the Sign Out pill, the status line and the two toggles. */
    @Test
    fun theFooterMatchesTheIosNumbers() {
        if (iosTokens == null || iosDrawer == null) return
        assertTrue("profile and model chips are 30 tall", iosDrawer.contains(".frame(height: 30)"))
        assertEquals(30.0, android("footerChipHeight"), 0.0001)
        assertTrue("profile avatar 18", iosDrawer.contains("ProfileAvatar(name: store.selectedProfile, avatar: store.profile?.avatar, size: 18)"))
        assertEquals(18.0, android("footerChipAvatar"), 0.0001)
        assertTrue("model glyph 14", iosDrawer.contains("CoreHubIconView(icon: .models, size: 14)"))
        assertEquals(14.0, android("footerChipIcon"), 0.0001)
        assertTrue("the chip carries a 1 pt border", iosDrawer.contains("stroke(CoreHubTokens.Palette.border, lineWidth: 1)"))
        assertTrue(drawer.contains("border(CoreHubTokens.Metrics.footerChipBorder, palette.border"))
        assertTrue("the username chip is 22 tall on hover", iosDrawer.contains(".frame(height: 22)") && iosDrawer.contains("CoreHubTokens.Palette.hover, in: Capsule()"))
        assertEquals(22.0, android("usernameChipHeight"), 0.0001)
        assertTrue(drawer.contains("background(palette.hover, RoundedCornerShape(CoreHubTokens.Radius.pill))"))
        assertTrue("connection dot 7", iosDrawer.contains(".frame(width: 7, height: 7)"))
        assertEquals(7.0, android("connectionDot"), 0.0001)
        assertTrue("the toggles are 28 × 24", iosDrawer.contains(".frame(width: 28, height: 24)"))
        assertEquals(28.0, android("footerToggleWidth"), 0.0001)
        assertEquals(24.0, android("footerToggleHeight"), 0.0001)
        assertTrue("on a tag radius", iosDrawer.contains("CoreHubTokens.Palette.hover, in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.tag)"))
        assertTrue(drawer.contains("RoundedCornerShape(CoreHubTokens.Radius.tag)") && drawer.contains("background(CoreHub.palette.hover)"))
        assertTrue("settings gear 18 in 32", iosDrawer.contains("icon: .settings, size: 18)") && iosDrawer.contains("frame(width: 32, height: 32)"))
        assertEquals(18.0, android("footerSettingsIcon"), 0.0001)
        assertEquals(32.0, android("footerSettingsButton"), 0.0001)
        assertTrue("GitHub mark 14", iosDrawer.contains("frame(width: 14, height: 14)"))
        assertEquals(14.0, android("githubIcon"), 0.0001)
        assertTrue("footer gutter 12 × 10 with 6 between rows", iosDrawer.contains("VStack(alignment: .leading, spacing: 6)"))
        assertEquals(12.0, android("footerPaddingH"), 0.0001)
        assertEquals(10.0, android("footerPaddingV"), 0.0001)
        assertEquals(6.0, android("footerRowGap"), 0.0001)
        // The language mark is short on both clients, never a whole word.
        assertTrue(iosDrawer.contains("""store.language == "ar" ? "ع" : store.language == "en" ? "EN" : "A""""))
        assertTrue(drawer.contains(""""ar" -> "ع""""))
        assertTrue(drawer.contains(""""en" -> "EN""""))
    }

    /** The room row iOS shows under the Group Chat segment. */
    @Test
    fun theRoomRowMatchesTheIosRoomRow() {
        val iosRoom = File("../../ios/HermesStudio/Features/GroupChat/RoomRowView.swift")
        if (!iosRoom.isFile) return
        val swift = iosRoom.readText()
        assertTrue("avatars then a 10 pt gutter", swift.contains("HStack(spacing: 10) {"))
        assertEquals(10.0, android("roomRowGap"), 0.0001)
        assertTrue("two lines 3 apart", swift.contains("VStack(alignment: .leading, spacing: 3) {"))
        assertEquals(3.0, android("roomRowLineGap"), 0.0001)
        assertTrue("agents then members", swift.contains("""Text("\(room.agentCount) agents")""") && swift.contains("""Text("\(room.memberCount) members")"""))
        assertTrue(drawer.contains("R.string.room_agent_count, room.agents.size"))
        assertTrue(drawer.contains("R.string.room_member_count, room.memberCount"))
        assertTrue("the same row padding as a session row", swift.contains("CoreHubTokens.Layout.sessionRowHorizontal") && swift.contains("CoreHubTokens.Layout.sessionRowVertical"))
        assertTrue(drawer.contains("CoreHubTokens.Metrics.sessionRowPaddingH") && drawer.contains("CoreHubTokens.Metrics.sessionRowPaddingV"))
    }

    /** Nothing in the drawer carries its own colour, size or radius. */
    @Test
    fun theDrawerIsBuiltFromTokens() {
        assertFalse("no hardcoded colour in the drawer", Regex("""Color\(0x[0-9A-Fa-f]{8}\)""").containsMatchIn(drawer))
        listOf(
            "CoreHubTokens.Metrics.drawerMaxWidth",
            "CoreHubTokens.Metrics.drawerWidthFraction",
            "CoreHubTokens.Metrics.edgeSwipeWidth",
            "CoreHubTokens.Metrics.drawerMark",
            "CoreHubTokens.Metrics.railRowHeight",
            "CoreHubTokens.Metrics.railIcon",
            "CoreHubTokens.Metrics.segmentItemHeight",
            "CoreHubTokens.Metrics.segmentIcon",
            "CoreHubTokens.Radius.segmentTrack",
            "CoreHubTokens.Metrics.footerChipHeight",
            "CoreHubTokens.Metrics.connectionDot",
            "CoreHubTokens.Metrics.footerToggleWidth",
        ).forEach { token -> assertTrue("the drawer should use $token", drawer.contains(token)) }
    }

    /** Every string the new drawer surfaces exists in both languages. */
    @Test
    fun everyDrawerStringExistsInBothLanguages() {
        val keys = listOf(
            "nav_chat", "nav_group_chat", "nav_workflow", "nav_history",
            "room_agent_count", "room_member_count", "groups_new", "groups_empty",
            "room_join_title", "workflows_empty", "settings_language", "nav_theme",
            "appearance_system", "appearance_light", "appearance_dark",
        )
        val english = strings("src/main/res/values/strings.xml")
        val arabic = strings("src/main/res/values-ar/strings.xml")
        keys.forEach { key ->
            assertTrue("missing English $key", english[key]?.isNotBlank() == true)
            assertTrue("missing Arabic $key", arabic[key]?.isNotBlank() == true)
        }
    }

    private fun strings(path: String): Map<String, String> {
        val document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).associate { index ->
            val element = nodes.item(index) as org.w3c.dom.Element
            element.getAttribute("name") to element.textContent
        }
    }
}
