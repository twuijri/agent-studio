package us.i3u.hermesstudio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import java.util.Locale

/**
 * On-device dictation through Android's [SpeechRecognizer].
 *
 * One [start] opens a take and binds one recognizer; the take then runs as
 * a chain of *sessions*, because the platform ends a session by itself as
 * soon as its endpointer hears a pause (see [ContinuousDictation]). Partial
 * hypotheses stream to [Listener.onPartial] while the user speaks, so the
 * composer can show the text as it forms; each session's final text arrives
 * through [Listener.onFinal], after which the caller either [restart]s the
 * next session with the same intent or [end]s the take. Every failure is
 * reported through [Listener.onError] with the platform code, never dropped.
 * All calls must happen on the main thread, which is what the platform
 * requires.
 */
class SpeechInput(private val context: Context) {

    interface Listener {
        fun onPartial(text: String)

        /** One session's final text; the take is still open until [end] or [cancel]. */
        fun onFinal(text: String)

        /** One session failed; the take is still open, and the caller decides whether to [restart]. */
        fun onError(code: Int)

        /**
         * The engine worked the language out for itself. Only reached when the
         * take was started with a [detectAmong] list on Android 14 or later,
         * and only for a guess the engine itself calls confident.
         */
        fun onLanguageDetected(tag: String) = Unit

        /**
         * The engine's own loudness reading, in dB as `onRmsChanged` reports
         * it (roughly −2…10 on Google's engine). This is the only measurement
         * the recording strip's waveform has; nothing else opens the mic.
         */
        fun onRms(rmsDb: Float) = Unit
    }

    private var recognizer: SpeechRecognizer? = null

    /** The intent every session of the open take runs with, so a restart changes nothing about the language. */
    private var intent: Intent? = null
    private var platformListener: RecognitionListener? = null

    /** Whether a take is open — between sessions included. */
    val isListening: Boolean get() = recognizer != null

    /**
     * Returns false when the device has no recognition service; the caller
     * then uses the server.
     *
     * [languageTag] is the language the take runs in — with [detectAmong]
     * non-empty it is only the language the take *opens* in, and the engine
     * may settle on any entry of that list instead. Detection needs Android 14
     * (`EXTRA_ENABLE_LANGUAGE_DETECTION`, API 34), so on anything older the
     * list is ignored and the take simply runs in [languageTag]; the caller
     * decides that in [SpeechLanguages.plan] rather than discovering it here.
     */
    fun start(languageTag: String, detectAmong: List<String> = emptyList(), listener: Listener): Boolean {
        if (recognizer != null) return true
        if (!isAvailable(context)) return false
        val instance = SpeechRecognizer.createSpeechRecognizer(context)
        val bridge = object : RecognitionListener {
            override fun onLanguageDetection(results: Bundle) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
                val detected = SpeechLanguages.readDetected(
                    results.getString(SpeechRecognizer.DETECTED_LANGUAGE),
                    results.getInt(
                        SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL,
                        SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL_UNKNOWN,
                    ),
                )
                if (detected != null) listener.onLanguageDetected(detected)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                firstResult(partialResults)?.let(listener::onPartial)
            }

            // Neither callback releases the recognizer: the session is over,
            // the take is not, and the caller may restart at once.
            override fun onResults(results: Bundle?) {
                listener.onFinal(firstResult(results).orEmpty())
            }

            override fun onError(error: Int) {
                listener.onError(error)
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = listener.onRms(rmsdB)
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }
        instance.setRecognitionListener(bridge)
        val request = recognitionIntent(context, languageTag, detectAmong)
        recognizer = instance
        intent = request
        platformListener = bridge
        instance.startListening(request)
        return true
    }

    /**
     * Starts the next session of the open take with the very same intent, so
     * the language, the detection list and the switch settings carry over.
     * [fresh] rebinds a new recognizer first — the answer to an engine that
     * reported itself busy after the last session. Returns false when no take
     * is open.
     */
    fun restart(fresh: Boolean = false): Boolean {
        val request = intent ?: return false
        val bridge = platformListener ?: return false
        var instance = recognizer ?: return false
        if (fresh) {
            runCatching { instance.destroy() }
            instance = SpeechRecognizer.createSpeechRecognizer(context)
            instance.setRecognitionListener(bridge)
            recognizer = instance
        }
        instance.startListening(request)
        return true
    }

    /** Asks the running session for its final text, which follows through the listener. */
    fun stop() {
        recognizer?.stopListening()
    }

    /** Drops the take without a final result. */
    fun cancel() {
        recognizer?.cancel()
        release()
    }

    /** The take is over and its last text has been delivered; lets the recognizer go. */
    fun end() = release()

    private fun release() {
        runCatching { recognizer?.destroy() }
        recognizer = null
        intent = null
        platformListener = null
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    companion object {
        fun isAvailable(context: Context): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

        /**
         * The silence hints, generous on purpose. The platform documents all
         * three as values that "depending on the recognizer implementation
         * ... may have no effect", and Google's engine does ignore them —
         * which is why the take is restarted per session instead of relying
         * on them — but an engine that honours them gets one long session
         * rather than many short ones, and ■ ends a session explicitly in
         * either case.
         */
        const val COMPLETE_SILENCE_MILLIS = 10_000L
        const val POSSIBLY_COMPLETE_SILENCE_MILLIS = 8_000L
        const val MINIMUM_SESSION_MILLIS = 30_000L

        /**
         * The intent every session of a take runs with. Detection and
         * switching are only attached on Android 14 and later, where the
         * extras exist at all; `EXTRA_LANGUAGE` is always set, because the
         * platform requires the take to open in some language even when it
         * may switch away from it.
         */
        fun recognitionIntent(
            context: Context,
            languageTag: String,
            detectAmong: List<String> = emptyList(),
        ): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, COMPLETE_SILENCE_MILLIS)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, POSSIBLY_COMPLETE_SILENCE_MILLIS)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, MINIMUM_SESSION_MILLIS)
            if (detectAmong.size < 2 || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return@apply
            val allowed = ArrayList(detectAmong)
            putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
            putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, allowed)
            // Balanced is the platform's own middle setting: it lets the engine
            // change language mid-take without the latency of high precision.
            putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
            putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, allowed)
            // API 35 only; a cap keeps one noisy take from thrashing between models.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_MAX_SWITCHES, MAX_LANGUAGE_SWITCHES)
            }
        }

        private const val MAX_LANGUAGE_SWITCHES = 2

        /**
         * What this device's recognition service says about languages.
         *
         * Two sources, in order of how much they prove. `checkRecognitionSupport`
         * (Android 13+) is the engine answering for itself, and separates the
         * models that are installed now from the ones it could fetch — only the
         * installed ones are worth handing to detection. Below that the only
         * option is the [RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS] ordered
         * broadcast, which reports one flat list.
         *
         * On Android 14 and later the first query carries the language
         * detection extras, so [RecognizerLanguages.detectionAccepted] records
         * whether this engine tolerates being asked to detect at all. An
         * engine that refuses is asked again without them, because its
         * language list is still worth having.
         *
         * [onResult] always runs, on the main thread, even when nothing
         * answered: [RecognizerLanguages.answered] is then false and the caller
         * offers its curated guess instead of inventing support.
         */
        fun queryLanguages(context: Context, onResult: (RecognizerLanguages) -> Unit) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !isAvailable(context)) {
                queryLanguageDetails(context, onResult)
                return
            }
            // Android 14 and later: ask first with the detection extras
            // attached, because "this engine can recognise speech" and "this
            // engine will accept an intent that asks it to detect the
            // language" are different questions and only the second one
            // justifies showing "detecting" on the recording strip.
            if (Build.VERSION.SDK_INT >= SpeechLanguages.DETECTION_SDK) {
                querySupport(context, detectAmong = detectionProbeLanguages(context)) { detecting ->
                    if (detecting != null) {
                        onResult(detecting.copy(detectionAccepted = true))
                        return@querySupport
                    }
                    // It refused that intent. It may still answer a plain one,
                    // and the language list is worth having either way.
                    querySupport(context, detectAmong = emptyList()) { plain ->
                        if (plain != null) onResult(plain) else queryLanguageDetails(context, onResult)
                    }
                }
                return
            }
            querySupport(context, detectAmong = emptyList()) { support ->
                if (support != null) onResult(support) else queryLanguageDetails(context, onResult)
            }
        }

        /**
         * Two languages for the detection probe, which needs at least two to
         * carry the extras at all. The phone's own locale and US English are
         * the safest pair to ask about; this is a question, not a claim, and
         * the answer only decides whether the engine tolerates the extras.
         */
        private fun detectionProbeLanguages(context: Context): List<String> {
            val device = SpeechLanguages.normalizeTag(Locale.getDefault().toLanguageTag())
            val app = SpeechLanguages.normalizeTag(
                runCatching { context.resources.configuration.locales[0].toLanguageTag() }.getOrDefault(""),
            )
            val probe = LinkedHashSet<String>()
            listOf(device, app, "en-US", "ar-SA").forEach { if (it.isNotEmpty()) probe.add(it) }
            return probe.take(2).toList()
        }

        /** `SpeechRecognizer.checkRecognitionSupport`, Android 13 and later. */
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private fun querySupport(
            context: Context,
            detectAmong: List<String>,
            onResult: (RecognizerLanguages?) -> Unit,
        ) {
            val recognizer = runCatching { SpeechRecognizer.createSpeechRecognizer(context) }.getOrNull()
            if (recognizer == null) {
                onResult(null)
                return
            }
            var answered = false
            fun finish(result: RecognizerLanguages?) {
                if (answered) return
                answered = true
                runCatching { recognizer.destroy() }
                onResult(result)
            }
            val probe = recognitionIntent(context, Locale.getDefault().toLanguageTag(), detectAmong)
            runCatching {
                recognizer.checkRecognitionSupport(
                    probe,
                    context.mainExecutor,
                    object : RecognitionSupportCallback {
                        override fun onSupportResult(support: RecognitionSupport) {
                            val installed = support.installedOnDeviceLanguages
                            val onDevice = (installed + support.supportedOnDeviceLanguages).distinct()
                            finish(
                                RecognizerLanguages(
                                    all = (onDevice + support.onlineLanguages).distinct(),
                                    // Detection runs against models that are
                                    // actually present, so "supported" alone
                                    // is not enough to promise it.
                                    onDevice = installed.ifEmpty { support.supportedOnDeviceLanguages },
                                    preferred = null,
                                    answered = true,
                                ),
                            )
                        }

                        // ERROR_CANNOT_CHECK_SUPPORT among others: the engine
                        // will not say, so nothing is assumed about it.
                        override fun onError(error: Int) = finish(null)
                    },
                )
            }.onFailure { finish(null) }
        }

        /** The pre-Android-13 broadcast; also the fallback when the engine refuses to answer. */
        private fun queryLanguageDetails(context: Context, onResult: (RecognizerLanguages) -> Unit) {
            val intent = Intent(RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS)
            runCatching {
                context.packageManager.queryBroadcastReceivers(intent, 0)
                    .firstOrNull()?.activityInfo?.packageName
            }.getOrNull()?.let(intent::setPackage)
            var answered = false
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(received: Context?, broadcast: Intent?) {
                    if (answered) return
                    answered = true
                    val extras: Bundle? = runCatching { getResultExtras(true) }.getOrNull()
                    val supported = extras?.getStringArrayList(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES)
                    onResult(
                        RecognizerLanguages(
                            all = supported.orEmpty(),
                            // The broadcast does not say which models are on
                            // the device, so detection gets the same list and
                            // the engine rejects what it cannot serve.
                            onDevice = supported.orEmpty(),
                            preferred = extras?.getString(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE),
                            answered = !supported.isNullOrEmpty(),
                        ),
                    )
                }
            }
            runCatching {
                context.sendOrderedBroadcast(
                    intent,
                    null,
                    receiver,
                    null,
                    android.app.Activity.RESULT_OK,
                    null,
                    null,
                )
            }.onFailure {
                if (!answered) {
                    answered = true
                    onResult(RecognizerLanguages())
                }
            }
        }
    }
}

enum class VoiceStatus { Idle, Listening, Transcribing, Error }

enum class VoiceSegmentKind {
    /** The current hypothesis; replaces the previous partial in the field. */
    Partial,

    /**
     * One session of a still-running take is finished: its text stays in the
     * field as ordinary draft text and the next session's partial anchors
     * after it. The take itself is not over, so the strip stays up.
     */
    Commit,

    /** The finished text; stays in the field and closes the take. */
    Final,

    /** The take was cancelled; whatever partial was inserted is removed. */
    Discard,
}

/** One update to the text the composer inserts for the running dictation. */
data class VoiceSegment(val text: String, val kind: VoiceSegmentKind, val serial: Long)

/**
 * The segments the composer has not placed yet, in order.
 *
 * The composer sees one segment at a time on the UI state and reports back
 * with the serial it placed. A newer partial may overwrite an unplaced
 * partial, because the newer hypothesis supersedes it; a commit, a final or
 * a discard must never be overwritten before it was seen. That matters once
 * a take is several sessions: the next session's first partial could
 * otherwise land before the last session's commit was placed, and the
 * committed words would vanish from the draft.
 */
class VoiceSegmentOutbox {
    private var shown: VoiceSegment? = null
    private val waiting = ArrayDeque<VoiceSegment>()

    /** What the composer should be looking at right now. */
    val current: VoiceSegment? get() = shown

    /** Hands [segment] to the composer now, or holds it until the ones before it were placed. Returns what to show now. */
    fun offer(segment: VoiceSegment): VoiceSegment? {
        val visible = shown
        if (visible == null || (visible.kind == VoiceSegmentKind.Partial && waiting.isEmpty())) {
            shown = segment
            return segment
        }
        if (segment.kind == VoiceSegmentKind.Partial && waiting.lastOrNull()?.kind == VoiceSegmentKind.Partial) waiting.removeLast()
        waiting.addLast(segment)
        return null
    }

    /** The composer placed [serial]. Returns what to show next: the next held segment, or null when there is none. */
    fun placed(serial: Long): VoiceSegment? {
        if (shown?.serial != serial) return shown
        shown = waiting.removeFirstOrNull()
        return shown
    }

    /** ✕: nothing held matters any more, only [segment]. */
    fun replaceAll(segment: VoiceSegment): VoiceSegment {
        waiting.clear()
        shown = segment
        return segment
    }
}

data class VoiceEdit(val text: String, val caret: Int, val segmentLength: Int)

/**
 * Replaces the interim dictation segment inside [text]. [anchor] is where the
 * segment starts (the caret when dictation began) and [previousLength] how
 * many characters the previous update inserted there. A space is added before
 * the segment when it follows a non-space character, and a committed or final
 * segment is separated from any text that follows it.
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
    val closes = kind == VoiceSegmentKind.Final || kind == VoiceSegmentKind.Commit
    val trail = if (closes && after.isNotEmpty() && !after[0].isWhitespace()) " " else ""
    val inserted = lead + body + trail
    val replaced = text.substring(0, start) + inserted + after
    return VoiceEdit(replaced, start + lead.length + body.length, inserted.length)
}
