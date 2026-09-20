import AVFoundation
import Foundation

/// Microphone level for the recording strip: one normalised number 0…1,
/// about twenty times a second, read from the audio the recogniser (or the
/// server-path recorder) is already receiving. No second audio session and
/// no second tap; the level is a by-product of the buffers that are flowing
/// anyway.
///
/// The arithmetic is pure so it is unit-tested (`RecordingStripTests`,
/// `ContinuousDictationTests`); only `AudioLevelTap` touches an
/// `AVAudioPCMBuffer`.
enum AudioLevelMeter {
    /// Silence, in dBFS, for a *processed* input (the server-path recorder's
    /// `averagePower`, which runs with the system's automatic gain).
    /// Anything quieter draws as a dot.
    static let floorDecibels: Double = -50
    /// Loud speech close to the microphone on a processed input; anything
    /// louder is clamped.
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

    /// Maps dBFS onto 0…1 between the fixed floor and ceiling, clamped. Only
    /// right for a processed input; the recogniser's raw tap goes through
    /// `AudioLevelRange` instead (see there for why).
    static func normalized(decibels: Double) -> Double {
        guard decibels.isFinite else { return 0 }
        let span = ceilingDecibels - floorDecibels
        return min(1, max(0, (decibels - floorDecibels) / span))
    }

    /// Linear RMS straight to the fixed window's 0…1.
    static func level(rms: Double) -> Double { normalized(decibels: decibels(rms: rms)) }

    /// Instant attack, exponential release, never below the incoming level.
    static func smoothed(previous: Double, incoming: Double) -> Double {
        incoming >= previous ? incoming : max(incoming, previous * release)
    }
}

/// A window that follows the signal instead of a fixed dBFS range.
///
/// The recogniser opens the audio session in `.measurement` mode, which
/// switches the system's input processing — automatic gain included — off.
/// The raw microphone then sits far below the −50…−10 dBFS window that suits
/// a processed input: a quiet room around −70 dBFS, ordinary speech at arm's
/// length around −45, so with the fixed window every bar of the strip was a
/// dot. Rather than guess a second window for a gain that differs per
/// device and per distance, the window is learnt from the take itself:
///
/// - the **floor** is the quietest level of the last two seconds (the room);
/// - the **peak** follows the loudest level and falls at 3 dB/s, so the
///   strip re-scales when the speaker moves or the voice drops;
/// - a level within `margin` of the floor is a dot (room noise never draws),
///   and the peak is never closer than `minimumSpan` to the floor, so a
///   fluctuating noise floor cannot fill the strip either.
///
/// Silence at digital zero is a dot and is not learnt as the floor. The first
/// tick of a take has nothing to compare against and is a dot too; the room
/// noise that precedes the first word gives the floor a moment later.
struct AudioLevelRange: Equatable {
    /// Ticks the floor looks back over (≈ 2 s at `publishInterval`).
    static let window = 40
    /// dB above the floor that still count as the room.
    static let margin: Double = 6
    /// The least distance from the floor at which a level fills the strip.
    static let minimumSpan: Double = 12
    /// How fast the peak falls, in dB per tick (3 dB/s).
    static let peakFall: Double = 0.15

    private(set) var recent: [Double] = []
    private(set) var peak: Double = -Double.infinity

    /// The quietest recent level; `-infinity` before the first finite one.
    var floor: Double { recent.min() ?? -Double.infinity }

    /// Learns `decibels` and returns it as 0…1 inside the current window.
    mutating func level(decibels: Double) -> Double {
        guard decibels.isFinite else { return 0 }
        recent.append(decibels)
        if recent.count > Self.window { recent.removeFirst() }
        peak = max(decibels, peak - Self.peakFall)
        let bottom = floor + Self.margin
        let top = max(peak, floor + Self.minimumSpan)
        return min(1, max(0, (decibels - bottom) / (top - bottom)))
    }

    mutating func reset() {
        recent = []
        peak = -Double.infinity
    }
}

/// The last ~1.8 s of levels, newest last, one bar per published level. It
/// starts as a flat line of dots and scrolls as levels arrive; `push` smooths
/// each new bar against the one before it so silence fades instead of
/// dropping. Value type, so a screen can hand it to the composer as state.
struct RecordingWaveform: Equatable {
    static let barCount = CoreHubTokens.Layout.waveformBarCount

    private(set) var levels: [Double]
    /// The learnt window a dBFS reading is placed in (`push(decibels:)`).
    private(set) var range = AudioLevelRange()

    init() { levels = Array(repeating: 0, count: Self.barCount) }

    /// The newest (smoothed) level, for the accessibility value.
    var current: Double { levels.last ?? 0 }

    /// Appends a level and drops the oldest, so the count never changes.
    mutating func push(_ level: Double) {
        let next = AudioLevelMeter.smoothed(previous: current, incoming: min(1, max(0, level)))
        levels.removeFirst()
        levels.append(next)
    }

    /// Appends a raw dBFS reading placed inside the window learnt so far.
    mutating func push(decibels: Double) {
        push(range.level(decibels: decibels))
    }

    mutating func reset() {
        levels = Array(repeating: 0, count: Self.barCount)
        range.reset()
    }

    /// An idle bar is drawn as a dot.
    static func isIdle(_ level: Double) -> Bool { level < AudioLevelMeter.idleThreshold }
}

/// Reads the level of each buffer on the audio thread and hands its dBFS
/// (`-infinity` for digital silence) to `sink` at most once per
/// `AudioLevelMeter.publishInterval`. Called from inside the tap
/// `OnDeviceSpeechRecognizer` already installs, so it adds no audio plumbing
/// of its own. The tap block is invoked serially, which is why `lastEmit`
/// needs no lock. `clock` is injectable so the throttle is unit-tested.
final class AudioLevelTap {
    private let sink: (Double) -> Void
    private let clock: () -> TimeInterval
    private var lastEmit: TimeInterval = -Double.infinity

    init(clock: @escaping () -> TimeInterval = { ProcessInfo.processInfo.systemUptime },
         sink: @escaping (Double) -> Void) {
        self.clock = clock
        self.sink = sink
    }

    func process(_ buffer: AVAudioPCMBuffer) {
        let now = clock()
        guard now - lastEmit >= AudioLevelMeter.publishInterval else { return }
        lastEmit = now
        guard let channel = buffer.floatChannelData?[0] else { return }
        let samples = UnsafeBufferPointer(start: channel, count: Int(buffer.frameLength))
        sink(AudioLevelMeter.decibels(rms: AudioLevelMeter.rms(samples)))
    }
}
