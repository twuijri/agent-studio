import SwiftUI
import UniformTypeIdentifiers

private struct ConversationPendingAction: Identifiable {
    let kind: String
    let payload: JSON
    var id: String { "\(kind)-\(actionID)" }
    var isApproval: Bool { kind.contains("approval") }
    var actionID: String { payload.string("approval_id", "approvalId", "clarify_id", "clarification_id", "clarificationId", "id") }
    var prompt: String { payload.string("prompt", "question", "message", "description", "tool_name").nilIfEmpty ?? String(localized: "The agent needs your response before it can continue.") }
    var choices: [String] { let values = payload.strings("choices"); return values.isEmpty ? ["once", "session", "always"] : values }
    var initialResponse: String { payload.string("initial_response") }
    var responseMode: String { payload.string("response_mode") }
    var remainingSeconds: Int { max(0, payload.int("remaining_timeout_ms") / 1000) }
}

struct ConversationView: View {
    @EnvironmentObject private var store: AppStore
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme
    let session: SessionSummary
    /// True when shown as the shell's root (hamburger instead of back).
    var embeddedInShell = false
    @State private var lines: [ChatLine] = []
    @State private var showingNewSession = false
    @State private var showingSessionSettings = false
    @State private var showingRename = false
    @State private var renameText = ""
    @State private var input = ""
    @State private var attachments: [Upload] = []
    @State private var loading = true
    @State private var sending = false
    @State private var importing = false
    @State private var uploading = false
    @State private var models: [ModelOption] = []
    @State private var selectedModel = ""
    @State private var selectedProvider = ""
    @State private var contextTokens = 0
    @State private var contextWindow = 0
    @State private var loadingContext = false
    @State private var socket = ChatSocket()
    @StateObject private var recorder = VoiceRecorder()
    @StateObject private var speech = OnDeviceSpeechRecognizer()
    @StateObject private var speechPlayer = SpeechPlayer()
    enum VoiceState: Equatable { case idle, listening, transcribing, error }
    @State private var voiceState: VoiceState = .idle
    /// Composer text captured when dictation started; live partial results are appended after it.
    @State private var voiceBase = ""
    @State private var serverSttProvider = ""
    @State private var voiceReplyPending = false
    @State private var bottomVisible = true
    @State private var actionLine: ChatLine?
    @State private var replyingTo: ChatLine?
    @State private var composerExpanded = false
    @State private var pendingAction: ConversationPendingAction?
    @State private var clarificationAnswer = ""
    @State private var queuedRuns: [QueuedRun] = []
    @State private var queueInsertionID = ""
    @State private var resumedWorkspace = ""; @State private var resumedPush = false; @State private var workspaceChanges: [JSON] = []
    @FocusState private var inputFocused: Bool

    var body: some View {
        VStack(spacing: 0) {
            ScrollViewReader { reader in
                ZStack(alignment: .bottomTrailing) {
                    ScrollView {
                        LazyVStack(spacing: 14) {
                            if loading { ProgressView().padding(.top, 40) }
                            ForEach(lines) { line in MessageBubble(line: line, profile: profile, api: store.api, sessionProfile: session.profile) { actionLine = line }.id(line.id) }
                            Color.clear.frame(height: 1).id("bottom")
                                .onAppear { bottomVisible = true }
                                .onDisappear { bottomVisible = false }
                        }.padding(.horizontal, 12).padding(.vertical, 16)
                    }
                    if !bottomVisible && !lines.isEmpty {
                        Button {
                            withAnimation(.easeOut(duration: 0.25)) { reader.scrollTo("bottom", anchor: .bottom) }
                        } label: {
                            Image(systemName: "arrow.down")
                                .font(.headline.weight(.semibold))
                                .foregroundStyle(CoreHubTokens.Palette.textPrimary)
                                .frame(width: 40, height: 40)
                                .background(CoreHubTokens.Palette.bgCard, in: Circle())
                                .overlay(Circle().stroke(CoreHubTokens.Palette.border))
                                .coreHubShadow(CoreHubTokens.Shadow.card)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Jump to latest message")
                        .padding(14)
                    }
                }
                .scrollDismissesKeyboard(.interactively)
                .refreshable { await reload() }
                .onChange(of: lines) { _, _ in withAnimation(.easeOut(duration: 0.22)) { reader.scrollTo("bottom", anchor: .bottom) } }
                .task { await reload(); try? await Task.sleep(for: .milliseconds(120)); reader.scrollTo("bottom", anchor: .bottom) }
            }
            if !queuedRuns.isEmpty { queuedPanel }
            if !workspaceChanges.isEmpty { HStack { Label("\(workspaceChanges.count) workspace changes", systemImage: "arrow.triangle.branch"); Spacer(); if !resumedWorkspace.isEmpty { TechnicalText(text: resumedWorkspace) } }.font(CoreHubTokens.Typography.metaFont).foregroundStyle(CoreHubTokens.Palette.textSecondary).padding(.horizontal, 14).padding(.vertical, 5) }
            composer
        }
        .background(CoreHubTokens.Palette.bgPrimary)
        .navigationTitle(displayTitle)
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
        .toolbarBackground(CoreHubTokens.Palette.bgPrimary, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                if embeddedInShell {
                    DrawerButton()
                } else {
                    Button { dismiss() } label: {
                        CoreHubIconView(icon: .back, size: 22).foregroundStyle(CoreHubTokens.Palette.textPrimary).frame(width: 38, height: 38).contentShape(Rectangle())
                    }.buttonStyle(.plain).accessibilityLabel("Back")
                }
            }
            ToolbarItem(placement: .principal) { ChatHeaderTitle(title: displayTitle, workspace: workspaceLabel, agent: session.agentDisplayName) }
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button { Task { await reload() } } label: { Label("Refresh", systemImage: "arrow.clockwise") }
                    Button { store.startNewChat(agent: session.agentID) } label: { Label("New conversation", systemImage: "plus.bubble") }
                    Button { showingNewSession = true } label: { Label("New conversation with agent…", systemImage: "cpu") }
                    Button { input = "/fork"; send() } label: { Label("Fork conversation", systemImage: "arrow.triangle.branch") }
                    Divider()
                    Button { renameText = displayTitle; showingRename = true } label: { Label("Rename", systemImage: "pencil") }
                    Button { showingSessionSettings = true } label: { Label("Session settings", systemImage: "slider.horizontal.3") }
                    Button { Task { await archiveSession() } } label: { Label("Archive", systemImage: "archivebox") }
                    Button(role: .destructive) { Task { await deleteSession() } } label: { Label("Delete", systemImage: "trash") }
                } label: {
                    CoreHubIconView(icon: .more, size: 22).foregroundStyle(CoreHubTokens.Palette.textPrimary).frame(width: 38, height: 38).contentShape(Rectangle())
                }
                .accessibilityLabel("Conversation options")
            }
        }
        .sheet(isPresented: $showingNewSession) { NewCodingSessionView(categories: []).environmentObject(store) }
        .sheet(isPresented: $showingSessionSettings) { SessionManagementView(session: session, categories: []) { store.sessionsChanged() }.environmentObject(store) }
        .alert("Rename conversation", isPresented: $showingRename) {
            TextField("Title", text: $renameText)
            Button("Save") { Task { await renameSession(renameText) } }
            Button("Cancel", role: .cancel) {}
        }
        .confirmationDialog("Message actions", isPresented: Binding(get: { actionLine != nil }, set: { if !$0 { actionLine = nil } }), presenting: actionLine) { line in
            Button("Copy") { UIPasteboard.general.string = line.text; actionLine = nil }
            Button("Reply") { replyingTo = line; inputFocused = true; actionLine = nil }
            Button("Fork conversation") { actionLine = nil; input = "/fork"; send() }
            Button("Cancel", role: .cancel) { actionLine = nil }
        }
        .sheet(item: $pendingAction) { action in
            NavigationStack {
                Form {
                    Section(action.isApproval ? "Approval required" : "Clarification required") { Text(action.prompt).textSelection(.enabled) }
                    if action.isApproval {
                        Section("Choose an action") {
                            ForEach(action.choices, id: \.self) { choice in Button(choice) { socket.respondToApproval(sessionID: session.id, approvalID: action.actionID, choice: choice); pendingAction = nil } }
                            Button("Reject", role: .destructive) { socket.respondToApproval(sessionID: session.id, approvalID: action.actionID, choice: "deny"); pendingAction = nil }
                        }
                    } else {
                        Section("Your answer") { TextField("Type clarification", text: $clarificationAnswer, axis: .vertical).lineLimit(2...6); if !action.responseMode.isEmpty { LabeledContent("Response mode", value: action.responseMode) }; if action.remainingSeconds > 0 { Label("\(action.remainingSeconds)s remaining", systemImage: "timer") } }
                    }
                }
                .navigationTitle(action.isApproval ? "Approval" : "Clarification").navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("Cancel") { pendingAction = nil } }
                    if !action.isApproval { ToolbarItem(placement: .confirmationAction) { Button("Send") { socket.respondToClarification(sessionID: session.id, clarificationID: action.actionID, answer: clarificationAnswer); clarificationAnswer = ""; pendingAction = nil }.disabled(clarificationAnswer.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) } }
                }
            }.presentationDetents([.medium, .large])
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.item], allowsMultipleSelection: true) { result in if case let .success(urls) = result { Task { await upload(urls) } } }
        .task { await loadModels() }
        .onChange(of: scenePhase) { _, phase in
            // Studio keeps running after the app is backgrounded. Pull the
            // persisted history as soon as the conversation becomes visible.
            if phase == .active { socket.resumeApp(sessionID: session.id); Task { await reload() } }
        }
        .onDisappear { socket.close(); speech.cancel(); if recorder.isRecording { _ = recorder.stop() }; voiceState = .idle }
    }

    private var profile: Profile? { store.profiles.first { $0.name == session.profile } }
    /// The shell keeps the live title after a rename; a pushed copy uses its own.
    private var displayTitle: String { (store.selectedSession?.id == session.id ? store.selectedSession?.title : nil) ?? session.title }
    private var workspaceLabel: String { WorkspaceChip.label(for: resumedWorkspace.nilIfEmpty ?? session.workspace) }

    private func renameSession(_ title: String) async {
        let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        await store.attempt { try await store.api.renameSession(session.id, title: trimmed) }
        if store.selectedSession?.id == session.id { store.selectedSession?.title = trimmed }
        store.sessionsChanged()
    }

    private func archiveSession() async {
        await store.attempt { try await store.api.setSessionArchived(session.id, archived: true) }
        store.sessionsChanged()
        if embeddedInShell { store.selectedSession = nil } else { dismiss() }
    }

    private func deleteSession() async {
        await store.attempt { try await store.api.deleteSession(session.id) }
        store.sessionsChanged()
        if embeddedInShell { store.selectedSession = nil } else { dismiss() }
    }

    private var composerIsEmpty: Bool {
        input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        attachments.isEmpty && voiceState == .idle && !uploading
    }

    private var composer: some View {
        Group {
            if !composerExpanded && composerIsEmpty {
                HStack(spacing: 7) {
                    Button {
                        composerExpanded = true
                    } label: {
                        CoreHubIconView(icon: .plus, size: 20)
                            .foregroundStyle(CoreHubTokens.Palette.textSecondary)
                            .frame(width: 40, height: 40)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Open message composer")
                    Button {
                        composerExpanded = true
                        inputFocused = true
                    } label: {
                        Text("Type a message…")
                            .font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.inputMinimum))
                            .foregroundStyle(CoreHubTokens.Palette.textMuted)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    Button { Task { await voice() } } label: {
                        Image(systemName: "mic.fill").font(.system(size: 16, weight: .medium)).foregroundStyle(CoreHubTokens.Palette.textSecondary).frame(width: 40, height: 40).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Voice input")
                }
                .padding(.horizontal, 7)
                .padding(.vertical, 5)
                .background(CoreHubTokens.Palette.bgComposer, in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.composer, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: CoreHubTokens.Radius.composer, style: .continuous).stroke(CoreHubTokens.Palette.borderLight))
                .coreHubShadow(CoreHubTokens.Shadow.composer(for: colorScheme))
                .padding(.horizontal, 12)
                .padding(.top, 7)
            } else {
                expandedComposer
            }
        }
        .padding(.bottom, 6)
        .onChange(of: inputFocused) { _, focused in
            if !focused && composerIsEmpty { composerExpanded = false }
        }
    }

    private var queuedPanel: some View {
        ScrollView(.horizontal, showsIndicators: false) { HStack(spacing: 8) { ForEach(queuedRuns) { item in HStack(spacing: 7) { Image(systemName: queueInsertionID == item.id ? "arrow.down.to.line.compact" : "clock"); Text(item.text.nilIfEmpty ?? String(localized: "Queued message")).lineLimit(1); Button { socket.insertQueued(sessionID: session.id, queueID: item.id); queueInsertionID = item.id } label: { Image(systemName: "arrow.up.to.line.compact") }; Button { socket.cancelQueued(sessionID: session.id, queueID: item.id); queuedRuns.removeAll { $0.id == item.id } } label: { Image(systemName: "xmark.circle.fill") } }.font(CoreHubTokens.Typography.metaFont).foregroundStyle(CoreHubTokens.Palette.textSecondary).padding(8).background(CoreHubTokens.Palette.bgSecondary, in: Capsule()) } }.padding(.horizontal, 12) }.padding(.vertical, 5)
    }

    private var expandedComposer: some View {
        VStack(spacing: 9) {
            if let replyingTo {
                HStack(spacing: 10) {
                    VStack(alignment: .leading, spacing: 2) { Text("Replying to").font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.author, weight: .semibold)).foregroundStyle(CoreHubTokens.Palette.accent); Text(replyingTo.text).font(CoreHubTokens.Typography.metaFont).foregroundStyle(CoreHubTokens.Palette.textSecondary).lineLimit(2) }
                    Spacer(); Button { self.replyingTo = nil } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(CoreHubTokens.Palette.textMuted) }
                }.padding(10).background(CoreHubTokens.Palette.bgSecondary, in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.bubble)).padding(.horizontal, 10)
            }
            if !attachments.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) { HStack { ForEach(attachments) { item in HStack(spacing: 6) { Image(systemName: item.mime.hasPrefix("image/") ? "photo" : "doc"); Text(item.name).lineLimit(1); Button { attachments.removeAll { $0.id == item.id } } label: { Image(systemName: "xmark.circle.fill") } }.font(CoreHubTokens.Typography.metaFont).foregroundStyle(CoreHubTokens.Palette.textSecondary).padding(8).background(CoreHubTokens.Palette.bgSecondary, in: Capsule()) } }.padding(.horizontal, 12) }
            }
            if voiceState != .idle { voiceStatusRow }
            TextField("Type a message…", text: $input, axis: .vertical)
                .font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.inputMinimum))
                .foregroundStyle(CoreHubTokens.Palette.textPrimary)
                .lineLimit(1...6)
                .focused($inputFocused)
                .padding(.horizontal, 15)
                .padding(.vertical, 11)
                .onSubmit { if !input.isEmpty { send() } }
                .padding(.horizontal, 10)
            HStack(alignment: .center, spacing: 8) {
                Menu {
                    Button { importing = true } label: { Label("Attach files", systemImage: "paperclip") }
                    Button { store.startNewChat(agent: session.agentID) } label: { Label("New conversation", systemImage: "plus.bubble") }
                } label: {
                    Group { if uploading { ProgressView().controlSize(.small) } else { CoreHubIconView(icon: .plus, size: 18) } }
                        .foregroundStyle(CoreHubTokens.Palette.textSecondary)
                        .frame(width: CoreHubTokens.Layout.composerButton, height: CoreHubTokens.Layout.composerButton)
                        .background(CoreHubTokens.Palette.bgCard, in: Circle())
                        .overlay(Circle().stroke(CoreHubTokens.Palette.inputBorderIdle))
                }.disabled(uploading).accessibilityLabel("Attach")
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        Menu { Button("Default") { store.setReasoning("") }; ForEach(["low", "medium", "high", "xhigh"], id: \.self) { value in Button(value.capitalized) { store.setReasoning(value) } } } label: {
                            ComposerPill(symbol: "brain.head.profile", text: store.reasoningEffort.nilIfEmpty?.capitalized ?? String(localized: "Default"))
                        }
                        Menu {
                            ForEach(models) { model in
                                Button(model.name) {
                                    selectedModel = model.id
                                    selectedProvider = model.provider
                                    Task { await refreshContextWindow() }
                                }
                            }
                        } label: {
                            ComposerPill(symbol: "cpu", text: selectedModel.nilIfEmpty ?? session.model.nilIfEmpty ?? String(localized: "Model"), technical: true)
                        }
                        ComposerPill(symbol: "person.crop.circle", text: session.profile)
                        if speechPlayer.isPlaying { Button { speechPlayer.stop() } label: { ComposerPill(symbol: "stop.fill", text: String(localized: "Stop voice")) } }
                        ContextUsageView(tokens: contextTokens, window: contextWindow, loading: loadingContext)
                    }
                }
                Button {
                    if voiceState == .listening { stopVoice() }
                    else if voiceState == .transcribing { return }
                    else if sending && canSend { queueCurrentMessage() } else if canSend || sending { send() } else { Task { await voice() } }
                } label: {
                    Image(systemName: composerButtonIcon)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(composerButtonForeground)
                        .frame(width: CoreHubTokens.Layout.composerButton, height: CoreHubTokens.Layout.composerButton)
                        .background(composerButtonBackground, in: Circle())
                }
                .disabled(voiceState == .transcribing)
                .accessibilityLabel(sending ? "Stop" : (canSend ? "Send" : "Voice input"))
            }.padding(.horizontal, 10)
        }
        .padding(.top, 9)
        .padding(.bottom, 9)
        .background(CoreHubTokens.Palette.bgComposer, in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.composer, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: CoreHubTokens.Radius.composer, style: .continuous).stroke(inputFocused ? CoreHubTokens.Palette.accent : CoreHubTokens.Palette.borderLight))
        .coreHubShadow(inputFocused ? CoreHubTokens.Shadow.focused : CoreHubTokens.Shadow.composer(for: colorScheme))
        .padding(.horizontal, 8)
    }

    private var canSend: Bool {
        !input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || !attachments.isEmpty
    }

    // MARK: - Voice input (mic button states: idle / listening / transcribing / error)

    private var composerButtonIcon: String {
        switch voiceState {
        case .listening: return "stop.fill"
        case .transcribing: return "ellipsis"
        case .error: return canSend ? "arrow.up" : "mic.slash.fill"
        case .idle: break
        }
        if sending && canSend { return "text.line.last.and.arrowtriangle.forward" }
        if sending { return "stop.fill" }
        return canSend ? "arrow.up" : "mic.fill"
    }

    private var composerButtonForeground: Color {
        if voiceState == .listening || sending || canSend { return CoreHubTokens.Palette.textOnAccent }
        if voiceState == .error { return CoreHubTokens.Palette.error }
        return CoreHubTokens.Palette.textPrimary
    }

    private var composerButtonBackground: Color {
        if voiceState == .listening || sending { return CoreHubTokens.Palette.error }
        if voiceState == .transcribing { return CoreHubTokens.Palette.bgSecondary }
        return canSend ? CoreHubTokens.Palette.accent : CoreHubTokens.Palette.bgSecondary
    }

    private var voiceStatusRow: some View {
        HStack(spacing: 8) {
            switch voiceState {
            case .listening:
                Circle().fill(.red).frame(width: 8, height: 8)
                if recorder.isRecording {
                    Text("Recording \(recorder.elapsed.formatted(.number.precision(.fractionLength(0))))s").font(.caption.monospacedDigit())
                } else {
                    Text("Listening…").font(.caption)
                }
                Spacer()
                Text("Tap the microphone to finish").font(.caption2).foregroundStyle(.secondary)
            case .transcribing:
                ProgressView().controlSize(.mini)
                Text("Transcribing…").font(.caption)
                Spacer()
            case .error:
                Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.red)
                Text("Voice input failed. Tap the microphone to try again.").font(.caption)
                Spacer()
            case .idle:
                EmptyView()
            }
        }.padding(.horizontal, 16)
    }

    private func voice() async {
        switch voiceState {
        case .listening: stopVoice(); return
        case .transcribing: return
        case .idle, .error: break
        }
        store.errorMessage = nil
        composerExpanded = true
        if store.voiceInput == Preferences.voiceInputServer { await startServerVoice(); return }
        guard OnDeviceSpeechRecognizer.isAvailable(localeIdentifier: store.speechLocaleIdentifier) else {
            store.notify(String(localized: "On-device speech is unavailable; using the Core Hub server"))
            await startServerVoice(); return
        }
        await startDeviceVoice()
    }

    private func startDeviceVoice() async {
        voiceBase = input
        do {
            try await speech.start(localeIdentifier: store.speechLocaleIdentifier) { text, isFinal in
                applyDictation(text)
                guard isFinal else { return }
                if let failure = speech.lastError, text.isEmpty {
                    store.errorMessage = failure; voiceState = .error
                } else {
                    voiceState = .idle; voiceReplyPending = !text.isEmpty
                }
                inputFocused = true
            }
            voiceState = .listening
        } catch OnDeviceSpeechRecognizer.Failure.notAuthorized {
            store.notify(String(localized: "Speech permission was not granted; using the Core Hub server"))
            await startServerVoice()
        } catch OnDeviceSpeechRecognizer.Failure.unavailable {
            store.notify(String(localized: "On-device speech is unavailable; using the Core Hub server"))
            await startServerVoice()
        } catch {
            store.errorMessage = error.localizedDescription; voiceState = .error
        }
    }

    /// Server path: check `/api/studio/stt/profile-status` first, then record 16 kHz WAV.
    private func startServerVoice() async {
        do {
            let status = try await store.api.sttProfileStatus(profile: session.profile)
            guard status.configured, !status.activeProvider.isEmpty else {
                store.errorMessage = status.message; voiceState = .error; return
            }
            serverSttProvider = status.activeProvider
            try await recorder.start()
            voiceState = .listening
        } catch {
            store.errorMessage = error.localizedDescription; voiceState = .error
        }
    }

    private func stopVoice() {
        if speech.isListening { speech.stop(); voiceState = .transcribing; return }
        if recorder.isRecording { Task { await finishServerVoice() } }
    }

    private func finishServerVoice() async {
        guard let url = recorder.stop() else { voiceState = .idle; return }
        voiceState = .transcribing
        defer { try? FileManager.default.removeItem(at: url) }
        do {
            let data = try Data(contentsOf: url)
            let result = try await store.api.transcribe(wav: data, provider: serverSttProvider, language: store.speechLanguageHint, profile: session.profile)
            voiceBase = input
            applyDictation(result.text)
            voiceState = .idle; voiceReplyPending = true; inputFocused = true
        } catch {
            store.errorMessage = error.localizedDescription; voiceState = .error
        }
    }

    /// Replaces the interim dictation segment after `voiceBase`; the final text stays in the field and is never sent automatically.
    private func applyDictation(_ text: String) {
        let base = voiceBase
        guard !text.isEmpty else { input = base; return }
        let separator = base.isEmpty || base.hasSuffix(" ") || base.hasSuffix("\n") ? "" : " "
        input = base + separator + text
    }

    private func reload() async {
        loading = true
        do {
            let history = try await store.api.conversationHistory(sessionID: session.id)
            lines = history.messages.map { ChatLine(text: $0.content, fromUser: $0.role == "user", timestamp: $0.sentAt, sender: $0.role == "user" ? nil : session.profile) }
            contextTokens = history.contextTokens ?? contextTokens
        }
        catch { if lines.isEmpty && session.title != String(localized: "New conversation") { store.errorMessage = error.localizedDescription } }
        loading = false
    }

    private func loadModels() async {
        models = (await store.attempt({ try await store.api.models(profile: session.profile) })) ?? []
        selectedModel = session.model.nilIfEmpty ?? store.preferredModel.nilIfEmpty ?? profile?.model ?? models.first?.id ?? ""
        selectedProvider = models.first { $0.id == selectedModel }?.provider ?? session.provider
        await refreshContextWindow()
    }

    private func refreshContextWindow() async {
        loadingContext = true
        do { contextWindow = try await store.api.contextLength(profile: session.profile, provider: selectedProvider, model: selectedModel) }
        catch HermesError.malformedResponse { /* Studio does not know this model's window; keep the last value. */ }
        catch { store.errorMessage = error.localizedDescription }
        loadingContext = false
    }

    private func send() {
        if sending { socket.abort(sessionID: session.id); socket.close(); sending = false; if let index = lines.indices.last { lines[index].isStreaming = false; lines[index].finishedAt = .now }; return }
        // Sending always ends dictation; the text already in the field is what goes out.
        if speech.isActive { speech.cancel() }
        if recorder.isRecording { _ = recorder.stop() }
        voiceState = .idle
        var text = input.trimmingCharacters(in: .whitespacesAndNewlines); guard !text.isEmpty || !attachments.isEmpty else { return }
        if let replyingTo {
            let quote = replyingTo.text.split(separator: "\n", omittingEmptySubsequences: false).prefix(8).map { "> \($0)" }.joined(separator: "\n")
            text = [quote, text].filter { !$0.isEmpty }.joined(separator: "\n\n")
            self.replyingTo = nil
        }
        let wantsVoiceReply = voiceReplyPending; voiceReplyPending = false
        let files = attachments; input = ""; attachments = []; inputFocused = false
        lines.append(ChatLine(text: text.isEmpty ? files.map(\.name).joined(separator: ", ") : text, fromUser: true))
        lines.append(ChatLine(text: "", fromUser: false, sender: session.profile, isStreaming: true))
        let replyID = lines.last!.id; sending = true
        Task {
            var gotAnything = false
            var activeReplyID = replyID
            let stream = socket.run(baseURL: store.baseURL, token: store.token, profile: session.profile, sessionID: session.id, input: text, attachments: files, reasoningEffort: store.reasoningEffort.nilIfEmpty, model: selectedModel.nilIfEmpty, provider: selectedProvider.nilIfEmpty, session: session)
            for await event in stream {
                if case .started = event, let prior = lines.firstIndex(where: { $0.id == activeReplyID }), lines[prior].finishedAt != nil {
                    lines.append(ChatLine(text: "", fromUser: false, sender: session.profile, isStreaming: true)); activeReplyID = lines.last!.id
                }
                guard let index = lines.firstIndex(where: { $0.id == activeReplyID }) else { continue }
                switch event {
                case let .started(date): lines[index].startedAt = date; gotAnything = true
                case let .text(delta): lines[index].text += delta; gotAnything = true
                case let .reasoning(delta): lines[index].reasoning += delta; gotAnything = true
                case let .tool(id, name, detail, status, duration): updateTool(index: index, id: id, name: name, detail: detail, status: status, duration: duration); gotAnything = true
                case let .usage(tokens, window): contextTokens = tokens; if let window { contextWindow = window }
                case let .completed(output, reasoning): if lines[index].text.isEmpty { lines[index].text = output }; if lines[index].reasoning.isEmpty { lines[index].reasoning = reasoning }; lines[index].isStreaming = false; lines[index].finishedAt = .now
                case let .requiresAction(kind, payload):
                    let action = ConversationPendingAction(kind: kind, payload: payload)
                    pendingAction = action
                    if !action.isApproval && clarificationAnswer.isEmpty { clarificationAnswer = action.initialResponse }
                    lines[index].text += action.isApproval ? String(localized: "Approval is waiting for your response.") : String(localized: "Clarification is waiting for your response.")
                case let .actionResolved(id): if pendingAction?.actionID == id || id.isEmpty { pendingAction = nil }
                case let .queued(items): queuedRuns = items
                case let .queueInsertion(id, phase): queueInsertionID = phase == "cancelled" ? "" : id
                case let .subagent(id, event, title, detail): updateTool(index: index, id: "subagent-\(id)", name: title, detail: detail.nilIfEmpty ?? event, status: event == "subagent.complete" ? .done : .running, duration: nil); gotAnything = true
                case let .resumeState(workspace, model, provider, _, pushEnabled, changes): resumedWorkspace = workspace; resumedPush = pushEnabled; workspaceChanges = changes; if !model.isEmpty { selectedModel = model }; if !provider.isEmpty { selectedProvider = provider }
                case let .failed(message, retryable):
                    if retryable && !gotAnything { await restFallback(index: index, text: text, files: files) }
                    else { lines[index].text = lines[index].text.nilIfEmpty ?? message; lines[index].isError = true; lines[index].isStreaming = false }
                }
            }
            if let index = lines.firstIndex(where: { $0.id == activeReplyID }) { lines[index].isStreaming = false; lines[index].finishedAt = .now }
            if wantsVoiceReply, let reply = lines.first(where: { $0.id == replyID })?.text.nilIfEmpty {
                await speak(reply)
            }
            sending = false; Preferences.setSession(session.id, profile: session.profile)
            // The server may have created or retitled the session; refresh the drawer.
            store.sessionsChanged()
        }
    }

    private func queueCurrentMessage() {
        var text = input.trimmingCharacters(in: .whitespacesAndNewlines); guard !text.isEmpty || !attachments.isEmpty else { return }
        if let replyingTo { let quote = replyingTo.text.split(separator: "\n").prefix(8).map { "> \($0)" }.joined(separator: "\n"); text = [quote, text].filter { !$0.isEmpty }.joined(separator: "\n\n"); self.replyingTo = nil }
        let files = attachments; input = ""; attachments = []; inputFocused = false
        socket.enqueue(profile: session.profile, sessionID: session.id, input: text, attachments: files, reasoningEffort: store.reasoningEffort.nilIfEmpty, model: selectedModel.nilIfEmpty, provider: selectedProvider.nilIfEmpty, session: session)
    }

    private func restFallback(index: Int, text: String, files: [Upload]) async {
        do { let result = try await store.api.runChatREST(profile: session.profile, sessionID: session.id, input: text, attachments: files, reasoningEffort: store.reasoningEffort.nilIfEmpty, model: selectedModel.nilIfEmpty, provider: selectedProvider.nilIfEmpty); lines[index].text = result.0; lines[index].reasoning = result.1 }
        catch { lines[index].text = error.localizedDescription; lines[index].isError = true }
    }

    private func updateTool(index: Int, id: String, name: String, detail: String?, status: ToolStatus, duration: Double?) {
        if let toolIndex = lines[index].tools.firstIndex(where: { $0.id == id }) { lines[index].tools[toolIndex].status = status; lines[index].tools[toolIndex].detail = detail ?? lines[index].tools[toolIndex].detail; lines[index].tools[toolIndex].duration = duration }
        else { lines[index].tools.append(ToolStep(id: id, name: name, detail: detail, status: status, duration: duration, startedAt: .now)) }
    }

    private func upload(_ urls: [URL]) async {
        uploading = true; defer { uploading = false }
        for url in urls {
            let scoped = url.startAccessingSecurityScopedResource(); defer { if scoped { url.stopAccessingSecurityScopedResource() } }
            do { let data = try Data(contentsOf: url); let type = (try? url.resourceValues(forKeys: [.contentTypeKey]).contentType)?.preferredMIMEType ?? "application/octet-stream"; attachments.append(try await store.api.upload(data: data, name: url.lastPathComponent, mime: type, profile: session.profile)) }
            catch { store.errorMessage = error.localizedDescription }
        }
    }

    private func speak(_ text: String) async {
        do { try speechPlayer.play(await store.api.synthesize(text: text, profile: session.profile)) }
        catch { store.errorMessage = error.localizedDescription }
    }
}

/// Chat header: title 16/600 with per-string direction, workspace chip
/// (folder 12, 11 pt muted, radius 4, last path segment).
struct ChatHeaderTitle: View {
    let title: String
    var workspace: String = ""
    var agent: String = ""

    var body: some View {
        VStack(spacing: 1) {
            Text(title)
                .font(CoreHubTokens.Typography.titleFont)
                .foregroundStyle(CoreHubTokens.Palette.textPrimary)
                .lineLimit(1)
                .environment(\.layoutDirection, MarkdownText.layoutDirection(for: title))
            HStack(spacing: 6) {
                if !workspace.isEmpty {
                    HStack(spacing: 4) {
                        CoreHubIconView(icon: .folder, size: 12)
                        TechnicalText(text: workspace, font: CoreHubTokens.Typography.font(CoreHubTokens.Typography.workspaceChip), color: CoreHubTokens.Palette.textMuted)
                    }
                    .foregroundStyle(CoreHubTokens.Palette.textMuted)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 2)
                    .background(CoreHubTokens.Palette.hover, in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.tag))
                } else if !agent.isEmpty {
                    Text(agent).font(CoreHubTokens.Typography.metaFont).foregroundStyle(CoreHubTokens.Palette.textMuted)
                }
            }
        }
        .frame(maxWidth: 240)
    }
}

/// Toolbar pill of the composer (radius 999, 11 pt, icon + label).
struct ComposerPill: View {
    let symbol: String
    let text: String
    var technical = false

    var body: some View {
        HStack(spacing: 5) {
            Image(systemName: symbol).font(.system(size: 11, weight: .medium))
            if technical {
                TechnicalText(text: text, font: CoreHubTokens.Typography.metaFont, color: CoreHubTokens.Palette.textSecondary)
                    .frame(maxWidth: CoreHubTokens.Layout.modelPillMaxWidth)
            } else {
                Text(text).font(CoreHubTokens.Typography.metaFont).lineLimit(1)
            }
        }
        .foregroundStyle(CoreHubTokens.Palette.textSecondary)
        .padding(.horizontal, 9)
        .frame(height: 26)
        .background(CoreHubTokens.Palette.bgCard, in: Capsule())
        .overlay(Capsule().stroke(CoreHubTokens.Palette.inputBorderIdle))
    }
}

/// "45.0k / 256.0k · remaining 211.0k", 11 pt muted, amber above 80 %,
/// bar 42×4 on phones.
struct ContextUsageView: View {
    let tokens: Int
    let window: Int
    let loading: Bool

    private var ratio: Double { window > 0 ? min(1, max(0, Double(tokens) / Double(window))) : 0 }
    private var color: Color { ratio > 0.8 ? CoreHubTokens.Palette.contextWarning : CoreHubTokens.Palette.textMuted }

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(loading ? String(localized: "Context…") : label)
                .font(CoreHubTokens.Typography.metaFont)
                .lineLimit(1)
                .environment(\.layoutDirection, .leftToRight)
            ZStack(alignment: .leading) {
                Capsule().fill(CoreHubTokens.Palette.border)
                Capsule().fill(color).frame(width: CoreHubTokens.Layout.contextBarWidthPhone * ratio)
            }
            .frame(width: CoreHubTokens.Layout.contextBarWidthPhone, height: CoreHubTokens.Layout.contextBarHeight)
        }
        .foregroundStyle(color)
    }

    private var label: String {
        guard window > 0 else { return String(localized: "Context —") }
        return "\(Self.compact(tokens)) / \(Self.compact(window)) · \(String(localized: "remaining")) \(Self.compact(max(0, window - tokens)))"
    }

    static func compact(_ value: Int) -> String {
        if value >= 1_000_000 { return String(format: "%.1fM", Double(value) / 1_000_000) }
        if value >= 1_000 { return String(format: "%.1fk", Double(value) / 1_000) }
        return String(value)
    }
}

private struct MessageBubble: View {
    let line: ChatLine
    let profile: Profile?
    let api: APIClient
    let sessionProfile: String
    let onTap: () -> Void
    @State private var reasoningExpanded = true
    var body: some View {
        let parsed = ChatFiles.parse(line.text)
        HStack(alignment: .top, spacing: 8) {
            if line.fromUser { Spacer(minLength: 45) } else { ProfileAvatar(name: sessionProfile, avatar: profile?.avatar, size: CoreHubTokens.Layout.assistantAvatar) }
            VStack(alignment: .leading, spacing: 9) {
                if !line.fromUser, let sender = line.sender, !sender.isEmpty {
                    Text(sender).font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.author, weight: .medium)).foregroundStyle(CoreHubTokens.Palette.textSecondary)
                }
                if !line.reasoning.isEmpty || !line.tools.isEmpty || line.isStreaming {
                    DisclosureGroup(isExpanded: $reasoningExpanded) {
                        VStack(alignment: .leading, spacing: 7) {
                            if !line.reasoning.isEmpty { Text(line.reasoning).font(CoreHubTokens.Typography.thinkingFont).foregroundStyle(CoreHubTokens.Palette.textSecondary.opacity(CoreHubTokens.Alpha.thinkingText)).textSelection(.enabled) }
                            ForEach(line.tools) { tool in ToolStepRow(tool: tool) }
                        }.padding(.top, 7)
                    } label: {
                        HStack { Text(verbatim: "💭"); Text(line.isStreaming ? "Thinking" : "Thinking details"); if line.isStreaming { ProgressView().controlSize(.mini) } }
                            .font(CoreHubTokens.Typography.thinkingFont).foregroundStyle(CoreHubTokens.Palette.textSecondary)
                    }
                }
                if !parsed.text.isEmpty { MarkdownText(text: parsed.text).font(CoreHubTokens.Typography.messageFont).foregroundStyle(line.isError ? CoreHubTokens.Palette.error : CoreHubTokens.Palette.textPrimary) }
                ForEach(parsed.files) { link in FileDownloadCard(link: link, fetch: { try await api.downloadFile(path: link.path, name: ChatFiles.fileName(for: link), profile: sessionProfile) }) }
                HStack(spacing: 5) { if line.isStreaming { ProgressView().controlSize(.mini) }; if let timestamp = line.timestamp { Text(timestamp.chatTime) } }.font(CoreHubTokens.Typography.metaFont).foregroundStyle(CoreHubTokens.Palette.textMuted)
            }
            // Assistant replies need a real proposed width so an RTL paragraph
            // can align against the bubble's right edge. User bubbles remain
            // compact and grow only as much as their own content needs.
            .frame(maxWidth: line.fromUser ? nil : .infinity, alignment: .leading)
            .padding(.horizontal, CoreHubTokens.Layout.bubblePaddingHorizontal).padding(.vertical, CoreHubTokens.Layout.bubblePaddingVertical)
            .background(bubbleBackground, in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.bubble, style: .continuous))
            .frame(maxWidth: line.fromUser ? 560 : .infinity, alignment: .leading)
            if !line.fromUser { Spacer(minLength: 24) }
        }.frame(maxWidth: .infinity).contentShape(Rectangle()).onTapGesture(perform: onTap)
    }

    private var bubbleBackground: Color {
        if line.isError { return CoreHubTokens.Palette.error.opacity(CoreHubTokens.Alpha.hover) }
        return line.fromUser ? CoreHubTokens.Palette.msgUser : CoreHubTokens.Palette.msgAssistant
    }
}

private struct ToolStepRow: View {
    let tool: ToolStep
    var body: some View {
        HStack(spacing: 9) { ToolIcon(name: tool.name); VStack(alignment: .leading, spacing: 2) { Text(tool.name).font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.meta, weight: .semibold)).lineLimit(1); if let detail = tool.detail { TechnicalText(text: detail, font: CoreHubTokens.Typography.mono(CoreHubTokens.Typography.meta)) } }; Spacer(); if let duration = tool.duration { Text("\(duration, specifier: "%.1f")s").font(CoreHubTokens.Typography.metaFont.monospacedDigit()).foregroundStyle(CoreHubTokens.Palette.textMuted) }; Group { switch tool.status { case .running: ProgressView(); case .done: Image(systemName: "checkmark.circle.fill").foregroundStyle(CoreHubTokens.Palette.success); case .error: Image(systemName: "xmark.circle.fill").foregroundStyle(CoreHubTokens.Palette.error) } }.controlSize(.small) }
            .padding(7).background(CoreHubTokens.Palette.bgSecondary, in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.button))
    }
}
