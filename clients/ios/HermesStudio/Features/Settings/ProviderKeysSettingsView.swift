import SwiftUI

/// Settings → Models (`ModelSettings.vue`): the API key of every configured
/// provider, nothing else. It is **not** the Models page — the subtitle in
/// Settings says "Provider keys" and the link below opens the real page.
struct ProviderKeysSettingsView: View {
    @EnvironmentObject private var store: AppStore
    @State private var catalog: ModelCatalog?
    @State private var keys: [String: String] = [:]
    @State private var states: [String: SaveState] = [:]
    @State private var loading = true

    private var groups: [ModelCatalog.Group] { catalog?.groups ?? [] }

    var body: some View {
        List {
            Section {
                Button { store.push(.models) } label: { Label("Open the Models page", systemImage: "arrow.up.forward.square") }
            } footer: {
                Text("Providers, models, ensembles and voice providers are managed on the Models page. This tab only stores keys.")
            }
            if !loading && groups.isEmpty {
                Section { Text("No providers are configured for this profile.").foregroundStyle(.secondary) }
            }
            ForEach(groups) { group in providerSection(group) }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Models")
        .navigationBarTitleDisplayMode(.inline)
        .overlay { if loading && catalog == nil { ProgressView() } }
        .task(id: store.selectedProfile) { await load() }
        .refreshable { await load() }
    }

    private func providerSection(_ group: ModelCatalog.Group) -> some View {
        Section {
            SecureField("API key", text: Binding(get: { keys[group.id] ?? "" }, set: { keys[group.id] = $0 }))
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            SaveButton(title: String(localized: "Save key"), state: states[group.id] ?? .idle) { Task { await save(group) } }
                .disabled((keys[group.id] ?? "").trimmingCharacters(in: .whitespaces).isEmpty)
        } header: {
            HStack(spacing: 7) {
                Text(group.label).contentDirection(of: group.label)
                StatusPill(text: group.isCustom ? String(localized: "Custom") : String(localized: "Built in"), color: group.isCustom ? CoreHubTokens.Palette.info : CoreHubTokens.Palette.textMuted)
                Spacer(minLength: 0)
                Circle().fill(group.apiKeyConfigured ? CoreHubTokens.Palette.success : CoreHubTokens.Palette.warning).frame(width: 7, height: 7)
                    .accessibilityLabel(Text(group.apiKeyConfigured ? "Configured" : "No key configured"))
            }
            .textCase(nil)
        } footer: {
            TechnicalText(text: group.id)
        }
    }

    private func load() async {
        loading = true
        catalog = (await store.attempt({ try await store.api.modelCatalog(profile: store.selectedProfile) })) ?? catalog
        loading = false
    }

    private func save(_ group: ModelCatalog.Group) async {
        states[group.id] = .saving
        do {
            try await store.api.updateProviderPool(group.id, apiKey: keys[group.id] ?? "", baseURL: nil, name: nil)
            states[group.id] = .saved
            keys[group.id] = ""
            await load()
        } catch {
            states[group.id] = .failed(error.localizedDescription)
        }
    }
}
