package us.i3u.hermesstudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import us.i3u.hermesstudio.ui.sessions.SessionGroupKind
import us.i3u.hermesstudio.ui.sessions.buildSessionGroups
import us.i3u.hermesstudio.ui.sessions.ChatAgentAvatars
import us.i3u.hermesstudio.ui.sessions.formatStamp
import us.i3u.hermesstudio.ui.sessions.workspaceChipLabel
import java.util.Calendar
import java.util.Locale

/** The session list's pure parts: grouping, the time stamp and the avatar mapping. */
class SessionGroupsTest {

    private fun session(id: String, updated: Long, category: Int? = null) =
        SessionSummary(id = id, title = id, model = null, updatedAt = updated.toString(), categoryId = category)

    private val work = SessionCategory(1, "Work")
    private val home = SessionCategory(2, "Home")

    @Test
    fun recentIsAShortcutThatLeavesSessionsInTheirRealGroups() {
        val sessions = listOf(
            session("a", 1_700_000_000, 1),
            session("b", 1_700_000_500),
            session("c", 1_700_000_200, 2),
            session("d", 1_700_000_100, 9), // unknown category → uncategorized
        )
        val groups = buildSessionGroups(sessions, listOf(work, home), pinnedIds = emptySet(), recentCount = 2, "Recent", "Pinned", "Uncategorized")

        assertEquals(listOf("recent", "category-1", "category-2", "category-none"), groups.map { it.key })
        assertEquals(SessionGroupKind.Recent, groups[0].kind)
        assertEquals(listOf("b", "c"), groups[0].sessions.map { it.id })
        assertEquals(listOf("a"), groups[1].sessions.map { it.id })
        assertEquals(listOf("c"), groups[2].sessions.map { it.id })
        assertEquals(listOf("b", "d"), groups[3].sessions.map { it.id })
        assertEquals("RECENT", groups[0].label.uppercase())
    }

    @Test
    fun pinnedSessionsLeaveRecentAndCategories() {
        val sessions = listOf(session("a", 3, 1), session("b", 2), session("c", 1))
        val groups = buildSessionGroups(sessions, listOf(work), pinnedIds = setOf("a"), recentCount = 10, "Recent", "Pinned", "Uncategorized")
        assertEquals(listOf("recent", "pinned", "category-none"), groups.map { it.key })
        assertEquals(listOf("b", "c"), groups[0].sessions.map { it.id })
        assertEquals(listOf("a"), groups[1].sessions.map { it.id })
        assertEquals(SessionGroupKind.Pinned, groups[1].kind)
    }

    @Test
    fun recentCountIsClampedToTheWebsBounds() {
        val sessions = (1..5).map { session("s$it", it.toLong()) }
        val none = buildSessionGroups(sessions, emptyList(), emptySet(), recentCount = 0, "R", "P", "U")
        assertEquals(1, none[0].sessions.size)
        val many = buildSessionGroups(sessions, emptyList(), emptySet(), recentCount = 500, "R", "P", "U")
        assertEquals(5, many[0].sessions.size)
    }

    @Test
    fun timeIsClockTodayAndShortDateOtherwise() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 19, 14, 5, 0); set(Calendar.MILLISECOND, 0) }
        val today = Calendar.getInstance().apply { timeInMillis = now.timeInMillis; set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 7) }
        val yesterday = Calendar.getInstance().apply { timeInMillis = now.timeInMillis; add(Calendar.DAY_OF_YEAR, -1) }
        assertEquals("09:07", formatStamp((today.timeInMillis / 1000).toString(), now.timeInMillis, Locale.US))
        assertEquals("Sep 18", formatStamp(yesterday.timeInMillis.toString(), now.timeInMillis, Locale.US))
        assertEquals("", formatStamp(null))
    }

    @Test
    fun runtimeMapsToTheSameAvatarAsTheWeb() {
        assertEquals("Hermes", ChatAgentAvatars.forRuntime(null).label)
        assertEquals("Ekko", ChatAgentAvatars.forRuntime("ekko_agent").label)
        assertEquals("Claude", ChatAgentAvatars.forRuntime("claude").label)
        assertEquals("Claude", ChatAgentAvatars.forRuntime("", source = "coding_agent").label)
        assertEquals("DeepSeek Harness", ChatAgentAvatars.forRuntime("DSH").label)
        assertEquals("OpenCode", ChatAgentAvatars.forRuntime("opencode").label)
        assertEquals("Ekko", ChatAgentAvatars.forSession(null).label)
    }

    @Test
    fun workspaceChipShowsTheLastPathSegment() {
        assertEquals("core-hub", workspaceChipLabel("/home/me/projects/core-hub/"))
        assertEquals("app", workspaceChipLabel("C:\\work\\app"))
        assertNull(workspaceChipLabel("   "))
        assertNull(workspaceChipLabel(null))
    }
}
