package us.i3u.hermesstudio.ui.agents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import us.i3u.hermesstudio.AgentCard
import us.i3u.hermesstudio.AgentKind
import us.i3u.hermesstudio.AgentPresence
import us.i3u.hermesstudio.AppViewModel
import us.i3u.hermesstudio.ConfirmDialog
import us.i3u.hermesstudio.ErrorNote
import us.i3u.hermesstudio.LoadingRow
import us.i3u.hermesstudio.NoticeNote
import us.i3u.hermesstudio.R
import us.i3u.hermesstudio.StudioHorizontalPadding
import us.i3u.hermesstudio.UiState
import us.i3u.hermesstudio.ui.sessions.AgentAvatar
import us.i3u.hermesstudio.ui.sessions.ChatAgentAvatars
import us.i3u.hermesstudio.ui.theme.CoreHub

/**
 * The Agent Manager, rebuilt around the web's `AgentManagerView.vue`: the
 * agents the server knows, each as a card carrying its install state, its
 * version, where it came from, and the actions that apply to it.
 *
 * Three things this screen fixes, all reported by the owner:
 *
 *  - It used to open a column of links, which read as a settings page. The
 *    agents come first now; the Hermes-side tools that used to be the whole
 *    page are kept, grouped and named, below the list.
 *  - Every agent card now leads somewhere. A coding agent opens its own
 *    settings — the two files the web's `CodingAgentConfigView` edits.
 *  - The list no longer depends on one endpoint answering. It is the fixed
 *    catalogue from the server source (see [us.i3u.hermesstudio.AgentCatalog]),
 *    and an agent the server did not report is shown as unsupported rather
 *    than dropped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentManagerScreen(
    state: UiState,
    viewModel: AppViewModel,
    hermesTools: @Composable () -> Unit,
) {
    val palette = CoreHub.palette
    var deleting by remember { mutableStateOf<AgentCard?>(null) }

    deleting?.let { card ->
        ConfirmDialog(
            title = stringResource(R.string.agent_delete_title, card.definition.name),
            body = stringResource(R.string.agent_delete_body, card.definition.packageName),
            action = stringResource(R.string.action_delete),
            onConfirm = { viewModel.deleteAgent(card.id); deleting = null },
            onDismiss = { deleting = null },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_agent_manager)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadAgents() }) {
                        Icon(Icons.Filled.Refresh, stringResource(R.string.action_refresh))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(StudioHorizontalPadding, 8.dp, StudioHorizontalPadding, 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.error?.let { item { ErrorNote(it) { viewModel.dismissError() } } }
            state.notice?.let { item { NoticeNote(it) { viewModel.dismissNotice() } } }
            if (state.loadingAgents) item { LoadingRow() }

            item {
                Text(
                    stringResource(R.string.agent_manager_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                )
            }

            // Installing, updating and deleting run npm where the server runs,
            // not on this phone. Saying so up front is cheaper than letting the
            // owner guess why a phone is installing a CLI.
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = palette.info.copy(alpha = 0.10f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.agent_server_side_note),
                        Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                }
            }

            if (state.agentSnapshotUnavailable) {
                item {
                    Text(
                        stringResource(R.string.agent_snapshot_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.warning,
                    )
                }
            }

            AgentKind.entries.forEach { kind ->
                val cards = state.agents.filter { it.definition.kind == kind }
                if (cards.isEmpty()) return@forEach
                item(key = "section-$kind") {
                    Text(
                        stringResource(
                            when (kind) {
                                AgentKind.Hermes -> R.string.agent_section_hermes
                                AgentKind.BuiltIn -> R.string.agent_section_builtin
                                AgentKind.Coding -> R.string.agent_section_coding
                            },
                        ),
                        Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.textSecondary,
                    )
                }
                cards.forEach { card ->
                    item(key = card.id) {
                        AgentRow(
                            card = card,
                            busy = state.agentBusyId == card.id,
                            viewModel = viewModel,
                            onDelete = { deleting = card },
                        )
                    }
                }
            }

            // The Hermes side of Core Hub — Kanban, skills, memory and the
            // rest — used to be this whole screen. It stays reachable, but
            // under the agents rather than instead of them.
            item { hermesTools() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgentRow(
    card: AgentCard,
    busy: Boolean,
    viewModel: AppViewModel,
    onDelete: () -> Unit,
) {
    val palette = CoreHub.palette
    val openSettings = { viewModel.openAgentSettings(card.definition) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = palette.bgCard,
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (card.hasSettings) Modifier.clickable { openSettings() } else Modifier),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AgentAvatar(ChatAgentAvatars.forRuntime(card.id), size = 34.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        card.definition.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        card.definition.provider,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                }
                PresenceBadge(card)
            }

            val detail = listOfNotNull(
                sourceLabel(card.source),
                card.version.takeIf { it.isNotBlank() }?.let { if (it.startsWith("v")) it else "v$it" },
            ).joinToString(" · ")
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = palette.textMuted)
            }
            if (card.definition.packageName.isNotBlank()) {
                Text(
                    card.definition.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = palette.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (card.path.isNotBlank()) {
                Text(
                    card.path,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = palette.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (card.error.isNotBlank()) {
                Text(card.error, style = MaterialTheme.typography.bodySmall, color = palette.error)
            }
            if (card.presence == AgentPresence.Unsupported) {
                Text(
                    stringResource(R.string.agent_unsupported_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.warning,
                )
            }

            if (card.autoUpdateSupported) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.agent_auto_update),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = card.autoUpdate,
                        enabled = !busy,
                        onCheckedChange = { viewModel.setAgentAutoUpdate(card.id, it) },
                    )
                }
            }

            if (busy) LoadingRow()

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (card.hasSettings) {
                    OutlinedButton(onClick = openSettings, enabled = !busy) {
                        Icon(Icons.Filled.Settings, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_settings))
                    }
                }
                if (card.installable) {
                    TextButton(onClick = { viewModel.installAgent(card.id) }, enabled = !busy) {
                        Text(
                            when {
                                card.updateVersion.isNotBlank() ->
                                    stringResource(R.string.agent_update_to, card.updateVersion)
                                card.installed -> stringResource(R.string.agent_reinstall)
                                else -> stringResource(R.string.agent_install)
                            },
                        )
                    }
                    TextButton(onClick = { viewModel.checkAgentUpdate(card.id) }, enabled = !busy) {
                        Text(stringResource(R.string.agent_check_update))
                    }
                    if (card.installed) {
                        TextButton(onClick = onDelete, enabled = !busy) {
                            Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // The one action the phone genuinely cannot offer: the web can ask
            // the server to open a native terminal, which the server refuses
            // inside Docker and without a desktop session anyway.
            if (card.definition.kind == AgentKind.Coding && card.installed) {
                Text(
                    stringResource(R.string.agent_terminal_unavailable),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textMuted,
                )
            }
        }
    }
}

@Composable
private fun PresenceBadge(card: AgentCard) {
    val palette = CoreHub.palette
    val (label, color) = when (card.presence) {
        AgentPresence.Installed -> stringResource(R.string.agent_state_installed) to palette.success
        AgentPresence.NotInstalled -> stringResource(R.string.agent_state_not_installed) to palette.warning
        AgentPresence.Unsupported -> stringResource(R.string.agent_state_unsupported) to palette.textMuted
        AgentPresence.Unknown -> stringResource(R.string.agent_state_unknown) to palette.textMuted
    }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.16f)) {
        Text(
            label,
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

/** `managed-runtime`, `user-cli`, `built-in`; `not-installed` says nothing extra. */
@Composable
private fun sourceLabel(source: String): String? = when (source) {
    "managed-runtime" -> stringResource(R.string.agent_source_managed)
    "user-cli" -> stringResource(R.string.agent_source_cli)
    "built-in" -> stringResource(R.string.agent_source_builtin)
    else -> null
}
