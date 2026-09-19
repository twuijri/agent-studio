import SwiftUI
import QuickLook

struct EmptyState: View {
    let icon: String
    let title: LocalizedStringKey
    let detail: LocalizedStringKey
    var body: some View {
        ContentUnavailableView { Label(title, systemImage: icon) } description: { Text(detail) }
    }
}

struct ErrorBanner: View {
    let message: String
    var body: some View {
        Label(message, systemImage: "exclamationmark.triangle.fill")
            .font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.sidebarTab))
            .foregroundStyle(CoreHubTokens.Palette.error)
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(CoreHubTokens.Palette.bgCard, in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.card))
            .overlay(RoundedRectangle(cornerRadius: CoreHubTokens.Radius.card).stroke(CoreHubTokens.Palette.error.opacity(0.4)))
            .coreHubShadow(CoreHubTokens.Shadow.card)
    }
}

struct SearchBar: View {
    @Binding var text: String
    var body: some View {
        HStack(spacing: 9) {
            CoreHubIconView(icon: .search, size: 16).foregroundStyle(CoreHubTokens.Palette.textMuted)
            TextField("Search", text: $text)
                .font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.inputMinimum))
                .textInputAutocapitalization(.never)
            if !text.isEmpty { Button { text = "" } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(CoreHubTokens.Palette.textMuted) }.buttonStyle(.plain) }
        }
        .padding(.horizontal, 12)
        .frame(height: 42)
        .background(CoreHubTokens.Palette.bgInput, in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.control))
        .overlay(RoundedRectangle(cornerRadius: CoreHubTokens.Radius.control).stroke(CoreHubTokens.Palette.inputBorderIdle))
    }
}

struct SettingsRow<Trailing: View>: View {
    let icon: String
    let color: Color
    let title: LocalizedStringKey
    var subtitle: String? = nil
    @ViewBuilder var trailing: Trailing
    var body: some View {
        HStack(spacing: 13) {
            Image(systemName: icon).font(.system(size: 16, weight: .semibold)).foregroundStyle(.white).frame(width: 31, height: 31).background(color.gradient, in: RoundedRectangle(cornerRadius: 8))
            VStack(alignment: .leading, spacing: 3) { Text(title).font(.body.weight(.medium)); if let subtitle, !subtitle.isEmpty { Text(subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(2) } }
            Spacer(minLength: 8); trailing
        }.contentShape(Rectangle()).padding(.vertical, 4)
    }
}

extension SettingsRow where Trailing == AnyView {
    init(icon: String, color: Color, title: LocalizedStringKey, subtitle: String? = nil) {
        self.init(icon: icon, color: color, title: title, subtitle: subtitle) { AnyView(Image(systemName: "chevron.forward").font(.caption.weight(.bold)).foregroundStyle(.tertiary)) }
    }
}

struct AgentToolRow: View {
    let icon: String
    let color: Color
    let title: LocalizedStringKey
    let detail: LocalizedStringKey
    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: icon).font(.system(size: 17, weight: .semibold)).foregroundStyle(color).frame(width: 38, height: 38).background(color.opacity(0.10), in: RoundedRectangle(cornerRadius: 10))
            VStack(alignment: .leading, spacing: 4) { Text(title).font(.headline); Text(detail).font(.subheadline).foregroundStyle(.secondary).lineLimit(2) }
            Spacer(); Image(systemName: "chevron.forward").font(.caption.weight(.bold)).foregroundStyle(.tertiary)
        }.padding(.vertical, 2).contentShape(Rectangle())
    }
}

struct FileDownloadCard: View {
    let link: DownloadLink
    /// Fetches the file with the bearer token in the Authorization header and
    /// returns a local copy (see `APIClient.downloadFile`).
    let fetch: () async throws -> URL
    @State private var localURL: URL?
    @State private var loading = false
    @State private var error: String?

    var body: some View {
        Button { Task { await download() } } label: {
            HStack(spacing: 12) {
                Image(systemName: fileIcon).font(.title3).foregroundStyle(CoreHubTokens.Palette.accent).frame(width: 40, height: 40).background(CoreHubTokens.Palette.accent.opacity(0.12), in: RoundedRectangle(cornerRadius: 11))
                VStack(alignment: .leading, spacing: 3) { Text(link.label).font(.subheadline.weight(.semibold)).lineLimit(2); Text(ChatFiles.fileName(for: link)).font(.caption).foregroundStyle(.secondary).lineLimit(1) }
                Spacer(); if loading { ProgressView() } else { Image(systemName: "arrow.down.circle.fill").font(.title3).foregroundStyle(CoreHubTokens.Palette.accent) }
            }.padding(11).background(CoreHubTokens.Palette.bgCard, in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.card)).overlay(RoundedRectangle(cornerRadius: CoreHubTokens.Radius.card).stroke(CoreHubTokens.Palette.borderLight))
        }.buttonStyle(.plain).quickLookPreview($localURL)
        if let error { Text(error).font(CoreHubTokens.Typography.metaFont).foregroundStyle(CoreHubTokens.Palette.error) }
    }

    private var fileIcon: String {
        let ext = URL(fileURLWithPath: link.path).pathExtension.lowercased()
        if ["png", "jpg", "jpeg", "webp", "gif"].contains(ext) { return "photo" }
        if ext == "pdf" { return "doc.richtext" }
        if ["ppt", "pptx"].contains(ext) { return "rectangle.on.rectangle.angled" }
        if ["zip", "tar", "gz"].contains(ext) { return "archivebox" }
        return "doc"
    }

    private func download() async {
        loading = true; defer { loading = false }
        do {
            localURL = try await fetch(); error = nil
        } catch { self.error = error.localizedDescription }
    }
}

struct FlowLayout: Layout {
    var spacing: CGFloat = 8
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 300; var x: CGFloat = 0, y: CGFloat = 0, row: CGFloat = 0
        for view in subviews { let size = view.sizeThatFits(.unspecified); if x + size.width > width && x > 0 { x = 0; y += row + spacing; row = 0 }; x += size.width + spacing; row = max(row, size.height) }
        return CGSize(width: width, height: y + row)
    }
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, row: CGFloat = 0
        for view in subviews { let size = view.sizeThatFits(.unspecified); if x + size.width > bounds.maxX && x > bounds.minX { x = bounds.minX; y += row + spacing; row = 0 }; view.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size)); x += size.width + spacing; row = max(row, size.height) }
    }
}
