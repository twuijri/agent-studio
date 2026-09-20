package us.i3u.hermesstudio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The recording strip's rules are tested in [RecordingStripTest] and its
 * levels in [DictationLevelsTest]. What is guarded here is the wiring none of
 * that can see: that the waveform is fed by the recognizer's own reading and
 * the recorder's own PCM rather than a second microphone, that the pill row
 * really swaps for the strip with the drawer's tween, that colours come from
 * the tokens, and that both locales carry the strip's strings.
 */
class RecordingStripWiringTest {

    private fun source(name: String) = File("src/main/java/us/i3u/hermesstudio/$name").readText()

    private val composer = source("ui/chat/Composer.kt")
    private val strip = source("ui/chat/RecordingStripRow.kt")
    private val speechInput = source("SpeechInput.kt")
    private val recorder = source("Recorder.kt")
    private val viewModel = source("AppViewModel.kt")

    @Test
    fun `the waveform is fed by the sources already open, never a second microphone`() {
        assertTrue("SpeechInput must forward onRmsChanged", speechInput.contains("override fun onRmsChanged(rmsdB: Float) = listener.onRms(rmsdB)"))
        assertTrue("the view model must feed the meter from it", viewModel.contains("override fun onRms(rmsDb: Float) = levelMeter.feedDb(rmsDb)"))
        assertTrue("a server take is measured from the recorder's own PCM", recorder.contains("DictationLevels.fromPcm16(chunk, read)"))
        listOf("AudioRecord", "MediaRecorder", "SpeechRecognizer").forEach { source ->
            assertFalse("the strip must not open $source itself", strip.contains(source))
            assertFalse("the composer must not open $source itself", composer.contains(source))
        }
    }

    @Test
    fun `the pill row swaps for the strip with the drawer's tween and respects reduced motion`() {
        assertTrue(composer.contains("AnimatedContent("))
        assertTrue(composer.contains("targetState = state.voice == VoiceStatus.Listening"))
        assertTrue(composer.contains("tween<Float>(CoreHubTokens.Metrics.transitionFastMs)"))
        assertTrue(composer.contains("if (reducedMotion) snap<Float>()"))
        assertTrue(strip.contains("Settings.Global.ANIMATOR_DURATION_SCALE"))
    }

    @Test
    fun `the strip's three controls and the levels flow are wired to the reducer`() {
        listOf(
            "onCancel = { dispatch(RecordingStrip.Event.Cancel) }",
            "onStop = { dispatch(RecordingStrip.Event.Stop) }",
            "onSend = { dispatch(RecordingStrip.Event.Send) }",
            "dispatch(RecordingStrip.Event.Start(field.text))",
            "RecordingStrip.Effect.SendDraft -> onSend()",
        ).forEach { assertTrue("composer must contain $it", composer.contains(it)) }
        assertTrue(strip.contains("viewModel.dictationLevels.collectAsStateWithLifecycle()"))
        assertTrue("the view model publishes levels apart from the UI state", viewModel.contains("val dictationLevels: StateFlow<List<Float>>"))
    }

    @Test
    fun `the strip draws with tokens only`() {
        assertFalse("no literal colours in the strip", Regex("Color\\(0x").containsMatchIn(strip))
        listOf(
            "palette.bgCard",
            "palette.accent",
            "palette.textOnAccent",
            "CoreHubTokens.Alpha.WAVEFORM_IDLE",
            "CoreHubTokens.Metrics.composerButton",
            "CoreHubTokens.Metrics.waveformBar",
        ).forEach { assertTrue("strip must use $it", strip.contains(it)) }
    }

    @Test
    fun `the waveform names the language and the level, and every control has a label`() {
        assertTrue(strip.contains("stringResource(R.string.composer_recording_in, language)"))
        assertTrue(strip.contains("stringResource(R.string.composer_recording_level, percent)"))
        assertTrue("↑ is the composer's own Send", strip.contains("label = stringResource(R.string.composer_send)"))
        listOf(
            "composer_dictation_cancel",
            "composer_dictation_cancel_hint",
            "composer_dictation_stop",
            "composer_dictation_stop_hint",
            "composer_recording_label",
            "composer_recording_in",
            "composer_recording_level",
        ).forEach { key ->
            assertTrue("strip uses $key", strip.contains("R.string.$key"))
            listOf("values", "values-ar").forEach { locale ->
                val strings = File("src/main/res/$locale/strings.xml").readText()
                assertTrue("$locale must define $key", strings.contains("name=\"$key\""))
            }
        }
    }

    /**
     * The rules of the session chain are in [ContinuousDictationTest]; this
     * is the wiring that keeps the strip up across it: every engine callback
     * is an event for the machine, a restart reuses the take's intent, a
     * commit does not end the strip, and the composer keeps its anchor while
     * ■'s final text is still on the way.
     */
    @Test
    fun `a session the engine ends is restarted without the strip noticing`() {
        listOf(
            "override fun onPartial(text: String) = dictate(ContinuousDictation.Event.Partial(text))",
            "dictate(ContinuousDictation.Event.SessionEnded(text, dictationClock()))",
            "dictate(ContinuousDictation.Event.SessionFailed(code, dictationClock()))",
            "dictate(ContinuousDictation.Event.Opened(dictationClock()))",
            "dictate(ContinuousDictation.Event.CeilingReached)",
            "dictate(ContinuousDictation.Event.Stop)",
            "if (speech.restart(fresh = effect.fresh))",
            "R.string.notice_dictation_ceiling",
        ).forEach { assertTrue("view model must contain $it", viewModel.contains(it)) }
        assertTrue("a restart reuses the intent the take opened with", speechInput.contains("val request = intent ?: return false"))
        assertFalse("the engine's final no longer releases the recognizer", speechInput.contains("release()\n                listener.onFinal"))
        listOf(
            "EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS",
            "EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS",
            "EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS",
        ).forEach { assertTrue("the silence hints are set anyway: $it", speechInput.contains(it)) }
        assertTrue(
            "only a final or a discard ends the strip; a commit keeps it up",
            composer.contains("if (segment.kind == VoiceSegmentKind.Final || segment.kind == VoiceSegmentKind.Discard) {"),
        )
        assertFalse(
            "the anchor must survive the transcribing status, or ■'s final text lands after the partial instead of replacing it",
            composer.contains("if (state.voice != VoiceStatus.Listening) {\n            voiceAnchor = null"),
        )
        listOf("values", "values-ar").forEach { locale ->
            val strings = File("src/main/res/$locale/strings.xml").readText()
            assertTrue("$locale must define notice_dictation_ceiling", strings.contains("name=\"notice_dictation_ceiling\""))
        }
    }

    @Test
    fun `the waveform is a timeline pinned LTR inside a strip that mirrors`() {
        assertTrue(strip.contains("CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)"))
        assertFalse("no per-bar mirroring on top of the pin", strip.contains("count - 1 - index"))
    }
}
