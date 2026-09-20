import SwiftUI

/// Settings → Tools → Performance (super-admin): the runtime processes of
/// the Core Hub host (`views/hermes/PerformanceView.vue`,
/// `GET /api/studio/performance/runtime`) — system CPU and memory, the
/// bridge broker and its workers, and the live sessions per profile.
struct PerformanceView: View {
    @EnvironmentObject private var store: AppStore
    @State private var snapshot: PerformanceSnapshot?
    @State private var loading = true
    @State private var autoRefresh = false

    var body: some View {
        List {
            if let snapshot {
                summary(snapshot)
                bridge(snapshot)
                workers(snapshot)
                sessions(snapshot)
            } else if !loading {
                Section { Text("Performance metrics could not be read.").foregroundStyle(.secondary) }
            }
        }
        .navigationTitle(NavDestination.performance.title)
        .navigationBarTitleDisplayMode(.inline)
        .overlay { if loading && snapshot == nil { ProgressView() } }
        .refreshable { await load() }
        .task { await load() }
        .task(id: autoRefresh) {
            guard autoRefresh else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(5))
                guard !Task.isCancelled else { return }
                await load()
            }
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Toggle(isOn: $autoRefresh) { Text("Auto refresh") }.toggleStyle(.button)
            }
        }
    }

    private func summary(_ snapshot: PerformanceSnapshot) -> some View {
        Section {
            UsageMetricRow(title: "System CPU", value: PerformanceFormat.percent(snapshot.cpuPercent), icon: "cpu", color: .mint)
            UsageMetricRow(title: "System memory", value: PerformanceFormat.percent(snapshot.memoryPercent), icon: "memorychip.fill", color: .pink)
            UsageMetricRow(title: "Active sessions", value: "\(snapshot.activeSessions)", icon: "waveform.path.ecg", color: .indigo)
            UsageMetricRow(title: "Workers", value: "\(snapshot.runningWorkers)/\(snapshot.workers.count)", icon: "point.3.connected.trianglepath.dotted", color: .cyan)
        } header: { Text("Overview") } footer: {
            Text(verbatim: "\(snapshot.platform) \(snapshot.arch) · \(snapshot.cpuCount) CPU · \(PerformanceFormat.duration(snapshot.uptimeSeconds))")
                .technicalDirection()
        }
    }

    private func bridge(_ snapshot: PerformanceSnapshot) -> some View {
        Section("Bridge") {
            LabeledContent("Reachable") { StatusPill(text: snapshot.bridgeReachable ? String(localized: "Yes") : String(localized: "No"), color: snapshot.bridgeReachable ? .green : .red) }
            LabeledContent("Broker") { StatusPill(text: snapshot.brokerRunning ? String(localized: "Running") : String(localized: "Stopped"), color: snapshot.brokerRunning ? .green : .gray) }
            LabeledContent("Worker memory") { Text(PerformanceFormat.bytes(snapshot.totalWorkerMemoryBytes)) }
            if !snapshot.bridgeError.isEmpty { Text(snapshot.bridgeError).font(.caption).foregroundStyle(CoreHubTokens.Palette.error) }
        }
    }

    private func workers(_ snapshot: PerformanceSnapshot) -> some View {
        Section("Processes") {
            if snapshot.workers.isEmpty { Text("No workers are running.").foregroundStyle(.secondary) }
            ForEach(snapshot.workers) { worker in PerformanceWorkerRow(worker: worker) }
        }
    }

    private func sessions(_ snapshot: PerformanceSnapshot) -> some View {
        Section("Sessions by profile") {
            if snapshot.sessionsByProfile.isEmpty { Text("No active sessions.").foregroundStyle(.secondary) }
            ForEach(snapshot.sessionsByProfile, id: \.profile) { entry in
                LabeledContent(entry.profile, value: "\(entry.count)")
            }
        }
    }

    private func load() async {
        loading = true
        snapshot = (await store.attempt({ try await store.api.performanceSnapshot() })) ?? snapshot
        loading = false
    }
}

struct PerformanceWorkerRow: View {
    let worker: PerformanceSnapshot.Worker

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(worker.profile.nilIfEmpty ?? String(localized: "Worker")).font(.headline)
                Spacer()
                StatusPill(text: worker.running ? String(localized: "Running") : String(localized: "Stopped"), color: worker.running ? .green : .gray)
            }
            Text(verbatim: "PID \(worker.pid) · \(PerformanceFormat.percent(worker.cpuPercent)) · \(PerformanceFormat.bytes(worker.memoryBytes))")
                .font(.caption).foregroundStyle(.secondary).technicalDirection()
            Text("\(worker.runningSessionCount) running of \(worker.sessionCount) sessions").font(.caption2).foregroundStyle(.secondary)
            if !worker.error.isEmpty { Text(worker.error).font(.caption2).foregroundStyle(CoreHubTokens.Palette.error) }
        }
        .padding(.vertical, 2)
    }
}

enum PerformanceFormat {
    static func percent(_ value: Double?) -> String {
        guard let value else { return "—" }
        return String(format: "%.1f%%", value)
    }

    static func bytes(_ value: Int) -> String {
        ByteCountFormatter.string(fromByteCount: Int64(value), countStyle: .memory)
    }

    /// `1d 2h 3m` from seconds, the web's `formatDuration`.
    static func duration(_ seconds: Double) -> String {
        let total = Int(max(0, seconds))
        let days = total / 86_400, hours = (total % 86_400) / 3_600, minutes = (total % 3_600) / 60
        if days > 0 { return "\(days)d \(hours)h" }
        if hours > 0 { return "\(hours)h \(minutes)m" }
        return "\(minutes)m"
    }
}
