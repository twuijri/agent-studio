import SwiftUI

/// Settings → This device: what lives on this iPhone only — the Core Hub
/// connection, the voice input mode, the dictation language, the spoken
/// replies, and the chat preferences kept locally. Server-side voice
/// providers are on the Models page (STT / TTS providers).
struct ThisDeviceSettingsView: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        List {
            Section("Connection") {
                NavigationLink { ServerView() } label: { SettingsRow(icon: "server.rack", color: CoreHubTokens.Palette.success, title: "Core Hub connection", subtitle: store.baseURL) }
            }
            Section("Voice") {
                Picker(selection: Binding(get: { store.voiceInput }, set: store.setVoiceInput)) {
                    Text("This device").tag(Preferences.voiceInputDevice); Text("Core Hub server").tag(Preferences.voiceInputServer)
                } label: { SettingsRow(icon: "mic.fill", color: CoreHubTokens.Palette.error, title: "Voice input") { EmptyView() } }
                SpeechInputLanguageRow()
                VoiceOutputSettingsRow()
                Toggle(isOn: Binding(get: { store.autoSpeakReplies }, set: store.setAutoSpeakReplies)) { SettingsRow(icon: "speaker.wave.2.fill", color: CoreHubTokens.Palette.info, title: "Voice mode") { EmptyView() } }
            }
            Section("Chat") {
                Picker(selection: Binding(get: { store.reasoningEffort }, set: store.setReasoning)) {
                    Text("Default").tag(""); Text("Low").tag("low"); Text("Medium").tag("medium"); Text("High").tag("high"); Text("Extra high").tag("xhigh")
                } label: { SettingsRow(icon: "brain.head.profile", color: CoreHubTokens.Palette.accent, title: "Reasoning effort") { EmptyView() } }
                Toggle(isOn: Binding(get: { store.showToolCalls }, set: store.setShowToolCalls)) { SettingsRow(icon: "wrench.and.screwdriver.fill", color: CoreHubTokens.Palette.warning, title: "Show tool calls") { EmptyView() } }
                Toggle(isOn: Binding(get: { store.allProfilesSessions }, set: store.setAllProfilesSessions)) { SettingsRow(icon: "person.2.fill", color: CoreHubTokens.Palette.accent, title: "Conversations from every profile") { EmptyView() } }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("This device")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct ServerView: View {
    @EnvironmentObject private var store: AppStore
    @State private var server = ""
    @State private var testing = false

    var body: some View {
        Form {
            Section {
                TextField("https://studio.example.com", text: $server).keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
                Button { Task { testing = true; await store.updateServer(server); testing = false } } label: { HStack { if testing { ProgressView() }; Text("Save and test connection") } }
                    .disabled(server.isEmpty || testing)
            } header: { Text("Studio address") } footer: { Text("Use your normal Studio web address. Local HTTP servers are also supported.") }
            Section("Status") {
                LabeledContent("Account", value: store.currentUser?.username ?? "—")
                LabeledContent("Profile", value: store.selectedProfile)
                LabeledContent("Connection") { StatusPill(text: store.connected ? String(localized: "Connected") : String(localized: "Disconnected"), color: store.connected ? .green : .gray) }
            }
        }
        .navigationTitle("Core Hub connection")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { server = store.baseURL }
    }
}

/// Settings → About: versions and the repositories.
struct AboutView: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        List {
            Section {
                HStack { SettingsRow(icon: "app.badge.fill", color: CoreHubTokens.Palette.accent, title: "Core Hub for iOS") { Text(verbatim: "v\(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "")").foregroundStyle(.secondary) } }
                if !store.serverVersion.isEmpty { LabeledContent("Core Hub server") { Text(verbatim: "v\(store.serverVersion)") } }
            }
            Section {
                Link(destination: URL(string: "https://github.com/twuijri/core-hub")!) { RepositorySettingsRow(title: "Core Hub on GitHub") }.foregroundStyle(.primary)
                Link(destination: URL(string: "https://github.com/EKKOLearnAI/hermes-studio")!) { RepositorySettingsRow(title: "Upstream: Hermes Studio") }.foregroundStyle(.primary)
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("About")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct RepositorySettingsRow: View {
    let title: LocalizedStringKey

    var body: some View {
        HStack(spacing: 13) {
            Image("GitHubMark")
                .resizable()
                .renderingMode(.template)
                .scaledToFit()
                .foregroundStyle(.white)
                .padding(6)
                .frame(width: 31, height: 31)
                .background(Color.black.gradient, in: RoundedRectangle(cornerRadius: 8))
            Text(title).font(.body.weight(.medium)).lineLimit(1)
            Spacer(minLength: 8)
            Image(systemName: "arrow.up.right").foregroundStyle(.secondary)
        }
        .contentShape(Rectangle())
        .padding(.vertical, 4)
    }
}
