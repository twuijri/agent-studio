import AVFoundation
import Foundation
import Speech

/// Live speech-to-text on the iPhone (Apple Speech + AVAudioEngine).
/// Partial results are delivered while the user speaks; the final text is
/// delivered once after `stop()`.
@MainActor
final class OnDeviceSpeechRecognizer: ObservableObject {
    enum Failure: LocalizedError {
        case unavailable
        case notAuthorized
        case engineFailed(String)

        var errorDescription: String? {
            switch self {
            case .unavailable: return String(localized: "On-device speech recognition is not available for this language right now.")
            case .notAuthorized: return String(localized: "Speech recognition permission is required for voice input. Enable it in Settings → H Studio → Speech Recognition.")
            case let .engineFailed(detail): return String(localized: "The microphone could not be started for speech recognition: \(detail)")
            }
        }
    }

    /// Microphone is open and partial results are flowing.
    @Published private(set) var isListening = false
    /// `stop()` was called and the final result has not arrived yet.
    @Published private(set) var isFinishing = false
    @Published private(set) var transcript = ""
    /// Set when recognition ended with an error and produced no text.
    @Published var lastError: String?

    private let audioEngine = AVAudioEngine()
    private var recognizer: SFSpeechRecognizer?
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private var onUpdate: ((String, Bool) -> Void)?
    private var finishTimeout: Task<Void, Never>?

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
    /// every partial result and once more with the final text.
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

        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        request.taskHint = .dictation

        let inputNode = audioEngine.inputNode
        let format = inputNode.outputFormat(forBus: 0)
        inputNode.removeTap(onBus: 0)
        inputNode.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in
            request.append(buffer)
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
        self.request = request
        self.onUpdate = onUpdate
        transcript = ""; lastError = nil
        isListening = true; isFinishing = false

        task = recognizer.recognitionTask(with: request) { [weak self] result, error in
            Task { @MainActor in
                guard let self else { return }
                self.handle(result: result, error: error)
            }
        }
    }

    private func handle(result: SFSpeechRecognitionResult?, error: Error?) {
        guard isActive else { return }
        if let result {
            transcript = result.bestTranscription.formattedString
            onUpdate?(transcript, result.isFinal)
            if result.isFinal { finish() }
            return
        }
        if let error {
            // Apple reports "no speech detected" and similar as errors after
            // endAudio(). Deliver whatever was heard as final, and surface the
            // failure only when nothing at all was transcribed.
            if transcript.isEmpty { lastError = error.localizedDescription }
            onUpdate?(transcript, true)
            finish()
        }
    }

    /// Stops the microphone and waits for the final result.
    func stop() {
        guard isListening else { return }
        isListening = false
        isFinishing = true
        audioEngine.stop()
        audioEngine.inputNode.removeTap(onBus: 0)
        request?.endAudio()
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
        audioEngine.stop()
        audioEngine.inputNode.removeTap(onBus: 0)
        request?.endAudio()
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
        task = nil; request = nil; recognizer = nil; onUpdate = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
}
