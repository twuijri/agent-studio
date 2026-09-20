import SwiftUI

/// The Agent Manager's chrome, value for value from the Android
/// `ui/agents/AgentManagerScreen.kt` the owner preferred ("شكل الأجنتات
/// بالأندرويد أجمل من شكله في الآيفون"). Every number is a `CoreHubTokens`
/// entry, and `AgentManagerParityTests` reads the Kotlin so the two phones
/// cannot drift apart again.

/// A card (`AgentRow`'s `Surface`): bg.card at the composer radius with a
/// 1 pt border, 16 of padding, its lines 8 apart.
struct AgentCardSurface<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: CoreHubTokens.Layout.agentCardGap) { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(CoreHubTokens.Layout.agentCardPadding)
            .background(CoreHubTokens.Palette.bgCard, in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.agentCard, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: CoreHubTokens.Radius.agentCard, style: .continuous).stroke(CoreHubTokens.Palette.border, lineWidth: CoreHubTokens.Layout.agentCardBorder))
    }
}

/// A section header: 13 bold, text.secondary, 8 below the item above it.
struct AgentSectionHeader: View {
    let title: LocalizedStringKey

    var body: some View {
        Text(title)
            .font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.sidebarTab, weight: .bold))
            .foregroundStyle(CoreHubTokens.Palette.textSecondary)
            .padding(.top, CoreHubTokens.Layout.agentSectionTop)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// The "runs on the server" note: 12 on info @ 10 % at radius 12, 12 of padding.
struct AgentNoteBox: View {
    let text: LocalizedStringKey

    var body: some View {
        Text(text)
            .font(CoreHubTokens.Typography.authorFont)
            .foregroundStyle(CoreHubTokens.Palette.textSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(CoreHubTokens.Layout.agentNotePadding)
            .background(CoreHubTokens.Palette.info.opacity(CoreHubTokens.Alpha.agentNoteFill), in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.agentNote, style: .continuous))
    }
}

/// The state pill (`PresenceBadge`): 11 semibold on its colour at 16 %,
/// 10 × 5 of padding, one line.
struct AgentStatePill: View {
    let text: String
    let color: Color

    var body: some View {
        Text(text)
            .font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.meta, weight: .semibold))
            .foregroundStyle(color)
            .lineLimit(1)
            .fixedSize()
            .padding(.horizontal, CoreHubTokens.Layout.agentPillPaddingH)
            .padding(.vertical, CoreHubTokens.Layout.agentPillPaddingV)
            .background(color.opacity(CoreHubTokens.Alpha.agentPill), in: Capsule())
    }
}

/// `LoadingRow`: a 22 pt spinner centred inside 16.
struct LoadingRowView: View {
    var body: some View {
        ProgressView()
            .frame(width: CoreHubTokens.Layout.loadingRowSpinner, height: CoreHubTokens.Layout.loadingRowSpinner)
            .frame(maxWidth: .infinity)
            .padding(CoreHubTokens.Layout.loadingRowPadding)
    }
}

/// Material's `OutlinedButton`: a 40 pt pill with a 1 pt border and 24 of
/// padding, 13/500, an optional 16 pt glyph 6 from its label.
struct AgentOutlinedButton: View {
    let title: LocalizedStringKey
    var icon: CoreHubIcon? = nil
    var enabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: CoreHubTokens.Layout.agentActionIconGap) {
                if let icon { CoreHubIconView(icon: icon, size: CoreHubTokens.Layout.agentActionIcon) }
                Text(title).lineLimit(1)
            }
            .font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.sidebarTab, weight: .medium))
            .foregroundStyle(CoreHubTokens.Palette.textPrimary)
            .padding(.horizontal, CoreHubTokens.Layout.agentOutlinedPaddingH)
            .frame(height: CoreHubTokens.Layout.agentActionHeight)
            .overlay(Capsule().stroke(CoreHubTokens.Palette.border, lineWidth: CoreHubTokens.Layout.agentCardBorder))
            .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : CoreHubTokens.Alpha.deleteAffordance)
    }
}

/// Material's `TextButton`: 40 tall, 12 of padding, 13/500, no chrome.
struct AgentTextButton: View {
    let title: LocalizedStringKey
    var color: Color = CoreHubTokens.Palette.textPrimary
    var enabled = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .lineLimit(1)
                .font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.sidebarTab, weight: .medium))
                .foregroundStyle(color)
                .padding(.horizontal, CoreHubTokens.Layout.agentTextButtonPaddingH)
                .frame(height: CoreHubTokens.Layout.agentActionHeight)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : CoreHubTokens.Alpha.deleteAffordance)
    }
}

/// `FlowRow`: the actions fill the first line from the start edge and wrap
/// when they must. A custom `Layout` is not mirrored for it, so the start
/// edge is the right one in Arabic here, by hand.
struct AgentActionsFlow: Layout {
    var spacing: CGFloat = CoreHubTokens.Layout.agentActionGap

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 320
        var x: CGFloat = 0, y: CGFloat = 0, row: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x + size.width > width && x > 0 { x = 0; y += row + spacing; row = 0 }
            x += size.width + spacing
            row = max(row, size.height)
        }
        return CGSize(width: width, height: y + row)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let rightToLeft = subviews.layoutDirection == .rightToLeft
        var x: CGFloat = 0, y: CGFloat = bounds.minY, row: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x + size.width > bounds.width && x > 0 { x = 0; y += row + spacing; row = 0 }
            let originX = rightToLeft ? bounds.maxX - x - size.width : bounds.minX + x
            view.place(at: CGPoint(x: originX, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            row = max(row, size.height)
        }
    }
}

// MARK: - The agent screen (`AgentScreen.kt` + `StudioGroupedCard`)

/// `StudioGroupedCard`: bg.card at the card radius with a 1 pt border.light
/// and no padding of its own — rows and dividers fill it.
struct AgentGroupedCard<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        VStack(spacing: 0) { content }
            .background(CoreHubTokens.Palette.bgCard, in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.card, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: CoreHubTokens.Radius.card, style: .continuous).stroke(CoreHubTokens.Palette.borderLight, lineWidth: CoreHubTokens.Layout.agentCardBorder))
    }
}

/// `StudioCardDivider`: border.light, indented 66 from the start edge.
struct AgentCardDivider: View {
    var inset: CGFloat = CoreHubTokens.Layout.agentRowDividerInset

    var body: some View {
        Rectangle()
            .fill(CoreHubTokens.Palette.borderLight)
            .frame(height: 1)
            .padding(.leading, inset)
    }
}

/// `StudioIconTile`: a 38 pt tile on accent @ 16 % at the bubble radius,
/// holding a 20 pt glyph. `settings` draws the Core Hub gear — the same
/// path Android's `CoreHubIcons.Settings` uses — the rest are SF Symbols.
struct AgentIconTile: View {
    let symbol: String

    var body: some View {
        Group {
            if symbol == "settings" {
                CoreHubIconView(icon: .settings, size: CoreHubTokens.Layout.agentRowIcon)
            } else {
                Image(systemName: symbol)
                    .resizable()
                    .scaledToFit()
                    .frame(width: CoreHubTokens.Layout.agentRowIcon, height: CoreHubTokens.Layout.agentRowIcon)
            }
        }
        .foregroundStyle(CoreHubTokens.Palette.accent)
        .frame(width: CoreHubTokens.Layout.agentRowIconTile, height: CoreHubTokens.Layout.agentRowIconTile)
        .background(CoreHubTokens.Palette.accent.opacity(CoreHubTokens.Alpha.agentIconTile), in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.bubble, style: .continuous))
    }
}

/// `StudioDestinationRow`: the tile, 12 to a 14/500 title, a chevron at the
/// end that mirrors in Arabic; 12 × 9 of padding.
struct AgentCapabilityRow: View {
    let title: Text
    let symbol: String

    var body: some View {
        HStack(spacing: CoreHubTokens.Layout.agentRowIconGap) {
            AgentIconTile(symbol: symbol)
            title
                .font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.base, weight: .medium))
                .foregroundStyle(CoreHubTokens.Palette.textPrimary)
                .lineLimit(1)
            Spacer(minLength: 0)
            CoreHubIconView(icon: .chevronForward, size: CoreHubTokens.Layout.settingsRowIcon)
                .foregroundStyle(CoreHubTokens.Palette.textSecondary)
        }
        .padding(.horizontal, CoreHubTokens.Layout.agentRowPaddingH)
        .padding(.vertical, CoreHubTokens.Layout.agentRowPaddingV)
        .contentShape(Rectangle())
    }
}

/// One line of the CLI details block: a 14/500 label, the value at the end.
struct AgentDetailRow<Value: View>: View {
    let title: LocalizedStringKey
    @ViewBuilder var value: Value

    var body: some View {
        HStack(spacing: CoreHubTokens.Layout.agentRowIconGap) {
            Text(title)
                .font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.base, weight: .medium))
                .foregroundStyle(CoreHubTokens.Palette.textPrimary)
            Spacer(minLength: CoreHubTokens.Layout.agentCardGap)
            value
        }
        .padding(.horizontal, CoreHubTokens.Layout.agentRowPaddingH)
        .padding(.vertical, CoreHubTokens.Layout.agentRowPaddingV)
    }
}
