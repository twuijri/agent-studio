import SwiftUI

/// Collapsible "N tools" card: 30 pt header (chevron rotating 90°, wrench,
/// count, ≤ 3 unique names "+N", trailing check / ••• / error), then one
/// 11 pt mono line per tool with Thinking / Arguments / Result details.
struct ToolSummaryCard: View {
    let tools: [ToolStep]
    @State private var expandedOverride: Bool?

    private var summary: ToolSummary { ToolSummary(tools: tools) }
    private var isExpanded: Bool { expandedOverride ?? summary.isActive }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Button {
                withAnimation(CoreHubTokens.Motion.quick) { expandedOverride = !isExpanded }
            } label: {
                ToolSummaryHeader(summary: summary, expanded: isExpanded)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(summary.countLabel)
            if isExpanded {
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(tools) { tool in ToolLineView(tool: tool) }
                }
                .padding(.horizontal, 2)
            }
        }
        .frame(maxWidth: CoreHubTokens.Layout.toolSummaryMaxWidth, alignment: .leading)
    }
}

private struct ToolSummaryHeader: View {
    let summary: ToolSummary
    let expanded: Bool

    var body: some View {
        HStack(spacing: 7) {
            CoreHubIconView(icon: .chevronForward, size: 11, strokeWidth: 2)
                .rotationEffect(.degrees(expanded ? 90 : 0))
                .foregroundStyle(CoreHubTokens.Palette.textMuted)
            CoreHubIconView(icon: .wrench, size: 13, strokeWidth: 1.5)
                .foregroundStyle(CoreHubTokens.Palette.accent.opacity(0.82))
            Text(summary.countLabel)
                .font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.meta, weight: .medium))
                .foregroundStyle(CoreHubTokens.Palette.textSecondary)
                .fixedSize()
            if !summary.names.isEmpty {
                TechnicalText(text: summary.names, font: CoreHubTokens.Typography.metaFont, color: CoreHubTokens.Palette.textMuted)
                    .truncationMode(.tail)
            }
            Spacer(minLength: 4)
            ToolSummaryTrailing(summary: summary)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .frame(minHeight: CoreHubTokens.Layout.toolSummaryHeader)
        .background(CoreHubTokens.Palette.bgSecondary.opacity(0.6), in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.control))
        .overlay(RoundedRectangle(cornerRadius: CoreHubTokens.Radius.control).stroke(CoreHubTokens.Palette.textPrimary.opacity(0.09)))
        .contentShape(Rectangle())
    }
}

private struct ToolSummaryTrailing: View {
    let summary: ToolSummary

    var body: some View {
        if summary.hasError {
            Text("Error").font(CoreHubTokens.Typography.metaFont).foregroundStyle(CoreHubTokens.Palette.error)
        } else if summary.hasInterrupted {
            Text("Tool result unavailable").font(CoreHubTokens.Typography.metaFont).foregroundStyle(CoreHubTokens.Palette.error).lineLimit(1)
        } else if summary.isActive {
            Text(verbatim: "•••").font(CoreHubTokens.Typography.metaFont).foregroundStyle(CoreHubTokens.Palette.accent.opacity(0.78))
        } else {
            CoreHubIconView(icon: .check, size: 14, strokeWidth: 2).foregroundStyle(CoreHubTokens.Palette.accent.opacity(0.78))
        }
    }
}

/// One tool line (11 pt mono): status, name, preview, duration; tapping
/// reveals Thinking / Arguments / Result.
struct ToolLineView: View {
    let tool: ToolStep
    @State private var showDetails = false

    private var hasDetails: Bool { tool.reasoning != nil || tool.arguments != nil || tool.output != nil }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Button {
                guard hasDetails else { return }
                withAnimation(CoreHubTokens.Motion.quick) { showDetails.toggle() }
            } label: {
                ToolLineHeader(tool: tool)
            }
            .buttonStyle(.plain)
            if showDetails {
                ToolLineDetails(tool: tool)
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .background(CoreHubTokens.Palette.bgSecondary.opacity(0.4), in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.button))
    }
}

private struct ToolLineHeader: View {
    let tool: ToolStep

    var body: some View {
        HStack(spacing: 7) {
            ToolStatusIcon(status: tool.status)
            Text(tool.name)
                .font(CoreHubTokens.Typography.mono(CoreHubTokens.Typography.meta, weight: .semibold))
                .foregroundStyle(CoreHubTokens.Palette.textPrimary)
                .lineLimit(1)
                .fixedSize()
            if let detail = tool.detail, !detail.isEmpty {
                TechnicalText(text: detail, font: CoreHubTokens.Typography.mono(CoreHubTokens.Typography.meta), color: CoreHubTokens.Palette.textMuted)
                    .truncationMode(.tail)
            }
            Spacer(minLength: 4)
            if let duration = tool.duration, tool.status != .running {
                Text(String(format: "%.1fs", duration))
                    .font(CoreHubTokens.Typography.mono(CoreHubTokens.Typography.meta).monospacedDigit())
                    .foregroundStyle(CoreHubTokens.Palette.textMuted)
                    .fixedSize()
            }
        }
        .contentShape(Rectangle())
    }
}

struct ToolStatusIcon: View {
    let status: ToolStatus

    var body: some View {
        switch status {
        case .running: ProgressView().controlSize(.mini).frame(width: 12, height: 12)
        case .done: Image(systemName: "checkmark").font(.system(size: 10, weight: .bold)).foregroundStyle(CoreHubTokens.Palette.success).frame(width: 12, height: 12)
        case .error: Image(systemName: "xmark").font(.system(size: 10, weight: .bold)).foregroundStyle(CoreHubTokens.Palette.error).frame(width: 12, height: 12)
        case .interrupted: Image(systemName: "minus").font(.system(size: 10, weight: .bold)).foregroundStyle(CoreHubTokens.Palette.warning).frame(width: 12, height: 12)
        }
    }
}

private struct ToolLineDetails: View {
    let tool: ToolStep

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let reasoning = tool.reasoning, !reasoning.isEmpty {
                ToolDetailSection(title: String(localized: "Thinking"), text: reasoning, italic: true)
            }
            if let arguments = tool.arguments, !arguments.isEmpty {
                ToolDetailSection(title: String(localized: "Arguments"), text: arguments)
            }
            if let output = tool.output, !output.isEmpty {
                ToolDetailSection(title: String(localized: "Result"), text: output)
                if tool.outputTruncated {
                    Text(truncationNote)
                        .font(CoreHubTokens.Typography.metaFont)
                        .foregroundStyle(CoreHubTokens.Palette.textMuted)
                }
            }
        }
        .padding(.top, 2)
    }

    private var truncationNote: String {
        if let length = tool.outputOriginalLength { return String(localized: "Result truncated (\(length) characters in full)") }
        return String(localized: "Result truncated")
    }
}

private struct ToolDetailSection: View {
    let title: String
    let text: String
    var italic = false

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(title.uppercased())
                .font(CoreHubTokens.Typography.groupHeaderFont)
                .tracking(CoreHubTokens.Typography.groupHeaderTracking)
                .foregroundStyle(CoreHubTokens.Palette.textMuted)
            Text(text)
                .font(italic ? CoreHubTokens.Typography.thinkingFont : CoreHubTokens.Typography.mono(CoreHubTokens.Typography.meta))
                .foregroundStyle(CoreHubTokens.Palette.textSecondary)
                .lineLimit(40)
                .textSelection(.enabled)
                .frame(maxWidth: .infinity, alignment: .leading)
                .environment(\.layoutDirection, italic ? MarkdownText.layoutDirection(for: text) : .leftToRight)
                .padding(8)
                .background(CoreHubTokens.Palette.codeBackground, in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.button))
        }
    }
}
