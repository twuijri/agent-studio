package us.i3u.hermesstudio.ui.models

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import us.i3u.hermesstudio.AppViewModel
import us.i3u.hermesstudio.ConfirmDialog
import us.i3u.hermesstudio.ErrorNote
import us.i3u.hermesstudio.FallbackEntry
import us.i3u.hermesstudio.LoadingRow
import us.i3u.hermesstudio.ModelEntry
import us.i3u.hermesstudio.ModelProvider
import us.i3u.hermesstudio.NoticeNote
import us.i3u.hermesstudio.R
import us.i3u.hermesstudio.StudioHorizontalPadding
import us.i3u.hermesstudio.TextPromptDialog
import us.i3u.hermesstudio.UiState
import us.i3u.hermesstudio.canManageProviders
import us.i3u.hermesstudio.ui.theme.CoreHub

/**
 * The Models page, built around the same information architecture as the web's
 * `ModelsView.vue`: a list of service providers, and under each provider the
 * models it offers.
 *
 * This is deliberately *not* the Settings › Models tab. On the web those are
 * two different pages — `components/hermes/settings/ModelSettings.vue` is a
 * column of API-key fields, while `views/hermes/ModelsView.vue` is the
 * provider grid — and the phone now keeps that split too: key management stays
 * in Settings, and this page shows state, catalogue and actions.
 *
 * Adapted for a phone: the web's `auto-fill` grid of 420 px cards becomes one
 * column, the per-model row becomes a chip, and the modals become sheets. The
 * web's General and Fallback tabs are both here; see the README for the tabs
 * that are not.
 */
private enum class ModelsTab { General, Fallback }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(state: UiState, viewModel: AppViewModel) {
    val palette = CoreHub.palette
    var tab by remember { mutableStateOf(ModelsTab.General) }
    var addingProvider by remember { mutableStateOf(false) }

    if (addingProvider) {
        AddProviderSheet(
            onDismiss = { addingProvider = false },
            onAdd = { name, baseUrl, key, apiMode ->
                viewModel.addCustomProvider(name, baseUrl, key, apiMode)
                addingProvider = false
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_models)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshModelCache() }) {
                        Icon(Icons.Filled.Refresh, stringResource(R.string.models_refresh_cache))
                    }
                    IconButton(onClick = { addingProvider = true }) {
                        Icon(Icons.Filled.Add, stringResource(R.string.models_add_provider))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val tabs = ModelsTab.entries
            val selected = tabs.indexOf(tab)
            ScrollableTabRow(
                selectedTabIndex = selected,
                edgePadding = 8.dp,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = palette.textPrimary,
                indicator = { positions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(positions[selected]),
                        color = palette.accent,
                    )
                },
                divider = {},
            ) {
                tabs.forEach { option ->
                    Tab(
                        selected = option == tab,
                        onClick = { tab = option },
                        text = {
                            Text(
                                stringResource(
                                    when (option) {
                                        ModelsTab.General -> R.string.models_tab_general
                                        ModelsTab.Fallback -> R.string.models_tab_fallback
                                    },
                                ),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                        selectedContentColor = palette.textPrimary,
                        unselectedContentColor = palette.textSecondary,
                    )
                }
            }
            when (tab) {
                ModelsTab.General -> ProvidersPanel(state, viewModel)
                ModelsTab.Fallback -> FallbackPanel(state, viewModel)
            }
        }
    }
}

/** The web's `ProvidersPanel.vue`: every configured provider, or an empty state. */
@Composable
private fun ProvidersPanel(state: UiState, viewModel: AppViewModel) {
    var removing by remember { mutableStateOf<ModelProvider?>(null) }

    removing?.let { provider ->
        ConfirmDialog(
            title = stringResource(
                if (provider.deletable) R.string.models_remove_provider else R.string.models_clear_credentials,
            ),
            body = stringResource(
                if (provider.deletable) R.string.models_remove_provider_confirm
                else R.string.models_clear_credentials_confirm,
                provider.label,
            ),
            action = stringResource(
                if (provider.deletable) R.string.action_remove else R.string.models_clear_credentials,
            ),
            onConfirm = { viewModel.removeProvider(provider); removing = null },
            onDismiss = { removing = null },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(StudioHorizontalPadding, 8.dp, StudioHorizontalPadding, 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.error?.let { item { ErrorNote(it) { viewModel.dismissError() } } }
        state.notice?.let { item { NoticeNote(it) { viewModel.dismissNotice() } } }
        if (state.loadingModelProviders || state.savingSetting) item { LoadingRow() }

        if (!state.loadingModelProviders && state.modelProviders.isEmpty()) {
            item { EmptyProviders() }
        }

        items(state.modelProviders, key = { it.id }) { provider ->
            ProviderCard(provider, state, viewModel, onRemove = { removing = provider })
        }
    }
}

@Composable
private fun EmptyProviders() {
    val palette = CoreHub.palette
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.models_no_providers),
            style = MaterialTheme.typography.titleMedium,
            color = palette.textSecondary,
        )
        Text(
            stringResource(R.string.models_no_providers_note),
            style = MaterialTheme.typography.bodySmall,
            color = palette.textMuted,
        )
    }
}

/**
 * One provider, laid out like the web's `ProviderCard.vue`: the name and its
 * badges, the identity rows, the default model, the model catalogue as chips,
 * then the actions.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ProviderCard(
    provider: ModelProvider,
    state: UiState,
    viewModel: AppViewModel,
    onRemove: () -> Unit,
) {
    val palette = CoreHub.palette
    val catalog = state.modelCatalog
    val isDefaultProvider = catalog?.defaultProvider == provider.id
    val defaultModel = catalog?.defaultModel.orEmpty()
    val visible = provider.models.filter { it.visible }
    val filtered = visible.size != provider.models.size

    var showAllModels by remember(provider.id) { mutableStateOf(false) }
    var aliasFor by remember(provider.id) { mutableStateOf<ModelEntry?>(null) }
    var pickingDefault by remember(provider.id) { mutableStateOf(false) }
    var managingVisibility by remember(provider.id) { mutableStateOf(false) }

    aliasFor?.let { entry ->
        TextPromptDialog(
            title = stringResource(R.string.model_alias),
            initial = entry.alias,
            hint = stringResource(R.string.model_alias_hint),
            action = stringResource(R.string.action_save),
            onConfirm = { viewModel.setModelAlias(provider.id, entry.id, it); aliasFor = null },
            onDismiss = { aliasFor = null },
        )
    }
    if (pickingDefault) {
        ModelPickerSheet(
            title = stringResource(R.string.models_default_model),
            models = visible,
            selected = defaultModel.takeIf { isDefaultProvider },
            onDismiss = { pickingDefault = false },
            onPick = { viewModel.setDefaultModelFromCatalog(provider.id, it.id); pickingDefault = false },
        )
    }
    if (managingVisibility) {
        VisibilitySheet(
            provider = provider,
            onDismiss = { managingVisibility = false },
            onToggle = { entry, wanted -> viewModel.setModelVisible(provider, entry.id, wanted) },
            onShowAll = { viewModel.showAllProviderModels(provider); managingVisibility = false },
        )
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = palette.bgCard,
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    provider.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isDefaultProvider) Badge(stringResource(R.string.models_current_default), palette.accent)
                Spacer(Modifier.width(6.dp))
                Badge(
                    stringResource(if (provider.builtin) R.string.models_builtin else R.string.models_custom),
                    palette.textSecondary,
                )
            }

            // The one status a provider actually reports, and only opencode-free
            // reports it: the web polls this until the catalogue is ready.
            when (provider.catalogStatus) {
                "loading" -> CardNote(stringResource(R.string.models_catalog_loading), palette.info)
                "error" -> CardNote(stringResource(R.string.models_catalog_error), palette.error)
                "unsupported" -> CardNote(stringResource(R.string.models_catalog_unsupported), palette.warning)
            }

            InfoRow(stringResource(R.string.models_provider_id), provider.id)
            if (provider.baseUrl.isNotBlank()) InfoRow(stringResource(R.string.models_base_url), provider.baseUrl)
            if (provider.apiMode.isNotBlank()) InfoRow(stringResource(R.string.models_api_mode), provider.apiMode)
            InfoRow(
                stringResource(R.string.models_models),
                if (filtered) "${visible.size}/${provider.models.size}" else "${provider.models.size}",
                mono = false,
            )
            InfoRow(
                stringResource(R.string.models_credential),
                stringResource(
                    if (provider.configured) R.string.models_credential_set else R.string.models_credential_oauth,
                ),
                mono = false,
            )

            Row(
                Modifier.fillMaxWidth().clickable { pickingDefault = true }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.models_default_model),
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.textSecondary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (isDefaultProvider && defaultModel.isNotBlank()) defaultModel
                    else stringResource(R.string.models_select_model),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDefaultProvider && defaultModel.isNotBlank()) palette.textPrimary else palette.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // The catalogue. The web previews the first 20 and counts the rest;
            // a phone shows fewer before the fold but keeps the same idea.
            val preview = if (showAllModels) visible else visible.take(PREVIEW_MODELS)
            if (visible.isEmpty()) {
                CardNote(stringResource(R.string.models_provider_no_models), palette.textMuted)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    preview.forEach { entry ->
                        ModelChip(
                            entry = entry,
                            isDefault = isDefaultProvider && entry.id == defaultModel,
                            onClick = { aliasFor = entry },
                        )
                    }
                    val hidden = visible.size - preview.size
                    if (hidden > 0) {
                        TextButton(onClick = { showAllModels = true }) {
                            Text(stringResource(R.string.models_more, hidden))
                        }
                    } else if (showAllModels && visible.size > PREVIEW_MODELS) {
                        TextButton(onClick = { showAllModels = false }) {
                            Text(stringResource(R.string.models_show_less))
                        }
                    }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(
                    onClick = { viewModel.setDefaultProvider(provider) },
                    enabled = !isDefaultProvider && visible.isNotEmpty() && !state.savingSetting,
                ) { Text(stringResource(R.string.models_set_default_provider)) }
                TextButton(onClick = { managingVisibility = true }) {
                    Text(stringResource(R.string.models_manage_visible))
                }
                if (provider.refreshable && state.canManageProviders) {
                    TextButton(onClick = { viewModel.refreshProviderModels(provider.id) }) {
                        Text(stringResource(R.string.models_refresh))
                    }
                }
                if (provider.restoreAvailable && state.canManageProviders) {
                    TextButton(onClick = { viewModel.restoreProviderModels(provider.id) }) {
                        Text(stringResource(R.string.models_restore))
                    }
                }
                TextButton(onClick = { viewModel.testProvider(provider.id) }) {
                    Text(stringResource(R.string.action_test))
                }
                if (state.canManageProviders) TextButton(onClick = onRemove) {
                    Text(
                        stringResource(
                            if (provider.deletable) R.string.models_remove_provider
                            else R.string.models_clear_credentials,
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

private const val PREVIEW_MODELS = 12

@Composable
private fun ModelChip(entry: ModelEntry, isDefault: Boolean, onClick: () -> Unit) {
    val palette = CoreHub.palette
    Surface(
        shape = RoundedCornerShape(50),
        color = if (isDefault) palette.selected else palette.bgSecondary,
        border = BorderStroke(
            1.dp,
            if (isDefault) palette.accent else palette.borderLight,
        ),
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 2.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column {
                Text(
                    entry.alias.ifBlank { entry.id },
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // The web shows the real id under an alias so the chip never
                // hides which model is actually being called.
                if (entry.alias.isNotBlank()) {
                    Text(
                        entry.id,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = palette.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (isDefault) {
                Text(
                    stringResource(R.string.models_default_short),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.accent,
                )
            }
            if (entry.custom) {
                Text(
                    stringResource(R.string.models_custom_short),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textMuted,
                )
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f)) {
        Text(
            text,
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun CardNote(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = true) {
    val palette = CoreHub.palette
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = palette.textSecondary,
            modifier = Modifier.width(96.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            color = palette.textPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The web's `ModelPickerModal.vue`, as a sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(
    title: String,
    models: List<ModelEntry>,
    selected: String?,
    onDismiss: () -> Unit,
    onPick: (ModelEntry) -> Unit,
) {
    val palette = CoreHub.palette
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Text(
            title,
            Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        LazyColumn(Modifier.fillMaxWidth().height(380.dp)) {
            items(models, key = { it.id }) { entry ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(entry) }
                        .background(if (entry.id == selected) palette.selected else palette.bgPrimary)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.alias.ifBlank { entry.id }, style = MaterialTheme.typography.bodyMedium)
                        if (entry.alias.isNotBlank()) {
                            Text(
                                entry.id,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = palette.textMuted,
                            )
                        }
                    }
                    if (entry.disabled) {
                        Badge(stringResource(R.string.models_model_unavailable), palette.warning)
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** The web's "manage visible models" modal, as a sheet of switches. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisibilitySheet(
    provider: ModelProvider,
    onDismiss: () -> Unit,
    onToggle: (ModelEntry, Boolean) -> Unit,
    onShowAll: () -> Unit,
) {
    val palette = CoreHub.palette
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Text(
            stringResource(R.string.models_manage_visible),
            Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            stringResource(R.string.models_manage_visible_note),
            Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
        )
        LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
            items(provider.models, key = { it.id }) { entry ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.alias.ifBlank { entry.id }, style = MaterialTheme.typography.bodyMedium)
                        if (entry.alias.isNotBlank()) {
                            Text(
                                entry.id,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = palette.textMuted,
                            )
                        }
                    }
                    Switch(
                        checked = entry.visible,
                        onCheckedChange = { onToggle(entry, it) },
                    )
                }
            }
        }
        TextButton(
            onClick = onShowAll,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        ) { Text(stringResource(R.string.models_show_all)) }
        Spacer(Modifier.height(20.dp))
    }
}

/**
 * The web's `FallbackProvidersPanel.vue`: an ordered chain Hermes walks when
 * the chosen model fails. Reordering is by button rather than by drag, which
 * is what the web offers the keyboard anyway.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FallbackPanel(state: UiState, viewModel: AppViewModel) {
    val palette = CoreHub.palette
    var adding by remember { mutableStateOf(false) }
    val dirty = state.fallbackChain != state.savedFallbackChain

    if (adding) {
        FallbackPickerSheet(
            state = state,
            onDismiss = { adding = false },
            onPick = { provider, model -> viewModel.addFallbackEntry(provider, model); adding = false },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(StudioHorizontalPadding, 8.dp, StudioHorizontalPadding, 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.error?.let { item { ErrorNote(it) { viewModel.dismissError() } } }
        state.notice?.let { item { NoticeNote(it) { viewModel.dismissNotice() } } }
        if (state.loadingFallbackChain) item { LoadingRow() }

        item {
            Text(
                stringResource(R.string.models_fallback_note),
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
            )
        }

        if (state.fallbackChain.isEmpty() && !state.loadingFallbackChain) {
            item {
                Text(
                    stringResource(R.string.models_fallback_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textMuted,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }

        itemsIndexed(state.fallbackChain, key = { _, entry -> "${entry.provider}/${entry.model}" }) { index, entry ->
            FallbackRow(
                index = index,
                entry = entry,
                label = state.modelProviders.firstOrNull { it.id == entry.provider }?.label ?: entry.provider,
                last = index == state.fallbackChain.lastIndex,
                onUp = { viewModel.moveFallbackEntry(index, index - 1) },
                onDown = { viewModel.moveFallbackEntry(index, index + 1) },
                onRemove = { viewModel.removeFallbackEntry(index) },
            )
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { adding = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.models_fallback_add))
                }
                Button(
                    onClick = { viewModel.saveFallbackChain() },
                    enabled = dirty && !state.savingSetting,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}

@Composable
private fun FallbackRow(
    index: Int,
    entry: FallbackEntry,
    label: String,
    last: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val palette = CoreHub.palette
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = palette.bgCard,
        border = BorderStroke(1.dp, palette.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(24.dp).background(palette.selected, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) {
                Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = palette.accent)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.model, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textMuted)
            }
            IconButton(onClick = onUp, enabled = index > 0) {
                Icon(Icons.Filled.ArrowUpward, stringResource(R.string.models_fallback_move_up), Modifier.size(18.dp))
            }
            IconButton(onClick = onDown, enabled = !last) {
                Icon(Icons.Filled.ArrowDownward, stringResource(R.string.models_fallback_move_down), Modifier.size(18.dp))
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, stringResource(R.string.action_remove), Modifier.size(18.dp))
            }
        }
    }
}

/** Every visible model of every provider, so a fallback link can be chosen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FallbackPickerSheet(
    state: UiState,
    onDismiss: () -> Unit,
    onPick: (String, String) -> Unit,
) {
    val palette = CoreHub.palette
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Text(
            stringResource(R.string.models_fallback_add),
            Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
            state.modelProviders.forEach { provider ->
                val visible = provider.models.filter { it.visible }
                if (visible.isEmpty()) return@forEach
                item(key = "header-${provider.id}") {
                    Text(
                        provider.label,
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.textSecondary,
                    )
                }
                items(visible, key = { "${provider.id}/${it.id}" }) { entry ->
                    val chosen = state.fallbackChain.any { it.provider == provider.id && it.model == entry.id }
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = !chosen) { onPick(provider.id, entry.id) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            entry.alias.ifBlank { entry.id },
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (chosen) palette.textMuted else palette.textPrimary,
                        )
                        if (chosen) {
                            Text(
                                stringResource(R.string.models_fallback_already),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textMuted,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** The web's `ProviderFormModal.vue`, trimmed to what a phone can usefully ask. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddProviderSheet(
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var apiMode by remember { mutableStateOf("chat_completions") }
    val modes = listOf("chat_completions", "codex_responses", "anthropic_messages")

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(R.string.models_add_provider),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.models_provider_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text(stringResource(R.string.models_base_url)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text(stringResource(R.string.models_api_key)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.models_api_mode),
                style = MaterialTheme.typography.labelMedium,
                color = CoreHub.palette.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                modes.forEach { mode ->
                    FilterChip(
                        selected = apiMode == mode,
                        onClick = { apiMode = mode },
                        label = { Text(mode, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
            Button(
                onClick = { onAdd(name, baseUrl, key, apiMode) },
                enabled = name.isNotBlank() && baseUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_add)) }
        }
    }
}
