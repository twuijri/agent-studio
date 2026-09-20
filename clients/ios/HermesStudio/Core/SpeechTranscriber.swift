import AVFoundation
import Foundation
import Speech

/// Live speech-to-text on the iPhone (Apple Speech + AVAudioEngine).
/// Partial results are delivered while the user speaks; the final text is
/// delivered once after `stop()`.
///
/// One take, many requests. Apple ends a
/// `SFSpeechAudioBufferRecognitionRequest` on its own — after about a minute
/// of audio when the server does the recognition, and sometimes after a
/// long pause ("no speech detected") — with a final result or an error.
/// None of that is the user's gesture, so it never ends the take: the audio
/// engine and its tap stay up, the ended request's text is committed, a new
/// request takes over the same tap without a gap, and the next partials are
/// appended after the committed text. `DictationTake` (pure, in
/// `Core/Dictation.swift`) decides restart / finish / fail; this class only
/// carries it out. The take ends on ■ / ↑ / ✕, on a real failure, or at
/// `DictationTake.ceiling`.
@MainActor
final class OnDeviceSpeechRecognizer: ObservableObject {
    enum Failure: LocalizedError {
        case unavailable
        case notAuthorized
        case engineFailed(String)

        var errorDescription: String? {
            switch self {
            case .unavailable: return String(localized: "On-device speech recognition is not available for this language right now.")
            case .notAuthorized: return String(localized: "Speech recognition permission is required for voice input. Enable it in Settings → Core Hub → Speech Recognition.")
            case let .engineFailed(detail): return String(localized: "The microphone could not be started for speech recognition: \(detail)")
            }
        }
    }

    /// Microphone is open and partial results are flowing.
    @Published private(set) var isListening = false
    /// `stop()` was called and the final result has not arrived yet.
    @Published private(set) var isFinishing = false
    /// The whole take so far: the text committed by ended requests plus the
    /// partial of the request now open.
    @Published private(set) var transcript = ""
    /// Set when recognition ended with an error and produced no text.
    @Published var lastError: String?
    /// The take was stopped by the recogniser at `DictationTake.ceiling`,
    /// not by the user; the final result still lands the usual way.
    @Published private(set) var endedAtCeiling = false
    /// Live input level for the recording strip, read from the same tap that
    /// feeds the recogniser (≈ 20 Hz). Flat while the microphone is closed.
    @Published private(set) var waveform = RecordingWaveform()

    private let audioEngine = AVAudioEngine()
    /// The request the tap feeds right now; swapped at every restart.
    private let feed = SpeechRequestFeed()
    private var recognizer: SFSpeechRecognizer?
    private var task: SFSpeechRecognitionTask?
    private var onUpdate: ((String, Bool) -> Void)?
    private var finishTimeout: Task<Void, Never>?
    private var ceilingTimer: Task<Void, Never>?
    private var take = DictationTake()
    /// Increments per request; a callback from a superseded request is ignored.
    private var generation = 0
    private var requestStartedAt: TimeInterval = 0

    var isActive: Bool { isListening || isFinishing }

    /// `false` when Apple Speech cannot serve this locale (offline, unsupported
    /// language, or the recognizer is temporarily busy). Callers fall back to
    /// the Core Hub server path in that case.
    static func isAvailable(localeIdentifier: String) -> Bool {
        guard let recognizer = SFSpeechRecognizer(locale: Locale(identifier: localeIdentifier)) else { return false }
        return recognizer.isAvailable
    }

    static func requestAuthorization() async -> SFSpeechRecognizerAuthorizationStatus {
        await withCheckedContinuation { continuation in
            SFSpeechRecognizer.requestAuthorization { continuation.resume(returning: $0) }
        }
    }

    /// Starts listening. `onUpdate(text, isFinal)` runs on the main actor for
    /// every partial result and once more with the final text. `text` is
    /// always the whole take, so the caller writes it after its own base.
    func start(localeIdentifier: String, onUpdate: @escaping (String, Bool) -> Void) async throws {
        cancel()
        guard let recognizer = SFSpeechRecognizer(locale: Locale(identifier: localeIdentifier)), recognizer.isAvailable else {
            throw Failure.unavailable
        }
        let status = await Self.requestAuthorization()
        guard status == .authorized else { throw Failure.notAuthorized }
        guard await VoiceRecorder.requestMicrophonePermission() else { throw VoiceRecorder.RecorderError.microphoneDenied }

        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.record, mode: .measurement, options: .duckOthers)
        try session.setActive(true, options: .notifyOthersOnDeactivation)

        let inputNode = audioEngine.inputNode
        let format = inputNode.outputFormat(forBus: 0)
        inputNode.removeTap(onBus: 0)
        // One tap, two readers: whichever request is open gets every buffer,
        // the strip gets a level from the same buffer about twenty times a
        // second. The tap is installed once per take and outlives every
        // request, so a restart never drops the waveform.
        let feed = self.feed
        let meter = AudioLevelTap { [weak self] decibels in
            Task { @MainActor in self?.waveform.push(decibels: decibels) }
        }
        inputNode.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in
            feed.append(buffer)
            meter.process(buffer)
        }
        audioEngine.prepare()
        do {
            try audioEngine.start()
        } catch {
            inputNode.removeTap(onBus: 0)
            try? session.setActive(false, options: .notifyOthersOnDeactivation)
            throw Failure.engineFailed(error.localizedDescription)
        }

        self.recognizer = recognizer
        self.onUpdate = onUpdate
        take = DictationTake()
        transcript = ""; lastError = nil; endedAtCeiling = false
        waveform.reset()
        isListening = true; isFinishing = false
        openRequest()

        ceilingTimer?.cancel()
        ceilingTimer = Task { [weak self] in
            try? await Task.sleep(for: .seconds(DictationTake.ceiling))
            guard !Task.isCancelled, let self, self.isListening else { return }
            self.endedAtCeiling = true
            self.stop()
        }
    }

    /// Opens the next request on the running engine. The new request is put
    /// on the tap *before* the old one is ended, so no buffer falls between
    /// them.
    private func openRequest() {
        guard let recognizer else { return }
        generation += 1
        let gen = generation
        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        request.taskHint = .dictation
        feed.swap(request)?.endAudio()
        requestStartedAt = ProcessInfo.processInfo.systemUptime
        task = recognizer.recognitionTask(with: request) { [weak self] result, error in
            Task { @MainActor in
                guard let self else { return }
                self.handle(generation: gen, result: result, error: error)
            }
        }
    }

    private func handle(generation gen: Int, result: SFSpeechRecognitionResult?, error: Error?) {
        guard gen == generation, isActive else { return }
        if let result {
            let text = result.bestTranscription.formattedString
            if result.isFinal {
                requestEnded(finalText: text, error: nil)
            } else {
                take.partialResult(text)
                transcript = take.text
                onUpdate?(transcript, false)
            }
            return
        }
        if let error {
            // Apple reports "no speech detected", the per-request limit and
            // similar as errors; while the take is open they are a restart,
            // not the end. `DictationTake` tells them apart from real ones.
            requestEnded(finalText: nil, error: error)
        }
    }

    private func requestEnded(finalText: String?, error: Error?) {
        let end = DictationTake.RequestEnd(
            finalText: finalText,
            failed: error != nil,
            authorized: SFSpeechRecognizer.authorizationStatus() == .authorized,
            recognizerAvailable: recognizer?.isAvailable ?? false,
            engineRunning: audioEngine.isRunning,
            requestDuration: ProcessInfo.processInfo.systemUptime - requestStartedAt
        )
        let outcome = take.requestEnded(end)
        transcript = take.text
        switch outcome {
        case .restart:
            onUpdate?(transcript, false)
            openRequest()
        case .finish:
            onUpdate?(transcript, true)
            finish()
        case .fail:
            // Surface the failure only when nothing at all was transcribed;
            // with text in the field the caller keeps it and just tells why
            // the take ended.
            lastError = error?.localizedDescription ?? Failure.unavailable.localizedDescription
            onUpdate?(transcript, true)
            finish()
        }
    }

    /// Stops the microphone and waits for the final result.
    func stop() {
        guard isListening else { return }
        isListening = false
        isFinishing = true
        take.stop()
        ceilingTimer?.cancel(); ceilingTimer = nil
        audioEngine.stop()
        audioEngine.inputNode.removeTap(onBus: 0)
        waveform.reset()
        feed.swap(nil)?.endAudio()
        finishTimeout?.cancel()
        finishTimeout = Task { [weak self] in
            try? await Task.sleep(for: .seconds(4))
            guard !Task.isCancelled, let self, self.isFinishing else { return }
            // The final callback never came; the partial text is already in the composer.
            self.onUpdate?(self.transcript, true)
            self.finish()
        }
    }

    /// Discards the current recognition without delivering a final result.
    func cancel() {
        guard isActive else { return }
        isListening = false
        isFinishing = false
        onUpdate = nil
        feed.swap(nil)?.endAudio()
        task?.cancel()
        cleanUp()
    }

    private func finish() {
        isListening = false
        isFinishing = false
        cleanUp()
    }

    private func cleanUp() {
        finishTimeout?.cancel(); finishTimeout = nil
        ceilingTimer?.cancel(); ceilingTimer = nil
        // Idempotent after `stop()`; needed when a failure ends the take
        // while the engine is still running.
        audioEngine.stop()
        audioEngine.inputNode.removeTap(onBus: 0)
        waveform.reset()
        feed.swap(nil)?.endAudio()
        task = nil; recognizer = nil; onUpdate = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
}

/// The hand-over point between the audio thread and the main actor: the tap
/// appends every buffer to whichever request is current, and a restart swaps
/// the request under a lock instead of touching actor-isolated state from
/// the render thread.
final class SpeechRequestFeed: @unchecked Sendable {
    private let lock = NSLock()
    private var request: SFSpeechAudioBufferRecognitionRequest?

    func append(_ buffer: AVAudioPCMBuffer) {
        lock.lock()
        let current = request
        lock.unlock()
        current?.append(buffer)
    }

    /// Installs `next` (or nothing) and returns the request it replaced.
    @discardableResult
    func swap(_ next: SFSpeechAudioBufferRecognitionRequest?) -> SFSpeechAudioBufferRecognitionRequest? {
        lock.lock()
        defer { lock.unlock() }
        let previous = request
        request = next
        return previous
    }
}
