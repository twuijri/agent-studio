import SwiftUI

// The four Models page tabs beside the provider catalogue
// (`ModelsView.vue:186-204`): Auxiliary Models, Model Ensembles, STT
// providers and TTS providers. Server-side voice configuration lives here,
// not under Settings.

/// `AuxiliaryModelsPanel.vue`: the provider and model of each auxiliary task.
struct AuxiliaryModelsView: View {
    @EnvironmentObject private var store: AppStore
    @State private var auxiliary: AuxiliaryModels?
    @State private var config: JSON = [:]
    @State private var loading = true
    @State private var state: SaveState = .idle

    var body: some View {
        Form {
            if let auxiliary {
                if auxiliary.tasks.isEmpty { Section { Text("The server reports no auxiliary tasks.").foregroundStyle(.secondary) } }
                ForEach(auxiliary.tasks) { task in taskSection(task) }
                Section { SaveButton(title: String(localized: "Save settings"), state: state) { Task { await save() } } }
            } else if !loading {
                Section { Text("Auxiliary models could not be loaded.").foregroundStyle(.secondary) }
            }
        }
        .overlay { if loading && auxiliary == nil { ProgressView() } }
        .task(id: store.selectedProfile) { await load() }
        .refreshable { await load() }
    }

    private func taskSection(_ task: AuxiliaryModels.Task) -> some View {
        Section(task.label) {
            TextField("Provider", text: binding(task.key, "provider")).textInputAutocapitalization(.never).autocorrectionDisabled()
            TextField("Model", text: binding(task.key, "model")).textInputAutocapitalization(.never).autocorrectionDisabled()
            TextField("Base URL (optional)", text: binding(task.key, "base_url")).keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
        }
    }

    private func binding(_ task: String, _ key: String) -> Binding<String> {
        Binding(
            get: { config.object(task).string(key) },
            set: { value in var entry = config.object(task); entry[key] = value; config[task] = entry }
        )
    }

    private func load() async {
        loading = true
        if let loaded = await store.attempt({ try await store.api.auxiliaryModels() }) { auxiliary = loaded; config = loaded.config }
        loading = false
    }

    private func save() async {
        state = .saving
        do { try await store.api.saveAuxiliaryModels(config); state = .saved; await load() } catch { state = .failed(error.localizedDescription) }
    }
}

/// `CombinationModelsPanel.vue`: the mixture-of-agents presets. The phone
/// switches the ensemble on or off and picks the active preset; editing the
/// reference models of a preset stays on the desktop.
struct ModelEnsemblesView: View {
    @EnvironmentObject private var store: AppStore
    @State private var moa: JSON = [:]
    @State private var loading = true
    @State private var state: SaveState = .idle

    private var presets: [(id: String, preset: JSON)] {
        moa.object("presets").compactMap { key, value in (value as? JSON).map { (id: key, preset: $0) } }.sorted { $0.id < $1.id }
    }

    var body: some View {
        Form {
            if !loading && moa.isEmpty {
                Section { Text("Model ensembles could not be loaded.").foregroundStyle(.secondary) }
            } else if !moa.isEmpty {
                Section {
                    Toggle("Ensemble enabled", isOn: Binding(get: { moa.bool("enabled") }, set: { moa["enabled"] = $0 }))
                    Picker("Active preset", selection: Binding(get: { moa.string("active_preset").nilIfEmpty ?? moa.string("default_preset") }, set: { moa["active_preset"] = $0 })) {
                        ForEach(presets, id: \.id) { entry in Text(entry.id).tag(entry.id) }
                    }
                }
                ForEach(presets, id: \.id) { entry in presetSection(entry.id, entry.preset) }
                Section { SaveButton(title: String(localized: "Save settings"), state: state) { Task { await save() } } } footer: {
                    Text("Reference models and aggregators are edited on the desktop.")
                }
            }
        }
        .overlay { if loading && moa.isEmpty { ProgressView() } }
        .task(id: store.selectedProfile) { await load() }
        .refreshable { await load() }
    }

    private func presetSection(_ id: String, _ preset: JSON) -> some View {
        Section {
            ForEach(preset.objects("reference_models").indices, id: \.self) { index in
                let slot = preset.objects("reference_models")[index]
                LabeledContent("Reference") { TechnicalText(text: ModelEnsemblesView.slotLabel(slot)) }
            }
            LabeledContent("Aggregator") { TechnicalText(text: ModelEnsemblesView.slotLabel(preset.object("aggregator"))) }
            LabeledContent("Maximum tokens", value: "\(preset.int("max_tokens"))")
        } header: {
            HStack(spacing: 6) {
                Text(id)
                if preset.bool("enabled") { StatusPill(text: String(localized: "Enabled"), color: .green) }
            }
        }
    }

    /// `provider/model` (with the reasoning effort when set).
    static func slotLabel(_ slot: JSON) -> String {
        let base = [slot.string("provider"), slot.string("model")].filter { !$0.isEmpty }.joined(separator: "/")
        let effort = slot.string("reasoning_effort")
        return effort.isEmpty ? base : "\(base) · \(effort)"
    }

    private func load() async {
        loading = true
        moa = (await store.attempt({ try await store.api.moaConfig() })) ?? moa
        loading = false
    }

    private func save() async {
        state = .saving
        do { try await store.api.saveMoaConfig(moa); state = .saved; await load() } catch { state = .failed(error.localizedDescription) }
    }
}

/// `VoiceSettings.vue kind="stt"`: the transcription providers stored on
/// the server and which one is active for the profile.
struct SttProvidersView: View {
    @EnvironmentObject private var store: AppStore
    @State private var settings: SttSettings?
    @State private var loading = true
    @State private var switching = ""

    var body: some View {
        List {
            if let settings {
                Section {
                    ForEach(settings.selectableProviders, id: \.self) { provider in
                        VoiceProviderRow(
                            title: SttProviderCatalog.label(provider),
                            detail: settings.providers.first(where: { $0.provider == provider })?.detail ?? "",
                            hasStoredKey: settings.providers.first(where: { $0.provider == provider })?.hasStoredKey ?? false,
                            active: settings.activeProvider == provider,
                            busy: switching == provider
                        ) { Task { await activate(provider) } }
                    }
                } footer: {
                    Text("Keys and models of a voice provider are entered on the desktop; the phone picks which one the profile uses.")
                }
            } else if !loading {
                Section { Text("Voice providers could not be loaded.").foregroundStyle(.secondary) }
            }
        }
        .listStyle(.insetGrouped)
        .overlay { if loading && settings == nil { ProgressView() } }
        .task(id: store.selectedProfile) { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        loading = true
        settings = (await store.attempt({ try await store.api.sttSettings(profile: store.selectedProfile) })) ?? settings
        loading = false
    }

    private func activate(_ provider: String) async {
        switching = provider
        defer { switching = "" }
        guard await store.attempt({ try await store.api.setActiveSttProvider(provider, profile: store.selectedProfile) }) != nil else { return }
        await load()
    }
}

/// `VoiceSettings.vue kind="tts"`: the speech providers stored on the
/// server and which one is active. The phone's own voice choice for spoken
/// replies is under Settings → This device.
struct TtsProvidersView: View {
    @EnvironmentObject private var store: AppStore
    @State private var settings: TtsSettings?
    @State private var loading = true
    @State private var switching = ""

    var body: some View {
        List {
            if let settings {
                Section {
                    ForEach(settings.selectableProviders, id: \.self) { provider in
                        VoiceProviderRow(
                            title: TtsProviderCatalog.label(provider),
                            detail: settings.setting(for: provider)?.detail ?? "",
                            hasStoredKey: settings.setting(for: provider)?.hasStoredKey ?? false,
                            active: settings.activeProvider == provider,
                            busy: switching == provider
                        ) { Task { await activate(provider) } }
                    }
                } footer: {
                    Text("Keys and voices of a provider are entered on the desktop; the phone picks which one the profile uses.")
                }
            } else if !loading {
                Section { Text("Voice providers could not be loaded.").foregroundStyle(.secondary) }
            }
        }
        .listStyle(.insetGrouped)
        .overlay { if loading && settings == nil { ProgressView() } }
        .task(id: store.selectedProfile) { await load() }
        .refreshable { await load() }
    }

    private func load() async {
        loading = true
        settings = (await store.attempt({ try await store.api.ttsSettings(profile: store.selectedProfile) })) ?? settings
        loading = false
    }

    private func activate(_ provider: String) async {
        switching = provider
        defer { switching = "" }
        guard await store.attempt({ try await store.api.setActiveTtsProvider(provider, profile: store.selectedProfile) }) != nil else { return }
        await load()
    }
}

/// One provider row shared by the STT and TTS tabs.
struct VoiceProviderRow: View {
    let title: String
    let detail: String
    let hasStoredKey: Bool
    let active: Bool
    let busy: Bool
    let activate: () -> Void

    var body: some View {
        Button(action: activate) {
            HStack(spacing: 10) {
                Image(systemName: active ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(active ? CoreHubTokens.Palette.accent : CoreHubTokens.Palette.textMuted)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).foregroundStyle(.primary)
                    if !detail.isEmpty { TechnicalText(text: detail) }
                }
                Spacer(minLength: 0)
                if busy { ProgressView().controlSize(.small) }
                if hasStoredKey { StatusPill(text: String(localized: "Key stored"), color: CoreHubTokens.Palette.success) }
            }
        }
        .disabled(busy || active)
    }
}
