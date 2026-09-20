import SwiftUI
import CoreImage.CIFilterBuiltins

/// The `Device connections` rail entry — `ConnectionsPanel.vue:85-90`: tab
/// `App` (sub-tabs Direct / Message push, `PersonalConnectionsPanel.vue`)
/// and tab `Devices` for the super-admin (`DevicesView.vue`). This is the
/// screen's only entry; it used to be reachable from Settings as well under
/// a different name.
struct DeviceConnectionsView: View {
    @EnvironmentObject private var store: AppStore
    @State private var tab = "app"
    @State private var appTab = "direct"

    private var tabs: [TabStripItem] {
        var items = [TabStripItem(id: "app", title: String(localized: "App"))]
        if store.isSuperAdmin { items.append(TabStripItem(id: "devices", title: String(localized: "Devices"))) }
        return items
    }

    var body: some View {
        VStack(spacing: 0) {
            TabStrip(items: tabs, selection: $tab)
            if tab == "devices" && store.isSuperAdmin {
                StudioDevicesPanel()
            } else {
                TabStrip(items: [TabStripItem(id: "direct", title: String(localized: "Direct")), TabStripItem(id: "messages", title: String(localized: "Message push"))], selection: $appTab, secondary: true)
                if appTab == "messages" { AppRelayPanel() } else { AppConnectionsPanel() }
            }
        }
        .background(CoreHubTokens.Palette.bgPrimary)
        .navigationTitle(NavDestination.deviceConnections.title)
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// App → Direct: the phones paired with this account over the LAN or the
/// cloud, and a code to pair one more.
struct AppConnectionsPanel: View {
    @EnvironmentObject private var store: AppStore
    @State private var connections: [AppConnectionItem] = []
    @State private var authorization = ""
    @State private var loading = true

    var body: some View {
        List {
            Section("Pair a device") {
                Button("Create LAN pairing code") { Task { await authorize(cloud: false) } }
                Button("Create cloud matching code") { Task { await authorize(cloud: true) } }
                if !authorization.isEmpty {
                    Text(authorization).font(.title3.monospaced()).textSelection(.enabled)
                    QRImageView(value: authorization).frame(maxWidth: .infinity)
                }
            }
            Section("Paired devices") {
                if !loading && connections.isEmpty { Text("No paired devices yet.").foregroundStyle(.secondary) }
                ForEach(connections) { item in
                    HStack {
                        VStack(alignment: .leading) { Text(item.name); Text("\(item.model) · \(item.type)").font(.caption).foregroundStyle(.secondary) }
                        Spacer()
                        StatusPill(text: item.online ? String(localized: "Online") : String(localized: "Offline"), color: item.online ? .green : .gray)
                    }
                    .swipeActions { Button(role: .destructive) { Task { await store.attempt({ try await store.api.deleteAppConnection(item.id) }); await load() } } label: { Label("Delete", systemImage: "trash") } }
                }
            }
        }
        .listStyle(.insetGrouped)
        .overlay { if loading && connections.isEmpty { ProgressView() } }
        .task { await load() }
        .refreshable { await load() }
    }

    private func authorize(cloud: Bool) async {
        let value = await store.attempt({ try await store.api.appAuthorization(cloud: cloud) })
        authorization = value?.string(cloud ? "matching_code" : "authorization_code", "qr_payload") ?? ""
    }

    private func load() async {
        loading = true
        connections = (await store.attempt({ try await store.api.appConnections() })) ?? connections
        loading = false
    }
}

/// App → Message push: the relay that carries messages to paired phones.
struct AppRelayPanel: View {
    @EnvironmentObject private var store: AppStore
    @State private var relay: AppRelayInfo?

    var body: some View {
        List {
            Section("App Relay") {
                LabeledContent("Status") { StatusPill(text: relay?.connected == true ? String(localized: "Connected") : String(localized: "Disconnected"), color: relay?.connected == true ? .green : .gray) }
                Picker("Route", selection: Binding(get: { relay?.route ?? "official" }, set: { route in Task { relay = await store.attempt({ try await store.api.appRelay("route", method: "PUT", body: ["route": route]) }) } })) {
                    Text("Official").tag("official"); Text("Cloudflare").tag("cloudflare")
                }
                if let code = relay?.pairingCode.nilIfEmpty {
                    LabeledContent("Pairing code", value: code).textSelection(.enabled)
                    QRImageView(value: code).frame(maxWidth: .infinity)
                }
                Button(relay?.connected == true ? "Disconnect relay" : "Connect relay") { Task { relay = await store.attempt({ try await store.api.appRelay(relay?.connected == true ? "disconnect" : "connect", method: "POST") }) } }
                Button("Refresh pairing code") { Task { relay = await store.attempt({ try await store.api.appRelay("pairing-code", method: "POST") }) } }
            }
        }
        .listStyle(.insetGrouped)
        .task { relay = await store.attempt({ try await store.api.appRelay() }) ?? relay }
        .refreshable { relay = await store.attempt({ try await store.api.appRelay() }) ?? relay }
    }
}

/// Devices (super-admin): other Core Hub installations and peer links.
struct StudioDevicesPanel: View {
    @EnvironmentObject private var store: AppStore
    @State private var devices: [StudioDevice] = []
    @State private var peers: [PeerConnection] = []
    @State private var pairingPayload = ""
    @State private var manualURL = ""
    @State private var requesting = false
    @State private var loading = true

    var body: some View {
        List {
            Section("Studio devices") {
                Button("Scan devices") { Task { devices = (await store.attempt({ try await store.api.devices(scan: true) })) ?? devices } }
                Button("Show pairing QR") { Task { let value = await store.attempt({ try await store.api.devicePairingLink() }); pairingPayload = value?.string("link", "code") ?? "" } }
                Button("Request device by URL") { requesting = true }
                if !pairingPayload.isEmpty { QRImageView(value: pairingPayload); Text(pairingPayload).font(.caption.monospaced()).textSelection(.enabled) }
                ForEach(devices) { device in StudioDeviceRow(device: device) { action in await act(device, action) } }
            }
            Section("Peer connections") {
                if !loading && peers.isEmpty { Text("No peer connections.").foregroundStyle(.secondary) }
                ForEach(peers) { peer in
                    HStack {
                        VStack(alignment: .leading) { Text(peer.name); Text(peer.url).font(.caption.monospaced()).foregroundStyle(.secondary) }
                        Spacer()
                        Button("Disconnect", role: .destructive) { Task { await store.attempt({ try await store.api.disconnectPeer(peer.id) }); await load() } }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .overlay { if loading && devices.isEmpty { ProgressView() } }
        .task { await load() }
        .refreshable { await load() }
        .alert("Request device by URL", isPresented: $requesting) {
            TextField("https://device.local", text: $manualURL)
            Button("Request") { Task { await store.attempt({ try await store.api.requestDevice(url: manualURL) }); await load() } }
            Button("Cancel", role: .cancel) {}
        }
    }

    private func act(_ device: StudioDevice, _ action: String) async {
        await store.attempt({ try await store.api.deviceAction(device.id, action: action) })
        await load()
    }

    private func load() async {
        loading = true
        // Sequential so every failure is reported through the shared banner.
        devices = await store.attempt({ try await store.api.devices() }) ?? devices
        peers = await store.attempt({ try await store.api.peerConnections() }) ?? peers
        loading = false
    }
}

struct StudioDeviceRow: View {
    let device: StudioDevice
    let act: (String) async -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(device.name).font(.headline)
                Spacer()
                StatusPill(text: device.online ? String(localized: "Online") : String(localized: "Offline"), color: device.online ? .green : .gray)
            }
            Text(device.url).font(.caption.monospaced()).foregroundStyle(.secondary)
            HStack {
                if device.inbound == "pending" {
                    Button("Approve") { Task { await act("approve") } }
                    Button("Reject") { Task { await act("reject") } }
                }
                if device.inbound == "blocked" {
                    Button("Unblock") { Task { await act("unblock") } }
                } else {
                    Button("Block") { Task { await act("block") } }
                }
                if device.inbound == "approved" || device.outbound == "approved" {
                    Button("Connect") { Task { await act("connect") } }
                }
            }
            .buttonStyle(.bordered)
            .font(.caption)
        }
    }
}

struct QRImageView: View {
    let value: String
    private var image: UIImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(value.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage?.transformed(by: CGAffineTransform(scaleX: 8, y: 8)),
              let cg = CIContext().createCGImage(output, from: output.extent) else { return nil }
        return UIImage(cgImage: cg)
    }
    var body: some View {
        Group {
            if let image {
                Image(uiImage: image).interpolation(.none).resizable().scaledToFit().frame(width: 190, height: 190).padding(10).background(.white, in: RoundedRectangle(cornerRadius: 14))
            }
        }
    }
}

// MARK: - Tab strip

struct TabStripItem: Identifiable, Equatable {
    let id: String
    let title: String
}

/// Horizontal line tabs (the web's `NTabs type="line"`), scrollable so five
/// or six tabs fit a phone. `secondary` draws the sub-tab row smaller.
struct TabStrip: View {
    let items: [TabStripItem]
    @Binding var selection: String
    var secondary = false

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 4) {
                ForEach(items) { item in
                    let active = selection == item.id
                    Button { withAnimation(CoreHubTokens.Motion.quick) { selection = item.id } } label: {
                        Text(item.title)
                            .font(CoreHubTokens.Typography.font(secondary ? CoreHubTokens.Typography.meta : CoreHubTokens.Typography.sidebarTab, weight: active ? .semibold : .regular))
                            .foregroundStyle(active ? CoreHubTokens.Palette.textPrimary : CoreHubTokens.Palette.textSecondary)
                            .padding(.horizontal, 12)
                            .frame(height: secondary ? 30 : 38)
                            .overlay(alignment: .bottom) {
                                Rectangle().fill(active ? CoreHubTokens.Palette.accent : Color.clear).frame(height: 2)
                            }
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityAddTraits(active ? [.isSelected] : [])
                }
            }
            .padding(.horizontal, 12)
        }
        .background(CoreHubTokens.Palette.bgPrimary)
        .overlay(alignment: .bottom) { Rectangle().fill(CoreHubTokens.Palette.borderLight).frame(height: 1) }
    }
}
