import SwiftUI

/// One section of the profile's Hermes config (`GET`/`PUT /api/hermes/config`).
/// The app-level tabs (Display, Proxy, Compression, Privacy) and the tabs
/// under Hermes → Settings (Agent + Gateway, Memory, Session + approvals)
/// are all built from these.
enum StudioSettingsSection: String {
    case display, proxy, agent, memory, compression, session, approvals, skills, privacy, gateway

    var apiName: String {
        switch self {
        case .session: return "sessionReset"
        case .gateway: return "gatewayAutoStart"
        default: return rawValue
        }
    }

    var title: LocalizedStringKey {
        switch self {
        case .display: return "Display"
        case .proxy: return "Proxy"
        case .agent: return "Agent"
        case .memory: return "Memory"
        case .compression: return "Compression"
        case .session: return "Session reset"
        case .approvals: return "Approvals"
        case .skills: return "Skill approvals"
        case .privacy: return "Privacy"
        case .gateway: return "Gateway auto-start"
        }
    }

    var note: LocalizedStringKey {
        switch self {
        case .display: return "Choose what Studio shows while agents work."
        case .proxy: return "Network proxy variables used by this profile."
        case .agent: return "Control run limits and tool behavior."
        case .memory: return "Manage long-term memory and user context."
        case .compression: return "Keep long conversations within the model context."
        case .session: return "Choose when a conversation starts a fresh session."
        case .approvals: return "Require confirmation before sensitive actions."
        case .skills: return "Control approval before the agent writes or changes skills."
        case .privacy: return "Protect personal information sent to models."
        case .gateway: return "Choose whether gateways start with Studio."
        }
    }

    /// Saving these restarts the runtime, as the web does.
    var restartsRuntime: Bool { self == .agent || self == .proxy || self == .gateway }

    var fields: [ConfigField] {
        switch self {
        case .display: return [
            .choice("busy_input_mode", "While the agent is busy", "queue", ["queue", "steer", "interrupt"]),
            .toggle("streaming", "Stream responses", true), .toggle("compact", "Compact layout", false),
            .toggle("show_reasoning", "Show reasoning", true), .toggle("show_cost", "Show cost", false),
            .toggle("inline_diffs", "Inline diffs", true), .toggle("bell_on_complete", "Bell on completion", false),
            .toggle("approval_bell", "Bell on approval", false), .toggle("notify_on_approval", "Approval notification", false),
            .toggle("notify_on_complete", "Completion notification", false), .number("chat_input_height", "Chat input height", 0),
        ]
        case .proxy: return [.text("HTTPS_PROXY", "HTTPS proxy"), .text("HTTP_PROXY", "HTTP proxy"), .text("ALL_PROXY", "All-protocol proxy"), .text("NO_PROXY", "Exclude hosts")]
        case .agent: return [.number("max_turns", "Maximum turns", 0), .number("gateway_timeout", "Gateway timeout", 0), .number("restart_drain_timeout", "Restart drain timeout", 30), .choice("tool_use_enforcement", "Tool use", "auto", ["auto", "required", "off"])]
        case .memory: return [.toggle("memory_enabled", "Memory enabled", true), .toggle("user_profile_enabled", "User profile memory", true), .number("memory_char_limit", "Memory character limit", 2000), .number("user_char_limit", "User context limit", 2000), .toggle("write_approval", "Approve memory writes", false)]
        case .compression: return [.toggle("enabled", "Compression enabled", true), .decimal("threshold", "Compression threshold", 0.5), .decimal("target_ratio", "Target ratio", 0.2), .number("protect_last_n", "Protect latest messages", 20), .number("protect_first_n", "Protect first messages", 3)]
        case .session: return [.choice("mode", "Reset mode", "both", ["off", "idle", "daily", "both"]), .number("idle_minutes", "Idle minutes", 60), .number("at_hour", "Daily reset hour", 0)]
        case .approvals: return [.choice("mode", "Approval mode", "off", ["off", "ask", "always"])]
        case .skills: return [.toggle("write_approval", "Approve skill changes", false)]
        case .privacy: return [.toggle("redact_pii", "Redact personal information", false)]
        case .gateway: return [.toggle("enabled", "Start gateways automatically", true), .choice("management", "Management", "per_profile", ["per_profile", "all"])]
        }
    }
}

struct ConfigField: Identifiable {
    enum Kind { case toggle, text, number, decimal, choice([String]) }
    let key: String; let title: LocalizedStringKey; let kind: Kind; let fallback: Any
    var id: String { key }
    static func toggle(_ key: String, _ title: LocalizedStringKey, _ value: Bool) -> Self { .init(key: key, title: title, kind: .toggle, fallback: value) }
    static func text(_ key: String, _ title: LocalizedStringKey) -> Self { .init(key: key, title: title, kind: .text, fallback: "") }
    static func number(_ key: String, _ title: LocalizedStringKey, _ value: Int) -> Self { .init(key: key, title: title, kind: .number, fallback: value) }
    static func decimal(_ key: String, _ title: LocalizedStringKey, _ value: Double) -> Self { .init(key: key, title: title, kind: .decimal, fallback: value) }
    static func choice(_ key: String, _ title: LocalizedStringKey, _ value: String, _ options: [String]) -> Self { .init(key: key, title: title, kind: .choice(options), fallback: value) }
}

/// One section as its own screen (Settings → Proxy, Compression, Privacy).
struct StudioSectionSettings: View {
    let section: StudioSettingsSection

    var body: some View {
        StudioSectionsForm(sections: [section])
            .navigationTitle(section.title)
            .navigationBarTitleDisplayMode(.inline)
    }
}

/// Several sections in one form with one Save (a tab of Hermes → Settings,
/// or the server part of Display). Each section is written with its own
/// `PUT`, restarting the runtime where the web does.
struct StudioSectionsForm: View {
    @EnvironmentObject private var store: AppStore
    let sections: [StudioSettingsSection]
    @State private var values: [StudioSettingsSection: JSON] = [:]
    @State private var loading = true
    @State private var state: SaveState = .idle

    var body: some View {
        Form {
            ForEach(sections, id: \.rawValue) { section in
                Section {
                    ForEach(section.fields) { field in control(section, field) }
                } header: {
                    if sections.count > 1 { Text(section.title) }
                } footer: {
                    Text(section.note)
                }
            }
            Section {
                SaveButton(title: String(localized: "Save settings"), state: state) { Task { await save() } }
            } footer: {
                Text("Changes are written to the selected profile: \(store.selectedProfile)")
            }
        }
        .overlay { if loading { ProgressView() } }
        .task(id: store.selectedProfile) { await load() }
    }

    @ViewBuilder private func control(_ section: StudioSettingsSection, _ field: ConfigField) -> some View {
        switch field.kind {
        case .toggle:
            Toggle(field.title, isOn: Binding(get: { (values[section]?[field.key] as? Bool) ?? (field.fallback as? Bool ?? false) }, set: { values[section, default: [:]][field.key] = $0 }))
        case .text:
            TextField(field.title, text: stringBinding(section, field)).textInputAutocapitalization(.never).autocorrectionDisabled()
        case .number:
            TextField(field.title, text: numberBinding(section, field, decimal: false)).keyboardType(.numberPad)
        case .decimal:
            TextField(field.title, text: numberBinding(section, field, decimal: true)).keyboardType(.decimalPad)
        case let .choice(options):
            Picker(field.title, selection: stringBinding(section, field)) {
                ForEach(options, id: \.self) { Text($0.capitalized.replacingOccurrences(of: "_", with: " ")).tag($0) }
            }
        }
    }

    private func stringBinding(_ section: StudioSettingsSection, _ field: ConfigField) -> Binding<String> {
        Binding(
            get: { (values[section] ?? [:]).string(field.key).nilIfEmpty ?? (field.fallback as? String ?? "") },
            set: { values[section, default: [:]][field.key] = $0 }
        )
    }

    private func numberBinding(_ section: StudioSettingsSection, _ field: ConfigField, decimal: Bool) -> Binding<String> {
        Binding(
            get: {
                if let number = values[section]?[field.key] as? NSNumber { return number.stringValue }
                return String(describing: field.fallback)
            },
            set: { values[section, default: [:]][field.key] = decimal ? (Double($0) ?? 0) : (Int($0) ?? 0) }
        )
    }

    private func load() async {
        loading = true
        defer { loading = false }
        guard let root = await store.attempt({ try await store.api.config(profile: store.selectedProfile) }) else { return }
        var loaded: [StudioSettingsSection: JSON] = [:]
        for section in sections {
            var current = root.object(section.apiName)
            for field in section.fields where current[field.key] == nil { current[field.key] = field.fallback }
            loaded[section] = current
        }
        values = loaded
    }

    private func save() async {
        state = .saving
        do {
            for section in sections {
                try await store.api.updateConfig(profile: store.selectedProfile, section: section.apiName, values: values[section] ?? [:], restart: section.restartsRuntime)
            }
            state = .saved
        } catch {
            state = .failed(error.localizedDescription)
        }
    }
}
