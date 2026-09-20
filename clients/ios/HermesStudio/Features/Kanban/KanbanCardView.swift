import SwiftUI

private typealias Palette = CoreHubTokens.Palette
private typealias Typography = CoreHubTokens.Typography

/// The task card (web `KanbanTaskCard.vue`). Columns group several statuses,
/// so the card names its stage: a badge for ready/scheduled/blocked/review/
/// done/archived, a green ring while running, the priority when it matters.
struct KanbanCardView: View {
    struct QuickAction {
        let label: String
        let run: () -> Void
    }

    let task: KanbanTask
    var pending = false
    var muted = false
    var quickAction: QuickAction? = nil

    private var stage: KanbanStatus { task.stage }
    private var color: Color { KanbanColumnStyle.color(stage) }
    private var running: Bool { KanbanCardStage.showsRunningRing(stage) }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            heading
            DirectionalText(text: task.title, font: Typography.font(Typography.sessionTitle, weight: .semibold), lineLimit: 3)
            footer
        }
        .padding(12)
        .background(Palette.bgCard, in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.bubble))
        .overlay(RoundedRectangle(cornerRadius: CoreHubTokens.Radius.bubble).stroke(running ? Palette.success : Palette.borderLight, lineWidth: running ? 2 : 1))
        .opacity(muted ? 0.6 : 1)
        .accessibilityElement(children: .combine)
    }

    private var heading: some View {
        HStack(spacing: 6) {
            Circle().fill(color).frame(width: 7, height: 7)
            TechnicalText(text: task.id)
            if let badge = KanbanCardStage.badge(for: stage) { badgeView(badge) }
            if pending { pendingBadge }
            Spacer(minLength: 0)
            if KanbanCardStage.showsPriority(level: task.priorityLevel) { priority }
        }
    }

    private func badgeView(_ text: String) -> some View {
        HStack(spacing: 3) {
            Image(systemName: stageIcon).font(.system(size: 9, weight: .bold))
            Text(text)
        }
        .font(Typography.font(Typography.categoryTag, weight: .semibold))
        .foregroundStyle(color)
        .padding(.horizontal, 6)
        .frame(height: 18)
        .background(color.opacity(0.13), in: Capsule())
        .lineLimit(1)
    }

    private var stageIcon: String {
        switch stage {
        case .scheduled: return "clock"
        case .blocked: return "pause.fill"
        case .review: return "eye"
        case .running: return "play.fill"
        case .done, .archived: return "checkmark"
        case .triage, .todo, .ready: return "circle.fill"
        }
    }

    private var pendingBadge: some View {
        HStack(spacing: 4) {
            ProgressView().controlSize(.mini)
            Text("Saving…")
        }
        .font(Typography.font(Typography.categoryTag, weight: .medium))
        .foregroundStyle(Palette.textMuted)
    }

    private var priority: some View {
        let high = task.priorityLevel >= 3
        return HStack(spacing: 4) {
            Circle().fill(high ? Palette.error : Palette.warning).frame(width: 6, height: 6)
            Text(KanbanCardStage.priorityLabel(level: task.priorityLevel))
        }
        .font(Typography.font(Typography.categoryTag, weight: .medium))
        .foregroundStyle(Palette.textSecondary)
    }

    private var footer: some View {
        HStack(spacing: 6) {
            if let assignee = task.assignee {
                Label(assignee, systemImage: "person.crop.circle")
                    .font(Typography.metaFont)
                    .foregroundStyle(Palette.textSecondary)
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
            if let quickAction {
                Button(quickAction.label, action: quickAction.run)
                    .font(Typography.font(Typography.meta, weight: .medium))
                    .buttonStyle(.borderless)
            }
            if task.createdAt > 0 {
                Text(Date(timeIntervalSince1970: task.createdAt), style: .relative)
                    .font(Typography.metaFont)
                    .foregroundStyle(Palette.textMuted)
                    .lineLimit(1)
            }
        }
    }
}

/// A column card: tap opens the task; touch and hold picks it up, then the
/// finger moves it (a `LongPressGesture` sequenced before a `DragGesture` so
/// the paging scroll view keeps plain swipes). The card reports its frame in
/// the board space for the ghost and for same-column reorders. A card whose
/// Hermes command is still running is not draggable.
struct KanbanDraggableCard: View {
    @ObservedObject var board: KanbanBoardController
    let task: KanbanTask
    let onOpen: () -> Void
    @GestureState private var dragActive = false

    private var pending: Bool { board.isPending(task.id) }
    private var isSource: Bool { board.drag?.task.id == task.id }

    var body: some View {
        KanbanCardView(task: task, pending: pending, quickAction: quickAction)
            .opacity(isSource ? 0.25 : 1)
            .contentShape(Rectangle())
            .onTapGesture(perform: onOpen)
            .gesture(dragGesture, including: pending ? .subviews : .all)
            .background(frameReporter)
            .onChange(of: dragActive) { _, active in
                if !active { board.cancelDrag(taskID: task.id) }
            }
            .accessibilityAddTraits(.isButton)
            .accessibilityHint(Text("Touch and hold to move"))
    }

    private var frameReporter: some View {
        GeometryReader { proxy in
            Color.clear.preference(key: KanbanCardFramesKey.self, value: [task.id: proxy.frame(in: .named(KanbanBoardSpace.name))])
        }
    }

    private var quickAction: KanbanCardView.QuickAction? {
        guard !pending else { return nil }
        switch task.stage {
        case .todo: return KanbanCardView.QuickAction(label: String(localized: "Promote to Ready")) { board.promote(task.id) }
        case .done: return KanbanCardView.QuickAction(label: String(localized: "Archive")) { board.requestArchive(task.id) }
        default: return nil
        }
    }

    private var dragGesture: some Gesture {
        LongPressGesture(minimumDuration: 0.35)
            .sequenced(before: DragGesture(minimumDistance: 0, coordinateSpace: .named(KanbanBoardSpace.name)))
            .updating($dragActive) { value, state, _ in
                if case .second(true, _) = value { state = true }
            }
            .onChanged { value in
                guard case .second(true, let drag) = value else { return }
                if let drag {
                    board.updateDrag(taskID: task.id, location: drag.location, translation: drag.translation)
                } else {
                    board.beginDrag(task: task, frame: board.cardFrames[task.id] ?? .zero)
                }
            }
            .onEnded { value in
                if case .second(true, let drag?) = value {
                    board.finishDrag(taskID: task.id, location: drag.location)
                } else {
                    board.cancelDrag(taskID: task.id)
                }
            }
    }
}
