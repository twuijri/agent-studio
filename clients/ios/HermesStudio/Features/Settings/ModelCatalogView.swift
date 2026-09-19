import SwiftUI

// The Models screen itself now lives in `Features/Models/ModelsHomeView.swift`
// and mirrors the web's ProvidersPanel: providers, and under each the models
// it offers. What stays here is the per-provider editor it pushes to — the
// counterpart of the web's ProviderEditorModal, alias modal and visibility
// modal — plus the custom-provider form.

/// One provider pool: credentials, the visibility rule, per-model aliases,
/// custom models and context limits.
struct ProviderCatalogView: View {
    @EnvironmentObject private var store: AppStore
    let group: ModelCatalog.Group
    let catalog: ModelCatalog?
    let reload: () async -> Void

    @State private var apiKey = ""
    @State private var baseURL = ""
    @State private var name = ""
    @State private var credentialState: SaveState = .idle
    @State private var visibilityState: SaveState = .idle
    @State private var includeOnly = false
    @State private var visible: Set<String> = []
    @State private var newModel = ""
    @State private var editingModel: String?
    @State private var catalogueBusy = ""
    @State private var catalogueNote = ""

    private var models: [String] { catalog?.allModels(of: group) ?? group.availableModels }

    var body: some View {
        List {
            credentialsSection
            catalogueSection
            visibilitySection
            modelsSection
            removeSection
        }
        .listStyle(.insetGrouped)
        .navigationTitle(group.label)
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: Binding(get: { editingModel.map { ModelEditTarget(provider: group.id, model: $0) } }, set: { editingModel = $0?.model })) { target in
            ModelDetailView(target: target, alias: catalog?.alias(provider: target.provider, model: target.model) ?? "") { await reload() }
        }
        .onAppear {
            baseURL = group.baseURL
            name = group.label
            let rule = catalog?.visibility[group.id]
            includeOnly = rule?.mode == "include"
            visible = Set(rule?.models ?? models)
        }
    }

    private var credentialsSection: some View {
        Section {
            if group.editable {
                TextField("Display name", text: $name)
                TextField("Base URL", text: $baseURL).textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.URL)
            } else {
                LabeledContent("Base URL") { TechnicalText(text: group.baseURL) }
            }
            SecureField("API key", text: $apiKey)
            SaveButton(title: String(localized: "Save provider"), state: credentialState) { Task { await saveCredentials() } }
        } header: { Text("Connection") } footer: {
            if group.apiKeyConfigured { Text("A key is configured. Leave the field empty to keep it.") }
            else { Text("No key is configured yet.") }
        }
    }

    private var visibilitySection: some View {
        Section {
            Toggle("Show only the selected models", isOn: $includeOnly)
            SaveButton(title: String(localized: "Save visibility"), state: visibilityState) { Task { await saveVisibility() } }
        } header: { Text("Visibility") } footer: { Text("Hidden models disappear from every model picker in Core Hub.") }
    }

    private var modelsSection: some View {
        Section {
            ForEach(models, id: \.self) { model in modelRow(model) }
            HStack {
                TextField("Add a custom model id", text: $newModel).textInputAutocapitalization(.never).autocorrectionDisabled().environment(\.layoutDirection, .leftToRight)
                Button("Add") { Task { await addModel() } }.disabled(newModel.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
        } header: { Text("Models") } footer: { Text("Tap a model to rename it or set its context limit.") }
    }

    private func modelRow(_ model: String) -> some View {
        HStack(spacing: 10) {
            if includeOnly {
                Button { toggle(model) } label: {
                    Image(systemName: visible.contains(model) ? "checkmark.circle.fill" : "circle")
                        .foregroundStyle(visible.contains(model) ? CoreHubTokens.Palette.accent : CoreHubTokens.Palette.textMuted)
                }
                .buttonStyle(.plain)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(catalog?.displayName(provider: group.id, model: model) ?? model).font(CoreHubTokens.Typography.sessionTitleFont)
                TechnicalText(text: model)
            }
            Spacer(minLength: 0)
            if catalog?.isCustom(provider: group.id, model: model) == true {
                StatusPill(text: String(localized: "Custom"), color: CoreHubTokens.Palette.info)
            }
            Button { editingModel = model } label: { Image(systemName: "slider.horizontal.3").foregroundStyle(CoreHubTokens.Palette.textMuted) }
                .buttonStyle(.plain)
                .accessibilityLabel("Model settings")
        }
        .swipeActions {
            if catalog?.isCustom(provider: group.id, model: model) == true {
                Button(role: .destructive) { Task { await removeModel(model) } } label: { Label("Remove", systemImage: "trash") }
            }
        }
    }

    /// The web card's catalogue actions: test the connection, re-read the
    /// provider's `/models` list, and put back a list a refresh trimmed.
    private var catalogueSection: some View {
        Section {
            Button { Task { await test() } } label: { catalogueRow("Test connection", "bolt.horizontal", key: "test") }
                .disabled(!catalogueBusy.isEmpty)
            if group.refreshable {
                Button { Task { await refreshModels() } } label: { catalogueRow("Refresh models", "arrow.clockwise", key: "refresh") }
                    .disabled(!catalogueBusy.isEmpty)
            }
            if group.restoreAvailable {
                Button { Task { await restoreModels() } } label: { catalogueRow("Restore the model list", "arrow.uturn.backward", key: "restore") }
                    .disabled(!catalogueBusy.isEmpty)
            }
        } header: { Text("Catalogue") } footer: {
            if !catalogueNote.isEmpty { Text(catalogueNote) }
            else if !group.refreshable && !group.refreshReason.isEmpty { Text(group.refreshReason) }
            else { Text("Refreshing asks the provider for its current model list.") }
        }
    }

    private func catalogueRow(_ title: LocalizedStringKey, _ icon: String, key: String) -> some View {
        HStack {
            Label(title, systemImage: icon)
            Spacer()
            if catalogueBusy == key { ProgressView().controlSize(.small) }
        }
    }

    /// The web distinguishes deleting a config-backed pool from only clearing
    /// the credentials of a built-in one; so does this.
    private var removeSection: some View {
        Section {
            Button(role: .destructive) { Task { await removePool() } } label: {
                if group.isConfigBacked { Label("Delete this provider", systemImage: "trash") }
                else { Label("Clear the stored key", systemImage: "trash") }
            }
        } footer: {
            if group.isConfigBacked { Text("The pool is removed from this profile's configuration.") }
            else { Text("The provider stays listed; only its stored credentials are cleared.") }
        }
    }

    private func toggle(_ model: String) {
        if visible.contains(model) { visible.remove(model) } else { visible.insert(model) }
    }

    private func saveCredentials() async {
        credentialState = .saving
        do {
            try await store.api.updateProviderPool(group.id, apiKey: apiKey.nilIfEmpty, baseURL: group.editable ? baseURL.nilIfEmpty : nil, name: group.editable ? name.nilIfEmpty : nil)
            apiKey = ""
            credentialState = .saved
            await reload()
        } catch { credentialState = .failed(error.localizedDescription) }
    }

    private func saveVisibility() async {
        visibilityState = .saving
        do {
            try await store.api.setModelVisibility(provider: group.id, mode: includeOnly ? "include" : "all", models: includeOnly ? Array(visible) : [])
            visibilityState = .saved
            await reload()
        } catch { visibilityState = .failed(error.localizedDescription) }
    }

    private func addModel() async {
        let model = newModel.trimmingCharacters(in: .whitespacesAndNewlines)
        guard await store.attempt({ try await store.api.addCustomModel(provider: group.id, model: model) }) != nil else { return }
        newModel = ""
        await reload()
    }

    private func removeModel(_ model: String) async {
        guard await store.attempt({ try await store.api.removeCustomModel(provider: group.id, model: model) }) != nil else { return }
        await reload()
    }

    private func removePool() async {
        guard await store.attempt({ try await store.api.removeCustomProvider(group.id) }) != nil else { return }
        await reload()
    }

    private func test() async {
        catalogueBusy = "test"
        defer { catalogueBusy = "" }
        guard let result = await store.attempt({ try await store.api.testProvider(group.id) }) else { return }
        catalogueNote = result.bool("success")
            ? String(localized: "The connection succeeded.")
            : (result.string("error", "message").nilIfEmpty ?? String(localized: "The connection failed."))
    }

    private func refreshModels() async {
        catalogueBusy = "refresh"
        defer { catalogueBusy = "" }
        guard var result = await store.attempt({ try await store.api.refreshProviderModels(group.id) }) else { return }
        // The server asks before applying a list that drops models; the phone
        // confirms in place rather than opening a second dialog.
        if result.bool("requires_confirmation") {
            guard let confirmed = await store.attempt({ try await store.api.refreshProviderModels(group.id, confirm: true) }) else { return }
            result = confirmed
        }
        guard result.bool("applied") else {
            catalogueNote = String(localized: "The model list could not be refreshed.")
            return
        }
        catalogueNote = String(localized: "The model list now has \(result.strings("models").count) models.")
        await reload()
    }

    private func restoreModels() async {
        catalogueBusy = "restore"
        defer { catalogueBusy = "" }
        guard let result = await store.attempt({ try await store.api.restoreProviderModels(group.id) }) else { return }
        guard result.bool("applied") else {
            catalogueNote = String(localized: "The model list could not be restored.")
            return
        }
        catalogueNote = String(localized: "The model list now has \(result.strings("models").count) models.")
        await reload()
    }
}

struct ModelEditTarget: Identifiable, Equatable {
    let provider: String
    let model: String
    var id: String { "\(provider)|\(model)" }
}

/// Alias and context limit of one model.
struct ModelDetailView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    let target: ModelEditTarget
    let alias: String
    let reload: () async -> Void

    @State private var name = ""
    @State private var limitText = ""
    @State private var aliasState: SaveState = .idle
    @State private var limitState: SaveState = .idle
    @State private var serverLimit: Int?

    var body: some View {
        NavigationStack {
            Form {
                Section("Model") { TechnicalText(text: target.model, font: CoreHubTokens.Typography.mono(CoreHubTokens.Typography.sidebarTab), color: CoreHubTokens.Palette.textPrimary) }
                Section {
                    TextField("Display name", text: $name)
                    SaveButton(title: String(localized: "Save name"), state: aliasState) { Task { await saveAlias() } }
                } header: { Text("Alias") } footer: { Text("An empty name restores the model id.") }
                Section {
                    TextField("Context limit in tokens", text: $limitText).keyboardType(.numberPad).environment(\.layoutDirection, .leftToRight)
                    SaveButton(title: String(localized: "Save context limit"), state: limitState) { Task { await saveLimit() } }
                } header: { Text("Context") } footer: {
                    if let serverLimit { Text("Core Hub currently uses \(serverLimit) tokens.") }
                    else { Text("Core Hub does not know this model's window yet.") }
                }
            }
            .navigationTitle("Model settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
            .task { await load() }
        }
    }

    private func load() async {
        name = alias
        guard let limit = await store.attempt({ try await store.api.modelContextLimit(provider: target.provider, model: target.model) }) else { return }
        serverLimit = limit?.contextLimit
        limitText = limit.map { String($0.contextLimit) } ?? ""
    }

    private func saveAlias() async {
        aliasState = .saving
        do {
            try await store.api.setModelAlias(provider: target.provider, model: target.model, alias: name)
            aliasState = .saved
            await reload()
        } catch { aliasState = .failed(error.localizedDescription) }
    }

    private func saveLimit() async {
        guard let value = Int(limitText.trimmingCharacters(in: .whitespaces)), value > 0 else {
            limitState = .failed(String(localized: "Enter the context limit as a number of tokens."))
            return
        }
        limitState = .saving
        do {
            try await store.api.setModelContextLimit(provider: target.provider, model: target.model, limit: value)
            serverLimit = value
            limitState = .saved
            await reload()
        } catch { limitState = .failed(error.localizedDescription) }
    }
}

/// Adds a custom provider pool (`POST /api/hermes/config/providers`).
struct CustomProviderView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    let reload: () async -> Void

    @State private var name = ""
    @State private var baseURL = ""
    @State private var apiKey = ""
    @State private var apiMode = ""
    @State private var state: SaveState = .idle

    var body: some View {
        NavigationStack {
            Form {
                Section("Provider") {
                    TextField("Name", text: $name)
                    TextField("Base URL", text: $baseURL).textInputAutocapitalization(.never).autocorrectionDisabled().keyboardType(.URL)
                    SecureField("API key", text: $apiKey)
                    Picker("API mode", selection: $apiMode) {
                        Text("Default").tag("")
                        Text("Chat Completions").tag("chat_completions")
                        Text("Responses").tag("codex_responses")
                        Text("Anthropic Messages").tag("anthropic_messages")
                    }
                }
                Section { SaveStateLabel(state: state) }
            }
            .navigationTitle("New provider")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { Task { await save() } }.disabled(name.isEmpty || baseURL.isEmpty || state.isSaving)
                }
            }
        }
    }

    private func save() async {
        state = .saving
        do {
            try await store.api.addCustomProvider(name: name, baseURL: baseURL, apiKey: apiKey, apiMode: apiMode.nilIfEmpty)
            state = .saved
            await reload()
            dismiss()
        } catch { state = .failed(error.localizedDescription) }
    }
}
