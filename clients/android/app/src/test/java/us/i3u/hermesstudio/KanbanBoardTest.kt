package us.i3u.hermesstudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The board model against the web client's own cases
 * (`tests/client/kanban-board-utils.test.ts`), so the phone and the desktop
 * never disagree about what a drop means.
 */
class KanbanBoardTest {

    private fun task(id: String, status: String, createdAt: Long = 0L) = KanbanTask(
        id = id, title = id, body = null, assignee = null, status = status,
        priority = 1, createdAt = createdAt, result = null, skills = emptyList(),
    )

    private fun t(action: KanbanTransitionAction, requiresReason: Boolean = false, confirm: Boolean = false) =
        KanbanTransition(action, requiresReason, confirm)

    // ── transitions ─────────────────────────────────────────────

    @Test
    fun `maps drops to the Hermes commands the server bridges, mirroring its source guards`() {
        assertEquals(t(KanbanTransitionAction.Promote), resolveKanbanTransition("todo", "ready"))
        assertEquals(t(KanbanTransitionAction.Unblock), resolveKanbanTransition("blocked", "ready"))
        assertEquals(t(KanbanTransitionAction.Unblock), resolveKanbanTransition("scheduled", "todo"))
        assertEquals(t(KanbanTransitionAction.ReopenReview), resolveKanbanTransition("review", "todo"))
        assertEquals(t(KanbanTransitionAction.Schedule), resolveKanbanTransition("running", "scheduled"))
        assertEquals(t(KanbanTransitionAction.Block, requiresReason = true), resolveKanbanTransition("ready", "blocked"))
        assertEquals(t(KanbanTransitionAction.RequestReview), resolveKanbanTransition("running", "review"))
        assertEquals(t(KanbanTransitionAction.Complete), resolveKanbanTransition("blocked", "done"))
        assertEquals(t(KanbanTransitionAction.Archive, confirm = true), resolveKanbanTransition("done", "archived"))
    }

    @Test
    fun `never offers triage or running as drop targets and rejects unsupported source statuses`() {
        for (from in KANBAN_STATUSES) {
            assertNull(from, resolveKanbanTransition(from, "triage"))
            assertNull(from, resolveKanbanTransition(from, "running"))
        }
        assertNull(resolveKanbanTransition("todo", "blocked"))
        assertNull(resolveKanbanTransition("todo", "done"))
        assertNull(resolveKanbanTransition("done", "ready"))
        assertNull(resolveKanbanTransition("archived", "done"))
        assertNull(resolveKanbanTransition("triage", "todo"))
    }

    @Test
    fun `treats same-column moves as allowed reorders`() {
        assertTrue(isKanbanDropTarget("todo", "todo"))
        assertTrue(isKanbanDropTarget("todo", "ready"))
        assertFalse(isKanbanDropTarget("todo", "running"))
    }

    @Test
    fun `the nine statuses and the transition ids match the web`() {
        assertEquals(
            listOf("triage", "todo", "scheduled", "ready", "running", "blocked", "review", "done", "archived"),
            KANBAN_STATUSES,
        )
        assertEquals(
            listOf("complete", "block", "unblock", "promote", "schedule", "requestReview", "reopenReview", "archive"),
            KanbanTransitionAction.values().map { it.id },
        )
        // TRANSITION_TARGET in KanbanView.vue
        assertEquals("done", KanbanTransitionAction.Complete.target)
        assertEquals("blocked", KanbanTransitionAction.Block.target)
        assertEquals("ready", KanbanTransitionAction.Unblock.target)
        assertEquals("ready", KanbanTransitionAction.Promote.target)
        assertEquals("scheduled", KanbanTransitionAction.Schedule.target)
        assertEquals("review", KanbanTransitionAction.RequestReview.target)
        assertEquals("todo", KanbanTransitionAction.ReopenReview.target)
        assertEquals("archived", KanbanTransitionAction.Archive.target)
    }

    // ── columns ─────────────────────────────────────────────────

    @Test
    fun `maps every Hermes status to the intake strip, a workflow column, or the archive under done`() {
        assertEquals(listOf("queue", "waiting", "review", "done"), KANBAN_COLUMNS.map { it.id.id })
        assertNull(kanbanColumnForStatus("triage"))
        assertEquals(KanbanColumnId.Queue, kanbanColumnForStatus("todo"))
        assertEquals(KanbanColumnId.Queue, kanbanColumnForStatus("ready"))
        assertEquals(KanbanColumnId.Queue, kanbanColumnForStatus("running"))
        assertEquals(KanbanColumnId.Waiting, kanbanColumnForStatus("scheduled"))
        assertEquals(KanbanColumnId.Waiting, kanbanColumnForStatus("blocked"))
        assertEquals(KanbanColumnId.Review, kanbanColumnForStatus("review"))
        assertEquals(KanbanColumnId.Done, kanbanColumnForStatus("done"))
        assertEquals(KanbanColumnId.Done, kanbanColumnForStatus("archived"))
        val covered = KANBAN_COLUMNS.flatMap { it.statuses }.toSortedSet()
        assertEquals(listOf("blocked", "done", "ready", "review", "running", "scheduled", "todo"), covered.toList())
        // Only the rarely used waiting column collapses, as on the desktop.
        assertEquals(listOf(KanbanColumnId.Waiting), KANBAN_COLUMNS.filter { it.collapsible }.map { it.id })
    }

    @Test
    fun `derives drop options per column from the status transitions and treats same-column moves as reorders`() {
        fun actions(from: String, column: KanbanColumnId) =
            kanbanColumnDropOptions(from, kanbanColumnById(column)).map { "${it.transition.action.id}->${it.to}" }

        assertEquals(emptyList<String>(), actions("todo", KanbanColumnId.Queue))
        assertEquals(emptyList<String>(), actions("ready", KanbanColumnId.Queue))
        assertEquals(listOf("unblock->todo"), actions("blocked", KanbanColumnId.Queue))
        assertEquals(listOf("reopenReview->todo"), actions("review", KanbanColumnId.Queue))
        assertEquals(emptyList<String>(), actions("running", KanbanColumnId.Queue))
        assertEquals(listOf("requestReview->review"), actions("running", KanbanColumnId.Review))
        assertEquals(listOf("schedule->scheduled", "block->blocked"), actions("ready", KanbanColumnId.Waiting))
        assertEquals(listOf("schedule->scheduled"), actions("todo", KanbanColumnId.Waiting))
        assertEquals(emptyList<String>(), actions("todo", KanbanColumnId.Review))
        assertEquals(listOf("complete->done"), actions("blocked", KanbanColumnId.Done))
        assertEquals(emptyList<String>(), actions("done", KanbanColumnId.Queue))
        assertTrue(isKanbanColumnDropTarget("scheduled", kanbanColumnById(KanbanColumnId.Waiting)))
        assertFalse(isKanbanColumnDropTarget("todo", kanbanColumnById(KanbanColumnId.Review)))
        assertTrue(isKanbanColumnDropTarget("ready", kanbanColumnById(KanbanColumnId.Review)))
    }

    // ── drag highlighting and the collapse rule (KanbanColumn.vue) ──

    @Test
    fun `marks columns that cannot accept the dragged card and highlights the ones that can`() {
        val review = kanbanColumnById(KanbanColumnId.Review)
        val queue = kanbanColumnById(KanbanColumnId.Queue)
        assertTrue(isKanbanColumnDropOpen(review, "ready"))
        assertFalse(isKanbanColumnDropBlocked(review, "ready"))
        assertTrue(isKanbanColumnDropBlocked(review, "todo"))
        assertFalse(isKanbanColumnDropOpen(review, "todo"))
        // The card's own column is neither open nor blocked: a reorder.
        assertFalse(isKanbanColumnDropOpen(queue, "todo"))
        assertFalse(isKanbanColumnDropBlocked(queue, "todo"))
        // Nothing dragged: nothing highlighted.
        assertFalse(isKanbanColumnDropOpen(review, null))
        assertFalse(isKanbanColumnDropBlocked(review, null))
    }

    @Test
    fun `collapses a collapsible column while empty and expands for drops or on tap`() {
        val waiting = kanbanColumnById(KanbanColumnId.Waiting)
        assertTrue(isKanbanColumnCollapsed(waiting, taskCount = 0, draggingStatus = null, expandedByUser = false))
        assertFalse(isKanbanColumnCollapsed(waiting, taskCount = 1, draggingStatus = null, expandedByUser = false))
        assertFalse(isKanbanColumnCollapsed(waiting, taskCount = 0, draggingStatus = "ready", expandedByUser = false))
        assertTrue(isKanbanColumnCollapsed(waiting, taskCount = 0, draggingStatus = "done", expandedByUser = false))
        assertFalse(isKanbanColumnCollapsed(waiting, taskCount = 0, draggingStatus = null, expandedByUser = true))
        val queue = kanbanColumnById(KanbanColumnId.Queue)
        assertFalse(isKanbanColumnCollapsed(queue, taskCount = 0, draggingStatus = null, expandedByUser = false))
    }

    // ── status filter (KanbanView.vue) ──────────────────────────

    @Test
    fun `a status filter narrows the board to the one column that shows that status`() {
        assertEquals(KANBAN_COLUMNS, visibleKanbanColumns(null))
        assertEquals(listOf(KanbanColumnId.Waiting), visibleKanbanColumns("blocked").map { it.id })
        assertEquals(listOf(KanbanColumnId.Done), visibleKanbanColumns("archived").map { it.id })
        assertEquals(emptyList<KanbanColumnDef>(), visibleKanbanColumns("triage"))
        assertTrue(isKanbanInboxVisible(null))
        assertTrue(isKanbanInboxVisible("triage"))
        assertFalse(isKanbanInboxVisible("done"))
    }

    // ── device-local layout ─────────────────────────────────────

    @Test
    fun `keeps manual card order and shows unsaved arrivals first, newest on top`() {
        val tasks = listOf(task("a", "todo", 1), task("b", "todo", 2), task("c", "todo", 3))
        assertEquals(listOf("c", "b", "a"), orderKanbanCards(tasks, null).map { it.id })
        assertEquals(listOf("a", "c", "b"), orderKanbanCards(tasks, listOf("a", "c", "b")).map { it.id })
        assertEquals(listOf("c", "a", "b"), orderKanbanCards(tasks, listOf("a", "missing", "b")).map { it.id })
        assertEquals(
            listOf("d", "c", "e", "b", "a"),
            orderKanbanCards(tasks + task("d", "todo", 4) + task("e", "todo", 0), listOf("b", "a")).map { it.id },
        )
    }

    @Test
    fun `a same-column drop moves the card to the slot under the pointer`() {
        val ids = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), reorderKanbanCards(ids, "a", 3))
        assertEquals(listOf("b", "c", "d", "a"), reorderKanbanCards(ids, "a", 4))
        assertEquals(listOf("d", "a", "b", "c"), reorderKanbanCards(ids, "d", 0))
        assertEquals(ids, reorderKanbanCards(ids, "b", 1))
        assertEquals(ids, reorderKanbanCards(ids, "b", 2))
        assertEquals(ids, reorderKanbanCards(ids, "zz", 0))
        assertEquals("default/queue", kanbanCardOrderKey("default", KanbanColumnId.Queue))
    }

    // ── grouping and the archive fold ───────────────────────────

    @Test
    fun `keeps triage in the inbox strip and archived tasks behind the done column`() {
        val tasks = listOf(
            task("t", "triage", 9), task("q1", "todo", 1), task("q2", "running", 5), task("q3", "ready", 3),
            task("w", "blocked", 2), task("r", "review", 4), task("d", "done", 6), task("x", "archived", 7),
        )
        val board = groupKanbanTasks(tasks)
        assertEquals(listOf("t"), board.inbox.map { it.id })
        assertEquals(listOf("q2", "q3", "q1"), board.columns.getValue(KanbanColumnId.Queue).map { it.id })
        assertEquals(listOf("w"), board.columns.getValue(KanbanColumnId.Waiting).map { it.id })
        assertEquals(listOf("r"), board.columns.getValue(KanbanColumnId.Review).map { it.id })
        assertEquals(listOf("d"), board.columns.getValue(KanbanColumnId.Done).map { it.id })
        assertEquals(listOf("x"), board.archived.map { it.id })
        // The saved order belongs to a board and a column.
        val ordered = groupKanbanTasks(tasks, "default", mapOf("default/queue" to listOf("q1", "q3")))
        assertEquals(listOf("q2", "q1", "q3"), ordered.columns.getValue(KanbanColumnId.Queue).map { it.id })
    }

    @Test
    fun `keeps a moved card in its new column while Hermes is still applying the command`() {
        val tasks = listOf(task("a", "ready"), task("b", "todo"))
        val board = groupKanbanTasks(tasks, pending = mapOf("a" to "review"))
        assertEquals(listOf("b"), board.columns.getValue(KanbanColumnId.Queue).map { it.id })
        assertEquals(listOf("a"), board.columns.getValue(KanbanColumnId.Review).map { it.id })
        assertEquals(mapOf("review" to 1, "todo" to 1), kanbanStatusCounts(tasks, mapOf("a" to "review")))
    }
}
