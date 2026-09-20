import SwiftUI

/// Settings → Display (`DisplaySettings.vue`): the local colour scheme, the
/// app language and the text size of this iPhone, then the profile's display
/// section from the server (busy-input behaviour, streaming, compact layout,
/// reasoning, cost, diffs, bells and notifications).
///
/// The colour-scheme picker is the desktop's `ThemeSwitch`, a local
/// preference; it is not the Theme screen (Settings → Tools → Theme).
struct DisplaySettingsView: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        List {
            Section {
                Picker(selection: Binding(get: { store.appearance }, set: store.setAppearance)) {
                    Text("System").tag("system"); Text("Light").tag("light"); Text("Dark").tag("dark")
                } label: { SettingsRow(icon: "circle.lefthalf.filled", color: CoreHubTokens.Palette.accent, title: "Color scheme") { EmptyView() } }
                Picker(selection: Binding(get: { store.language }, set: store.setLanguage)) {
                    Text("System").tag("system"); Text("العربية").tag("ar"); Text("English").tag("en")
                } label: { SettingsRow(icon: "globe", color: CoreHubTokens.Palette.info, title: "App language") { EmptyView() } }
                TextScaleRow()
            } header: { Text("This iPhone") } footer: {
                Text("Stored on this device only. The server theme and background are under Settings → Tools → Theme.")
            }
            Section("Chat display") {
                NavigationLink { StudioSectionSettings(section: .display) } label: {
                    SettingsRow(icon: "rectangle.on.rectangle", color: CoreHubTokens.Palette.info, title: "Chat display", subtitle: String(localized: "Streaming, layout, reasoning, cost, diffs and notifications"))
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Display")
        .navigationBarTitleDisplayMode(.inline)
        .id("\(store.language)-\(store.languageRefresh)")
    }
}

/// Device-local text scale (0.85–1.45) applied to every screen.
private struct TextScaleRow: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            SettingsRow(icon: "textformat.size", color: CoreHubTokens.Palette.info, title: "Text size") {
                Text(verbatim: "\(Int((store.textScale * 100).rounded()))%").foregroundStyle(.secondary)
            }
            Slider(value: Binding(get: { store.textScale }, set: store.setTextScale), in: 0.85...1.45, step: 0.05)
                .accessibilityLabel("Text size")
        }
        .padding(.vertical, 2)
    }
}
