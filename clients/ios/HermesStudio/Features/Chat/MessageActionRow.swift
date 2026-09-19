import SwiftUI

/// Always-visible action row: speech play/pause, copy, reference (quote into
/// the composer), fork, and the 11 pt timestamp. Buttons are 24 pt, radius 6.
struct MessageActionRow: View {
    let line: ChatLine
    let context: MessageRowContext

    private var hasText: Bool { !line.text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

    var body: some View {
        HStack(spacing: 2) {
            if hasText && line.kind == .assistant {
                ActionButton(symbol: context.isSpeaking(line) ? "pause.fill" : "play.fill", label: context.isSpeaking(line) ? String(localized: "Pause") : String(localized: "Play voice")) { context.onSpeak(line) }
            }
            if hasText {
                ActionButton(symbol: "doc.on.doc", label: String(localized: "Copy message")) { context.onCopy(line) }
                ActionButton(symbol: "arrowshape.turn.up.left", label: String(localized: "Reference message")) { context.onReference(line) }
            }
            if context.canFork && line.kind == .assistant && !line.isStreaming {
                ActionButton(symbol: "arrow.triangle.branch", label: String(localized: "Fork conversation")) { context.onFork(line) }
            }
            Spacer(minLength: 6)
            if line.isStreaming {
                ProgressView().controlSize(.mini)
            } else if let timestamp = line.timestamp {
                Text(timestamp.chatTime)
                    .font(CoreHubTokens.Typography.metaFont)
                    .foregroundStyle(CoreHubTokens.Palette.textMuted)
            }
        }
        .padding(.top, 2)
    }
}

struct ActionButton: View {
    let symbol: String
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(CoreHubTokens.Palette.textMuted)
                .frame(width: CoreHubTokens.Layout.actionButton, height: CoreHubTokens.Layout.actionButton)
                .background(CoreHubTokens.Palette.hover, in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.button))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
    }
}
