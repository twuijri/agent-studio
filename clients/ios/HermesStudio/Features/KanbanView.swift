import QuickLook
import SwiftUI

/// The Kanban screen: the board picker, the desktop board at phone width
/// (`Features/Kanban/`), and the task editor and detail sheets. Loading,
/// failure and empty states are explicit so the screen is never blank.
struct KanbanView: View {
    @EnvironmentObject private var store: AppStore
    @StateObject private var board = KanbanBoardController()
    @State private var creating = false
    @State private var selectedTask: KanbanTask?
    @State private var showingOperations = false

    var body: some View {
        VStack(spacing: 0) {
            if board.boards.count > 1 { boardPicker }
            KanbanBoardView(board: board) { selectedTask = $0 }
        }
        .background(CoreHubTokens.Palette.bgPrimary)
        .navigationTitle(NavDestination.kanban.title)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button { Task { await board.refresh() } } label: { Image(systemName: "arrow.clockwise") }
                Button { showingOperations = true } label: { Image(systemName: "gauge.with.dots.needle.50percent") }
                Button { creating = true } label: { Image(systemName: "plus") }
            }
        }
        .sheet(isPresented: $creating) { TaskEditorView(board: board.selectedBoard) { await board.refresh() } }
        .sheet(item: $selectedTask) { task in TaskDetailView(task: task, board: board.selectedBoard) { await board.refresh() } }
        .sheet(isPresented: $showingOperations) { KanbanOperationsSheet(board: board.selectedBoard) { await board.refresh() } }
        .task {
            board.attach(store)
            await board.load()
        }
    }

    private var boardPicker: some View {
        Picker("Board", selection: Binding(get: { board.selectedBoard }, set: { board.selectBoard($0) })) {
            ForEach(board.boards) { item in Text(item.name).tag(item.id) }
        }
        .pickerStyle(.segmented)
        .padding()
    }
}

/// Board health: the server's stats, dispatch, and the diagnostics rows.
struct KanbanOperationsSheet: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    let board: String
    let onChange: () async -> Void
    @State private var stats: JSON = [:]
    @State private var diagnostics: [JSON] = []

    var body: some View {
        NavigationStack {
            List {
                Section("Statistics") {
                    LabeledContent("Total", value: "\(stats.int("total"))")
                    ForEach(stats.object("by_status").keys.sorted(), id: \.self) { key in
                        LabeledContent(KanbanStatus(raw: key)?.title ?? key.capitalized, value: "\(stats.object("by_status").int(key))")
                    }
                }
                Section("Operations") {
                    Button("Dispatch ready tasks") { Task { await dispatch(false) } }
                    Button("Preview dispatch") { Task { await dispatch(true) } }
                }
                Section("Diagnostics") {
                    ForEach(diagnostics.indices, id: \.self) { index in diagnosticRow(diagnostics[index]) }
                }
            }
            .navigationTitle("Board health")
            .toolbar { Button("Done") { dismiss() } }
            .task { await load() }
        }
    }

    private func diagnosticRow(_ row: JSON) -> some View {
        VStack(alignment: .leading) {
            Text(row.string("message", "title", "kind")).font(.headline)
            Text(row.string("detail", "severity", "task_id")).font(.caption).foregroundStyle(.secondary)
        }
    }

    private func load() async {
        stats = (await store.attempt({ try await store.api.kanbanStats(board: board) })) ?? [:]
        diagnostics = (await store.attempt({ try await store.api.kanbanDiagnostics(board: board) })) ?? []
    }

    private func dispatch(_ dryRun: Bool) async {
        do {
            try await store.api.dispatchKanban(board: board, dryRun: dryRun)
            store.notify(dryRun ? String(localized: "Dispatch preview complete") : String(localized: "Tasks dispatched"))
            await onChange()
            await load()
        } catch { store.errorMessage = error.localizedDescription }
    }
}

private struct TaskEditorView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    let board: String
    let onSave: () async -> Void
    @State private var title = ""; @State private var description = ""; @State private var priority = "medium"; @State private var saving = false
    var body: some View {
        NavigationStack { Form { Section("Task") { TextField("Title", text: $title).contentDirection(of: title); TextField("Description", text: $description, axis: .vertical).lineLimit(3...8).contentDirection(of: description) }; Section("Priority") { Picker("Priority", selection: $priority) { Text("Low").tag("low"); Text("Medium").tag("medium"); Text("High").tag("high") }.pickerStyle(.segmented) } }.navigationTitle("New task").navigationBarTitleDisplayMode(.inline).toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }; ToolbarItem(placement: .confirmationAction) { Button("Create") { Task { await save() } }.disabled(title.isEmpty || saving) } } }
    }
    private func save() async { saving = true; do { try await store.api.createTask(board: board, title: title, description: description, priority: priority); await onSave(); dismiss() } catch { store.errorMessage = error.localizedDescription }; saving = false }
}

private struct TaskDetailView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    let task: KanbanTask; let board: String; let onChange: () async -> Void
    @State private var comment = ""; @State private var assignee = ""
    @State private var reason = ""; @State private var summary = ""; @State private var log = ""; @State private var attachments: [JSON] = []
    var body: some View {
        NavigationStack { Form { Section { Text(task.title).font(.title3.bold()).contentDirection(of: task.title); if !task.description.isEmpty { Text(task.description).contentDirection(of: task.description) } } header: { Text("Task") }; Section("Status") { Picker("Status", selection: Binding(get: { task.status }, set: { value in Task { await store.attempt({ try await store.api.moveTasks(board: board, ids: [task.id], status: value) }); await onChange(); dismiss() } })) { ForEach(KanbanStatus.pickable) { Text($0.title).tag($0.rawValue) } }; TextField("Reason or completion summary", text: $reason, axis: .vertical).contentDirection(of: reason); HStack { Button("Block") { Task { await block() } }.disabled(reason.isEmpty); Button("Unblock") { Task { await unblock() } }; Button("Complete") { Task { await complete() } } } }; Section("Assignee") { Picker("Agent", selection: $assignee) { Text("Unassigned").tag(""); ForEach(store.profiles) { Text($0.name).tag($0.name) } }.onChange(of: assignee) { _, value in Task { if let profile = value.nilIfEmpty { await store.attempt({ try await store.api.reassignKanban(board: board, id: task.id, profile: profile) }) } else { await store.attempt({ try await store.api.assignTask(board: board, id: task.id, profile: nil) }) }; await onChange() } } }; Section("Comment") { TextField("Add a comment…", text: $comment, axis: .vertical).contentDirection(of: comment); Button("Post comment") { Task { await store.attempt({ try await store.api.commentTask(board: board, id: task.id, comment: comment) }); comment = ""; await onChange() } }.disabled(comment.isEmpty) }; Section("Attachments") { ForEach(attachments.indices, id: \.self) { i in let row = attachments[i]; Button { Task { await openAttachment(row) } } label: { LabeledContent(row.string("filename"), value: ByteCountFormatter.string(fromByteCount: Int64(row.int("size")), countStyle: .file)) }.disabled(downloadingAttachment) } }; if !log.isEmpty { Section("Worker log") { Text(log).font(.caption.monospaced()).textSelection(.enabled) } } }.navigationTitle("Task details").navigationBarTitleDisplayMode(.inline).toolbar { Button("Done") { dismiss() } }.onAppear { assignee = task.assignee ?? "" }.task { await loadDetails() }.quickLookPreview($attachmentURL) }
    }
    @State private var attachmentURL: URL?; @State private var downloadingAttachment = false
    /// Downloads the attachment with the bearer token in the header (never in the URL) and previews the local copy.
    private func openAttachment(_ row: JSON) async { downloadingAttachment = true; defer { downloadingAttachment = false }; do { attachmentURL = try await store.api.downloadKanbanAttachment(board: board, taskID: task.id, attachmentID: row.int("id"), name: row.string("filename")) } catch { store.errorMessage = error.localizedDescription } }
    private func loadDetails() async { if let result = await store.attempt({ try await store.api.kanbanLog(board: board, id: task.id) }) { log = result.string("content") }; attachments = (await store.attempt({ try await store.api.kanbanAttachments(board: board, id: task.id) })) ?? [] }
    private func block() async { do { try await store.api.blockKanban(board: board, id: task.id, reason: reason); await onChange(); dismiss() } catch { store.errorMessage = error.localizedDescription } }
    private func unblock() async { do { try await store.api.unblockKanban(board: board, ids: [task.id]); await onChange(); dismiss() } catch { store.errorMessage = error.localizedDescription } }
    private func complete() async { do { try await store.api.completeKanban(board: board, ids: [task.id], summary: reason); await onChange(); dismiss() } catch { store.errorMessage = error.localizedDescription } }
}
