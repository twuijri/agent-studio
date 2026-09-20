import SwiftUI

private typealias Palette = CoreHubTokens.Palette
private typealias Typography = CoreHubTokens.Typography

/// The desktop stats bar: total plus one chip per Hermes status; tapping a
/// chip narrows the board to the column that shows that status.
struct KanbanStatsBar: View {
    @ObservedObject var board: KanbanBoardController

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                chip(title: String(localized: "Total"), count: board.total, status: nil)
                ForEach(KanbanBoardRules.statuses) { status in
                    chip(title: status.title, count: board.count(of: status), status: status)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
        }
    }

    private func chip(title: String, count: Int, status: KanbanStatus?) -> some View {
        let active = board.filter == status
        return Button { board.toggleFilter(status) } label: {
            HStack(spacing: 5) {
                Text("\(count)").font(Typography.font(Typography.meta, weight: .semibold))
                Text(title).font(Typography.metaFont)
            }
            .padding(.horizontal, 10)
            .frame(height: 28)
            .foregroundStyle(active ? Palette.textOnAccent : Palette.textSecondary)
            .background(active ? Palette.accent : Palette.bgCard, in: Capsule())
            .overlay(Capsule().stroke(active ? Color.clear : Palette.borderLight, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(active ? AccessibilityTraits.isSelected : AccessibilityTraits())
    }
}

/// The intake strip above the columns: closed it is one row with the count;
/// open it lists the triage cards. Triage cards are never draggable (nothing
/// accepts them); a card is opened and specified from its detail.
struct KanbanInboxStrip: View {
    @ObservedObject var board: KanbanBoardController
    let onOpen: (KanbanTask) -> Void
    @State private var open = false

    var body: some View {
        VStack(spacing: 0) {
            Button {
                withAnimation(.easeInOut(duration: CoreHubTokens.Motion.fast)) { open.toggle() }
            } label: { header }
            .buttonStyle(.plain)
            .accessibilityAddTraits(.isButton)
            if open { list }
        }
        .background(Palette.bgSecondary, in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.card))
        .overlay(RoundedRectangle(cornerRadius: CoreHubTokens.Radius.card).stroke(Palette.borderLight, lineWidth: 1))
        .padding(.horizontal, 12)
        .onChange(of: board.filter) { _, filter in
            if filter == KanbanBoardRules.inboxStatus { open = true }
        }
    }

    private var header: some View {
        HStack(spacing: 8) {
            Text("\(board.snapshot.inbox.count)")
                .font(Typography.font(Typography.meta, weight: .semibold))
                .foregroundStyle(Palette.textSecondary)
                .padding(.horizontal, 7)
                .frame(height: 20)
                .background(Palette.bgCard, in: Capsule())
            Text(KanbanBoardRules.inboxTitle)
                .font(Typography.font(Typography.sidebarTab, weight: .semibold))
                .foregroundStyle(Palette.textPrimary)
            Spacer()
            Image(systemName: open ? "chevron.up" : "chevron.down")
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(Palette.textMuted)
        }
        .padding(.horizontal, 12)
        .frame(height: 40)
        .contentShape(Rectangle())
    }

    private var list: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Tasks waiting to be specified. Open a card and use Specify to move it into the queue.")
                .font(Typography.metaFont)
                .foregroundStyle(Palette.textMuted)
            if board.snapshot.inbox.isEmpty {
                Text(board.isInitialLoading ? String(localized: "Loading tasks…") : String(localized: "No tasks"))
                    .font(Typography.metaFont)
                    .foregroundStyle(Palette.textMuted)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
            } else {
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(spacing: 8) {
                        ForEach(board.snapshot.inbox) { task in
                            KanbanCardView(task: task)
                                .contentShape(Rectangle())
                                .onTapGesture { onOpen(task) }
                        }
                    }
                }
                .frame(maxHeight: 260)
            }
        }
        .padding(.horizontal, 10)
        .padding(.bottom, 10)
    }
}
