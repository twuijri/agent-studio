package us.i3u.hermesstudio.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import us.i3u.hermesstudio.AppViewModel
import us.i3u.hermesstudio.R
import us.i3u.hermesstudio.Screen
import us.i3u.hermesstudio.UiState
import us.i3u.hermesstudio.navigation.NavDestination
import us.i3u.hermesstudio.ui.theme.CoreHub
import us.i3u.hermesstudio.ui.theme.CoreHubIcons

/**
 * The root shell shared by the four sections of the conversation switch
 * (Chat, Group Chat, Workflow, History): the content plus the off-canvas
 * drawer, opened from the app bar's hamburger.
 */
@Composable
fun HomeShell(
    state: UiState,
    viewModel: AppViewModel,
    content: @Composable (openDrawer: () -> Unit) -> Unit,
) {
    // The flag lives on the view model, not here: the conversation switch
    // changes `state.screen`, which swaps this shell for another section's
    // shell, and a shell-local flag would take the open drawer with it.
    CoreHubDrawerHost(
        open = state.drawerOpen,
        onOpenChange = viewModel::setDrawerOpen,
        drawer = { CoreHubDrawerContent(state, viewModel, onClose = { viewModel.setDrawerOpen(false) }) },
    ) {
        val banner = globalAgentNeedsYou(state)
        Column(Modifier.fillMaxSize()) {
            if (banner) GlobalAgentBanner(viewModel)
            // The banner already spent the status-bar inset; the section's own
            // Scaffold must not pad for it a second time.
            Box(
                Modifier
                    .weight(1f)
                    .then(if (banner) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier),
            ) { content { viewModel.setDrawerOpen(true) } }
        }
    }
    // The drawer's Search: a sheet over whatever is showing (SessionSearchModal.vue).
    SessionSearchSheet(state, viewModel)
}

/**
 * The web's `GlobalPendingActions.vue` is a corner notification for a Global
 * Agent conversation that needs the person. The phone knows that as an unread
 * `global_agent` session (activity landed while it was not open) or as a
 * pending approval in the open one; either way the strip opens the Global
 * Agent, which has no menu entry — as on the web.
 */
private fun globalAgentNeedsYou(state: UiState): Boolean {
    val unreadGlobal = state.sessions.any { it.source == "global_agent" && it.id in state.unreadSessionIds }
    val pendingGlobal = state.pendingRunAction != null && state.openSession?.source == "global_agent" && state.screen != Screen.Conversation
    return unreadGlobal || pendingGlobal
}

@Composable
private fun GlobalAgentBanner(viewModel: AppViewModel) {
    val palette = CoreHub.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.accent.copy(alpha = 0.12f))
            .clickable { viewModel.openGlobalAgent() }
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(CoreHubIcons.AgentManager, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
        Text(
            stringResource(R.string.global_agent_banner),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textPrimary,
            modifier = Modifier.weight(1f).padding(start = 10.dp),
        )
        Text(stringResource(NavDestination.globalAgent.labelKey), style = MaterialTheme.typography.labelLarge, color = palette.accent)
    }
}

/** The app-bar hamburger that opens the drawer. */
@Composable
fun MenuButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(CoreHubIcons.Menu, contentDescription = stringResource(R.string.nav_menu), tint = CoreHub.palette.textPrimary)
    }
}
