import CoreGraphics
import Foundation

// MARK: - Sessions

/// `GET /api/studio/sessions/search?q=` result: a session plus the matched
/// message snippet (title and message-text search, like the web).
struct SessionSearchResult: Identifiable, Hashable {
    let session: SessionSummary
    let snippet: String
    let matchedMessageID: String
    var id: String { session.id }

    init(_ json: JSON, profile: String = "") {
        session = SessionSummary(json, profile: profile)
        snippet = json.string("snippet")
        matchedMessageID = json.string("matched_message_id")
    }
}

/// One page of `GET /sessions/conversations/{id}/messages/paginated`.
struct MessagePage: Equatable {
    let messages: [Message]
    let total: Int
    let offset: Int
    let limit: Int
    let hasMore: Bool

    init(_ json: JSON) {
        messages = json.objects("messages").map(Message.init)
        total = json.int("total")
        offset = json.int("offset")
        limit = json.int("limit")
        hasMore = json.bool("hasMore")
    }
}

/// Offset window used to page conversation history from the end, like the
/// web's virtual list: the first request fetches the newest `limit`
/// messages, "load earlier" moves the window back.
enum MessagePaging {
    static let pageSize = 150

    /// Offset of the newest page for `total` messages.
    static func lastPageOffset(total: Int, limit: Int = pageSize) -> Int {
        max(0, total - max(1, limit))
    }

    /// The window that precedes `currentOffset`; `nil` when the beginning
    /// has been reached.
    static func earlierWindow(currentOffset: Int, limit: Int = pageSize) -> (offset: Int, limit: Int)? {
        guard currentOffset > 0 else { return nil }
        let offset = max(0, currentOffset - max(1, limit))
        return (offset, currentOffset - offset)
    }
}

/// `GET /sessions/{id}/context` — the compressed context the model sees.
struct SessionContextMessage: Identifiable, Hashable {
    let id: String
    let role: String
    let content: String
    let timestamp: Int64
    init(_ json: JSON) {
        id = json.string("id").nilIfEmpty ?? UUID().uuidString
        role = json.string("role")
        content = json.string("content")
        timestamp = (json["timestamp"] as? NSNumber)?.int64Value ?? 0
    }
}

struct SessionUsage: Hashable {
    let inputTokens: Int
    let outputTokens: Int
    init(_ json: JSON) { inputTokens = json.int("input_tokens"); outputTokens = json.int("output_tokens") }
    var total: Int { inputTokens + outputTokens }
}

/// Batch result of `batch-delete` / `batch-archive`.
struct BatchResult: Equatable {
    let succeeded: Int
    let failed: Int
    let errors: [String]
    init(_ json: JSON, successKey: String) {
        succeeded = json.int(successKey)
        failed = json.int("failed")
        errors = json.objects("errors").map { "\($0.string("id")): \($0.string("error"))" }
    }
}

/// Selection state of the session list's batch mode (archive / delete /
/// move). Pure so it can be unit-tested.
struct SessionBatchSelection: Equatable {
    var ids: Set<String> = []
    var active = false

    mutating func toggle(_ id: String) { if ids.contains(id) { ids.remove(id) } else { ids.insert(id) } }
    mutating func clear() { ids = []; active = false }

    func selected(in sessions: [SessionSummary]) -> [SessionSummary] { sessions.filter { ids.contains($0.id) } }
    func archiveTargets(in sessions: [SessionSummary]) -> [SessionSummary] { selected(in: sessions).filter { !$0.archived && $0.source != "global_agent" } }
    func unarchiveTargets(in sessions: [SessionSummary]) -> [SessionSummary] { selected(in: sessions).filter { $0.archived } }
}

// MARK: - Users (Account Management)

struct ManagedUser: Identifiable, Hashable {
    let id: Int
    var username: String
    var role: String
    var status: String
    var profiles: [String]
    var defaultProfile: String
    var lastLoginAt: Int64?

    init(_ json: JSON) {
        id = json.int("id")
        username = json.string("username")
        role = json.string("role").nilIfEmpty ?? "admin"
        status = json.string("status").nilIfEmpty ?? "active"
        profiles = json.strings("profiles")
        defaultProfile = json.string("default_profile", "defaultProfile")
        lastLoginAt = (json["last_login_at"] as? NSNumber)?.int64Value
    }

    var isSuperAdmin: Bool { role == "super_admin" }
    var isActive: Bool { status == "active" }
}

// MARK: - Workflows

/// Live status of one workflow from the `/workflow` namespace
/// (`workflow.status.updated`).
struct WorkflowRuntimeStatus: Identifiable, Hashable {
    let workflowID: String
    var status: String
    var runID: String
    var error: String
    var nodeStatuses: [String: String]
    var pendingApprovals: [String]
    var run: WorkflowRun?
    var id: String { workflowID }

    init(_ json: JSON) {
        workflowID = json.string("workflowId", "workflow_id")
        status = json.string("status").nilIfEmpty ?? "idle"
        runID = json.string("runId", "run_id")
        error = json.string("error")
        var statuses: [String: String] = [:]
        for (key, value) in json.object("nodeStatuses") { if let text = value as? String { statuses[key] = text } }
        nodeStatuses = statuses
        pendingApprovals = json.objects("pendingApprovals").map { $0.string("nodeId") }
        let runJSON = json.object("run")
        run = runJSON.isEmpty ? nil : WorkflowRun(runJSON)
    }

    var isActive: Bool { ["queued", "running", "pending_approval"].contains(status) }
}

/// A node of a stored workflow graph (`WorkflowAgentNodeData` subset), read
/// only — the app never edits graphs.
struct WorkflowNodeSummary: Identifiable, Hashable {
    let id: String
    let title: String
    let agent: String
    let agentMode: String
    let model: String
    let provider: String
    let input: String
    let approvalRequired: Bool
    let skills: [String]
    let position: CGPoint

    init(_ json: JSON) {
        id = json.string("id")
        let data = json.object("data")
        title = data.string("title", "label").nilIfEmpty ?? id
        agent = data.string("agent").nilIfEmpty ?? "hermes"
        agentMode = data.string("agentMode").nilIfEmpty ?? "scoped"
        model = data.string("model")
        provider = data.string("provider")
        input = data.string("input")
        approvalRequired = data.bool("approvalRequired")
        skills = data.strings("skills")
        let pos = json.object("position")
        position = CGPoint(x: pos.double("x"), y: pos.double("y"))
    }
}

struct WorkflowEdgeSummary: Hashable {
    let source: String
    let target: String
    let route: String
    init(_ json: JSON) {
        source = json.string("source"); target = json.string("target")
        route = json.object("data").string("route", "condition").nilIfEmpty ?? json.string("sourceHandle").nilIfEmpty ?? "always"
    }
}

/// Pure helpers for the read-only graph summary and the run timeline.
enum WorkflowGraph {
    static func nodes(_ workflow: WorkflowItem) -> [WorkflowNodeSummary] { workflow.nodes.map(WorkflowNodeSummary.init).filter { !$0.id.isEmpty } }
    static func edges(_ workflow: WorkflowItem) -> [WorkflowEdgeSummary] { workflow.edges.map(WorkflowEdgeSummary.init) }

    /// Nodes in execution order: start nodes (no incoming edge) first, then
    /// breadth-first along edges; unreachable nodes keep their stored order.
    static func ordered(nodes: [WorkflowNodeSummary], edges: [WorkflowEdgeSummary]) -> [WorkflowNodeSummary] {
        let targets = Set(edges.map(\.target))
        var queue = nodes.filter { !targets.contains($0.id) }.map(\.id)
        var seen: [String] = []
        while !queue.isEmpty {
            let current = queue.removeFirst()
            if seen.contains(current) { continue }
            seen.append(current)
            for edge in edges where edge.source == current && !seen.contains(edge.target) { queue.append(edge.target) }
        }
        for node in nodes where !seen.contains(node.id) { seen.append(node.id) }
        let byID = Dictionary(nodes.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        return seen.compactMap { byID[$0] }
    }

    /// One timeline row per node, merging the persisted node sessions of a
    /// run with the live `nodeStatuses` of the runtime status.
    struct TimelineRow: Identifiable, Hashable {
        let node: WorkflowNodeSummary
        var status: String
        var error: String
        var sessionID: String
        var executionID: String
        var startedAt: Int64?
        var finishedAt: Int64?
        var id: String { node.id }
        var awaitingApproval: Bool { status == "blocked" || status == "pending_approval" }
    }

    static func timeline(workflow: WorkflowItem, run: WorkflowRun?, live: WorkflowRuntimeStatus?) -> [TimelineRow] {
        let ordered = ordered(nodes: nodes(workflow), edges: edges(workflow))
        return ordered.map { node in
            let session = run?.nodes.last { $0.nodeID == node.id }
            var status = session?.status ?? "idle"
            if let liveStatus = live?.nodeStatuses[node.id], live?.runID == run?.id || run == nil { status = liveStatus }
            if live?.pendingApprovals.contains(node.id) == true { status = "pending_approval" }
            return TimelineRow(node: node, status: status, error: session?.error ?? "", sessionID: session?.sessionID ?? "", executionID: session?.executionID ?? "", startedAt: session?.startedAt, finishedAt: session?.finishedAt)
        }
    }
}

// MARK: - Models catalog

/// `GET /api/hermes/available-models?profile=…` — the same payload the web's
/// models store reads.
///
/// `groups` are the providers this profile actually has credentials for and is
/// what the Models screen lists, exactly like `useModelsStore().providers`.
/// `allProviders` is the full preset catalogue the server always returns; the
/// web only consults it to restore hidden models, so it is kept apart here
/// instead of being merged into the visible list. `moa` is a virtual Hermes
/// routing provider and is filtered out, as in the web store.
struct ModelCatalog: Equatable {
    struct Group: Identifiable, Equatable {
        let id: String
        let label: String
        let baseURL: String
        let models: [String]
        let availableModels: [String]
        let apiKeyConfigured: Bool
        let builtin: Bool
        let editable: Bool
        let refreshable: Bool
        let restoreAvailable: Bool
        let refreshReason: String
        let apiMode: String
        /// `custom_providers` / `providers` for config-backed pools, else "".
        let providerSource: String
        let providerKey: String
        let catalogStatus: String

        init(_ json: JSON) {
            id = json.string("provider", "id")
            label = json.string("label").nilIfEmpty ?? id
            baseURL = json.string("base_url")
            models = json.strings("models").filter { $0 != "*" }
            let available = json.strings("available_models")
            availableModels = available.isEmpty ? models : available
            apiKeyConfigured = !json.string("api_key").isEmpty || json.bool("credential_configured")
            builtin = json.bool("builtin")
            editable = json["provider_editable"] == nil ? true : json.bool("provider_editable")
            refreshable = json.bool("model_refreshable")
            restoreAvailable = json.bool("model_restore_available")
            refreshReason = json.string("model_refresh_reason")
            apiMode = json.string("api_mode")
            providerSource = json.string("provider_source")
            providerKey = json.string("provider_key")
            catalogStatus = json.string("catalog_status")
        }

        /// `provider.provider.startsWith('custom:')` in `ProviderCard.vue`.
        var isCustomKey: Bool { id.hasPrefix("custom:") }
        /// The web's `isCustom`: the "Custom" badge.
        var isCustom: Bool { !builtin && isCustomKey }
        /// The web's `isConfigBackedProvider`: deleting removes the pool
        /// itself; otherwise the destructive action only clears credentials.
        var isConfigBacked: Bool { isCustomKey || (!builtin && !providerSource.isEmpty) }
    }

    let defaultModel: String
    let defaultProvider: String
    /// Providers configured for this profile — what the screen lists.
    let groups: [Group]
    /// The server's full preset catalogue, used only to widen a provider's
    /// model list when its own `available_models` is empty.
    let presets: [Group]
    /// provider → model → alias
    let aliases: [String: [String: String]]
    /// provider → (mode, models)
    let visibility: [String: (mode: String, models: [String])]
    let customModels: [String: [String]]

    init(_ json: JSON) {
        defaultModel = json.string("default")
        defaultProvider = json.string("default_provider")
        groups = Self.unique(json.objects("groups"))
        presets = Self.unique(json.objects("allProviders"))
        var aliasMap: [String: [String: String]] = [:]
        for (provider, raw) in json.object("model_aliases") {
            guard let table = raw as? JSON else { continue }
            var entries: [String: String] = [:]
            for (model, alias) in table { if let text = alias as? String, !text.isEmpty { entries[model] = text } }
            aliasMap[provider] = entries
        }
        aliases = aliasMap
        var rules: [String: (mode: String, models: [String])] = [:]
        for (provider, raw) in json.object("model_visibility") {
            guard let rule = raw as? JSON else { continue }
            rules[provider] = (rule.string("mode").nilIfEmpty ?? "all", rule.strings("models"))
        }
        visibility = rules
        var custom: [String: [String]] = [:]
        for (provider, raw) in json.object("custom_models") { custom[provider] = (raw as? [String]) ?? [] }
        customModels = custom
    }

    private static func unique(_ raw: [JSON]) -> [Group] {
        var result: [Group] = []
        for group in raw.map(Group.init) where !group.id.isEmpty && group.id != "moa" {
            if !result.contains(where: { $0.id == group.id }) { result.append(group) }
        }
        return result
    }

    static func == (lhs: ModelCatalog, rhs: ModelCatalog) -> Bool {
        lhs.defaultModel == rhs.defaultModel && lhs.defaultProvider == rhs.defaultProvider && lhs.groups == rhs.groups
            && lhs.aliases == rhs.aliases && lhs.customModels == rhs.customModels
            && lhs.visibility.keys.sorted() == rhs.visibility.keys.sorted()
    }

    func alias(provider: String, model: String) -> String? { aliases[provider]?[model] }
    func displayName(provider: String, model: String) -> String { alias(provider: provider, model: model) ?? model }
    func isVisible(provider: String, model: String) -> Bool {
        guard let rule = visibility[provider], rule.mode == "include" else { return true }
        return rule.models.contains(model)
    }
    func isCustom(provider: String, model: String) -> Bool { customModels[provider]?.contains(model) == true }
    func isDefault(provider: String, model: String) -> Bool {
        provider == defaultProvider && model == defaultModel
    }
    /// The full list the visibility editor shows, mirroring `ProviderCard`'s
    /// `allModels`: the provider's own catalogue, widened by the preset entry
    /// when the provider reported none, plus the profile's custom models.
    func allModels(of group: Group) -> [String] {
        var result = group.availableModels
        if result.isEmpty, let preset = presets.first(where: { $0.id == group.id }) {
            result = preset.models
        }
        for model in customModels[group.id] ?? [] where !result.contains(model) { result.append(model) }
        return result
    }
    /// `12` normally, `8/12` once a visibility rule hides some, like the
    /// web's `visibleCountLabel`.
    func modelCountLabel(of group: Group) -> String {
        let total = allModels(of: group).count
        guard visibility[group.id]?.mode == "include" else { return "\(total)" }
        return "\(group.models.count)/\(total)"
    }
}

/// `GET /api/hermes/model-context?provider=&model=`.
struct ModelContextLimit: Equatable {
    let provider: String
    let model: String
    let contextLimit: Int
    init(_ json: JSON) { provider = json.string("provider"); model = json.string("model"); contextLimit = json.int("context_limit") }
}

// MARK: - Save state

/// Per-setting save feedback ("Saving…", "Saved", or the error) so every
/// settings control can show what happened.
enum SaveState: Equatable {
    case idle, saving, saved, failed(String)

    var isSaving: Bool { self == .saving }
}
