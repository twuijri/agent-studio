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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import us.i3u.hermesstudio.Screen
import us.i3u.hermesstudio.SettingsGroup
import us.i3u.hermesstudio.Tab
import us.i3u.hermesstudio.UiState
import us.i3u.hermesstudio.isSuperAdmin
import us.i3u.hermesstudio.ui.chat.STUDIO_REPOSITORY_URL
import us.i3u.hermesstudio.ui.sessions.SessionListPane
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
        Column(Modifier.fillMaxHeight().statusBarsPadding().navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(CoreHubTokens.Metrics.headerHeight).padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_core_hub_mark), contentDescription = null, tint = palette.textPrimary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(CoreHubIcons.Close, contentDescription = stringResource(R.string.action_dismiss), tint = palette.textSecondary) }
            }

            Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RailItem(CoreHubIcons.NewChat, stringResource(R.string.action_new_chat)) { go { viewModel.startNewConversation() } }
                RailItem(CoreHubIcons.Search, stringResource(R.string.nav_search), selected = state.screen == Screen.History) { go { viewModel.showTab(Tab.History) } }
                RailItem(CoreHubIcons.DeviceConnections, stringResource(R.string.nav_device_connections), selected = state.screen == Screen.Connections) { go { viewModel.openConnections() } }
                if (state.isSuperAdmin) {
                    RailItem(CoreHubIcons.AgentManager, stringResource(R.string.nav_agent_manager), selected = state.screen == Screen.AgentHub) { go { viewModel.openAgentManager() } }
                }
                RailItem(CoreHubIcons.Models, stringResource(R.string.nav_models), selected = state.openGroup == SettingsGroup.Models && state.screen == Screen.SettingsGroup) { go { viewModel.openSettingsGroup(SettingsGroup.Models) } }
            }

            Spacer(Modifier.height(10.dp))
            ConversationSwitch(
                selected = state.tab,
                modifier = Modifier.padding(horizontal = 12.dp),
            ) { tab -> go { viewModel.showTab(tab) } }
            Spacer(Modifier.height(6.dp))

            SessionListPane(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                onOpen = { session -> go { viewModel.showTab(Tab.Chat); viewModel.openSession(session) } },
            )

            HorizontalDivider(color = palette.borderLight)
            DrawerFooter(state, viewModel, onSignOut = { confirmSignOut = true }, onNavigate = ::go)
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 2.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.footer_version, state.serverVersion ?: BuildConfig.VERSION_NAME),
                    style = CoreHubTextStyles.meta.copy(textDirection = TextDirection.Ltr),
                    color = palette.textMuted,
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
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) CoreHubTokens.Type.selectedWeight else FontWeight.Normal,
                    color = if (active) palette.textPrimary else palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
        }
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

    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(8.dp).background(if (state.connected) palette.success else palette.error, CircleShape))
            Text(
                stringResource(if (state.connected) R.string.connected else R.string.disconnected),
                style = CoreHubTextStyles.meta,
                color = palette.textMuted,
            )
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
            .padding(horizontal = 10.dp, vertical = 8.dp),
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
