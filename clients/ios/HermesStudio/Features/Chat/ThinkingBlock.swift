import SwiftUI

/// 💭 Thinking · Observed {duration} · {count} chars — 13 pt italic at 85 %,
/// collapsible, ticking every second while the reply is still streaming.
struct ThinkingBlock: View {
    let line: ChatLine
    @State private var expanded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Button {
                withAnimation(CoreHubTokens.Motion.quick) { expanded.toggle() }
            } label: {
                if line.isStreaming {
                    TimelineView(.periodic(from: .now, by: 1)) { context in
                        ThinkingHeader(line: line, expanded: expanded, now: context.date)
                    }
                } else {
                    ThinkingHeader(line: line, expanded: expanded, now: .now)
                }
            }
            .buttonStyle(.plain)
            if expanded && !line.reasoning.isEmpty {
                MarkdownText(text: line.reasoning)
                    .font(CoreHubTokens.Typography.thinkingFont)
                    .foregroundStyle(CoreHubTokens.Palette.textSecondary)
                    .opacity(CoreHubTokens.Alpha.thinkingText)
                    .padding(.leading, 6)
            }
        }
    }
}

private struct ThinkingHeader: View {
    let line: ChatLine
    let expanded: Bool
    let now: Date

    private var isThinkingNow: Bool { line.isStreaming && line.text.isEmpty && line.tools.isEmpty }

    var body: some View {
        HStack(spacing: 5) {
            CoreHubIconView(icon: .chevronForward, size: 10, strokeWidth: 2)
                .rotationEffect(.degrees(expanded ? 90 : 0))
            Text(verbatim: "💭")
            Text("Thinking")
            if isThinkingNow { ProgressView().controlSize(.mini) }
            if let observed = ThinkingFormat.observed(for: line, now: now), observed > 0 {
                Text(verbatim: "· ") + Text("Observed \(ThinkingFormat.duration(observed))")
            }
            if !line.reasoning.isEmpty {
                Text(verbatim: "· ") + Text("\(ThinkingFormat.characterCount(line.reasoning)) chars")
            }
        }
        .font(CoreHubTokens.Typography.thinkingFont)
        .foregroundStyle(CoreHubTokens.Palette.textSecondary)
        .opacity(CoreHubTokens.Alpha.thinkingText)
        .lineLimit(1)
        .contentShape(Rectangle())
        .accessibilityLabel("Thinking")
    }
}
