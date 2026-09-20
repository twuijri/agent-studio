package us.i3u.hermesstudio.ui.agents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewKanban
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import us.i3u.hermesstudio.AgentCard
import us.i3u.hermesstudio.AgentKind
import us.i3u.hermesstudio.AppViewModel
import us.i3u.hermesstudio.ErrorNote
import us.i3u.hermesstudio.LoadingRow
import us.i3u.hermesstudio.NoticeNote
import us.i3u.hermesstudio.R
import us.i3u.hermesstudio.StudioCardDivider
import us.i3u.hermesstudio.StudioDestinationRow
import us.i3u.hermesstudio.StudioGroupedCard
import us.i3u.hermesstudio.StudioHorizontalPadding
import us.i3u.hermesstudio.StudioTopBar
import us.i3u.hermesstudio.UiState
import us.i3u.hermesstudio.navigation.NavDestination
import us.i3u.hermesstudio.ui.sessions.AgentAvatar
import us.i3u.hermesstudio.ui.sessions.ChatAgentAvatars
import us.i3u.hermesstudio.ui.theme.CoreHub
import us.i3u.hermesstudio.ui.theme.CoreHubIcons

/**
 * "Under the agent" (NAVIGATION.md §4): entered from a card in the Agent
 * Manager, left back to it. The rows come from `NavDestination.agentSections`
 * — capabilities first, settings last — so Hermes, Ekko and a coding agent
 * each list exactly what their web sidebar lists, and Skills, MCP and Memory
 * exist once per agent instead of once for the whole app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(state: UiState, viewModel: AppViewModel) {
    val agent = state.openAgent ?: return
    val palette = CoreHub.palette
    val card = state.agents.firstOrNull { it.id == agent.id }
    val sections = NavDestination.agentSections(agent.kind)

    Scaffold(
        topBar = {
            StudioTopBar(
                title = agent.name,
                subtitle = if (agent.kind == AgentKind.Coding) stringResource(NavDestination.agentCoding.labelKey) else agent.provider,
                onBack = { viewModel.back() },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(StudioHorizontalPadding, 8.dp, StudioHorizontalPadding, 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { item { ErrorNote(it) { viewModel.dismissError() } } }
            state.notice?.let { item { NoticeNote(it) { viewModel.dismissNotice() } } }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AgentAvatar(ChatAgentAvatars.forRuntime(agent.id), size = 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(agent.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        card?.let { c ->
                            val detail = listOfNotNull(
                                c.version.takeIf { it.isNotBlank() }?.let { if (it.startsWith("v")) it else "v$it" },
                                c.source.takeIf { it.isNotBlank() && it != "not-installed" },
                            ).joinToString(" · ")
                            if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = palette.textMuted)
                        }
                    }
                }
            }
            item {
                Text(stringResource(R.string.agent_sections_note), style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
            }
            item {
                StudioGroupedCard {
                    sections.forEachIndexed { index, destination ->
                        AgentSectionRow(destination, sectionIcon(destination), palette.accent) {
                            viewModel.openAgentSection(agent, destination)
                        }
                        if (index != sections.lastIndex) StudioCardDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentSectionRow(destination: NavDestination, icon: ImageVector, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    StudioDestinationRow(icon = icon, color = color, title = stringResource(destination.labelKey), onClick = onClick)
}

private fun sectionIcon(destination: NavDestination): ImageVector = when (destination) {
    NavDestination.jobs -> Icons.Filled.Schedule
    NavDestination.kanban -> Icons.Filled.ViewKanban
    NavDestination.channels -> Icons.Filled.Forum
    NavDestination.skills -> Icons.Filled.School
    NavDestination.plugins -> Icons.Filled.Extension
    NavDestination.mcp -> Icons.Filled.Cable
    NavDestination.memory -> Icons.Filled.Memory
    NavDestination.journey -> Icons.Filled.AccountTree
    NavDestination.hermesSettings, NavDestination.ekkoSettings, NavDestination.codingAgentSettings -> CoreHubIcons.Settings
    else -> Icons.Filled.Tune
}

/**
 * The Hermes card's "Manage runtime" (`AgentManagerView.vue:517-536`): the
 * runtime versions as a sheet over the Agent Manager, not a screen of their
 * own — installed versions, activation, downloads and the Web UI restart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuntimeManagerSheet(state: UiState, viewModel: AppViewModel, onDismiss: () -> Unit) {
    val palette = CoreHub.palette
    LaunchedEffect(Unit) { viewModel.loadRuntimeVersions() }
    val versions = state.runtimeVersions
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(
            contentPadding = PaddingValues(StudioHorizontalPadding, 4.dp, StudioHorizontalPadding, 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text(stringResource(R.string.agent_manage_runtime), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            if (state.loadingRuntimeVersions) item { LoadingRow() }
            state.error?.let { item { ErrorNote(it) { viewModel.dismissError() } } }
            item {
                Text(
                    "${versions?.platform.orEmpty()} · Runtime ${versions?.activeRuntime.orEmpty()} · WebUI ${versions?.activeWebUi.orEmpty()}",
                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
                    color = palette.textSecondary,
                )
            }
            item { Button(onClick = viewModel::restartWebUi) { Text(stringResource(R.string.restart_webui)) } }
            val installed = versions?.runtime.orEmpty() + versions?.webUi.orEmpty()
            items(installed.size, key = { installed[it].kind + installed[it].version }) { index ->
                val version = installed[index]
                StudioGroupedCard {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${version.kind} ${version.version}", Modifier.weight(1f), fontFamily = FontFamily.Monospace)
                        if (version.active) Text(stringResource(R.string.agent_status_active), color = palette.success)
                        else TextButton(onClick = { viewModel.activateVersion(version) }) { Text(stringResource(R.string.activate)) }
                    }
                }
            }
            versions?.remoteRuntime?.let { remote ->
                items(remote.size, key = { "runtime-$it" }) { index ->
                    TextButton(onClick = { viewModel.downloadVersion(remote[index], false) }) { Text("Download Runtime ${remote[index]}") }
                }
            }
            versions?.remoteWebUi?.let { remote ->
                items(remote.size, key = { "webui-$it" }) { index ->
                    TextButton(onClick = { viewModel.downloadVersion(remote[index], true) }) { Text("Download WebUI ${remote[index]}") }
                }
            }
        }
    }
}

/** The Hermes card's "CLI details": what the server found on its own PATH. */
@Composable
fun HermesCliDetailsDialog(card: AgentCard, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.agent_cli_details)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(card.version.ifBlank { "—" }, fontFamily = FontFamily.Monospace)
                Text(card.path.ifBlank { "—" }, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                if (card.error.isNotBlank()) Text(card.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) } },
    )
}
