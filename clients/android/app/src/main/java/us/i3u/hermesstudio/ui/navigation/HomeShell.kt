package us.i3u.hermesstudio.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    var open by rememberSaveable { mutableStateOf(false) }
    CoreHubDrawerHost(
        open = open,
        onOpenChange = { open = it },
        drawer = { CoreHubDrawerContent(state, viewModel, onClose = { open = false }) },
    ) {
        content { open = true }
    }
}

/** The app-bar hamburger that opens the drawer. */
@Composable
fun MenuButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(CoreHubIcons.Menu, contentDescription = stringResource(R.string.nav_menu), tint = CoreHub.palette.textPrimary)
    }
}
