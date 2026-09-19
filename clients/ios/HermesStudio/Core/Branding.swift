import CryptoKit
import Foundation
import SwiftUI
import UIKit

@MainActor
final class StudioLogoStore: ObservableObject {
    static let shared = StudioLogoStore()

    @Published private(set) var image: UIImage?

    private let refreshInterval: TimeInterval = 7 * 24 * 60 * 60
    private var didLoadCache = false

    private init() {}

    func loadCached() async {
        guard !didLoadCache else { return }
        didLoadCache = true
        let file = Self.logoFile
        let data = await Task.detached(priority: .utility) { try? Data(contentsOf: file) }.value
        if let data, let decoded = UIImage(data: data) { image = decoded }
    }

    func sync(from api: APIClient, force: Bool = false) async {
        await loadCached()
        let file = Self.logoFile
        let serverFile = Self.serverFile
        let server = api.baseURL
        let refreshInterval = refreshInterval
        let fresh = await Task.detached(priority: .utility) {
            guard !force,
                  FileManager.default.fileExists(atPath: file.path),
                  (try? String(contentsOf: serverFile, encoding: .utf8)) == server,
                  let values = try? file.resourceValues(forKeys: [.contentModificationDateKey]),
                  let modified = values.contentModificationDate
            else { return false }
            return Date().timeIntervalSince(modified) < refreshInterval
        }.value
        guard !fresh, let data = await api.logoData(), let decoded = UIImage(data: data) else { return }

        await Task.detached(priority: .utility) {
            try? FileManager.default.createDirectory(at: Self.directory, withIntermediateDirectories: true)
            try? data.write(to: file, options: .atomic)
            try? server.write(to: serverFile, atomically: true, encoding: .utf8)
        }.value
        image = decoded
    }

    nonisolated private static var directory: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("HermesStudio/branding", isDirectory: true)
    }
    nonisolated private static var logoFile: URL { directory.appendingPathComponent("studio-logo") }
    nonisolated private static var serverFile: URL { directory.appendingPathComponent("studio-logo.server") }
}

@MainActor
final class AvatarImageCache: ObservableObject {
    static let shared = AvatarImageCache()

    @Published private var images: [String: UIImage] = [:]
    private var loadedFingerprints: [String: String] = [:]
    private var loading: Set<String> = []

    private init() {}

    func image(for profile: String) -> UIImage? { images[profile] }

    func ensure(profile: String, avatar: AvatarSpec?) async {
        guard !profile.isEmpty else { return }
        let fingerprint = avatarFingerprint(profile: profile, avatar: avatar)
        guard loadedFingerprints[profile] != fingerprint, !loading.contains(profile) else { return }
        loading.insert(profile)

        if let cached = await Task.detached(priority: .utility, operation: {
            AvatarDiskCache.cached(profile: profile, fingerprint: fingerprint)
        }).value {
            images[profile] = cached
            loadedFingerprints[profile] = fingerprint
            loading.remove(profile)
            return
        }

        // Only stored image avatars are cached as bitmaps. A generated avatar
        // is drawn natively by `BeamAvatarView`, so it never round-trips
        // through a renderer or the disk.
        var result: UIImage?
        if avatar?.type == "image", let dataURL = avatar?.dataURL {
            result = await Task.detached(priority: .utility) {
                AvatarDiskCache.decodeDataURL(dataURL)
            }.value
        }

        if let result {
            await Task.detached(priority: .utility) {
                AvatarDiskCache.store(result, profile: profile, fingerprint: fingerprint)
            }.value
            images[profile] = result
            loadedFingerprints[profile] = fingerprint
        }
        loading.remove(profile)
    }
}

func avatarFingerprint(profile: String, avatar: AvatarSpec?) -> String {
    if avatar?.type == "image", let dataURL = avatar?.dataURL {
        return "image:\(avatar?.updatedAt ?? 0):\(sha256(dataURL))"
    }
    return "generated:\(avatar?.seed ?? profile)"
}

private enum AvatarDiskCache {
    static func cached(profile: String, fingerprint: String) -> UIImage? {
        let files = files(profile: profile)
        guard (try? String(contentsOf: files.stamp, encoding: .utf8)) == fingerprint else { return nil }
        return UIImage(contentsOfFile: files.image.path)
    }

    static func store(_ image: UIImage, profile: String, fingerprint: String) {
        let files = files(profile: profile)
        guard let png = image.pngData() else { return }
        try? png.write(to: files.image, options: .atomic)
        try? fingerprint.write(to: files.stamp, atomically: true, encoding: .utf8)
    }

    static func decodeDataURL(_ value: String) -> UIImage? {
        guard let comma = value.firstIndex(of: ","),
              let data = Data(base64Encoded: String(value[value.index(after: comma)...]), options: .ignoreUnknownCharacters)
        else { return nil }
        return UIImage(data: data)
    }

    private static func files(profile: String) -> (image: URL, stamp: URL) {
        let directory = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("HermesStudio/avatars", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let key = sha256(profile)
        return (
            directory.appendingPathComponent("\(key).png"),
            directory.appendingPathComponent("\(key).stamp")
        )
    }
}

private func sha256(_ value: String) -> String {
    SHA256.hash(data: Data(value.utf8)).map { String(format: "%02x", $0) }.joined()
}
