import SwiftUI

/// Settings → Tools → Usage: token usage per period, model, agent and day
/// (`views/hermes/UsageView.vue`). Runtime processes are a different screen,
/// `PerformanceView`; the two used to share one "Insights" list.
struct UsageView: View {
    @EnvironmentObject private var store: AppStore
    @State private var days = 30
    @State private var usage: UsageStats?
    @State private var loading = true

    var body: some View {
        List {
            Section {
                Picker("Period", selection: $days) {
                    ForEach([7, 30, 90, 365], id: \.self) { Text("\($0) days").tag($0) }
                }.pickerStyle(.segmented)
            }
            if loading { Section { HStack { Spacer(); ProgressView(); Spacer() } } }
            if let usage {
                totals(usage)
                models(usage)
                agents(usage)
                daily(usage)
            } else if !loading {
                Section { Text("No usage data yet.").foregroundStyle(.secondary) }
            }
        }
        .navigationTitle(NavDestination.usage.title)
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await load() }
        .task(id: days) { await load() }
    }

    private func totals(_ usage: UsageStats) -> some View {
        Section("Totals") {
            UsageMetricRow(title: "Tokens", value: UsageFormat.compact(usage.inputTokens + usage.outputTokens), icon: "number.square.fill", color: .purple)
            UsageMetricRow(title: "Sessions", value: "\(usage.sessions)", icon: "bubble.left.and.bubble.right.fill", color: .blue)
            UsageMetricRow(title: "Estimated cost", value: usage.cost.formatted(.currency(code: "USD").precision(.fractionLength(4))), icon: "dollarsign.circle.fill", color: .green)
            UsageMetricRow(title: "Cache tokens", value: UsageFormat.compact(usage.cacheTokens), icon: "bolt.horizontal.fill", color: .orange)
        }
    }

    @ViewBuilder private func models(_ usage: UsageStats) -> some View {
        if !usage.models.isEmpty {
            Section("By model") {
                ForEach(usage.models.prefix(8)) { model in
                    LabeledContent { Text(UsageFormat.compact(model.totalTokens)).fontWeight(.semibold) } label: {
                        VStack(alignment: .leading) { TechnicalText(text: model.name); Text("\(model.sessions) sessions").font(.caption).foregroundStyle(.secondary) }
                    }
                }
            }
        }
    }

    @ViewBuilder private func agents(_ usage: UsageStats) -> some View {
        if !usage.agents.isEmpty {
            Section("By agent") {
                ForEach(usage.agents) { agent in
                    LabeledContent { Text(UsageFormat.compact(agent.totalTokens)).fontWeight(.semibold) } label: {
                        VStack(alignment: .leading) { Text(AgentIdentity.displayName(for: agent.name)); Text("\(agent.sessions) sessions").font(.caption).foregroundStyle(.secondary) }
                    }
                }
            }
        }
    }

    @ViewBuilder private func daily(_ usage: UsageStats) -> some View {
        if !usage.daily.isEmpty {
            Section("Daily activity") {
                ForEach(usage.daily.suffix(14)) { day in
                    LabeledContent(day.name) {
                        VStack(alignment: .trailing) { Text(UsageFormat.compact(day.totalTokens)).fontWeight(.semibold); Text("\(day.sessions) sessions").font(.caption2).foregroundStyle(.secondary) }
                    }
                }
            }
        }
    }

    private func load() async {
        loading = true
        usage = (await store.attempt({ try await store.api.usageStats(days: days) })) ?? usage
        loading = false
    }
}

struct UsageMetricRow: View {
    let title: LocalizedStringKey
    let value: String
    let icon: String
    let color: Color

    var body: some View {
        HStack {
            Image(systemName: icon).foregroundStyle(color).frame(width: 24)
            Text(title)
            Spacer()
            Text(value).fontWeight(.bold).foregroundStyle(CoreHubTokens.Palette.accent).technicalDirection()
        }
    }
}

enum UsageFormat {
    /// `1.2K`, `3.4M`, or the plain number.
    static func compact(_ value: Int) -> String {
        if value >= 1_000_000 { return String(format: "%.1fM", Double(value) / 1_000_000) }
        if value >= 1_000 { return String(format: "%.1fK", Double(value) / 1_000) }
        return "\(value)"
    }
}
