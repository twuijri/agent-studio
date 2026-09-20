import CoreGraphics
import Foundation

// The board model of the desktop client, ported from
// `packages/client/src/utils/hermes/kanban-board.ts`. Everything here is pure:
// Hermes keeps nine task statuses; the board shows them as an intake strip
// plus four workflow columns, `archived` folded inside `done`. A drop maps to
// the Hermes command the server bridges, or to nothing when Hermes could not
// apply it. Same-column moves are reorders that stay on the device (Hermes
// has no order field), and columns keep the workflow order.

// MARK: - Statuses

enum KanbanStatus: String, CaseIterable, Identifiable, Hashable {
    case triage, todo, scheduled, ready, running, blocked, review, done, archived

    var id: String { rawValue }

    /// Tolerant of case, dashes and spaces; anything else is not a Hermes status.
    init?(raw: String) {
        let key = raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            .replacingOccurrences(of: "-", with: "_")
            .replacingOccurrences(of: " ", with: "_")
        self.init(rawValue: key)
    }

    /// The web's `kanban.columns.<status>` wording; key `kanban_status_<status>` in both locale files.
    var title: String { String(localized: String.LocalizationValue("kanban_status_" + rawValue)) }

    /// Statuses the detail screen may set directly; `archived` only goes through the archive flag.
    static let pickable: [KanbanStatus] = allCases.filter { $0 != .archived }
}

// MARK: - Transitions

enum KanbanTransitionAction: String, CaseIterable {
    case complete, block, unblock, promote, schedule, requestReview, reopenReview, archive
}

struct KanbanTransition: Equatable {
    let action: KanbanTransitionAction
    /// The Hermes CLI requires a reason, so the UI must ask before running it.
    var requiresReason = false
    /// Terminal transition; confirm before running it.
    var confirm = false
}

// MARK: - Columns

enum KanbanColumnID: String, CaseIterable, Identifiable {
    case queue, waiting, review, done

    var id: String { rawValue }

    /// The web's `kanban.board.columns.<column>` wording; key `kanban_column_<column>`.
    var title: String { String(localized: String.LocalizationValue("kanban_column_" + rawValue)) }

    /// Collapses to a narrow strip while empty (the desktop marks `waiting` alone).
    var collapsible: Bool { self == .waiting }
}

struct KanbanColumn: Identifiable, Equatable {
    let id: KanbanColumnID
    let statuses: [KanbanStatus]
}

struct KanbanColumnDrop: Equatable {
    /// Target status inside the column that Hermes will move the task to.
    let to: KanbanStatus
    let transition: KanbanTransition
}

enum KanbanBoardRules {
    static let statuses: [KanbanStatus] = KanbanStatus.allCases
    static let inboxStatus: KanbanStatus = .triage
    static let archivedStatus: KanbanStatus = .archived

    static let columns: [KanbanColumn] = [
        KanbanColumn(id: .queue, statuses: [.todo, .ready, .running]),
        KanbanColumn(id: .waiting, statuses: [.scheduled, .blocked]),
        KanbanColumn(id: .review, statuses: [.review]),
        KanbanColumn(id: .done, statuses: [.done]),
    ]

    // Keyed by target status, then source status, mirroring the server
    // controller's source guards. `triage` and `running` belong to the intake
    // flow and the worker, so nothing can be dropped there.
    private static let dropRules: [KanbanStatus: [KanbanStatus: KanbanTransition]] = [
        .todo: [
            .blocked: KanbanTransition(action: .unblock),
            .scheduled: KanbanTransition(action: .unblock),
            .review: KanbanTransition(action: .reopenReview),
        ],
        .scheduled: [
            .todo: KanbanTransition(action: .schedule),
            .ready: KanbanTransition(action: .schedule),
            .running: KanbanTransition(action: .schedule),
            .blocked: KanbanTransition(action: .schedule),
        ],
        .ready: [
            .todo: KanbanTransition(action: .promote),
            .blocked: KanbanTransition(action: .unblock),
            .scheduled: KanbanTransition(action: .unblock),
            .review: KanbanTransition(action: .reopenReview),
        ],
        .blocked: [
            .running: KanbanTransition(action: .block, requiresReason: true),
            .ready: KanbanTransition(action: .block, requiresReason: true),
        ],
        .review: [
            .running: KanbanTransition(action: .requestReview),
            .ready: KanbanTransition(action: .requestReview),
        ],
        .done: [
            .running: KanbanTransition(action: .complete),
            .ready: KanbanTransition(action: .complete),
            .blocked: KanbanTransition(action: .complete),
        ],
        .archived: [
            .done: KanbanTransition(action: .archive, confirm: true),
        ],
    ]

    /// The status a task is shown in while its command runs (web `TRANSITION_TARGET`).
    static let transitionTarget: [KanbanTransitionAction: KanbanStatus] = [
        .complete: .done, .block: .blocked, .unblock: .ready, .promote: .ready,
        .schedule: .scheduled, .requestReview: .review, .reopenReview: .todo, .archive: .archived,
    ]

    static func resolveTransition(from: KanbanStatus, to: KanbanStatus) -> KanbanTransition? {
        if from == to { return nil }
        return dropRules[to]?[from]
    }

    static func isDropTarget(from: KanbanStatus, to: KanbanStatus) -> Bool {
        from == to || resolveTransition(from: from, to: to) != nil
    }

    static func column(_ id: KanbanColumnID) -> KanbanColumn {
        columns.first { $0.id == id }!
    }

    /// Column that shows a status as a card, or nil for the intake strip.
    static func columnID(for status: KanbanStatus) -> KanbanColumnID? {
        if status == archivedStatus { return .done }
        return columns.first { $0.statuses.contains(status) }?.id
    }

    /// Every Hermes transition a drop into `column` could mean for a card coming
    /// from `from`. Same-column moves are reorders and never yield a transition.
    /// One result runs directly; several (schedule vs block) need the user to pick.
    static func dropOptions(from: KanbanStatus, column: KanbanColumn) -> [KanbanColumnDrop] {
        if column.statuses.contains(from) { return [] }
        var seen: Set<KanbanTransitionAction> = []
        var options: [KanbanColumnDrop] = []
        for to in column.statuses {
            guard let transition = resolveTransition(from: from, to: to), !seen.contains(transition.action) else { continue }
            seen.insert(transition.action)
            options.append(KanbanColumnDrop(to: to, transition: transition))
        }
        return options
    }

    static func isColumnDropTarget(from: KanbanStatus, column: KanbanColumn) -> Bool {
        column.statuses.contains(from) || !dropOptions(from: from, column: column).isEmpty
    }

    /// A status filter narrows the board to the one column that shows that status.
    static func visibleColumns(filter: KanbanStatus?) -> [KanbanColumn] {
        guard let filter else { return columns }
        guard let id = columnID(for: filter) else { return [] }
        return columns.filter { $0.id == id }
    }

    static func inboxVisible(filter: KanbanStatus?) -> Bool {
        filter == nil || filter == inboxStatus
    }

    /// The web's `kanban.board.columns.inbox`; key `kanban_column_inbox`.
    static var inboxTitle: String { String(localized: "kanban_column_inbox") }

    /// A collapsible column only takes a full slot when it has cards, when a card
    /// that may land there is being dragged, or after the user opened it by hand.
    static func isCollapsed(collapsible: Bool, cardCount: Int, dropOpen: Bool, expandedByUser: Bool) -> Bool {
        collapsible && cardCount == 0 && !dropOpen && !expandedByUser
    }
}

// MARK: - Device-local layout (manual card order inside a column)

struct KanbanBoardLayout: Equatable {
    var cards: [KanbanColumnID: [String]] = [:]

    static let storagePrefix = "hermes.kanban.layout."

    static func storageKey(board: String) -> String { storagePrefix + board }

    var hasCustomLayout: Bool { !cards.isEmpty }

    /// Older layouts keyed cards by status or stored a column order; both are ignored and dropped on the next save.
    static func parse(_ raw: String?) -> KanbanBoardLayout {
        guard let raw, let data = raw.data(using: .utf8),
              let parsed = try? JSONSerialization.jsonObject(with: data) as? JSON else { return KanbanBoardLayout() }
        var cards: [KanbanColumnID: [String]] = [:]
        for (column, ids) in parsed.object("cards") {
            guard let id = KanbanColumnID(rawValue: column), let list = ids as? [Any] else { continue }
            let clean = list.compactMap { $0 as? String }.filter { !$0.isEmpty }
            if !clean.isEmpty { cards[id] = clean }
        }
        return KanbanBoardLayout(cards: cards)
    }

    func encoded() -> String? {
        let object: JSON = ["cards": Dictionary(uniqueKeysWithValues: cards.map { ($0.key.rawValue, $0.value) })]
        guard let data = try? JSONSerialization.data(withJSONObject: object, options: [.sortedKeys]) else { return nil }
        return String(data: data, encoding: .utf8)
    }
}

/// Persists the manual card order per board in `UserDefaults` under the same
/// key the web client uses in `localStorage`. Never sent to Hermes.
final class KanbanLayoutStore {
    let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) { self.defaults = defaults }

    func layout(board: String) -> KanbanBoardLayout {
        KanbanBoardLayout.parse(defaults.string(forKey: KanbanBoardLayout.storageKey(board: board)))
    }

    func save(_ layout: KanbanBoardLayout, board: String) {
        let key = KanbanBoardLayout.storageKey(board: board)
        if layout.hasCustomLayout, let encoded = layout.encoded() { defaults.set(encoded, forKey: key) }
        else { defaults.removeObject(forKey: key) }
    }

    func setCardOrder(_ ids: [String], column: KanbanColumnID, board: String) {
        var layout = self.layout(board: board)
        layout.cards[column] = ids
        save(layout, board: board)
    }

    func reset(board: String) { save(KanbanBoardLayout(), board: board) }
}

/// Newest first, like the web's default column order.
func defaultKanbanCardOrder(_ tasks: [KanbanTask]) -> [KanbanTask] {
    tasks.sorted { $0.createdAt > $1.createdAt }
}

/// Tasks not covered by the saved order (new arrivals) stay on top in default
/// order so they remain visible; saved tasks keep the viewer's manual order.
func orderKanbanCards(_ tasks: [KanbanTask], savedIDs: [String]?) -> [KanbanTask] {
    let sorted = defaultKanbanCardOrder(tasks)
    guard let savedIDs, !savedIDs.isEmpty else { return sorted }
    var byID = Dictionary(sorted.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
    var saved: [KanbanTask] = []
    for id in savedIDs {
        guard let task = byID.removeValue(forKey: id) else { continue }
        saved.append(task)
    }
    return sorted.filter { byID[$0.id] != nil } + saved
}

// MARK: - Folding the task list into the board

struct KanbanBoardSnapshot {
    let inbox: [KanbanTask]
    let archived: [KanbanTask]
    let columns: [KanbanColumnID: [KanbanTask]]

    init(tasks: [KanbanTask], layout: KanbanBoardLayout = KanbanBoardLayout()) {
        inbox = defaultKanbanCardOrder(tasks.filter { $0.stage == KanbanBoardRules.inboxStatus })
        archived = defaultKanbanCardOrder(tasks.filter { $0.stage == KanbanBoardRules.archivedStatus })
        var columns: [KanbanColumnID: [KanbanTask]] = [:]
        for column in KanbanBoardRules.columns {
            let cards = tasks.filter { column.statuses.contains($0.stage) }
            columns[column.id] = orderKanbanCards(cards, savedIDs: layout.cards[column.id])
        }
        self.columns = columns
    }

    func cards(in column: KanbanColumnID) -> [KanbanTask] { columns[column] ?? [] }

    func count(of status: KanbanStatus) -> Int {
        switch status {
        case .triage: return inbox.count
        case .archived: return archived.count
        default: return (columns[KanbanBoardRules.columnID(for: status) ?? .queue] ?? []).filter { $0.stage == status }.count
        }
    }
}

// MARK: - Route of each transition (mirrors the web store)

struct KanbanTransitionRequest {
    /// Path without the `?board=` query; the client appends it.
    let path: String
    let body: JSON

    /// `stores/hermes/kanban.ts`: complete/unblock post task id lists, archive is a
    /// bulk update with `archive: true`, the rest post to the task's own route.
    /// A missing note is omitted, like `JSON.stringify` dropping `undefined`.
    static func make(_ action: KanbanTransitionAction, taskID: String, note: String?) -> KanbanTransitionRequest {
        let note = note?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
        func noted(_ key: String) -> JSON {
            guard let note else { return [:] }
            return [key: note]
        }
        let task = "/api/hermes/kanban/\(taskID.urlEncoded)"
        var completeBody: JSON = ["task_ids": [taskID]]
        if let note { completeBody["summary"] = note }
        switch action {
        case .complete: return KanbanTransitionRequest(path: "/api/hermes/kanban/complete", body: completeBody)
        case .unblock: return KanbanTransitionRequest(path: "/api/hermes/kanban/unblock", body: ["task_ids": [taskID]])
        case .block: return KanbanTransitionRequest(path: task + "/block", body: ["reason": note ?? ""])
        case .promote: return KanbanTransitionRequest(path: task + "/promote", body: noted("reason"))
        case .schedule: return KanbanTransitionRequest(path: task + "/schedule", body: noted("reason"))
        case .requestReview: return KanbanTransitionRequest(path: task + "/request-review", body: noted("summary"))
        case .reopenReview: return KanbanTransitionRequest(path: task + "/reopen-review", body: noted("reason"))
        case .archive: return KanbanTransitionRequest(path: "/api/hermes/kanban/tasks/bulk", body: ["ids": [taskID], "archive": true])
        }
    }
}

extension KanbanTransitionAction {
    /// Web `kanban.message.*` after the command succeeded.
    var successMessage: String {
        switch self {
        case .complete: return String(localized: "Task completed")
        case .block: return String(localized: "Task blocked")
        case .unblock: return String(localized: "Task unblocked")
        case .promote: return String(localized: "Task promoted to ready")
        case .schedule: return String(localized: "Task scheduled")
        case .requestReview: return String(localized: "Review requested")
        case .reopenReview: return String(localized: "Review reopened")
        case .archive: return String(localized: "Task archived")
        }
    }
}

// MARK: - Card stage (what the card itself shows)

enum KanbanCardStage {
    /// Columns group several statuses, so the card names its stage; todo and triage need no badge.
    static func badge(for status: KanbanStatus) -> String? {
        switch status {
        case .ready, .running, .scheduled, .blocked, .review, .done, .archived: return status.title
        case .triage, .todo: return nil
        }
    }

    static func showsRunningRing(_ status: KanbanStatus) -> Bool { status == .running }

    static func priorityLabel(level: Int) -> String {
        if level >= 3 { return String(localized: "High") }
        if level == 2 { return String(localized: "Medium") }
        return String(localized: "Low")
    }

    static func showsPriority(level: Int) -> Bool { level >= 2 }
}

// MARK: - Drag geometry (physical coordinates; RTL needs nothing special)

enum KanbanPhysicalSide { case left, right }

enum KanbanDragGeometry {
    /// The column under the finger; the workflow order breaks ties between overlapping frames.
    static func column(at point: CGPoint, frames: [KanbanColumnID: CGRect]) -> KanbanColumnID? {
        KanbanColumnID.allCases.first { frames[$0]?.contains(point) == true }
    }

    /// The column whose frame is next to `column` on `side`, or nil at the end of the board.
    static func neighbour(of column: KanbanColumnID, frames: [KanbanColumnID: CGRect], side: KanbanPhysicalSide) -> KanbanColumnID? {
        guard let origin = frames[column] else { return nil }
        let candidates = frames.filter { $0.key != column }
        switch side {
        case .left:
            return candidates.filter { $0.value.midX < origin.midX }.max { $0.value.midX < $1.value.midX }?.key
        case .right:
            return candidates.filter { $0.value.midX > origin.midX }.min { $0.value.midX < $1.value.midX }?.key
        }
    }

    /// The column nearest to `x` (by centre), for a finger in the gap between columns.
    static func nearest(x: CGFloat, frames: [KanbanColumnID: CGRect]) -> KanbanColumnID? {
        frames.min { abs($0.value.midX - x) < abs($1.value.midX - x) }?.key
    }

    /// Which board edge the finger is pressing against, if any.
    static func edge(x: CGFloat, width: CGFloat, margin: CGFloat) -> KanbanPhysicalSide? {
        if width <= 0 { return nil }
        if x <= margin { return .left }
        if x >= width - margin { return .right }
        return nil
    }

    /// Position of a dropped card among `cardFrames` (top to bottom): before the first card whose middle is below the finger.
    static func insertionIndex(y: CGFloat, cardFrames: [CGRect]) -> Int {
        cardFrames.filter { $0.midY < y }.count
    }

    /// The new id order after moving `id` to `index` inside its own column.
    static func reordered(_ ids: [String], moving id: String, to index: Int) -> [String] {
        var rest = ids.filter { $0 != id }
        let clamped = max(0, min(index, rest.count))
        rest.insert(id, at: clamped)
        return rest
    }
}
