import SwiftUI

private typealias Palette = CoreHubTokens.Palette
private typealias Typography = CoreHubTokens.Typography

/// One workflow column (web `KanbanColumn.vue`): a header with the count, the
/// cards in a vertical list, a loading or empty state so the column is never
/// blank, the blocked hint while a card that cannot land here is dragged, the
/// archive fold under `done`, and the collapsed strip for an empty `waiting`.
struct KanbanColumnView: View {
    @ObservedObject var board: KanbanBoardController
    let column: KanbanColumn
    let width: CGFloat
    let onOpen: (KanbanTask) -> Void
    @State private var expandedByUser = false
    @State private var showArchived = false

    static let stripWidth: CGFloat = 44

    private var cards: [KanbanTask] { board.snapshot.cards(in: column.id) }
    private var accent: Color { KanbanColumnStyle.color(column.id) }

    private var dropBlocked: Bool {
        guard let from = board.draggingStatus else { return false }
        return !KanbanBoardRules.isColumnDropTarget(from: from, column: column)
    }

    private var dropOpen: Bool {
        guard let from = board.draggingStatus else { return false }
        return !column.statuses.contains(from) && !dropBlocked
    }

    private var hovered: Bool { board.drag != nil && board.drag?.target == column.id }

    private var collapsed: Bool {
        KanbanBoardRules.isCollapsed(collapsible: column.id.collapsible, cardCount: cards.count, dropOpen: dropOpen, expandedByUser: expandedByUser)
    }

    var body: some View {
        Group {
            if collapsed {
                KanbanCollapsedStrip(column: column, count: cards.count, color: accent) { expandedByUser = true }
            } else {
                expanded
            }
        }
        .frame(width: collapsed ? Self.stripWidth : width)
        .frame(maxHeight: .infinity)
        .background(GeometryReader { proxy in
            Color.clear.preference(key: KanbanColumnFramesKey.self, value: [column.id: proxy.frame(in: .named(KanbanBoardSpace.name))])
        })
        .opacity(dropBlocked ? 0.5 : 1)
        .animation(.easeInOut(duration: CoreHubTokens.Motion.normal), value: collapsed)
        .onChange(of: board.filter) { _, filter in
            if filter == KanbanBoardRules.archivedStatus { showArchived = true }
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel(column.id.title)
    }

    private var expanded: some View {
        VStack(spacing: 0) {
            header
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 8) {
                    ForEach(cards) { task in
                        KanbanDraggableCard(board: board, task: task) { onOpen(task) }
                    }
                    if cards.isEmpty {
                        emptyState
                    } else if dropBlocked {
                        hint(String(localized: "Cannot move here"))
                    }
                    if column.id == .done { archiveSection }
                }
                .padding(10)
            }
        }
        .background(Palette.bgSecondary, in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.card))
        .overlay(RoundedRectangle(cornerRadius: CoreHubTokens.Radius.card).stroke(borderColor, lineWidth: hovered && dropOpen ? 2 : 1))
    }

    private var borderColor: Color {
        dropOpen ? accent.opacity(hovered ? 1 : 0.55) : Palette.borderLight
    }

    private var header: some View {
        HStack(spacing: 8) {
            Circle().fill(accent).frame(width: 8, height: 8)
            Text(column.id.title)
                .font(Typography.font(Typography.sidebarTab, weight: .semibold))
                .foregroundStyle(Palette.textPrimary)
            Spacer()
            Text("\(cards.count)")
                .font(Typography.font(Typography.meta, weight: .semibold))
                .foregroundStyle(Palette.textSecondary)
                .padding(.horizontal, 7)
                .frame(height: 20)
                .background(Palette.bgCard, in: Capsule())
            if column.id.collapsible {
                Image(systemName: "chevron.up")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(Palette.textMuted)
            }
        }
        .padding(.horizontal, 12)
        .frame(height: 40)
        .contentShape(Rectangle())
        .onTapGesture {
            if column.id.collapsible { expandedByUser.toggle() }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 6) {
            if board.isInitialLoading {
                ProgressView().controlSize(.small)
                Text("Loading tasks…")
            } else if dropBlocked {
                Text("Cannot move here")
            } else {
                Image(systemName: "tray").font(.system(size: 20))
                Text("No tasks")
            }
        }
        .font(Typography.metaFont)
        .foregroundStyle(Palette.textMuted)
        .frame(maxWidth: .infinity)
        .frame(minHeight: 96)
    }

    private func hint(_ text: String) -> some View {
        Text(text)
            .font(Typography.metaFont)
            .foregroundStyle(Palette.textMuted)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 6)
    }

    /// `archived` lives inside `done` behind a toggle (web `archive-section`).
    private var archiveSection: some View {
        let archived = board.snapshot.archived
        let count = archived.count
        return VStack(spacing: 8) {
            Button {
                withAnimation(.easeInOut(duration: CoreHubTokens.Motion.fast)) { showArchived.toggle() }
            } label: {
                Text(showArchived ? String(localized: "Hide archived (\(count))") : String(localized: "Show archived (\(count))"))
                    .font(Typography.font(Typography.meta, weight: .medium))
                    .foregroundStyle(Palette.textSecondary)
                    .frame(maxWidth: .infinity)
                    .frame(height: 32)
                    .background(Palette.bgCard, in: Capsule())
            }
            .buttonStyle(.plain)
            if showArchived {
                if archived.isEmpty { hint(String(localized: "No tasks")) }
                ForEach(archived) { task in
                    KanbanCardView(task: task, muted: true)
                        .contentShape(Rectangle())
                        .onTapGesture { onOpen(task) }
                }
            }
        }
        .padding(.top, 4)
    }
}

/// An empty collapsible column: a narrow strip with the title turned on its
/// side; tap or drag over it to open.
struct KanbanCollapsedStrip: View {
    let column: KanbanColumn
    let count: Int
    let color: Color
    let expand: () -> Void

    var body: some View {
        Button(action: expand) {
            VStack(spacing: 12) {
                Circle().fill(color).frame(width: 8, height: 8)
                Text(column.id.title)
                    .font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.sidebarTab, weight: .semibold))
                    .foregroundStyle(CoreHubTokens.Palette.textSecondary)
                    .fixedSize()
                    .rotationEffect(.degrees(90))
                    .frame(width: 20, height: 110)
                Text("\(count)")
                    .font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.meta, weight: .semibold))
                    .foregroundStyle(CoreHubTokens.Palette.textMuted)
                Spacer(minLength: 0)
            }
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .background(CoreHubTokens.Palette.bgSecondary, in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.card))
        .overlay(RoundedRectangle(cornerRadius: CoreHubTokens.Radius.card).stroke(CoreHubTokens.Palette.borderLight, lineWidth: 1))
        .accessibilityLabel(column.id.title)
        .accessibilityValue("\(count)")
    }
}

/// The desktop column and status colours (`KanbanColumn.vue`, `KanbanTaskCard.vue`).
enum KanbanColumnStyle {
    static func color(_ column: KanbanColumnID) -> Color {
        switch column {
        case .queue: return Color(kanbanHex: 0xA66D23)
        case .waiting: return Color(kanbanHex: 0xB8860B)
        case .review: return Color(kanbanHex: 0x7B5FB3)
        case .done: return CoreHubTokens.Palette.success
        }
    }

    static func color(_ status: KanbanStatus) -> Color {
        switch status {
        case .triage: return Color(kanbanHex: 0x8B8F95)
        case .todo: return Color(kanbanHex: 0x6F7782)
        case .scheduled: return Color(kanbanHex: 0xB8860B)
        case .ready: return Color(kanbanHex: 0xA66D23)
        case .running, .done: return CoreHubTokens.Palette.success
        case .blocked: return CoreHubTokens.Palette.error
        case .review: return Color(kanbanHex: 0x7B5FB3)
        case .archived: return Color(kanbanHex: 0x777B81)
        }
    }
}

extension Color {
    init(kanbanHex hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}
