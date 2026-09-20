import Foundation
import SwiftUI

/// Dictation, once, for every screen that has a composer.
///
/// The mic used to exist only on the conversation screen, with its flow
/// written inline in `ConversationView`. It is here now so a group room gets
/// the identical behaviour — the same on-device/server decision, the same
/// fallbacks, the same long-press language picker and the same hint policy —
/// instead of a second implementation that drifts.

/// The bookkeeping one screen keeps for dictation.
struct DictationState: Equatable {
    var voice: ComposerVoiceState = .idle
    /// Composer text captured when dictation started; partial results are
    /// written after it, so a re-send of the same partial replaces itself.
    var base = ""
    /// Provider named by `/stt/profile-status` for the current recording.
    var serverProvider = ""
    /// `language` sent with the current upload; `nil` when the owner asked
    /// the server to detect the language itself.
    var serverLanguageHint: String?
    /// The transient "long-press the mic" reminder is on screen.
    /// `DictationHintPolicy` decides when; this only says it is visible.
    var showingHint = false
    var showingLanguagePicker = false
    /// A dictated draft is waiting to be sent. Chat speaks the reply back;
    /// a room has no TTS path and ignores this.
    var replyPending = false
}

/// Where the live transcript goes: into the field itself, never a separate
/// preview buffer. Pure, so the rule is unit-tested.
enum DictationText {
    /// Interim results replace the segment after `base`. A single space is
    /// inserted only when the draft does not already end in whitespace, and
    /// an empty transcript restores the draft untouched.
    static func merged(base: String, transcript: String) -> String {
        guard !transcript.isEmpty else { return base }
        let separator = base.isEmpty || base.hasSuffix(" ") || base.hasSuffix("\n") ? "" : " "
        return base + separator + transcript
    }
}

/// The recording strip that replaces the pill row while the microphone is
/// open (✕ cancel · waveform · ■ stop · ↑ send). Pure rules, unit-tested;
/// `DictationRunner` carries them out and `RecordingStripView` draws them.
enum RecordingStrip {
    /// How a take ends.
    enum Exit: Equatable {
        /// ✕: discard what was dictated; the draft before the take comes back.
        case cancel
        /// ■: keep the text, wait for the final transcript; the pill row returns.
        case stop
        /// ↑: keep the text and send it now; no final transcript is awaited.
        case send
    }

    struct Resolution: Equatable {
        /// What is left in the field.
        let text: String
        /// The field goes out now.
        let sends: Bool
        /// The recogniser is still finishing (`.transcribing`), so the mic
        /// stays disabled until the final result lands.
        let awaitsFinal: Bool
    }

    /// The strip is on screen only while the microphone is open. While the
    /// final transcript is awaited, and after an error, the pill row is back
    /// with the mic showing that state.
    static func isVisible(_ voice: ComposerVoiceState) -> Bool { voice == .listening }

    /// `base` is the draft captured when the take began (`DictationState.base`)
    /// and `current` the field with the partial transcript merged after it.
    static func resolve(_ exit: Exit, base: String, current: String) -> Resolution {
        switch exit {
        case .cancel: return Resolution(text: base, sends: false, awaitsFinal: false)
        case .stop: return Resolution(text: current, sends: false, awaitsFinal: true)
        case .send: return Resolution(text: current, sends: true, awaitsFinal: false)
        }
    }
}

/// One dictation take across the recogniser's requests.
///
/// Apple ends a `SFSpeechAudioBufferRecognitionRequest` on its own: after
/// about a minute of audio when the server does the recognition, and at
/// times after a long pause ("no speech detected"), with a final result or
/// an error. Neither is the user's gesture, so while the take is open an
/// ended request is **restarted transparently**: its text is committed
/// (`DictationText.merged`'s separator rules), the next request's partials
/// are appended after it, and the strip never moves. The take ends only on
/// ■ / ↑ / ✕ (`stop()` here, `cancel()` on the recogniser), on a real
/// failure, or at `ceiling`. Pure, so the machine is unit-tested
/// (`ContinuousDictationTests`); `OnDeviceSpeechRecognizer` drives it.
struct DictationTake: Equatable {
    enum Phase: Equatable {
        /// The microphone is open; an ended request is restarted.
        case listening
        /// ■ (or the ceiling) ended the take; the open request's final text
        /// is awaited and then the take is over.
        case stopping
        case ended
    }

    /// How a request ended, and the state of the world at that moment.
    struct RequestEnd: Equatable {
        /// The final transcript when the request closed with one; `nil` when
        /// it closed with an error, in which case the partial heard so far
        /// stands in for it.
        var finalText: String?
        var failed = false
        /// Speech permission is still granted.
        var authorized = true
        /// The recogniser for the locale still reports itself available.
        var recognizerAvailable = true
        /// The audio engine is still running (an interruption stops it).
        var engineRunning = true
        /// Seconds the request lived.
        var requestDuration: TimeInterval = 30
    }

    enum Outcome: Equatable {
        /// Open the next request on the same engine and tap.
        case restart
        /// The take is over and `text` is what the field keeps.
        case finish
        /// A real failure ended the take; `text` is kept, the error is shown.
        case fail
    }

    /// The longest take: after ten minutes the recogniser stops it as ■
    /// would and the caller shows a notice.
    static let ceiling: TimeInterval = 600
    /// A request that dies with nothing heard in under this many seconds is
    /// a failed restart, not a pause.
    static let shortRequest: TimeInterval = 2
    /// That many failed restarts in a row mean the recogniser is not coming
    /// back (no network for the server, a dead session) and the take fails.
    static let maxShortFailures = 3

    /// Text of every request that has ended, merged in order.
    private(set) var committed = ""
    /// Partial text of the request now open.
    private(set) var partial = ""
    private(set) var phase: Phase = .listening
    /// Requests opened so far, the first included.
    private(set) var requests = 1
    private(set) var shortFailures = 0

    /// What the field shows after the caller's own base.
    var text: String { DictationText.merged(base: committed, transcript: partial) }

    static func reachedCeiling(elapsed: TimeInterval) -> Bool { elapsed >= ceiling }

    /// A partial result of the open request. Still accepted after `stop()`
    /// so the finish timeout can deliver the latest text.
    mutating func partialResult(_ text: String) {
        guard phase != .ended else { return }
        partial = text
    }

    /// ■: the take ends with the open request's final result.
    mutating func stop() {
        if phase == .listening { phase = .stopping }
    }

    /// The open request closed. Commits what it heard and says what to do.
    mutating func requestEnded(_ end: RequestEnd) -> Outcome {
        guard phase != .ended else { return .finish }
        let heard = end.finalText ?? partial
        committed = DictationText.merged(base: committed, transcript: heard)
        partial = ""
        if phase == .stopping {
            phase = .ended
            return end.failed && committed.isEmpty ? .fail : .finish
        }
        guard end.authorized, end.recognizerAvailable, end.engineRunning else {
            phase = .ended
            return .fail
        }
        if end.failed, heard.isEmpty, end.requestDuration < Self.shortRequest {
            shortFailures += 1
            if shortFailures >= Self.maxShortFailures {
                phase = .ended
                return .fail
            }
        } else {
            shortFailures = 0
        }
        requests += 1
        return .restart
    }
}

/// The whole microphone flow. Holds no state of its own: the screen passes
/// the bindings it already owns, so both composers run this exact code.
@MainActor
struct DictationRunner {
    let store: AppStore
    /// Profile the dictation language, the counters and the STT provider are
    /// resolved against.
    let profile: String
    let recorder: VoiceRecorder
    let speech: OnDeviceSpeechRecognizer
    let state: Binding<DictationState>
    /// The composer draft.
    let text: Binding<String>
    /// Put the keyboard focus back on the field (or take it away).
    let focus: (Bool) -> Void

    // MARK: - Entry points

    /// The mic button. Starts a recording, or finishes the one in progress.
    func toggle() async {
        switch state.wrappedValue.voice {
        case .listening: stop(); return
        case .transcribing: return
        case .idle, .error: break
        }
        store.errorMessage = nil
        await start()
        // Only a recording that actually opened the microphone counts, and
        // only then can the hint appear: a failed start already has a banner
        // of its own and must not also carry a tip.
        guard state.wrappedValue.voice == .listening else { return }
        let show = store.registerDictationAndShouldHint(profile: profile)
        withAnimation(CoreHubTokens.Motion.drawer) { state.wrappedValue.showingHint = show }
    }

    /// The long press (and the hint's own tap). Opens the language picker and
    /// records that the gesture has been found, which retires the hint.
    func openLanguagePicker() {
        store.markDictationLongPressUsed(profile: profile)
        withAnimation(CoreHubTokens.Motion.drawer) { state.wrappedValue.showingHint = false }
        state.wrappedValue.showingLanguagePicker = true
    }

    /// The strip's ✕. Stops recognition without waiting for a final result
    /// and puts back the draft that existed before the take — exactly, with
    /// whatever trailing space or newline it had, because
    /// `DictationText.merged` only ever appended after it.
    func cancel() {
        withAnimation(CoreHubTokens.Motion.drawer) { state.wrappedValue.showingHint = false }
        if speech.isActive { speech.cancel() }
        if recorder.isRecording, let url = recorder.stop() { try? FileManager.default.removeItem(at: url) }
        text.wrappedValue = RecordingStrip.resolve(.cancel, base: state.wrappedValue.base, current: text.wrappedValue).text
        state.wrappedValue.voice = .idle
        state.wrappedValue.replyPending = false
        focus(true)
    }

    /// Sending always ends dictation (the strip's ↑ included); the text
    /// already in the field is what goes out, and nothing is ever sent
    /// automatically.
    func endForSend() {
        if speech.isActive { speech.cancel() }
        if recorder.isRecording { _ = recorder.stop() }
        state.wrappedValue.voice = .idle
        state.wrappedValue.showingHint = false
    }

    /// Leaving the screen: stop the microphone and take the hint away.
    func endForDisappear() {
        speech.cancel()
        if recorder.isRecording { _ = recorder.stop() }
        state.wrappedValue.voice = .idle
        state.wrappedValue.showingHint = false
    }

    // MARK: - Starting

    /// Picks the transcription path for this recording. The dictation
    /// language is per profile and decides both: the recogniser's locale
    /// here, and the `language` hint on the server.
    private func start() async {
        let plan = store.speechPlan(for: profile)
        if plan.requiresServer || store.voiceInput == Preferences.voiceInputServer {
            await startServer(plan: plan); return
        }
        guard let locale = plan.deviceLocaleIdentifier,
              OnDeviceSpeechRecognizer.isAvailable(localeIdentifier: locale) else {
            store.notify(String(format: String(localized: "On-device speech is unavailable for %@; using the Core Hub server"),
                                SpeechLocaleCatalog.endonym(for: plan.fallbackLocaleIdentifier)))
            await startServer(plan: plan); return
        }
        await startDevice(localeIdentifier: locale)
    }

    /// `allowServerFallback` is false when the device path is itself already
    /// the fallback from a failed server attempt, so the two cannot bounce.
    private func startDevice(localeIdentifier: String, allowServerFallback: Bool = true) async {
        state.wrappedValue.base = text.wrappedValue
        do {
            // `partial` is the whole take (text committed by requests Apple
            // ended on its own, plus the open request's partial), so it is
            // always written after `base`; a restart inside the recogniser
            // changes nothing here and the strip stays. `isFinal` comes only
            // from ■, ↑ handled elsewhere, the ceiling, or a real failure.
            try await speech.start(localeIdentifier: localeIdentifier) { partial, isFinal in
                apply(partial)
                guard isFinal else { return }
                if let failure = speech.lastError, partial.isEmpty {
                    store.errorMessage = failure
                    state.wrappedValue.voice = .error
                } else {
                    state.wrappedValue.voice = .idle
                    state.wrappedValue.replyPending = !partial.isEmpty
                    if let failure = speech.lastError {
                        // The take ended for a real reason but the text is
                        // kept; say why the strip closed.
                        store.notify(failure)
                    } else if speech.endedAtCeiling {
                        store.notify(String(format: String(localized: "Dictation stopped after %lld minutes. Tap the microphone to continue."),
                                            Int(DictationTake.ceiling / 60)))
                    }
                }
                focus(true)
            }
            state.wrappedValue.voice = .listening
        } catch OnDeviceSpeechRecognizer.Failure.notAuthorized where allowServerFallback {
            store.notify(String(localized: "Speech permission was not granted; using the Core Hub server"))
            await startServer(plan: store.speechPlan(for: profile))
        } catch OnDeviceSpeechRecognizer.Failure.unavailable where allowServerFallback {
            store.notify(String(format: String(localized: "On-device speech is unavailable for %@; using the Core Hub server"),
                                SpeechLocaleCatalog.endonym(for: localeIdentifier)))
            await startServer(plan: store.speechPlan(for: profile))
        } catch {
            store.errorMessage = error.localizedDescription
            state.wrappedValue.voice = .error
        }
    }

    /// Server path: check `/api/studio/stt/profile-status` first, then record
    /// 16 kHz WAV. The plan's hint travels with the upload; server detection
    /// sends no hint at all.
    private func startServer(plan: SpeechLanguagePlan) async {
        do {
            let status = try await store.api.sttProfileStatus(profile: profile)
            guard status.configured, !status.activeProvider.isEmpty else {
                await serverUnavailable(plan: plan, reason: status.message); return
            }
            state.wrappedValue.serverProvider = status.activeProvider
            state.wrappedValue.serverLanguageHint = plan.serverLanguageHint
            try await recorder.start()
            state.wrappedValue.voice = .listening
        } catch {
            await serverUnavailable(plan: plan, reason: error.localizedDescription)
        }
    }

    /// The server cannot transcribe. Dictation is never dropped silently: the
    /// chosen (or keyboard-derived) language takes over on the device and the
    /// banner says why, and only a language the recogniser cannot serve either
    /// leaves the owner with an error.
    private func serverUnavailable(plan: SpeechLanguagePlan, reason: String) async {
        let fallback = plan.fallbackLocaleIdentifier
        guard OnDeviceSpeechRecognizer.isAvailable(localeIdentifier: fallback) else {
            store.errorMessage = reason
            state.wrappedValue.voice = .error
            return
        }
        store.errorMessage = String(format: String(localized: "The Core Hub server cannot transcribe right now (%1$@). Dictating in %2$@ on this iPhone instead."),
                                    reason, SpeechLocaleCatalog.endonym(for: fallback))
        await startDevice(localeIdentifier: fallback, allowServerFallback: false)
    }

    // MARK: - Finishing

    /// The strip's ■ (and the mic tap while listening): end the take and
    /// keep the text. On the device path the final transcript is still
    /// awaited; on the server path the WAV goes up for transcription.
    func stop() {
        withAnimation(CoreHubTokens.Motion.drawer) { state.wrappedValue.showingHint = false }
        if speech.isListening {
            speech.stop()
            state.wrappedValue.voice = .transcribing
            return
        }
        if recorder.isRecording { Task { await finishServer() } }
    }

    private func finishServer() async {
        guard let url = recorder.stop() else { state.wrappedValue.voice = .idle; return }
        state.wrappedValue.voice = .transcribing
        defer { try? FileManager.default.removeItem(at: url) }
        do {
            let data = try Data(contentsOf: url)
            let result = try await store.api.transcribe(wav: data,
                                                        provider: state.wrappedValue.serverProvider,
                                                        language: state.wrappedValue.serverLanguageHint,
                                                        profile: profile)
            state.wrappedValue.base = text.wrappedValue
            apply(result.text)
            state.wrappedValue.voice = .idle
            state.wrappedValue.replyPending = true
            focus(true)
        } catch {
            store.errorMessage = error.localizedDescription
            state.wrappedValue.voice = .error
        }
    }

    private func apply(_ transcript: String) {
        text.wrappedValue = DictationText.merged(base: state.wrappedValue.base, transcript: transcript)
    }
}
