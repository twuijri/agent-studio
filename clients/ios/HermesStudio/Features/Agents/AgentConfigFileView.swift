import SwiftUI

/// Which two files the web's agent settings page edits, per agent
/// (`settingsKeys` in `views/hermes/CodingAgentConfigView.vue`). An agent the
/// app has not heard of gets no editors rather than a guessed key.
enum AgentConfigFiles {
    struct Editor: Hashable {
        /// `preference` or `configuration` — the web's two editor slots.
        let key: String
        /// The `config-files/{key}` the server serves it under.
        let fileKey: String
        let title: LocalizedStringKey
    }

    private static let keys: [String: (preference: String, configuration: String)] = [
        "claude-code": ("memory", "settings"),
        "codex": ("agents", "config"),
        "pi": ("agents", "settings"),
        "grok": ("agents", "settings"),
        "opencode": ("memory", "settings"),
        "dsh": ("memory", "settings"),
    ]

    static func editors(for agentID: String) -> [Editor] {
        guard let pair = keys[agentID] else { return [] }
        return [
            Editor(key: "preference", fileKey: pair.preference, title: "Preferences"),
            Editor(key: "configuration", fileKey: pair.configuration, title: "Configuration"),
        ]
    }
}

/// A plain text editor over one of the agent's files on the Core Hub host
/// (`GET`/`PUT /api/coding-agents/{id}/config-files/{key}`).
struct AgentConfigFileView: View {
    @EnvironmentObject private var store: AppStore

    let agentID: String
    let agentName: String
    let editor: AgentConfigFiles.Editor

    @State private var file: CodingAgentConfigFile?
    @State private var content = ""
    @State private var loading = true
    @State private var state: SaveState = .idle

    var body: some View {
        Form {
            Section {
                TextEditor(text: $content)
                    .font(CoreHubTokens.Typography.mono())
                    .frame(minHeight: 280)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
                    // A settings file is code: always left to right, whatever
                    // the interface language is doing.
                    .technicalDirection()
            } header: {
                Text(editor.title)
            } footer: {
                VStack(alignment: .leading, spacing: 3) {
                    if let file { TechnicalText(text: file.path) }
                    if file?.exists == false { Text("This file does not exist yet; saving creates it.") }
                }
            }
            Section {
                SaveButton(title: String(localized: "Save on the server"), state: state) { Task { await save() } }
            }
        }
        .navigationTitle(agentName)
        .navigationBarTitleDisplayMode(.inline)
        .overlay { if loading { ProgressView() } }
        .task { await load() }
    }

    private func load() async {
        loading = true
        defer { loading = false }
        guard let loaded = await store.attempt({
            try await store.api.codingAgentConfigFile(agentID, key: editor.fileKey)
        }) else { return }
        file = loaded
        content = loaded.content
    }

    private func save() async {
        state = .saving
        do {
            let saved = try await store.api.saveCodingAgentConfigFile(agentID, key: editor.fileKey, content: content)
            file = saved
            content = saved.content
            state = .saved
        } catch {
            state = .failed(error.localizedDescription)
        }
    }
}

/// The MCP servers one coding agent has registered on the host. The phone
/// reads, tests and removes them; writing a new server config is a JSON form
/// that is not built yet, so it says so instead of pretending.
struct AgentMcpServersView: View {
    @EnvironmentObject private var store: AppStore

    let agentID: String
    let agentName: String

    @State private var servers: [CodingAgentMcpServer] = []
    @State private var loading = true
    @State private var note = ""
    @State private var working = ""

    var body: some View {
        List {
            if servers.isEmpty && !loading {
                Section { Text("This agent has no MCP servers configured.") }
            }
            ForEach(servers) { server in
                Section {
                    AgentMcpServerRow(server: server, working: working == server.id)
                    serverActions(server)
                } header: {
                    Text(server.name).textCase(nil)
                }
            }
            if !note.isEmpty {
                Section { Text(note).font(CoreHubTokens.Typography.metaFont) }
            }
            Section { Text("Adding or editing an MCP server needs its JSON configuration; do that in Core Hub on a computer.") }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("MCP servers")
        .navigationBarTitleDisplayMode(.inline)
        .overlay { if loading && servers.isEmpty { ProgressView() } }
        .refreshable { await load() }
        .task { await load() }
    }

    private func serverActions(_ server: CodingAgentMcpServer) -> some View {
        HStack(spacing: 14) {
            Button("Test") { Task { await test(server) } }.disabled(!working.isEmpty)
            if !server.managed {
                Button("Remove", role: .destructive) { Task { await remove(server) } }.disabled(!working.isEmpty)
            }
            Spacer(minLength: 0)
            if working == server.id { ProgressView().controlSize(.small) }
        }
        .font(CoreHubTokens.Typography.metaFont)
    }

    private func load() async {
        loading = true
        defer { loading = false }
        servers = (await store.attempt({ try await store.api.codingAgentMcpServers(agentID) })) ?? servers
    }

    private func test(_ server: CodingAgentMcpServer) async {
        working = server.id
        defer { working = "" }
        guard let result = await store.attempt({ try await store.api.testCodingAgentMcpServer(agentID, name: server.name) }) else { return }
        note = result.bool("connected") || result.bool("ok")
            ? String(localized: "Connected, \(result.int("tools")) tools.")
            : (result.string("error").nilIfEmpty ?? String(localized: "The server did not answer."))
    }

    private func remove(_ server: CodingAgentMcpServer) async {
        working = server.id
        defer { working = "" }
        guard await store.attempt({ try await store.api.removeCodingAgentMcpServer(agentID, name: server.name) }) != nil else { return }
        await load()
    }
}

struct AgentMcpServerRow: View {
    let server: CodingAgentMcpServer
    let working: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            HStack(spacing: 7) {
                StatusPill(
                    text: server.connected ? String(localized: "Connected") : String(localized: "Not connected"),
                    color: server.connected ? CoreHubTokens.Palette.success : CoreHubTokens.Palette.textMuted
                )
                Text(server.transport.uppercased()).font(CoreHubTokens.Typography.metaFont).technicalDirection()
                if server.managed { StatusPill(text: String(localized: "Managed"), color: CoreHubTokens.Palette.info) }
                Spacer(minLength: 0)
                Text(String(server.tools)).font(CoreHubTokens.Typography.metaFont).foregroundStyle(CoreHubTokens.Palette.textMuted)
            }
            if !server.toolNames.isEmpty {
                TechnicalText(text: server.toolNames.prefix(6).joined(separator: ", "))
            }
            if !server.error.isEmpty {
                Text(server.error).font(CoreHubTokens.Typography.metaFont).foregroundStyle(CoreHubTokens.Palette.error)
            }
        }
        .opacity(working ? 0.5 : 1)
    }
}
