package us.i3u.hermesstudio.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.unit.sp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import us.i3u.hermesstudio.AppViewModel
import us.i3u.hermesstudio.BuildConfig
import us.i3u.hermesstudio.ConfirmDialog
import us.i3u.hermesstudio.LanguageSheet
import us.i3u.hermesstudio.ProfileAvatar
import us.i3u.hermesstudio.R
import us.i3u.hermesstudio.RoomInfo
import us.i3u.hermesstudio.Screen
import us.i3u.hermesstudio.StudioWorkflow
import us.i3u.hermesstudio.Tab
import us.i3u.hermesstudio.TextPromptDialog
import us.i3u.hermesstudio.UiState
import us.i3u.hermesstudio.isSuperAdmin
import us.i3u.hermesstudio.navigation.NavDestination
import us.i3u.hermesstudio.ui.chat.STUDIO_REPOSITORY_URL
import us.i3u.hermesstudio.ui.groups.AgentAvatarStack
import us.i3u.hermesstudio.ui.groups.NewRoomDialog
import us.i3u.hermesstudio.ui.sessions.SessionListPane
import us.i3u.hermesstudio.ui.sessions.formatStamp
import us.i3u.hermesstudio.ui.theme.CoreHub
import us.i3u.hermesstudio.ui.theme.CoreHubIcons
import us.i3u.hermesstudio.ui.theme.CoreHubTextStyles
import us.i3u.hermesstudio.ui.theme.CoreHubTokens

/**
 * The off-canvas drawer that mirrors the web's mobile sidebar: 250 ms slide
 * from the start edge, 40 % scrim, closed by the scrim, the system back gesture
 * or any navigation. Hand-rolled instead of ModalNavigationDrawer so the
 * timing and scrim follow the spec exactly.
 */
@Composable
fun CoreHubDrawerHost(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    drawer: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val progress by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = tween(CoreHubTokens.Metrics.drawerSlideMs),
        label = "drawer",
    )
    // The soft keyboard covered the drawer's lower half (profile, Sign Out,
    // settings) whenever the composer still had focus. Dismiss it on the
    // open transition itself — not on a timer — and drop the composer's focus
    // so Android does not bring the keyboard straight back.
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(open) {
        if (open) {
            keyboard?.hide()
            focusManager.clearFocus(force = true)
        }
    }
    BackHandler(enabled = open) { onOpenChange(false) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Never wider than the spec's 300 dp, and never more than 84 % of a
        // narrow screen, so the page behind stays visible (iOS RootShell).
        val width = minOf(CoreHubTokens.Metrics.drawerMaxWidth, maxWidth * CoreHubTokens.Metrics.drawerWidthFraction)
        content()
        if (progress > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .alpha(progress)
                    .background(Color.Black.copy(alpha = CoreHubTokens.Metrics.scrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onOpenChange(false) },
                    ),
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(width)
                    // offset(x) follows the layout direction, so the sheet slides in
                    // from the right edge in Arabic without any special casing.
                    .offset(x = -width * (1f - progress))
                    // Drag it back out of the way; `toStart` is already the
                    // mirrored direction, so Arabic needs no special case.
                    .drawerDrag { toStart -> if (toStart) onOpenChange(false) },
            ) { drawer() }
        } else {
            // The edge strip that swipes the drawer open.
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(CoreHubTokens.Metrics.edgeSwipeWidth)
                    .drawerDrag { toStart -> if (!toStart) onOpenChange(true) },
            )
        }
    }
}

/**
 * A horizontal drag, reported as "towards the start edge" rather than as a
 * pixel sign, so the caller never has to know which way Arabic runs.
 */
private fun Modifier.drawerDrag(threshold: Dp = 24.dp, onDrag: (toStart: Boolean) -> Unit): Modifier = composed {
    val mirrored = LocalLayoutDirection.current == LayoutDirection.Rtl
    val minimum = with(LocalDensity.current) { threshold.toPx() }
    var travelled by remember { mutableFloatStateOf(0f) }
    pointerInput(mirrored, minimum) {
        detectHorizontalDragGestures(
            onDragStart = { travelled = 0f },
            onDragEnd = {
                val towardsStart = if (mirrored) travelled > 0f else travelled < 0f
                if (kotlin.math.abs(travelled) > minimum) onDrag(towardsStart)
            },
        ) { _, delta -> travelled += delta }
    }
}

/**
 * Drawer body, top to bottom: primary rail → 4-segment conversation switch →
 * session list → footer (profile, model, sign out, status, version, language).
 */
@Composable
fun CoreHubDrawerContent(state: UiState, viewModel: AppViewModel, onClose: () -> Unit) {
    val palette = CoreHub.palette
    val context = LocalContext.current
    var confirmSignOut by remember { mutableStateOf(false) }
    if (confirmSignOut) {
        ConfirmDialog(
            title = stringResource(R.string.confirm_sign_out_title),
            body = stringResource(R.string.confirm_sign_out_body),
            action = stringResource(R.string.action_sign_out),
            onConfirm = { confirmSignOut = false; onClose(); viewModel.signOut() },
            onDismiss = { confirmSignOut = false },
        )
    }
    fun go(action: () -> Unit) { onClose(); action() }

    // A square sheet with a hairline along its end edge, as on iOS — not a
    // floating rounded card.
    Surface(
        color = palette.bgSidebar,
        contentColor = palette.textPrimary,
        modifier = Modifier
            .fillMaxHeight()
            .drawWithContent {
                drawContent()
                val edge = if (layoutDirection == LayoutDirection.Rtl) 0f else size.width
                drawLine(palette.border, Offset(edge, 0f), Offset(edge, size.height), strokeWidth = 1.dp.toPx())
            },
    ) {
        // Bottom inset = max(navigation bar, IME). The host dismisses the
        // keyboard when the drawer opens; if one is still up (a hardware or
        // third-party IME that ignores hide()), the footer stays reachable
        // instead of sitting underneath it.
        Column(
            Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CoreHubTokens.Metrics.headerHeight)
                    .padding(horizontal = CoreHubTokens.Metrics.drawerHeaderPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_core_hub_mark),
                    contentDescription = null,
                    tint = palette.textPrimary,
                    modifier = Modifier.size(CoreHubTokens.Metrics.drawerMark),
                )
                Spacer(Modifier.width(CoreHubTokens.Metrics.drawerHeaderGap))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(CoreHubTokens.Metrics.drawerCloseButton)) {
                    Icon(
                        CoreHubIcons.Close,
                        contentDescription = stringResource(R.string.action_dismiss),
                        tint = palette.textSecondary,
                        modifier = Modifier.size(CoreHubTokens.Metrics.drawerCloseIcon),
                    )
                }
            }

            // Rail, switch and the list of the selected segment share one
            // scroll so a short screen (landscape) still reaches the list; the
            // footer stays put.
            val drawerHeader: LazyListScope.() -> Unit = {
                item(key = "rail") {
                    Column(
                        modifier = Modifier.padding(
                            start = CoreHubTokens.Metrics.railPaddingH,
                            end = CoreHubTokens.Metrics.railPaddingH,
                            top = CoreHubTokens.Metrics.railPaddingTop,
                        ),
                        verticalArrangement = Arrangement.spacedBy(CoreHubTokens.Metrics.railRowGap),
                    ) {
                        // The registry owns the rail: its order, its labels, and
                        // that Agent Manager is super-admin only (router/index.ts:196-201).
                        NavDestination.rail
                            .filter { it != NavDestination.agentManager || state.isSuperAdmin }
                            .forEach { destination ->
                                RailItem(destination, railIcon(destination), selected = railSelected(state, destination)) {
                                    // Search is a sheet over the drawer, so the drawer stays.
                                    if (destination == NavDestination.search) viewModel.openRailDestination(destination)
                                    else go { viewModel.openRailDestination(destination) }
                                }
                            }
                    }
                }
                item(key = "switch") {
                    ConversationSwitch(
                        selected = state.tab,
                        modifier = Modifier.padding(
                            horizontal = CoreHubTokens.Metrics.segmentPaddingH,
                            vertical = CoreHubTokens.Metrics.segmentPaddingV,
                        ),
                    ) { tab ->
                        // iOS `AppStore.switchMode`: the segment picks what the
                        // list underneath shows and the drawer stays open;
                        // only History, a full page of its own, closes it.
                        if (tab == Tab.History) go { viewModel.showTab(tab) } else viewModel.showTab(tab)
                    }
                }
                // The switch is ruled off from the list below it, as on iOS.
                item(key = "switch-rule") { HorizontalDivider(color = palette.borderLight) }
            }
            val listModifier = Modifier.weight(1f).fillMaxWidth()
            when (state.tab) {
                Tab.Group -> DrawerRoomList(state, viewModel, listModifier, drawerHeader, onOpen = { room -> go { viewModel.openRoom(room) } })
                Tab.Workflow -> DrawerWorkflowList(state, viewModel, listModifier, drawerHeader, onOpen = { workflow -> go { viewModel.openWorkflow(workflow) } })
                Tab.Chat, Tab.History -> SessionListPane(
                    state = state,
                    viewModel = viewModel,
                    modifier = listModifier,
                    header = drawerHeader,
                    // Opening a session leaves the selected segment alone: a
                    // session picked under History returns to History.
                    onOpen = { session -> go { viewModel.openSession(session) } },
                )
            }

            HorizontalDivider(color = palette.borderLight)
            DrawerFooter(state, viewModel, onSignOut = { confirmSignOut = true }, onNavigate = ::go)
        }
    }
}

private fun railIcon(destination: NavDestination): ImageVector = when (destination) {
    NavDestination.newChat -> CoreHubIcons.NewChat
    NavDestination.search -> CoreHubIcons.Search
    NavDestination.deviceConnections -> CoreHubIcons.DeviceConnections
    NavDestination.agentManager -> CoreHubIcons.AgentManager
    else -> CoreHubIcons.Models
}

private fun railSelected(state: UiState, destination: NavDestination): Boolean = when (destination) {
    NavDestination.deviceConnections -> state.screen == Screen.Connections
    NavDestination.agentManager -> state.screen == Screen.AgentManager
    NavDestination.models -> state.screen == Screen.Models
    else -> false
}

/** Nav item: 36 dp tall, 14 sp, radius 6, selected = accent @ 12 % with text.primary at weight 500. */
@Composable
private fun RailItem(destination: NavDestination, icon: ImageVector, selected: Boolean = false, onClick: () -> Unit) {
    val palette = CoreHub.palette
    val label = stringResource(destination.labelKey)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CoreHubTokens.Metrics.railRowHeight)
            .clip(RoundedCornerShape(CoreHubTokens.Radius.small))
            .background(if (selected) palette.selected else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = CoreHubTokens.Metrics.railRowPaddingH),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoreHubTokens.Metrics.railIconGap),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) palette.textPrimary else palette.textSecondary,
            modifier = Modifier.size(CoreHubTokens.Metrics.railIcon),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) CoreHubTokens.Type.selectedWeight else null,
            // An unselected row is secondary; only the selected one is primary.
            color = if (selected) palette.textPrimary else palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The 4-segment conversation switch.
 *
 * Track: accent @ 5 %, radius 7, 2 dp of padding. Thumb: bg.card, radius 5,
 * one segment wide, sliding to the selection in 150 ms — `offset(x)` is a
 * layout-direction offset, so in Arabic it slides the other way on its own.
 * Each segment is a 16 dp icon over a 10 sp label, 42 dp tall in all.
 */
@Composable
fun ConversationSwitch(selected: Tab, modifier: Modifier = Modifier, onSelect: (Tab) -> Unit) {
    val palette = CoreHub.palette
    val segments = listOf(
        Triple(Tab.Chat, CoreHubIcons.Chat, NavDestination.chat.labelKey),
        Triple(Tab.Group, CoreHubIcons.Group, NavDestination.groupChat.labelKey),
        Triple(Tab.Workflow, CoreHubIcons.Workflow, NavDestination.workflow.labelKey),
        Triple(Tab.History, CoreHubIcons.History, NavDestination.history.labelKey),
    )
    val gap = CoreHubTokens.Metrics.segmentGap
    val index by animateFloatAsState(
        targetValue = segments.indexOfFirst { it.first == selected }.coerceAtLeast(0).toFloat(),
        animationSpec = tween(CoreHubTokens.Metrics.transitionFastMs),
        label = "segment",
    )
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(CoreHubTokens.Metrics.segmentItemHeight + CoreHubTokens.Metrics.segmentTrackPadding * 2)
            .background(palette.segmentTrack, RoundedCornerShape(CoreHubTokens.Radius.segmentTrack))
            .padding(CoreHubTokens.Metrics.segmentTrackPadding),
    ) {
        val segmentWidth = (maxWidth - gap * (segments.size - 1)) / segments.size
        Box(
            Modifier
                .offset(x = (segmentWidth + gap) * index)
                .width(segmentWidth)
                .fillMaxHeight()
                .background(palette.bgCard, RoundedCornerShape(CoreHubTokens.Radius.segment)),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            segments.forEach { (tab, icon, label) ->
                val active = tab == selected
                val tint = if (active) palette.textPrimary else palette.textSecondary
                Column(
                    modifier = Modifier
                        .width(segmentWidth)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(CoreHubTokens.Radius.segment))
                        .clickable { onSelect(tab) }
                        // iOS adds `.isSelected` here; TalkBack needs the same.
                        .semantics { this.selected = active },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(CoreHubTokens.Metrics.segmentIcon))
                    Spacer(Modifier.height(CoreHubTokens.Metrics.segmentLabelGap))
                    BasicText(
                        text = stringResource(label),
                        style = CoreHubTextStyles.segmentLabel.copy(
                            fontWeight = if (active) CoreHubTokens.Type.groupHeaderWeight else FontWeight.Normal,
                            color = tint,
                        ),
                        maxLines = 1,
                        softWrap = false,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = CoreHubTokens.Type.segmentLabelMin,
                            maxFontSize = CoreHubTokens.Type.groupHeader,
                            stepSize = 0.5.sp,
                        ),
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * The Group Chat segment's list, in the drawer itself (iOS `DrawerRoomList`):
 * the room rows for the current account, under a "GROUP CHAT n" header that
 * carries "New room" and "Join by code".
 */
@Composable
private fun DrawerRoomList(
    state: UiState,
    viewModel: AppViewModel,
    modifier: Modifier,
    header: LazyListScope.() -> Unit,
    onOpen: (RoomInfo) -> Unit,
) {
    val palette = CoreHub.palette
    var creating by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }
    if (creating) NewRoomDialog(state, viewModel) { creating = false }
    if (joining) {
        TextPromptDialog(
            title = stringResource(R.string.room_join_title),
            initial = "",
            hint = stringResource(R.string.room_join_hint),
            action = stringResource(R.string.room_join),
            onConfirm = { viewModel.joinRoomByCode(it); joining = false },
            onDismiss = { joining = false },
        )
    }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
        header()
        item(key = "rooms-header") {
            DrawerSectionHeader(stringResource(R.string.nav_group_chat), state.rooms.size) {
                DrawerSectionAction(CoreHubIcons.NewChat, stringResource(R.string.groups_new)) { creating = true }
                DrawerSectionAction(CoreHubIcons.Link, stringResource(R.string.room_join_title)) { joining = true }
            }
        }
        if (state.loadingRooms && state.rooms.isEmpty()) {
            item { Text(stringResource(R.string.intro_restoring), style = CoreHubTextStyles.meta, color = palette.textMuted, modifier = Modifier.padding(10.dp)) }
        }
        if (!state.loadingRooms && state.rooms.isEmpty()) {
            item { Text(stringResource(R.string.groups_empty), style = CoreHubTextStyles.meta, color = palette.textMuted, modifier = Modifier.padding(10.dp)) }
        }
        items(state.rooms, key = { it.id }) { room ->
            DrawerRoomRow(room, selected = state.openRoom?.room?.id == room.id) { onOpen(room) }
        }
    }
}

/** Room row: avatar stack, name … time, then "n agents  n members". */
@Composable
private fun DrawerRoomRow(room: RoomInfo, selected: Boolean, onClick: () -> Unit) {
    val palette = CoreHub.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CoreHubTokens.Radius.small))
            .background(if (selected) palette.selected else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = CoreHubTokens.Metrics.sessionRowPaddingH, vertical = CoreHubTokens.Metrics.sessionRowPaddingV),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AgentAvatarStack(room.agents, max = 3)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    room.name,
                    style = CoreHubTextStyles.sessionTitle.copy(
                        textDirection = TextDirection.Content,
                        fontWeight = if (selected) CoreHubTokens.Type.selectedWeight else null,
                    ),
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    formatStamp(room.lastActiveAt?.toString() ?: room.createdAt?.toString()),
                    style = CoreHubTextStyles.meta,
                    color = palette.textMuted,
                    maxLines = 1,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.room_agent_count, room.agents.size), style = CoreHubTextStyles.meta, color = palette.textMuted, maxLines = 1)
                Text(stringResource(R.string.room_member_count, room.memberCount), style = CoreHubTextStyles.meta, color = palette.textMuted, maxLines = 1)
            }
        }
    }
}

/** The Workflow segment's list (iOS `DrawerWorkflowList`): icon, name, node count. */
@Composable
private fun DrawerWorkflowList(
    state: UiState,
    viewModel: AppViewModel,
    modifier: Modifier,
    header: LazyListScope.() -> Unit,
    onOpen: (StudioWorkflow) -> Unit,
) {
    val palette = CoreHub.palette
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
        header()
        item(key = "workflows-header") { DrawerSectionHeader(stringResource(R.string.nav_workflow), state.workflows.size) }
        if (state.loadingWorkflows && state.workflows.isEmpty()) {
            item { Text(stringResource(R.string.intro_restoring), style = CoreHubTextStyles.meta, color = palette.textMuted, modifier = Modifier.padding(10.dp)) }
        }
        if (!state.loadingWorkflows && state.workflows.isEmpty()) {
            item { Text(stringResource(R.string.workflows_empty), style = CoreHubTextStyles.meta, color = palette.textMuted, modifier = Modifier.padding(10.dp)) }
        }
        items(state.workflows, key = { it.id }) { workflow ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CoreHubTokens.Radius.small))
                    .background(if (state.openWorkflow?.id == workflow.id) palette.selected else Color.Transparent)
                    .clickable { onOpen(workflow) }
                    .padding(horizontal = CoreHubTokens.Metrics.sessionRowPaddingH, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(CoreHubIcons.Workflow, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                Text(
                    workflow.name,
                    style = CoreHubTextStyles.sessionTitle.copy(textDirection = TextDirection.Content),
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(workflow.nodeCount.toString(), style = CoreHubTextStyles.meta, color = palette.textMuted)
            }
        }
    }
}

/**
 * The 10/600 uppercase section header the whole drawer shares (iOS
 * `GroupHeaderLabel`): title, muted count, then the section's own actions.
 */
@Composable
private fun DrawerSectionHeader(title: String, count: Int, actions: @Composable RowScope.() -> Unit = {}) {
    val palette = CoreHub.palette
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 2.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            title.uppercase(),
            style = CoreHubTextStyles.groupHeader,
            color = palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(count.toString(), style = CoreHubTextStyles.groupHeader.copy(fontWeight = null), color = palette.textMuted)
        Spacer(Modifier.weight(1f))
        actions()
    }
}

@Composable
private fun DrawerSectionAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(24.dp)) {
        Icon(icon, contentDescription = label, tint = CoreHub.palette.textMuted, modifier = Modifier.size(14.dp))
    }
}

/**
 * The footer, in the four rows iOS uses:
 *
 *  1. the profile chip and the model chip, side by side;
 *  2. Sign Out as a pill, the username beside it, the settings gear at the end;
 *  3. the connection dot and its label, then the language and theme toggles;
 *  4. the version, forced left-to-right, and the GitHub link.
 */
@Composable
private fun DrawerFooter(
    state: UiState,
    viewModel: AppViewModel,
    onSignOut: () -> Unit,
    onNavigate: (() -> Unit) -> Unit,
) {
    val palette = CoreHub.palette
    val context = LocalContext.current
    var profileMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    val activeProfile = state.activeProfile.ifBlank { "default" }
    val profileAvatar = state.profiles.firstOrNull { it.name == activeProfile }?.avatar
    val modelLabel = state.sessionModel
        ?: state.profiles.firstOrNull { it.name == activeProfile }?.model?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.sheet_model)

    Column(
        modifier = Modifier.padding(
            horizontal = CoreHubTokens.Metrics.footerPaddingH,
            vertical = CoreHubTokens.Metrics.footerPaddingV,
        ),
        verticalArrangement = Arrangement.spacedBy(CoreHubTokens.Metrics.footerRowGap),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(CoreHubTokens.Metrics.footerChipGap)) {
            Box(Modifier.weight(1f)) {
                FooterChip(
                    label = activeProfile,
                    leading = { ProfileAvatar(activeProfile, profileAvatar, size = CoreHubTokens.Metrics.footerChipAvatar) },
                    trailing = {
                        Icon(
                            CoreHubIcons.ChevronUpDown,
                            contentDescription = null,
                            tint = palette.textMuted,
                            modifier = Modifier.size(CoreHubTokens.Metrics.footerChipChevron),
                        )
                    },
                    contentDescription = stringResource(R.string.drawer_profile),
                ) { profileMenu = true }
                DropdownMenu(expanded = profileMenu, onDismissRequest = { profileMenu = false }) {
                    state.profiles.forEach { profile ->
                        DropdownMenuItem(
                            text = { Text(profile.name, fontWeight = if (profile.name == activeProfile) FontWeight.SemiBold else null) },
                            leadingIcon = { ProfileAvatar(profile.name, profile.avatar, size = 20.dp) },
                            onClick = { profileMenu = false; onNavigate { viewModel.selectProfile(profile.name) } },
                        )
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                FooterChip(
                    label = modelLabel,
                    labelDirection = TextDirection.Ltr,
                    leading = {
                        Icon(
                            CoreHubIcons.Models,
                            contentDescription = null,
                            tint = palette.textPrimary,
                            modifier = Modifier.size(CoreHubTokens.Metrics.footerChipIcon),
                        )
                    },
                    contentDescription = stringResource(R.string.drawer_model),
                ) { viewModel.loadModels(); modelMenu = true }
                DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                    if (state.loadingModels && state.models.isEmpty()) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.context_loading)) }, onClick = {}, enabled = false)
                    }
                    if (!state.loadingModels && state.models.isEmpty()) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.sheet_empty)) }, onClick = {}, enabled = false)
                    }
                    state.models.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(option.id, fontWeight = if (option.id == state.sessionModel) FontWeight.SemiBold else null, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr))
                                    Text(option.provider, style = CoreHubTextStyles.meta, color = palette.textMuted)
                                }
                            },
                            onClick = { modelMenu = false; viewModel.selectModel(option) },
                        )
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CoreHubTokens.Metrics.footerChipGap),
        ) {
            Row(
                modifier = Modifier
                    .height(CoreHubTokens.Metrics.pillHeight)
                    .clip(RoundedCornerShape(CoreHubTokens.Radius.pill))
                    .background(palette.bgCard)
                    .border(CoreHubTokens.Metrics.footerChipBorder, palette.inputBorder, RoundedCornerShape(CoreHubTokens.Radius.pill))
                    .clickable(onClick = onSignOut)
                    .padding(horizontal = CoreHubTokens.Metrics.pillPaddingH),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CoreHubTokens.Metrics.sectionGap),
            ) {
                Icon(
                    CoreHubIcons.Logout,
                    contentDescription = null,
                    tint = palette.textPrimary,
                    modifier = Modifier.size(CoreHubTokens.Metrics.pillIcon),
                )
                Text(
                    stringResource(R.string.action_sign_out),
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.textPrimary,
                    maxLines = 1,
                )
            }
            state.account?.takeIf { it.isNotBlank() }?.let { username ->
                Text(
                    username,
                    style = CoreHubTextStyles.meta,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .height(CoreHubTokens.Metrics.usernameChipHeight)
                        .background(palette.hover, RoundedCornerShape(CoreHubTokens.Radius.pill))
                        .padding(horizontal = CoreHubTokens.Metrics.usernameChipPaddingH)
                        .wrapContentHeight(),
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { onNavigate { viewModel.openSettings() } },
                modifier = Modifier.size(CoreHubTokens.Metrics.footerSettingsButton),
            ) {
                Icon(
                    CoreHubIcons.Settings,
                    contentDescription = stringResource(NavDestination.settings.labelKey),
                    tint = if (state.screen == Screen.Settings) palette.textPrimary else palette.textSecondary,
                    modifier = Modifier.size(CoreHubTokens.Metrics.footerSettingsIcon),
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CoreHubTokens.Metrics.footerChipGap),
        ) {
            Box(
                Modifier
                    .size(CoreHubTokens.Metrics.connectionDot)
                    .background(if (state.connected) palette.success else palette.error, CircleShape),
            )
            Text(
                stringResource(if (state.connected) R.string.connected else R.string.disconnected),
                style = CoreHubTextStyles.meta,
                color = palette.textMuted,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            DrawerLanguageSwitch(state, viewModel)
            DrawerThemeSwitch(state, viewModel)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CoreHubTokens.Metrics.footerChipGap)) {
            Text(
                stringResource(R.string.footer_version, state.serverVersion ?: BuildConfig.VERSION_NAME),
                style = CoreHubTextStyles.meta.copy(textDirection = TextDirection.Ltr),
                color = palette.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(STUDIO_REPOSITORY_URL))) },
                modifier = Modifier.size(CoreHubTokens.Metrics.footerToggleWidth),
            ) {
                Icon(
                    painterResource(R.drawable.ic_github),
                    contentDescription = stringResource(R.string.settings_studio_github),
                    tint = palette.textMuted,
                    modifier = Modifier.size(CoreHubTokens.Metrics.githubIcon),
                )
            }
        }
    }
}

/** Profile / model chip: 30 dp tall, bg.card on a 1 dp border, radius 6. */
@Composable
private fun FooterChip(
    label: String,
    leading: @Composable () -> Unit,
    contentDescription: String,
    labelDirection: TextDirection = TextDirection.Content,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val palette = CoreHub.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CoreHubTokens.Metrics.footerChipHeight)
            .clip(RoundedCornerShape(CoreHubTokens.Radius.small))
            .background(palette.bgCard)
            .border(CoreHubTokens.Metrics.footerChipBorder, palette.border, RoundedCornerShape(CoreHubTokens.Radius.small))
            .clickable(onClickLabel = contentDescription, onClick = onClick)
            .padding(horizontal = CoreHubTokens.Metrics.usernameChipPaddingH),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoreHubTokens.Metrics.sectionGap),
    ) {
        leading()
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(textDirection = labelDirection, fontWeight = FontWeight.Normal),
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/**
 * The 28 × 24 language chip: the current language as a short mark ("A" while
 * the phone decides, "EN", "ع"), never the whole word.
 */
@Composable
private fun DrawerLanguageSwitch(state: UiState, viewModel: AppViewModel) {
    var sheet by remember { mutableStateOf(false) }
    if (sheet) LanguageSheet(state, viewModel) { sheet = false }
    FooterToggle(stringResource(R.string.settings_language), onClick = { sheet = true }) {
        Text(
            when (state.language) {
                "ar" -> "ع"
                "en" -> "EN"
                else -> "A"
            },
            style = CoreHubTextStyles.meta.copy(fontWeight = CoreHubTokens.Type.groupHeaderWeight),
            color = CoreHub.palette.textSecondary,
            maxLines = 1,
        )
    }
}

/** The 28 × 24 theme chip: sun, moon, or the half circle for "follow the system". */
@Composable
private fun DrawerThemeSwitch(state: UiState, viewModel: AppViewModel) {
    var menu by remember { mutableStateOf(false) }
    Box {
        FooterToggle(stringResource(NavDestination.theme.labelKey), onClick = { menu = true }) {
            Icon(
                when (state.appearance) {
                    "light" -> CoreHubIcons.ThemeLight
                    "dark" -> CoreHubIcons.ThemeDark
                    else -> CoreHubIcons.ThemeSystem
                },
                contentDescription = null,
                tint = CoreHub.palette.textSecondary,
                modifier = Modifier.size(CoreHubTokens.Metrics.footerToggleIcon),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            listOf(
                "system" to R.string.appearance_system,
                "light" to R.string.appearance_light,
                "dark" to R.string.appearance_dark,
            ).forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label), fontWeight = if (state.appearance == value) FontWeight.SemiBold else null) },
                    onClick = { menu = false; viewModel.setAppearance(value) },
                )
            }
        }
    }
}

/** The small square control the language and theme switches share. */
@Composable
private fun FooterToggle(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = CoreHubTokens.Metrics.footerToggleWidth, height = CoreHubTokens.Metrics.footerToggleHeight)
            .clip(RoundedCornerShape(CoreHubTokens.Radius.tag))
            .background(CoreHub.palette.hover)
            .clickable(onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}
