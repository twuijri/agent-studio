package us.i3u.hermesstudio.ui.agents

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import us.i3u.hermesstudio.AppViewModel
import us.i3u.hermesstudio.EkkoMcpServer
import us.i3u.hermesstudio.EkkoMemory
import us.i3u.hermesstudio.ErrorNote
import us.i3u.hermesstudio.LoadingRow
import us.i3u.hermesstudio.NoticeNote
import us.i3u.hermesstudio.R
import us.i3u.hermesstudio.StudioGroupedCard
import us.i3u.hermesstudio.StudioHorizontalPadding
import us.i3u.hermesstudio.StudioTopBar
import us.i3u.hermesstudio.UiState
import us.i3u.hermesstudio.navigation.NavDestination
import us.i3u.hermesstudio.ui.theme.CoreHub

/**
 * Ekko's four sections (`EkkoConfigSidebar.vue:54-79`): Memory, Skills, MCP
 * and Settings, each a screen of its own under the Ekko card, titled by the
 * same key as the row that opened it. The first three share one snapshot,
 * refreshed in place by `reloadEkko`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EkkoScaffold(
    destination: NavDestination,
    state: UiState,
    viewModel: AppViewModel,
    onRefresh: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            StudioTopBar(
                title = stringResource(destination.labelKey),
                subtitle = stringResource(NavDestination.agentEkko.labelKey),
                onBack = { viewModel.back() },
                actions = { IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, stringResource(R.string.action_refresh)) } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(StudioHorizontalPadding, 8.dp, StudioHorizontalPadding, 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.loadingEkko || state.busy) item { LoadingRow() }
            state.error?.let { item { ErrorNote(it) { viewModel.dismissError() } } }
            state.notice?.let { item { NoticeNote(it) { viewModel.dismissNotice() } } }
            content()
        }
    }
}

@Composable
fun EkkoMemoryScreen(state: UiState, viewModel: AppViewModel) {
    var editMemory by remember { mutableStateOf<EkkoMemory?>(null) }
    editMemory?.let { memory -> EkkoMemoryDialog(memory, { editMemory = null }) { title, content -> editMemory = null; viewModel.saveEkkoMemory(memory, title, content) } }
    EkkoScaffold(NavDestination.memory, state, viewModel, viewModel::reloadEkko) {
        items(state.ekkoMemories, key = { it.id }) { memory ->
            HubCard {
                Column(Modifier.weight(1f)) {
                    Text(memory.title.ifBlank { memory.id }, fontWeight = FontWeight.Bold)
                    Text(memory.content, maxLines = 3, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content))
                    Text(memory.status + " · r" + memory.revision, style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = { editMemory = memory }) { Text(stringResource(R.string.action_edit)) }
                TextButton(onClick = { viewModel.deleteEkkoMemory(memory) }) { Text(stringResource(R.string.action_delete)) }
            }
        }
    }
}

@Composable
fun EkkoSkillsScreen(state: UiState, viewModel: AppViewModel) {
    var newSkill by remember { mutableStateOf(false) }
    var editDirectories by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val importSkill = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        viewModel.importEkkoSkill(bytes, uri.lastPathSegment ?: "skill.zip")
    }
    if (newSkill) EkkoSkillDialog({ newSkill = false }) { name, content -> newSkill = false; viewModel.saveEkkoSkill(name, content, true) }
    if (editDirectories) {
        var dirs by remember { mutableStateOf(state.ekkoExternalDirectories.joinToString("\n")) }
        AlertDialog(
            onDismissRequest = { editDirectories = false },
            title = { Text(stringResource(R.string.ekko_external_dirs)) },
            text = { OutlinedTextField(dirs, { dirs = it }, minLines = 6) },
            confirmButton = { TextButton(onClick = { editDirectories = false; viewModel.saveExternalDirectories(dirs) }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { editDirectories = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    state.ekkoOpenSkill?.let { skill ->
        var content by remember(skill.name, state.ekkoSkillContent) { mutableStateOf(state.ekkoSkillContent) }
        AlertDialog(
            onDismissRequest = viewModel::closeEkkoSkill,
            title = { Text(skill.name) },
            text = {
                Column {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        state.ekkoSkillFiles.forEach { path -> TextButton(onClick = { viewModel.openEkkoSkillFile(path) }) { Text(path.substringAfterLast('/')) } }
                    }
                    OutlinedTextField(content, { content = it }, minLines = 10)
                    state.ekkoSkillFilePreviewPath?.let { path ->
                        Text(path, style = MaterialTheme.typography.labelMedium)
                        Text(state.ekkoSkillFilePreviewContent, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.saveEkkoSkill(skill.name, content, false); viewModel.closeEkkoSkill() }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = viewModel::closeEkkoSkill) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    EkkoScaffold(NavDestination.skills, state, viewModel, viewModel::reloadEkko) {
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { editDirectories = true }) { Text(stringResource(R.string.ekko_external_dirs)) }
                TextButton(onClick = { newSkill = true }) { Text(stringResource(R.string.action_add)) }
                TextButton(onClick = { importSkill.launch("application/zip") }) { Text(stringResource(R.string.files_upload)) }
            }
        }
        state.ekkoSkills.flatMap { it.skills }.forEach { skill ->
            item(key = "skill-${skill.name}") {
                HubCard {
                    Column(Modifier.weight(1f).clickable { viewModel.openEkkoSkill(skill) }) {
                        Text(skill.name, fontWeight = FontWeight.Bold)
                        Text(skill.description, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(skill.enabled, { viewModel.toggleEkkoSkill(skill) })
                    TextButton(onClick = { viewModel.deleteEkkoSkill(skill) }) { Text(stringResource(R.string.action_delete)) }
                }
            }
        }
    }
}

@Composable
fun EkkoMcpScreen(state: UiState, viewModel: AppViewModel) {
    var editMcp by remember { mutableStateOf<EkkoMcpServer?>(null) }
    var newMcp by remember { mutableStateOf(false) }
    if (newMcp || editMcp != null) {
        val original = editMcp?.name
        EkkoMcpDialog(editMcp, { newMcp = false; editMcp = null }) { name, config -> newMcp = false; editMcp = null; viewModel.saveEkkoMcp(original, name, config) }
    }
    EkkoScaffold(NavDestination.mcp, state, viewModel, viewModel::reloadEkko) {
        item { Button(onClick = { newMcp = true }) { Text(stringResource(R.string.mcp_add)) } }
        items(state.ekkoMcpServers, key = { it.name }) { server ->
            HubCard {
                Column(Modifier.weight(1f)) {
                    Text(server.name, fontWeight = FontWeight.Bold)
                    Text(server.transport, style = MaterialTheme.typography.bodySmall)
                }
                Switch(server.enabled, { viewModel.toggleEkkoMcp(server) })
                TextButton(onClick = { viewModel.testEkkoMcp(server) }) { Text(stringResource(R.string.action_test)) }
                TextButton(onClick = { editMcp = server }) { Text(stringResource(R.string.action_edit)) }
                TextButton(onClick = { viewModel.deleteEkkoMcp(server) }) { Text(stringResource(R.string.action_delete)) }
            }
        }
    }
}

/** The web's six tabs (`ekko/SettingsView.vue:157-309`) over the sections of `editableConfig`. */
private data class EkkoTab(val label: Int, val sections: List<String>)

private val EKKO_TABS = listOf(
    EkkoTab(R.string.ekko_tab_runtime, listOf("runtime")),
    EkkoTab(R.string.ekko_tab_model, listOf("model")),
    EkkoTab(R.string.settings_tab_compression, listOf("compression")),
    EkkoTab(R.string.ekko_tab_tools, listOf("tools", "mcp", "skills")),
    EkkoTab(R.string.ekko_tab_modules, listOf("memory", "delegation")),
    EkkoTab(R.string.ekko_tab_advanced, listOf("logging", "prompt")),
)

/**
 * Ekko › Settings: `GET/PUT /api/ekko/config`. Each tab shows its sections as
 * JSON to edit and save one at a time; the server merges the section into
 * the rest of the file (`updateEkkoSettings`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EkkoSettingsScreen(state: UiState, viewModel: AppViewModel) {
    val palette = CoreHub.palette
    var selected by remember { mutableIntStateOf(0) }
    val config = state.ekkoConfig
    // Sections the tabs above do not name still have to be reachable.
    val known = EKKO_TABS.flatMap { it.sections }.toSet()
    val extra = config?.config?.keys()?.asSequence()?.filter { it !in known }?.toList().orEmpty()
    val tabs = if (extra.isEmpty()) EKKO_TABS else EKKO_TABS.dropLast(1) + EKKO_TABS.last().copy(sections = EKKO_TABS.last().sections + extra)

    Scaffold(
        topBar = {
            StudioTopBar(
                title = stringResource(NavDestination.ekkoSettings.labelKey),
                subtitle = stringResource(NavDestination.agentEkko.labelKey),
                onBack = { viewModel.back() },
                actions = { IconButton(onClick = viewModel::openEkkoSettings) { Icon(Icons.Filled.Refresh, stringResource(R.string.action_refresh)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(
                selectedTabIndex = selected,
                edgePadding = 8.dp,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = palette.textPrimary,
                indicator = { positions -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[selected]), color = palette.accent) },
                divider = {},
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = index == selected,
                        onClick = { selected = index },
                        text = { Text(stringResource(tab.label), style = MaterialTheme.typography.labelLarge) },
                        selectedContentColor = palette.textPrimary,
                        unselectedContentColor = palette.textSecondary,
                    )
                }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(StudioHorizontalPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.loadingEkkoConfig || state.busy) LoadingRow()
                state.error?.let { ErrorNote(it) { viewModel.dismissError() } }
                state.notice?.let { NoticeNote(it) { viewModel.dismissNotice() } }
                Text(stringResource(R.string.ekko_config_note), style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                config?.configPath?.takeIf { it.isNotBlank() }?.let {
                    Text(stringResource(R.string.ekko_config_path, it), style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr), color = palette.textMuted)
                }
                tabs[selected].sections.forEach { section ->
                    val current = config?.config?.optJSONObject(section)?.toString(2) ?: config?.config?.opt(section)?.toString().orEmpty()
                    var draft by remember(section, current) { mutableStateOf(current) }
                    StudioGroupedCard {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(section, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                draft,
                                { draft = it },
                                Modifier.fillMaxWidth(),
                                minLines = 4,
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr),
                            )
                            Row {
                                TextButton(onClick = { viewModel.saveEkkoConfigSection(section, draft) }, enabled = draft != current && runCatching { JSONObject(draft) }.isSuccess) {
                                    Text(stringResource(R.string.action_save))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HubCard(content: @Composable RowScope.() -> Unit) {
    StudioGroupedCard {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
private fun EkkoMemoryDialog(memory: EkkoMemory, dismiss: () -> Unit, save: (String, String) -> Unit) {
    var title by remember { mutableStateOf(memory.title) }
    var content by remember { mutableStateOf(memory.content) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.ekko_memory_edit)) },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.ekko_memory_name)) })
                OutlinedTextField(content, { content = it }, label = { Text(stringResource(R.string.ekko_memory_content)) }, minLines = 5)
            }
        },
        confirmButton = { TextButton(onClick = { save(title, content) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun EkkoMcpDialog(server: EkkoMcpServer?, dismiss: () -> Unit, save: (String, String) -> Unit) {
    var name by remember { mutableStateOf(server?.name.orEmpty()) }
    var config by remember { mutableStateOf(server?.config ?: "{\n  \"transport\": \"stdio\",\n  \"command\": \"\"\n}") }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.ekko_mcp_edit)) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.mcp_name)) })
                OutlinedTextField(config, { config = it }, label = { Text(stringResource(R.string.mcp_advanced_json)) }, minLines = 7)
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { save(name, config) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun EkkoSkillDialog(dismiss: () -> Unit, save: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("---\nname: \ndescription: \n---\n") }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.ekko_skill_new)) },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.mcp_name)) })
                OutlinedTextField(content, { content = it }, minLines = 8)
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { save(name, content) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
