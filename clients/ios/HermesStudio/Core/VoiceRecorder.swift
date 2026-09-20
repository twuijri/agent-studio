import AVFoundation
import Foundation

/// Records microphone audio as 16 kHz mono 16-bit Linear PCM WAV, the format
/// every Core Hub speech provider accepts through `/api/studio/stt/transcribe`.
@MainActor
final class VoiceRecorder: NSObject, ObservableObject, AVAudioRecorderDelegate {
    @Published var isRecording = false
    @Published var elapsed: TimeInterval = 0
    /// Live input level for the recording strip on the server path, from the
    /// recorder's own metering (≈ 20 Hz). Flat while not recording.
    @Published private(set) var waveform = RecordingWaveform()
    private var recorder: AVAudioRecorder?
    private var timer: Timer?
    private var outputURL: URL?

    enum RecorderError: LocalizedError {
        case microphoneDenied
        case startFailed

        var errorDescription: String? {
            switch self {
            case .microphoneDenied: return String(localized: "Microphone access is required for voice input. Enable it in Settings → Core Hub → Microphone.")
            case .startFailed: return String(localized: "The microphone could not start recording.")
            }
        }
    }

    static let wavSettings: [String: Any] = [
        AVFormatIDKey: Int(kAudioFormatLinearPCM),
        AVSampleRateKey: 16_000.0,
        AVNumberOfChannelsKey: 1,
        AVLinearPCMBitDepthKey: 16,
        AVLinearPCMIsFloatKey: false,
        AVLinearPCMIsBigEndianKey: false,
    ]

    static func requestMicrophonePermission() async -> Bool {
        await withCheckedContinuation { continuation in
            AVAudioApplication.requestRecordPermission { continuation.resume(returning: $0) }
        }
    }

    /// Starts a new WAV recording. Every failure is thrown so the caller can
    /// show it; nothing fails silently.
    func start() async throws {
        guard !isRecording else { return }
        guard await Self.requestMicrophonePermission() else { throw RecorderError.microphoneDenied }
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.record, mode: .spokenAudio)
        try session.setActive(true)
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("hermes-\(UUID().uuidString).wav")
        let recorder = try AVAudioRecorder(url: url, settings: Self.wavSettings)
        recorder.delegate = self
        recorder.isMeteringEnabled = true
        guard recorder.record() else {
            try? session.setActive(false)
            throw RecorderError.startFailed
        }
        self.recorder = recorder; outputURL = url; elapsed = 0; isRecording = true
        waveform.reset()
        timer = Timer.scheduledTimer(withTimeInterval: AudioLevelMeter.publishInterval, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.meter() }
        }
    }

    /// One tick of the recording: the elapsed time and one bar of the strip.
    private func meter() {
        guard let recorder else { return }
        elapsed = recorder.currentTime
        recorder.updateMeters()
        waveform.push(AudioLevelMeter.normalized(decibels: Double(recorder.averagePower(forChannel: 0))))
    }

    /// Stops recording and returns the WAV file, or `nil` when nothing was recorded.
    func stop() -> URL? {
        recorder?.stop(); recorder = nil; timer?.invalidate(); timer = nil; isRecording = false
        waveform.reset()
        try? AVAudioSession.sharedInstance().setActive(false)
        let url = outputURL; outputURL = nil
        return url
    }
}

@MainActor
final class SpeechPlayer: NSObject, ObservableObject, AVAudioPlayerDelegate {
    @Published var isPlaying = false
    private var player: AVAudioPlayer?

    func play(_ data: Data) throws {
        stop()
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playback, mode: .spokenAudio)
        try session.setActive(true)
        let player = try AVAudioPlayer(data: data)
        player.delegate = self
        player.prepareToPlay()
        player.play()
        self.player = player
        isPlaying = true
    }

    func stop() {
        player?.stop(); player = nil; isPlaying = false
        try? AVAudioSession.sharedInstance().setActive(false)
    }

    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor in stop() }
    }
}
