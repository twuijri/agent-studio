import SwiftUI

struct ChatsView: View {
    @EnvironmentObject private var store: AppStore
    @AppStorage("studioArchivedSessionIDs") private var archivedSessionIDs = ""
    @State private var sessions: [SessionSummary] = []
    @State private var search = ""
    @State private var profileFilter = ""
    @State private var agentFilter = ""
    @State private var showArchived = false
    @State private var categories: [SessionCategory] = []
    @State private var managingCategories = false
    @State private var loading = true
    @State private var editSession: SessionSummary?
    @State private var newTitle = ""
    @State private var creatingSession = false
    @State private var selectedIDs: Set<String> = []; @State private var manageSession: SessionSummary?; @State private var pageLimit = 100

    var body: some View {
        Group {
            if loading && sessions.isEmpty { ProgressView("Loading conversations…") }
            else {
                List(selection: $selectedIDs) {
                    Section {
                        HStack(spacing: 10) {
                            Menu {
                                Button { profileFilter = "" } label: {
                                    if profileFilter.isEmpty { Label("All profiles", systemImage: "checkmark") }
                                    else { Text("All profiles") }
                                }
                                Divider()
                                ForEach(store.profiles) { profile in
                                    Button { profileFilter = profile.name } label: {
                                        if profileFilter == profile.name { Label(profile.name, systemImage: "checkmark") }
                                        else { Text(profile.name) }
                                    }
                                }
                            } label: {
                                Label(profileFilter.isEmpty ? String(localized: "All profiles") : profileFilter, systemImage: "line.3.horizontal.decrease.circle")
                                    .font(.subheadline.weight(.semibold))
                            }
                            Menu {
                                Button { agentFilter = "" } label: { if agentFilter.isEmpty { Label("All agents", systemImage: "checkmark") } else { Text("All agents") } }
                                Divider()
                                ForEach(["hermes", "ekko-agent", "claude-code", "codex", "pi"], id: \.self) { id in
                                    Button { agentFilter = id } label: { if agentFilter == id { Label(AgentIdentity.displayName(for: id), systemImage: "checkmark") } else { Text(AgentIdentity.displayName(for: id)) } }
                                }
                            } label: { Label(agentFilter.isEmpty ? String(localized: "All agents") : AgentIdentity.displayName(for: agentFilter), systemImage: "cpu") .font(.subheadline.weight(.semibold)) }
                            Spacer()
                            Text("\(sessions.count) \(String(localized: "conversations"))").font(.caption).foregroundStyle(.secondary)
                        }
                        SearchBar(text: $search).listRowInsets(EdgeInsets()).listRowBackground(Color.clear).listRowSeparator(.hidden)
                    }
                    if !categories.isEmpty {
                        Section {
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack { ForEach(categories) { category in Text(category.name).font(.caption.weight(.semibold)).padding(.horizontal, 10).padding(.vertical, 6).background(.thinMaterial, in: Capsule()) } }
                            }
                        }.listRowBackground(Color.clear).listRowSeparator(.hidden)
                    }
                    if filtered.isEmpty {
                        EmptyState(icon: "bubble.left.and.bubble.right", title: "No conversations", detail: "Start a conversation with your agent.")
                            .listRowBackground(Color.clear).listRowSeparator(.hidden)
                    }
                    ForEach(filtered) { session in
                        SessionRowView(
                            session: session,
                            categoryLabel: categories.first { $0.id == session.categoryID }?.name,
                            pinned: store.browserPrefs.isPinned(session.id, profile: session.profile),
                            unread: store.browserPrefs.unreadIDs().contains(session.id),
                            selected: store.selectedSession?.id == session.id,
                            profileAvatar: (store.profiles.first { $0.name == session.profile } ?? store.profile)?.avatar,
                            time: SessionTimeFormatter.string(for: session.updatedAt, locale: store.locale),
                            open: { store.open(session) },
                            requestDelete: { Task { await delete(session) } }
                        )
                            .tag(session.id)
                            .listRowInsets(EdgeInsets(top: 0, leading: 6, bottom: 0, trailing: 6))
                            .listRowSeparator(.hidden)
                            .swipeActions(edge: .trailing) { Button(role: .destructive) { Task { await delete(session) } } label: { Label("Delete", systemImage: "trash") }; Button { Task { await archive(session, archived: !session.archived) } } label: { Label(session.archived ? "Unarchive" : "Archive", systemImage: session.archived ? "tray.and.arrow.up" : "archivebox") }.tint(CoreHubTokens.Palette.warning); Button { newTitle = session.title; editSession = session } label: { Label("Rename", systemImage: "pencil") }.tint(CoreHubTokens.Palette.info) }
                            .contextMenu {
                                Button { newTitle = session.title; editSession = session } label: { Label("Rename", systemImage: "pencil") }
                                Menu("Move to category") {
                                    Button("No category") { Task { await assign(session, category: nil) } }
                                    ForEach(categories) { category in Button(category.name) { Task { await assign(session, category: category.id) } } }
                                }
                                Button { Task { await archive(session, archived: !session.archived) } } label: { Label(session.archived ? "Unarchive" : "Archive", systemImage: "archivebox") }
                                Button("Session settings") { manageSession = session }
                                Button(role: .destructive) { Task { await delete(session) } } label: { Label("Delete", systemImage: "trash") }
                            }
                    }
                    if !showArchived && search.isEmpty && sessions.count >= pageLimit { Button("Load more") { pageLimit += 100; Task { await load() } }.frame(maxWidth: .infinity) }
                }.listStyle(.insetGrouped).refreshable { await load() }
            }
        }
        .navigationTitle("History")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                if !selectedIDs.isEmpty { Button(role: .destructive) { Task { await batchDelete() } } label: { Image(systemName: "trash") } }
                EditButton()
                Button { showArchived.toggle() } label: { Image(systemName: showArchived ? "tray.full.fill" : "archivebox") }.accessibilityLabel(showArchived ? "Show active" : "Show archived")
                Button { managingCategories = true } label: { Image(systemName: "folder.badge.gearshape") }.accessibilityLabel("Manage categories")
                Button { Task { await load() } } label: { Image(systemName: "arrow.clockwise") }.accessibilityLabel("Refresh")
                Button { creatingSession = true } label: { Image(systemName: "square.and.pencil") }.accessibilityLabel("New conversation")
            }
        }
        .task(id: "\(profileFilter)|\(search)|\(showArchived)") { if !search.isEmpty { try? await Task.sleep(for: .milliseconds(300)) }; guard !Task.isCancelled else { return }; await load() }
        .sheet(isPresented: $managingCategories) { NavigationStack { SessionCategoriesView(categories: $categories) }.environmentObject(store) }
        .sheet(isPresented: $creatingSession) { NewCodingSessionView(categories: categories).environmentObject(store) }
        .sheet(item: $manageSession) { item in SessionManagementView(session: item, categories: categories) { await load() }.environmentObject(store) }
        .alert("Rename conversation", isPresented: Binding(get: { editSession != nil }, set: { if !$0 { editSession = nil } })) {
            TextField("Title", text: $newTitle)
            Button("Save") { guard let editSession else { return }; Task { await store.attempt { try await store.api.renameSession(editSession.id, title: newTitle) }; await load(); store.sessionsChanged() } }
            Button("Cancel", role: .cancel) { editSession = nil }
        }
    }

    private var filtered: [SessionSummary] { sessions.filter { session in session.archived == showArchived && (agentFilter.isEmpty || AgentIdentity.canonicalID(session.agentID) == agentFilter) && (search.isEmpty || session.title.localizedCaseInsensitiveContains(search) || session.model.localizedCaseInsensitiveContains(search) || session.agentDisplayName.localizedCaseInsensitiveContains(search) || session.preview.localizedCaseInsensitiveContains(search)) } }
    private var archivedIDs: [String] { archivedSessionIDs.split(separator: "\n").map(String.init) }
    private func load() async {
        loading = true
        do {
            async let categoryRequest = store.api.sessionCategories()
            if showArchived {
                var archived: [SessionSummary] = []
                for id in archivedIDs { if let item = try? await store.api.sessionSummary(id, profile: profileFilter.nilIfEmpty), item.archived { archived.append(item) } }
                sessions = archived
            } else if search.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { sessions = try await store.api.sessions(profile: profileFilter.nilIfEmpty, limit: pageLimit) }
            else { sessions = try await store.api.searchSessions(search, profile: profileFilter.nilIfEmpty) }
            categories = try await categoryRequest
        } catch { store.errorMessage = error.localizedDescription }
        loading = false
    }
    private func delete(_ session: SessionSummary) async { do { try await store.api.deleteSession(session.id); sessions.removeAll { $0.id == session.id }; if store.selectedSession?.id == session.id { store.selectedSession = nil }; store.sessionsChanged() } catch { store.errorMessage = error.localizedDescription } }
    private func batchDelete() async { let targets = sessions.filter { selectedIDs.contains($0.id) }; do { _ = try await store.api.batchDeleteSessions(targets); selectedIDs = []; await load(); store.sessionsChanged() } catch { store.errorMessage = error.localizedDescription } }
    private func archive(_ session: SessionSummary, archived: Bool) async {
        do {
            try await store.api.setSessionArchived(session.id, archived: archived)
            var ids = Set(archivedIDs)
            if archived { ids.insert(session.id) } else { ids.remove(session.id) }
            archivedSessionIDs = ids.sorted().joined(separator: "\n")
            sessions.removeAll { $0.id == session.id }
            store.sessionsChanged()
        } catch { store.errorMessage = error.localizedDescription }
    }
    private func assign(_ session: SessionSummary, category: Int?) async { do { try await store.api.setSessionCategory(session.id, categoryID: category); await load(); store.sessionsChanged() } catch { store.errorMessage = error.localizedDescription } }
}

struct SessionManagementView: View {
    @EnvironmentObject private var store: AppStore; @Environment(\.dismiss) private var dismiss
    let session: SessionSummary; let categories: [SessionCategory]; let reload: () async -> Void
    @State private var workspace = ""; @State private var categoryID = 0; @State private var push = true; @State private var folders: [String] = []; @State private var exportURL: URL?
    var body: some View { NavigationStack { Form { Section("Workspace") { Picker("Recent workspaces", selection: $workspace) { Text("No workspace").tag(""); ForEach(folders, id: \.self) { Text($0).tag($0) } }; TextField("Workspace path", text: $workspace).textInputAutocapitalization(.never) }; Section("Organization") { Picker("Category", selection: $categoryID) { Text("No category").tag(0); ForEach(categories) { Text($0.name).tag($0.id) } }; Toggle("Push completion notification", isOn: $push) }; Section("Export") { Button("Prepare full JSON") { Task { await export(mode: "full", ext: "json") } }; Button("Prepare compressed text") { Task { await export(mode: "compressed", ext: "txt") } }; if let exportURL { ShareLink(item: exportURL) { Label("Share export", systemImage: "square.and.arrow.up") } } }; Section { Button("Save") { Task { await save() } }.frame(maxWidth: .infinity) } }.navigationTitle("Session settings").toolbar { Button("Done") { dismiss() } }.task { workspace = session.workspace; categoryID = session.categoryID ?? 0; push = session.pushEnabled; folders = (try? await store.api.workspaceFolders()) ?? [] } } }
    private func save() async { do { try await store.api.setSessionWorkspace(session.id, workspace: workspace.nilIfEmpty); try await store.api.setSessionCategory(session.id, categoryID: categoryID > 0 ? categoryID : nil); try await store.api.setSessionPush(session.id, enabled: push); await reload(); store.notify(String(localized: "Session updated")) } catch { store.errorMessage = error.localizedDescription } }
    private func export(mode: String, ext: String) async { do { let url = FileManager.default.temporaryDirectory.appendingPathComponent("session-\(session.id).\(ext)"); try await store.api.exportSession(session.id, mode: mode, ext: ext).write(to: url, options: .atomic); exportURL = url } catch { store.errorMessage = error.localizedDescription } }
}

struct NewCodingSessionView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    let categories: [SessionCategory]
    @State private var agent = "hermes"; @State private var profile = ""; @State private var mode = "scoped"; @State private var workspace = ""; @State private var categoryID = 0; @State private var apiMode = "codex_responses"; @State private var baseURL = ""; @State private var apiKey = ""; @State private var pushEnabled = true
    private var isCoding: Bool { AgentIdentity.canonicalID(agent) != "hermes" }
    var body: some View { NavigationStack { Form { Section("Agent") { Picker("Runtime", selection: $agent) { ForEach(["hermes", "ekko-agent", "claude-code", "codex", "pi"], id: \.self) { Text(AgentIdentity.displayName(for: $0)).tag($0) } }; Picker("Profile", selection: $profile) { ForEach(store.profiles) { Text($0.name).tag($0.name) } } }; if isCoding { Section("Launch mode") { Picker("Mode", selection: $mode) { Text("Scoped").tag("scoped"); Text("Global").tag("global") }.pickerStyle(.segmented); Text(mode == "global" ? "Use the agent's global configuration." : "Use isolated Studio provider configuration.").font(.caption).foregroundStyle(.secondary) } }; Section("Session") { TextField("Workspace path", text: $workspace).textInputAutocapitalization(.never); Picker("Category", selection: $categoryID) { Text("No category").tag(0); ForEach(categories) { Text($0.name).tag($0.id) } }; Toggle("Push completion notification", isOn: $pushEnabled) }; if isCoding && mode == "scoped" { Section("Provider API") { Picker("API mode", selection: $apiMode) { Text("Responses").tag("codex_responses"); Text("Chat Completions").tag("chat_completions"); Text("Anthropic Messages").tag("anthropic_messages") }; TextField("Base URL", text: $baseURL).textInputAutocapitalization(.never).keyboardType(.URL); SecureField("API key", text: $apiKey) } }; Section { Button("Start conversation") { store.open(makeSession()); dismiss() }.frame(maxWidth: .infinity) } }.navigationTitle("New conversation").navigationBarTitleDisplayMode(.inline).toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } } }.onAppear { if profile.isEmpty { profile = store.selectedProfile } } } }
    private func makeSession() -> SessionSummary { var json: JSON = ["id": UUID().uuidString, "title": String(localized: "New conversation"), "profile": profile.nilIfEmpty ?? store.selectedProfile, "agent": agent, "source": isCoding ? "coding_agent" : "cli", "agent_mode": mode, "workspace": workspace, "api_mode": apiMode, "base_url": baseURL, "api_key": apiKey, "push_enabled": pushEnabled]; if categoryID > 0 { json["category_id"] = categoryID }; return SessionSummary(json, profile: profile) }
}

struct SessionCategoriesView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @Binding var categories: [SessionCategory]
    @State private var newName = ""
    @State private var editing: SessionCategory?
    @State private var editName = ""
    var body: some View {
        List {
            Section("New category") { HStack { TextField("Category name", text: $newName); Button("Add") { Task { await create() } }.disabled(newName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) } }
            Section("Categories") {
                ForEach(categories) { category in Button { editing = category; editName = category.name } label: { Label(category.name, systemImage: "folder.fill") }.foregroundStyle(.primary) }
                    .onDelete { offsets in for index in offsets { Task { await remove(categories[index]) } } }
            }
        }.navigationTitle("Session categories").toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        .alert("Rename category", isPresented: Binding(get: { editing != nil }, set: { if !$0 { editing = nil } })) { TextField("Category name", text: $editName); Button("Save") { Task { await rename() } }; Button("Cancel", role: .cancel) {} }
    }
    private func refresh() async { categories = (try? await store.api.sessionCategories()) ?? categories }
    private func create() async { do { _ = try await store.api.createSessionCategory(newName); newName = ""; await refresh() } catch { store.errorMessage = error.localizedDescription } }
    private func rename() async { guard let editing else { return }; do { try await store.api.renameSessionCategory(editing.id, name: editName); self.editing = nil; await refresh() } catch { store.errorMessage = error.localizedDescription } }
    private func remove(_ category: SessionCategory) async { do { try await store.api.deleteSessionCategory(category.id); await refresh() } catch { store.errorMessage = error.localizedDescription } }
}
