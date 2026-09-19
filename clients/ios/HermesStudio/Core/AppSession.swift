import Foundation
import UIKit

/// Payload encoded in the pairing QR code that Core Hub shows under
/// App connections → "Create LAN pairing code".
struct AppConnectionQR: Equatable {
    static let expectedType = "hermes-studio.app-connection"

    let backendURL: String
    let machineID: String
    let authorizationCode: String
    let connectionType: String
    let expiresAt: Date?

    enum ParseError: LocalizedError, Equatable {
        case notJSON
        case wrongType(String)
        case missingField(String)
        case invalidURL(String)

        var errorDescription: String? {
            switch self {
            case .notJSON: return String(localized: "This QR code is not a Core Hub pairing code")
            case .wrongType: return String(localized: "This QR code is not a Core Hub pairing code")
            case let .missingField(field): return String(localized: "The pairing code is incomplete (missing \(field))")
            case let .invalidURL(url): return String(localized: "The pairing code points to an invalid server address: \(url)")
            }
        }
    }

    static func parse(_ text: String) throws -> AppConnectionQR {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let data = trimmed.data(using: .utf8),
              let raw = try? JSONSerialization.jsonObject(with: data),
              let json = raw as? JSON else { throw ParseError.notJSON }
        let type = json.string("type")
        guard type == expectedType else { throw ParseError.wrongType(type) }
        let backend = json.string("backend_url", "backendUrl")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard !backend.isEmpty else { throw ParseError.missingField("backend_url") }
        guard let url = URL(string: backend),
              let scheme = url.scheme?.lowercased(),
              scheme == "http" || scheme == "https",
              let host = url.host, !host.isEmpty else { throw ParseError.invalidURL(backend) }
        let code = json.string("authorization_code", "authorizationCode")
        guard !code.isEmpty else { throw ParseError.missingField("authorization_code") }
        return AppConnectionQR(
            backendURL: backend,
            machineID: json.string("machine_id", "machineId"),
            authorizationCode: code,
            connectionType: json.string("connection_type", "connectionType").nilIfEmpty ?? "lan",
            expiresAt: EpochDate.date(json["expires_at"] ?? json["expiresAt"])
        )
    }

    func isExpired(now: Date = Date()) -> Bool {
        guard let expiresAt else { return false }
        return expiresAt <= now
    }
}

/// Core Hub reports epoch timestamps in seconds; a few older payloads use
/// milliseconds. Accept both.
enum EpochDate {
    static func date(_ raw: Any?) -> Date? {
        let value: Double
        if let number = raw as? NSNumber { value = number.doubleValue }
        else if let text = raw as? String, let parsed = Double(text) { value = parsed }
        else { return nil }
        guard value > 0 else { return nil }
        return Date(timeIntervalSince1970: value >= 100_000_000_000 ? value / 1_000 : value)
    }
}

/// Decides when the app-connection token must be refreshed silently.
enum AppTokenRefreshPolicy {
    static let minimumRemaining: TimeInterval = 7 * 24 * 60 * 60
    static let maximumAge: TimeInterval = 24 * 60 * 60

    /// - Parameters:
    ///   - expiresAt: `token_expires_at` from the last app-login/app-refresh; `nil` when unknown.
    ///   - lastRefresh: when the token was last issued or refreshed; `nil` when unknown.
    ///   - now: the current time (injected for tests).
    static func shouldRefresh(expiresAt: Date?, lastRefresh: Date?, now: Date) -> Bool {
        guard let expiresAt else { return true }
        if expiresAt.timeIntervalSince(now) < minimumRemaining { return true }
        guard let lastRefresh else { return true }
        if lastRefresh > now { return true }
        return now.timeIntervalSince(lastRefresh) > maximumAge
    }
}

/// Everything the app keeps about a QR-paired connection. Stored as one
/// Keychain item so a refresh replaces all fields in a single write.
struct AppSessionRecord: Codable, Equatable {
    static let keychainKey = "appSession"

    var token: String
    var tokenExpiresAt: Date?
    var connectionID: Int
    var deviceCode: String
    var lastRefresh: Date?

    static func load() -> AppSessionRecord? {
        let raw = SecureStore.get(keychainKey)
        guard !raw.isEmpty, let data = raw.data(using: .utf8) else { return nil }
        return try? JSONDecoder().decode(AppSessionRecord.self, from: data)
    }

    func save() throws {
        let data = try JSONEncoder().encode(self)
        guard let text = String(data: data, encoding: .utf8) else { throw HermesError.malformedResponse }
        SecureStore.set(text, for: Self.keychainKey)
    }

    static func clear() { SecureStore.remove(keychainKey) }

    func needsRefresh(now: Date = Date()) -> Bool {
        AppTokenRefreshPolicy.shouldRefresh(expiresAt: tokenExpiresAt, lastRefresh: lastRefresh, now: now)
    }
}

/// Parsed response of `POST /api/auth/app-login` and `POST /api/auth/app-refresh`.
struct AppAuthResponse {
    let token: String
    let userID: Int
    let profiles: [String]
    let connection: JSON

    init(_ json: JSON) throws {
        guard let token = json.string("token").nilIfEmpty else {
            throw HermesError.server(String(localized: "Login succeeded but no token was returned"))
        }
        self.token = token
        userID = json.int("userId")
        profiles = json.strings("profiles")
        connection = json.object("appConnection")
    }

    var connectionID: Int { connection.int("id") }
    var tokenExpiresAt: Date? { EpochDate.date(connection["token_expires_at"]) }
}

/// Stable identity of this iPhone as seen by Core Hub's app connections.
enum DeviceIdentity {
    private static let deviceCodeKey = "appDeviceCode"

    /// A UUID generated once and kept in the Keychain so the same device
    /// keeps the same `device_code` across reinstalls and token refreshes.
    static var deviceCode: String {
        let existing = SecureStore.get(deviceCodeKey)
        if !existing.isEmpty { return existing }
        let fresh = UUID().uuidString.lowercased()
        SecureStore.set(fresh, for: deviceCodeKey)
        return fresh
    }

    /// Hardware model identifier such as `iPhone16,2`.
    static var modelIdentifier: String {
        var systemInfo = utsname()
        uname(&systemInfo)
        let capacity = MemoryLayout.size(ofValue: systemInfo.machine)
        let machine = withUnsafePointer(to: &systemInfo.machine) { pointer -> String in
            pointer.withMemoryRebound(to: CChar.self, capacity: capacity) { String(cString: $0) }
        }
        return machine.isEmpty ? "iPhone" : machine
    }

    @MainActor static var defaultName: String {
        let name = UIDevice.current.name.trimmingCharacters(in: .whitespacesAndNewlines)
        return name.isEmpty ? UIDevice.current.model : name
    }
}
