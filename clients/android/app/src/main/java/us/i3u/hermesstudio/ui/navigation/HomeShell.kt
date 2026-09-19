package us.i3u.hermesstudio.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import us.i3u.hermesstudio.AppViewModel
import us.i3u.hermesstudio.R
import us.i3u.hermesstudio.UiState
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
        content { viewModel.setDrawerOpen(true) }
    }
}

/** The app-bar hamburger that opens the drawer. */
@Composable
fun MenuButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(CoreHubIcons.Menu, contentDescription = stringResource(R.string.nav_menu), tint = CoreHub.palette.textPrimary)
    }
}
