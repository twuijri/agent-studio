package us.i3u.hermesstudio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * On-device dictation through Android's [SpeechRecognizer].
 *
 * Partial hypotheses stream to [Listener.onPartial] while the user speaks, so
 * the composer can show the text as it forms; the final text arrives once
 * through [Listener.onFinal] after [stop]. Every failure is reported through
 * [Listener.onError] with the platform code, never dropped. All calls must
 * happen on the main thread, which is what the platform requires.
 */
class SpeechInput(private val context: Context) {

    interface Listener {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onError(code: Int)
    }

    private var recognizer: SpeechRecognizer? = null

    val isListening: Boolean get() = recognizer != null

    /** Returns false when the device has no recognition service; the caller then uses the server. */
    fun start(languageTag: String, listener: Listener): Boolean {
        if (recognizer != null) return true
        if (!isAvailable(context)) return false
        val instance = SpeechRecognizer.createSpeechRecognizer(context)
        instance.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle?) {
                firstResult(partialResults)?.let(listener::onPartial)
            }

            override fun onResults(results: Bundle?) {
                release()
                listener.onFinal(firstResult(results).orEmpty())
            }

            override fun onError(error: Int) {
                release()
                listener.onError(error)
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        recognizer = instance
        instance.startListening(intent)
        return true
    }

    /** Ends the take; the final text follows through the listener. */
    fun stop() {
        recognizer?.stopListening()
    }

    /** Drops the take without a final result. */
    fun cancel() {
        recognizer?.cancel()
        release()
    }

    private fun release() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    companion object {
        fun isAvailable(context: Context): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

        /** The recognizer gave up before any text: the user simply said nothing it could use. */
        fun isNoSpeech(code: Int): Boolean =
            code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
    }
}

enum class VoiceStatus { Idle, Listening, Transcribing, Error }

enum class VoiceSegmentKind {
    /** The current hypothesis; replaces the previous partial in the field. */
    Partial,
    /** The finished text; stays in the field and closes the segment. */
    Final,
    /** The take was cancelled; whatever partial was inserted is removed. */
    Discard,
}

/** One update to the text the composer inserts for the running dictation. */
data class VoiceSegment(val text: String, val kind: VoiceSegmentKind, val serial: Long)

data class VoiceEdit(val text: String, val caret: Int, val segmentLength: Int)

/**
 * Replaces the interim dictation segment inside [text]. [anchor] is where the
 * segment starts (the caret when dictation began) and [previousLength] how
 * many characters the previous update inserted there. A space is added before
 * the segment when it follows a non-space character, and a final segment is
 * separated from any text that follows it.
 */
fun applyVoiceSegment(
    text: String,
    anchor: Int,
    previousLength: Int,
    segment: String,
    kind: VoiceSegmentKind,
): VoiceEdit {
    val start = anchor.coerceIn(0, text.length)
    val end = (start + previousLength).coerceIn(start, text.length)
    val body = if (kind == VoiceSegmentKind.Discard) "" else segment.trim()
    if (body.isEmpty()) {
        val cleared = text.substring(0, start) + text.substring(end)
        return VoiceEdit(cleared, start, 0)
    }
    val lead = if (start > 0 && !text[start - 1].isWhitespace()) " " else ""
    val after = text.substring(end)
    val trail = if (kind == VoiceSegmentKind.Final && after.isNotEmpty() && !after[0].isWhitespace()) " " else ""
    val inserted = lead + body + trail
    val replaced = text.substring(0, start) + inserted + after
    return VoiceEdit(replaced, start + lead.length + body.length, inserted.length)
}
