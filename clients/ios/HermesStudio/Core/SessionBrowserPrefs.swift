import Foundation

/// Per-device session-list preferences, mirroring the web's
/// `session-browser-prefs.ts`: pins are local to this client and scoped to
/// the profile, RECENT shows 1–100 sessions (default 10), group collapse
/// state is remembered, and completed-but-unread sessions show a dot.
struct SessionBrowserPrefs {
    let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) { self.defaults = defaults }

    // MARK: Recent count

    var recentCount: Int {
        get { Self.clampRecent(defaults.object(forKey: Self.recentCountKey) as? Int ?? CoreHubTokens.Layout.recentDefault) }
        nonmutating set { defaults.set(Self.clampRecent(newValue), forKey: Self.recentCountKey) }
    }

    static func clampRecent(_ value: Int) -> Int {
        min(CoreHubTokens.Layout.recentMaximum, max(CoreHubTokens.Layout.recentMinimum, value))
    }

    // MARK: Pins (per profile)

    func pinnedIDs(profile: String) -> [String] {
        defaults.stringArray(forKey: Self.pinsKey(profile)) ?? []
    }

    func isPinned(_ sessionID: String, profile: String) -> Bool {
        pinnedIDs(profile: profile).contains(sessionID)
    }

    func togglePin(_ sessionID: String, profile: String) {
        var ids = pinnedIDs(profile: profile)
        if let index = ids.firstIndex(of: sessionID) { ids.remove(at: index) } else { ids.append(sessionID) }
        defaults.set(ids, forKey: Self.pinsKey(profile))
    }

    func unpin(_ sessionID: String, profile: String) {
        let ids = pinnedIDs(profile: profile).filter { $0 != sessionID }
        defaults.set(ids, forKey: Self.pinsKey(profile))
    }

    /// Drops pins whose sessions no longer exist. Returns true when changed.
    @discardableResult
    func prunePins(existing: Set<String>, profile: String) -> Bool {
        let current = pinnedIDs(profile: profile)
        let next = current.filter { existing.contains($0) }
        guard next != current else { return false }
        defaults.set(next, forKey: Self.pinsKey(profile))
        return true
    }

    // MARK: Collapsed groups

    func isCollapsed(_ groupKey: String) -> Bool {
        (defaults.stringArray(forKey: Self.collapsedKey) ?? []).contains(groupKey)
    }

    func setCollapsed(_ groupKey: String, _ collapsed: Bool) {
        var keys = defaults.stringArray(forKey: Self.collapsedKey) ?? []
        keys.removeAll { $0 == groupKey }
        if collapsed { keys.append(groupKey) }
        defaults.set(keys, forKey: Self.collapsedKey)
    }

    // MARK: Unread (completed while not open)

    func unreadIDs() -> Set<String> { Set(defaults.stringArray(forKey: Self.unreadKey) ?? []) }

    func markUnread(_ sessionID: String) {
        var ids = unreadIDs(); ids.insert(sessionID)
        defaults.set(Array(ids).sorted(), forKey: Self.unreadKey)
    }

    func markRead(_ sessionID: String) {
        var ids = unreadIDs(); ids.remove(sessionID)
        defaults.set(Array(ids).sorted(), forKey: Self.unreadKey)
    }

    // MARK: Keys

    static let recentCountKey = "sessionBrowser.recentCount"
    static let collapsedKey = "sessionBrowser.collapsedGroups"
    static let unreadKey = "sessionBrowser.unread"
    static func pinsKey(_ profile: String) -> String { "sessionBrowser.pins.\(profile)" }
}
