import CoreLocation
import Foundation

/// Builds the `location.respond` payload (unit-tested).
enum LocationResponsePayload {
    static func success(sessionID: String, requestID: String, latitude: Double, longitude: Double, accuracyMeters: Double, timestamp: Date, altitudeMeters: Double? = nil, speedMetersPerSecond: Double? = nil) -> JSON {
        var location: JSON = [
            "latitude": latitude,
            "longitude": longitude,
            "accuracyMeters": max(0, accuracyMeters),
            "coordinateSystem": "wgs84",
            "timestamp": Int(timestamp.timeIntervalSince1970 * 1000),
        ]
        if let altitudeMeters { location["altitudeMeters"] = altitudeMeters }
        if let speedMetersPerSecond, speedMetersPerSecond >= 0 { location["speedMetersPerSecond"] = speedMetersPerSecond }
        return ["session_id": sessionID, "location_request_id": requestID, "status": "success", "location": location]
    }

    static func denied(sessionID: String, requestID: String) -> JSON {
        ["session_id": sessionID, "location_request_id": requestID, "status": "denied"]
    }

    static func error(sessionID: String, requestID: String, code: String, message: String) -> JSON {
        ["session_id": sessionID, "location_request_id": requestID, "status": "error", "error": ["code": code, "message": message]]
    }
}

/// One-shot CoreLocation reader (`NSLocationWhenInUseUsageDescription`).
@MainActor
final class LocationReader: NSObject, CLLocationManagerDelegate {
    enum Failure: LocalizedError {
        case denied
        case unavailable
        case timeout

        var errorDescription: String? {
            switch self {
            case .denied: return String(localized: "Location access was denied. Enable it in Settings → Core Hub → Location.")
            case .unavailable: return String(localized: "Location services are unavailable on this device.")
            case .timeout: return String(localized: "The location could not be determined in time.")
            }
        }
        var code: String {
            switch self {
            case .denied: return "permission_denied"
            case .unavailable: return "location_unavailable"
            case .timeout: return "location_timeout"
            }
        }
    }

    private let manager = CLLocationManager()
    private var continuation: CheckedContinuation<CLLocation, Error>?
    private var authorizationContinuation: CheckedContinuation<CLAuthorizationStatus, Never>?

    override init() {
        super.init()
        manager.delegate = self
    }

    func read(precise: Bool, timeout: TimeInterval) async throws -> CLLocation {
        guard CLLocationManager.locationServicesEnabled() else { throw Failure.unavailable }
        var status = manager.authorizationStatus
        if status == .notDetermined {
            status = await withCheckedContinuation { continuation in
                authorizationContinuation = continuation
                manager.requestWhenInUseAuthorization()
            }
        }
        guard status == .authorizedWhenInUse || status == .authorizedAlways else { throw Failure.denied }
        manager.desiredAccuracy = precise ? kCLLocationAccuracyBest : kCLLocationAccuracyHundredMeters
        let timer = Task { [weak self] in
            try? await Task.sleep(for: .seconds(max(1, timeout)))
            guard !Task.isCancelled else { return }
            self?.fail(Failure.timeout)
        }
        defer { timer.cancel() }
        return try await withCheckedThrowingContinuation { continuation in
            self.continuation = continuation
            manager.requestLocation()
        }
    }

    private func fail(_ error: Error) {
        manager.stopUpdatingLocation()
        continuation?.resume(throwing: error); continuation = nil
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        Task { @MainActor in
            guard status != .notDetermined else { return }
            self.authorizationContinuation?.resume(returning: status); self.authorizationContinuation = nil
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        Task { @MainActor in
            self.continuation?.resume(returning: location); self.continuation = nil
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in
            let failure: Error = (error as? CLError)?.code == .denied ? Failure.denied : error
            self.continuation?.resume(throwing: failure); self.continuation = nil
        }
    }
}
