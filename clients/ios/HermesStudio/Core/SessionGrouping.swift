import Foundation

/// One collapsible group of the session list (RECENT, Pinned, a category,
/// Uncategorized), in display order.
struct SessionGroup: Identifiable, Equatable {
    enum Kind: Equatable { case recent, pinned, category(Int), uncategorized }

    let kind: Kind
    let label: String
    let sessions: [SessionSummary]

    var id: String {
        switch kind {
        case .recent: return "recent"
        case .pinned: return "pinned"
        case let .category(id): return "category-\(id)"
        case .uncategorized: return "category-none"
        }
    }
    var count: Int { sessions.count }
}

/// Pure grouping logic of the web's `session-category-groups.ts` plus the
/// Pinned group: RECENT is a shortcut (its sessions stay in their real
/// groups), Pinned follows, then every non-empty category in server order,
/// then Uncategorized (unknown category ids count as uncategorized).
enum SessionGrouping {
    struct Labels {
        var recent: String
        var pinned: String
        var uncategorized: String
    }

    static func groups(
        sessions: [SessionSummary],
        categories: [SessionCategory],
        pinnedIDs: [String],
        recentCount: Int,
        labels: Labels
    ) -> [SessionGroup] {
        var result: [SessionGroup] = []
        let limit = SessionBrowserPrefs.clampRecent(recentCount)
        let recent = Array(sessions.sorted { updated($0) > updated($1) }.prefix(limit))
        if !recent.isEmpty { result.append(SessionGroup(kind: .recent, label: labels.recent, sessions: recent)) }

        let pinnedSet = Set(pinnedIDs)
        let pinned = sessions.filter { pinnedSet.contains($0.id) }
        if !pinned.isEmpty { result.append(SessionGroup(kind: .pinned, label: labels.pinned, sessions: pinned)) }

        let known = Set(categories.map(\.id))
        for category in categories {
            let members = sessions.filter { $0.categoryID == category.id }
            if !members.isEmpty { result.append(SessionGroup(kind: .category(category.id), label: category.name, sessions: members)) }
        }
        let uncategorized = sessions.filter { session in
            guard let id = session.categoryID else { return true }
            return !known.contains(id)
        }
        if !uncategorized.isEmpty { result.append(SessionGroup(kind: .uncategorized, label: labels.uncategorized, sessions: uncategorized)) }
        return result
    }

    static func updated(_ session: SessionSummary) -> TimeInterval {
        StudioTimestamp.date(from: session.updatedAt)?.timeIntervalSince1970 ?? 0
    }
}

/// Session-row time: same day → `HH:mm`, otherwise `MMM d` ("Sep 18"),
/// both in the given locale (web: `formatTimestampMs`).
enum SessionTimeFormatter {
    static func string(for date: Date?, now: Date = Date(), locale: Locale = .autoupdatingCurrent, timeZone: TimeZone = .current) -> String {
        guard let date else { return "" }
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = locale
        calendar.timeZone = timeZone
        let formatter = DateFormatter()
        formatter.locale = locale
        formatter.timeZone = timeZone
        formatter.calendar = calendar
        if calendar.isDate(date, inSameDayAs: now) {
            formatter.setLocalizedDateFormatFromTemplate("HH:mm")
        } else {
            formatter.setLocalizedDateFormatFromTemplate("MMM d")
        }
        return formatter.string(from: date)
    }

    static func string(for raw: String, now: Date = Date(), locale: Locale = .autoupdatingCurrent) -> String {
        string(for: StudioTimestamp.date(from: raw), now: now, locale: locale)
    }
}

/// The last path segment of a workspace path, for the chat-header chip.
enum WorkspaceChip {
    static func label(for workspace: String) -> String {
        let trimmed = workspace.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "" }
        let parts = trimmed.split(whereSeparator: { $0 == "/" || $0 == "\\" }).map(String.init)
        return parts.last ?? trimmed
    }
}
