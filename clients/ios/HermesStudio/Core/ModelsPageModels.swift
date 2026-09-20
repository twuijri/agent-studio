import Foundation

// Payloads of the Models page tabs that are not the provider catalogue:
// auxiliary models, STT providers and the DSH agent presets.

/// `GET /api/hermes/config/auxiliary-models`: the tasks the server knows and
/// the provider/model configured for each (`AuxiliaryModelsResponse`).
struct AuxiliaryModels: Equatable {
    struct Task: Identifiable, Equatable {
        let key: String
        let label: String
        var id: String { key }
    }

    let tasks: [Task]
    /// `auxiliary[taskKey] = { provider, model, base_url, api_key, timeout }`.
    let config: JSON

    init(_ json: JSON) {
        tasks = json.objects("tasks").map { Task(key: $0.string("key"), label: $0.string("label").nilIfEmpty ?? $0.string("key")) }.filter { !$0.key.isEmpty }
        config = json.object("auxiliary")
    }

    func provider(of task: String) -> String { config.object(task).string("provider") }
    func model(of task: String) -> String { config.object(task).string("model") }

    static func == (lhs: AuxiliaryModels, rhs: AuxiliaryModels) -> Bool {
        lhs.tasks == rhs.tasks && NSDictionary(dictionary: lhs.config).isEqual(to: rhs.config)
    }
}

/// The server's stored STT provider list (`api/studio/stt-settings.ts`).
enum SttProviderCatalog {
    static let all = ["browser", "local", "openai", "custom", "doubao", "groq", "mistral", "xai", "elevenlabs", "deepinfra"]

    static func isKnown(_ provider: String) -> Bool { all.contains(provider) }

    static func label(_ provider: String) -> String {
        switch provider {
        case "browser": return String(localized: "Browser speech")
        case "local": return String(localized: "Local model")
        case "openai": return "OpenAI"
        case "custom": return String(localized: "Custom STT")
        case "doubao": return "Doubao"
        case "groq": return "Groq"
        case "mistral": return "Mistral"
        case "xai": return "xAI"
        case "elevenlabs": return "ElevenLabs"
        case "deepinfra": return "DeepInfra"
        default: return provider
        }
    }
}

/// One stored STT provider row; the key comes back as `[stored]` only.
struct SttProviderSetting: Identifiable, Equatable {
    let provider: String
    let model: String
    let language: String
    let baseURL: String
    let hasStoredKey: Bool
    var id: String { provider }

    init(_ json: JSON) {
        let settings = json.object("settings")
        provider = json.string("provider")
        model = settings.string("model")
        language = settings.string("language")
        baseURL = settings.string("baseUrl")
        hasStoredKey = !json.object("secrets").string("apiKey").isEmpty
    }

    var detail: String { [model, language].compactMap(\.nilIfEmpty).joined(separator: " · ") }
}

/// `GET /api/studio/stt/settings`, read like `TtsSettings`: both `providers`
/// and `settings` are accepted, and a JSON `null` active provider is `nil`.
struct SttSettings: Equatable {
    let providers: [SttProviderSetting]
    let activeProvider: String?

    init(_ json: JSON) {
        let rows = json.objects("providers").isEmpty ? json.objects("settings") : json.objects("providers")
        providers = rows.map(SttProviderSetting.init).filter { SttProviderCatalog.isKnown($0.provider) }
        let raw = (json.value("activeProvider", "active_provider") as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        activeProvider = SttProviderCatalog.isKnown(raw) ? raw : nil
    }

    /// Every stored provider plus the active one when it has no row, plus
    /// the two providers that need no configuration.
    var selectableProviders: [String] {
        var ids = providers.map(\.provider)
        for builtIn in ["browser", "local"] where !ids.contains(builtIn) { ids.append(builtIn) }
        if let activeProvider, !ids.contains(activeProvider) { ids.append(activeProvider) }
        return ids
    }
}

/// One DSH agent preset (`DshAgentPreset` in `api/coding-agents/dsh.ts`).
struct DshPreset: Identifiable, Equatable {
    let id: String
    let name: String
    let description: String
    let trust: String
    let isDefault: Bool
    let broken: String

    init(_ json: JSON) {
        id = json.string("id")
        name = json.string("name").nilIfEmpty ?? json.string("id")
        description = json.string("description")
        trust = json.string("trust")
        isDefault = json.bool("isDefault")
        broken = json.string("broken")
    }
}
