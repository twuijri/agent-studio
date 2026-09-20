import SwiftUI

// The individual controls of `ComposerCard`. Both screens get exactly these,
// so a fix to the microphone or to a chip is a fix in both places.

/// Toolbar pill (radius 999, 11 pt, icon + optional label; the model label is
/// technical text limited to 190 pt).
struct ComposerPill: View {
    let symbol: String
    let text: String?
    var technical = false

    var body: some View {
        HStack(spacing: 5) {
            Image(systemName: symbol).font(.system(size: 11, weight: .medium))
            if let text {
                if technical {
                    TechnicalText(text: text, font: CoreHubTokens.Typography.metaFont, color: CoreHubTokens.Palette.textSecondary)
                        .frame(maxWidth: CoreHubTokens.Layout.modelPillMaxWidth)
                } else {
                    Text(text).font(CoreHubTokens.Typography.metaFont).lineLimit(1)
                }
            }
        }
        .foregroundStyle(CoreHubTokens.Palette.textSecondary)
        .padding(.horizontal, 9)
        .frame(height: 26)
        .background(CoreHubTokens.Palette.bgCard, in: Capsule())
        .overlay(Capsule().stroke(CoreHubTokens.Palette.inputBorderIdle))
    }
}

/// Menu row with a check mark when selected (an empty SF Symbol name logs a warning).
struct MenuChoice: View {
    let text: String
    let selected: Bool

    var body: some View {
        if selected { Label(text, systemImage: "checkmark") } else { Text(text) }
    }
}

struct ReasoningPill: View {
    let value: String
    let compact: Bool
    let select: (String) -> Void

    var body: some View {
        Menu {
            Button { select("") } label: { MenuChoice(text: String(localized: "Default"), selected: value.isEmpty) }
            ForEach(ReasoningEffortOption.allCases) { option in
                Button { select(option.rawValue) } label: { MenuChoice(text: option.label, selected: value == option.rawValue) }
            }
        } label: {
            ComposerPill(symbol: "brain.head.profile", text: compact ? nil : ReasoningEffortOption.label(for: value))
        }
        .accessibilityLabel("Reasoning effort")
    }
}

/// The ⚙ pill. Its rows come from the configuration, so a room offers only
/// the switches a room actually honours.
struct SettingsPill: View {
    let toggles: [ComposerToggle]
    let state: ComposerState
    let compact: Bool
    let toggle: (ComposerToggle) -> Void

    var body: some View {
        Menu {
            ForEach(toggles) { item in
                Button { toggle(item) } label: { MenuChoice(text: item.label, selected: state.isOn(item)) }
            }
        } label: {
            ComposerPill(symbol: "gearshape", text: compact ? nil : String(localized: "Settings"))
        }
        .accessibilityLabel("Settings")
    }
}

struct ModelPill: View {
    let models: [ModelOption]
    let selected: String
    let compact: Bool
    let select: (ModelOption) -> Void

    var body: some View {
        Menu {
            if models.isEmpty { Text("No models available") }
            ForEach(models) { model in
                Button { select(model) } label: { MenuChoice(text: model.name, selected: model.id == selected) }
            }
        } label: {
            ComposerPill(symbol: "cpu", text: compact && selected.isEmpty ? nil : (selected.nilIfEmpty ?? String(localized: "Model")), technical: true)
        }
        .accessibilityLabel("Model")
    }
}

/// The room's @ menu. A seat name is user text, so it is never folded into a
/// localized sentence and never reordered by the interface direction.
struct MentionPill: View {
    let names: [String]
    let insert: (String) -> Void

    var body: some View {
        Menu {
            ForEach(names, id: \.self) { name in
                Button { insert(name) } label: { Text(verbatim: "@\(name)") }
            }
        } label: {
            ComposerPill(symbol: "at", text: String(localized: "Mention"))
        }
        .accessibilityLabel("Mention an agent")
        .accessibilityHint("Inserts the agent's name into the message")
    }
}

/// Send the draft after the reply that is streaming (chat only).
struct QueueButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "text.line.last.and.arrowtriangle.forward")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(CoreHubTokens.Palette.textSecondary)
                .frame(width: CoreHubTokens.Layout.composerButton, height: CoreHubTokens.Layout.composerButton)
                .background(CoreHubTokens.Palette.bgSecondary, in: Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Queue message")
    }
}

struct MicButton: View {
    let state: ComposerState
    let action: () -> Void
    let longPress: () -> Void

    private var symbol: String {
        switch state.voiceState {
        case .listening: return "stop.fill"
        case .transcribing: return "ellipsis"
        case .error: return "mic.slash.fill"
        case .idle: return "mic.fill"
        }
    }
    private var foreground: Color {
        switch state.voiceState {
        case .listening: return CoreHubTokens.Palette.textOnAccent
        case .error: return CoreHubTokens.Palette.error
        default: return CoreHubTokens.Palette.textPrimary
        }
    }
    private var background: Color { state.voiceState == .listening ? CoreHubTokens.Palette.error : CoreHubTokens.Palette.bgSecondary }
    private var isDisabled: Bool { state.voiceState == .transcribing }

    /// A plain `Button` plus `simultaneousGesture` fires both callbacks on a
    /// long press, so tap and long press are separate gestures here.
    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(foreground)
            .frame(width: CoreHubTokens.Layout.composerButton, height: CoreHubTokens.Layout.composerButton)
            .background(background, in: Circle())
            .opacity(isDisabled ? 0.55 : 1)
            .contentShape(Circle())
            .onTapGesture { if !isDisabled { action() } }
            .onLongPressGesture(minimumDuration: 0.45) { longPress() }
            .accessibilityElement(children: .ignore)
            .accessibilityAddTraits(.isButton)
            .accessibilityLabel(state.voiceState == .listening ? "Stop" : "Voice input")
            .accessibilityHint("Touch and hold to choose the dictation language")
            .accessibilityAction(named: Text("Dictation language")) { longPress() }
    }
}

/// Accent circle with an arrow; a spinner while a send is in flight, and a
/// stop square while a run is streaming on a screen that has one run.
struct SendButton: View {
    let isRunning: Bool
    let sending: Bool
    let canSend: Bool
    let send: () -> Void
    let stop: () -> Void

    var body: some View {
        Button { isRunning ? stop() : send() } label: {
            Group {
                if sending {
                    ProgressView().controlSize(.small).tint(CoreHubTokens.Palette.textOnAccent)
                } else if isRunning {
                    RoundedRectangle(cornerRadius: 2).fill(CoreHubTokens.Palette.textOnAccent).frame(width: 11, height: 11)
                } else {
                    Image(systemName: "arrow.up").font(.system(size: 14, weight: .semibold)).foregroundStyle(CoreHubTokens.Palette.textOnAccent)
                }
            }
            .frame(width: CoreHubTokens.Layout.composerButton, height: CoreHubTokens.Layout.composerButton)
            .background(isRunning || canSend ? CoreHubTokens.Palette.accent : CoreHubTokens.Palette.accentMuted, in: Circle())
        }
        .buttonStyle(.plain)
        .disabled(!isRunning && !canSend)
        .accessibilityLabel(isRunning ? "Stop" : "Send")
    }
}

struct ReferenceChip: View {
    let line: ChatLine
    let onCancel: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Replying to").font(CoreHubTokens.Typography.font(CoreHubTokens.Typography.author, weight: .semibold)).foregroundStyle(CoreHubTokens.Palette.accent)
                DirectionalText(text: line.text, font: CoreHubTokens.Typography.metaFont, color: CoreHubTokens.Palette.textSecondary, lineLimit: 2)
            }
            Spacer()
            Button(action: onCancel) { Image(systemName: "xmark.circle.fill").foregroundStyle(CoreHubTokens.Palette.textMuted) }
                .buttonStyle(.plain)
                .accessibilityLabel("Cancel reference")
        }
        .padding(10)
        .background(CoreHubTokens.Palette.bgSecondary, in: RoundedRectangle(cornerRadius: CoreHubTokens.Radius.bubble))
    }
}

/// Attachment chips with upload progress and a cancel/remove button.
struct AttachmentStrip: View {
    let uploads: [AttachmentUpload]
    let onCancel: (String) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(uploads) { upload in AttachmentChip(upload: upload, onCancel: onCancel) }
            }
        }
    }
}

private struct AttachmentChip: View {
    let upload: AttachmentUpload
    let onCancel: (String) -> Void

    private var symbol: String {
        switch MediaKind.classify(path: upload.name, mime: upload.mime) {
        case .image: return "photo"
        case .video: return "video"
        case .audio: return "waveform"
        case .file: return "doc"
        }
    }

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: symbol)
            VStack(alignment: .leading, spacing: 2) {
                TechnicalText(text: upload.name, font: CoreHubTokens.Typography.metaFont, color: CoreHubTokens.Palette.textSecondary).frame(maxWidth: 140)
                switch upload.phase {
                case .uploading:
                    ProgressView(value: upload.fraction).progressViewStyle(.linear).frame(width: 90, height: 3).tint(CoreHubTokens.Palette.accent)
                case .done:
                    EmptyView()
                case let .failed(message):
                    Text(message).font(CoreHubTokens.Typography.font(10)).foregroundStyle(CoreHubTokens.Palette.error).lineLimit(1)
                case .cancelled:
                    Text("Cancelled").font(CoreHubTokens.Typography.font(10)).foregroundStyle(CoreHubTokens.Palette.textMuted)
                }
            }
            Button { onCancel(upload.id) } label: { Image(systemName: "xmark.circle.fill") }
                .buttonStyle(.plain)
                .accessibilityLabel(upload.phase == .uploading ? "Cancel upload" : "Remove attachment")
        }
        .font(CoreHubTokens.Typography.metaFont)
        .foregroundStyle(CoreHubTokens.Palette.textSecondary)
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(CoreHubTokens.Palette.bgSecondary, in: Capsule())
    }
}

/// Which language the mic is listening for, so a wrong language is visible
/// before the words come back wrong. Its own direction: an Arabic endonym must
/// not be reordered by an English layout.
private struct SpeechLanguageChip: View {
    let label: String

    var body: some View {
        if !label.isEmpty {
            Text(label)
                .font(CoreHubTokens.Typography.metaFont)
                .foregroundStyle(CoreHubTokens.Palette.textMuted)
                .lineLimit(1)
                .padding(.horizontal, 7)
                .padding(.vertical, 2)
                .background(CoreHubTokens.Palette.bgSecondary, in: Capsule())
                .contentDirection(of: label)
                .accessibilityLabel(Text("Dictation language"))
                .accessibilityValue(Text(label))
        }
    }
}

struct VoiceStatusRow: View {
    let state: ComposerState

    var body: some View {
        HStack(spacing: 8) {
            switch state.voiceState {
            case .listening:
                Circle().fill(CoreHubTokens.Palette.error).frame(width: 8, height: 8)
                if state.isRecording {
                    Text("Recording \(state.recordingElapsed.formatted(.number.precision(.fractionLength(0))))s").font(CoreHubTokens.Typography.metaFont.monospacedDigit())
                } else {
                    Text("Listening…").font(CoreHubTokens.Typography.metaFont)
                }
                SpeechLanguageChip(label: state.speechLanguage)
                // The mic is off screen while listening — the recording strip
                // holds ✕ and ■ instead — so there is no "tap the mic" tip here.
                Spacer(minLength: 6)
            case .transcribing:
                ProgressView().controlSize(.mini)
                Text("Transcribing…").font(CoreHubTokens.Typography.metaFont)
                SpeechLanguageChip(label: state.speechLanguage)
                Spacer()
            case .error:
                Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(CoreHubTokens.Palette.error)
                Text("Voice input failed. Tap the microphone to try again.").font(CoreHubTokens.Typography.metaFont)
                Spacer()
            case .idle:
                EmptyView()
            }
        }
        .foregroundStyle(CoreHubTokens.Palette.textSecondary)
    }
}

/// "45.0k / 256.0k · remaining 211.0k", 11 pt muted, amber above 80 %, bar 42×4.
struct ContextUsageView: View {
    let tokens: Int
    let window: Int
    let loading: Bool

    private var ratio: Double { ContextUsageFormat.ratio(used: tokens, limit: window) }
    private var color: Color { ContextUsageFormat.isWarning(used: tokens, limit: window) ? CoreHubTokens.Palette.contextWarning : CoreHubTokens.Palette.textMuted }

    var body: some View {
        VStack(alignment: .trailing, spacing: 3) {
            Text(loading ? String(localized: "Context…") : label)
                .font(CoreHubTokens.Typography.metaFont)
                .lineLimit(1)
                .technicalDirection()
            ZStack(alignment: .leading) {
                Capsule().fill(CoreHubTokens.Palette.border)
                Capsule().fill(color).frame(width: CoreHubTokens.Layout.contextBarWidthPhone * ratio)
            }
            .frame(width: CoreHubTokens.Layout.contextBarWidthPhone, height: CoreHubTokens.Layout.contextBarHeight)
        }
        .foregroundStyle(color)
        .accessibilityLabel(label)
    }

    private var label: String {
        guard window > 0 else { return String(localized: "Context —") }
        return ContextUsageFormat.label(used: tokens, limit: window, remainingWord: String(localized: "remaining"))
    }

    static func compact(_ value: Int) -> String { ContextUsageFormat.tokens(value) }
}

/// The transient "long-press the mic" reminder, placed directly above the
/// composer card. Shared so the room shows it on exactly the same policy as
/// the chat instead of hiding a feature nobody can discover.
struct ComposerDictationHint: View {
    @Binding var state: DictationState
    /// Open the dictation-language picker (same destination as the gesture).
    let open: () -> Void

    var body: some View {
        if state.showingHint {
            DictationLanguageHint(
                onTap: open,
                onExpire: { withAnimation(CoreHubTokens.Motion.drawer) { state.showingHint = false } }
            )
            .transition(.opacity.combined(with: .move(edge: .bottom)))
        }
    }
}

/// The room's counter: what the room has spent so far, with no bar.
///
/// A room has no single context window — each seat carries its own model and
/// its own window — so the chat's "used / limit · remaining" cannot be drawn
/// here without inventing the limit. The total is the number the server
/// actually reports (`room_updated.totalTokens`), and it is hidden until the
/// room has reported one.
struct TokenTotalView: View {
    let tokens: Int

    static func label(_ tokens: Int) -> String {
        String(format: String(localized: "%@ tokens"), ContextUsageFormat.tokens(tokens))
    }

    var body: some View {
        if tokens > 0 {
            Text(Self.label(tokens))
                .font(CoreHubTokens.Typography.metaFont)
                .foregroundStyle(CoreHubTokens.Palette.textMuted)
                .lineLimit(1)
                .technicalDirection()
                .accessibilityLabel(Text("Tokens in context"))
                .accessibilityValue(Text(ContextUsageFormat.tokens(tokens)))
        }
    }
}
