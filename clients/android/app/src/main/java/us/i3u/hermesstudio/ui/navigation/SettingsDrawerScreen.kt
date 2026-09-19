package us.i3u.hermesstudio.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import us.i3u.hermesstudio.AppViewModel
import us.i3u.hermesstudio.ErrorNote
import us.i3u.hermesstudio.NoticeNote
import us.i3u.hermesstudio.R
import us.i3u.hermesstudio.StudioCardDivider
import us.i3u.hermesstudio.StudioDestinationRow
import us.i3u.hermesstudio.StudioGroupedCard
import us.i3u.hermesstudio.StudioHorizontalPadding
import us.i3u.hermesstudio.StudioTopBar
import us.i3u.hermesstudio.UiState
import us.i3u.hermesstudio.isSuperAdmin
import us.i3u.hermesstudio.ui.theme.CoreHub
import us.i3u.hermesstudio.ui.theme.CoreHubIcons

/**
 * The web's settings sidebar (AppSidebar) as a screen: Logs, Usage,
 * Performance (super-admin), Skills Usage, Theme, Pets, Profiles (super-admin),
 * Settings — in that order — then "Back" to the chat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDrawerScreen(state: UiState, viewModel: AppViewModel) {
    val palette = CoreHub.palette
    data class Entry(val icon: ImageVector, val label: Int, val superAdminOnly: Boolean = false, val open: () -> Unit)
    val entries = listOf(
        Entry(Icons.AutoMirrored.Filled.Article, R.string.settings_entry_logs) { viewModel.openLogs() },
        Entry(Icons.Filled.BarChart, R.string.settings_entry_usage) { viewModel.openInsights() },
        Entry(Icons.Filled.Speed, R.string.settings_entry_performance, superAdminOnly = true) { viewModel.openInsights() },
        Entry(Icons.Filled.School, R.string.settings_entry_skills_usage) { viewModel.openJourney() },
        Entry(Icons.Filled.Palette, R.string.settings_entry_theme) { viewModel.openAppearance() },
        Entry(Icons.Filled.Pets, R.string.settings_entry_pets) { viewModel.openPets() },
        Entry(Icons.Filled.Person, R.string.settings_entry_profiles, superAdminOnly = true) { viewModel.openProfiles() },
        Entry(CoreHubIcons.Settings, R.string.settings_entry_settings) { viewModel.openSettingsPage() },
    ).filter { !it.superAdminOnly || state.isSuperAdmin }

    Scaffold(
        topBar = { StudioTopBar(title = stringResource(R.string.settings_title), onBack = { viewModel.back() }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = StudioHorizontalPadding, end = StudioHorizontalPadding, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { message -> item { ErrorNote(message) { viewModel.dismissError() } } }
            state.notice?.let { message -> item { NoticeNote(message) { viewModel.dismissNotice() } } }
            item {
                StudioGroupedCard {
                    entries.forEachIndexed { index, entry ->
                        StudioDestinationRow(
                            icon = entry.icon,
                            color = palette.accent,
                            title = stringResource(entry.label),
                            onClick = entry.open,
                        )
                        if (index != entries.lastIndex) StudioCardDivider()
                    }
                }
            }
            item {
                TextButton(onClick = { viewModel.back() }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.drawer_back_to_chat), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
