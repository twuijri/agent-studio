import AVFoundation
import Foundation

/// Microphone level for the recording strip: one normalised number 0…1,
/// about twenty times a second, read from the audio the recogniser (or the
/// server-path recorder) is already receiving. No second audio session and
/// no second tap; the level is a by-product of the buffers that are flowing
/// anyway.
///
/// The arithmetic is pure so it is unit-tested (`RecordingStripTests`); only
/// `AudioLevelTap` touches an `AVAudioPCMBuffer`.
enum AudioLevelMeter {
    /// Silence, in dBFS. Anything quieter draws as a dot.
    static let floorDecibels: Double = -50
    /// Loud speech close to the microphone; anything louder is clamped.
    static let ceilingDecibels: Double = -10
    /// How often a level is published (≈ 20 Hz), and so one bar's worth of time.
    static let publishInterval: TimeInterval = 0.05
    /// Per-tick release: a bar falls to about 10 % in half a second while it
    /// rises instantly, so a pause reads as a fade rather than a cliff.
    static let release: Double = 0.8
    /// Below this a bar is idle and drawn as a dot at the idle alpha.
    static let idleThreshold: Double = 0.06

    /// Root mean square of one buffer of linear samples (0 when empty).
    static func rms<S: Sequence>(_ samples: S) -> Double where S.Element == Float {
        var sum: Double = 0
        var count = 0
        for sample in samples {
            let value = Double(sample)
            sum += value * value
            count += 1
        }
        guard count > 0 else { return 0 }
        return (sum / Double(count)).squareRoot()
    }

    /// dBFS of a linear RMS value; silence is `-infinity`, which normalises to 0.
    static func decibels(rms: Double) -> Double {
        guard rms > 0 else { return -Double.infinity }
        return 20 * log10(rms)
    }

    /// Maps dBFS onto 0…1 between the floor and the ceiling, clamped.
    static func normalized(decibels: Double) -> Double {
        guard decibels.isFinite else { return 0 }
        let span = ceilingDecibels - floorDecibels
        return min(1, max(0, (decibels - floorDecibels) / span))
    }

    /// Linear RMS straight to the strip's 0…1.
    static func level(rms: Double) -> Double { normalized(decibels: decibels(rms: rms)) }

    /// Instant attack, exponential release, never below the incoming level.
    static func smoothed(previous: Double, incoming: Double) -> Double {
        incoming >= previous ? incoming : max(incoming, previous * release)
    }
}

/// The last ~1.8 s of levels, newest last, one bar per published level. It
/// starts as a flat line of dots and scrolls as levels arrive; `push` smooths
/// each new bar against the one before it so silence fades instead of
/// dropping. Value type, so a screen can hand it to the composer as state.
struct RecordingWaveform: Equatable {
    static let barCount = CoreHubTokens.Layout.waveformBarCount

    private(set) var levels: [Double]

    init() { levels = Array(repeating: 0, count: Self.barCount) }

    /// The newest (smoothed) level, for the accessibility value.
    var current: Double { levels.last ?? 0 }

    /// Appends a level and drops the oldest, so the count never changes.
    mutating func push(_ level: Double) {
        let next = AudioLevelMeter.smoothed(previous: current, incoming: min(1, max(0, level)))
        levels.removeFirst()
        levels.append(next)
    }

    mutating func reset() { levels = Array(repeating: 0, count: Self.barCount) }

    /// An idle bar is drawn as a dot.
    static func isIdle(_ level: Double) -> Bool { level < AudioLevelMeter.idleThreshold }
}

/// Reads the level of each buffer on the audio thread and hands a normalised
/// level to `sink` at most once per `AudioLevelMeter.publishInterval`.
/// Called from inside the tap `OnDeviceSpeechRecognizer` already installs,
/// so it adds no audio plumbing of its own. The tap block is invoked
/// serially, which is why `lastEmit` needs no lock.
final class AudioLevelTap {
    private let sink: (Double) -> Void
    private var lastEmit: TimeInterval = 0

    init(sink: @escaping (Double) -> Void) { self.sink = sink }

    func process(_ buffer: AVAudioPCMBuffer) {
        let now = ProcessInfo.processInfo.systemUptime
        guard now - lastEmit >= AudioLevelMeter.publishInterval else { return }
        lastEmit = now
        guard let channel = buffer.floatChannelData?[0] else { return }
        let samples = UnsafeBufferPointer(start: channel, count: Int(buffer.frameLength))
        sink(AudioLevelMeter.level(rms: AudioLevelMeter.rms(samples)))
    }
}
