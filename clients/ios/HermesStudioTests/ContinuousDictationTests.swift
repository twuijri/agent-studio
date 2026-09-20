import AVFoundation
import XCTest
@testable import HermesStudio

/// A take lasts until the user ends it. Apple ends a recognition request on
/// its own (about a minute of audio, or a long pause); `DictationTake`
/// restarts it transparently and commits the text. The second half pins the
/// level window the recording strip learns from the raw microphone, which
/// the fixed −50…−10 dBFS window left as a row of dots on the phone.
final class ContinuousDictationTests: XCTestCase {

    // MARK: - Requests ending while the take is open

    func testARequestThatEndsWhileListeningIsRestartedAndItsTextCommitted() {
        var take = DictationTake()
        take.partialResult("hello wor")
        let outcome = take.requestEnded(.init(finalText: "hello world"))
        XCTAssertEqual(outcome, .restart)
        XCTAssertEqual(take.phase, .listening)
        XCTAssertEqual(take.committed, "hello world")
        XCTAssertEqual(take.partial, "", "the next request starts empty")
        XCTAssertEqual(take.text, "hello world")
        XCTAssertEqual(take.requests, 2)
    }

    func testAnErrorAfterAPauseIsRestartedWithThePartialKept() {
        // "No speech detected" arrives as an error with no final result; what
        // was heard before the pause is not lost.
        var take = DictationTake()
        take.partialResult("hello")
        let outcome = take.requestEnded(.init(finalText: nil, failed: true, requestDuration: 45))
        XCTAssertEqual(outcome, .restart)
        XCTAssertEqual(take.text, "hello")
        XCTAssertEqual(take.shortFailures, 0, "a long request that dies is a pause, not a failed restart")
    }

    func testAnEmptyErrorAfterALongSilenceIsJustARestart() {
        var take = DictationTake()
        XCTAssertEqual(take.requestEnded(.init(finalText: nil, failed: true, requestDuration: 40)), .restart)
        XCTAssertEqual(take.text, "")
        XCTAssertEqual(take.phase, .listening)
    }

    // MARK: - Requests ending after the user stopped

    func testARequestThatEndsAfterStopFinishesTheTake() {
        var take = DictationTake()
        take.partialResult("hello")
        take.stop()
        XCTAssertEqual(take.phase, .stopping)
        take.partialResult("hello th")
        XCTAssertEqual(take.text, "hello th", "partials are still taken after ■ so the finish timeout has the latest")
        let outcome = take.requestEnded(.init(finalText: "hello there"))
        XCTAssertEqual(outcome, .finish)
        XCTAssertEqual(take.phase, .ended)
        XCTAssertEqual(take.text, "hello there")
    }

    func testAnErrorAfterStopFinishesWithTextAndFailsWithout() {
        var withText = DictationTake()
        withText.partialResult("hello")
        withText.stop()
        XCTAssertEqual(withText.requestEnded(.init(finalText: nil, failed: true)), .finish)
        XCTAssertEqual(withText.text, "hello")

        var silent = DictationTake()
        silent.stop()
        XCTAssertEqual(silent.requestEnded(.init(finalText: nil, failed: true)), .fail)
        XCTAssertEqual(silent.text, "")
    }

    func testNothingChangesOnceTheTakeHasEnded() {
        var take = DictationTake()
        take.stop()
        XCTAssertEqual(take.requestEnded(.init(finalText: "one")), .finish)
        take.partialResult("late partial")
        XCTAssertEqual(take.requestEnded(.init(finalText: "late final")), .finish)
        XCTAssertEqual(take.text, "one")
        XCTAssertEqual(take.phase, .ended)
    }

    // MARK: - Real failures stop; transient ones restart

    func testPermissionAvailabilityAndEngineLossStopTheTake() {
        let reasons: [DictationTake.RequestEnd] = [
            .init(finalText: nil, failed: true, authorized: false),
            .init(finalText: nil, failed: true, recognizerAvailable: false),
            .init(finalText: nil, failed: true, engineRunning: false),
            // Even a clean final result cannot be restarted on a dead engine.
            .init(finalText: "words", engineRunning: false),
        ]
        for end in reasons {
            var take = DictationTake()
            take.partialResult("kept")
            XCTAssertEqual(take.requestEnded(end), .fail, "\(end)")
            XCTAssertEqual(take.phase, .ended)
            XCTAssertFalse(take.text.isEmpty, "what was heard is kept even when the take fails")
        }
    }

    func testThreeQuickEmptyDeathsInARowStopTheTake() {
        var take = DictationTake()
        let quick = DictationTake.RequestEnd(finalText: nil, failed: true, requestDuration: 0.4)
        XCTAssertEqual(take.requestEnded(quick), .restart)
        XCTAssertEqual(take.requestEnded(quick), .restart)
        XCTAssertEqual(take.shortFailures, 2)
        XCTAssertEqual(take.requestEnded(quick), .fail)
        XCTAssertEqual(take.phase, .ended)
        XCTAssertEqual(DictationTake.maxShortFailures, 3)
        XCTAssertEqual(DictationTake.shortRequest, 2, accuracy: 1e-9)
    }

    func testARequestThatHeardSomethingResetsTheFailureCount() {
        var take = DictationTake()
        let quick = DictationTake.RequestEnd(finalText: nil, failed: true, requestDuration: 0.4)
        XCTAssertEqual(take.requestEnded(quick), .restart)
        XCTAssertEqual(take.requestEnded(quick), .restart)
        take.partialResult("back")
        XCTAssertEqual(take.requestEnded(.init(finalText: "back again", requestDuration: 1)), .restart)
        XCTAssertEqual(take.shortFailures, 0)
        XCTAssertEqual(take.requestEnded(quick), .restart, "the count starts over")
        XCTAssertEqual(take.text, "back again")
    }

    // MARK: - Text across requests

    func testTextIsCommittedAcrossThreeRequestsWithMergedsSeparators() {
        var take = DictationTake()
        take.partialResult("hello")
        XCTAssertEqual(take.requestEnded(.init(finalText: "hello world")), .restart)
        take.partialResult("again")
        XCTAssertEqual(take.text, "hello world again", "the second request's partial lands after the first's text")
        XCTAssertEqual(take.requestEnded(.init(finalText: nil, failed: true, requestDuration: 61)), .restart)
        take.partialResult("third")
        take.stop()
        XCTAssertEqual(take.requestEnded(.init(finalText: "third one")), .finish)
        XCTAssertEqual(take.text, "hello world again third one")
        XCTAssertEqual(take.requests, 3)
        // The same separator rule the field uses for the base, so nothing is
        // glued together and nothing gets two spaces.
        XCTAssertEqual(DictationText.merged(base: "note", transcript: take.text), "note hello world again third one")
        XCTAssertEqual(DictationText.merged(base: "note\n", transcript: take.text), "note\nhello world again third one")
    }

    func testAnEmptyRequestCommitsNothingAndAddsNoSeparator() {
        var take = DictationTake()
        take.partialResult("hello")
        XCTAssertEqual(take.requestEnded(.init(finalText: "hello")), .restart)
        XCTAssertEqual(take.requestEnded(.init(finalText: "")), .restart)
        XCTAssertEqual(take.requestEnded(.init(finalText: nil, failed: true, requestDuration: 30)), .restart)
        XCTAssertEqual(take.text, "hello")
    }

    func testSendDuringARestartGapSendsTheCommittedTextPlusTheInFlightPartial() {
        // ↑ reads the field, which `DictationRunner` keeps as base + take.text
        // after every update — including the update a restart itself sends.
        let base = "note"
        var take = DictationTake()
        take.partialResult("hello world")
        XCTAssertEqual(take.requestEnded(.init(finalText: "hello world")), .restart)
        let fieldInTheGap = DictationText.merged(base: base, transcript: take.text)
        XCTAssertEqual(fieldInTheGap, "note hello world")
        XCTAssertEqual(RecordingStrip.resolve(.send, base: base, current: fieldInTheGap),
                       RecordingStrip.Resolution(text: "note hello world", sends: true, awaitsFinal: false))

        take.partialResult("and more")
        let fieldMidRequest = DictationText.merged(base: base, transcript: take.text)
        XCTAssertEqual(RecordingStrip.resolve(.send, base: base, current: fieldMidRequest).text, "note hello world and more")
    }

    // MARK: - The ceiling

    func testTheCeilingIsTenMinutesAndEndsTheTakeLikeStop() {
        XCTAssertEqual(DictationTake.ceiling, 600, accuracy: 1e-9)
        XCTAssertFalse(DictationTake.reachedCeiling(elapsed: 599.9))
        XCTAssertTrue(DictationTake.reachedCeiling(elapsed: 600))
        var take = DictationTake()
        take.partialResult("long")
        take.stop()
        XCTAssertEqual(take.requestEnded(.init(finalText: "long take")), .finish)
        XCTAssertEqual(take.text, "long take")
    }

    // MARK: - The level window the strip learns

    func testARawMicrophoneFarBelowTheFixedWindowStillDraws() {
        // Measurement mode: the room near −70 dBFS, speech near −45. The fixed
        // window put both under its −50 floor; the learnt one does not.
        XCTAssertEqual(AudioLevelMeter.normalized(decibels: -55), 0, "the fixed window: a dot")
        XCTAssertLessThan(AudioLevelMeter.normalized(decibels: -45), 0.15, "the fixed window: a sliver at best")
        var range = AudioLevelRange()
        for _ in 0..<10 { XCTAssertEqual(range.level(decibels: -70), 0) }
        XCTAssertGreaterThan(range.level(decibels: -45), 0.9)
        XCTAssertEqual(range.floor, -70)
    }

    func testRoomNoiseStaysADot() {
        var range = AudioLevelRange()
        for db in [-70.0, -68, -71, -67, -69, -66, -70, -68] {
            XCTAssertTrue(RecordingWaveform.isIdle(range.level(decibels: db)), "\(db) dBFS")
        }
        XCTAssertEqual(AudioLevelRange.margin, 6)
        XCTAssertEqual(AudioLevelRange.minimumSpan, 12)
    }

    func testAProcessedInputStillDraws() {
        var range = AudioLevelRange()
        for _ in 0..<5 { XCTAssertEqual(range.level(decibels: -45), 0) }
        XCTAssertGreaterThan(range.level(decibels: -20), 0.9)
        let quieter = range.level(decibels: -30)
        XCTAssertGreaterThan(quieter, 0.4, "a quieter syllable is drawn proportionally")
        XCTAssertLessThan(quieter, 0.6)
    }

    func testDigitalSilenceIsADotAndNotTheFloor() {
        var range = AudioLevelRange()
        XCTAssertEqual(range.level(decibels: -.infinity), 0)
        XCTAssertEqual(range.level(decibels: .nan), 0)
        XCTAssertEqual(range.floor, -.infinity, "nothing learnt yet")
        XCTAssertEqual(range.level(decibels: -60), 0, "the first real level has nothing to compare against")
        XCTAssertGreaterThan(range.level(decibels: -40), 0.9)
    }

    func testThePeakFallsSoAQuieterVoiceFillsTheStripAgain() {
        var range = AudioLevelRange()
        _ = range.level(decibels: -70)
        XCTAssertGreaterThan(range.level(decibels: -30), 0.9)
        // 3 dB/s: after ten seconds of room the peak has come down 30 dB.
        for _ in 0..<200 { _ = range.level(decibels: -70) }
        XCTAssertEqual(range.peak, -60, accuracy: 1e-6)
        XCTAssertGreaterThan(range.level(decibels: -50), 0.9)
        XCTAssertEqual(AudioLevelRange.peakFall, 0.15, accuracy: 1e-9)
    }

    func testTheFloorForgetsAfterTwoSeconds() {
        var range = AudioLevelRange()
        _ = range.level(decibels: -70)
        for _ in 0..<AudioLevelRange.window { _ = range.level(decibels: -50) }
        XCTAssertEqual(range.floor, -50, "the −70 tick fell out of the two-second window")
        XCTAssertEqual(range.recent.count, AudioLevelRange.window)
        XCTAssertEqual(range.level(decibels: -50), 0, "a steady level is the room now")
        XCTAssertEqual(AudioLevelRange.window, 40)
    }

    func testTheWaveformTakesDecibelsAndResetForgetsTheWindow() {
        var waveform = RecordingWaveform()
        waveform.push(decibels: -70)
        XCTAssertTrue(RecordingWaveform.isIdle(waveform.current))
        waveform.push(decibels: -45)
        XCTAssertGreaterThan(waveform.current, 0.9)
        waveform.reset()
        XCTAssertEqual(waveform, RecordingWaveform())
        XCTAssertEqual(waveform.range.floor, -.infinity)
    }

    // MARK: - The tap itself

    /// A mono Float32 buffer of an alternating ±`amplitude` square wave, whose
    /// RMS is exactly `amplitude` — the same `AVAudioPCMBuffer` shape the
    /// engine's input tap hands `AudioLevelTap`.
    private func squareWave(amplitude: Float, frames: AVAudioFrameCount = 4800) throws -> AVAudioPCMBuffer {
        let format = try XCTUnwrap(AVAudioFormat(standardFormatWithSampleRate: 48_000, channels: 1))
        let buffer = try XCTUnwrap(AVAudioPCMBuffer(pcmFormat: format, frameCapacity: frames))
        buffer.frameLength = frames
        let channel = try XCTUnwrap(buffer.floatChannelData)[0]
        for index in 0..<Int(frames) { channel[index] = index % 2 == 0 ? amplitude : -amplitude }
        return buffer
    }

    func testABufferAtMinus20dBFSThroughTheTapDrawsABarWellAboveIdle() throws {
        var now: TimeInterval = 10
        var readings: [Double] = []
        let tap = AudioLevelTap(clock: { now }) { readings.append($0) }
        // A tick of the room, then the word.
        let room = try squareWave(amplitude: 0.001)
        let word = try squareWave(amplitude: 0.1)
        tap.process(room)
        now += 0.1
        tap.process(word)
        XCTAssertEqual(readings.count, 2)
        XCTAssertEqual(readings[0], -60, accuracy: 0.01)
        XCTAssertEqual(readings[1], -20, accuracy: 0.01)

        var waveform = RecordingWaveform()
        for decibels in readings { waveform.push(decibels: decibels) }
        XCTAssertGreaterThan(waveform.current, 0.9)
        XCTAssertFalse(RecordingWaveform.isIdle(waveform.current))
    }

    func testTheTapPublishesAtMostTwentyTimesASecond() throws {
        var now: TimeInterval = 0
        var readings: [Double] = []
        let tap = AudioLevelTap(clock: { now }) { readings.append($0) }
        let word = try squareWave(amplitude: 0.1)
        tap.process(word)
        now += 0.02
        tap.process(word)
        XCTAssertEqual(readings.count, 1, "a buffer 20 ms after the last level is dropped")
        now += 0.05
        tap.process(word)
        XCTAssertEqual(readings.count, 2)
    }

    func testAnEmptyOrSilentBufferReadsAsSilence() throws {
        var readings: [Double] = []
        let tap = AudioLevelTap(clock: { 0 }) { readings.append($0) }
        let silence = try squareWave(amplitude: 0, frames: 4800)
        tap.process(silence)
        XCTAssertEqual(readings, [-.infinity])
    }
}
