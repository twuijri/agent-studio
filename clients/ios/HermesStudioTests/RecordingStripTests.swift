import XCTest
@testable import HermesStudio

/// The recording strip that replaces the pill row while the microphone is
/// open (✕ · waveform · ■ · ↑). The views need a host; these pin the
/// arithmetic behind the waveform and the rules behind the three controls.
final class RecordingStripTests: XCTestCase {

    // MARK: - Level normalisation

    func testSilenceIsZeroAndTheCeilingIsOne() {
        XCTAssertEqual(AudioLevelMeter.normalized(decibels: AudioLevelMeter.floorDecibels), 0)
        XCTAssertEqual(AudioLevelMeter.normalized(decibels: AudioLevelMeter.ceilingDecibels), 1)
        // Half way between -50 and -10 dBFS.
        XCTAssertEqual(AudioLevelMeter.normalized(decibels: -30), 0.5, accuracy: 1e-9)
    }

    func testLevelsAreClampedToTheStripsRange() {
        XCTAssertEqual(AudioLevelMeter.normalized(decibels: -120), 0)
        XCTAssertEqual(AudioLevelMeter.normalized(decibels: 0), 1)
        XCTAssertEqual(AudioLevelMeter.normalized(decibels: -.infinity), 0)
        XCTAssertEqual(AudioLevelMeter.normalized(decibels: .nan), 0)
        XCTAssertEqual(AudioLevelMeter.level(rms: 0), 0)
        XCTAssertEqual(AudioLevelMeter.level(rms: 1), 1)
    }

    func testRmsOfABufferAndItsLevel() {
        let empty: [Float] = []
        let silence: [Float] = [0, 0, 0]
        let square: [Float] = [1, -1, 1, -1]
        let half: [Float] = [0.5, -0.5]
        let quiet: [Float] = [0.1, -0.1, 0.1, -0.1]
        XCTAssertEqual(AudioLevelMeter.rms(empty), 0)
        XCTAssertEqual(AudioLevelMeter.rms(silence), 0)
        XCTAssertEqual(AudioLevelMeter.rms(square), 1, accuracy: 1e-9)
        XCTAssertEqual(AudioLevelMeter.rms(half), 0.5, accuracy: 1e-9)
        // 0.1 RMS is -20 dBFS, three quarters of the way from -50 to -10.
        XCTAssertEqual(AudioLevelMeter.decibels(rms: AudioLevelMeter.rms(quiet)), -20, accuracy: 1e-6)
        XCTAssertEqual(AudioLevelMeter.level(rms: AudioLevelMeter.rms(quiet)), 0.75, accuracy: 1e-6)
    }

    // MARK: - Decay

    func testABarRisesInstantlyAndFallsSmoothly() {
        XCTAssertEqual(AudioLevelMeter.smoothed(previous: 0.2, incoming: 0.8), 0.8)
        XCTAssertEqual(AudioLevelMeter.smoothed(previous: 0.8, incoming: 0), 0.8 * AudioLevelMeter.release, accuracy: 1e-9)
        // Never below what the microphone actually reports.
        XCTAssertEqual(AudioLevelMeter.smoothed(previous: 0.8, incoming: 0.7), 0.7)
        XCTAssertEqual(AudioLevelMeter.smoothed(previous: 0, incoming: 0), 0)
    }

    func testSilenceFadesToADotWithinASecond() {
        // Ten ticks of 50 ms: about 10 % left; twenty: an idle dot.
        var level = 1.0
        for _ in 0..<10 { level = AudioLevelMeter.smoothed(previous: level, incoming: 0) }
        XCTAssertLessThan(level, 0.11)
        XCTAssertGreaterThan(level, 0.1)
        XCTAssertFalse(RecordingWaveform.isIdle(level))
        for _ in 0..<10 { level = AudioLevelMeter.smoothed(previous: level, incoming: 0) }
        XCTAssertTrue(RecordingWaveform.isIdle(level))
        XCTAssertEqual(AudioLevelMeter.publishInterval, 0.05, accuracy: 1e-9)
    }

    func testTheWaveformStartsFlatAndScrolls() {
        var waveform = RecordingWaveform()
        XCTAssertEqual(RecordingWaveform.barCount, 36)
        XCTAssertEqual(waveform.levels.count, 36)
        XCTAssertTrue(waveform.levels.allSatisfy(RecordingWaveform.isIdle))
        XCTAssertEqual(waveform.current, 0)

        waveform.push(0.9)
        XCTAssertEqual(waveform.levels.count, 36, "a push never changes the bar count")
        XCTAssertEqual(waveform.current, 0.9)
        XCTAssertEqual(waveform.levels.last, 0.9)

        waveform.push(0)
        XCTAssertEqual(waveform.levels[34], 0.9, "an older bar keeps its own height")
        XCTAssertEqual(waveform.current, 0.9 * AudioLevelMeter.release, accuracy: 1e-9, "the newest bar decays from the one before it")

        waveform.push(2)
        XCTAssertEqual(waveform.current, 1, "levels are clamped to 1")
        waveform.push(-1)
        XCTAssertEqual(waveform.current, AudioLevelMeter.release, accuracy: 1e-9, "and to 0 before smoothing")

        waveform.reset()
        XCTAssertEqual(waveform, RecordingWaveform())
    }

    // MARK: - The strip's state machine

    func testTheStripReplacesThePillRowOnlyWhileListening() {
        XCTAssertTrue(RecordingStrip.isVisible(.listening))
        XCTAssertFalse(RecordingStrip.isVisible(.idle))
        // While the final transcript is awaited the pill row is back with the
        // mic disabled; after an error the mic shows the error.
        XCTAssertFalse(RecordingStrip.isVisible(.transcribing))
        XCTAssertFalse(RecordingStrip.isVisible(.error))
    }

    func testStopKeepsTheTextAndWaitsForTheFinalResult() {
        let out = RecordingStrip.resolve(.stop, base: "note", current: "note hello")
        XCTAssertEqual(out, RecordingStrip.Resolution(text: "note hello", sends: false, awaitsFinal: true))
    }

    func testSendKeepsTheTextAndSendsWithoutWaiting() {
        let out = RecordingStrip.resolve(.send, base: "note", current: "note hello")
        XCTAssertEqual(out, RecordingStrip.Resolution(text: "note hello", sends: true, awaitsFinal: false))
    }

    func testCancelRestoresTheDraftAndSendsNothing() {
        let out = RecordingStrip.resolve(.cancel, base: "note", current: "note hello")
        XCTAssertEqual(out, RecordingStrip.Resolution(text: "note", sends: false, awaitsFinal: false))
    }

    // MARK: - Cancel restores the pre-take draft exactly

    func testCancelUndoesEverySeparatorMergedAdds() {
        // The same four separator cases `DictationText.merged` is tested on,
        // plus an Arabic draft: whatever the merge appended, cancel removes,
        // and the draft's own trailing space or newline survives untouched.
        for base in ["", "note", "note ", "note\n", "راجع"] {
            let current = DictationText.merged(base: base, transcript: "hello world")
            XCTAssertNotEqual(current, base, "the take must have changed the field")
            XCTAssertEqual(RecordingStrip.resolve(.cancel, base: base, current: current).text, base,
                           "cancel must give back exactly \(base.debugDescription)")
        }
    }

    func testCancelAfterAnEmptyPartialStillRestoresTheDraft() {
        let base = "note "
        let current = DictationText.merged(base: base, transcript: "")
        XCTAssertEqual(current, base)
        XCTAssertEqual(RecordingStrip.resolve(.cancel, base: base, current: current).text, base)
    }

    func testCancelDoesNotTouchWhatWasTypedBeforeTheTake() {
        // What the owner typed before tapping the mic is the base; only what
        // was dictated after it goes.
        let base = "first line\nsecond"
        let current = DictationText.merged(base: base, transcript: "dictated")
        XCTAssertEqual(current, "first line\nsecond dictated")
        XCTAssertEqual(RecordingStrip.resolve(.cancel, base: base, current: current).text, base)
    }

    // MARK: - What VoiceOver hears

    func testTheWaveformNamesTheLanguageAndTheLevel() {
        // The wording is localized; the language and the number are under test.
        XCTAssertTrue(RecordingWaveformView.label(language: "العربية").contains("العربية"))
        XCTAssertFalse(RecordingWaveformView.label(language: "").isEmpty)
        XCTAssertTrue(RecordingWaveformView.value(level: 0.5).contains("50"))
        XCTAssertTrue(RecordingWaveformView.value(level: 1).contains("100"))
        XCTAssertTrue(RecordingWaveformView.value(level: 0.004).contains("0"))
    }
}
