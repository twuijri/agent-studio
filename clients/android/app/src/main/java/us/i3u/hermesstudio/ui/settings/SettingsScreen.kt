package us.i3u.hermesstudio.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import us.i3u.hermesstudio.AboutSettings
import us.i3u.hermesstudio.AccountSettings
import us.i3u.hermesstudio.AppViewModel
import us.i3u.hermesstudio.CompressionStudioSettings
import us.i3u.hermesstudio.DeviceSettings
import us.i3u.hermesstudio.DisplayStudioSettings
import us.i3u.hermesstudio.ErrorNote
import us.i3u.hermesstudio.LoadingRow
import us.i3u.hermesstudio.ManagedUsersSettings
import us.i3u.hermesstudio.ModelProvidersSettings
import us.i3u.hermesstudio.NoticeNote
import us.i3u.hermesstudio.PrivacyStudioSettings
import us.i3u.hermesstudio.ProxyStudioSettings
import us.i3u.hermesstudio.R
import us.i3u.hermesstudio.SettingsGroup
import us.i3u.hermesstudio.StudioCardDivider
import us.i3u.hermesstudio.StudioDestinationRow
import us.i3u.hermesstudio.StudioGroupedCard
import us.i3u.hermesstudio.StudioHorizontalPadding
import us.i3u.hermesstudio.StudioSectionTitle
import us.i3u.hermesstudio.StudioTopBar
import us.i3u.hermesstudio.UiState
import us.i3u.hermesstudio.UpdateNote
import us.i3u.hermesstudio.WebhookEndpoint
import us.i3u.hermesstudio.isSuperAdmin
import us.i3u.hermesstudio.navigation.NavDestination
import us.i3u.hermesstudio.ui.theme.CoreHub

/** One tab of the Settings screen; the order is the web's (`SettingsView.vue:104-129`). */
private data class SettingsTab(val group: SettingsGroup, val label: Int, val superAdminOnly: Boolean = false)

private val SETTINGS_TABS = listOf(
    SettingsTab(SettingsGroup.Account, R.string.settings_tab_current_account),
    SettingsTab(SettingsGroup.Users, R.string.settings_tab_account_management, superAdminOnly = true),
    SettingsTab(SettingsGroup.Webhooks, R.string.settings_tab_webhooks, superAdminOnly = true),
    SettingsTab(SettingsGroup.Display, R.string.settings_tab_display),
    SettingsTab(SettingsGroup.Proxy, R.string.settings_tab_proxy),
    SettingsTab(SettingsGroup.Compression, R.string.settings_tab_compression),
    SettingsTab(SettingsGroup.Privacy, R.string.settings_tab_privacy),
    SettingsTab(SettingsGroup.Models, R.string.settings_tab_models),
    // Phone-only tabs after the web's: this device's own preferences and About.
    SettingsTab(SettingsGroup.Device, R.string.settings_tab_device),
    SettingsTab(SettingsGroup.About, R.string.settings_tab_about),
)

/**
 * The one Settings screen (NAVIGATION.md §2): the web's tabs in the web's
 * order, then `This device` and `About`, then the `Tools` section that mirrors
 * `AppSidebar.vue:113-317` — Logs, Usage, Performance, Skills Usage, Theme,
 * Profiles — each opening a different screen titled by the same key as
 * the row. There is no settings list in front of this and none behind it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: UiState, viewModel: AppViewModel) {
    val palette = CoreHub.palette
    val tabs = SETTINGS_TABS.filter { !it.superAdminOnly || state.isSuperAdmin }
    val selectedIndex = tabs.indexOfFirst { it.group == state.openGroup }.coerceAtLeast(0)
    val group = tabs[selectedIndex].group

    Scaffold(
        topBar = { StudioTopBar(title = stringResource(NavDestination.settings.labelKey), onBack = { viewModel.back() }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(
                selectedTabIndex = selectedIndex,
                edgePadding = 8.dp,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = palette.textPrimary,
                indicator = { positions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(positions[selectedIndex]),
                        color = palette.accent,
                    )
                },
                divider = {},
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = index == selectedIndex,
                        onClick = { viewModel.selectSettingsTab(tab.group) },
                        text = { Text(stringResource(tab.label), style = MaterialTheme.typography.labelLarge) },
                        selectedContentColor = palette.textPrimary,
                        unselectedContentColor = palette.textSecondary,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
            ) {
                if (state.savingSetting) LoadingRow()
                state.error?.let { ErrorNote(it) { viewModel.dismissError() } }
                state.notice?.let { NoticeNote(it) { viewModel.dismissNotice() } }
                // The manual check lives on the About tab, so its answer has
                // to be visible here too, not only above the composer.
                if (state.update.showNotice) UpdateNote(state.update, viewModel)
                val loading = state.loadingStudioSettings || state.loadingAccountSettings ||
                    state.loadingManagedUsers || state.loadingModelProviders || state.loadingWebhooks
                if (loading) {
                    LoadingRow()
                } else {
                    when (group) {
                        SettingsGroup.Account -> AccountSettings(state, viewModel)
                        SettingsGroup.Users -> ManagedUsersSettings(state, viewModel)
                        SettingsGroup.Webhooks -> WebhooksSettingsBody(state, viewModel)
                        SettingsGroup.Display -> DisplayStudioSettings(state, viewModel)
                        SettingsGroup.Proxy -> ProxyStudioSettings(state, viewModel)
                        SettingsGroup.Compression -> CompressionStudioSettings(state, viewModel)
                        SettingsGroup.Privacy -> PrivacyStudioSettings(state, viewModel)
                        SettingsGroup.Models -> ModelKeysTab(state, viewModel)
                        SettingsGroup.Device -> DeviceSettings(state, viewModel)
                        SettingsGroup.About -> AboutSettings(state, viewModel)
                        // Hermes-only groups live under the Hermes card, never here.
                        SettingsGroup.Agent, SettingsGroup.Memory, SettingsGroup.Sessions -> Unit
                    }
                }
                SettingsTools(state, viewModel)
            }
        }
    }
}

/**
 * Settings › Models is `ModelSettings.vue`: the key of each provider, and
 * nothing else. It says so, and points at the Models page, because the
 * drawer's Models item is a different screen with the same word on it.
 */
@Composable
private fun ModelKeysTab(state: UiState, viewModel: AppViewModel) {
    val palette = CoreHub.palette
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = StudioHorizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.settings_models_provider_keys),
            style = MaterialTheme.typography.titleSmall,
            color = palette.textSecondary,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { viewModel.openModels() }) { Text(stringResource(R.string.settings_open_models_page)) }
    }
    ModelProvidersSettings(state, viewModel)
}

/** Webhooks are a tab, not a screen (`Webhooks — تبويب في الإعدادات فقط`). */
@Composable
fun WebhooksSettingsBody(state: UiState, viewModel: AppViewModel) {
    val palette = CoreHub.palette
    var edit by remember { mutableStateOf<WebhookEndpoint?>(null) }
    var add by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    if (add || edit != null) {
        AlertDialog(
            onDismissRequest = { add = false; edit = null },
            title = { Text(stringResource(R.string.webhooks_add)) },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name)) })
                    OutlinedTextField(url, { url = it }, label = { Text("URL") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val item = edit
                    add = false
                    edit = null
                    if (item == null) viewModel.createWebhook(name, url) else viewModel.updateWebhook(item, name, url)
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { add = false; edit = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    Column(Modifier.fillMaxWidth().padding(StudioHorizontalPadding)) {
        Text(stringResource(R.string.webhooks_note), style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
            Button(onClick = { name = ""; url = ""; add = true }) { Text(stringResource(R.string.action_add)) }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = viewModel::clearWebhookEvents) { Text(stringResource(R.string.clear_events)) }
        }
        state.webhooks.forEach { hook ->
            StudioGroupedCard {
                Column(Modifier.padding(12.dp)) {
                    Text(hook.name, fontWeight = FontWeight.Bold)
                    Text(hook.url, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                    Text("${hook.state} · ✓${hook.delivered} · !${hook.failed}", style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(hook.enabled, { viewModel.toggleWebhook(hook) })
                        TextButton(onClick = { name = hook.name; url = hook.url; edit = hook }) { Text(stringResource(R.string.action_edit)) }
                        TextButton(onClick = { viewModel.testWebhook(hook) }) { Text(stringResource(R.string.action_test)) }
                        TextButton(onClick = { viewModel.deleteWebhook(hook) }) { Text(stringResource(R.string.action_delete)) }
                    }
                }
            }
            Spacer(Modifier.padding(4.dp))
        }
        if (state.webhookEvents.isNotEmpty()) {
            Text(stringResource(R.string.local_events), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            state.webhookEvents.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary) }
        }
    }
}

/**
 * The `Tools` section: `AppSidebar.vue:113-317` in order and by name. The
 * registry owns the order; this only draws it and gates the super-admin rows.
 */
@Composable
private fun SettingsTools(state: UiState, viewModel: AppViewModel) {
    val palette = CoreHub.palette
    val tools = NavDestination.settingsTools.filter { destination ->
        state.isSuperAdmin || destination !in SUPER_ADMIN_TOOLS
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = StudioHorizontalPadding, vertical = 12.dp)) {
        StudioSectionTitle(stringResource(R.string.settings_tools))
        StudioGroupedCard {
            tools.forEachIndexed { index, destination ->
                ToolRow(destination, toolIcon(destination), palette.accent) { viewModel.openTool(destination) }
                if (index != tools.lastIndex) StudioCardDivider()
            }
        }
    }
}

/** Performance and Profiles are super-admin routes on the web (`router/index.ts`). */
private val SUPER_ADMIN_TOOLS = setOf(NavDestination.performance, NavDestination.profiles)

@Composable
private fun ToolRow(destination: NavDestination, icon: ImageVector, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    StudioDestinationRow(icon = icon, color = color, title = stringResource(destination.labelKey), onClick = onClick)
}

private fun toolIcon(destination: NavDestination): ImageVector = when (destination) {
    NavDestination.logs -> Icons.AutoMirrored.Filled.Article
    NavDestination.usage -> Icons.Filled.BarChart
    NavDestination.performance -> Icons.Filled.Speed
    NavDestination.skillsUsage -> Icons.Filled.School
    NavDestination.theme -> Icons.Filled.Palette
    else -> Icons.Filled.Person
}
