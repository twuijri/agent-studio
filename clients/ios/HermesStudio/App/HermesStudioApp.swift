import SwiftUI

@main
struct HermesStudioApp: App {
    @StateObject private var store = AppStore()

    var body: some Scene {
        WindowGroup {
            ZStack(alignment: .top) {
                Group {
                    switch store.phase {
                    case .launching: LaunchView(server: store.baseURL)
                    case .signedOut: LoginView()
                    case .signedIn: RootShell()
                    }
                }
                if let error = store.errorMessage {
                    ErrorBanner(message: error).padding(.horizontal).padding(.top, 8).transition(.move(edge: .top).combined(with: .opacity)).onTapGesture { store.errorMessage = nil }
                } else if let message = store.successMessage {
                    Label(message, systemImage: "checkmark.circle.fill")
                        .font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.sidebarTab, weight: .medium))
                        .foregroundStyle(CoreHubTokens.Palette.textOnAccent)
                        .padding(11)
                        .background(CoreHubTokens.Palette.success, in: Capsule())
                        .padding(.top, 8)
                        .transition(.move(edge: .top).combined(with: .opacity))
                }
                if store.languageTransitioning {
                    CoreHubTokens.Palette.bgPrimary
                        .ignoresSafeArea()
                        .transition(.opacity)
                        .zIndex(100)
                        .accessibilityHidden(true)
                }
            }
            .environmentObject(store)
            .environment(\.locale, store.locale)
            .environment(\.layoutDirection, store.layoutDirection)
            .preferredColorScheme(store.preferredColorScheme)
            .tint(CoreHubTokens.Palette.accent)
            .task { await store.boot() }
            .animation(.snappy, value: store.errorMessage)
            .animation(.snappy, value: store.successMessage)
            .animation(.easeInOut(duration: 0.11), value: store.languageTransitioning)
        }
    }
}

/// Splash: the Core Hub mark on the splash colour while the session boots.
private struct LaunchView: View {
    let server: String

    var body: some View {
        VStack(spacing: 14) {
            AppMark(size: 94)
            Text("Core Hub").font(CoreHubTokens.Typography.font(22, weight: .bold)).foregroundStyle(CoreHubTokens.Palette.textPrimary)
            if !server.isEmpty {
                TechnicalText(text: server, font: CoreHubTokens.Typography.font(CoreHubTokens.Typography.sidebarTab), color: CoreHubTokens.Palette.textSecondary)
            }
            ProgressView().controlSize(.large).padding(.top, 5)
        }
        .padding(.horizontal, 28)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(CoreHubTokens.Palette.splash.ignoresSafeArea())
    }
}
