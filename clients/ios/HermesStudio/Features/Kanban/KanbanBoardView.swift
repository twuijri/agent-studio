import SwiftUI

private typealias Palette = CoreHubTokens.Palette
private typealias Typography = CoreHubTokens.Typography

/// The named coordinate space every drag position and frame is measured in.
enum KanbanBoardSpace { static let name = "kanban-board" }

struct KanbanColumnFramesKey: PreferenceKey {
    static var defaultValue: [KanbanColumnID: CGRect] = [:]
    static func reduce(value: inout [KanbanColumnID: CGRect], nextValue: () -> [KanbanColumnID: CGRect]) {
        value.merge(nextValue()) { $1 }
    }
}

struct KanbanCardFramesKey: PreferenceKey {
    static var defaultValue: [String: CGRect] = [:]
    static func reduce(value: inout [String: CGRect], nextValue: () -> [String: CGRect]) {
        value.merge(nextValue()) { $1 }
    }
}

/// The desktop board at phone width: the stats chips, the inbox strip, the
/// four workflow columns paged horizontally, the drag ghost above everything,
/// and the prompts a drop can open.
struct KanbanBoardView: View {
    @ObservedObject var board: KanbanBoardController
    let onOpen: (KanbanTask) -> Void

    var body: some View {
        VStack(spacing: 0) {
            KanbanStatsBar(board: board)
            if board.inboxVisible { KanbanInboxStrip(board: board, onOpen: onOpen) }
            content
        }
        .coordinateSpace(name: KanbanBoardSpace.name)
        .overlay { KanbanDragGhost(drag: board.drag) }
        // Newer SDKs declare the preference closure `@Sendable`; the values are
        // delivered on the main thread, so hop back onto the actor explicitly.
        .onPreferenceChange(KanbanColumnFramesKey.self) { frames in
            MainActor.assumeIsolated { board.columnFrames = frames }
        }
        .onPreferenceChange(KanbanCardFramesKey.self) { frames in
            MainActor.assumeIsolated { board.cardFrames = frames }
        }
        .sheet(item: sheetPrompt) { prompt in KanbanDropSheet(prompt: prompt, board: board) }
        .alert(String(localized: "Archive Task"), isPresented: archivePrompt, actions: archiveActions, message: { Text("Archive this completed task?") })
    }

    @ViewBuilder private var content: some View {
        if let failure = board.loadFailure, board.tasks.isEmpty {
            KanbanLoadFailure(message: failure) { Task { await board.load() } }
        } else {
            KanbanColumnsPager(board: board, onOpen: onOpen)
        }
    }

    /// Swiping the sheet away cancels the drop; programmatic dismissal never calls the setter.
    private var sheetPrompt: Binding<KanbanDropPrompt?> {
        Binding(
            get: { board.prompt?.needsSheet == true ? board.prompt : nil },
            set: { if $0 == nil { board.cancelPrompt() } }
        )
    }

    /// The alert's buttons decide; the binding setter stays inert so the
    /// dismissal cannot cancel the archive the user just confirmed.
    private var archivePrompt: Binding<Bool> {
        Binding(get: { board.prompt?.isArchiveConfirmation == true }, set: { _ in })
    }

    @ViewBuilder private func archiveActions() -> some View {
        Button(String(localized: "Archive"), role: .destructive) { board.confirmArchive() }
        Button(String(localized: "Cancel"), role: .cancel) { board.cancelPrompt() }
    }
}

/// The columns, horizontally paged; a collapsed column is a narrow strip.
/// Frames are physical, so RTL mirrors the order and the drag for free.
struct KanbanColumnsPager: View {
    @ObservedObject var board: KanbanBoardController
    let onOpen: (KanbanTask) -> Void

    var body: some View {
        GeometryReader { proxy in
            ScrollViewReader { scroller in
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(alignment: .top, spacing: 10) {
                        ForEach(board.visibleColumns) { column in
                            KanbanColumnView(board: board, column: column, width: Self.columnWidth(for: proxy.size.width), onOpen: onOpen)
                                .id(column.id)
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .scrollTargetLayout()
                }
                .scrollTargetBehavior(.viewAligned)
                .scrollDisabled(board.drag != nil)
                .onAppear { board.boardWidth = proxy.size.width }
                .onChange(of: proxy.size.width) { _, width in board.boardWidth = width }
                .onChange(of: board.autoScrollRequest) { _, request in
                    guard let request else { return }
                    withAnimation(.easeInOut(duration: CoreHubTokens.Motion.normal)) { scroller.scrollTo(request.column, anchor: .center) }
                }
            }
        }
    }

    /// A column fills the phone width minus a peek of the next one; capped on wide screens.
    static func columnWidth(for boardWidth: CGFloat) -> CGFloat { max(200, min(340, boardWidth - 56)) }
}

/// The dragged card, drawn above the board at the finger.
struct KanbanDragGhost: View {
    let drag: KanbanDragState?

    var body: some View {
        if let drag {
            KanbanCardView(task: drag.task)
                .frame(width: drag.origin.width)
                .scaleEffect(1.04)
                .rotationEffect(.degrees(1.5))
                .shadow(color: .black.opacity(0.25), radius: 18, y: 10)
                .position(x: drag.ghostFrame.midX, y: drag.ghostFrame.midY)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
        }
    }
}

/// The board never stays blank: a failed first load shows the server's
/// message and a retry, in addition to the banner.
struct KanbanLoadFailure: View {
    let message: String
    let retry: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 28))
                .foregroundStyle(Palette.warning)
            Text("The board could not be loaded")
                .font(Typography.titleFont)
                .foregroundStyle(Palette.textPrimary)
            Text(message)
                .font(Typography.metaFont)
                .foregroundStyle(Palette.textSecondary)
                .multilineTextAlignment(.center)
            Button(String(localized: "Retry"), action: retry)
                .buttonStyle(CoreHubPillButtonStyle(prominent: true))
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
