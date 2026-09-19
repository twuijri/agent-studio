import SwiftUI

/// The message box, described once.
///
/// The chat screen and a group room used to carry two hand-written composers
/// that drifted apart: the room had no microphone, no settings, its own
/// three-item attach menu and its own paddings. There is one composer now
/// (`ComposerCard`), and a screen says what it wants with a
/// `ComposerConfiguration`. Anything a room genuinely does not have is absent
/// from its configuration, never faked.

enum ComposerVoiceState: Equatable { case idle, listening, transcribing, error }

/// A switch the ⚙ pill can offer. A screen lists only the switches that do
/// something *on that screen*, so no menu row is a decoration.
enum ComposerToggle: String, CaseIterable, Identifiable, Equatable {
    /// Speak the agent's replies back (chat only: a room has no TTS path).
    case voiceMode
    /// Show tool calls in the transcript. Honoured by both screens.
    case showToolCalls
    /// Completion push notification, stored on the session.
    case push

    var id: String { rawValue }

    var label: String {
        switch self {
        case .voiceMode: return String(localized: "Voice mode")
        case .showToolCalls: return String(localized: "Show tool calls")
        case .push: return String(localized: "Push")
        }
    }
}

/// A pill in the toolbar's scrolling strip, in the order it is drawn.
enum ComposerPillKind: String, CaseIterable, Identifiable, Equatable {
    case mention, reasoning, settings, model
    var id: String { rawValue }
}

/// A round control between the pills and the send button.
enum ComposerControlKind: String, CaseIterable, Identifiable, Equatable {
    /// Send the draft after the run that is streaming (chat only).
    case queue
    /// The microphone, with the dictation-language long press.
    case dictation
    var id: String { rawValue }
}

/// What the card draws in its top corner.
enum ComposerCounter: Equatable {
    case none
    /// Used against the session's context window, with the bar.
    case context
    /// A running total and no bar: a room has no single context window —
    /// every seat carries its own — so a ratio there would be invented.
    case total
}

/// Which controls a screen's composer carries. Pure data with no behaviour,
/// so the two screens can be compared in a unit test instead of by eye.
struct ComposerConfiguration: Equatable {
    var placeholder: LocalizedStringKey
    var pills: [ComposerPillKind]
    /// Rows of the ⚙ pill; empty means the pill is not drawn at all.
    var toggles: [ComposerToggle]
    var controls: [ComposerControlKind]
    var counter: ComposerCounter
    /// The send button turns into a stop while a reply streams. True for a
    /// chat, which has exactly one run; false for a room, where several
    /// seats can be replying and each is interrupted from the activity strip.
    var stopsRun: Bool

    /// One conversation: everything a session owns.
    static let chat = ComposerConfiguration(
        placeholder: "Type a message…",
        pills: [.reasoning, .settings, .model],
        toggles: [.voiceMode, .showToolCalls, .push],
        controls: [.queue, .dictation],
        counter: .context,
        stopsRun: true
    )

    /// A group room: the @ menu takes the place of the session pills. The
    /// model and the reasoning effort belong to a session, and every seat
    /// carries its own model, so both stay in the room settings.
    static let room = ComposerConfiguration(
        placeholder: "Message the group…",
        pills: [.mention, .settings],
        toggles: [.showToolCalls],
        controls: [.dictation],
        counter: .total,
        stopsRun: false
    )

    /// The ⚙ pill is drawn only when it has rows to show.
    var showsSettings: Bool { pills.contains(.settings) && !toggles.isEmpty }
    /// Room above the first row for the corner counter.
    var topInset: CGFloat { counter == .none ? 14 : 22 }
    /// The control the room used to be missing entirely.
    var hasDictation: Bool { controls.contains(.dictation) }
}

/// Values the composer renders; the screen owns them.
struct ComposerState {
    /// An agent is producing a reply (the run in a chat, any seat in a room).
    var isRunning = false
    /// A send is in flight (a room waits for the socket acknowledgement).
    var sending = false
    /// False while the screen cannot take a message at all — a room that is
    /// not joined, or one the member was removed from.
    var canCompose = true
    var contextTokens = 0
    var contextWindow = 0
    var loadingContext = false
    /// Tokens the room has used so far (`ComposerCounter.total`).
    var totalTokens = 0
    var models: [ModelOption] = []
    var selectedModel = ""
    var reasoningEffort = ""
    var showToolCalls = true
    var voiceMode = false
    var pushEnabled = true
    /// Seat names (plus `all` when allowed) offered by the @ menu.
    var mentionNames: [String] = []
    var voiceState: ComposerVoiceState = .idle
    var isRecording = false
    var recordingElapsed: TimeInterval = 0
    /// Dictation language of the active profile, shown while the mic is open.
    var speechLanguage = ""

    func isOn(_ toggle: ComposerToggle) -> Bool {
        switch toggle {
        case .voiceMode: return voiceMode
        case .showToolCalls: return showToolCalls
        case .push: return pushEnabled
        }
    }
}

struct ComposerActions {
    var send: () -> Void = {}
    var stop: () -> Void = {}
    var queue: () -> Void = {}
    var mic: () -> Void = {}
    /// Long press on the mic: pick the dictation language without leaving the screen.
    var micLanguage: () -> Void = {}
    var attachCamera: () -> Void = {}
    var attachPhotos: () -> Void = {}
    var attachFiles: () -> Void = {}
    var cancelUpload: (String) -> Void = { _ in }
    var selectModel: (ModelOption) -> Void = { _ in }
    var selectReasoning: (String) -> Void = { _ in }
    var toggle: (ComposerToggle) -> Void = { _ in }
    var insertMention: (String) -> Void = { _ in }
    var cancelReference: () -> Void = {}
}
