import SwiftUI

/// The sheet a drop opens when it has several meanings (waiting: schedule or
/// block) or when the Hermes command needs a reason (block). Cancelling puts
/// the card back.
struct KanbanDropSheet: View {
    let prompt: KanbanDropPrompt
    @ObservedObject var board: KanbanBoardController
    @State private var reason = ""

    var body: some View {
        NavigationStack {
            Form {
                switch prompt {
                case let .choose(_, _, options): choices(options)
                case let .reason(_, drop): reasonSection(drop)
                case .confirmArchive: EmptyView()
                }
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(String(localized: "Cancel")) { board.cancelPrompt() }
                }
            }
        }
        .presentationDetents([.medium])
        .interactiveDismissDisabled(false)
    }

    private var title: String {
        switch prompt {
        case .choose: return String(localized: "How should this task wait?")
        case let .reason(_, drop): return drop.transition.action == .block ? String(localized: "Reason for blocking") : String(localized: "Note (optional)")
        case .confirmArchive: return String(localized: "Archive Task")
        }
    }

    private func choices(_ options: [KanbanColumnDrop]) -> some View {
        Section {
            ForEach(options, id: \.to) { drop in
                Button { board.choose(drop) } label: {
                    Label(Self.label(for: drop), systemImage: drop.transition.action == .block ? "pause.circle" : "clock")
                }
            }
        }
    }

    private func reasonSection(_ drop: KanbanColumnDrop) -> some View {
        Section {
            TextField(String(localized: "Reason for blocking"), text: $reason, axis: .vertical)
                .lineLimit(2...6)
                .contentDirection(of: reason)
            Button(Self.confirmLabel(for: drop)) { board.confirmReason(reason) }
                .disabled(reason.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        }
    }

    /// Web `choiceLabel`: the waiting kinds get their own wording, anything else its status name.
    static func label(for drop: KanbanColumnDrop) -> String {
        switch drop.transition.action {
        case .schedule: return String(localized: "Park until later (scheduled)")
        case .block: return String(localized: "Block until someone acts (needs a reason)")
        default: return drop.to.title
        }
    }

    static func confirmLabel(for drop: KanbanColumnDrop) -> String {
        drop.transition.action == .block ? String(localized: "Block") : String(localized: "Move")
    }
}
