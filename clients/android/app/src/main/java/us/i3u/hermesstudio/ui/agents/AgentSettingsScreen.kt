package us.i3u.hermesstudio.ui.agents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import us.i3u.hermesstudio.AgentConfigFile
import us.i3u.hermesstudio.AppViewModel
import us.i3u.hermesstudio.ErrorNote
import us.i3u.hermesstudio.LoadingRow
import us.i3u.hermesstudio.NoticeNote
import us.i3u.hermesstudio.R
import us.i3u.hermesstudio.StudioHorizontalPadding
import us.i3u.hermesstudio.UiState
import us.i3u.hermesstudio.ui.theme.CoreHub

/**
 * One coding agent's settings, the phone's answer to the web's
 * `CodingAgentConfigView.vue` settings section: the agent's preference file
 * and its configuration file, each read from and written back to
 * `/api/coding-agents/{id}/config-files/{key}`.
 *
 * These are the agent's own dotfiles on the Core Hub server — `~/.claude`,
 * `~/.codex` and so on — so they are technical text: left-to-right,
 * monospaced, never reflowed by the interface language.
 *
 * The web's other sections (MCP servers, skills, and the DeepSeek Harness
 * presets and plugins) are not here; the README lists them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentSettingsScreen(state: UiState, viewModel: AppViewModel) {
    val open = state.openAgentSettings ?: return
    val palette = CoreHub.palette

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(open.agent.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(R.string.agent_settings_title),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(StudioHorizontalPadding, 8.dp, StudioHorizontalPadding, 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { item { ErrorNote(it) { viewModel.dismissError() } } }
            state.notice?.let { item { NoticeNote(it) { viewModel.dismissNotice() } } }
            if (open.loading || state.savingSetting) item { LoadingRow() }

            item {
                Text(
                    stringResource(R.string.agent_settings_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                )
            }

            item {
                FileEditor(
                    title = stringResource(R.string.agent_settings_preference),
                    file = open.preference,
                    draft = open.preferenceDraft,
                    error = open.preferenceError,
                    saving = state.savingSetting,
                    onChange = { viewModel.editAgentSettingsDraft(preference = true, value = it) },
                    onSave = { viewModel.saveAgentSettings(preference = true) },
                )
            }
            item {
                FileEditor(
                    title = stringResource(R.string.agent_settings_configuration),
                    file = open.configuration,
                    draft = open.configurationDraft,
                    error = open.configurationError,
                    saving = state.savingSetting,
                    onChange = { viewModel.editAgentSettingsDraft(preference = false, value = it) },
                    onSave = { viewModel.saveAgentSettings(preference = false) },
                )
            }
        }
    }
}

@Composable
private fun FileEditor(
    title: String,
    file: AgentConfigFile?,
    draft: String,
    error: String,
    saving: Boolean,
    onChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    val palette = CoreHub.palette
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = palette.bgCard,
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                file?.language?.takeIf { it.isNotBlank() }?.let { language ->
                    Surface(shape = RoundedCornerShape(50), color = palette.bgSecondary) {
                        Text(
                            language,
                            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary,
                        )
                    }
                }
            }
            file?.path?.takeIf { it.isNotBlank() }?.let { path ->
                Text(
                    path,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = palette.textMuted,
                )
            }
            if (file != null && !file.exists) {
                Text(
                    stringResource(R.string.agent_settings_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.warning,
                )
            }

            if (error.isNotBlank()) {
                Text(error, style = MaterialTheme.typography.bodySmall, color = palette.error)
                return@Column
            }

            // A dotfile is technical text, not conversation: it reads
            // left to right whatever the interface language is, the way
            // `technicalInputProps` pins the web's editors.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onChange,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 360.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    singleLine = false,
                )
            }
            Button(
                onClick = onSave,
                enabled = !saving && file != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
}
