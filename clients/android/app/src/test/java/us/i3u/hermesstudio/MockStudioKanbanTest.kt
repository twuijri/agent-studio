package us.i3u.hermesstudio

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * The board against `tools/mock-studio.py`, which carries the server
 * controller's source-status guards: every drop the board model offers is a
 * command the mock accepts, and a drop it does not offer is one the mock
 * refuses. Skipped when the machine has no python3.
 */
class MockStudioKanbanTest {

    private val mock = File("../tools/mock-studio.py")
    private var process: Process? = null
    private lateinit var api: HermesApi

    @Before
    fun startMock() {
        assumeTrue("python3 is required to run the mock server", python3() != null)
        assumeTrue("tools/mock-studio.py must be next to the app module", mock.isFile)
        val started = ProcessBuilder(python3()!!, mock.absolutePath, "0")
            .directory(mock.parentFile)
            .redirectErrorStream(true)
            .start()
        process = started
        val banner = started.inputStream.bufferedReader().readLine().orEmpty()
        val port = Regex(":(\\d+)").find(banner)?.groupValues?.get(1)
        assumeTrue("the mock did not report a port: $banner", port != null)
        api = HermesApi("http://127.0.0.1:$port", "mock-token")
    }

    @After
    fun stopMock() {
        process?.destroy()
        process?.waitFor()
    }

    private fun status(id: String): String = api.kanbanTasks("default").first { it.id == id }.status

    @Test
    fun `the board lists every stage, the archive included, and folds it the way the desktop does`() {
        val board = groupKanbanTasks(api.kanbanTasks("default"))

        assertEquals(listOf("k5"), board.inbox.map { it.id })
        assertEquals(listOf("k1", "k3", "k8"), board.columns.getValue(KanbanColumnId.Queue).map { it.id })
        assertEquals(listOf("k6", "k7"), board.columns.getValue(KanbanColumnId.Waiting).map { it.id })
        assertEquals(listOf("k2"), board.columns.getValue(KanbanColumnId.Review).map { it.id })
        assertEquals(listOf("k4"), board.columns.getValue(KanbanColumnId.Done).map { it.id })
        assertEquals(listOf("k9"), board.archived.map { it.id })
    }

    @Test
    fun `every drop the board offers is a command Hermes accepts`() {
        fun drop(id: String, column: KanbanColumnId, pick: Int = 0, note: String? = null): String {
            val from = status(id)
            val options = kanbanColumnDropOptions(from, kanbanColumnById(column))
            assertTrue("$from -> ${column.id} must be offered", options.isNotEmpty())
            val drop = options[pick]
            api.applyKanbanTransition("default", id, drop.transition.action, note)
            return status(id)
        }

        // todo -> ready is inside the queue column, so it is the card's quick action, not a drop.
        api.applyKanbanTransition("default", "k3", KanbanTransitionAction.Promote)
        assertEquals("ready", status("k3"))
        assertEquals("scheduled", drop("k8", KanbanColumnId.Waiting, pick = 0))          // ready -> schedule
        assertEquals("ready", drop("k8", KanbanColumnId.Queue))                            // scheduled -> unblock
        assertEquals("blocked", drop("k8", KanbanColumnId.Waiting, pick = 1, note = "waiting on design")) // ready -> block
        assertEquals("done", drop("k7", KanbanColumnId.Done))                              // blocked -> complete
        assertEquals("review", drop("k1", KanbanColumnId.Review))                          // running -> request review
        assertEquals("todo", drop("k2", KanbanColumnId.Queue))                             // review -> reopen review

        api.applyKanbanTransition("default", "k4", KanbanTransitionAction.Archive)         // done -> archive (confirmed in the UI)
        assertEquals("archived", status("k4"))
        assertEquals(listOf("k4", "k9"), groupKanbanTasks(api.kanbanTasks("default")).archived.map { it.id }.sorted())
    }

    @Test
    fun `a drop the board does not offer is one Hermes refuses`() {
        // todo -> review: the model offers nothing, and the controller's guard answers 400.
        assertTrue(kanbanColumnDropOptions("todo", kanbanColumnById(KanbanColumnId.Review)).isEmpty())
        val refused = assertThrows(HermesException::class.java) {
            api.applyKanbanTransition("default", "k3", KanbanTransitionAction.RequestReview)
        }
        assertEquals(400, refused.statusCode)
        assertEquals("todo", status("k3"))

        // Block needs a reason: the model says so, and the server rejects an empty one.
        assertTrue(resolveKanbanTransition("ready", "blocked")!!.requiresReason)
        val blank = assertThrows(HermesException::class.java) {
            api.applyKanbanTransition("default", "k8", KanbanTransitionAction.Block, "   ")
        }
        assertEquals(400, blank.statusCode)
        assertEquals("ready", status("k8"))
    }

    private fun python3(): String? = sequenceOf("/usr/bin/python3", "python3")
        .firstOrNull { candidate ->
            runCatching {
                ProcessBuilder(candidate, "--version").redirectErrorStream(true).start().waitFor() == 0
            }.getOrDefault(false)
        }
}
