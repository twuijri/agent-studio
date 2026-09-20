import SwiftUI

/// The Hermes memory browser — the phone's `views/hermes/MemoryView.vue`.
/// It had no equivalent here at all: Settings → Advanced → Memory is the
/// memory *settings* section, and Ekko memory is a different agent's store.
/// This is the profile's three Markdown files, read and written through
/// `GET`/`POST /api/hermes/memory`.
struct HermesMemoryView: View {
    @EnvironmentObject private var store: AppStore

    @State private var memory: HermesMemory?
    @State private var loading = true

    /// The web's three sections, with its own labels.
    private static let sections: [(key: String, title: LocalizedStringKey, empty: LocalizedStringKey)] = [
        ("memory", "My Notes", "No notes yet."),
        ("user", "User Profile", "No profile yet."),
        ("soul", "Soul", "No soul configuration yet."),
    ]

    var body: some View {
        List {
            ForEach(Self.sections, id: \.key) { section in
                Section {
                    NavigationLink {
                        HermesMemoryEditor(section: section.key, title: section.title, initial: text(section.key)) {
                            await load()
                        }
                    } label: {
                        preview(section)
                    }
                } header: { Text(section.title) }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(NavDestination.memory.title)
        .navigationBarTitleDisplayMode(.inline)
        .overlay { if loading && memory == nil { ProgressView() } }
        .refreshable { await load() }
        .task(id: store.selectedProfile) { await load() }
    }

    private func preview(_ section: (key: String, title: LocalizedStringKey, empty: LocalizedStringKey)) -> some View {
        let body = text(section.key)
        return Group {
            if body.isEmpty {
                Text(section.empty).foregroundStyle(CoreHubTokens.Palette.textMuted)
            } else {
                DirectionalText(text: body, font: CoreHubTokens.Typography.font(CoreHubTokens.Typography.base), lineLimit: 4)
            }
        }
    }

    private func text(_ key: String) -> String { memory?.text(for: key) ?? "" }

    private func load() async {
        loading = true
        defer { loading = false }
        memory = (await store.attempt({ try await store.api.hermesMemory() })) ?? memory
    }
}

/// One memory file. Markdown content, so the field follows the text's own
/// direction rather than the interface's.
struct HermesMemoryEditor: View {
    @EnvironmentObject private var store: AppStore

    let section: String
    let title: LocalizedStringKey
    let initial: String
    let reload: () async -> Void

    @State private var content = ""
    @State private var state: SaveState = .idle

    var body: some View {
        Form {
            Section {
                TextEditor(text: $content)
                    .frame(minHeight: 280)
                    .contentDirection(of: content)
            }
            Section {
                SaveButton(title: String(localized: "Save"), state: state) { Task { await save() } }
            } footer: {
                Text("Written to the profile \(store.selectedProfile) on the server.")
            }
        }
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { if content.isEmpty { content = initial } }
    }

    private func save() async {
        state = .saving
        do {
            try await store.api.saveHermesMemory(section: section, content: content)
            state = .saved
            await reload()
        } catch {
            state = .failed(error.localizedDescription)
        }
    }
}
