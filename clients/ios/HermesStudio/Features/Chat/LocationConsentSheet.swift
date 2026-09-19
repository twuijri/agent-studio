import SwiftUI

extension LocationRequest: Identifiable {}

/// Consent sheet for `location.requested`: shows the agent's purpose and the
/// requested accuracy; sharing reads one CoreLocation fix and answers
/// `location.respond`, declining answers `denied`.
struct LocationConsentSheet: View {
    let request: LocationRequest
    let onDecision: (LocationDecision) -> Void
    @State private var working = false
    @State private var failure: String?
    @State private var reader = LocationReader()

    enum LocationDecision {
        case share(JSON)
        case deny
        case error(JSON)
    }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                HStack(spacing: 10) {
                    Image(systemName: "location.circle.fill").font(.system(size: 28)).foregroundStyle(CoreHubTokens.Palette.info)
                    Text("The agent is asking for your location").font(CoreHubTokens.Typography.titleFont)
                }
                if !request.purpose.isEmpty {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Purpose").font(CoreHubTokens.Typography.groupHeaderFont).foregroundStyle(CoreHubTokens.Palette.textMuted)
                        DirectionalText(text: request.purpose, font: CoreHubTokens.Typography.messageFont, lineLimit: nil)
                    }
                }
                Text(request.accuracy == "precise" ? "Precise location (GPS accuracy)" : "Approximate location (about 100 m)")
                    .font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.sidebarTab))
                    .foregroundStyle(CoreHubTokens.Palette.textSecondary)
                Text("Your location is sent once, only to this conversation, and only if you tap Share.")
                    .font(CoreHubTokens.Typography.metaFont)
                    .foregroundStyle(CoreHubTokens.Palette.textMuted)
                if let failure { Text(failure).font(CoreHubTokens.Typography.metaFont).foregroundStyle(CoreHubTokens.Palette.error) }
                Spacer()
                Button { Task { await share() } } label: {
                    HStack { if working { ProgressView().controlSize(.small).tint(CoreHubTokens.Palette.textOnAccent) }; Text("Share location") }
                        .font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.base, weight: .semibold))
                        .foregroundStyle(CoreHubTokens.Palette.textOnAccent)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(CoreHubTokens.Palette.accent, in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.control))
                }
                .buttonStyle(.plain)
                .disabled(working)
                Button { onDecision(.deny) } label: {
                    Text("Don't share")
                        .font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.base, weight: .medium))
                        .foregroundStyle(CoreHubTokens.Palette.textPrimary)
                        .frame(maxWidth: .infinity, minHeight: 44)
                        .background(CoreHubTokens.Palette.bgSecondary, in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.control))
                }
                .buttonStyle(.plain)
                .disabled(working)
            }
            .padding(20)
            .navigationTitle("Location request")
            .navigationBarTitleDisplayMode(.inline)
        }
        .presentationDetents([.medium])
        .interactiveDismissDisabled(working)
    }

    private func share() async {
        working = true; failure = nil
        defer { working = false }
        do {
            let location = try await reader.read(precise: request.accuracy == "precise", timeout: Double(request.timeoutMs) / 1000)
            let payload = LocationResponsePayload.success(
                sessionID: request.sessionID, requestID: request.id,
                latitude: location.coordinate.latitude, longitude: location.coordinate.longitude,
                accuracyMeters: location.horizontalAccuracy, timestamp: location.timestamp,
                altitudeMeters: location.verticalAccuracy >= 0 ? location.altitude : nil,
                speedMetersPerSecond: location.speed >= 0 ? location.speed : nil
            )
            onDecision(.share(payload))
        } catch let error as LocationReader.Failure {
            failure = error.localizedDescription
            onDecision(.error(LocationResponsePayload.error(sessionID: request.sessionID, requestID: request.id, code: error.code, message: error.localizedDescription)))
        } catch {
            failure = error.localizedDescription
            onDecision(.error(LocationResponsePayload.error(sessionID: request.sessionID, requestID: request.id, code: "location_error", message: error.localizedDescription)))
        }
    }
}
