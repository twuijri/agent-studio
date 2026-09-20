import SwiftUI

/// The card being dragged: where it was picked up (board coordinates), how
/// far the finger moved, and the column under the finger.
struct KanbanDragState: Equatable {
    let task: KanbanTask
    let origin: CGRect
    var translation: CGSize = .zero
    var location: CGPoint
    var target: KanbanColumnID?

    var ghostFrame: CGRect { origin.offsetBy(dx: translation.width, dy: translation.height) }
}

/// A drop that needs the user before Hermes runs anything: several meanings
/// (waiting = schedule or block), a required reason (block), or a terminal
/// confirmation (archive).
enum KanbanDropPrompt: Identifiable, Equatable {
    case choose(taskID: String, from: KanbanStatus, options: [KanbanColumnDrop])
    case reason(taskID: String, drop: KanbanColumnDrop)
    case confirmArchive(taskID: String, drop: KanbanColumnDrop)

    var taskID: String {
        switch self {
        case let .choose(taskID, _, _), let .reason(taskID, _), let .confirmArchive(taskID, _): return taskID
        }
    }

    var id: String {
        switch self {
        case .choose: return "choose-" + taskID
        case .reason: return "reason-" + taskID
        case .confirmArchive: return "archive-" + taskID
        }
    }

    var needsSheet: Bool { if case .confirmArchive = self { return false }; return true }
    var isArchiveConfirmation: Bool { !needsSheet }
}

struct KanbanScrollRequest: Equatable {
    let column: KanbanColumnID
    let sequence: Int
}

/// State of the selected board: the tasks, the optimistic pending
/// transitions, the status filter, the device-local card order, the drag in
/// progress and the prompt a drop opened. The rules are in `Core/KanbanBoard.swift`.
@MainActor
final class KanbanBoardController: ObservableObject {
    enum LoadState: Equatable { case idle, loading, loaded, failed(String) }

    @Published var boards: [KanbanBoard] = []
    @Published var selectedBoard = "default"
    @Published private(set) var tasks: [KanbanTask] = []
    @Published private(set) var loadState: LoadState = .idle
    @Published private(set) var snapshot = KanbanBoardSnapshot(tasks: [])
    @Published private(set) var serverCounts: [KanbanStatus: Int] = [:]
    @Published private(set) var serverTotal: Int?
    @Published private(set) var filter: KanbanStatus?
    @Published private(set) var pending: [String: KanbanStatus] = [:]
    @Published private(set) var drag: KanbanDragState?
    @Published private(set) var prompt: KanbanDropPrompt?
    @Published private(set) var autoScrollRequest: KanbanScrollRequest?
    @Published var columnFrames: [KanbanColumnID: CGRect] = [:]
    @Published var cardFrames: [String: CGRect] = [:]
    @Published var boardWidth: CGFloat = 0

    /// Distance from the board's physical edge that scrolls to the neighbouring column while dragging.
    static let autoScrollMargin: CGFloat = 40
    private static let autoScrollInterval: TimeInterval = 0.7

    let layouts: KanbanLayoutStore
    private weak var store: AppStore?
    private var generation = 0
    private var lastAutoScroll = Date.distantPast
    private var scrollSequence = 0

    init(layouts: KanbanLayoutStore = KanbanLayoutStore()) { self.layouts = layouts }

    func attach(_ store: AppStore) { self.store = store }

    // MARK: Derived

    /// Tasks with a pending Hermes command shown in their target status, even
    /// if a refresh in between still reports the old one.
    var displayedTasks: [KanbanTask] {
        guard !pending.isEmpty else { return tasks }
        return tasks.map { task in
            guard let expected = pending[task.id], expected != task.stage else { return task }
            var moved = task
            moved.status = expected.rawValue
            return moved
        }
    }

    var visibleColumns: [KanbanColumn] { KanbanBoardRules.visibleColumns(filter: filter) }
    var inboxVisible: Bool { KanbanBoardRules.inboxVisible(filter: filter) }
    var isInitialLoading: Bool { loadState == .loading && tasks.isEmpty }
    var loadFailure: String? {
        if case let .failed(message) = loadState { return message }
        return nil
    }
    var total: Int { serverTotal ?? tasks.count }
    var draggingStatus: KanbanStatus? { drag?.task.stage }
    var hasCustomLayout: Bool { layouts.layout(board: selectedBoard).hasCustomLayout }

    func count(of status: KanbanStatus) -> Int { serverCounts[status] ?? snapshot.count(of: status) }
    func isPending(_ taskID: String) -> Bool { pending[taskID] != nil }

    private func rebuild() {
        snapshot = KanbanBoardSnapshot(tasks: displayedTasks, layout: layouts.layout(board: selectedBoard))
    }

    // MARK: Loading

    func load() async {
        guard let store else { return }
        loadState = .loading
        do {
            boards = try await store.api.boards()
            if !boards.contains(where: { $0.id == selectedBoard }) { selectedBoard = boards.first?.id ?? "default" }
        } catch {
            fail(error)
            return
        }
        await loadTasks()
    }

    /// `silent` keeps the current cards on screen while the list refreshes.
    func loadTasks(silent: Bool = false) async {
        guard let store else { return }
        generation += 1
        let current = generation
        let board = selectedBoard
        if !silent { loadState = .loading }
        do {
            let fetched = try await store.api.kanbanTasks(board: board)
            guard current == generation else { return }
            tasks = fetched
            loadState = .loaded
            rebuild()
        } catch {
            guard current == generation else { return }
            fail(error)
        }
        await loadStats()
    }

    func loadStats() async {
        guard let store else { return }
        let board = selectedBoard
        guard let stats = try? await store.api.kanbanStats(board: board), board == selectedBoard else { return }
        let byStatus = stats.object("by_status")
        var counts: [KanbanStatus: Int] = [:]
        for status in KanbanStatus.allCases where byStatus[status.rawValue] != nil { counts[status] = byStatus.int(status.rawValue) }
        serverCounts = counts
        serverTotal = stats["total"] == nil ? nil : stats.int("total")
    }

    func refresh() async { await loadTasks(silent: true) }

    func selectBoard(_ board: String) {
        guard board != selectedBoard else { return }
        selectedBoard = board
        tasks = []
        serverCounts = [:]
        serverTotal = nil
        pending = [:]
        rebuild()
        Task { await loadTasks() }
    }

    /// Every failure is visible twice on purpose: the banner carries the
    /// server's message, and the board itself shows a retry state instead of
    /// staying blank after the banner is dismissed.
    private func fail(_ error: Error) {
        let message = error.localizedDescription
        loadState = .failed(message)
        store?.errorMessage = message
    }

    // MARK: Filter and layout

    func toggleFilter(_ status: KanbanStatus?) {
        filter = (status == nil || filter == status) ? nil : status
    }

    func setCardOrder(_ ids: [String], column: KanbanColumnID) {
        layouts.setCardOrder(ids, column: column, board: selectedBoard)
        rebuild()
    }

    func resetLayout() {
        layouts.reset(board: selectedBoard)
        rebuild()
    }

    // MARK: Drag

    func beginDrag(task: KanbanTask, frame: CGRect) {
        guard drag == nil, !isPending(task.id) else { return }
        drag = KanbanDragState(task: task, origin: frame, location: CGPoint(x: frame.midX, y: frame.midY), target: KanbanBoardRules.columnID(for: task.stage))
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
    }

    func updateDrag(taskID: String, location: CGPoint, translation: CGSize) {
        guard var state = drag, state.task.id == taskID else { return }
        state.translation = translation
        state.location = location
        state.target = KanbanDragGeometry.column(at: location, frames: columnFrames)
        drag = state
        autoScrollIfAtEdge(x: location.x)
    }

    func finishDrag(taskID: String, location: CGPoint) {
        guard let state = drag, state.task.id == taskID else { return }
        drag = nil
        autoScrollRequest = nil
        guard let target = KanbanDragGeometry.column(at: location, frames: columnFrames) else { return }
        let column = KanbanBoardRules.column(target)
        if column.statuses.contains(state.task.stage) {
            reorder(state.task, in: target, y: location.y)
        } else {
            handleDrop(task: state.task, column: column)
        }
    }

    /// Also called when the gesture is cancelled by the system; a no-op after `finishDrag`.
    func cancelDrag(taskID: String) {
        guard drag?.task.id == taskID else { return }
        drag = nil
        autoScrollRequest = nil
    }

    private func autoScrollIfAtEdge(x: CGFloat) {
        guard let side = KanbanDragGeometry.edge(x: x, width: boardWidth, margin: Self.autoScrollMargin),
              Date().timeIntervalSince(lastAutoScroll) > Self.autoScrollInterval,
              let current = KanbanDragGeometry.nearest(x: x, frames: columnFrames),
              let next = KanbanDragGeometry.neighbour(of: current, frames: columnFrames, side: side) else { return }
        lastAutoScroll = Date()
        scrollSequence += 1
        autoScrollRequest = KanbanScrollRequest(column: next, sequence: scrollSequence)
    }

    /// Same-column drop: a device-local reorder, never sent to Hermes.
    private func reorder(_ task: KanbanTask, in column: KanbanColumnID, y: CGFloat) {
        let ids = snapshot.cards(in: column).map(\.id)
        let others = ids.filter { $0 != task.id }
        let frames = others.compactMap { cardFrames[$0] }
        guard frames.count == others.count else { return }
        let index = KanbanDragGeometry.insertionIndex(y: y, cardFrames: frames)
        let next = KanbanDragGeometry.reordered(ids, moving: task.id, to: index)
        guard next != ids else { return }
        setCardOrder(next, column: column)
    }

    // MARK: Drops and transitions

    private func handleDrop(task: KanbanTask, column: KanbanColumn) {
        let options = KanbanBoardRules.dropOptions(from: task.stage, column: column)
        guard let first = options.first else { return }
        if options.count > 1 {
            beginPending(task.id, expected: first.to)
            prompt = .choose(taskID: task.id, from: task.stage, options: options)
            return
        }
        applyDrop(taskID: task.id, drop: first)
    }

    private func applyDrop(taskID: String, drop: KanbanColumnDrop) {
        // The card already sits in its new column; keep it there while we ask.
        beginPending(taskID, expected: drop.to)
        if drop.transition.requiresReason {
            prompt = .reason(taskID: taskID, drop: drop)
        } else if drop.transition.confirm {
            prompt = .confirmArchive(taskID: taskID, drop: drop)
        } else {
            Task { await run(drop.transition.action, taskID: taskID, note: nil, expected: drop.to) }
        }
    }

    func choose(_ drop: KanbanColumnDrop) {
        guard case let .choose(taskID, _, _)? = prompt else { return }
        prompt = nil
        applyDrop(taskID: taskID, drop: drop)
    }

    func confirmReason(_ reason: String) {
        guard case let .reason(taskID, drop)? = prompt else { return }
        let text = reason.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        prompt = nil
        Task { await run(drop.transition.action, taskID: taskID, note: text, expected: drop.to) }
    }

    func confirmArchive() {
        guard case let .confirmArchive(taskID, drop)? = prompt else { return }
        prompt = nil
        Task { await run(.archive, taskID: taskID, note: nil, expected: drop.to) }
    }

    /// Dismissing a prompt puts the card back where the server has it.
    func cancelPrompt() {
        guard let prompt else { return }
        self.prompt = nil
        endPending(prompt.taskID)
    }

    /// Card quick actions (web `KanbanTaskCard`): promote from todo, archive from done.
    func promote(_ taskID: String) {
        Task { await run(.promote, taskID: taskID, note: nil, expected: .ready) }
    }

    func requestArchive(_ taskID: String) {
        applyDrop(taskID: taskID, drop: KanbanColumnDrop(to: .archived, transition: KanbanTransition(action: .archive, confirm: true)))
    }

    private func beginPending(_ taskID: String, expected: KanbanStatus) {
        pending[taskID] = expected
        rebuild()
    }

    private func endPending(_ taskID: String) {
        guard pending[taskID] != nil else { return }
        pending[taskID] = nil
        rebuild()
    }

    func run(_ action: KanbanTransitionAction, taskID: String, note: String?, expected: KanbanStatus) async {
        guard let store else { return }
        beginPending(taskID, expected: expected)
        let board = selectedBoard
        do {
            try await store.api.runKanbanTransition(KanbanTransitionRequest.make(action, taskID: taskID, note: note), board: board)
            store.notify(action.successMessage)
        } catch {
            store.errorMessage = error.localizedDescription
        }
        if board == selectedBoard { await loadTasks(silent: true) }
        endPending(taskID)
    }
}
