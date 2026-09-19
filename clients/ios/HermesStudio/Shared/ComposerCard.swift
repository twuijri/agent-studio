import SwiftUI

/// The one composer card both screens draw (radius 18, min 150 pt, shadow):
/// reference chip, attachment strip with progress, the dictation status row,
/// a 16 pt input with per-string direction, the toolbar
/// [+] [pills…] … [queue] [mic 30] [send/stop 30], and the counter in the top
/// end corner. Labels collapse to icons under 380 pt. Never auto-focused.
///
/// What differs between a chat and a room is the `ComposerConfiguration`, not
/// this view.
struct ComposerCard: View {
    @Binding var text: String
    var focused: FocusState<Bool>.Binding
    let configuration: ComposerConfiguration
    let uploads: [AttachmentUpload]
    let reference: ChatLine?
    let state: ComposerState
    let actions: ComposerActions
    let availableWidth: CGFloat
    @Environment(\.colorScheme) private var colorScheme

    private var compact: Bool { availableWidth < 380 }
    private var trimmed: String { text.trimmingCharacters(in: .whitespacesAndNewlines) }
    private var uploading: Bool { uploads.contains { $0.phase == .uploading } }
    private var canSend: Bool {
        (!trimmed.isEmpty || uploads.contains { $0.phase == .done })
            && !uploading && state.canCompose && !state.sending
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let reference { ReferenceChip(line: reference, onCancel: actions.cancelReference) }
            if !uploads.isEmpty { AttachmentStrip(uploads: uploads, onCancel: actions.cancelUpload) }
            if state.voiceState != .idle { VoiceStatusRow(state: state) }
            ComposerInput(text: $text, placeholder: configuration.placeholder, focused: focused,
                          onSubmit: { if canSend { actions.send() } })
            ComposerToolbar(configuration: configuration, state: state, actions: actions,
                            compact: compact, canSend: canSend, hasDraft: !trimmed.isEmpty)
        }
        .padding(.top, configuration.topInset)
        .padding(.horizontal, 12)
        .padding(.bottom, 9)
        .frame(minHeight: CoreHubTokens.Layout.composerMinHeight, alignment: .bottom)
        .overlay(alignment: .topTrailing) { counter }
        .background(CoreHubTokens.Palette.bgComposer, in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.composer, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: CoreHubTokens.Radius.composer, style: .continuous).stroke(focused.wrappedValue ? CoreHubTokens.Palette.accent : CoreHubTokens.Palette.borderLight))
        .coreHubShadow(focused.wrappedValue ? CoreHubTokens.Shadow.focused : CoreHubTokens.Shadow.composer(for: colorScheme))
        .padding(.horizontal, 8)
        .padding(.bottom, 6)
    }

    @ViewBuilder private var counter: some View {
        switch configuration.counter {
        case .none:
            EmptyView()
        case .context:
            ContextUsageView(tokens: state.contextTokens, window: state.contextWindow, loading: state.loadingContext)
                .padding(.top, 8)
                .padding(.trailing, 14)
        case .total:
            TokenTotalView(tokens: state.totalTokens)
                .padding(.top, 8)
                .padding(.trailing, 14)
        }
    }
}

/// The field the owner types **and dictates** into: live partial results land
/// here, so its direction has to follow the words, not the app language.
private struct ComposerInput: View {
    @Binding var text: String
    let placeholder: LocalizedStringKey
    var focused: FocusState<Bool>.Binding
    let onSubmit: () -> Void

    var body: some View {
        TextField(placeholder, text: $text, axis: .vertical)
            .font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.inputMinimum))
            .foregroundStyle(CoreHubTokens.Palette.textPrimary)
            .lineLimit(1...8)
            .focused(focused)
            .padding(.horizontal, 4)
            .padding(.vertical, 6)
            .frame(maxWidth: .infinity, alignment: .leading)
            // Last: the frame's `.leading` must resolve in the text's own
            // direction, otherwise Arabic is laid out right-to-left inside a
            // box that is still anchored to the left. An empty draft keeps
            // the interface direction so the caret does not jump sides.
            .contentDirection(of: text)
    }
}

private struct ComposerToolbar: View {
    let configuration: ComposerConfiguration
    let state: ComposerState
    let actions: ComposerActions
    let compact: Bool
    let canSend: Bool
    let hasDraft: Bool

    var body: some View {
        HStack(alignment: .center, spacing: 6) {
            // The "+" opens the attachment sheet (Chat/AttachmentSheet.swift).
            AttachmentSheetButton(onCamera: actions.attachCamera, onPhotos: actions.attachPhotos, onFiles: actions.attachFiles)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(configuration.pills) { pill in
                        ComposerPillView(kind: pill, configuration: configuration, state: state, actions: actions, compact: compact)
                    }
                }
            }
            Spacer(minLength: 2)
            ForEach(configuration.controls) { control in
                ComposerControlView(kind: control, state: state, actions: actions, hasDraft: hasDraft)
            }
            SendButton(isRunning: configuration.stopsRun && state.isRunning, sending: state.sending,
                       canSend: canSend, send: actions.send, stop: actions.stop)
        }
    }
}

/// One pill of the scrolling strip. Kept as its own type so the toolbar's
/// body stays small enough for the type checker.
private struct ComposerPillView: View {
    let kind: ComposerPillKind
    let configuration: ComposerConfiguration
    let state: ComposerState
    let actions: ComposerActions
    let compact: Bool

    @ViewBuilder var body: some View {
        switch kind {
        case .mention:
            if !state.mentionNames.isEmpty {
                MentionPill(names: state.mentionNames, insert: actions.insertMention)
            }
        case .reasoning:
            ReasoningPill(value: state.reasoningEffort, compact: compact, select: actions.selectReasoning)
        case .settings:
            if configuration.showsSettings {
                SettingsPill(toggles: configuration.toggles, state: state, compact: compact, toggle: actions.toggle)
            }
        case .model:
            ModelPill(models: state.models, selected: state.selectedModel, compact: compact, select: actions.selectModel)
        }
    }
}

/// One round control before the send button.
private struct ComposerControlView: View {
    let kind: ComposerControlKind
    let state: ComposerState
    let actions: ComposerActions
    let hasDraft: Bool

    @ViewBuilder var body: some View {
        switch kind {
        case .queue:
            if state.isRunning && hasDraft { QueueButton(action: actions.queue) }
        case .dictation:
            MicButton(state: state, action: actions.mic, longPress: actions.micLanguage)
        }
    }
}
