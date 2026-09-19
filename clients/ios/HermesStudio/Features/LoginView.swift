import SwiftUI

extension AppConnectionQR: Identifiable {
    var id: String { authorizationCode }
}

struct LoginView: View {
    @EnvironmentObject private var store: AppStore
    @State private var server = ""
    @State private var username = ""
    @State private var password = ""
    @State private var scanning = false
    @State private var pendingQR: AppConnectionQR?
    @State private var showPasswordLogin = false
    @FocusState private var focused: Field?
    enum Field { case server, username, password }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 26) {
                    VStack(spacing: 14) {
                        AppMark(size: 88).coreHubShadow(CoreHubTokens.Shadow.card)
                        Text("Core Hub").font(.largeTitle.bold())
                        Text("Your agents, native on iPhone").font(.subheadline).foregroundStyle(.secondary)
                    }.padding(.top, 50)

                    VStack(spacing: 12) {
                        Button { scanning = true } label: {
                            HStack(spacing: 10) {
                                if store.busy { ProgressView().tint(.white) } else { Image(systemName: "qrcode.viewfinder").font(.title3) }
                                Text(store.busy ? "Connecting…" : "Scan QR code").fontWeight(.semibold)
                            }.frame(maxWidth: .infinity).frame(height: 52)
                        }.buttonStyle(.borderedProminent).buttonBorderShape(.roundedRectangle(radius: 16)).disabled(store.busy)
                        Text("In Core Hub open Settings → App connections → Create LAN pairing code, then scan it here.")
                            .font(.caption).foregroundStyle(.secondary).multilineTextAlignment(.center).padding(.horizontal, 8)
                    }

                    DisclosureGroup(isExpanded: $showPasswordLogin) {
                        VStack(spacing: 14) {
                            field("Studio address", icon: "server.rack", text: $server, field: .server, contentType: .URL)
                                .keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
                            field("Username", icon: "person", text: $username, field: .username, contentType: .username)
                                .textInputAutocapitalization(.never).autocorrectionDisabled()
                            HStack(spacing: 12) {
                                Image(systemName: "lock").foregroundStyle(.secondary).frame(width: 22)
                                SecureField("Password", text: $password).textContentType(.password).focused($focused, equals: .password).submitLabel(.go).onSubmit(login)
                            }.padding(.horizontal, 15).frame(height: 54).background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))

                            Button(action: login) {
                                HStack { if store.busy { ProgressView() }; Text(store.busy ? "Connecting…" : "Connect").fontWeight(.semibold) }
                                    .frame(maxWidth: .infinity).frame(height: 48)
                            }.buttonStyle(.bordered).buttonBorderShape(.roundedRectangle(radius: 16)).disabled(!valid || store.busy)
                        }.padding(.top, 10)
                    } label: {
                        Label("Sign in with username and password", systemImage: "person.badge.key").font(.subheadline.weight(.medium))
                    }

                    SurfaceCard {
                        HStack(alignment: .top, spacing: 12) {
                            Image(systemName: "lock.shield.fill").foregroundStyle(.green)
                            VStack(alignment: .leading, spacing: 4) { Text("Private and direct").font(.subheadline.weight(.semibold)); Text("The app connects straight to your Core Hub server. Your access token is kept in the iPhone Keychain.").font(.caption).foregroundStyle(.secondary) }
                        }
                    }
                }.padding(.horizontal, 22).padding(.bottom, 30)
            }.hermesBackground()
        }
        .onAppear { server = store.baseURL }
        .sheet(isPresented: $scanning) {
            QRScanSheet { code in
                scanning = false
                handleScanned(code)
            }
        }
        .sheet(item: $pendingQR) { qr in
            PairingConfirmSheet(qr: qr) { name in
                await store.pair(with: qr, deviceName: name)
                if store.phase == .signedIn { pendingQR = nil }
            }
        }
    }

    private var valid: Bool { !server.trimmingCharacters(in: .whitespaces).isEmpty && !username.isEmpty && !password.isEmpty }
    private func login() { focused = nil; Task { await store.login(server: server, username: username, password: password) } }

    private func handleScanned(_ code: String) {
        do {
            let qr = try AppConnectionQR.parse(code)
            if qr.isExpired() {
                store.errorMessage = AppStore.pairingErrorMessage(HermesError.http(410, ""))
                return
            }
            store.errorMessage = nil
            pendingQR = qr
        } catch {
            store.errorMessage = error.localizedDescription
        }
    }

    private func field(_ title: LocalizedStringKey, icon: String, text: Binding<String>, field: Field, contentType: UITextContentType) -> some View {
        HStack(spacing: 12) { Image(systemName: icon).foregroundStyle(.secondary).frame(width: 22); TextField(title, text: text).textContentType(contentType).focused($focused, equals: field).submitLabel(field == .password ? .go : .next).onSubmit { focused = field == .server ? .username : .password } }
            .padding(.horizontal, 15).frame(height: 54).background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
    }
}

/// Full-screen camera sheet. Errors (no permission, no camera) are shown
/// inside the sheet so the user can fix them and try again.
private struct QRScanSheet: View {
    @Environment(\.dismiss) private var dismiss
    let onCode: (String) -> Void
    @State private var cameraError: String?

    var body: some View {
        NavigationStack {
            ZStack {
                QRScannerView(onCode: onCode) { message in cameraError = message }
                    .ignoresSafeArea()
                VStack {
                    Spacer()
                    if let cameraError {
                        Text(cameraError).font(.footnote).foregroundStyle(.white).multilineTextAlignment(.center)
                            .padding(14).background(.red.opacity(0.85), in: RoundedRectangle(cornerRadius: 14)).padding()
                    } else {
                        Text("Point the camera at the Core Hub pairing QR code").font(.footnote.weight(.medium)).foregroundStyle(.white)
                            .padding(12).background(.black.opacity(0.55), in: Capsule()).padding(.bottom, 30)
                    }
                }
            }
            .navigationTitle("Scan QR code").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } } }
        }
    }
}

/// Lets the user confirm the server and edit the device name before pairing.
private struct PairingConfirmSheet: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.dismiss) private var dismiss
    let qr: AppConnectionQR
    let onPair: (String) async -> Void
    @State private var deviceName = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("Core Hub server") {
                    Text(qr.backendURL).font(.body.monospaced()).textSelection(.enabled)
                    if !qr.machineID.isEmpty { LabeledContent("Machine", value: qr.machineID) }
                    if let expiresAt = qr.expiresAt { LabeledContent("Code expires", value: expiresAt.formatted(date: .omitted, time: .shortened)) }
                }
                Section {
                    TextField("Device name", text: $deviceName)
                } header: { Text("This iPhone") } footer: { Text("Shown in Core Hub under App connections so you can recognise and remove this device later.") }
                Section {
                    Button { Task { await onPair(deviceName) } } label: {
                        HStack { if store.busy { ProgressView() }; Text("Pair this iPhone").fontWeight(.semibold) }.frame(maxWidth: .infinity)
                    }.disabled(store.busy)
                }
            }
            .navigationTitle("Pair with Core Hub").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() }.disabled(store.busy) } }
            .onAppear { if deviceName.isEmpty { deviceName = DeviceIdentity.defaultName } }
        }
        .presentationDetents([.medium, .large])
        .interactiveDismissDisabled(store.busy)
    }
}
