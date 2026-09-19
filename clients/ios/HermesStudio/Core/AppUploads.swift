import Foundation

/// Chunked attachment upload (`/api/studio/app-uploads`):
/// `POST` opens a session → `PUT …/chunks?offset=N` (≤ 256 KiB raw body each)
/// → `POST …/complete` returns the stored `{ name, path }` used in the
/// message content block. `DELETE …/{id}` cancels. Limits: 50 MB, 5-minute TTL.
enum AppUploadPlan {
    static let maxBytes = 50 * 1024 * 1024
    static let defaultChunkBytes = 256 * 1024
    static let idAlphabet = Array("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-")

    /// Client-generated id: 8–128 characters of `[A-Za-z0-9_-]`.
    static func makeID(length: Int = 32) -> String {
        let size = min(128, max(8, length))
        return String((0..<size).map { _ in idAlphabet.randomElement()! })
    }

    static func isValidID(_ id: String) -> Bool {
        (8...128).contains(id.count) && id.allSatisfy { idAlphabet.contains($0) }
    }

    /// Byte ranges for a file of `size` bytes, honouring the server's
    /// `maxChunkBytes` (never above 256 KiB).
    static func chunks(size: Int, maxChunkBytes: Int) -> [Range<Int>] {
        let chunk = max(1, min(maxChunkBytes, defaultChunkBytes))
        guard size > 0 else { return [] }
        var ranges: [Range<Int>] = []
        var offset = 0
        while offset < size {
            let end = min(size, offset + chunk)
            ranges.append(offset..<end)
            offset = end
        }
        return ranges
    }
}

struct AppUploadSession: Equatable {
    let id: String
    let nextOffset: Int
    let maxChunkBytes: Int

    init(_ json: JSON) {
        id = json.string("id")
        nextOffset = json.int("nextOffset", default: json.int("next_offset"))
        let declared = json.int("maxChunkBytes", default: json.int("max_chunk_bytes"))
        maxChunkBytes = declared > 0 ? declared : AppUploadPlan.defaultChunkBytes
    }
}

/// Progress of one attachment while it uploads.
struct AttachmentUpload: Identifiable, Hashable {
    enum Phase: Hashable { case uploading, done, failed(String), cancelled }
    let id: String
    let name: String
    let mime: String
    let size: Int
    var sent = 0
    var phase: Phase = .uploading
    var result: Upload?

    var fraction: Double { size > 0 ? min(1, Double(sent) / Double(size)) : (phase == .done ? 1 : 0) }
}

extension APIClient {
    func openAppUpload(id: String, name: String, size: Int, profile: String) async throws -> AppUploadSession {
        AppUploadSession(try await object("/api/studio/app-uploads", method: "POST", body: ["id": id, "name": name, "size": size], profile: profile))
    }

    func appendAppUploadChunk(id: String, offset: Int, data: Data, profile: String) async throws -> Int {
        var request = URLRequest(url: try url("/api/studio/app-uploads/\(id.urlEncoded)/chunks?offset=\(offset)"))
        request.httpMethod = "PUT"
        request.httpBody = data
        request.setValue("application/octet-stream", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(profile, forHTTPHeaderField: "X-Hermes-Profile")
        let (responseData, http) = try await send(request)
        guard (200..<300).contains(http.statusCode) else { throw HermesError.http(http.statusCode, Self.errorDetailText(responseData)) }
        let json = (try? JSONSerialization.jsonObject(with: responseData) as? JSON) ?? [:]
        let next = json.int("nextOffset", default: json.int("next_offset", default: -1))
        return next >= 0 ? next : offset + data.count
    }

    func completeAppUpload(id: String, fallbackName: String, mime: String, profile: String) async throws -> Upload {
        let root = try await object("/api/studio/app-uploads/\(id.urlEncoded)/complete", method: "POST", profile: profile)
        guard let file = root.objects("files").first, let path = file.string("path").nilIfEmpty else { throw HermesError.malformedResponse }
        return Upload(name: file.string("name").nilIfEmpty ?? fallbackName, path: path, mime: file.string("media_type", "mime").nilIfEmpty ?? mime)
    }

    func abortAppUpload(id: String, profile: String) async throws {
        _ = try await object("/api/studio/app-uploads/\(id.urlEncoded)", method: "DELETE", profile: profile)
    }

    /// Runs the whole chunked upload. `progress` receives bytes sent so far.
    /// Cancelling the task aborts the server session.
    func uploadAttachment(id: String, name: String, mime: String, data: Data, profile: String, progress: @escaping @Sendable (Int) -> Void) async throws -> Upload {
        guard data.count <= AppUploadPlan.maxBytes else { throw HermesError.server(String(localized: "Attachments are limited to 50 MB.")) }
        let session = try await openAppUpload(id: id, name: name, size: data.count, profile: profile)
        var offset = session.nextOffset
        do {
            for range in AppUploadPlan.chunks(size: data.count, maxChunkBytes: session.maxChunkBytes) where range.lowerBound >= offset {
                try Task.checkCancellation()
                offset = try await appendAppUploadChunk(id: session.id, offset: range.lowerBound, data: data.subdata(in: range), profile: profile)
                progress(offset)
            }
            try Task.checkCancellation()
            return try await completeAppUpload(id: session.id, fallbackName: name, mime: mime, profile: profile)
        } catch {
            try? await abortAppUpload(id: session.id, profile: profile)
            throw error
        }
    }

    static func errorDetailText(_ data: Data) -> String {
        if let json = try? JSONSerialization.jsonObject(with: data) as? JSON { return json.string("error", "message", "detail") }
        return String(data: data, encoding: .utf8)?.prefix(300).description ?? ""
    }
}
