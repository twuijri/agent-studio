package us.i3u.hermesstudio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import us.i3u.hermesstudio.navigation.NavDestination
import us.i3u.hermesstudio.ui.theme.CoreHub
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ParityScaffold(title: String, vm: AppViewModel, content: @Composable ColumnScope.() -> Unit) = Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(vm::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } }) }) { pad -> Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }

/** Hermes › Journey (`JourneyView.vue`): the skill and memory graph, and only that. */
@Composable fun JourneyScreen(state: UiState, vm: AppViewModel) = ParityScaffold(stringResource(NavDestination.journey.labelKey), vm) {
    val graph = state.journey
    if (state.loadingJourney) LoadingRow()
    state.error?.let { ErrorNote(it) { vm.dismissError() } }
    Text(stringResource(R.string.journey_summary, graph?.nodes?.size ?: 0, graph?.edges?.size ?: 0), fontWeight = FontWeight.Bold)
    if (graph == null || graph.nodes.isEmpty()) Text(stringResource(R.string.journey_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (graph != null && graph.clusters.isNotEmpty()) item { Text(stringResource(R.string.journey_clusters), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
        graph?.clusters?.let { items(it) { cluster -> Text("${cluster.first}: ${cluster.second}", style = MaterialTheme.typography.bodyMedium) } }
        graph?.nodes?.let { items(it, key = { n -> n.id }) { node -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(node.label, fontWeight = FontWeight.Bold); Text("${node.kind} · ${node.category} · ${node.uses}") } } } }
    }
}



/** Settings › Tools › Theme (`ThemeView.vue`): the server-side theme and background. */
@Composable fun ThemeScreen(state: UiState, vm: AppViewModel) { val source=state.themeSettings; val context=LocalContext.current;val upload=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->uri?:return@rememberLauncherForActivityResult;val bytes=context.contentResolver.openInputStream(uri)?.use{it.readBytes()}?:return@rememberLauncherForActivityResult;vm.uploadThemeBackground(bytes,uri.lastPathSegment?:"background",context.contentResolver.getType(uri)?:"image/jpeg")};var size by remember(source){mutableStateOf((source?.fontSize?:16).toString())};var text by remember(source){mutableStateOf(source?.textColor.orEmpty())};var accent by remember(source){mutableStateOf(source?.accentColor.orEmpty())};ParityScaffold(stringResource(NavDestination.theme.labelKey),vm){OutlinedTextField(size,{size=it.filter(Char::isDigit)},label={Text(stringResource(R.string.font_size))});OutlinedTextField(text,{text=it},label={Text(stringResource(R.string.text_color))});OutlinedTextField(accent,{accent=it},label={Text(stringResource(R.string.accent_color))});Button(onClick={vm.saveTheme(size.toIntOrNull()?:16,text,accent)}){Text(stringResource(R.string.action_save))};TextButton(onClick={upload.launch("image/*")}){Text(stringResource(R.string.upload_background))};if(!source?.backgroundName.isNullOrBlank()){Text(source?.backgroundName.orEmpty());TextButton(onClick=vm::removeThemeBackground){Text(stringResource(R.string.remove_background))}}} }
