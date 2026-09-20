import SwiftUI

/// The Search rail entry — the phone's `SessionSearchModal.vue`: a sheet
/// over the sessions with the field focused at once, the last eight sessions
/// while the field is empty, and up to ten title/message matches with their
/// snippet once it is not. A result whose source is `global_agent` opens the
/// Global Agent conversation.
struct SessionSearchSheet: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @FocusState private var focused: Bool

    @State private var query = ""
    @State private var recent: [SessionSummary] = []
    @State private var results: [SessionSearchResult] = []
    @State private var loading = false

    private var items: [SessionSearchResult] { SessionSearchModel.items(query: query, recent: recent, results: results) }

    var body: some View {
        NavigationStack {
            List {
                field
                if loading && items.isEmpty {
                    ProgressView().frame(maxWidth: .infinity).listRowBackground(Color.clear)
                }
                if !loading && items.isEmpty { empty }
                ForEach(items) { item in row(item) }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .background(CoreHubTokens.Palette.bgPrimary)
            .navigationTitle(NavDestination.search.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } } }
        }
        .task { await loadRecent() }
        .task(id: query) {
            guard SessionSearchModel.hasQuery(query) else { results = []; return }
            try? await Task.sleep(for: .milliseconds(SessionSearchModel.debounceMilliseconds))
            guard !Task.isCancelled else { return }
            await search()
        }
        // `@FocusState` cannot be set before the field exists in the
        // hierarchy; the first run loop after appearance is the earliest.
        .onAppear { DispatchQueue.main.async { focused = true } }
    }

    private var field: some View {
        HStack(spacing: 8) {
            CoreHubIconView(icon: .search, size: 16).foregroundStyle(CoreHubTokens.Palette.textMuted)
            TextField("Search conversations", text: $query)
                .focused($focused)
                .submitLabel(.search)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .contentDirection(of: query)
            if !query.isEmpty {
                Button { query = "" } label: { CoreHubIconView(icon: .close, size: 14).foregroundStyle(CoreHubTokens.Palette.textMuted) }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Clear")
            }
        }
        .padding(.horizontal, 10)
        .frame(height: 40)
        .background(CoreHubTokens.Palette.bgCard, in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.control, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: CoreHubTokens.Radius.control, style: .continuous).stroke(focused ? CoreHubTokens.Palette.accent : CoreHubTokens.Palette.inputBorderIdle, lineWidth: 1))
        .listRowInsets(EdgeInsets(top: 8, leading: 12, bottom: 8, trailing: 12))
        .listRowBackground(Color.clear)
        .listRowSeparator(.hidden)
    }

    private var empty: some View {
        EmptyState(
            icon: "magnifyingglass",
            title: SessionSearchModel.hasQuery(query) ? "No matches" : "No conversations",
            detail: SessionSearchModel.hasQuery(query) ? "Nothing matched in titles or messages." : "Start a conversation with your agent."
        )
        .listRowBackground(Color.clear)
        .listRowSeparator(.hidden)
    }

    private func row(_ item: SessionSearchResult) -> some View {
        Button { open(item.session) } label: {
            SessionSearchRow(item: item, time: SessionTimeFormatter.string(for: item.session.updatedAt, locale: store.locale))
        }
        .buttonStyle(RailButtonStyle())
        .listRowInsets(EdgeInsets(top: 0, leading: 6, bottom: 0, trailing: 6))
        .listRowBackground(Color.clear)
        .listRowSeparator(.hidden)
    }

    private func open(_ session: SessionSummary) {
        dismiss()
        if SessionSearchModel.opensGlobalAgent(session) { store.openGlobalAgent(session) } else { store.open(session) }
    }

    private func loadRecent() async {
        loading = true
        defer { loading = false }
        recent = (await store.attempt({ try await store.api.sessions(profile: store.sessionListProfile, limit: SessionSearchModel.recentLimit) })) ?? []
    }

    private func search() async {
        loading = true
        defer { loading = false }
        let text = query.trimmingCharacters(in: .whitespacesAndNewlines)
        let found = await store.attempt({ try await store.api.searchSessionMatches(text, profile: store.sessionListProfile, limit: SessionSearchModel.resultLimit) })
        // A slower earlier request must not overwrite a newer query's results.
        guard text == query.trimmingCharacters(in: .whitespacesAndNewlines) else { return }
        results = found ?? []
    }
}

/// Title, source · time, and the matched snippet (or the preview).
struct SessionSearchRow: View {
    let item: SessionSearchResult
    let time: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 6) {
                AgentAvatarView(asset: AgentAvatarAsset.resolve(session: item.session))
                DirectionalText(text: SessionSearchModel.title(of: item.session), font: CoreHubTokens.Typography.font(CoreHubTokens.Typography.sessionTitle, weight: .medium))
                Text(time).font(CoreHubTokens.Typography.metaFont).foregroundStyle(CoreHubTokens.Palette.textMuted).lineLimit(1).fixedSize()
            }
            HStack(spacing: 4) {
                Text(SessionSearchModel.sourceLabel(item.session.source))
                Text(verbatim: "·")
                Text(item.session.profile)
            }
            .font(CoreHubTokens.Typography.metaFont)
            .foregroundStyle(CoreHubTokens.Palette.textMuted)
            .lineLimit(1)
            if !item.snippet.isEmpty {
                DirectionalText(text: item.snippet, font: CoreHubTokens.Typography.metaFont, color: CoreHubTokens.Palette.textSecondary, lineLimit: 2)
            }
        }
        .padding(.vertical, CoreHubTokens.Layout.sessionRowVertical)
        .padding(.horizontal, CoreHubTokens.Layout.sessionRowHorizontal)
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
    }
}

/// The pure part of the sheet, mirrored from `SessionSearchModal.vue`.
enum SessionSearchModel {
    static let recentLimit = 8
    static let resultLimit = 10
    static let debounceMilliseconds = 250

    static func hasQuery(_ query: String) -> Bool {
        !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    /// Recent sessions stand in for results while the field is empty; their
    /// preview is the "snippet".
    static func items(query: String, recent: [SessionSummary], results: [SessionSearchResult]) -> [SessionSearchResult] {
        guard hasQuery(query) else { return recent.map { SessionSearchResult(session: $0, snippet: $0.preview) } }
        return results
    }

    /// The desktop opens `global_agent` results as the Global Agent's own
    /// conversation rather than as a plain session.
    static func opensGlobalAgent(_ session: SessionSummary) -> Bool { session.source == "global_agent" }

    /// `getItemTitle`: title, else preview, else the id.
    static func title(of session: SessionSummary) -> String {
        if let title = session.title.nilIfEmpty { return title }
        if let preview = session.preview.nilIfEmpty { return preview }
        return session.id
    }

    /// `formatSource` in the modal.
    static func sourceLabel(_ source: String) -> String {
        switch source {
        case "api_server": return "API Server"
        case "cli": return "CLI"
        case "telegram": return "Telegram"
        case "discord": return "Discord"
        case "slack": return "Slack"
        case "matrix": return "Matrix"
        case "whatsapp": return "WhatsApp"
        case "signal": return "Signal"
        case "cron": return "Cron"
        case "weixin": return "WeChat"
        case "global_agent": return NavDestination.globalAgent.title
        case "": return String(localized: "Unknown source")
        default: return source
        }
    }
}
