package us.i3u.hermesstudio

/**
 * The Kanban board model, ported from the web client's
 * `packages/client/src/utils/hermes/kanban-board.ts`. Pure Kotlin so
 * `KanbanBoardTest` can run it against the same cases as the web's
 * `tests/client/kanban-board-utils.test.ts`.
 *
 * Hermes keeps nine task statuses; the board shows them as an intake strip
 * (`triage`) plus four workflow columns, with `archived` folded under the
 * done column. A drop between columns is one Hermes command, resolved here;
 * a drop inside a column is a reorder that stays on this device, because
 * Hermes has no order field. Columns keep the Hermes workflow order and are
 * never rearranged.
 */

/** Every Hermes task status, in workflow order (web: `KANBAN_BOARD_STATUSES`). */
val KANBAN_STATUSES: List<String> = listOf(
    "triage", "todo", "scheduled", "ready", "running", "blocked", "review", "done", "archived",
)

const val KANBAN_INBOX_STATUS = "triage"
const val KANBAN_ARCHIVED_STATUS = "archived"

/**
 * The Hermes command a drop means. [target] is the status the card is shown
 * in while the command runs (web: `TRANSITION_TARGET`), unless the drop
 * itself names a more specific one.
 */
enum class KanbanTransitionAction(val id: String, val target: String) {
    Complete("complete", "done"),
    Block("block", "blocked"),
    Unblock("unblock", "ready"),
    Promote("promote", "ready"),
    Schedule("schedule", "scheduled"),
    RequestReview("requestReview", "review"),
    ReopenReview("reopenReview", "todo"),
    Archive("archive", "archived"),
}

data class KanbanTransition(
    val action: KanbanTransitionAction,
    /** The Hermes CLI requires a reason for this transition, so the UI must ask before running it. */
    val requiresReason: Boolean = false,
    /** Terminal transition; confirm before running it. */
    val confirm: Boolean = false,
)

private fun transition(
    action: KanbanTransitionAction,
    requiresReason: Boolean = false,
    confirm: Boolean = false,
) = KanbanTransition(action, requiresReason, confirm)

// Mirrors the source-status guards in the server controller so a drop is only
// offered when Hermes can actually apply it. `triage` and `running` are owned by
// the intake flow and the worker, so nothing can be dropped there.
// Keyed by target status, then source status.
private val DROP_RULES: Map<String, Map<String, KanbanTransition>> = mapOf(
    "todo" to mapOf(
        "blocked" to transition(KanbanTransitionAction.Unblock),
        "scheduled" to transition(KanbanTransitionAction.Unblock),
        "review" to transition(KanbanTransitionAction.ReopenReview),
    ),
    "scheduled" to mapOf(
        "todo" to transition(KanbanTransitionAction.Schedule),
        "ready" to transition(KanbanTransitionAction.Schedule),
        "running" to transition(KanbanTransitionAction.Schedule),
        "blocked" to transition(KanbanTransitionAction.Schedule),
    ),
    "ready" to mapOf(
        "todo" to transition(KanbanTransitionAction.Promote),
        "blocked" to transition(KanbanTransitionAction.Unblock),
        "scheduled" to transition(KanbanTransitionAction.Unblock),
        "review" to transition(KanbanTransitionAction.ReopenReview),
    ),
    "blocked" to mapOf(
        "running" to transition(KanbanTransitionAction.Block, requiresReason = true),
        "ready" to transition(KanbanTransitionAction.Block, requiresReason = true),
    ),
    "review" to mapOf(
        "running" to transition(KanbanTransitionAction.RequestReview),
        "ready" to transition(KanbanTransitionAction.RequestReview),
    ),
    "done" to mapOf(
        "running" to transition(KanbanTransitionAction.Complete),
        "ready" to transition(KanbanTransitionAction.Complete),
        "blocked" to transition(KanbanTransitionAction.Complete),
    ),
    "archived" to mapOf(
        "done" to transition(KanbanTransitionAction.Archive, confirm = true),
    ),
)

fun resolveKanbanTransition(from: String, to: String): KanbanTransition? {
    if (from == to) return null
    return DROP_RULES[to]?.get(from)
}

fun isKanbanDropTarget(from: String, to: String): Boolean =
    from == to || resolveKanbanTransition(from, to) != null

// ─── Board columns ───────────────────────────────────────────────

enum class KanbanColumnId(val id: String) {
    Queue("queue"),
    Waiting("waiting"),
    Review("review"),
    Done("done"),
}

data class KanbanColumnDef(
    val id: KanbanColumnId,
    val statuses: List<String>,
    /** Collapses to a narrow strip while empty; expands for drops and on tap. */
    val collapsible: Boolean = false,
)

/**
 * The four workflow columns in Hermes order. The card itself shows which
 * stage it is in: the queue goes todo -> ready (badge) -> running (ring) in
 * place, waiting distinguishes scheduled from blocked, and so on. The
 * waiting column is the rarely used one the desktop collapses.
 */
val KANBAN_COLUMNS: List<KanbanColumnDef> = listOf(
    KanbanColumnDef(KanbanColumnId.Queue, listOf("todo", "ready", "running")),
    KanbanColumnDef(KanbanColumnId.Waiting, listOf("scheduled", "blocked"), collapsible = true),
    KanbanColumnDef(KanbanColumnId.Review, listOf("review")),
    KanbanColumnDef(KanbanColumnId.Done, listOf("done")),
)

fun kanbanColumnById(id: KanbanColumnId): KanbanColumnDef = KANBAN_COLUMNS.first { it.id == id }

/** Column that shows a status as a card, or null for the intake strip. The archive folds under done. */
fun kanbanColumnForStatus(status: String): KanbanColumnId? {
    if (status == KANBAN_ARCHIVED_STATUS) return KanbanColumnId.Done
    return KANBAN_COLUMNS.firstOrNull { status in it.statuses }?.id
}

data class KanbanColumnDrop(
    /** Target status inside the column that Hermes will move the task to. */
    val to: String,
    val transition: KanbanTransition,
)

/**
 * Every Hermes transition a drop into [column] could mean for a card coming
 * from [from]. Same-column moves are reorders and never yield a transition.
 * One result runs directly; several (schedule vs block) need the user to pick.
 */
fun kanbanColumnDropOptions(from: String, column: KanbanColumnDef): List<KanbanColumnDrop> {
    if (from in column.statuses) return emptyList()
    val seen = mutableSetOf<KanbanTransitionAction>()
    val options = mutableListOf<KanbanColumnDrop>()
    for (to in column.statuses) {
        val transition = resolveKanbanTransition(from, to) ?: continue
        if (!seen.add(transition.action)) continue
        options.add(KanbanColumnDrop(to, transition))
    }
    return options
}

fun isKanbanColumnDropTarget(from: String, column: KanbanColumnDef): Boolean =
    from in column.statuses || kanbanColumnDropOptions(from, column).isNotEmpty()

// ─── Drag highlighting and the collapse rule ─────────────────────

/** The dragged card may land here and would change status: highlight the column. */
fun isKanbanColumnDropOpen(column: KanbanColumnDef, draggingStatus: String?): Boolean =
    draggingStatus != null && draggingStatus !in column.statuses && isKanbanColumnDropTarget(draggingStatus, column)

/** The dragged card cannot land here: dim the column. */
fun isKanbanColumnDropBlocked(column: KanbanColumnDef, draggingStatus: String?): Boolean =
    draggingStatus != null && !isKanbanColumnDropTarget(draggingStatus, column)

/**
 * A collapsible column only takes a full slot when it has cards, when a card
 * that may land here is being dragged, or after the user opened it by hand.
 */
fun isKanbanColumnCollapsed(
    column: KanbanColumnDef,
    taskCount: Int,
    draggingStatus: String?,
    expandedByUser: Boolean,
): Boolean = column.collapsible && taskCount == 0 && !isKanbanColumnDropOpen(column, draggingStatus) && !expandedByUser

// ─── Status filter ───────────────────────────────────────────────

/**
 * Columns always follow the Hermes workflow order; a status filter narrows
 * the board to the one column that shows that status (none for the inbox).
 */
fun visibleKanbanColumns(filterStatus: String?): List<KanbanColumnDef> {
    if (filterStatus == null) return KANBAN_COLUMNS
    val column = kanbanColumnForStatus(filterStatus) ?: return emptyList()
    return KANBAN_COLUMNS.filter { it.id == column }
}

fun isKanbanInboxVisible(filterStatus: String?): Boolean =
    filterStatus == null || filterStatus == KANBAN_INBOX_STATUS

// ─── Device-local layout (manual card order inside a column) ─────

/** The key a column's saved order is stored under; one order per board. */
fun kanbanCardOrderKey(board: String, column: KanbanColumnId): String = "$board/${column.id}"

private fun defaultCardOrder(tasks: List<KanbanTask>): List<KanbanTask> = tasks.sortedByDescending { it.createdAt }

/**
 * Tasks not covered by the saved order (new arrivals) stay on top in default
 * order so they remain visible; saved tasks keep the viewer's manual order.
 */
fun orderKanbanCards(tasks: List<KanbanTask>, savedIds: List<String>?): List<KanbanTask> {
    val sorted = defaultCardOrder(tasks)
    if (savedIds.isNullOrEmpty()) return sorted
    val byId = sorted.associateBy { it.id }.toMutableMap()
    val saved = mutableListOf<KanbanTask>()
    for (id in savedIds) {
        val task = byId.remove(id) ?: continue
        saved.add(task)
    }
    return sorted.filter { it.id in byId } + saved
}

// ─── Grouping tasks onto the board ───────────────────────────────

data class KanbanBoardTasks(
    /** Triage: the intake strip. */
    val inbox: List<KanbanTask>,
    val columns: Map<KanbanColumnId, List<KanbanTask>>,
    /** Folded under the done column behind a toggle. */
    val archived: List<KanbanTask>,
)

/**
 * Applies the optimistic statuses of [pending] (task id -> status shown while
 * Hermes applies a command), then places every task on the board.
 */
fun groupKanbanTasks(
    tasks: List<KanbanTask>,
    board: String = "",
    cardOrder: Map<String, List<String>> = emptyMap(),
    pending: Map<String, String> = emptyMap(),
): KanbanBoardTasks {
    val displayed = if (pending.isEmpty()) tasks else tasks.map { task ->
        val expected = pending[task.id]
        if (expected != null && expected != task.status) task.copy(status = expected) else task
    }
    return KanbanBoardTasks(
        inbox = defaultCardOrder(displayed.filter { it.status == KANBAN_INBOX_STATUS }),
        columns = KANBAN_COLUMNS.associate { column ->
            column.id to orderKanbanCards(
                displayed.filter { it.status in column.statuses },
                cardOrder[kanbanCardOrderKey(board, column.id)],
            )
        },
        archived = defaultCardOrder(displayed.filter { it.status == KANBAN_ARCHIVED_STATUS }),
    )
}

/** Task count per status after the optimistic statuses are applied; the stats header. */
fun kanbanStatusCounts(tasks: List<KanbanTask>, pending: Map<String, String> = emptyMap()): Map<String, Int> =
    tasks.groupingBy { pending[it.id] ?: it.status }.eachCount()

/**
 * Where a card dropped inside its own column lands. [slotIndex] is the slot
 * under the pointer in the current order (the list length means "after the
 * last card"); the card's own slot is skipped so a drop onto itself is a
 * no-op.
 */
fun reorderKanbanCards(ids: List<String>, taskId: String, slotIndex: Int): List<String> {
    val from = ids.indexOf(taskId)
    if (from < 0) return ids
    val without = ids.toMutableList().apply { removeAt(from) }
    val target = (if (slotIndex > from) slotIndex - 1 else slotIndex).coerceIn(0, without.size)
    without.add(target, taskId)
    return without
}
