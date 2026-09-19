package us.i3u.hermesstudio.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import us.i3u.hermesstudio.AppViewModel
import us.i3u.hermesstudio.BuildConfig
import us.i3u.hermesstudio.ConfirmDialog
import us.i3u.hermesstudio.LanguageAction
import us.i3u.hermesstudio.ProfileAvatar
import us.i3u.hermesstudio.R
import us.i3u.hermesstudio.RoomInfo
import us.i3u.hermesstudio.Screen
import us.i3u.hermesstudio.SettingsGroup
import us.i3u.hermesstudio.StudioWorkflow
import us.i3u.hermesstudio.Tab
import us.i3u.hermesstudio.TextPromptDialog
import us.i3u.hermesstudio.UiState
import us.i3u.hermesstudio.isSuperAdmin
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
    val width = CoreHubTokens.Metrics.drawerWidth
    Box(Modifier.fillMaxSize()) {
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
                    .offset(x = -width * (1f - progress)),
            ) { drawer() }
        }
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

    Surface(
        color = palette.bgSidebar,
        contentColor = palette.textPrimary,
        shape = RoundedCornerShape(topEnd = CoreHubTokens.Radius.card, bottomEnd = CoreHubTokens.Radius.card),
        shadowElevation = CoreHubTokens.Shadow.card,
        modifier = Modifier.fillMaxHeight(),
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
                modifier = Modifier.fillMaxWidth().height(CoreHubTokens.Metrics.headerHeight).padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_core_hub_mark), contentDescription = null, tint = palette.textPrimary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(CoreHubIcons.Close, contentDescription = stringResource(R.string.action_dismiss), tint = palette.textSecondary) }
            }

            // Rail, switch and the list of the selected segment share one
            // scroll so a short screen (landscape) still reaches the list; the
            // footer stays put.
            val drawerHeader: LazyListScope.() -> Unit = {
                item(key = "rail") {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        RailItem(CoreHubIcons.NewChat, stringResource(R.string.action_new_chat)) { go { viewModel.startNewConversation() } }
                        RailItem(CoreHubIcons.Search, stringResource(R.string.nav_search), selected = state.screen == Screen.History) { go { viewModel.showTab(Tab.History) } }
                        RailItem(CoreHubIcons.DeviceConnections, stringResource(R.string.nav_device_connections), selected = state.screen == Screen.Connections) { go { viewModel.openConnections() } }
                        if (state.isSuperAdmin) {
                            RailItem(CoreHubIcons.AgentManager, stringResource(R.string.nav_agent_manager), selected = state.screen == Screen.AgentHub) { go { viewModel.openAgentManager() } }
                        }
                        RailItem(CoreHubIcons.Models, stringResource(R.string.nav_models), selected = state.openGroup == SettingsGroup.Models && state.screen == Screen.SettingsGroup) { go { viewModel.openSettingsGroup(SettingsGroup.Models) } }
                    }
                }
                item(key = "switch") {
                    ConversationSwitch(
                        selected = state.tab,
                        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 8.dp),
                    ) { tab ->
                        // iOS `AppStore.switchMode`: the segment picks what the
                        // list underneath shows and the drawer stays open;
                        // only History, a full page of its own, closes it.
                        if (tab == Tab.History) go { viewModel.showTab(tab) } else viewModel.showTab(tab)
                    }
                }
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
                    onOpen = { session -> go { viewModel.showTab(Tab.Chat); viewModel.openSession(session) } },
                )
            }

            HorizontalDivider(color = palette.borderLight)
            DrawerFooter(state, viewModel, onSignOut = { confirmSignOut = true }, onNavigate = ::go)
            // Status dot · version · GitHub · language, on one compact line.
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(8.dp).background(if (state.connected) palette.success else palette.error, CircleShape))
                Text(
                    stringResource(if (state.connected) R.string.connected else R.string.disconnected),
                    style = CoreHubTextStyles.meta,
                    color = palette.textMuted,
                    maxLines = 1,
                )
                Text("·", style = CoreHubTextStyles.meta, color = palette.textMuted)
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
                    modifier = Modifier.size(28.dp),
                ) { Icon(painterResource(R.drawable.ic_github), contentDescription = stringResource(R.string.settings_studio_github), tint = palette.textMuted, modifier = Modifier.size(15.dp)) }
                LanguageAction(state, viewModel)
            }
        }
    }
}

/** Nav item: 14 sp, radius 6, selected = accent @ 12 % with text.primary at weight 500. */
@Composable
private fun RailItem(icon: ImageVector, label: String, selected: Boolean = false, onClick: () -> Unit) {
    val palette = CoreHub.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CoreHubTokens.Radius.small))
            .background(if (selected) palette.selected else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) palette.textPrimary else palette.textSecondary, modifier = Modifier.size(18.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) CoreHubTokens.Type.selectedWeight else null,
            color = if (selected) palette.textPrimary else palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The 4-segment control: 30 px tall, radius 5, track accent @ 5 %, selected segment on bg.card. */
@Composable
fun ConversationSwitch(selected: Tab, modifier: Modifier = Modifier, onSelect: (Tab) -> Unit) {
    val palette = CoreHub.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(CoreHubTokens.Metrics.segmentHeight)
            .background(palette.segmentTrack, RoundedCornerShape(CoreHubTokens.Radius.segment))
            .padding(2.dp),
    ) {
        listOf(
            Tab.Chat to R.string.segment_chat,
            Tab.Group to R.string.segment_group_chat,
            Tab.Workflow to R.string.segment_workflow,
            Tab.History to R.string.segment_history,
        ).forEach { (tab, label) ->
            val active = tab == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(CoreHubTokens.Radius.tag))
                    .background(if (active) palette.bgCard else Color.Transparent)
                    .clickable { onSelect(tab) },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = stringResource(label),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (active) CoreHubTokens.Type.selectedWeight else FontWeight.Normal,
                        color = if (active) palette.textPrimary else palette.textSecondary,
                    ),
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = CoreHubTokens.Type.navTab, stepSize = 0.5.sp),
                    modifier = Modifier.padding(horizontal = 3.dp),
                )
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
            DrawerSectionHeader(stringResource(R.string.segment_group_chat), state.rooms.size) {
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
        item(key = "workflows-header") { DrawerSectionHeader(stringResource(R.string.segment_workflow), state.workflows.size) }
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

/** Profile selector, model selector, sign out (+username chip), status dot. */
@Composable
private fun DrawerFooter(
    state: UiState,
    viewModel: AppViewModel,
    onSignOut: () -> Unit,
    onNavigate: (() -> Unit) -> Unit,
) {
    val palette = CoreHub.palette
    var profileMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    val activeProfile = state.activeProfile.ifBlank { "default" }
    val profileAvatar = state.profiles.firstOrNull { it.name == activeProfile }?.avatar
    val modelLabel = state.sessionModel
        ?: state.profiles.firstOrNull { it.name == activeProfile }?.model?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.sheet_model)

    Column(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        // The gear that opens the settings drawer (the web's AppSidebar).
        RailItem(CoreHubIcons.Settings, stringResource(R.string.settings_title), selected = state.screen == Screen.Settings) { onNavigate { viewModel.openSettings() } }
        Box {
            FooterRow(
                leading = { ProfileAvatar(activeProfile, profileAvatar, size = 20.dp) },
                label = activeProfile,
                trailing = stringResource(R.string.drawer_profile),
            ) { profileMenu = true }
            DropdownMenu(expanded = profileMenu, onDismissRequest = { profileMenu = false }) {
                state.profiles.forEach { profile ->
                    DropdownMenuItem(
                        text = { Text(profile.name, fontWeight = if (profile.name == activeProfile) FontWeight.SemiBold else null) },
                        leadingIcon = { ProfileAvatar(profile.name, profile.avatar, size = 20.dp) },
                        onClick = { profileMenu = false; onNavigate { viewModel.selectProfile(profile.name) } },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_profiles)) },
                    onClick = { profileMenu = false; onNavigate { viewModel.openProfiles() } },
                )
            }
        }
        Box {
            FooterRow(
                leading = { Icon(CoreHubIcons.Models, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(18.dp)) },
                label = modelLabel,
                labelDirection = TextDirection.Ltr,
                trailing = stringResource(R.string.drawer_model),
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CoreHubTokens.Radius.small))
                .clickable(onClick = onSignOut)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(CoreHubIcons.Logout, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.action_sign_out), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            state.account?.takeIf { it.isNotBlank() }?.let { username ->
                Text(
                    username,
                    style = CoreHubTextStyles.meta,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .background(palette.selected, RoundedCornerShape(CoreHubTokens.Radius.pill))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun FooterRow(
    leading: @Composable () -> Unit,
    label: String,
    trailing: String,
    labelDirection: TextDirection = TextDirection.Content,
    onClick: () -> Unit,
) {
    val palette = CoreHub.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CoreHubTokens.Radius.small))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        leading()
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge.copy(textDirection = labelDirection),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(trailing, style = CoreHubTextStyles.meta, color = palette.textMuted)
        Icon(CoreHubIcons.ChevronRight, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(12.dp))
    }
}
