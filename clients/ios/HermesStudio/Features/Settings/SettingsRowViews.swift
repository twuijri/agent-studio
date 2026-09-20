import SwiftUI

/// The settings anatomy of the Android client (`SettingsRowContent`,
/// `SettingsSection` and the Material `TabRow` in `HermesSettingsScreen`),
/// which the owner found tidier than the iOS form. Every number comes from
/// `CoreHubTokens.Layout`; `AgentManagerParityTests` pins them to the Kotlin.

/// Material's `TabRow`: equal tabs 48 tall across the width, 13/500,
/// text.primary when selected and text.secondary otherwise, a 2 pt accent
/// indicator under the selected one, no divider.
struct MaterialTabRow: View {
    let items: [TabStripItem]
    @Binding var selection: String

    var body: some View {
        HStack(spacing: 0) {
            ForEach(items) { item in
                let active = selection == item.id
                Button { withAnimation(CoreHubTokens.Motion.quick) { selection = item.id } } label: {
                    Text(item.title)
                        .font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.sidebarTab, weight: .medium))
                        .foregroundStyle(active ? CoreHubTokens.Palette.textPrimary : CoreHubTokens.Palette.textSecondary)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity)
                        .frame(height: CoreHubTokens.Layout.tabRowHeight)
                        .overlay(alignment: .bottom) {
                            Rectangle()
                                .fill(active ? CoreHubTokens.Palette.accent : Color.clear)
                                .frame(height: CoreHubTokens.Layout.tabIndicator)
                        }
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(active ? [.isSelected] : [])
            }
        }
        .background(CoreHubTokens.Palette.bgPrimary)
    }
}

/// `SettingsRowContent`: a bg.card row at the composer radius inside a
/// 16 × 4 margin, 16 × 14 of padding, a 24 pt glyph 14 from a 14 title over
/// an 11 value (two lines at most), and a switch or a chevron at the end.
struct SettingsCardRow<Trailing: View>: View {
    let icon: String
    let title: LocalizedStringKey
    let value: Text
    var onTap: (() -> Void)? = nil
    @ViewBuilder var trailing: Trailing

    var body: some View {
        HStack(spacing: CoreHubTokens.Layout.settingsRowGap) {
            Image(systemName: icon)
                .resizable()
                .scaledToFit()
                .frame(width: CoreHubTokens.Layout.settingsRowIcon, height: CoreHubTokens.Layout.settingsRowIcon)
                .foregroundStyle(CoreHubTokens.Palette.textSecondary)
            VStack(alignment: .leading, spacing: 0) {
                Text(title).font(CoreHubTokens.Typography.bodyFont).foregroundStyle(CoreHubTokens.Palette.textPrimary)
                value.font(CoreHubTokens.Typography.metaFont).foregroundStyle(CoreHubTokens.Palette.textSecondary).lineLimit(2)
            }
            Spacer(minLength: CoreHubTokens.Layout.agentCardGap)
            trailing
        }
        .padding(.horizontal, CoreHubTokens.Layout.settingsRowPaddingH)
        .padding(.vertical, CoreHubTokens.Layout.settingsRowPaddingV)
        .background(CoreHubTokens.Palette.bgCard, in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.agentCard, style: .continuous))
        .contentShape(RoundedRectangle(cornerRadius: CoreHubTokens.Radius.agentCard, style: .continuous))
        .onTapGesture { onTap?() }
        .padding(.horizontal, CoreHubTokens.Layout.screenPaddingH)
        .padding(.vertical, CoreHubTokens.Layout.settingsRowOuterV)
    }
}

extension SettingsCardRow where Trailing == AnyView {
    /// A row that opens something: the chevron at the end, mirrored in Arabic.
    init(icon: String, title: LocalizedStringKey, value: Text, onTap: @escaping () -> Void) {
        self.init(icon: icon, title: title, value: value, onTap: onTap) {
            AnyView(
                CoreHubIconView(icon: .chevronForward, size: CoreHubTokens.Layout.settingsRowIcon)
                    .foregroundStyle(CoreHubTokens.Palette.textSecondary)
            )
        }
    }
}

/// `SettingsSection`: 12 semibold, text.secondary, inside 22 / 18 / 22 / 4.
struct SettingsSectionLabel: View {
    let title: LocalizedStringKey

    var body: some View {
        Text(title)
            .font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.author, weight: .semibold))
            .foregroundStyle(CoreHubTokens.Palette.textSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, CoreHubTokens.Layout.settingsSectionPaddingH)
            .padding(.top, CoreHubTokens.Layout.settingsSectionPaddingTop)
            .padding(.bottom, CoreHubTokens.Layout.settingsSectionPaddingBottom)
    }
}

/// The hint under a section label (`agent_autostart_note`): 11,
/// text.secondary, inside 20 × 4.
struct SettingsHintText: View {
    let text: LocalizedStringKey

    var body: some View {
        Text(text)
            .font(CoreHubTokens.Typography.metaFont)
            .foregroundStyle(CoreHubTokens.Palette.textSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, CoreHubTokens.Layout.settingsHintPaddingH)
            .padding(.vertical, CoreHubTokens.Layout.settingsHintPaddingV)
    }
}
