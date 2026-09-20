package us.i3u.hermesstudio.ui.agents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import us.i3u.hermesstudio.AppViewModel
import us.i3u.hermesstudio.DshAgentPreset
import us.i3u.hermesstudio.DshPluginEntry
import us.i3u.hermesstudio.DshPluginInventory
import us.i3u.hermesstudio.DshPluginPreset
import us.i3u.hermesstudio.DshPluginState
import us.i3u.hermesstudio.DshWebPackage
import us.i3u.hermesstudio.EmptyToolState
import us.i3u.hermesstudio.ErrorNote
import us.i3u.hermesstudio.LoadingRow
import us.i3u.hermesstudio.R
import us.i3u.hermesstudio.StudioCardDivider
import us.i3u.hermesstudio.StudioGroupedCard
import us.i3u.hermesstudio.StudioHorizontalPadding
import us.i3u.hermesstudio.StudioSectionTitle
import us.i3u.hermesstudio.StudioTopBar
import us.i3u.hermesstudio.UiState
import us.i3u.hermesstudio.navigation.NavDestination
import us.i3u.hermesstudio.ui.theme.CoreHub

/*
 * The two screens only the DeepSeek Harness (dsh) card lists
 * (`CodingAgentConfigSidebar.vue:19-24`, rendered by
 * `CodingAgentConfigView.vue:139-140`). Both are titled by the registry key
 * of the row that opens them. What the phone cannot do is written on the
 * screen, not hidden: installing web packages and the plugin settings page
 * (`DshPluginSettingsPanel.vue`, an iframe over a UI session) need the
 * desktop; so do copying and deleting a preset.
 */

/** dsh › Plugins (`DshPluginsPanel.vue` › `DshNativePluginsPanel.vue`): the inventory, read-only. */
@Composable
fun DshPluginsScreen(state: UiState, viewModel: AppViewModel) {
    val ui = state.dshUi
    val inventory = ui.inventory
    Scaffold(
        topBar = {
            StudioTopBar(
                stringResource(NavDestination.plugins.labelKey),
                inventory?.let { stringResource(R.string.dsh_plugins_summary, it.presets.size, it.webPackages.size) },
                onBack = { viewModel.back() },
                actions = {
                    IconButton(onClick = viewModel::refreshDshPlugins, enabled = !ui.loading) {
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
            if (ui.loading) item { LoadingRow() }
            item { Text(stringResource(R.string.dsh_plugins_note), style = MaterialTheme.typography.bodySmall, color = CoreHub.palette.textSecondary) }
            if (!ui.loading && inventory != null && inventory.presets.isEmpty() && inventory.webPackages.isEmpty()) {
                item { EmptyToolState(Icons.Filled.Extension, stringResource(R.string.dsh_plugins_empty), stringResource(R.string.dsh_plugins_empty_note)) }
            }
            inventory?.presets?.forEach { preset -> dshPluginPreset(preset) }
            if (inventory != null) dshWebPackages(inventory)
        }
    }
}

private fun LazyListScope.dshPluginPreset(preset: DshPluginPreset) {
    item(key = "preset-${preset.id}") {
        Column {
            StudioSectionTitle(preset.name)
            Text(
                stringResource(R.string.dsh_plugins_entries, preset.entries.size, preset.entries.count { it.state == DshPluginState.Enabled }) +
                    if (preset.isDefault) " · " + stringResource(R.string.dsh_presets_default) else "",
                style = MaterialTheme.typography.bodySmall,
                color = CoreHub.palette.textMuted,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            if (preset.error.isNotBlank()) {
                Text(preset.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(6.dp))
            }
        }
    }
    item(key = "entries-${preset.id}") {
        StudioGroupedCard {
            preset.entries.forEachIndexed { index, entry ->
                DshPluginEntryRow(entry)
                if (index != preset.entries.lastIndex) StudioCardDivider(startIndent = 12)
            }
        }
    }
}

@Composable
private fun DshPluginEntryRow(entry: DshPluginEntry) {
    val palette = CoreHub.palette
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entry.title.ifBlank { entry.moduleName }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(entry.moduleName, style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr), color = palette.textMuted)
            if (entry.description.isNotBlank()) {
                Text(entry.description, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary, maxLines = 2)
            }
        }
        Spacer(Modifier.width(8.dp))
        val (label, color) = when (entry.state) {
            DshPluginState.Enabled -> R.string.dsh_plugin_enabled to palette.success
            DshPluginState.Disabled -> R.string.dsh_plugin_disabled to palette.textMuted
            DshPluginState.Conditional -> R.string.dsh_plugin_conditional to palette.accent
        }
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

private fun LazyListScope.dshWebPackages(inventory: DshPluginInventory) {
    item(key = "web-title") { StudioSectionTitle(stringResource(R.string.dsh_plugins_web_packages)) }
    if (inventory.webPackages.isNotEmpty()) {
        item(key = "web-packages") {
            StudioGroupedCard {
                inventory.webPackages.forEachIndexed { index, pkg ->
                    DshWebPackageRow(pkg)
                    if (index != inventory.webPackages.lastIndex) StudioCardDivider(startIndent = 12)
                }
            }
        }
    }
    item(key = "web-note") {
        Text(stringResource(R.string.dsh_plugins_web_note), style = MaterialTheme.typography.bodySmall, color = CoreHub.palette.textSecondary, modifier = Modifier.padding(horizontal = 6.dp))
    }
}

@Composable
private fun DshWebPackageRow(pkg: DshWebPackage) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(pkg.title.ifBlank { pkg.name }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Text("${pkg.name} ${pkg.version}".trim(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr), color = CoreHub.palette.textMuted)
        if (pkg.description.isNotBlank()) Text(pkg.description, style = MaterialTheme.typography.bodySmall, color = CoreHub.palette.textSecondary, maxLines = 2)
        if (pkg.error.isNotBlank()) Text(pkg.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

/** dsh › Presets (`DshAgentPresetsPanel.vue`): list, view a file, choose the default. */
@Composable
fun DshPresetsScreen(state: UiState, viewModel: AppViewModel) {
    val ui = state.dshUi
    val active = ui.presets.firstOrNull { it.isDefault }
    Scaffold(
        topBar = {
            StudioTopBar(
                stringResource(NavDestination.presets.labelKey),
                active?.let { stringResource(R.string.dsh_presets_active_summary, it.name) },
                onBack = { viewModel.back() },
                actions = {
                    IconButton(onClick = viewModel::refreshDshPresets, enabled = !ui.loading) {
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
            if (ui.loading) item { LoadingRow() }
            item { Text(stringResource(R.string.dsh_presets_note), style = MaterialTheme.typography.bodySmall, color = CoreHub.palette.textSecondary) }
            if (!ui.loading && ui.presets.isEmpty()) {
                item { EmptyToolState(Icons.Filled.Tune, stringResource(R.string.dsh_presets_empty), stringResource(R.string.dsh_presets_empty_note)) }
            }
            items(ui.presets.size, key = { ui.presets[it].id }) { index ->
                DshPresetCard(ui.presets[index], busy = ui.actionId != null, viewModel)
            }
        }
    }
    ui.viewer?.let { viewer -> DshPresetViewerDialog(viewer.title, viewer.content, onDismiss = viewModel::closeDshPresetViewer) }
}

@Composable
private fun DshPresetCard(preset: DshAgentPreset, busy: Boolean, viewModel: AppViewModel) {
    val palette = CoreHub.palette
    StudioGroupedCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(preset.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(if (preset.trust == "user") R.string.dsh_presets_trust_user else R.string.dsh_presets_trust_system),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textMuted,
                )
            }
            Text(preset.id, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr), color = palette.textMuted)
            if (preset.description.isNotBlank()) Text(preset.description, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
            if (preset.broken.isNotBlank()) Text(preset.broken, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (preset.isDefault) {
                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.dsh_presets_default)) })
                } else if (preset.broken.isBlank()) {
                    TextButton(onClick = { viewModel.selectDshPreset(preset) }, enabled = !busy) { Text(stringResource(R.string.dsh_presets_use_default)) }
                }
                TextButton(onClick = { viewModel.viewDshPreset(preset) }, enabled = !busy) { Text(stringResource(R.string.dsh_presets_view)) }
            }
        }
    }
}

@Composable
private fun DshPresetViewerDialog(title: String, content: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                content.ifBlank { "—" },
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_dismiss)) } },
    )
}
