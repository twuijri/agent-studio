import SwiftUI

/// The drawer's session list: RECENT → Pinned → categories → Uncategorized,
/// two-line rows, long-press context menu and swipe/✕ delete (web
/// `SessionListItem.vue` + `session-category-groups.ts`).
struct SessionListView: View {
    @EnvironmentObject private var store: AppStore
    @State private var sessions: [SessionSummary] = []
    @State private var categories: [SessionCategory] = []
    @State private var loading = true
    @State private var renaming: SessionSummary?
    @State private var renameText = ""
    @State private var deleting: SessionSummary?
    @State private var managing: SessionSummary?
    @State private var managingCategories = false
    @State private var editingRecentCount = false
    @State private var recentCountText = ""
    /// Bumped after any local preference change (pin, collapse, recent count).
    @State private var prefsVersion = 0

    private var groups: [SessionGroup] {
        _ = prefsVersion
        return SessionGrouping.groups(
            sessions: sessions,
            categories: categories,
            pinnedIDs: store.browserPrefs.pinnedIDs(profile: store.selectedProfile),
            recentCount: store.browserPrefs.recentCount,
            labels: .init(recent: String(localized: "Recent"), pinned: String(localized: "Pinned"), uncategorized: String(localized: "Uncategorized"))
        )
    }

    var body: some View {
        List {
            if loading && sessions.isEmpty {
                ProgressView().frame(maxWidth: .infinity).listRowBackground(Color.clear).listRowSeparator(.hidden)
            } else if sessions.isEmpty {
                Text("No conversations").font(CoreHubTokens.Typography.metaFont).foregroundStyle(CoreHubTokens.Palette.textMuted).listRowBackground(Color.clear).listRowSeparator(.hidden)
            }
            ForEach(groups) { group in
                Section {
                    if !store.browserPrefs.isCollapsed(group.id) {
                        ForEach(group.sessions) { session in row(session) }
                    }
                } header: { header(group) }
            }
        }
        .drawerListStyle()
        .task(id: "\(store.selectedProfile)|\(store.sessionListVersion)") { await load() }
        .refreshable { await load() }
        .alert("Rename conversation", isPresented: Binding(get: { renaming != nil }, set: { if !$0 { renaming = nil } })) {
            TextField("Title", text: $renameText)
            Button("Save") { if let renaming { Task { await rename(renaming, to: renameText) } } }
            Button("Cancel", role: .cancel) { renaming = nil }
        }
        .alert("Recent conversations", isPresented: $editingRecentCount) {
            TextField("Count (1–100)", text: $recentCountText).keyboardType(.numberPad)
            Button("Save") { store.browserPrefs.recentCount = Int(recentCountText) ?? store.browserPrefs.recentCount; prefsVersion &+= 1 }
            Button("Cancel", role: .cancel) {}
        } message: { Text("How many conversations to show under Recent.") }
        .confirmationDialog("Delete conversation?", isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }), presenting: deleting) { session in
            Button("Delete", role: .destructive) { Task { await delete(session) } }
            Button("Cancel", role: .cancel) { deleting = nil }
        } message: { session in Text(session.title) }
        .sheet(item: $managing) { item in SessionManagementView(session: item, categories: categories) { await load() }.environmentObject(store) }
        .sheet(isPresented: $managingCategories) { NavigationStack { SessionCategoriesView(categories: $categories) }.environmentObject(store) }
    }

    // MARK: Group header

    private func header(_ group: SessionGroup) -> some View {
        HStack(spacing: 4) {
            Button {
                store.browserPrefs.setCollapsed(group.id, !store.browserPrefs.isCollapsed(group.id))
                withAnimation(CoreHubTokens.Motion.quick) { prefsVersion &+= 1 }
            } label: {
                GroupHeaderLabel(title: group.label, count: group.count, expanded: !store.browserPrefs.isCollapsed(group.id))
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            if group.kind == .recent {
                Button { recentCountText = "\(store.browserPrefs.recentCount)"; editingRecentCount = true } label: {
                    CoreHubIconView(icon: .settings, size: 12).foregroundStyle(CoreHubTokens.Palette.textMuted).frame(width: 24, height: 24).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Recent count")
            } else if case .category = group.kind {
                Menu {
                    Button("Manage categories") { managingCategories = true }
                } label: {
                    CoreHubIconView(icon: .more, size: 14).foregroundStyle(CoreHubTokens.Palette.textMuted).frame(width: 24, height: 24).contentShape(Rectangle())
                }
                .accessibilityLabel("Category options")
            }
        }
        .padding(.vertical, 2)
    }

    // MARK: Row

    private func row(_ session: SessionSummary) -> some View {
        let pinned = store.browserPrefs.isPinned(session.id, profile: store.selectedProfile)
        return SessionRowView(
            session: session,
            categoryLabel: categories.first { $0.id == session.categoryID }?.name,
            pinned: pinned,
            unread: store.browserPrefs.unreadIDs().contains(session.id),
            selected: store.selectedSession?.id == session.id,
            profileAvatar: store.profiles.first { $0.name == session.profile }?.avatar,
            time: SessionTimeFormatter.string(for: session.updatedAt, locale: store.locale),
            open: { store.open(session) },
            requestDelete: { deleting = session }
        )
        .listRowInsets(EdgeInsets(top: 0, leading: 6, bottom: 0, trailing: 6))
        .listRowBackground(Color.clear)
        .listRowSeparator(.hidden)
        .contextMenu {
            Button { renameText = session.title; renaming = session } label: { Label("Rename", systemImage: "pencil") }
            Button { store.browserPrefs.togglePin(session.id, profile: store.selectedProfile); prefsVersion &+= 1 } label: { Label(pinned ? "Unpin" : "Pin", systemImage: pinned ? "pin.slash" : "pin") }
            Menu {
                Button("No category") { Task { await assign(session, category: nil) } }
                ForEach(categories) { category in Button(category.name) { Task { await assign(session, category: category.id) } } }
            } label: { Label("Move to category", systemImage: "folder") }
            Button { Task { await archive(session) } } label: { Label("Archive", systemImage: "archivebox") }
            Button { managing = session } label: { Label("Session settings", systemImage: "slider.horizontal.3") }
            Button(role: .destructive) { deleting = session } label: { Label("Delete", systemImage: "trash") }
        }
        .swipeActions(edge: .trailing) {
            Button(role: .destructive) { deleting = session } label: { Label("Delete", systemImage: "trash") }
            Button { Task { await archive(session) } } label: { Label("Archive", systemImage: "archivebox") }.tint(CoreHubTokens.Palette.warning)
        }
    }

    // MARK: Data

    private func load() async {
        loading = true
        do {
            async let categoryRequest = store.api.sessionCategories()
            sessions = try await store.api.sessions(profile: store.selectedProfile.nilIfEmpty, limit: 100).filter { !$0.archived }
            categories = try await categoryRequest
            store.browserPrefs.prunePins(existing: Set(sessions.map(\.id)), profile: store.selectedProfile)
        } catch { store.errorMessage = error.localizedDescription }
        loading = false
    }

    private func rename(_ session: SessionSummary, to title: String) async {
        await store.attempt { try await store.api.renameSession(session.id, title: title) }
        renaming = nil
        if store.selectedSession?.id == session.id { store.selectedSession?.title = title }
        store.sessionsChanged()
    }

    private func delete(_ session: SessionSummary) async {
        await store.attempt { try await store.api.deleteSession(session.id) }
        deleting = nil
        store.browserPrefs.unpin(session.id, profile: store.selectedProfile)
        if store.selectedSession?.id == session.id { store.selectedSession = nil }
        store.sessionsChanged()
    }

    private func archive(_ session: SessionSummary) async {
        await store.attempt { try await store.api.setSessionArchived(session.id, archived: true) }
        if store.selectedSession?.id == session.id { store.selectedSession = nil }
        store.sessionsChanged()
    }

    private func assign(_ session: SessionSummary, category: Int?) async {
        await store.attempt { try await store.api.setSessionCategory(session.id, categoryID: category) }
        store.sessionsChanged()
    }
}

/// Two-line session row (padding 8×10, radius 6): pin · unread dot · title
/// (per-string direction) … time / agent avatar · profile chip · category tag.
struct SessionRowView: View {
    let session: SessionSummary
    var categoryLabel: String?
    var pinned = false
    var unread = false
    var selected = false
    var streaming = false
    var profileAvatar: AvatarSpec?
    var time: String
    let open: () -> Void
    let requestDelete: () -> Void

    var body: some View {
        HStack(spacing: 4) {
            Button(action: open) {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        if pinned {
                            CoreHubIconView(icon: .pin, size: CoreHubTokens.Layout.pinIcon).foregroundStyle(CoreHubTokens.Palette.accent)
                        }
                        if unread {
                            Circle().fill(CoreHubTokens.Palette.accent)
                                .frame(width: CoreHubTokens.Layout.unreadDot, height: CoreHubTokens.Layout.unreadDot)
                                .background(Circle().fill(CoreHubTokens.Palette.accent.opacity(CoreHubTokens.Alpha.unreadHalo)).padding(-3))
                        }
                        DirectionalText(text: session.title, font: CoreHubTokens.Typography.font(CoreHubTokens.Typography.sessionTitle, weight: selected ? .medium : .regular))
                        Text(time)
                            .font(CoreHubTokens.Typography.metaFont)
                            .foregroundStyle(CoreHubTokens.Palette.textMuted)
                            .lineLimit(1)
                            .fixedSize()
                    }
                    HStack(spacing: 6) {
                        AgentAvatarView(asset: AgentAvatarAsset.resolve(session: session), streaming: streaming)
                        HStack(spacing: 4) {
                            ProfileAvatar(name: session.profile, avatar: profileAvatar, size: CoreHubTokens.Layout.profileChipAvatar)
                            Text(session.profile).font(CoreHubTokens.Typography.metaFont).foregroundStyle(CoreHubTokens.Palette.textMuted).lineLimit(1)
                        }
                        if let categoryLabel, !categoryLabel.isEmpty {
                            Text(categoryLabel)
                                .font(CoreHubTokens.Typography.categoryTagFont)
                                .foregroundStyle(CoreHubTokens.Palette.textSecondary)
                                .lineLimit(1)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 1)
                                .background(CoreHubTokens.Palette.categoryTag, in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.tag))
                                .frame(maxWidth: CoreHubTokens.Layout.drawerMaxWidth * CoreHubTokens.Layout.categoryTagMaxWidthFraction, alignment: .leading)
                        }
                        Spacer(minLength: 0)
                    }
                }
                .padding(.vertical, CoreHubTokens.Layout.sessionRowVertical)
                .padding(.horizontal, CoreHubTokens.Layout.sessionRowHorizontal)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(selected ? CoreHubTokens.Palette.selected : Color.clear, in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.button, style: .continuous))
                .contentShape(Rectangle())
            }
            .buttonStyle(RailButtonStyle())
            Button(action: requestDelete) {
                CoreHubIconView(icon: .close, size: 12)
                    .foregroundStyle(CoreHubTokens.Palette.textSecondary)
                    .opacity(CoreHubTokens.Alpha.deleteAffordance)
                    .frame(width: 24, height: 24)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Delete")
        }
        .accessibilityElement(children: .contain)
    }
}
