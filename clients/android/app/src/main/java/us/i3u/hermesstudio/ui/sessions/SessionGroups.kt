package us.i3u.hermesstudio.ui.sessions

import us.i3u.hermesstudio.SessionCategory
import us.i3u.hermesstudio.SessionSummary
import us.i3u.hermesstudio.ui.theme.CoreHubTokens

/** Which kind of group a header represents; drives its trailing control. */
enum class SessionGroupKind { Recent, Pinned, Category, Uncategorized }

data class SessionGroup(
    val key: String,
    val label: String,
    val kind: SessionGroupKind,
    val sessions: List<SessionSummary>,
    /** The category behind a [SessionGroupKind.Category] group. */
    val category: SessionCategory? = null,
)

/**
 * The web sidebar's grouping (`session-category-groups.ts` + ChatPanel):
 * RECENT (a shortcut: the newest N unpinned sessions, which stay in their real
 * group too) → Pinned → one group per category with members → Uncategorized.
 * Pure so it can be unit-tested.
 */
fun buildSessionGroups(
    sessions: List<SessionSummary>,
    categories: List<SessionCategory>,
    pinnedIds: Set<String>,
    recentCount: Int,
    recentLabel: String,
    pinnedLabel: String,
    uncategorizedLabel: String,
): List<SessionGroup> {
    val groups = mutableListOf<SessionGroup>()
    val limit = recentCount.coerceIn(CoreHubTokens.Metrics.recentMin, CoreHubTokens.Metrics.recentMax)
    val unpinned = sessions.filter { it.id !in pinnedIds }
    val recent = unpinned
        .sortedByDescending { sessionEpochMillis(it.updatedAt) ?: 0L }
        .take(limit)
    if (recent.isNotEmpty()) groups += SessionGroup("recent", recentLabel, SessionGroupKind.Recent, recent)

    val pinned = sessions.filter { it.id in pinnedIds }
    if (pinned.isNotEmpty()) groups += SessionGroup("pinned", pinnedLabel, SessionGroupKind.Pinned, pinned)

    val knownIds = categories.map { it.id }.toSet()
    categories.forEach { category ->
        val members = unpinned.filter { it.categoryId == category.id }
        if (members.isNotEmpty()) {
            groups += SessionGroup("category-${category.id}", category.name, SessionGroupKind.Category, members, category)
        }
    }
    val uncategorized = unpinned.filter { it.categoryId == null || it.categoryId !in knownIds }
    if (uncategorized.isNotEmpty()) {
        groups += SessionGroup("category-none", uncategorizedLabel, SessionGroupKind.Uncategorized, uncategorized)
    }
    return groups
}

/** The last path segment of a workspace, as the chat header's workspace chip shows it. */
fun workspaceChipLabel(workspace: String?): String? {
    val clean = workspace?.trim()?.trimEnd('/', '\\')?.takeIf { it.isNotBlank() } ?: return null
    return clean.substringAfterLast('/').substringAfterLast('\\').ifBlank { clean }
}
