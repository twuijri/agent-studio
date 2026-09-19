package us.i3u.hermesstudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The drawer in Arabic.
 *
 * Compose mirrors a layout on its own, but three things in this drawer are
 * positioned by hand and would stay stubbornly left-handed in Arabic: the
 * sheet's own slide, the hairline down its outer edge, and the segmented
 * bar's sliding thumb. Each has to be written in start/end terms, or in terms
 * of `LocalLayoutDirection`, and none of it shows up in a screenshot anybody
 * thinks to take in English.
 */
class DrawerRtlTest {

    private val drawer = File("src/main/java/us/i3u/hermesstudio/ui/navigation/CoreHubDrawer.kt").readText()
    private val icons = File("src/main/java/us/i3u/hermesstudio/ui/theme/CoreHubIcons.kt").readText()

    /** The sheet comes in from the start edge — the right-hand one in Arabic. */
    @Test
    fun theDrawerOpensFromTheStartEdge() {
        assertTrue(drawer.contains(".align(Alignment.CenterStart)"))
        assertFalse("CenterStart, never an absolute alignment", drawer.contains("AbsoluteAlignment"))
        // offset(x) is a layout-direction offset; absoluteOffset is not.
        assertTrue(drawer.contains(".offset(x = -width * (1f - progress))"))
        assertFalse("absoluteOffset would not mirror", drawer.contains("absoluteOffset"))
    }

    /** The thumb slides towards the segment the reader actually tapped. */
    @Test
    fun theSegmentThumbSlidesInTheReadingDirection() {
        assertTrue("the thumb is placed with a direction-aware offset", drawer.contains(".offset(x = (segmentWidth + gap) * index)"))
        assertFalse(drawer.contains("absoluteOffset(x = (segmentWidth"))
        // The label and its icon are centred, not pinned to one side.
        assertTrue(drawer.contains("horizontalAlignment = Alignment.CenterHorizontally"))
    }

    /** The outer rule follows the drawer's outer edge, which swaps in Arabic. */
    @Test
    fun theEdgeRuleFollowsTheLayoutDirection() {
        assertTrue(drawer.contains("if (layoutDirection == LayoutDirection.Rtl) 0f else size.width"))
    }

    /** A swipe "towards the start" is a different finger direction in Arabic. */
    @Test
    fun theSwipeGesturesAreDescribedByEdgeNotByPixelSign() {
        assertTrue(drawer.contains("LocalLayoutDirection.current == LayoutDirection.Rtl"))
        assertTrue(drawer.contains("val towardsStart = if (mirrored) travelled > 0f else travelled < 0f"))
        assertTrue("the edge strip sits on the start edge", drawer.contains("width(CoreHubTokens.Metrics.edgeSwipeWidth)"))
    }

    /** Every glyph in the drawer that implies a direction is auto-mirrored. */
    @Test
    fun directionalDrawerIconsAreAutoMirrored() {
        listOf("Chat", "Group", "Workflow", "ChevronRight", "Logout", "Back").forEach { name ->
            assertTrue("$name must be declared with mirror = true", declarationOf(name).contains("mirror = true"))
        }
        // History is a clock and Models is a sun: both read the same either way.
        listOf("History", "Models").forEach { name ->
            assertFalse("$name reads the same either way and must not be mirrored", declarationOf(name).contains("mirror = true"))
        }
    }

    /** The body of one `val <Name>: ImageVector by lazy { … }` declaration. */
    private fun declarationOf(name: String): String {
        val start = icons.indexOf("val $name: ImageVector by lazy {")
        assertTrue("CoreHubIcons has no icon named $name", start >= 0)
        val next = Regex("""\n    (?:/\*\*|val )""").find(icons, start + 1)?.range?.first ?: icons.length
        return icons.substring(start, next)
    }

    /** Padding in the drawer is written in start/end terms, never left/right. */
    @Test
    fun theDrawerIsWrittenInStartAndEndTerms() {
        val physical = Regex("""\b(left|right)\s*=""")
        val offenders = drawer.lines().withIndex().filter { (_, line) ->
            val code = line.trim()
            !code.startsWith("//") && !code.startsWith("*") && physical.containsMatchIn(code)
        }.map { (index, line) -> "${index + 1}: ${line.trim()}" }
        assertEquals("use start/end so the drawer mirrors", emptyList<String>(), offenders)
    }

    /** The version string stays Latin-ordered even inside an Arabic layout. */
    @Test
    fun theVersionLineStaysLeftToRight() {
        assertTrue(drawer.contains("CoreHubTextStyles.meta.copy(textDirection = TextDirection.Ltr)"))
        // A model id is Latin too.
        assertTrue(drawer.contains("labelDirection = TextDirection.Ltr"))
        // Room and workflow names follow their own content.
        assertTrue(drawer.contains("textDirection = TextDirection.Content"))
    }
}
