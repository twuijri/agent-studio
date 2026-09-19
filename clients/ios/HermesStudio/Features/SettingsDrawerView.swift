import SwiftUI

/// The settings drawer (web `AppSidebar`): Logs, Usage, Performance (sa),
/// Skills Usage, Theme, Pets, Profiles (sa), Settings.
struct SettingsDrawerView: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        ScrollView {
            VStack(spacing: 2) {
                row("Logs", symbol: "doc.text.magnifyingglass", destination: .logs)
                row("Usage", symbol: "chart.bar.xaxis", destination: .usage)
                if store.isSuperAdmin { row("Performance", symbol: "gauge.with.dots.needle.33percent", destination: .performance) }
                row("Skills Usage", symbol: "square.stack.3d.up", destination: .skillsUsage)
                row("Theme", symbol: "paintpalette", destination: .theme)
                row("Pets", symbol: "pawprint", destination: .pets)
                if store.isSuperAdmin { row("Profiles", symbol: "person.2", destination: .profiles) }
                Divider().overlay(CoreHubTokens.Palette.borderLight).padding(.vertical, 6)
                Button { store.show(.settings) } label: {
                    HStack(spacing: 10) {
                        CoreHubIconView(icon: .settings, size: CoreHubTokens.Layout.railIcon)
                        Text("Settings").font(CoreHubTokens.Typography.navItemFont)
                        Spacer(minLength: 0)
                    }
                    .foregroundStyle(store.shellDestination == .settings ? CoreHubTokens.Palette.textPrimary : CoreHubTokens.Palette.textSecondary)
                    .padding(.horizontal, 10)
                    .frame(height: 36)
                    .background(store.shellDestination == .settings ? CoreHubTokens.Palette.selected : Color.clear, in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.button, style: .continuous))
                    .contentShape(Rectangle())
                }
                .buttonStyle(RailButtonStyle())
            }
            .padding(8)
        }
    }

    private func row(_ title: LocalizedStringKey, symbol: String, destination: ShellDestination) -> some View {
        let selected = store.shellDestination == destination
        return Button { store.show(destination) } label: {
            HStack(spacing: 10) {
                Image(systemName: symbol).font(.system(size: 15, weight: .regular)).frame(width: CoreHubTokens.Layout.railIcon)
                Text(title).font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.navItem, weight: selected ? .medium : .regular))
                Spacer(minLength: 0)
            }
            .foregroundStyle(selected ? CoreHubTokens.Palette.textPrimary : CoreHubTokens.Palette.textSecondary)
            .padding(.horizontal, 10)
            .frame(height: 36)
            .background(selected ? CoreHubTokens.Palette.selected : Color.clear, in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.button, style: .continuous))
            .contentShape(Rectangle())
        }
        .buttonStyle(RailButtonStyle())
    }
}

/// Shown for web screens that the app does not implement yet.
struct ComingLaterView: View {
    let title: LocalizedStringKey

    var body: some View {
        ContentUnavailableView {
            Label(title, systemImage: "hammer")
        } description: {
            Text("Coming in a later milestone")
        }
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
    }
}
