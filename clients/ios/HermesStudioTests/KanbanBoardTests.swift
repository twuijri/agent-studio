import XCTest
@testable import HermesStudio

/// The board model against the same cases as the web's
/// `tests/client/kanban-board-utils.test.ts`, plus the phone's own pure parts:
/// the route of every transition, the drag geometry, the server's response
/// shape (JSON nulls included) and the column wording in both locales.
final class KanbanBoardTests: XCTestCase {

    private func task(_ id: String, _ status: KanbanStatus, createdAt: Double = 0, priority: Int = 1) -> KanbanTask {
        KanbanTask(["id": id, "title": id, "status": status.rawValue, "priority": priority, "created_at": createdAt])
    }

    private func actions(_ from: KanbanStatus, _ column: KanbanColumnID) -> [String] {
        KanbanBoardRules.dropOptions(from: from, column: KanbanBoardRules.column(column)).map { "\($0.transition.action.rawValue)->\($0.to.rawValue)" }
    }

    // MARK: - Transitions

    func testDropsMapToTheHermesCommandsTheServerBridges() {
        XCTAssertEqual(KanbanBoardRules.resolveTransition(from: .todo, to: .ready), KanbanTransition(action: .promote))
        XCTAssertEqual(KanbanBoardRules.resolveTransition(from: .blocked, to: .ready), KanbanTransition(action: .unblock))
        XCTAssertEqual(KanbanBoardRules.resolveTransition(from: .scheduled, to: .todo), KanbanTransition(action: .unblock))
        XCTAssertEqual(KanbanBoardRules.resolveTransition(from: .review, to: .todo), KanbanTransition(action: .reopenReview))
        XCTAssertEqual(KanbanBoardRules.resolveTransition(from: .running, to: .scheduled), KanbanTransition(action: .schedule))
        XCTAssertEqual(KanbanBoardRules.resolveTransition(from: .ready, to: .blocked), KanbanTransition(action: .block, requiresReason: true))
        XCTAssertEqual(KanbanBoardRules.resolveTransition(from: .running, to: .review), KanbanTransition(action: .requestReview))
        XCTAssertEqual(KanbanBoardRules.resolveTransition(from: .blocked, to: .done), KanbanTransition(action: .complete))
        XCTAssertEqual(KanbanBoardRules.resolveTransition(from: .done, to: .archived), KanbanTransition(action: .archive, confirm: true))
    }

    func testNeverOffersTriageOrRunningAsDropTargetsAndRejectsUnsupportedSources() {
        for from in KanbanBoardRules.statuses {
            XCTAssertNil(KanbanBoardRules.resolveTransition(from: from, to: .triage), from.rawValue)
            XCTAssertNil(KanbanBoardRules.resolveTransition(from: from, to: .running), from.rawValue)
        }
        XCTAssertNil(KanbanBoardRules.resolveTransition(from: .todo, to: .blocked))
        XCTAssertNil(KanbanBoardRules.resolveTransition(from: .todo, to: .done))
        XCTAssertNil(KanbanBoardRules.resolveTransition(from: .done, to: .ready))
        XCTAssertNil(KanbanBoardRules.resolveTransition(from: .archived, to: .done))
        XCTAssertNil(KanbanBoardRules.resolveTransition(from: .triage, to: .todo))
    }

    func testSameColumnMovesAreAllowedReorders() {
        XCTAssertTrue(KanbanBoardRules.isDropTarget(from: .todo, to: .todo))
        XCTAssertTrue(KanbanBoardRules.isDropTarget(from: .todo, to: .ready))
        XCTAssertFalse(KanbanBoardRules.isDropTarget(from: .todo, to: .running))
    }

    // MARK: - Columns

    func testEveryStatusMapsToTheInboxAColumnOrTheArchiveUnderDone() {
        XCTAssertEqual(KanbanBoardRules.columns.map(\.id), [.queue, .waiting, .review, .done])
        XCTAssertEqual(KanbanStatus.allCases.map(\.rawValue), ["triage", "todo", "scheduled", "ready", "running", "blocked", "review", "done", "archived"])
        XCTAssertNil(KanbanBoardRules.columnID(for: .triage))
        XCTAssertEqual(KanbanBoardRules.columnID(for: .todo), .queue)
        XCTAssertEqual(KanbanBoardRules.columnID(for: .ready), .queue)
        XCTAssertEqual(KanbanBoardRules.columnID(for: .running), .queue)
        XCTAssertEqual(KanbanBoardRules.columnID(for: .scheduled), .waiting)
        XCTAssertEqual(KanbanBoardRules.columnID(for: .blocked), .waiting)
        XCTAssertEqual(KanbanBoardRules.columnID(for: .review), .review)
        XCTAssertEqual(KanbanBoardRules.columnID(for: .done), .done)
        XCTAssertEqual(KanbanBoardRules.columnID(for: .archived), .done)
        let covered = Set(KanbanBoardRules.columns.flatMap(\.statuses)).map(\.rawValue).sorted()
        XCTAssertEqual(covered, ["blocked", "done", "ready", "review", "running", "scheduled", "todo"])
        XCTAssertEqual(KanbanColumnID.allCases.filter(\.collapsible), [.waiting])
    }

    func testDerivesDropOptionsPerColumnAndTreatsSameColumnMovesAsReorders() {
        XCTAssertEqual(actions(.todo, .queue), [])
        XCTAssertEqual(actions(.ready, .queue), [])
        XCTAssertEqual(actions(.blocked, .queue), ["unblock->todo"])
        XCTAssertEqual(actions(.review, .queue), ["reopenReview->todo"])
        XCTAssertEqual(actions(.running, .queue), [])
        XCTAssertEqual(actions(.running, .review), ["requestReview->review"])
        XCTAssertEqual(actions(.ready, .waiting), ["schedule->scheduled", "block->blocked"])
        XCTAssertEqual(actions(.todo, .waiting), ["schedule->scheduled"])
        XCTAssertEqual(actions(.todo, .review), [])
        XCTAssertEqual(actions(.blocked, .done), ["complete->done"])
        XCTAssertEqual(actions(.done, .queue), [])
        XCTAssertTrue(KanbanBoardRules.isColumnDropTarget(from: .scheduled, column: KanbanBoardRules.column(.waiting)))
        XCTAssertFalse(KanbanBoardRules.isColumnDropTarget(from: .todo, column: KanbanBoardRules.column(.review)))
        XCTAssertTrue(KanbanBoardRules.isColumnDropTarget(from: .ready, column: KanbanBoardRules.column(.review)))
    }

    func testTheStatusFilterNarrowsTheBoardToOneColumn() {
        XCTAssertEqual(KanbanBoardRules.visibleColumns(filter: nil).map(\.id), [.queue, .waiting, .review, .done])
        XCTAssertEqual(KanbanBoardRules.visibleColumns(filter: .blocked).map(\.id), [.waiting])
        XCTAssertEqual(KanbanBoardRules.visibleColumns(filter: .archived).map(\.id), [.done])
        XCTAssertEqual(KanbanBoardRules.visibleColumns(filter: .triage).map(\.id), [])
        XCTAssertTrue(KanbanBoardRules.inboxVisible(filter: nil))
        XCTAssertTrue(KanbanBoardRules.inboxVisible(filter: .triage))
        XCTAssertFalse(KanbanBoardRules.inboxVisible(filter: .done))
    }

    func testACollapsibleColumnOnlyTakesASlotWhenItHasWork() {
        XCTAssertTrue(KanbanBoardRules.isCollapsed(collapsible: true, cardCount: 0, dropOpen: false, expandedByUser: false))
        XCTAssertFalse(KanbanBoardRules.isCollapsed(collapsible: true, cardCount: 1, dropOpen: false, expandedByUser: false))
        XCTAssertFalse(KanbanBoardRules.isCollapsed(collapsible: true, cardCount: 0, dropOpen: true, expandedByUser: false))
        XCTAssertFalse(KanbanBoardRules.isCollapsed(collapsible: true, cardCount: 0, dropOpen: false, expandedByUser: true))
        XCTAssertFalse(KanbanBoardRules.isCollapsed(collapsible: false, cardCount: 0, dropOpen: false, expandedByUser: false))
    }

    // MARK: - Device-local layout

    func testParsesStoredLayoutsDefensivelyAndDropsTheRetiredColumnOrder() {
        XCTAssertEqual(KanbanBoardLayout.parse(nil), KanbanBoardLayout())
        XCTAssertEqual(KanbanBoardLayout.parse("not json"), KanbanBoardLayout())
        let legacy = #"{"columns":["done","todo"],"cards":{"queue":["a","",3,"b"],"todo":["legacy"],"nope":["x"],"review":[]}}"#
        XCTAssertEqual(KanbanBoardLayout.parse(legacy), KanbanBoardLayout(cards: [.queue: ["a", "b"]]))
        XCTAssertFalse(KanbanBoardLayout().hasCustomLayout)
        XCTAssertTrue(KanbanBoardLayout(cards: [.queue: ["a"]]).hasCustomLayout)
        XCTAssertEqual(KanbanBoardLayout.storageKey(board: "project-a"), "hermes.kanban.layout.project-a")
        XCTAssertEqual(KanbanBoardLayout.parse(KanbanBoardLayout(cards: [.done: ["x", "y"]]).encoded()), KanbanBoardLayout(cards: [.done: ["x", "y"]]))
    }

    func testTheLayoutStoreRoundTripsThroughUserDefaults() {
        let defaults = UserDefaults(suiteName: "KanbanBoardTests-\(UUID().uuidString)")!
        let store = KanbanLayoutStore(defaults: defaults)
        XCTAssertFalse(store.layout(board: "default").hasCustomLayout)
        store.setCardOrder(["b", "a"], column: .queue, board: "default")
        XCTAssertEqual(store.layout(board: "default").cards[.queue], ["b", "a"])
        XCTAssertNotNil(defaults.string(forKey: "hermes.kanban.layout.default"))
        XCTAssertFalse(store.layout(board: "other").hasCustomLayout, "layouts are per board")
        store.reset(board: "default")
        XCTAssertNil(defaults.string(forKey: "hermes.kanban.layout.default"))
    }

    func testKeepsManualCardOrderAndShowsUnsavedArrivalsFirstNewestOnTop() {
        let tasks = [task("a", .todo, createdAt: 1), task("b", .todo, createdAt: 2), task("c", .todo, createdAt: 3)]
        XCTAssertEqual(orderKanbanCards(tasks, savedIDs: nil).map(\.id), ["c", "b", "a"])
        XCTAssertEqual(orderKanbanCards(tasks, savedIDs: ["a", "c", "b"]).map(\.id), ["a", "c", "b"])
        XCTAssertEqual(orderKanbanCards(tasks, savedIDs: ["a", "missing", "b"]).map(\.id), ["c", "a", "b"])
        let more = tasks + [task("d", .todo, createdAt: 4), task("e", .todo, createdAt: 0)]
        XCTAssertEqual(orderKanbanCards(more, savedIDs: ["b", "a"]).map(\.id), ["d", "c", "e", "b", "a"])
    }

    func testTheSnapshotFoldsTheInboxAndTheArchiveAndOrdersEachColumn() {
        let tasks = [
            task("t1", .triage, createdAt: 5), task("q1", .todo, createdAt: 1), task("q2", .ready, createdAt: 2), task("q3", .running, createdAt: 3),
            task("w1", .blocked, createdAt: 4), task("r1", .review), task("d1", .done, createdAt: 1), task("z1", .archived, createdAt: 9),
        ]
        let snapshot = KanbanBoardSnapshot(tasks: tasks, layout: KanbanBoardLayout(cards: [.queue: ["q1", "q2"]]))
        XCTAssertEqual(snapshot.inbox.map(\.id), ["t1"])
        XCTAssertEqual(snapshot.archived.map(\.id), ["z1"])
        XCTAssertEqual(snapshot.cards(in: .queue).map(\.id), ["q3", "q1", "q2"], "the unsaved arrival first, then the manual order")
        XCTAssertEqual(snapshot.cards(in: .waiting).map(\.id), ["w1"])
        XCTAssertEqual(snapshot.cards(in: .done).map(\.id), ["d1"], "archived cards are not done cards")
        XCTAssertEqual(snapshot.count(of: .archived), 1)
        XCTAssertEqual(snapshot.count(of: .ready), 1)
        XCTAssertEqual(snapshot.count(of: .triage), 1)
    }

    // MARK: - Routes (the web store's mapping)

    func testEveryTransitionPostsToTheRouteTheWebStoreUses() {
        func make(_ action: KanbanTransitionAction, note: String? = nil) -> KanbanTransitionRequest {
            KanbanTransitionRequest.make(action, taskID: "TASK-7", note: note)
        }
        XCTAssertEqual(make(.promote).path, "/api/hermes/kanban/TASK-7/promote")
        XCTAssertEqual(make(.schedule).path, "/api/hermes/kanban/TASK-7/schedule")
        XCTAssertEqual(make(.block, note: "waiting on keys").path, "/api/hermes/kanban/TASK-7/block")
        XCTAssertEqual(make(.requestReview).path, "/api/hermes/kanban/TASK-7/request-review")
        XCTAssertEqual(make(.reopenReview).path, "/api/hermes/kanban/TASK-7/reopen-review")
        XCTAssertEqual(make(.unblock).path, "/api/hermes/kanban/unblock")
        XCTAssertEqual(make(.complete).path, "/api/hermes/kanban/complete")
        XCTAssertEqual(make(.archive).path, "/api/hermes/kanban/tasks/bulk")

        XCTAssertEqual(make(.block, note: " waiting on keys ").body["reason"] as? String, "waiting on keys")
        XCTAssertEqual(make(.unblock).body["task_ids"] as? [String], ["TASK-7"])
        XCTAssertEqual(make(.complete).body["task_ids"] as? [String], ["TASK-7"])
        XCTAssertNil(make(.complete).body["summary"], "an absent note is omitted, like JSON.stringify dropping undefined")
        XCTAssertEqual(make(.complete, note: "shipped").body["summary"] as? String, "shipped")
        XCTAssertEqual(make(.requestReview, note: "ready for eyes").body["summary"] as? String, "ready for eyes")
        XCTAssertNil(make(.promote).body["reason"])
        XCTAssertEqual(make(.reopenReview, note: "needs work").body["reason"] as? String, "needs work")
        XCTAssertEqual(make(.archive).body["ids"] as? [String], ["TASK-7"])
        XCTAssertEqual(make(.archive).body["archive"] as? Bool, true)
        XCTAssertEqual(KanbanTransitionRequest.make(.promote, taskID: "a b/c", note: nil).path, "/api/hermes/kanban/a%20b%2Fc/promote")

        XCTAssertEqual(KanbanBoardRules.transitionTarget[.unblock], .ready)
        XCTAssertEqual(KanbanBoardRules.transitionTarget[.reopenReview], .todo)
        XCTAssertEqual(KanbanBoardRules.transitionTarget.count, KanbanTransitionAction.allCases.count)
    }

    // MARK: - Drag geometry

    func testDragGeometryUsesPhysicalFramesSoRTLNeedsNothingSpecial() {
        // Frames as an RTL board lays them out: done on the left, queue on the right.
        let frames: [KanbanColumnID: CGRect] = [
            .done: CGRect(x: 0, y: 0, width: 100, height: 400),
            .review: CGRect(x: 110, y: 0, width: 100, height: 400),
            .waiting: CGRect(x: 220, y: 0, width: 44, height: 400),
            .queue: CGRect(x: 274, y: 0, width: 100, height: 400),
        ]
        XCTAssertEqual(KanbanDragGeometry.column(at: CGPoint(x: 50, y: 10), frames: frames), .done)
        XCTAssertEqual(KanbanDragGeometry.column(at: CGPoint(x: 230, y: 10), frames: frames), .waiting)
        XCTAssertNil(KanbanDragGeometry.column(at: CGPoint(x: 105, y: 10), frames: frames), "the gap belongs to no column")
        XCTAssertEqual(KanbanDragGeometry.nearest(x: 105, frames: frames), .done)
        XCTAssertEqual(KanbanDragGeometry.neighbour(of: .review, frames: frames, side: .left), .done)
        XCTAssertEqual(KanbanDragGeometry.neighbour(of: .review, frames: frames, side: .right), .waiting)
        XCTAssertNil(KanbanDragGeometry.neighbour(of: .queue, frames: frames, side: .right))
        XCTAssertNil(KanbanDragGeometry.neighbour(of: .done, frames: frames, side: .left))
        XCTAssertEqual(KanbanDragGeometry.edge(x: 10, width: 390, margin: 40), .left)
        XCTAssertEqual(KanbanDragGeometry.edge(x: 380, width: 390, margin: 40), .right)
        XCTAssertNil(KanbanDragGeometry.edge(x: 200, width: 390, margin: 40))
        XCTAssertNil(KanbanDragGeometry.edge(x: 10, width: 0, margin: 40))

        let cards = [CGRect(x: 0, y: 0, width: 100, height: 60), CGRect(x: 0, y: 70, width: 100, height: 60), CGRect(x: 0, y: 140, width: 100, height: 60)]
        XCTAssertEqual(KanbanDragGeometry.insertionIndex(y: 10, cardFrames: cards), 0)
        XCTAssertEqual(KanbanDragGeometry.insertionIndex(y: 105, cardFrames: cards), 2)
        XCTAssertEqual(KanbanDragGeometry.insertionIndex(y: 500, cardFrames: cards), 3)
        XCTAssertEqual(KanbanDragGeometry.reordered(["a", "b", "c"], moving: "c", to: 0), ["c", "a", "b"])
        XCTAssertEqual(KanbanDragGeometry.reordered(["a", "b", "c"], moving: "a", to: 9), ["b", "c", "a"])
        XCTAssertEqual(KanbanDragGeometry.reordered(["a", "b", "c"], moving: "b", to: 1), ["a", "b", "c"])
    }

    // MARK: - Card stage

    func testTheCardNamesItsStageBecauseColumnsGroupStatuses() {
        XCTAssertNil(KanbanCardStage.badge(for: .todo))
        XCTAssertNil(KanbanCardStage.badge(for: .triage))
        for status in [KanbanStatus.ready, .running, .scheduled, .blocked, .review, .done, .archived] {
            XCTAssertEqual(KanbanCardStage.badge(for: status), status.title)
        }
        XCTAssertTrue(KanbanCardStage.showsRunningRing(.running))
        XCTAssertFalse(KanbanCardStage.showsRunningRing(.ready))
        XCTAssertFalse(KanbanCardStage.showsPriority(level: 1))
        XCTAssertTrue(KanbanCardStage.showsPriority(level: 2))
        XCTAssertEqual(KanbanCardStage.priorityLabel(level: 3), String(localized: "High"))
    }

    // MARK: - The server's responses

    func testDecodesTheServersTaskListWithItsJSONNulls() throws {
        // `GET /api/hermes/kanban?board=default&includeArchived=true` → `{ tasks }` (controllers/kanban.ts `list`,
        // fields per `kanban-service.ts` `KanbanTask`).
        let raw = #"""
        {"tasks":[
          {"id":"TASK-7","title":"Ship the board","body":null,"assignee":null,"status":"running","priority":3,"created_by":"twuijri","created_at":1758300000,"started_at":1758300100,"completed_at":null,"workspace_kind":"local","workspace_path":null,"tenant":null,"result":null,"skills":null,"goal_mode":false},
          {"id":"TASK-8","title":"","body":"Old one","assignee":"main","status":"archived","priority":1,"created_by":null,"created_at":1758200000,"started_at":null,"completed_at":1758250000,"workspace_kind":"local","workspace_path":"/w","tenant":null,"result":"done","skills":["docs","swift"]}
        ]}
        """#
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(raw.utf8)) as? JSON)
        let tasks = json.objects("tasks").map(KanbanTask.init)
        XCTAssertEqual(tasks.count, 2)
        let running = tasks[0]
        XCTAssertEqual(running.id, "TASK-7")
        XCTAssertEqual(running.title, "Ship the board")
        XCTAssertEqual(running.description, "", "a JSON null body is not the text \"<null>\"")
        XCTAssertNil(running.assignee)
        XCTAssertEqual(running.stage, .running)
        XCTAssertEqual(running.priorityLevel, 3)
        XCTAssertEqual(running.priority, "high")
        XCTAssertEqual(running.createdAt, 1_758_300_000)
        XCTAssertEqual(running.tags, [])
        let archived = tasks[1]
        XCTAssertEqual(archived.title, String(localized: "Untitled task"))
        XCTAssertEqual(archived.description, "Old one")
        XCTAssertEqual(archived.assignee, "main")
        XCTAssertEqual(archived.stage, .archived)
        XCTAssertEqual(archived.tags, ["docs", "swift"])
        let snapshot = KanbanBoardSnapshot(tasks: tasks)
        XCTAssertEqual(snapshot.cards(in: .queue).map(\.id), ["TASK-7"])
        XCTAssertEqual(snapshot.archived.map(\.id), ["TASK-8"])
    }

    func testDecodesTheServersBoardList() throws {
        // `GET /api/hermes/kanban/boards` → `{ boards }` with `slug`, not `id`.
        let raw = #"{"boards":[{"slug":"default","name":"Default","description":"","icon":"","color":"","created_at":null,"archived":false,"counts":{"todo":2},"total":2},{"slug":"mobile","name":"","description":"","icon":"","color":"","created_at":1758000000,"archived":false,"counts":{},"total":0}]}"#
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(raw.utf8)) as? JSON)
        let boards = json.objects("boards").map(KanbanBoard.init)
        XCTAssertEqual(boards.map(\.id), ["default", "mobile"])
        XCTAssertEqual(boards.map(\.name), ["Default", "mobile"], "an empty name falls back to the slug")
    }

    func testStatusAndPriorityParsingIsTolerantButNotInventive() {
        XCTAssertEqual(KanbanStatus(raw: " Running "), .running)
        XCTAssertEqual(KanbanStatus(raw: "TO-DO"), .todo)
        XCTAssertNil(KanbanStatus(raw: "in_progress"))
        XCTAssertNil(KanbanStatus(raw: ""))
        XCTAssertEqual(KanbanTask(["id": "x", "status": "in_progress"]).stage, .todo, "an unknown status is still shown, in the queue")
        XCTAssertEqual(KanbanTask.priorityLevel(NSNumber(value: 2)), 2)
        XCTAssertEqual(KanbanTask.priorityLevel("high"), 3)
        XCTAssertEqual(KanbanTask.priorityLevel("1"), 1)
        XCTAssertEqual(KanbanTask.priorityLevel(nil), 2)
        XCTAssertEqual(KanbanTask.priorityLevel(NSNull()), 2)
        XCTAssertEqual(KanbanStatus.pickable.map(\.rawValue), ["triage", "todo", "scheduled", "ready", "running", "blocked", "review", "done"])
    }

    // MARK: - Wording

    func testColumnAndStatusWordingMatchesTheWebInBothLocales() throws {
        let english = try LocalizableStrings.load(locale: "en")
        let arabic = try LocalizableStrings.load(locale: "ar")
        let columns: [(String, String, String)] = [
            ("inbox", "Inbox", "الوارد"), ("queue", "Queue", "الطابور"), ("waiting", "Waiting", "بانتظار"), ("review", "Review", "المراجعة"), ("done", "Done", "منتهية"),
        ]
        for (key, en, ar) in columns {
            XCTAssertEqual(english["kanban_column_" + key], en, key)
            XCTAssertEqual(arabic["kanban_column_" + key], ar, key)
        }
        let statuses: [(String, String, String)] = [
            ("triage", "Triage", "الفرز"), ("todo", "To Do", "للتنفيذ"), ("scheduled", "Scheduled", "مجدولة"), ("ready", "Ready", "جاهزة"),
            ("running", "Running", "قيد التشغيل"), ("blocked", "Blocked", "محجوبة"), ("review", "Review", "المراجعة"), ("done", "Done", "منتهية"), ("archived", "Archived", "مؤرشفة"),
        ]
        for (key, en, ar) in statuses {
            XCTAssertEqual(english["kanban_status_" + key], en, key)
            XCTAssertEqual(arabic["kanban_status_" + key], ar, key)
        }
        for status in KanbanStatus.allCases {
            XCTAssertNotNil(english["kanban_status_" + status.rawValue], status.rawValue)
        }
        for key in ["Show archived (%lld)", "Hide archived (%lld)", "Cannot move here", "How should this task wait?", "Park until later (scheduled)",
                    "Block until someone acts (needs a reason)", "Reason for blocking", "Archive this completed task?", "No tasks", "Loading tasks…",
                    "The board could not be loaded", "Retry", "Touch and hold to move", "Task promoted to ready", "Review reopened"] {
            XCTAssertNotNil(english[key], key)
            XCTAssertNotNil(arabic[key], key)
        }
        // The section-per-status screen's own strings left with it.
        for key in ["Drop tasks here", "Loading board…", "To do"] {
            XCTAssertNil(english[key], key)
            XCTAssertNil(arabic[key], key)
        }
    }
}
