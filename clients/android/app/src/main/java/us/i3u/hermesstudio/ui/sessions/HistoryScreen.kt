package us.i3u.hermesstudio.ui.sessions

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import us.i3u.hermesstudio.AppViewModel
import us.i3u.hermesstudio.ConfirmDialog
import us.i3u.hermesstudio.R
import us.i3u.hermesstudio.StudioHorizontalPadding
import us.i3u.hermesstudio.Tab
import us.i3u.hermesstudio.UiState
import us.i3u.hermesstudio.ui.navigation.MenuButton
import us.i3u.hermesstudio.ui.theme.CoreHub

/**
 * The History section (the web's hermes.history route): the full session
 * browser with search, the same grouped list as the drawer, refresh and
 * "delete shown".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(state: UiState, viewModel: AppViewModel, onMenu: () -> Unit) {
    val palette = CoreHub.palette
    var deleteVisible by remember { mutableStateOf(false) }
    val shown = state.sessionSearchResults ?: state.sessions
    if (deleteVisible) {
        ConfirmDialog(
            title = stringResource(R.string.session_batch_delete),
            body = stringResource(R.string.session_batch_delete_body, shown.size),
            action = stringResource(R.string.action_delete),
            onConfirm = { viewModel.batchDeleteVisibleSessions(); deleteVisible = false },
            onDismiss = { deleteVisible = false },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.segment_history), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { MenuButton(onMenu) },
                actions = {
                    IconButton(onClick = { deleteVisible = true }, enabled = shown.isNotEmpty()) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.session_batch_delete), tint = palette.textSecondary)
                    }
                    IconButton(onClick = { viewModel.refreshSessions() }, enabled = !state.refreshingSessions) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh), tint = palette.textSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.bgPrimary, scrolledContainerColor = palette.bgPrimary),
            )
        },
    ) { padding ->
        SessionListPane(
            state = state,
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize().padding(padding),
            showSearch = true,
            contentPadding = PaddingValues(start = StudioHorizontalPadding, end = StudioHorizontalPadding, top = 8.dp, bottom = 28.dp),
            onOpen = { session -> viewModel.showTab(Tab.Chat); viewModel.openSession(session) },
        )
    }
}
