package us.i3u.hermesstudio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import us.i3u.hermesstudio.ui.theme.CoreHub
import us.i3u.hermesstudio.ui.theme.CoreHubTokens
import kotlin.math.roundToInt

/*
 * The Kanban board, mirroring the desktop's KanbanView.vue and
 * KanbanColumn.vue on the board model in KanbanBoard.kt:
 *
 *  - a stats header, one chip per Hermes status, that narrows the board to
 *    the one column showing that status;
 *  - the inbox strip (triage) above the board, opened on tap; its cards are
 *    specified from the task screen, never dragged;
 *  - four columns in Hermes order — queue · waiting · review · done — paged
 *    sideways at phone width, the archive folded under done behind a toggle,
 *    and the rarely used waiting column collapsed to a narrow strip while
 *    empty (it expands when a card that may land there hovers, and on tap);
 *  - long-press a card to drag it; the board scrolls itself sideways while
 *    the finger sits near an edge, columns that cannot take the card are
 *    dimmed and the ones that can are outlined; a drop in another column is
 *    the Hermes command the model resolves (a sheet asks when it could mean
 *    two things or needs a reason), a drop in the same column reorders on
 *    this phone only.
 */

// ─── Colours (KanbanColumn.vue / KanbanTaskCard.vue) ─────────────

@Composable
internal fun kanbanStatusColor(status: String): Color {
    val palette = CoreHub.palette
    return when (status) {
        "triage" -> Color(0xFF8B8F95)
        "todo" -> Color(0xFF6F7782)
        "scheduled" -> Color(0xFFB8860B)
        "ready" -> Color(0xFFA66D23)
        "running" -> palette.success
        "blocked" -> palette.error
        "review" -> Color(0xFF7B5FB3)
        "done" -> palette.success
        "archived" -> Color(0xFF777B81)
        else -> Color(0xFF7F858D)
    }
}

@Composable
internal fun kanbanColumnColor(id: KanbanColumnId): Color = when (id) {
    KanbanColumnId.Queue -> Color(0xFFA66D23)
    KanbanColumnId.Waiting -> Color(0xFFB8860B)
    KanbanColumnId.Review -> Color(0xFF7B5FB3)
    KanbanColumnId.Done -> CoreHub.palette.success
}

/** `kanban.board.columns.*` on the web. */
@Composable
internal fun kanbanColumnLabel(id: KanbanColumnId): String = stringResource(
    when (id) {
        KanbanColumnId.Queue -> R.string.kanban_column_queue
        KanbanColumnId.Waiting -> R.string.kanban_column_waiting
        KanbanColumnId.Review -> R.string.kanban_column_review
        KanbanColumnId.Done -> R.string.kanban_column_done
    },
)

private val COLLAPSED_COLUMN_WIDTH = 44.dp
private val COLUMN_SHAPE_RADIUS = CoreHubTokens.Radius.card
private val CARD_SHAPE_RADIUS = CoreHubTokens.Radius.medium

/** A card's button: promote from the queue, archive from done, specify from the inbox. */
internal enum class KanbanQuickAction { Promote, Archive, Specify }

// ─── Screen ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KanbanScreen(state: UiState, viewModel: AppViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    var create by remember { mutableStateOf(false) }
    var boardMenu by remember { mutableStateOf(false) }
    var inboxOpen by rememberSaveable { mutableStateOf(false) }
    val kanban = state.kanban
    val filter = kanban.filterStatus
    val searched = remember(kanban.tasks, query) {
        val clean = query.trim()
        if (clean.isBlank()) kanban.tasks else kanban.tasks.filter {
            it.title.contains(clean, true) || it.body.orEmpty().contains(clean, true) ||
                it.assignee.orEmpty().contains(clean, true)
        }
    }
    val board = remember(searched, kanban.board, kanban.cardOrder, kanban.pending) {
        groupKanbanTasks(searched, kanban.board, kanban.cardOrder, kanban.pending)
    }
    val counts = remember(kanban.tasks, kanban.pending) { kanbanStatusCounts(kanban.tasks, kanban.pending) }
    val columns = visibleKanbanColumns(filter)
    val inboxVisible = isKanbanInboxVisible(filter)
    // First fetch of a board: columns say "loading" instead of "no tasks".
    val initialLoading = kanban.loading && kanban.tasks.isEmpty()

    LaunchedEffect(filter) {
        if (filter == KANBAN_INBOX_STATUS) inboxOpen = true
    }

    if (create) {
        CreateTaskDialog(
            assignees = kanban.assignees,
            busy = kanban.actionId == "new",
            onDismiss = { create = false },
            onCreate = { title, body, assignee, priority, skills, triage ->
                viewModel.createKanbanTask(title, body, assignee, priority, skills, triage)
                create = false
            },
        )
    }
    KanbanDropSheets(kanban, viewModel)

    val onQuickAction: (KanbanTask, KanbanQuickAction) -> Unit = { task, action ->
        when (action) {
            KanbanQuickAction.Promote -> viewModel.runKanbanQuickAction(task, KanbanTransitionAction.Promote)
            KanbanQuickAction.Archive -> viewModel.runKanbanQuickAction(task, KanbanTransitionAction.Archive)
            // Specification needs the task screen's operations; open the task there.
            KanbanQuickAction.Specify -> viewModel.openKanbanTask(task)
        }
    }

    Scaffold(
        topBar = {
            StudioTopBar(
                title = stringResource(R.string.nav_kanban),
                subtitle = stringResource(R.string.kanban_mobile_note),
                onBack = { viewModel.back() },
                actions = {
                    IconButton(onClick = { viewModel.refreshKanban() }, enabled = !kanban.loading) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { create = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.kanban_add))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (kanban.loading && !initialLoading) {
                CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.CenterHorizontally))
            }
            state.error?.let { ErrorNote(it) { viewModel.dismissError() } }
            state.notice?.let { NoticeNote(it) { viewModel.dismissNotice() } }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    OutlinedButton(onClick = { boardMenu = true }) {
                        Icon(Icons.AutoMirrored.Filled.Assignment, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            kanban.boards.firstOrNull { it.slug == kanban.board }?.name
                                ?: stringResource(R.string.kanban_default_board),
                            maxLines = 1,
                        )
                    }
                    DropdownMenu(expanded = boardMenu, onDismissRequest = { boardMenu = false }) {
                        kanban.boards.forEach { board ->
                            DropdownMenuItem(
                                text = { Text("${board.name}  ·  ${board.total}") },
                                onClick = {
                                    boardMenu = false
                                    viewModel.selectKanbanBoard(board.slug)
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.action_search)) },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            KanbanStatsBar(
                counts = counts,
                total = kanban.tasks.size,
                active = filter,
                // The active chip clears the filter again; the web's "Total" chip does the same.
                onSelect = { status -> viewModel.setKanbanFilter(if (status == filter) null else status) },
            )
            Text(
                stringResource(R.string.kanban_drag_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
            if (inboxVisible) {
                KanbanInboxStrip(
                    tasks = board.inbox,
                    open = inboxOpen,
                    loading = initialLoading,
                    onToggle = { inboxOpen = !inboxOpen },
                    onOpen = viewModel::openKanbanTask,
                    onQuickAction = onQuickAction,
                )
            }
            if (!kanban.loading && kanban.tasks.isEmpty() && query.isBlank()) {
                EmptyToolState(
                    icon = Icons.Filled.FilterList,
                    title = stringResource(R.string.kanban_empty),
                    note = stringResource(R.string.kanban_empty_note),
                    modifier = Modifier.weight(1f),
                )
            } else {
                KanbanBoardColumns(
                    columns = columns,
                    board = board,
                    pendingIds = kanban.pendingIds,
                    loading = initialLoading,
                    onOpen = viewModel::openKanbanTask,
                    onQuickAction = onQuickAction,
                    onMove = viewModel::moveKanbanTask,
                    onDrop = viewModel::dropKanbanTask,
                    onReorder = viewModel::reorderKanbanCards,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ─── Stats header (KanbanView.vue .stats-bar) ────────────────────

@Composable
private fun KanbanStatsBar(
    counts: Map<String, Int>,
    total: Int,
    active: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        KanbanStatChip(
            count = total,
            label = stringResource(R.string.kanban_stats_total),
            color = CoreHub.palette.textMuted,
            active = active == null,
            onClick = { onSelect(null) },
        )
        KANBAN_STATUSES.forEach { status ->
            KanbanStatChip(
                count = counts[status] ?: 0,
                label = statusLabel(status),
                color = kanbanStatusColor(status),
                active = active == status,
                onClick = { onSelect(status) },
            )
        }
    }
}

@Composable
private fun KanbanStatChip(count: Int, label: String, color: Color, active: Boolean, onClick: () -> Unit) {
    val palette = CoreHub.palette
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(CoreHubTokens.Radius.small))
            .background(if (active) palette.bgSecondary else Color.Transparent)
            .border(1.dp, if (active) palette.border else Color.Transparent, RoundedCornerShape(CoreHubTokens.Radius.small))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(5.dp).background(color, RoundedCornerShape(CoreHubTokens.Radius.pill)))
        Text(count.toString(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
        Text(label, fontSize = 12.sp, color = palette.textMuted, maxLines = 1)
    }
}

// ─── Inbox strip (KanbanView.vue .kanban-inbox) ──────────────────

@Composable
private fun KanbanInboxStrip(
    tasks: List<KanbanTask>,
    open: Boolean,
    loading: Boolean,
    onToggle: () -> Unit,
    onOpen: (KanbanTask) -> Unit,
    onQuickAction: (KanbanTask, KanbanQuickAction) -> Unit,
) {
    val palette = CoreHub.palette
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(COLUMN_SHAPE_RADIUS),
        color = palette.bgSecondary.copy(alpha = .66f),
        border = BorderStroke(1.dp, palette.borderLight),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 11.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KanbanCountPill(tasks.size)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.kanban_column_inbox),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = palette.textMuted,
                    modifier = Modifier.size(18.dp).rotate(if (open) 180f else 0f),
                )
            }
            AnimatedVisibility(visible = open) {
                Column(Modifier.padding(start = 9.dp, end = 9.dp, bottom = 9.dp)) {
                    Text(
                        stringResource(R.string.kanban_inbox_hint),
                        fontSize = 12.sp,
                        color = palette.textMuted,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    if (tasks.isEmpty()) {
                        Text(
                            stringResource(if (loading) R.string.kanban_loading_tasks else R.string.kanban_no_tasks),
                            fontSize = 12.sp,
                            color = palette.textMuted,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(tasks, key = { it.id }) { task ->
                                KanbanTaskCard(task = task, onClick = { onOpen(task) }, onQuickAction = { onQuickAction(task, it) })
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── The columns and the drag ────────────────────────────────────

/** The card in flight, hoisted to the board so every column can react to it. */
private class KanbanDrag {
    var task by mutableStateOf<KanbanTask?>(null)
    var fromColumn: KanbanColumnId? = null

    /** The finger, in root coordinates. */
    var pointer by mutableStateOf(Offset.Zero)

    /** Where inside the card the finger grabbed it. */
    var grab = Offset.Zero
    var cardSize by mutableStateOf(IntSize.Zero)

    /** The column under the finger. */
    var hover by mutableStateOf<KanbanColumnId?>(null)

    fun clear() {
        task = null
        fromColumn = null
        hover = null
    }
}

@Composable
private fun KanbanBoardColumns(
    columns: List<KanbanColumnDef>,
    board: KanbanBoardTasks,
    pendingIds: Set<String>,
    loading: Boolean,
    onOpen: (KanbanTask) -> Unit,
    onQuickAction: (KanbanTask, KanbanQuickAction) -> Unit,
    onMove: (KanbanTask, String) -> Unit,
    onDrop: (KanbanTask, KanbanColumnId) -> Unit,
    onReorder: (KanbanColumnId, List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    // One column fills a phone (minus a peek of the next); wider screens see several.
    val columnWidth = if (columns.size == 1) (screenWidth - 24.dp).coerceAtMost(520.dp) else (screenWidth - 40.dp).coerceAtMost(300.dp)
    val scroll = rememberScrollState()
    val drag = remember { KanbanDrag() }
    var boardBounds by remember { mutableStateOf(Rect.Zero) }
    val columnBounds = remember { mutableStateMapOf<KanbanColumnId, Rect>() }
    val listTops = remember { mutableStateMapOf<KanbanColumnId, Float>() }
    val listStates = KANBAN_COLUMNS.associate { column -> column.id to rememberLazyListState() }

    fun columnAt(pointer: Offset): KanbanColumnId? {
        if (pointer.y < boardBounds.top || pointer.y > boardBounds.bottom) return null
        return columnBounds.entries.firstOrNull { pointer.x >= it.value.left && pointer.x <= it.value.right }?.key
    }

    // While a card is held near a horizontal edge the board scrolls itself
    // toward the neighbouring column. `scrollBy` counts from the start edge,
    // so the physical direction flips in a right-to-left layout.
    LaunchedEffect(drag.task != null) {
        if (drag.task == null) return@LaunchedEffect
        val edge = with(density) { 40.dp.toPx() }
        val step = with(density) { 14.dp.toPx() }
        while (drag.task != null) {
            withFrameNanos { }
            val x = drag.pointer.x - boardBounds.left
            val physical = when {
                x < edge -> -1f
                x > boardBounds.width - edge -> 1f
                else -> 0f
            }
            if (physical != 0f) {
                scroll.scrollBy(physical * step * if (isRtl) -1f else 1f)
                drag.hover = columnAt(drag.pointer)
            }
        }
    }

    fun finishDrag() {
        val task = drag.task ?: return
        val from = drag.fromColumn
        val target = drag.hover
        val pointer = drag.pointer
        drag.clear()
        if (target == null) return
        if (target != from) {
            onDrop(task, target)
            return
        }
        // Same column: the slot under the finger, on this device only.
        val tasks = board.columns[target] ?: return
        val listState = listStates[target] ?: return
        val top = listTops[target] ?: return
        val y = pointer.y - top
        val slots = listState.layoutInfo.visibleItemsInfo.filter { it.index < tasks.size }
        val slot = slots.firstOrNull { y < it.offset + it.size / 2f }?.index
            ?: slots.lastOrNull()?.let { it.index + 1 }
            ?: tasks.size
        val ids = tasks.map { it.id }
        val reordered = reorderKanbanCards(ids, task.id, slot)
        if (reordered != ids) onReorder(target, reordered)
    }

    Box(modifier.fillMaxWidth().onGloballyPositioned { boardBounds = it.boundsInRoot() }) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(scroll)
                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            columns.forEach { column ->
                KanbanBoardColumn(
                    column = column,
                    tasks = board.columns[column.id].orEmpty(),
                    archived = if (column.id == KanbanColumnId.Done) board.archived else null,
                    width = columnWidth,
                    draggingStatus = drag.task?.status,
                    draggingId = drag.task?.id,
                    hovered = drag.hover == column.id,
                    pendingIds = pendingIds,
                    loading = loading,
                    listState = listStates.getValue(column.id),
                    onBounds = { columnBounds[column.id] = it },
                    onListTop = { listTops[column.id] = it },
                    onGone = { columnBounds.remove(column.id); listTops.remove(column.id) },
                    onOpen = onOpen,
                    onQuickAction = onQuickAction,
                    onMove = onMove,
                    onDragStart = { task, pointer, grab, size ->
                        drag.task = task
                        drag.fromColumn = column.id
                        drag.pointer = pointer
                        drag.grab = grab
                        drag.cardSize = size
                        drag.hover = column.id
                    },
                    onDrag = { pointer ->
                        drag.pointer = pointer
                        drag.hover = columnAt(pointer)
                    },
                    onDragEnd = ::finishDrag,
                    onDragCancel = { drag.clear() },
                )
            }
        }
        // The lifted card follows the finger above the columns. Positions are
        // physical (root) coordinates, so it is placed with `place`, which
        // does not mirror in a right-to-left layout.
        drag.task?.let { task ->
            val x = (drag.pointer.x - drag.grab.x - boardBounds.left).roundToInt()
            val y = (drag.pointer.y - drag.grab.y - boardBounds.top).roundToInt()
            val cardWidth = with(density) { drag.cardSize.width.toDp() }
            val label = stringResource(R.string.kanban_dragging, task.title)
            Box(
                Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(Constraints(maxWidth = with(density) { cardWidth.roundToPx() }))
                        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(x, y) }
                    }
                    .semantics { contentDescription = label },
            ) {
                KanbanTaskCard(task = task, lifted = true, onClick = null, onQuickAction = null)
            }
        }
    }
}

@Composable
private fun KanbanBoardColumn(
    column: KanbanColumnDef,
    tasks: List<KanbanTask>,
    /** Archived tasks shown under the done column behind a toggle. */
    archived: List<KanbanTask>?,
    width: Dp,
    draggingStatus: String?,
    draggingId: String?,
    hovered: Boolean,
    pendingIds: Set<String>,
    loading: Boolean,
    listState: LazyListState,
    onBounds: (Rect) -> Unit,
    onListTop: (Float) -> Unit,
    onGone: () -> Unit,
    onOpen: (KanbanTask) -> Unit,
    onQuickAction: (KanbanTask, KanbanQuickAction) -> Unit,
    onMove: (KanbanTask, String) -> Unit,
    onDragStart: (KanbanTask, Offset, Offset, IntSize) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val palette = CoreHub.palette
    var expandedByUser by rememberSaveable(column.id) { mutableStateOf(false) }
    var showArchived by rememberSaveable(column.id) { mutableStateOf(false) }
    val collapsed = isKanbanColumnCollapsed(column, tasks.size, draggingStatus, expandedByUser)
    val dropOpen = isKanbanColumnDropOpen(column, draggingStatus)
    val dropBlocked = isKanbanColumnDropBlocked(column, draggingStatus)
    val color = kanbanColumnColor(column.id)
    val label = kanbanColumnLabel(column.id)
    val animatedWidth by animateDpAsState(if (collapsed) COLLAPSED_COLUMN_WIDTH else width, label = "column-width")
    val outline = when {
        dropOpen && hovered -> color
        dropOpen -> color.copy(alpha = .55f)
        else -> palette.borderLight
    }

    DisposableEffect(column.id) { onDispose(onGone) }

    Surface(
        modifier = Modifier
            .width(animatedWidth)
            .fillMaxHeight()
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
            .alpha(if (dropBlocked) .55f else 1f)
            .semantics { contentDescription = label },
        shape = RoundedCornerShape(COLUMN_SHAPE_RADIUS),
        color = palette.bgSecondary.copy(alpha = .66f),
        border = BorderStroke(if (dropOpen && hovered) 2.dp else 1.dp, outline),
    ) {
        if (collapsed) {
            // Empty collapsible column: a narrow strip with a vertical title.
            Column(
                Modifier.fillMaxSize().clickable { expandedByUser = true }.padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(6.dp).background(color, RoundedCornerShape(CoreHubTokens.Radius.pill)))
                Spacer(Modifier.height(8.dp))
                KanbanCountPill(tasks.size)
                Spacer(Modifier.height(10.dp))
                Text(
                    label,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.textMuted,
                    maxLines = 1,
                    modifier = Modifier.vertical().rotate(90f),
                )
            }
            return@Surface
        }
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (column.collapsible) Modifier.clickable { expandedByUser = !expandedByUser } else Modifier)
                    .padding(horizontal = 11.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).background(color, RoundedCornerShape(CoreHubTokens.Radius.pill)))
                Spacer(Modifier.width(7.dp))
                Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                KanbanCountPill(tasks.size)
            }
            HorizontalDivider(color = palette.borderLight)
            Box(Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().onGloballyPositioned { onListTop(it.boundsInRoot().top) },
                    contentPadding = PaddingValues(start = 9.dp, end = 9.dp, top = 9.dp, bottom = 72.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(tasks, key = { it.id }) { task ->
                        KanbanDraggableCard(
                            task = task,
                            pending = task.id in pendingIds,
                            lifted = task.id == draggingId,
                            onOpen = onOpen,
                            onQuickAction = onQuickAction,
                            onMove = onMove,
                            onDragStart = onDragStart,
                            onDrag = onDrag,
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragCancel,
                        )
                    }
                    if (archived != null) {
                        item(key = "archive-toggle") {
                            Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                HorizontalDivider(color = palette.borderLight)
                                TextButton(onClick = { showArchived = !showArchived }, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        stringResource(
                                            if (showArchived) R.string.kanban_hide_archived else R.string.kanban_show_archived,
                                            archived.size,
                                        ),
                                        fontSize = 11.5.sp,
                                        color = palette.textMuted,
                                    )
                                }
                            }
                        }
                        if (showArchived) {
                            if (archived.isEmpty()) {
                                item(key = "archive-empty") {
                                    Text(
                                        stringResource(R.string.kanban_no_tasks),
                                        fontSize = 12.sp,
                                        color = palette.textMuted,
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    )
                                }
                            }
                            items(archived, key = { "archived-${it.id}" }) { task ->
                                KanbanTaskCard(task = task, muted = true, onClick = { onOpen(task) }, onQuickAction = null)
                            }
                        }
                    }
                }
                if (tasks.isEmpty() && !showArchived) {
                    KanbanColumnNote(
                        text = stringResource(
                            when {
                                loading -> R.string.kanban_loading_tasks
                                dropBlocked -> R.string.kanban_drop_not_allowed
                                else -> R.string.kanban_no_tasks
                            },
                        ),
                        loading = loading,
                        modifier = Modifier.fillMaxSize().padding(9.dp),
                    )
                } else if (dropBlocked) {
                    KanbanColumnNote(
                        text = stringResource(R.string.kanban_drop_not_allowed),
                        loading = false,
                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(9.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun KanbanColumnNote(text: String, loading: Boolean, modifier: Modifier) {
    val palette = CoreHub.palette
    Box(
        modifier
            .then(if (loading) Modifier else Modifier.dashedBorder(palette.borderLight))
            .background(if (loading) Color.Transparent else palette.bgCard.copy(alpha = .85f), RoundedCornerShape(CoreHubTokens.Radius.small))
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (loading) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = palette.textSecondary)
            Text(text, fontSize = 12.sp, color = palette.textMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun KanbanCountPill(count: Int) {
    val palette = CoreHub.palette
    Text(
        count.toString(),
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Medium,
        color = palette.textMuted,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier
            .widthIn(min = 22.dp)
            .border(1.dp, palette.borderLight, RoundedCornerShape(CoreHubTokens.Radius.pill))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Long-press lifts the card; the board takes it from there. Pending cards stay put. */
@Composable
private fun KanbanDraggableCard(
    task: KanbanTask,
    pending: Boolean,
    lifted: Boolean,
    onOpen: (KanbanTask) -> Unit,
    onQuickAction: (KanbanTask, KanbanQuickAction) -> Unit,
    onMove: (KanbanTask, String) -> Unit,
    onDragStart: (KanbanTask, Offset, Offset, IntSize) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val haptic = LocalHapticFeedback.current
    Box(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates = it }
            // The original stays in its slot as a ghost while the lifted copy follows the finger.
            .alpha(if (lifted) .35f else 1f)
            .then(
                if (pending) Modifier else Modifier.pointerInput(task.id, task.status) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val coords = coordinates ?: return@detectDragGesturesAfterLongPress
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDragStart(task, coords.localToRoot(offset), offset, coords.size)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            coordinates?.let { onDrag(it.localToRoot(change.position)) }
                        },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragCancel,
                    )
                },
            ),
    ) {
        KanbanTaskCard(
            task = task,
            pending = pending,
            onClick = { onOpen(task) },
            onQuickAction = { onQuickAction(task, it) },
            onMove = { onMove(task, it) },
        )
    }
}

// ─── The card (KanbanTaskCard.vue) ───────────────────────────────

@Composable
internal fun KanbanTaskCard(
    task: KanbanTask,
    pending: Boolean = false,
    /** Muted and without a quick action (the archive list). */
    muted: Boolean = false,
    /** The copy that follows the finger. */
    lifted: Boolean = false,
    onClick: (() -> Unit)?,
    onQuickAction: ((KanbanQuickAction) -> Unit)?,
    /**
     * The card's menu: the same transitions a drop resolves, for a finger or a
     * screen reader that cannot long-press and drag. Null hides it.
     */
    onMove: ((String) -> Unit)? = null,
) {
    val palette = CoreHub.palette
    val statusColor = kanbanStatusColor(task.status)
    var moveMenu by remember { mutableStateOf(false) }
    // One entry per Hermes command, as the drop options: unblock lands on
    // todo and ready alike, so it is listed once.
    val moves = remember(task.status) {
        val seen = mutableSetOf<KanbanTransitionAction>()
        KANBAN_STATUSES.mapNotNull { status ->
            val transition = resolveKanbanTransition(task.status, status) ?: return@mapNotNull null
            if (!seen.add(transition.action)) return@mapNotNull null
            status to transition
        }
    }
    val ringed = task.status in setOf("blocked", "review", "scheduled", "running")
    val ring = if (ringed) 2.dp else 1.dp
    val shape = RoundedCornerShape(CARD_SHAPE_RADIUS)
    val quickAction: KanbanQuickAction? = if (muted || pending || onQuickAction == null) null else when (task.status) {
        "todo" -> KanbanQuickAction.Promote
        "done" -> KanbanQuickAction.Archive
        "triage" -> KanbanQuickAction.Specify
        else -> null
    }
    val badge = when (task.status) {
        "ready", "running", "scheduled", "blocked", "review", "done", "archived" -> statusLabel(task.status)
        else -> null
    }
    val badgeIcon = when (task.status) {
        "scheduled" -> Icons.Filled.Schedule
        "blocked" -> Icons.Filled.Pause
        "review" -> Icons.Filled.Visibility
        "done", "archived" -> Icons.Filled.Check
        else -> null
    }
    val priority = when {
        task.priority >= 3 -> R.string.kanban_priority_high
        task.priority == 2 -> R.string.kanban_priority_medium
        else -> null
    }
    val priorityColor = if (task.priority >= 3) palette.error else palette.warning
    val now = remember { System.currentTimeMillis() / 1000 }

    Box(
        Modifier
            .fillMaxWidth()
            .shadow(if (lifted) 12.dp else 0.dp, shape)
            .clip(shape)
            .background(palette.border)
            .kanbanRing(task.status, statusColor, ring, CARD_SHAPE_RADIUS)
            .padding(ring),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CARD_SHAPE_RADIUS - ring))
                .background(palette.bgCard)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(start = 10.dp, end = 10.dp, top = 9.dp, bottom = 9.dp)
                .alpha(if (muted || task.status == "done") .72f else if (pending) .85f else 1f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(6.dp).background(statusColor, RoundedCornerShape(CoreHubTokens.Radius.pill)))
                Text(
                    task.id,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = palette.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (badge != null) {
                    Row(
                        Modifier
                            .background(statusColor.copy(alpha = CoreHubTokens.Alpha.SELECTED), RoundedCornerShape(CoreHubTokens.Radius.tag))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        if (badgeIcon != null) Icon(badgeIcon, null, Modifier.size(11.dp), tint = statusColor)
                        Text(badge, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = statusColor, maxLines = 1)
                    }
                }
                if (pending) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.5.dp, color = palette.textMuted)
                        Text(stringResource(R.string.kanban_syncing), fontSize = 10.sp, color = palette.textMuted, maxLines = 1)
                    }
                }
                if (priority != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.size(5.dp).background(priorityColor, RoundedCornerShape(CoreHubTokens.Radius.pill)))
                        Text(stringResource(priority), fontSize = 10.sp, color = priorityColor, maxLines = 1)
                    }
                }
                if (onMove != null && moves.isNotEmpty() && !pending && !muted) {
                    Box {
                        IconButton(onClick = { moveMenu = true }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.MoreVert, stringResource(R.string.kanban_move), Modifier.size(16.dp), tint = palette.textMuted)
                        }
                        DropdownMenu(expanded = moveMenu, onDismissRequest = { moveMenu = false }) {
                            moves.forEach { (status, transition) ->
                                DropdownMenuItem(
                                    text = { Text("${kanbanActionLabel(transition.action)} · ${statusLabel(status)}") },
                                    onClick = { moveMenu = false; onMove(status) },
                                )
                            }
                        }
                    }
                }
            }
            // Human text decides its own direction (docs/CONTENT-DIRECTION.md).
            Text(
                task.title,
                style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Content),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                task.assignee?.let {
                    Icon(Icons.Filled.Person, null, Modifier.size(14.dp), tint = palette.textSecondary)
                    Text(it, fontSize = 11.sp, color = palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                Spacer(Modifier.weight(1f))
                if (quickAction != null && onQuickAction != null) {
                    Text(
                        stringResource(
                            when (quickAction) {
                                KanbanQuickAction.Promote -> R.string.kanban_action_promote
                                KanbanQuickAction.Archive -> R.string.kanban_action_archive
                                KanbanQuickAction.Specify -> R.string.kanban_action_specify
                            },
                        ),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.textPrimary,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(CoreHubTokens.Radius.small))
                            .border(1.dp, palette.border, RoundedCornerShape(CoreHubTokens.Radius.small))
                            .clickable { onQuickAction(quickAction) }
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
                Text(timeAgo(now - task.createdAt), fontSize = 10.5.sp, color = palette.textMuted, maxLines = 1)
            }
        }
    }
}

/** `kanban.action.*` on the web. */
@Composable
internal fun kanbanActionLabel(action: KanbanTransitionAction): String = stringResource(
    when (action) {
        KanbanTransitionAction.Complete -> R.string.kanban_complete
        KanbanTransitionAction.Block -> R.string.kanban_action_block
        KanbanTransitionAction.Unblock -> R.string.kanban_action_unblock
        KanbanTransitionAction.Promote -> R.string.kanban_action_promote
        KanbanTransitionAction.Schedule -> R.string.kanban_action_schedule
        KanbanTransitionAction.RequestReview -> R.string.kanban_action_request_review
        KanbanTransitionAction.ReopenReview -> R.string.kanban_action_reopen_review
        KanbanTransitionAction.Archive -> R.string.kanban_action_archive
    },
)

@Composable
private fun timeAgo(seconds: Long): String = when {
    seconds < 60 -> stringResource(R.string.kanban_time_just_now)
    seconds < 3600 -> stringResource(R.string.kanban_time_minutes, (seconds / 60).toInt())
    seconds < 86400 -> stringResource(R.string.kanban_time_hours, (seconds / 3600).toInt())
    else -> stringResource(R.string.kanban_time_days, (seconds / 86400).toInt())
}

/**
 * The card's ring: a solid coloured stroke for blocked and review, a dashed
 * one for scheduled (waiting on time), and for running a green sweep that
 * travels around the card while the worker owns it.
 */
@Composable
private fun Modifier.kanbanRing(status: String, color: Color, ring: Dp, radius: Dp): Modifier {
    val angle = if (status == "running") {
        rememberInfiniteTransition(label = "running-ring").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
            label = "running-ring-angle",
        ).value
    } else {
        0f
    }
    val base = CoreHub.palette.border
    return drawBehind {
        val stroke = ring.toPx()
        val corner = CornerRadius(radius.toPx())
        val inset = stroke / 2f
        val strokeSize = Size(size.width - stroke, size.height - stroke)
        when (status) {
            "blocked", "review" -> drawRoundRect(color, Offset(inset, inset), strokeSize, corner, Stroke(stroke))
            "scheduled" -> drawRoundRect(
                color, Offset(inset, inset), strokeSize, corner,
                Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
            )
            "running" -> {
                drawRoundRect(color.copy(alpha = .35f).compositeOver(base), Offset(inset, inset), strokeSize, corner, Stroke(stroke))
                // Clip to the ring, then spin a sweep gradient behind it: the
                // gradient turns while the rounded stroke stays where it is.
                val outer = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), corner)) }
                val inner = Path().apply {
                    addRoundRect(
                        RoundRect(
                            Rect(Offset(stroke, stroke), Size(size.width - 2 * stroke, size.height - 2 * stroke)),
                            CornerRadius((corner.x - stroke).coerceAtLeast(0f)),
                        ),
                    )
                }
                val reach = maxOf(size.width, size.height)
                val sweep = Brush.sweepGradient(
                    0f to Color.Transparent, .55f to Color.Transparent, .85f to color, 1f to Color.Transparent,
                    center = center,
                )
                clipPath(outer) {
                    clipPath(inner, ClipOp.Difference) {
                        rotate(angle) {
                            drawRect(sweep, topLeft = center - Offset(reach, reach), size = Size(reach * 2, reach * 2))
                        }
                    }
                }
            }
        }
    }
}

/** A dashed rounded border, the web's `border: 1px dashed`. */
private fun Modifier.dashedBorder(color: Color): Modifier = drawBehind {
    val stroke = 1.dp.toPx()
    drawRoundRect(
        color,
        Offset(stroke / 2f, stroke / 2f),
        Size(size.width - stroke, size.height - stroke),
        CornerRadius(CoreHubTokens.Radius.small.toPx()),
        Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
    )
}

/** Swaps the measured width and height so a `rotate(90f)` text takes a vertical slot (the web's `writing-mode: vertical-rl`). */
private fun Modifier.vertical(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(
        Constraints(minWidth = 0, maxWidth = constraints.maxHeight, minHeight = 0, maxHeight = constraints.maxWidth),
    )
    layout(placeable.height, placeable.width) {
        placeable.place(
            x = -(placeable.width / 2 - placeable.height / 2),
            y = -(placeable.height / 2 - placeable.width / 2),
        )
    }
}

// ─── Drop prompts (KanbanView.vue modals) ────────────────────────

/**
 * The two questions a drop can raise, and the archive confirmation: a sheet
 * when the waiting column could mean schedule or block, a sheet for the
 * block reason, a dialog before archiving. Shown from the board and from the
 * task screen, which share the same transition flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KanbanDropSheets(kanban: KanbanUiState, viewModel: AppViewModel) {
    val direction = LocalLayoutDirection.current
    val palette = CoreHub.palette

    kanban.pendingChoice?.let { choice ->
        ModalBottomSheet(onDismissRequest = viewModel::cancelKanbanDrop) {
            // A sheet is its own window; carry the app's direction into it.
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Column(
                    Modifier.fillMaxWidth().padding(start = CoreHubTokens.Metrics.sheetPadding, end = CoreHubTokens.Metrics.sheetPadding, bottom = CoreHubTokens.Metrics.sheetPadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(stringResource(R.string.kanban_waiting_kind_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        choice.task.title,
                        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                        color = palette.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    choice.options.forEach { drop ->
                        OutlinedButton(
                            onClick = { viewModel.chooseKanbanDrop(drop) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = CoreHubTokens.Metrics.sheetButtonHeight),
                        ) {
                            Text(kanbanDropChoiceLabel(drop), modifier = Modifier.fillMaxWidth())
                        }
                    }
                    TextButton(onClick = viewModel::cancelKanbanDrop, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
        }
    }

    kanban.pendingReason?.let { pending ->
        var reason by rememberSaveable(pending.task.id) { mutableStateOf("") }
        ModalBottomSheet(onDismissRequest = viewModel::cancelKanbanDrop) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Column(
                    Modifier.fillMaxWidth().padding(start = CoreHubTokens.Metrics.sheetPadding, end = CoreHubTokens.Metrics.sheetPadding, bottom = CoreHubTokens.Metrics.sheetPadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(stringResource(R.string.kanban_action_block), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        pending.task.title,
                        style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                        color = palette.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    CompositionLocalProvider(LocalLayoutDirection provides contentLayoutDirection(reason)) {
                        OutlinedTextField(
                            value = reason,
                            onValueChange = { reason = it },
                            placeholder = { Text(stringResource(R.string.kanban_block_reason)) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        TextButton(onClick = viewModel::cancelKanbanDrop) { Text(stringResource(R.string.action_cancel)) }
                        Button(
                            onClick = { viewModel.confirmKanbanDropReason(reason) },
                            enabled = reason.isNotBlank(),
                        ) { Text(stringResource(R.string.action_ok)) }
                    }
                }
            }
        }
    }

    kanban.pendingConfirm?.let {
        AlertDialog(
            onDismissRequest = viewModel::cancelKanbanDrop,
            title = { Text(stringResource(R.string.kanban_action_archive)) },
            text = { Text(stringResource(R.string.kanban_archive_confirm)) },
            confirmButton = {
                Button(onClick = viewModel::confirmKanbanArchive) { Text(stringResource(R.string.kanban_action_archive)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelKanbanDrop) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun kanbanDropChoiceLabel(drop: KanbanColumnDrop): String = when (drop.transition.action) {
    KanbanTransitionAction.Schedule -> stringResource(R.string.kanban_waiting_schedule)
    KanbanTransitionAction.Block -> stringResource(R.string.kanban_waiting_block)
    else -> statusLabel(drop.to)
}

// ─── Task screen ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KanbanTaskScreen(state: UiState, viewModel: AppViewModel) {
    val detail = state.kanban.openTask
    val task = detail?.task
    var comment by rememberSaveable(task?.id) { mutableStateOf("") }
    var assigneeMenu by remember { mutableStateOf(false) }
    val pending = task != null && task.id in state.kanban.pending

    KanbanDropSheets(state.kanban, viewModel)

    Scaffold(
        topBar = {
            StudioTopBar(
                title = task?.title ?: stringResource(R.string.kanban_task),
                subtitle = task?.let { statusLabel(it.status) },
                onBack = { viewModel.back() },
                actions = {
                    IconButton(onClick = { task?.let(viewModel::openKanbanTask) }, enabled = !state.kanban.loading) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 30.dp),
        ) {
            if (state.kanban.loading) item { LoadingRow() }
            state.error?.let { item { ErrorNote(it) { viewModel.dismissError() } } }
            state.notice?.let { item { NoticeNote(it) { viewModel.dismissNotice() } } }
            if (task != null) {
                item {
                    // The stages Hermes can move this task to from where it is: the same
                    // transition model as a board drop, one chip per target status.
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        KANBAN_STATUSES.forEach { status ->
                            AssistChip(
                                onClick = { viewModel.moveKanbanTask(task, status) },
                                enabled = !pending && resolveKanbanTransition(task.status, status) != null,
                                label = { Text(statusLabel(status)) },
                                leadingIcon = {
                                    Box(Modifier.size(8.dp).background(kanbanStatusColor(status), RoundedCornerShape(4.dp)))
                                },
                            )
                        }
                    }
                }
                item {
                    ToolSectionCard(title = stringResource(R.string.kanban_details)) {
                        DetailLine(stringResource(R.string.kanban_priority), (task.priority + 1).toString())
                        Box {
                            DetailLine(
                                stringResource(R.string.kanban_assignee),
                                task.assignee ?: stringResource(R.string.kanban_unassigned),
                                onClick = { assigneeMenu = true },
                            )
                            DropdownMenu(expanded = assigneeMenu, onDismissRequest = { assigneeMenu = false }) {
                                state.kanban.assignees.forEach { assignee ->
                                    DropdownMenuItem(
                                        text = { Text(assignee) },
                                        onClick = {
                                            assigneeMenu = false
                                            viewModel.assignKanbanTask(task.id, assignee)
                                        },
                                    )
                                }
                            }
                        }
                        task.skills.takeIf { it.isNotEmpty() }?.let {
                            DetailLine(stringResource(R.string.kanban_skills), it.joinToString(" · "))
                        }
                        task.body?.let { Text(it, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyLarge) }
                        task.result?.let {
                            HorizontalDivider(Modifier.padding(vertical = 12.dp))
                            Text(stringResource(R.string.kanban_result), fontWeight = FontWeight.SemiBold)
                            Text(it, Modifier.padding(top = 6.dp))
                        }
                    }
                }
                item {
                    ToolSectionCard(stringResource(R.string.kanban_operations)) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (task.status == KANBAN_INBOX_STATUS) {
                                AssistChip({ viewModel.kanbanCommand(task, "specify", state.account.orEmpty()) }, { Text(stringResource(R.string.kanban_action_specify)) })
                            }
                            AssistChip({ viewModel.kanbanCommand(task, "dispatch") }, { Text(stringResource(R.string.kanban_dispatch)) })
                            AssistChip({ viewModel.kanbanCommand(task, "complete", task.result.orEmpty()) }, { Text(stringResource(R.string.kanban_complete)) })
                            AssistChip({ viewModel.kanbanCommand(task, "block", "Blocked from mobile") }, { Text(stringResource(R.string.block)) })
                            AssistChip({ viewModel.kanbanCommand(task, "unblock") }, { Text(stringResource(R.string.unblock)) })
                            task.assignee?.let { profile -> AssistChip({ viewModel.kanbanCommand(task, "reassign", profile) }, { Text(stringResource(R.string.kanban_reassign)) }) }
                        }
                        if (state.kanbanStats.isNotBlank()) Text(state.kanbanStats, style = MaterialTheme.typography.bodySmall)
                        state.kanbanDiagnostics.forEach { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        state.kanbanAttachments.forEach { Text("📎 $it", style = MaterialTheme.typography.bodySmall) }
                        if (state.kanbanLog.isNotBlank()) Text(state.kanbanLog, maxLines = 12, style = MaterialTheme.typography.bodySmall)
                    }
                }
                detail.latestSummary?.let { summary ->
                    item { ToolSectionCard(stringResource(R.string.kanban_summary)) { Text(summary) } }
                }
                item {
                    ToolSectionCard(stringResource(R.string.kanban_comments, detail.comments.size)) {
                        detail.comments.forEach { item ->
                            Text(item.author, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                            Text(item.body, Modifier.padding(bottom = 12.dp))
                        }
                        OutlinedTextField(
                            value = comment,
                            onValueChange = { comment = it },
                            label = { Text(stringResource(R.string.kanban_comment_hint)) },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = { viewModel.addKanbanComment(task.id, comment); comment = "" },
                            enabled = comment.isNotBlank() && state.kanban.actionId == null,
                            modifier = Modifier.align(Alignment.End).padding(top = 8.dp),
                        ) { Text(stringResource(R.string.kanban_comment_add)) }
                    }
                }
                if (detail.runs.isNotEmpty()) {
                    item {
                        ToolSectionCard(stringResource(R.string.kanban_runs, detail.runs.size)) {
                            detail.runs.forEach { run ->
                                DetailLine(run.status, run.summary ?: run.error ?: run.id)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateTaskDialog(
    assignees: List<String>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, Int, List<String>, Boolean) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var assignee by rememberSaveable { mutableStateOf("") }
    var skills by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableIntStateOf(1) }
    var triage by rememberSaveable { mutableStateOf(false) }
    var assigneeMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.kanban_new_task)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(
                    title, { title = it }, label = { Text(stringResource(R.string.kanban_task_title)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    body, { body = it }, label = { Text(stringResource(R.string.kanban_description)) },
                    minLines = 3, modifier = Modifier.fillMaxWidth(),
                )
                Box {
                    OutlinedTextField(
                        assignee,
                        { assignee = it },
                        label = { Text(stringResource(R.string.kanban_assignee)) },
                        singleLine = true,
                        trailingIcon = { IconButton(onClick = { assigneeMenu = true }) { Icon(Icons.Filled.Person, null) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownMenu(expanded = assigneeMenu, onDismissRequest = { assigneeMenu = false }) {
                        assignees.forEach {
                            DropdownMenuItem(text = { Text(it) }, onClick = { assignee = it; assigneeMenu = false })
                        }
                    }
                }
                Text(stringResource(R.string.kanban_priority_value, priority + 1), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(5) { value ->
                        AssistChip(onClick = { priority = value }, label = { Text((value + 1).toString()) })
                    }
                }
                OutlinedTextField(
                    skills,
                    { skills = it },
                    label = { Text(stringResource(R.string.kanban_skills_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = triage, onCheckedChange = { triage = it })
                    Text(stringResource(R.string.kanban_send_triage))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(
                        title,
                        body,
                        assignee,
                        priority,
                        skills.split(',').map(String::trim).filter(String::isNotBlank),
                        triage,
                    )
                },
                enabled = title.isNotBlank() && !busy,
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
internal fun ToolSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
internal fun DetailLine(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun EmptyToolState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    note: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, Modifier.size(46.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        Text(
            note,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 5.dp).widthIn(max = 340.dp),
        )
    }
}

/** `kanban.columns.*` on the web: the status names, also the stage badge on a card. */
@Composable
internal fun statusLabel(status: String): String = stringResource(
    when (status) {
        "triage" -> R.string.kanban_status_triage
        "todo" -> R.string.kanban_status_todo
        "scheduled" -> R.string.kanban_status_scheduled
        "ready" -> R.string.kanban_status_ready
        "running" -> R.string.kanban_status_running
        "blocked" -> R.string.kanban_status_blocked
        "review" -> R.string.kanban_status_review
        "done" -> R.string.kanban_status_done
        "archived" -> R.string.kanban_status_archived
        else -> R.string.kanban_status_triage
    },
)
