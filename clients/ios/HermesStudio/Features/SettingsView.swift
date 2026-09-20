import SwiftUI
import PhotosUI

/// The one Settings screen (`NAVIGATION.md` §2): the desktop tabs in order
/// (`SettingsView.vue:104-129`) — Current Account, Account Management (sa),
/// Webhooks (sa), Display, Proxy, Compression, Privacy, Models — then the
/// two phone-only tabs `This device` and `About`, and the `Tools` section
/// that mirrors `AppSidebar.vue:113-317`. Agent runtime settings and agent
/// capabilities are **not** here; they live under the agent (§4).
struct SettingsView: View {
    @EnvironmentObject private var store: AppStore

    var body: some View {
        List {
            accountSection
            if store.isSuperAdmin { adminSection }
            tabsSection
            phoneSection
            toolsSection
        }
        .listStyle(.insetGrouped)
        .navigationTitle(NavDestination.settings.title)
        .navigationBarTitleDisplayMode(.inline)
        // Rebuild this visible list after UIKit's direction transform has
        // settled (see AppStore.setLanguage).
        .id("\(store.language)-\(store.languageRefresh)")
    }

    private var accountSection: some View {
        Section("Current Account") {
            NavigationLink { AccountView() } label: {
                HStack(spacing: 13) {
                    ProfileAvatar(name: store.currentUser?.username ?? "Account", avatar: store.currentUser?.avatar, size: 48)
                    VStack(alignment: .leading, spacing: 3) {
                        Text(store.currentUser?.username ?? "Account").font(.headline)
                        Text(store.currentUser?.role.replacingOccurrences(of: "_", with: " ").capitalized ?? "").font(.caption).foregroundStyle(.secondary)
                    }
                }
            }
            Button(role: .destructive) { store.signOut() } label: { Label("Sign Out", systemImage: "rectangle.portrait.and.arrow.right") }
        }
    }

    private var adminSection: some View {
        Group {
            Section("Account Management") {
                NavigationLink { AccountManagementView() } label: { SettingsRow(icon: "person.2.badge.gearshape", color: CoreHubTokens.Palette.info, title: "Account Management") }
            }
            Section("Webhooks") {
                NavigationLink { WebhooksView() } label: { SettingsRow(icon: "arrow.triangle.branch", color: CoreHubTokens.Palette.warning, title: "Webhooks") }
            }
        }
    }

    private var tabsSection: some View {
        Group {
            Section("Display") {
                NavigationLink { DisplaySettingsView() } label: { SettingsRow(icon: "rectangle.on.rectangle", color: CoreHubTokens.Palette.info, title: "Display") }
            }
            Section("Proxy") { NavigationLink { StudioSectionSettings(section: .proxy) } label: { SettingsRow(icon: "network", color: CoreHubTokens.Palette.info, title: "Proxy") } }
            Section("Compression") { NavigationLink { StudioSectionSettings(section: .compression) } label: { SettingsRow(icon: "arrow.down.right.and.arrow.up.left", color: CoreHubTokens.Palette.warning, title: "Compression") } }
            Section("Privacy") { NavigationLink { StudioSectionSettings(section: .privacy) } label: { SettingsRow(icon: "hand.raised.fill", color: CoreHubTokens.Palette.error, title: "Privacy") } }
            Section("Models") {
                // `ModelSettings.vue`: provider keys only. The Models page is
                // the rail entry; the row says so to avoid the mix-up.
                NavigationLink { ProviderKeysSettingsView() } label: { SettingsRow(icon: "key.fill", color: CoreHubTokens.Palette.accent, title: "Models", subtitle: String(localized: "Provider keys")) }
            }
        }
    }

    private var phoneSection: some View {
        Group {
            Section("This device") {
                NavigationLink { ThisDeviceSettingsView() } label: { SettingsRow(icon: "iphone", color: CoreHubTokens.Palette.success, title: "This device", subtitle: store.baseURL) }
            }
            Section("About") {
                NavigationLink { AboutView() } label: { SettingsRow(icon: "info.circle.fill", color: CoreHubTokens.Palette.info, title: "About") }
            }
        }
    }

    /// `AppSidebar.vue:113-317`, one different screen per row.
    private var toolsSection: some View {
        Section("Tools") {
            ForEach(NavDestination.tools) { destination in
                if !NavDestination.superAdminOnly.contains(destination) || store.isSuperAdmin {
                    NavigationLink(value: destination) {
                        SettingsRow(icon: SettingsView.symbol(for: destination), color: SettingsView.color(for: destination), title: destination.label) { EmptyView() }
                    }
                }
            }
        }
    }

    static func symbol(for destination: NavDestination) -> String {
        switch destination {
        case .logs: return "doc.text.magnifyingglass"
        case .usage: return "chart.bar.xaxis"
        case .performance: return "gauge.with.dots.needle.33percent"
        case .skillsUsage: return "square.stack.3d.up"
        case .theme: return "paintpalette.fill"
        case .pets: return "pawprint.fill"
        case .profiles: return "person.2.fill"
        default: return "circle"
        }
    }

    static func color(for destination: NavDestination) -> Color {
        switch destination {
        case .logs, .skillsUsage: return CoreHubTokens.Palette.info
        case .usage, .performance: return CoreHubTokens.Palette.success
        case .theme, .profiles: return CoreHubTokens.Palette.accent
        default: return CoreHubTokens.Palette.warning
        }
    }
}

// MARK: - Current Account

private struct AccountView: View {
    @EnvironmentObject private var store: AppStore
    @State private var changePassword = false
    @State private var changeUsername = false
    @State private var photo: PhotosPickerItem?

    var body: some View {
        List {
            Section {
                HStack(spacing: 15) {
                    ProfileAvatar(name: store.currentUser?.username ?? "Account", avatar: store.currentUser?.avatar, size: 68)
                    VStack(alignment: .leading) { Text(store.currentUser?.username ?? "").font(.title3.bold()); Text(store.currentUser?.role.capitalized ?? "").foregroundStyle(.secondary) }
                }
            }
            Section("Security") {
                Button { changeUsername = true } label: { SettingsRow(icon: "person.text.rectangle", color: .blue, title: "Change username") }
                Button { changePassword = true } label: { SettingsRow(icon: "key.fill", color: .orange, title: "Change password") }
            }
            Section("Avatar") {
                PhotosPicker(selection: $photo, matching: .images) { SettingsRow(icon: "photo.fill", color: .purple, title: "Upload image") }
                    .onChange(of: photo) { _, item in Task { await upload(item) } }
                Button { Task { await store.attempt({ try await store.api.resetAvatar() }); store.currentUser = await store.attempt({ try await store.api.currentUser() }) } } label: { SettingsRow(icon: "arrow.counterclockwise", color: .gray, title: "Reset avatar") }
            }
        }
        .navigationTitle("Current Account")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $changePassword) { CredentialChangeView(kind: .password) }
        .sheet(isPresented: $changeUsername) { CredentialChangeView(kind: .username) }
    }

    private func upload(_ item: PhotosPickerItem?) async {
        guard let data = try? await item?.loadTransferable(type: Data.self), let mime = item?.supportedContentTypes.first?.preferredMIMEType else { return }
        do {
            try await store.api.updateAvatar(dataURL: "data:\(mime);base64,\(data.base64EncodedString())")
            store.currentUser = try await store.api.currentUser()
        } catch { store.errorMessage = error.localizedDescription }
    }
}

private struct CredentialChangeView: View {
    enum Kind { case password, username }
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    let kind: Kind
    @State private var current = ""; @State private var value = ""; @State private var confirm = ""; @State private var saving = false

    var body: some View {
        NavigationStack {
            Form {
                SecureField("Current password", text: $current)
                if kind == .password { SecureField("New password", text: $value); SecureField("Confirm password", text: $confirm) } else { TextField("New username", text: $value).textInputAutocapitalization(.never) }
            }
            .navigationTitle(kind == .password ? "Change password" : "Change username")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Save") { Task { await save() } }.disabled(current.isEmpty || value.isEmpty || (kind == .password && value != confirm) || saving) }
            }
        }
    }

    private func save() async {
        saving = true
        do {
            if kind == .password { try await store.api.changePassword(current: current, new: value) } else { try await store.api.changeUsername(currentPassword: current, newUsername: value); store.currentUser = try await store.api.currentUser() }
            store.notify(String(localized: "Account updated")); dismiss()
        } catch { store.errorMessage = error.localizedDescription }
        saving = false
    }
}

// MARK: - Profiles (Settings → Tools → Profiles, super-admin)

struct ProfilesView: View {
    @EnvironmentObject private var store: AppStore
    @State private var creating = false; @State private var cloning = false; @State private var newName = ""; @State private var renaming: Profile?; @State private var renameText = ""; @State private var runtimes: [ProfileRuntime] = []

    var body: some View {
        List { ForEach(store.profiles) { profile in profileRow(profile) } }
            .listStyle(.insetGrouped)
            .navigationTitle(NavDestination.profiles.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { Menu { Button("New profile") { cloning = false; creating = true }; Button("Clone active profile") { cloning = true; creating = true } } label: { Image(systemName: "plus") } }
            .refreshable { await store.refreshProfiles(); await loadRuntimes() }
            .task { await loadRuntimes() }
            .alert(cloning ? "Clone active profile" : "New profile", isPresented: $creating) {
                TextField("Name", text: $newName)
                Button(cloning ? "Clone" : "Create") { Task { do { if cloning { try await store.api.cloneProfile(newName) } else { try await store.api.createProfile(newName) }; await store.refreshProfiles(); newName = "" } catch { store.errorMessage = error.localizedDescription } } }
                Button("Cancel", role: .cancel) {}
            }
            .alert("Rename profile", isPresented: Binding(get: { renaming != nil }, set: { if !$0 { renaming = nil } })) {
                TextField("Name", text: $renameText)
                Button("Save") { guard let renaming else { return }; Task { do { try await store.api.renameProfile(renaming.name, to: renameText); await store.refreshProfiles() } catch { store.errorMessage = error.localizedDescription } } }
                Button("Cancel", role: .cancel) {}
            }
    }

    private func profileRow(_ profile: Profile) -> some View {
        NavigationLink { ProfileDetailView(profile: profile) } label: {
            HStack(spacing: 13) {
                ProfileAvatar(name: profile.name, avatar: profile.avatar, size: 45)
                VStack(alignment: .leading, spacing: 3) {
                    Text(profile.name).font(.headline).foregroundStyle(.primary)
                    Text(profile.model ?? "Default model").font(.caption).foregroundStyle(.secondary)
                    if let runtime = runtimes.first(where: { $0.id == profile.name }) {
                        Text(runtime.bridgeRunning ? "Runtime running" : "Runtime stopped").font(.caption2).foregroundStyle(runtime.bridgeRunning ? .green : .secondary)
                    }
                }
                Spacer()
                if profile.name == store.selectedProfile { Image(systemName: "checkmark.circle.fill").foregroundStyle(CoreHubTokens.Palette.accent) }
            }
            .padding(.vertical, 3)
        }
        .swipeActions(edge: .leading) {
            Button { renameText = profile.name; renaming = profile } label: { Label("Rename", systemImage: "pencil") }.tint(.blue)
            Button { Task { await store.attempt({ try await store.api.restartProfileRuntime(profile.name) }); await loadRuntimes() } } label: { Label("Restart runtime", systemImage: "arrow.clockwise") }.tint(.orange)
        }
        .swipeActions(edge: .trailing) {
            if store.profiles.count > 1 {
                Button(role: .destructive) { Task { await store.attempt({ try await store.api.deleteProfile(profile.name) }); await store.refreshProfiles() } } label: { Label("Delete", systemImage: "trash") }
            }
        }
    }

    private func loadRuntimes() async { runtimes = (await store.attempt({ try await store.api.profileRuntimes() })) ?? runtimes }
}

/// One profile card (`ProfileCard.vue`). "Edit config" is the only way to
/// the Files screen (`ProfileCard.vue:100-107`).
private struct ProfileDetailView: View {
    @EnvironmentObject private var store: AppStore
    let profile: Profile
    @State private var runtime: ProfileRuntime?
    @State private var photo: PhotosPickerItem?
    @State private var importing = false
    @State private var exportedURL: URL?

    var body: some View {
        List {
            Section { HStack(spacing: 14) { ProfileAvatar(name: profile.name, avatar: profile.avatar, size: 64); VStack(alignment: .leading) { Text(profile.name).font(.title3.bold()); Text(profile.model ?? "Default model").foregroundStyle(.secondary) } } }
            runtimeSection
            Section {
                Button(profile.name == store.selectedProfile ? "Active profile" : "Make active profile") { Task { do { try await store.api.activateProfile(profile.name); store.chooseProfile(profile.name); await store.refreshProfiles() } catch { store.errorMessage = error.localizedDescription } } }
                    .disabled(profile.name == store.selectedProfile)
                Button { store.chooseProfile(profile.name); store.push(.files) } label: { Label("Edit config", systemImage: "folder.fill") }
            }
            Section("Avatar") {
                PhotosPicker(selection: $photo, matching: .images) { Label("Upload profile avatar", systemImage: "photo") }.onChange(of: photo) { _, item in Task { await upload(item) } }
                Button("Reset profile avatar", role: .destructive) { Task { await store.attempt({ try await store.api.resetProfileAvatar(profile.name) }); await store.refreshProfiles() } }
            }
            Section("Backup") {
                Button("Export profile") { Task { await export() } }
                if let exportedURL { ShareLink(item: exportedURL) { Label("Share exported archive", systemImage: "square.and.arrow.up") } }
                Button("Import profile archive") { importing = true }
            }
        }
        .navigationTitle(profile.name)
        .task { await load() }
        .refreshable { await load() }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.data]) { result in if case let .success(url) = result { Task { await importArchive(url) } } }
    }

    private var runtimeSection: some View {
        Section("Runtime status") {
            LabeledContent("Bridge") { StatusPill(text: runtime?.bridgeRunning == true ? String(localized: "Running") : String(localized: "Stopped"), color: runtime?.bridgeRunning == true ? .green : .gray) }
            LabeledContent("Gateway") { StatusPill(text: runtime?.gatewayRunning == true ? String(localized: "Running") : String(localized: "Stopped"), color: runtime?.gatewayRunning == true ? .green : .gray) }
            if let url = runtime?.gatewayURL.nilIfEmpty { Text(url).font(.caption.monospaced()).textSelection(.enabled) }
            Button("Restart runtime") { Task { await store.attempt({ try await store.api.restartProfileRuntime(profile.name) }); await load() } }
            Button("Restart gateway") { Task { await store.attempt({ try await store.api.restartGateway(profile: profile.name) }); await load() } }
        }
    }

    private func load() async { if let values = await store.attempt({ try await store.api.profileRuntimes() }) { runtime = values.first { $0.id == profile.name } } }
    private func upload(_ item: PhotosPickerItem?) async {
        guard let data = try? await item?.loadTransferable(type: Data.self), let mime = item?.supportedContentTypes.first?.preferredMIMEType else { return }
        do { try await store.api.setProfileAvatar(profile.name, dataURL: "data:\(mime);base64,\(data.base64EncodedString())"); await store.refreshProfiles() } catch { store.errorMessage = error.localizedDescription }
    }
    private func export() async {
        do {
            let data = try await store.api.exportProfile(profile.name)
            let url = FileManager.default.temporaryDirectory.appendingPathComponent("hermes-profile-\(profile.name).tar.gz")
            try data.write(to: url, options: .atomic)
            exportedURL = url
        } catch { store.errorMessage = error.localizedDescription }
    }
    private func importArchive(_ url: URL) async {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        do { try await store.api.importProfile(data: Data(contentsOf: url), name: url.lastPathComponent); await store.refreshProfiles(); store.notify(String(localized: "Profile imported")) } catch { store.errorMessage = error.localizedDescription }
    }
}
