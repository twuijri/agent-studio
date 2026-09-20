import SwiftUI

/// `.agentHermes` / `.agentEkko` / `.agentCoding` pushed by value: loads the
/// inventory the Agent Manager already has and opens the same
/// `AgentDetailView` the card opens.
struct AgentScreenLoader: View {
    @EnvironmentObject private var store: AppStore
    let agentID: String

    @State private var agent: AgentRuntimeStatus?
    @State private var tool: CodingAgentTool?
    @State private var policy: AgentUpdatePolicy?
    @State private var loading = true

    var body: some View {
        Group {
            if let agent {
                AgentDetailView(agent: agent, tool: tool, policy: policy) { await load() }
            } else if loading {
                ProgressView()
            } else {
                EmptyState(icon: "person.crop.circle.badge.questionmark", title: "Agent unavailable", detail: "Core Hub did not return this agent.")
            }
        }
        .task(id: agentID) { await load() }
    }

    private func load() async {
        loading = true
        defer { loading = false }
        let statuses = (try? await store.api.agentStatuses()) ?? []
        let tools = (try? await store.api.codingAgents()) ?? []
        let policies = (try? await store.api.agentUpdatePolicies()) ?? [:]
        tool = tools.first { $0.id == agentID }
        policy = policies[agentID]
        if let found = statuses.first(where: { $0.id == agentID }) {
            agent = found
        } else if let tool {
            agent = AgentManagerView.status(from: tool)
        } else {
            agent = AgentScreenLoader.placeholder(agentID)
        }
    }

    /// Hermes and Ekko exist even when `/api/agents/status` is refused.
    static func placeholder(_ agentID: String) -> AgentRuntimeStatus {
        let family = AgentFamily(agentID: agentID)
        let kind: String
        switch family {
        case .hermes: kind = "hermes"
        case .ekko: kind = "built-in"
        case .coding: kind = "coding-agent"
        }
        return AgentRuntimeStatus(["id": family.agentID, "name": AgentIdentity.displayName(for: agentID), "kind": kind, "installed": family != .coding(id: family.agentID)])
    }
}

/// `.skills` under the agent that was opened last.
struct AgentSkillsScreen: View {
    let family: AgentFamily

    var body: some View {
        switch family {
        case .hermes: SkillsView()
        case .ekko: EkkoSkillsView()
        case .coding: SkillsView(target: family.skillTarget)
        }
    }
}

/// `.plugins`: Hermes plugins, or the DSH plugin inventory.
struct AgentPluginsScreen: View {
    let family: AgentFamily

    var body: some View {
        switch family {
        case .coding(let id) where id == "dsh": DshPluginsView()
        default: PluginsView()
        }
    }
}

/// `.mcp`: each agent keeps its own MCP servers.
struct AgentMcpScreen: View {
    let family: AgentFamily

    var body: some View {
        switch family {
        case .hermes: MCPView()
        case .ekko: EkkoMCPView()
        case let .coding(id): AgentMcpServersView(agentID: id, agentName: AgentIdentity.displayName(for: id))
        }
    }
}

/// `.memory`: the Hermes memory browser or Ekko's memory store.
struct AgentMemoryScreen: View {
    let family: AgentFamily

    var body: some View {
        switch family {
        case .hermes: HermesMemoryView()
        case .ekko: EkkoMemoryView()
        case .coding:
            EmptyState(icon: "brain", title: "No memory browser", detail: "Coding agents keep their notes in their own settings files.")
                .navigationTitle(NavDestination.memory.title)
        }
    }
}

/// `.codingAgentSettings`: the two files the web's agent settings page edits
/// (`CodingAgentConfigView.vue`), keyed per agent.
struct CodingAgentSettingsView: View {
    let agentID: String

    private var agentName: String { AgentIdentity.displayName(for: agentID) }

    var body: some View {
        List {
            Section {
                ForEach(AgentConfigFiles.editors(for: agentID), id: \.key) { editor in
                    NavigationLink {
                        AgentConfigFileView(agentID: agentID, agentName: agentName, editor: editor)
                    } label: {
                        Label(editor.title, systemImage: editor.key == "preference" ? "text.book.closed" : "gearshape")
                    }
                }
            } footer: {
                Text("The agent's own files on the server, the same two the web's agent settings page edits.")
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(NavDestination.codingAgentSettings.title)
        .navigationBarTitleDisplayMode(.inline)
    }
}
