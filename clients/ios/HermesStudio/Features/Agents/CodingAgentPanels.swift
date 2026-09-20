import SwiftUI

/// DSH → Plugins (`DshPluginsPanel.vue`, `GET /api/coding-agents/dsh/plugin-inventory`):
/// the shipped presets with their plugin entries, and the web packages. The
/// phone reads the inventory; installing web packages and the plugin UI
/// session stay on the desktop, and the screen says so.
struct DshPluginsView: View {
    @EnvironmentObject private var store: AppStore
    @State private var inventory: JSON = [:]
    @State private var loading = true

    private var presets: [JSON] { inventory.objects("presets") }
    private var packages: [JSON] { inventory.object("web").objects("packages") }

    var body: some View {
        List {
            if !loading && presets.isEmpty && packages.isEmpty {
                Section { Text("No plugin inventory was returned.").foregroundStyle(.secondary) }
            }
            ForEach(presets.indices, id: \.self) { index in presetSection(presets[index]) }
            if !packages.isEmpty { packagesSection }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(NavDestination.plugins.title)
        .navigationBarTitleDisplayMode(.inline)
        .overlay { if loading && inventory.isEmpty { ProgressView() } }
        .task { await load() }
        .refreshable { await load() }
    }

    private func presetSection(_ preset: JSON) -> some View {
        Section {
            ForEach(preset.objects("entries").indices, id: \.self) { index in
                let entry = preset.objects("entries")[index]
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(entry.string("title").nilIfEmpty ?? entry.string("moduleName")).font(.headline)
                        if let description = entry.string("description").nilIfEmpty { Text(description).font(.caption).foregroundStyle(.secondary).lineLimit(2) }
                    }
                    Spacer()
                    StatusPill(text: DshPluginsView.enabledLabel(entry["configuredEnabled"]), color: entry.bool("configuredEnabled") ? .green : .gray)
                }
            }
        } header: {
            HStack(spacing: 6) {
                Text(preset.string("name").nilIfEmpty ?? preset.string("id"))
                if preset.bool("isDefault") { StatusPill(text: String(localized: "Default"), color: CoreHubTokens.Palette.accent) }
            }
        } footer: {
            if let error = preset.string("error").nilIfEmpty { Text(error).foregroundStyle(CoreHubTokens.Palette.error) }
        }
    }

    private var packagesSection: some View {
        Section {
            ForEach(packages.indices, id: \.self) { index in
                let package = packages[index]
                VStack(alignment: .leading, spacing: 2) {
                    Text(package.string("title").nilIfEmpty ?? package.string("name")).font(.headline)
                    TechnicalText(text: "\(package.string("name")) \(package.string("version"))")
                    if let error = package.string("error").nilIfEmpty { Text(error).font(.caption).foregroundStyle(CoreHubTokens.Palette.error) }
                }
            }
        } header: { Text("Web packages") } footer: {
            Text("Installing or removing web packages needs the desktop client.")
        }
    }

    /// `configuredEnabled` is `true`, `false` or the string `"conditional"`.
    static func enabledLabel(_ raw: Any?) -> String {
        if let text = raw as? String, text == "conditional" { return String(localized: "Conditional") }
        if let flag = raw as? Bool { return flag ? String(localized: "Enabled") : String(localized: "Disabled") }
        if let number = raw as? NSNumber { return number.boolValue ? String(localized: "Enabled") : String(localized: "Disabled") }
        return String(localized: "Disabled")
    }

    private func load() async {
        loading = true
        inventory = (await store.attempt({ try await store.api.dshPluginInventory() })) ?? inventory
        loading = false
    }
}

/// DSH → Presets (`DshAgentPresetsPanel.vue`), the `.presets` destination:
/// the agent presets with the default marked; a preset can be made the
/// default (a visible button, and the leading swipe) or, for a user copy,
/// deleted. Copying a preset and editing its file stay on the desktop, and
/// the footer says so.
struct DshPresetsView: View {
    @EnvironmentObject private var store: AppStore
    @State private var presets: [DshPreset] = []
    @State private var loading = true

    var body: some View {
        List {
            Section {
                if !loading && presets.isEmpty { Text("No presets.").foregroundStyle(.secondary) }
                ForEach(presets) { preset in presetRow(preset) }
            } footer: {
                Text("New dsh sessions start from the default preset. Choose it here; copying a preset and editing its file need the desktop client.")
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(NavDestination.presets.title)
        .navigationBarTitleDisplayMode(.inline)
        .overlay { if loading && presets.isEmpty { ProgressView() } }
        .task { await load() }
        .refreshable { await load() }
    }

    private func presetRow(_ preset: DshPreset) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(spacing: 6) {
                Text(preset.name).font(.headline)
                if preset.isDefault { StatusPill(text: String(localized: "Default"), color: CoreHubTokens.Palette.accent) }
                Spacer()
                Text(preset.trust).font(.caption).foregroundStyle(.secondary)
            }
            if !preset.description.isEmpty { Text(preset.description).font(.caption).foregroundStyle(.secondary).lineLimit(3) }
            if !preset.broken.isEmpty { Text(preset.broken).font(.caption).foregroundStyle(CoreHubTokens.Palette.error) }
            if !preset.isDefault && preset.broken.isEmpty {
                Button("Use as default") { Task { await makeDefault(preset) } }
                    .buttonStyle(.borderless)
                    .font(.caption)
            }
        }
        .swipeActions(edge: .leading) {
            if !preset.isDefault && preset.broken.isEmpty {
                Button { Task { await makeDefault(preset) } } label: { Label("Make default", systemImage: "star") }.tint(.orange)
            }
        }
        .swipeActions(edge: .trailing) {
            if preset.trust == "user" {
                Button(role: .destructive) { Task { await store.attempt({ try await store.api.deleteDshPreset(preset.id) }); await load() } } label: { Label("Delete", systemImage: "trash") }
            }
        }
    }

    private func makeDefault(_ preset: DshPreset) async {
        await store.attempt({ try await store.api.setDefaultDshPreset(preset.id) })
        await load()
    }

    private func load() async {
        loading = true
        presets = (await store.attempt({ try await store.api.dshAgentPresets() })) ?? presets
        loading = false
    }
}
