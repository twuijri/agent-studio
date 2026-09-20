package us.i3u.hermesstudio.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import us.i3u.hermesstudio.AppViewModel
import us.i3u.hermesstudio.LoadingRow
import us.i3u.hermesstudio.R
import us.i3u.hermesstudio.SessionSummary
import us.i3u.hermesstudio.UiState
import us.i3u.hermesstudio.navigation.NavDestination
import us.i3u.hermesstudio.ui.sessions.formatStamp
import us.i3u.hermesstudio.ui.theme.CoreHub
import us.i3u.hermesstudio.ui.theme.CoreHubIcons
import us.i3u.hermesstudio.ui.theme.CoreHubTextStyles
import us.i3u.hermesstudio.ui.theme.CoreHubTokens

/**
 * The drawer's `Search`: the web's `SessionSearchModal.vue` as a sheet. The
 * field takes focus the moment it opens; with no query it lists the eight
 * most recent sessions, with one it lists up to ten hits with their matching
 * snippet. A hit opens its conversation — a `global_agent` hit lands in the
 * Global Agent's — through `openSearchResult`. It is not the History page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSearchSheet(state: UiState, viewModel: AppViewModel) {
    val sheet = state.searchSheet
    if (!sheet.open) return
    val palette = CoreHub.palette
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val items = if (sheet.query.isBlank()) sheet.recent else sheet.results

    ModalBottomSheet(
        onDismissRequest = { viewModel.closeSearch() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.bgSidebar,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).imePadding()) {
            Text(
                stringResource(NavDestination.search.labelKey),
                style = MaterialTheme.typography.titleMedium,
                color = palette.textPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            OutlinedTextField(
                value = sheet.query,
                onValueChange = viewModel::setSearchQuery,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(CoreHubIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .focusRequester(focus),
            )
            if (sheet.loading) LoadingRow()
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (sheet.query.isBlank() && items.isNotEmpty()) {
                    item(key = "recent-header") {
                        Text(
                            stringResource(R.string.search_recent).uppercase(),
                            style = CoreHubTextStyles.groupHeader,
                            color = palette.textSecondary,
                            modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 2.dp),
                        )
                    }
                }
                if (!sheet.loading && sheet.query.isNotBlank() && items.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.search_no_results),
                            style = CoreHubTextStyles.meta,
                            color = palette.textMuted,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                items(items, key = { it.id }) { session ->
                    SearchResultRow(session) { viewModel.openSearchResult(session) }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(session: SessionSummary, onClick: () -> Unit) {
    val palette = CoreHub.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CoreHubTokens.Metrics.sessionRowPaddingH, vertical = CoreHubTokens.Metrics.sessionRowPaddingV),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                session.title.ifBlank { session.id },
                style = CoreHubTextStyles.sessionTitle.copy(textDirection = TextDirection.Content),
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                sourceLabel(session.source),
                style = CoreHubTextStyles.meta.copy(fontWeight = FontWeight.Medium),
                color = palette.textSecondary,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            Text(formatStamp(session.updatedAt), style = CoreHubTextStyles.meta, color = palette.textMuted, maxLines = 1)
        }
        session.snippet?.takeIf { it.isNotBlank() }?.let { snippet ->
            Text(
                snippet,
                style = CoreHubTextStyles.meta.copy(textDirection = TextDirection.Content),
                color = palette.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** `formatSource` of SessionSearchModal.vue. */
internal fun sourceLabel(source: String): String = when (source) {
    "api_server" -> "API Server"
    "cli" -> "CLI"
    "telegram" -> "Telegram"
    "discord" -> "Discord"
    "slack" -> "Slack"
    "matrix" -> "Matrix"
    "whatsapp" -> "WhatsApp"
    "signal" -> "Signal"
    "cron" -> "Cron"
    "weixin" -> "WeChat"
    "global_agent" -> "Global Agent"
    else -> source
}
