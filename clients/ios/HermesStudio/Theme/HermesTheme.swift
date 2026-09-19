import SwiftUI

/// The in-app logo: the server's custom logo when Core Hub has one, else the
/// vector Core Hub mark on the splash colour (#f7f7f4 / #1a1a1a).
struct AppMark: View {
    var size: CGFloat = 58
    @ObservedObject private var logo = StudioLogoStore.shared

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.23, style: .continuous)
                .fill(CoreHubTokens.Palette.splash)
            if let image = logo.image {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
            } else {
                CoreHubMarkView(size: size * 0.6, color: CoreHubTokens.Palette.textPrimary)
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: size * 0.23, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: size * 0.23, style: .continuous).stroke(CoreHubTokens.Palette.borderLight, lineWidth: 1))
        .accessibilityLabel("Core Hub")
        .task { await logo.loadCached() }
    }
}

struct ProfileAvatar: View {
    let name: String
    var avatar: AvatarSpec?
    var size: CGFloat = 42
    @ObservedObject private var cache = AvatarImageCache.shared

    var body: some View {
        Group {
            if let image = cache.image(for: name) {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                ZStack {
                    CoreHubTokens.Palette.accent
                    Text(initials).font(.system(size: size * 0.38, weight: .semibold, design: .rounded)).foregroundStyle(CoreHubTokens.Palette.textOnAccent)
                }
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .overlay(Circle().stroke(Color.white.opacity(0.14), lineWidth: 1))
        .task(id: avatarFingerprint(profile: name, avatar: avatar)) {
            await cache.ensure(profile: name, avatar: avatar)
        }
    }

    private var initials: String {
        name.split(separator: " ").prefix(2).compactMap(\.first).map(String.init).joined().uppercased().nilIfEmpty ?? "C"
    }
}

/// Card surface: bg.card, radius 14, 1 px border.light, card shadow.
struct SurfaceCard<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        content.padding(14)
            .background(CoreHubTokens.Palette.bgCard, in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.card, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: CoreHubTokens.Radius.card, style: .continuous).stroke(CoreHubTokens.Palette.borderLight))
            .coreHubShadow(CoreHubTokens.Shadow.card)
    }
}

struct StatusPill: View {
    let text: String
    let color: Color
    var body: some View {
        Text(text)
            .font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.meta, weight: .semibold))
            .padding(.horizontal, 9).padding(.vertical, 5)
            .foregroundStyle(color)
            .background(color.opacity(0.13), in: Capsule())
    }
}

struct ToolIcon: View {
    let name: String
    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: 15, weight: .semibold))
            .foregroundStyle(color)
            .frame(width: 30, height: 30)
            .background(color.opacity(0.12), in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.control))
    }
    private var symbol: String {
        let key = name.lowercased()
        if key.contains("terminal") || key.contains("shell") { return "terminal" }
        if key.contains("vision") || key.contains("image") { return "eye" }
        if key.contains("search") || key.contains("web") { return "magnifyingglass" }
        if key.contains("file") || key.contains("read") || key.contains("write") { return "doc.text" }
        if key.contains("browser") { return "globe" }
        if key.contains("python") || key.contains("code") { return "chevron.left.forwardslash.chevron.right" }
        return "wrench.and.screwdriver"
    }
    private var color: Color { name.lowercased().contains("terminal") ? CoreHubTokens.Palette.success : CoreHubTokens.Palette.accent }
}
