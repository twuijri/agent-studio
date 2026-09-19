import AVFoundation
import Foundation

/// Per-message text-to-speech: `POST /api/studio/tts/synthesize` (audio/mpeg)
/// played with `AVAudioPlayer`; when the server call fails the text is
/// spoken with `AVSpeechSynthesizer` instead. Only one message plays at a time.
@MainActor
final class MessageSpeaker: NSObject, ObservableObject, AVAudioPlayerDelegate, AVSpeechSynthesizerDelegate {
    enum State: Equatable { case idle, loading, playing, paused }

    @Published private(set) var lineID: UUID?
    @Published private(set) var state: State = .idle
    /// Set when the server voice failed and the device voice was used.
    @Published private(set) var usedFallback = false

    private var player: AVAudioPlayer?
    private let synthesizer = AVSpeechSynthesizer()
    private var loadTask: Task<Void, Never>?

    override init() {
        super.init()
        synthesizer.delegate = self
    }

    func isActive(_ id: UUID) -> Bool { lineID == id && state != .idle }
    func isPlaying(_ id: UUID) -> Bool { lineID == id && (state == .playing || state == .loading) }

    /// Play / pause / resume toggle for one message.
    func toggle(lineID id: UUID, text: String, languageCode: String?, synthesize: @escaping () async throws -> Data) {
        if lineID == id {
            switch state {
            case .playing: pause(); return
            case .paused: resume(); return
            case .loading: stop(); return
            case .idle: break
            }
        }
        play(lineID: id, text: text, languageCode: languageCode, synthesize: synthesize)
    }

    func play(lineID id: UUID, text: String, languageCode: String?, synthesize: @escaping () async throws -> Data) {
        stop()
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        lineID = id; state = .loading; usedFallback = false
        loadTask = Task { [weak self] in
            guard let self else { return }
            do {
                let data = try await synthesize()
                guard !Task.isCancelled, self.lineID == id else { return }
                try self.startPlayer(data)
            } catch {
                guard !Task.isCancelled, self.lineID == id else { return }
                self.usedFallback = true
                self.startFallback(text, languageCode: languageCode)
            }
        }
    }

    func pause() {
        if let player { player.pause(); state = .paused; return }
        if synthesizer.isSpeaking { synthesizer.pauseSpeaking(at: .word); state = .paused }
    }

    func resume() {
        if let player { player.play(); state = .playing; return }
        if synthesizer.isPaused { synthesizer.continueSpeaking(); state = .playing }
    }

    func stop() {
        loadTask?.cancel(); loadTask = nil
        player?.stop(); player = nil
        if synthesizer.isSpeaking || synthesizer.isPaused { synthesizer.stopSpeaking(at: .immediate) }
        state = .idle; lineID = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    private func startPlayer(_ data: Data) throws {
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playback, mode: .spokenAudio)
        try session.setActive(true)
        let player = try AVAudioPlayer(data: data)
        player.delegate = self
        player.prepareToPlay()
        player.play()
        self.player = player
        state = .playing
    }

    private func startFallback(_ text: String, languageCode: String?) {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .spokenAudio)
        try? session.setActive(true)
        let utterance = AVSpeechUtterance(string: text)
        if let languageCode, let voice = AVSpeechSynthesisVoice(language: languageCode) { utterance.voice = voice }
        synthesizer.speak(utterance)
        state = .playing
    }

    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor in self.stop() }
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        Task { @MainActor in if self.state != .idle { self.stop() } }
    }

    nonisolated func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        Task { @MainActor in if self.state != .idle { self.stop() } }
    }
}
