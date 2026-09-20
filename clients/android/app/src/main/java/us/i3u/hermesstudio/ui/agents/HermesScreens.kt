package us.i3u.hermesstudio.ui.agents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import us.i3u.hermesstudio.AgentSettings
import us.i3u.hermesstudio.AppViewModel
import us.i3u.hermesstudio.ErrorNote
import us.i3u.hermesstudio.LoadingRow
import us.i3u.hermesstudio.MemoryStudioSettings
import us.i3u.hermesstudio.NoticeNote
import us.i3u.hermesstudio.R
import us.i3u.hermesstudio.SessionStudioSettings
import us.i3u.hermesstudio.SettingsGroup
import us.i3u.hermesstudio.StudioGroupedCard
import us.i3u.hermesstudio.StudioHorizontalPadding
import us.i3u.hermesstudio.StudioTopBar
import us.i3u.hermesstudio.UiState
import us.i3u.hermesstudio.navigation.NavDestination
import us.i3u.hermesstudio.ui.theme.CoreHub

/** The three tabs of `HermesSettingsView.vue:66-76`, in that order. */
private val HERMES_SETTINGS_TABS = listOf(
    SettingsGroup.Agent to R.string.hermes_settings_tab_agent,
    SettingsGroup.Memory to R.string.hermes_settings_tab_memory,
    SettingsGroup.Sessions to R.string.hermes_settings_tab_session,
)

/**
 * Hermes › Settings: the agent's runtime settings, which belong under the
 * agent and never in the app's Settings (NAVIGATION.md rule 2). `Agent` is
 * `AgentSettings.vue` plus `GatewayAutoStartSettings.vue`; `Memory` is
 * `MemorySettings.vue`; `Session` is `SessionSettings.vue` — approvals, skill
 * approvals and the session reset.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HermesSettingsScreen(state: UiState, viewModel: AppViewModel) {
    val palette = CoreHub.palette
    val selectedIndex = HERMES_SETTINGS_TABS.indexOfFirst { it.first == state.hermesSettingsTab }.coerceAtLeast(0)
    Scaffold(
        topBar = {
            StudioTopBar(
                title = stringResource(NavDestination.hermesSettings.labelKey),
                subtitle = stringResource(NavDestination.agentHermes.labelKey),
                onBack = { viewModel.back() },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                selectedTabIndex = selectedIndex,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = palette.textPrimary,
                indicator = { positions ->
                    TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[selectedIndex]), color = palette.accent)
                },
                divider = {},
            ) {
                HERMES_SETTINGS_TABS.forEachIndexed { index, (group, label) ->
                    Tab(
                        selected = index == selectedIndex,
                        onClick = { viewModel.selectHermesSettingsTab(group) },
                        text = { Text(stringResource(label), style = MaterialTheme.typography.labelLarge) },
                        selectedContentColor = palette.textPrimary,
                        unselectedContentColor = palette.textSecondary,
                    )
                }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()) {
                if (state.savingSetting) LoadingRow()
                state.error?.let { ErrorNote(it) { viewModel.dismissError() } }
                state.notice?.let { NoticeNote(it) { viewModel.dismissNotice() } }
                if (state.loadingAgentSettings || state.loadingStudioSettings) {
                    LoadingRow()
                } else {
                    when (HERMES_SETTINGS_TABS[selectedIndex].first) {
                        SettingsGroup.Agent -> AgentSettings(state, viewModel)
                        SettingsGroup.Memory -> MemoryStudioSettings(state, viewModel)
                        else -> SessionStudioSettings(state, viewModel)
                    }
                }
            }
        }
    }
}

private data class MemorySection(val key: String, val title: Int, val text: (UiState) -> String)

private val MEMORY_SECTIONS = listOf(
    MemorySection("memory", R.string.memory_section_memory) { it.hermesMemory?.memory.orEmpty() },
    MemorySection("user", R.string.memory_section_user) { it.hermesMemory?.user.orEmpty() },
    MemorySection("soul", R.string.memory_section_soul) { it.hermesMemory?.soul.orEmpty() },
)

/**
 * Hermes › Memory: the web's memory browser (`MemoryView.vue`) — MEMORY.md,
 * USER.md and SOUL.md of the profile, each readable in place and editable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HermesMemoryScreen(state: UiState, viewModel: AppViewModel) {
    val palette = CoreHub.palette
    var editing by remember { mutableStateOf<MemorySection?>(null) }
    editing?.let { section ->
        var draft by remember(section.key) { mutableStateOf(section.text(state)) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(section.title)) },
            text = {
                OutlinedTextField(
                    draft,
                    { draft = it },
                    Modifier.fillMaxWidth(),
                    minLines = 10,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Content),
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.saveHermesMemory(section.key, draft); editing = null }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    Scaffold(
        topBar = {
            StudioTopBar(
                title = stringResource(NavDestination.memory.labelKey),
                subtitle = stringResource(NavDestination.agentHermes.labelKey),
                onBack = { viewModel.back() },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(StudioHorizontalPadding, 8.dp, StudioHorizontalPadding, 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.loadingHermesMemory || state.busy) item { LoadingRow() }
            state.error?.let { item { ErrorNote(it) { viewModel.dismissError() } } }
            state.notice?.let { item { NoticeNote(it) { viewModel.dismissNotice() } } }
            MEMORY_SECTIONS.forEach { section ->
                item(key = section.key) {
                    val text = section.text(state)
                    StudioGroupedCard {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(section.title), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { editing = section }) { Text(stringResource(R.string.action_edit)) }
                            }
                            Text(
                                text.ifBlank { stringResource(R.string.memory_empty) },
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Content),
                                color = if (text.isBlank()) palette.textMuted else palette.textSecondary,
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
