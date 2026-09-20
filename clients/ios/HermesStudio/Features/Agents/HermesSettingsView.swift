import SwiftUI

/// Hermes → Settings (`HermesSettingsView.vue:66-76`): tab `Agent` renders
/// `AgentSettings` plus `GatewayAutoStartSettings`, tab `Memory` the memory
/// settings, tab `Session` the session reset with approvals and skill
/// approvals (`SessionSettings.vue`). Agent runtime settings live here, not
/// under the app's Settings.
///
/// Drawn like the Android `HermesSettingsScreen`, which the owner found
/// tidier: a Material tab row, then one card per setting — a glyph, the
/// label, the value or note, a switch or a chevron — each written to the
/// server on its own, with the loading row above while it saves and
/// `Saved` in the banner after.
struct HermesSettingsView: View {
    @State private var tab = "agent"

    private static let tabs = [
        TabStripItem(id: "agent", title: String(localized: "Agent")),
        TabStripItem(id: "memory", title: String(localized: "Memory")),
        TabStripItem(id: "session", title: String(localized: "Session")),
    ]

    var body: some View {
        VStack(spacing: 0) {
            MaterialTabRow(items: Self.tabs, selection: $tab)
            HermesSettingsRows(sections: HermesSettingsView.sections(for: tab)).id(tab)
        }
        .hermesBackground()
        .navigationTitle(NavDestination.hermesSettings.title)
        .navigationBarTitleDisplayMode(.inline)
    }

    /// Which config sections each tab edits. Gateway auto-start sits with
    /// the agent tab, exactly as the desktop composes it.
    static func sections(for tab: String) -> [StudioSettingsSection] {
        switch tab {
        case "memory": return [.memory]
        case "session": return [.session, .approvals, .skills]
        default: return [.agent, .gateway]
        }
    }

    /// Android labels the gateway block alone (`agent_autostart_title` with
    /// its note); the Session tab is one flat list of rows.
    static func showsLabel(_ section: StudioSettingsSection) -> Bool { section == .gateway }
}

/// The rows of one Hermes settings tab. A switch saves as it flips, a
/// number opens a prompt, a choice opens the option sheet — the Android
/// `setAgentValue` / `setStudioValue` flow, one key per `PUT`.
struct HermesSettingsRows: View {
    @EnvironmentObject private var store: AppStore
    let sections: [StudioSettingsSection]

    @State private var values: [StudioSettingsSection: JSON] = [:]
    @State private var loading = true
    @State private var saving = false
    @State private var editing: HermesSettingEdit?
    @State private var choosing: HermesSettingEdit?
    @State private var draft = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                if loading || saving { LoadingRowView() }
                ForEach(sections, id: \.rawValue) { section in sectionRows(section) }
            }
            .padding(.bottom, CoreHubTokens.Layout.agentListPaddingBottom)
        }
        .task(id: store.selectedProfile) { await load() }
        .alert(Text(editing?.field.title ?? ""), isPresented: shown($editing), presenting: editing) { edit in
            TextField("", text: $draft).keyboardType(HermesSettingEdit.keyboard(for: edit.field.kind))
            Button("Save") { Task { await save(edit, HermesSettingEdit.parse(draft, kind: edit.field.kind)) } }
            Button("Cancel", role: .cancel) {}
        }
        .confirmationDialog(Text(choosing?.field.title ?? ""), isPresented: shown($choosing), titleVisibility: .visible, presenting: choosing) { edit in
            ForEach(edit.field.options, id: \.self) { option in
                Button(HermesSettingEdit.optionLabel(option)) { Task { await save(edit, option) } }
            }
            Button("Cancel", role: .cancel) {}
        }
    }

    private func shown(_ edit: Binding<HermesSettingEdit?>) -> Binding<Bool> {
        Binding(get: { edit.wrappedValue != nil }, set: { if !$0 { edit.wrappedValue = nil } })
    }

    @ViewBuilder
    private func sectionRows(_ section: StudioSettingsSection) -> some View {
        if HermesSettingsView.showsLabel(section) {
            SettingsSectionLabel(title: section.title)
            SettingsHintText(text: section.note)
        }
        ForEach(section.fields) { field in row(section, field) }
    }

    @ViewBuilder
    private func row(_ section: StudioSettingsSection, _ field: ConfigField) -> some View {
        let edit = HermesSettingEdit(section: section, field: field)
        switch field.kind {
        case .toggle:
            SettingsCardRow(icon: field.icon, title: field.title, value: Text(field.note ?? section.note), trailing: {
                Toggle(field.title, isOn: Binding(get: { bool(section, field) }, set: { value in Task { await save(edit, value) } }))
                    .labelsHidden()
                    .disabled(saving)
            })
        case .choice:
            SettingsCardRow(icon: field.icon, title: field.title, value: Text(HermesSettingEdit.optionLabel(string(section, field))), onTap: { choosing = edit })
        case .number, .decimal:
            SettingsCardRow(icon: field.icon, title: field.title, value: numberValue(section, field), onTap: {
                draft = number(section, field)
                editing = edit
            })
        case .text:
            SettingsCardRow(icon: field.icon, title: field.title, value: Text(verbatim: string(section, field)), onTap: {
                draft = string(section, field)
                editing = edit
            })
        }
    }

    /// `60 · Inactivity before a new session` (`StudioNumber`'s value line).
    private func numberValue(_ section: StudioSettingsSection, _ field: ConfigField) -> Text {
        let current = Text(verbatim: number(section, field))
        guard let note = field.note else { return current }
        return current + Text(verbatim: " · ") + Text(note)
    }

    // MARK: Values

    private func bool(_ section: StudioSettingsSection, _ field: ConfigField) -> Bool {
        (values[section]?[field.key] as? Bool) ?? (field.fallback as? Bool ?? false)
    }

    private func string(_ section: StudioSettingsSection, _ field: ConfigField) -> String {
        (values[section] ?? [:]).string(field.key).nilIfEmpty ?? (field.fallback as? String ?? "")
    }

    private func number(_ section: StudioSettingsSection, _ field: ConfigField) -> String {
        if let number = values[section]?[field.key] as? NSNumber { return number.stringValue }
        return String(describing: field.fallback)
    }

    // MARK: Server

    private func load(showLoading: Bool = true) async {
        if showLoading { loading = true }
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

    /// One key per `PUT`, as Android's `updateConfigSection` sends it; the
    /// runtime restarts where the web restarts it.
    private func save(_ edit: HermesSettingEdit, _ value: Any) async {
        saving = true
        values[edit.section, default: [:]][edit.field.key] = value
        do {
            try await store.api.updateConfig(profile: store.selectedProfile, section: edit.section.apiName, values: [edit.field.key: value], restart: edit.section.restartsRuntime)
            store.notify(String(localized: "Saved"))
        } catch {
            store.errorMessage = error.localizedDescription
        }
        saving = false
        await load(showLoading: false)
    }
}

/// One setting being edited: which section and field, plus the pure helpers
/// the prompt and the option sheet need.
struct HermesSettingEdit: Identifiable {
    let section: StudioSettingsSection
    let field: ConfigField
    var id: String { section.rawValue + "." + field.key }

    static func keyboard(for kind: ConfigField.Kind) -> UIKeyboardType {
        switch kind {
        case .number: return .numberPad
        case .decimal: return .decimalPad
        default: return .default
        }
    }

    /// What the prompt typed, as the section stores it: an `Int`, a
    /// `Double`, or the trimmed text.
    static func parse(_ text: String, kind: ConfigField.Kind) -> Any {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        switch kind {
        case .number: return Int(trimmed) ?? 0
        case .decimal: return Double(trimmed) ?? 0
        default: return trimmed
        }
    }

    /// `per_profile` → `Per profile`, the way the form's picker showed it.
    static func optionLabel(_ option: String) -> String {
        option.capitalized.replacingOccurrences(of: "_", with: " ")
    }
}
