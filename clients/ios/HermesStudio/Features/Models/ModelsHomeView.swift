import SwiftUI

/// The Models screen, shaped like the web's `views/hermes/ModelsView.vue` →
/// `ProvidersPanel.vue` → `ProviderCard.vue`: the service providers this
/// profile is configured for, and under each one the models it offers.
///
/// It is deliberately **not** a credential screen. Keys, base URLs, visibility
/// and custom models live one level down in `ProviderCatalogView`, the same
/// way the web hides them behind the card's Edit action.
struct ModelsHomeView: View {
    @EnvironmentObject private var store: AppStore
    @State private var catalog: ModelCatalog?
    @State private var loading = true
    @State private var expanded: Set<String> = []
    @State private var addingProvider = false
    @State private var refreshingCache = false
    @State private var busyModel: String?

    private var groups: [ModelCatalog.Group] { catalog?.groups ?? [] }

    var body: some View {
        List {
            defaultSection
            if groups.isEmpty && !loading {
                emptySection
            } else {
                ForEach(groups) { group in providerSection(group) }
            }
            actionsSection
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Models")
        .navigationBarTitleDisplayMode(.inline)
        .overlay { if loading && catalog == nil { ProgressView() } }
        .refreshable { await load() }
        .task(id: store.selectedProfile) { await load() }
        .sheet(isPresented: $addingProvider) { CustomProviderView { await load() } }
    }

    // MARK: Sections

    private var defaultSection: some View {
        Section {
            LabeledContent("Default model") { Text(defaultModelLabel).foregroundStyle(.secondary) }
            Button { Task { await refreshCache() } } label: {
                HStack {
                    Label("Refresh model cache", systemImage: "arrow.triangle.2.circlepath")
                    Spacer()
                    if refreshingCache { ProgressView().controlSize(.small) }
                }
            }
            .disabled(refreshingCache)
        } footer: {
            Text("The providers below are the ones the profile \(store.selectedProfile) has credentials for.")
        }
    }

    private var emptySection: some View {
        Section {
            ContentUnavailableView {
                Label("No providers yet", systemImage: "cube.transparent")
            } description: {
                Text("Add a provider to give this profile models to run.")
            } actions: {
                Button("Add a provider") { addingProvider = true }.buttonStyle(.borderedProminent)
            }
        }
    }

    private func providerSection(_ group: ModelCatalog.Group) -> some View {
        Section {
            ProviderModelsDisclosure(
                group: group,
                catalog: catalog,
                expanded: binding(for: group.id),
                busyModel: busyModel,
                select: { model in Task { await setDefault(model: model, provider: group.id) } },
                reload: { await load() }
            )
            NavigationLink {
                ProviderCatalogView(group: group, catalog: catalog) { await load() }
            } label: {
                Label("Provider settings", systemImage: "slider.horizontal.3")
            }
        } header: {
            ProviderSectionHeader(group: group, isDefault: catalog?.defaultProvider == group.id)
        } footer: {
            ProviderSectionFooter(group: group, catalog: catalog)
        }
    }

    private var actionsSection: some View {
        Section {
            Button { addingProvider = true } label: { Label("Add a provider", systemImage: "plus.circle") }
            NavigationLink { ProviderSignInView() } label: { Label("Provider sign-in", systemImage: "person.badge.key.fill") }
        } footer: {
            Text("Keys are stored by Core Hub; the app never keeps them on the device.")
        }
    }

    // MARK: State

    private var defaultModelLabel: String {
        guard let catalog, !catalog.defaultModel.isEmpty else { return String(localized: "Not set") }
        let name = catalog.displayName(provider: catalog.defaultProvider, model: catalog.defaultModel)
        guard let provider = catalog.groups.first(where: { $0.id == catalog.defaultProvider }) else { return name }
        return "\(name) · \(provider.label)"
    }

    private func binding(for id: String) -> Binding<Bool> {
        Binding(
            get: { expanded.contains(id) },
            set: { value in if value { expanded.insert(id) } else { expanded.remove(id) } }
        )
    }

    private func load() async {
        loading = true
        catalog = (await store.attempt({ try await store.api.modelCatalog(profile: store.selectedProfile) })) ?? catalog
        // Open the default provider first, the way the web card highlights it.
        if expanded.isEmpty, let provider = catalog?.defaultProvider, !provider.isEmpty { expanded.insert(provider) }
        loading = false
    }

    private func setDefault(model: String, provider: String) async {
        busyModel = model
        defer { busyModel = nil }
        guard await store.attempt({
            try await store.api.setDefaultModel(profile: store.selectedProfile, model: model, provider: provider)
        }) != nil else { return }
        store.notify(String(localized: "Default model updated"))
        await load()
    }

    private func refreshCache() async {
        refreshingCache = true
        defer { refreshingCache = false }
        guard await store.attempt({ try await store.api.refreshProviderCache() }) != nil else { return }
        await load()
    }
}

// MARK: - Provider header, footer and model list

/// The provider name plus the web card's badges: the default marker and
/// Built in / Custom.
struct ProviderSectionHeader: View {
    let group: ModelCatalog.Group
    let isDefault: Bool

    var body: some View {
        HStack(spacing: 7) {
            Text(group.label)
                .font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.sessionTitle, weight: .semibold))
                .lineLimit(1)
                .contentDirection(of: group.label)
            if isDefault { StatusPill(text: String(localized: "Default"), color: CoreHubTokens.Palette.accent) }
            StatusPill(
                text: group.isCustom ? String(localized: "Custom") : String(localized: "Built in"),
                color: group.isCustom ? CoreHubTokens.Palette.info : CoreHubTokens.Palette.textMuted
            )
            Spacer(minLength: 0)
            Circle()
                .fill(group.apiKeyConfigured ? CoreHubTokens.Palette.success : CoreHubTokens.Palette.warning)
                .frame(width: 7, height: 7)
                .accessibilityLabel(Text(group.apiKeyConfigured ? "Configured" : "No key configured"))
        }
        .textCase(nil)
    }
}

/// The card's identity lines: the credential pool key, the base URL and the
/// OpenCode-free catalogue hint the web shows while its list is loading.
struct ProviderSectionFooter: View {
    let group: ModelCatalog.Group
    let catalog: ModelCatalog?

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            TechnicalText(text: group.id)
            if !group.baseURL.isEmpty { TechnicalText(text: group.baseURL) }
            if let hint = catalogHint { Text(hint) }
        }
    }

    private var catalogHint: String? {
        guard group.id == "opencode-free" else { return nil }
        switch group.catalogStatus {
        case "loading": return String(localized: "The free model list is still loading.")
        case "error": return String(localized: "The free model list could not be loaded. Pull to refresh.")
        case "unsupported": return String(localized: "This Hermes runtime is too old for the free model list.")
        default: return nil
        }
    }
}

/// "N models" that opens into the provider's models, with the default one
/// ticked. Tapping a model makes it the default for this provider — the web's
/// per-card default-model select, adapted to a phone.
struct ProviderModelsDisclosure: View {
    let group: ModelCatalog.Group
    let catalog: ModelCatalog?
    @Binding var expanded: Bool
    let busyModel: String?
    let select: (String) -> Void
    let reload: () async -> Void

    @State private var editing: String?

    private var models: [String] {
        let all = catalog?.allModels(of: group) ?? group.availableModels
        return all.filter { catalog?.isVisible(provider: group.id, model: $0) != false }
    }

    var body: some View {
        DisclosureGroup(isExpanded: $expanded) {
            if models.isEmpty {
                Text("This provider offers no models yet.").font(CoreHubTokens.Typography.metaFont).foregroundStyle(.secondary)
            }
            ForEach(models, id: \.self) { model in
                ProviderModelRow(
                    model: model,
                    group: group,
                    catalog: catalog,
                    busy: busyModel == model,
                    select: { select(model) },
                    edit: { editing = model }
                )
            }
        } label: {
            countLabel
        }
        .sheet(item: Binding(get: { editing.map { ModelEditTarget(provider: group.id, model: $0) } },
                             set: { editing = $0?.model })) { target in
            ModelDetailView(target: target, alias: catalog?.alias(provider: target.provider, model: target.model) ?? "") {
                await reload()
            }
        }
    }

    private var countLabel: some View {
        HStack {
            Label("Models", systemImage: "cpu")
            Spacer()
            Text(catalog?.modelCountLabel(of: group) ?? "\(group.models.count)")
                .font(CoreHubTokens.Typography.metaFont)
                .foregroundStyle(CoreHubTokens.Palette.textMuted)
                .technicalDirection()
        }
    }
}

/// One model of one provider.
struct ProviderModelRow: View {
    let model: String
    let group: ModelCatalog.Group
    let catalog: ModelCatalog?
    let busy: Bool
    let select: () -> Void
    let edit: () -> Void

    private var isDefault: Bool { catalog?.isDefault(provider: group.id, model: model) == true }
    private var alias: String? { catalog?.alias(provider: group.id, model: model) }

    var body: some View {
        HStack(spacing: 10) {
            Button(action: select) {
                HStack(spacing: 10) {
                    Image(systemName: isDefault ? "checkmark.circle.fill" : "circle")
                        .foregroundStyle(isDefault ? CoreHubTokens.Palette.accent : CoreHubTokens.Palette.textMuted)
                    VStack(alignment: .leading, spacing: 2) {
                        DirectionalText(text: catalog?.displayName(provider: group.id, model: model) ?? model,
                                        font: CoreHubTokens.Typography.font(CoreHubTokens.Typography.base))
                        if alias != nil { TechnicalText(text: model) }
                    }
                    Spacer(minLength: 0)
                }
            }
            .buttonStyle(.plain)
            .disabled(busy)

            if busy { ProgressView().controlSize(.small) }
            if catalog?.isCustom(provider: group.id, model: model) == true {
                StatusPill(text: String(localized: "Custom"), color: CoreHubTokens.Palette.info)
            }
            Button(action: edit) {
                Image(systemName: "ellipsis.circle").foregroundStyle(CoreHubTokens.Palette.textMuted)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Model settings")
        }
    }
}
