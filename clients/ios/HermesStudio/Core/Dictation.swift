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

    /// Sending always ends dictation; the text already in the field is what
    /// goes out, and nothing is ever sent automatically.
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
            try await speech.start(localeIdentifier: localeIdentifier) { partial, isFinal in
                apply(partial)
                guard isFinal else { return }
                if let failure = speech.lastError, partial.isEmpty {
                    store.errorMessage = failure
                    state.wrappedValue.voice = .error
                } else {
                    state.wrappedValue.voice = .idle
                    state.wrappedValue.replyPending = !partial.isEmpty
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

    private func stop() {
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
