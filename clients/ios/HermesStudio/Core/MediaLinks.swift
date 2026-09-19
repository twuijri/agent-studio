import Foundation

/// Files referenced from assistant Markdown: absolute server paths and
/// `device://<deviceId>/<path>` links. Both stream through
/// `/api/studio/files/download?path=…` with the bearer header.
enum MediaKind: Equatable {
    case video, audio, image, file

    static let videoExtensions: Set<String> = ["mp4", "webm", "mov", "m4v"]
    static let audioExtensions: Set<String> = ["mp3", "wav", "ogg", "m4a", "aac", "flac"]
    static let imageExtensions: Set<String> = ["png", "jpg", "jpeg", "webp", "gif", "heic"]

    static func classify(path: String, mime: String = "") -> MediaKind {
        let lower = mime.lowercased()
        if lower.hasPrefix("video/") { return .video }
        if lower.hasPrefix("audio/") { return .audio }
        if lower.hasPrefix("image/") { return .image }
        let ext = URL(fileURLWithPath: path.split(separator: "?").first.map(String.init) ?? path).pathExtension.lowercased()
        if videoExtensions.contains(ext) { return .video }
        if audioExtensions.contains(ext) { return .audio }
        if imageExtensions.contains(ext) { return .image }
        return .file
    }
}

struct DeviceFileLink: Equatable {
    let deviceID: String
    let path: String

    /// `device://<id>/<absolute path>`; the id is `[A-Za-z0-9_.-]+`.
    static func parse(_ value: String) -> DeviceFileLink? {
        guard value.hasPrefix("device://") else { return nil }
        let rest = value.dropFirst("device://".count)
        guard let slash = rest.firstIndex(of: "/") else { return nil }
        let id = String(rest[..<slash])
        let path = String(rest[slash...])
        guard !id.isEmpty, id.allSatisfy({ $0.isLetter || $0.isNumber || "_.-".contains($0) }), path.hasPrefix("/") else { return nil }
        return DeviceFileLink(deviceID: id, path: path)
    }
}

extension DownloadLink {
    var isDeviceFile: Bool { DeviceFileLink.parse(path) != nil }
    var mediaKind: MediaKind { MediaKind.classify(path: DeviceFileLink.parse(path)?.path ?? path) }
}
