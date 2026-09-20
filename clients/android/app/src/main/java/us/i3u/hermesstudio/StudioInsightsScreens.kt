package us.i3u.hermesstudio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import us.i3u.hermesstudio.navigation.NavDestination
import us.i3u.hermesstudio.ui.chat.compactNumber
import us.i3u.hermesstudio.ui.theme.CoreHub

/**
 * Settings › Tools › Usage, Performance and Skills Usage — three screens
 * where there used to be one "Insights" and one "Journey". Each mirrors one
 * web view: `UsageView.vue` (token usage, `/api/studio/usage/stats`),
 * `PerformanceView.vue` (runtime processes, `/api/studio/performance/runtime`),
 * `SkillsUsageView.vue` (`/api/hermes/skills/usage/stats`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InsightsScaffold(
    destination: NavDestination,
    loading: Boolean,
    state: UiState,
    viewModel: AppViewModel,
    onRefresh: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            StudioTopBar(
                title = stringResource(destination.labelKey),
                onBack = { viewModel.back() },
                actions = {
                    IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(StudioHorizontalPadding, 8.dp, StudioHorizontalPadding, 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (loading) item { LoadingRow() }
            state.error?.let { item { ErrorNote(it) { viewModel.dismissError() } } }
            content()
        }
    }
}

private fun LazyListScope.periodChips(selected: Int, onPick: (Int) -> Unit) {
    item(key = "period") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 30, 90, 365).forEach { days ->
                AssistChip(
                    onClick = { onPick(days) },
                    label = { Text(stringResource(R.string.insights_days, days)) },
                    leadingIcon = if (days == selected) ({ Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) }) else null,
                )
            }
        }
    }
}

@Composable
fun UsageScreen(state: UiState, viewModel: AppViewModel) {
    InsightsScaffold(NavDestination.usage, state.loadingUsage, state, viewModel, onRefresh = { viewModel.refreshUsage() }) {
        periodChips(state.usageDays) { viewModel.refreshUsage(it) }
        state.usageStats?.let { stats ->
            item {
                StudioGroupedCard {
                    InsightMetric(stringResource(R.string.insights_tokens), compactNumber(stats.inputTokens + stats.outputTokens))
                    StudioCardDivider()
                    InsightMetric(stringResource(R.string.insights_sessions), stats.sessions.toString())
                    StudioCardDivider()
                    InsightMetric(stringResource(R.string.insights_cost), "$${"%.4f".format(stats.cost)}")
                    StudioCardDivider()
                    InsightMetric(stringResource(R.string.insights_cache), compactNumber(stats.cacheReadTokens + stats.cacheWriteTokens))
                }
            }
            if (stats.models.isNotEmpty()) {
                item { StudioSectionTitle(stringResource(R.string.insights_by_model)) }
                items(stats.models.take(8), key = { "model-" + it.name }) { row ->
                    StudioGroupedCard { InsightMetric(row.name, compactNumber(row.totalTokens), row.sessions.toString()) }
                }
            }
            if (stats.agents.isNotEmpty()) {
                item { StudioSectionTitle(stringResource(R.string.insights_by_agent)) }
                items(stats.agents.take(8), key = { "agent-" + it.name }) { row ->
                    StudioGroupedCard { InsightMetric(row.name, compactNumber(row.totalTokens), row.sessions.toString()) }
                }
            }
            if (stats.daily.isNotEmpty()) {
                item { StudioSectionTitle(stringResource(R.string.insights_daily)) }
                items(stats.daily.takeLast(14).reversed(), key = { "day-" + it.date }) { row ->
                    StudioGroupedCard { InsightMetric(row.date, compactNumber(row.totalTokens), "$${"%.3f".format(row.cost)}") }
                }
            }
        }
    }
}

@Composable
fun PerformanceScreen(state: UiState, viewModel: AppViewModel) {
    val palette = CoreHub.palette
    InsightsScaffold(NavDestination.performance, state.loadingPerformance, state, viewModel, onRefresh = { viewModel.refreshPerformance() }) {
        state.runtimePerformance?.let { runtime ->
            item {
                StudioGroupedCard {
                    InsightMetric("CPU", runtime.cpuPercent?.let { "%.1f%%".format(it) } ?: "—")
                    StudioCardDivider()
                    InsightMetric(stringResource(R.string.insights_memory), runtime.memoryPercent?.let { "%.1f%%".format(it) } ?: "—", runtime.usedMemoryBytes?.let { used -> runtime.totalMemoryBytes?.let { "${formatBytes(used)} / ${formatBytes(it)}" } })
                    StudioCardDivider()
                    InsightMetric(stringResource(R.string.insights_live_sessions), runtime.sessionCount.toString(), stringResource(R.string.performance_running_sessions, runtime.runningSessions))
                    StudioCardDivider()
                    InsightMetric(stringResource(R.string.insights_workers), "${runtime.runningWorkers}/${runtime.workerCount}", stringResource(R.string.performance_total_worker_memory) + " " + formatBytes(runtime.totalWorkerMemoryBytes))
                }
            }
            item {
                StudioSectionTitle(stringResource(R.string.performance_processes))
                Text(
                    listOf(runtime.platform, runtime.arch, "${runtime.cpuCount} CPU", stringResource(R.string.performance_uptime) + " " + formatDuration(runtime.uptimeSeconds))
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                    color = palette.textMuted,
                )
            }
            if (runtime.workers.isEmpty()) {
                item { Text(stringResource(R.string.performance_no_workers), style = MaterialTheme.typography.bodySmall, color = palette.textMuted) }
            }
            items(runtime.workers, key = { "worker-${it.pid}" }) { worker ->
                StudioGroupedCard {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(worker.profile.ifBlank { "pid ${worker.pid}" }, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(if (worker.running) R.string.performance_running else R.string.performance_stopped),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (worker.running) palette.success else palette.textMuted,
                            )
                        }
                        Text(
                            "pid ${worker.pid} · ${worker.cpuPercent?.let { "%.1f%% CPU".format(it) } ?: "—"} · ${formatBytes(worker.memoryRssBytes)} · ${worker.runningSessionCount}/${worker.sessionCount}",
                            style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
                            color = palette.textMuted,
                        )
                        worker.error?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = palette.error) }
                    }
                }
            }
            if (runtime.sessionsByProfile.isNotEmpty()) {
                item { StudioSectionTitle(stringResource(R.string.performance_sessions_by_profile)) }
                item {
                    StudioGroupedCard {
                        runtime.sessionsByProfile.entries.forEachIndexed { index, (profile, count) ->
                            InsightMetric(profile, count.toString())
                            if (index != runtime.sessionsByProfile.size - 1) StudioCardDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SkillsUsageScreen(state: UiState, viewModel: AppViewModel) {
    val palette = CoreHub.palette
    InsightsScaffold(NavDestination.skillsUsage, state.loadingSkillsUsage, state, viewModel, onRefresh = { viewModel.refreshSkillsUsage() }) {
        periodChips(state.usageDays) { viewModel.refreshSkillsUsage(it) }
        state.skillUsage?.let { usage ->
            item {
                StudioGroupedCard {
                    InsightMetric(stringResource(R.string.skills_usage_actions), usage.totalActions.toString())
                    StudioCardDivider()
                    InsightMetric(stringResource(R.string.skills_usage_loads), usage.totalLoads.toString())
                    StudioCardDivider()
                    InsightMetric(stringResource(R.string.skills_usage_edits), usage.totalEdits.toString())
                    StudioCardDivider()
                    InsightMetric(stringResource(R.string.skills_usage_distinct), usage.distinctSkills.toString())
                }
            }
            if (usage.topSkills.isEmpty()) {
                item { Text(stringResource(R.string.skills_usage_empty), style = MaterialTheme.typography.bodySmall, color = palette.textMuted) }
            } else {
                item { StudioSectionTitle(stringResource(R.string.skills_usage_top)) }
                item {
                    StudioGroupedCard {
                        usage.topSkills.forEachIndexed { index, (skill, count) ->
                            InsightMetric(skill, count.toString())
                            if (index != usage.topSkills.lastIndex) StudioCardDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun InsightMetric(label: String, value: String, supporting: String? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            supporting?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "%.0f KB".format(bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}

private fun formatDuration(seconds: Long): String {
    val days = seconds / 86_400
    val hours = seconds % 86_400 / 3_600
    val minutes = seconds % 3_600 / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}
