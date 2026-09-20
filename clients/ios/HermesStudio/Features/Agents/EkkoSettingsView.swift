import SwiftUI

/// Ekko → Settings (`views/ekko/SettingsView.vue`): Runtime, Model,
/// Compression, Tools, Modules, Advanced — one `GET`/`PUT /api/ekko/config`.
struct EkkoSettingsView: View {
    @EnvironmentObject private var store: AppStore
    @State private var root: JSON = [:]
    @State private var loading = true
    @State private var state: SaveState = .idle
    @State private var tab = "runtime"

    private static let tabs = [
        TabStripItem(id: "runtime", title: String(localized: "Runtime")),
        TabStripItem(id: "model", title: String(localized: "Model")),
        TabStripItem(id: "compression", title: String(localized: "Compression")),
        TabStripItem(id: "tools", title: String(localized: "Tools")),
        TabStripItem(id: "modules", title: String(localized: "Modules")),
        TabStripItem(id: "advanced", title: String(localized: "Advanced")),
    ]

    var body: some View {
        VStack(spacing: 0) {
            TabStrip(items: Self.tabs, selection: $tab)
            Form {
                if !loading { tabSections }
                Section { SaveButton(title: String(localized: "Save settings"), state: state) { Task { await save() } } }
            }
            .overlay { if loading { ProgressView() } }
        }
        .background(CoreHubTokens.Palette.bgPrimary)
        .navigationTitle(NavDestination.ekkoSettings.title)
        .navigationBarTitleDisplayMode(.inline)
        .task { await load() }
    }

    @ViewBuilder private var tabSections: some View {
        switch tab {
        case "model": modelSection
        case "compression": compressionSection
        case "tools": toolsSection
        case "modules": modulesSection
        case "advanced": advancedSection
        default: runtimeSection
        }
    }

    // MARK: Tabs

    private var runtimeSection: some View {
        Group {
            Section("Runtime") {
                Stepper("Maximum steps: \(int(["runtime", "maxSteps"], 30))", value: intBinding(["runtime", "maxSteps"], 30), in: 1...500, step: 5)
                Stepper("Model retries: \(int(["runtime", "maxModelRetries"], 2))", value: intBinding(["runtime", "maxModelRetries"], 2), in: 0...20)
                Stepper("Tool failure recovery after: \(int(["runtime", "toolFailureRecoveryThreshold"], 3))", value: intBinding(["runtime", "toolFailureRecoveryThreshold"], 3), in: 1...50)
            }
            Section("Delegation") {
                Toggle("Background delegation", isOn: boolBinding(["delegation", "backgroundEnabled"], true))
                Stepper("Subtask maximum steps: \(int(["delegation", "subtaskMaxSteps"], 20))", value: intBinding(["delegation", "subtaskMaxSteps"], 20), in: 1...500, step: 5)
            }
        }
    }

    private var modelSection: some View {
        Section("Model") {
            TextField("Default provider", text: stringBinding(["model", "defaultProvider"])).textInputAutocapitalization(.never).autocorrectionDisabled()
            TextField("Default model", text: stringBinding(["model", "defaultModel"])).textInputAutocapitalization(.never).autocorrectionDisabled()
            TextField("Request timeout (ms)", text: numberBinding(["model", "requestTimeoutMs"])).keyboardType(.numberPad)
            TextField("Temperature", text: numberBinding(["model", "temperature"], decimal: true)).keyboardType(.decimalPad)
            TextField("Maximum tokens", text: numberBinding(["model", "maxTokens"])).keyboardType(.numberPad)
            Picker("Reasoning", selection: stringBinding(["model", "reasoningEffort"])) {
                ForEach(["none", "minimal", "low", "medium", "high", "xhigh", "max"], id: \.self) { Text($0).tag($0) }
            }
            Picker("Reasoning summary", selection: stringBinding(["model", "reasoningSummary"])) {
                ForEach(["auto", "concise", "detailed", "none"], id: \.self) { Text($0).tag($0) }
            }
        }
    }

    private var compressionSection: some View {
        Section("Compression") {
            Toggle("Compression enabled", isOn: boolBinding(["compression", "enabled"], true))
            TextField("Compression threshold", text: numberBinding(["compression", "threshold"], decimal: true)).keyboardType(.decimalPad)
            TextField("Target ratio", text: numberBinding(["compression", "targetRatio"], decimal: true)).keyboardType(.decimalPad)
            TextField("Protect latest messages", text: numberBinding(["compression", "protectLastN"])).keyboardType(.numberPad)
            TextField("Protect first messages", text: numberBinding(["compression", "protectFirstN"])).keyboardType(.numberPad)
        }
    }

    private var toolsSection: some View {
        Group {
            Section("Tools") {
                Toggle("Tools enabled", isOn: boolBinding(["tools", "enabled"], true))
                TextField("Execution timeout (ms)", text: numberBinding(["tools", "executionTimeoutMs"])).keyboardType(.numberPad)
                Toggle("Tool approvals", isOn: boolBinding(["tools", "approvals", "enabled"], true))
                TextField("Approval timeout (ms)", text: numberBinding(["tools", "approvals", "timeoutMs"])).keyboardType(.numberPad)
            }
            Section("Code execution") {
                Toggle("Code execution", isOn: boolBinding(["tools", "codeExec", "enabled"], false))
                TextField("Code timeout (ms)", text: numberBinding(["tools", "codeExec", "timeoutMs"])).keyboardType(.numberPad)
                TextField("Maximum tool calls", text: numberBinding(["tools", "codeExec", "maxToolCalls"])).keyboardType(.numberPad)
            }
        }
    }

    private var modulesSection: some View {
        Group {
            Section("Memory") {
                Toggle("Memory enabled", isOn: boolBinding(["memory", "enabled"], true))
                TextField("Recent message limit", text: numberBinding(["memory", "recentMessageLimit"])).keyboardType(.numberPad)
                TextField("Automatic recall token budget", text: numberBinding(["memory", "automaticRecallTokenBudget"])).keyboardType(.numberPad)
                TextField("Search result limit", text: numberBinding(["memory", "searchResultLimit"])).keyboardType(.numberPad)
            }
            Section("Skills and MCP") {
                Toggle("Skills enabled", isOn: boolBinding(["skills", "enabled"], true))
                TextField("Review skills every N tool calls", text: numberBinding(["skills", "reviewEveryToolCalls"])).keyboardType(.numberPad)
                Toggle("MCP enabled", isOn: boolBinding(["mcp", "enabled"], true))
            }
        }
    }

    private var advancedSection: some View {
        Group {
            Section("Logging") {
                TextField("Log size limit (bytes)", text: numberBinding(["logging", "maxBytes"])).keyboardType(.numberPad)
            }
            Section("Prompt instructions") {
                TextField("One instruction per line", text: linesBinding(["prompt", "instructions"]), axis: .vertical).lineLimit(3...10)
            }
            if let path = root.string("configPath", "path").nilIfEmpty {
                Section("Config file") { TechnicalText(text: path) }
            }
        }
    }

    // MARK: Bindings over the nested config

    private var config: JSON { root.object("config") }

    private func value(_ path: [String]) -> Any? {
        var current: Any = config
        for key in path {
            guard let next = (current as? JSON)?[key] else { return nil }
            current = next
        }
        return current
    }

    private func set(_ path: [String], _ value: Any) {
        var cfg = config
        func assign(_ object: inout JSON, _ keys: ArraySlice<String>) {
            guard let key = keys.first else { return }
            if keys.count == 1 { object[key] = value; return }
            var child = object.object(key)
            assign(&child, keys.dropFirst())
            object[key] = child
        }
        assign(&cfg, path[...])
        root["config"] = cfg
    }

    private func int(_ path: [String], _ fallback: Int) -> Int { (value(path) as? NSNumber)?.intValue ?? fallback }
    private func intBinding(_ path: [String], _ fallback: Int) -> Binding<Int> { Binding(get: { int(path, fallback) }, set: { set(path, $0) }) }
    private func stringBinding(_ path: [String]) -> Binding<String> { Binding(get: { value(path) as? String ?? "" }, set: { set(path, $0) }) }
    private func boolBinding(_ path: [String], _ fallback: Bool) -> Binding<Bool> { Binding(get: { value(path) as? Bool ?? fallback }, set: { set(path, $0) }) }
    private func numberBinding(_ path: [String], decimal: Bool = false) -> Binding<String> {
        Binding(
            get: { (value(path) as? NSNumber)?.stringValue ?? "" },
            set: { text in
                if text.isEmpty { set(path, NSNull()); return }
                if decimal, let number = Double(text) { set(path, number) } else if let number = Int(text) { set(path, number) }
            }
        )
    }
    private func linesBinding(_ path: [String]) -> Binding<String> {
        Binding(
            get: { ((value(path) as? [Any]) ?? []).compactMap { $0 as? String }.joined(separator: "\n") },
            set: { set(path, $0.split(separator: "\n").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }) }
        )
    }

    // MARK: Work

    private func load() async {
        loading = true
        do { root = try await store.api.ekkoConfig() } catch { store.errorMessage = error.localizedDescription }
        loading = false
    }

    private func save() async {
        state = .saving
        // `NSNull` marks a cleared number: drop it so the server keeps its default.
        var cleaned = config
        for (key, value) in cleaned where value is NSNull { cleaned.removeValue(forKey: key) }
        do {
            try await store.api.saveEkkoConfig(cleaned)
            state = .saved
            await load()
        } catch {
            state = .failed(error.localizedDescription)
        }
    }
}

/// Ekko → Memory (`/api/ekko/memory`), Ekko's own store.
struct EkkoMemoryView: View {
    @EnvironmentObject private var store: AppStore
    @State private var items: [EkkoMemoryItem] = []
    @State private var query = ""
    @State private var editing: EkkoMemoryItem?

    var body: some View {
        List {
            SearchBar(text: $query)
            ForEach(items) { item in
                Button { editing = item } label: {
                    VStack(alignment: .leading, spacing: 5) {
                        Text(item.title.nilIfEmpty ?? item.content).font(.headline).foregroundStyle(.primary).lineLimit(2)
                        Text(item.content).font(.caption).foregroundStyle(.secondary).lineLimit(3)
                        StatusPill(text: item.status, color: item.status == "active" ? .green : .gray)
                    }
                }
                .swipeActions {
                    Button(role: .destructive) { Task { await store.attempt({ try await store.api.deleteEkkoMemory(item) }); await load() } } label: { Label("Delete", systemImage: "trash") }
                }
            }
        }
        .navigationTitle(NavDestination.memory.title)
        .navigationBarTitleDisplayMode(.inline)
        .task(id: query) { try? await Task.sleep(for: .milliseconds(250)); await load() }
        .sheet(item: $editing) { EkkoMemoryEditor(item: $0) { await load() } }
    }

    private func load() async { items = (await store.attempt({ try await store.api.ekkoMemory(query: query) })) ?? items }
}

private struct EkkoMemoryEditor: View {
    @EnvironmentObject var store: AppStore
    @Environment(\.dismiss) var dismiss
    @State var item: EkkoMemoryItem
    let saved: () async -> Void

    var body: some View {
        NavigationStack {
            Form {
                TextField("Title", text: $item.title)
                TextField("Content", text: $item.content, axis: .vertical).lineLimit(5...15)
                TextField("Tags", text: Binding(get: { item.tags.joined(separator: ", ") }, set: { item.tags = $0.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) } }))
            }
            .navigationTitle("Edit memory")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { Task { do { try await store.api.updateEkkoMemory(item); await saved(); dismiss() } catch { store.errorMessage = error.localizedDescription } } }
                }
            }
        }
    }
}
