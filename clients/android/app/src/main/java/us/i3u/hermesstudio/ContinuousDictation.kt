package us.i3u.hermesstudio

import android.speech.SpeechRecognizer

/**
 * Why one take is a chain of recognizer sessions, and what to do each time
 * one of them ends.
 *
 * Android's [SpeechRecognizer] is built for a single utterance. Once its
 * endpointer decides the speaker has finished — a pause of a second or two
 * on Google's engine — it fires `onEndOfSpeech`, delivers `onResults` with
 * "the results for the full speech since onReadyForSpeech", and the session
 * is over; when it heard nothing usable it delivers `ERROR_SPEECH_TIMEOUT`
 * or `ERROR_NO_MATCH` instead. The silence extras on the intent are hints:
 * the platform documents them as values that "depending on the recognizer
 * implementation ... may have no effect", and Google's engine ignores them.
 * That is why build 48 stopped by itself after "أهلاً وسهلاً شلونك" and a
 * breath: nothing was pressed, the engine simply finished its one utterance.
 *
 * So while the recording strip is up, a session that ends on its own is
 * restarted at once with the same intent. Its text is committed into the
 * draft first ([VoiceSegmentKind.Commit]), the next session's partials
 * append after it, and the strip never learns that anything happened. The
 * chain ends only with ■ or ↑ ([Event.Stop]), ✕ ([Event.Cancel]), a real
 * error, or the [MAX_TAKE_MILLIS] ceiling.
 *
 * The rules are pure so they can be read in a test. The view model runs the
 * [Effect]s: it owns the recognizer, the timers and the clock, and passes
 * the time in with each event rather than reading it here.
 */
object ContinuousDictation {

    /** A take does not run forever: after this it ends by itself, text kept, with a visible notice. */
    const val MAX_TAKE_MILLIS = 10L * 60 * 1000

    /**
     * `ERROR_RECOGNIZER_BUSY` (or `ERROR_CLIENT`) right after a restart means
     * the engine has not let go of the last session yet. One retry, on a
     * fresh recognizer, after this pause; a second refusal is a real error.
     */
    const val BUSY_RETRY_DELAY_MILLIS = 300L
    const val BUSY_RETRIES = 1

    /**
     * How long ■ or ↑ waits for the engine's last text before keeping what it
     * already has. A stop that lands between sessions can otherwise hang.
     */
    const val STOP_TIMEOUT_MILLIS = 4_000L

    enum class Phase {
        /** No take; every event is ignored until [Event.Opened]. */
        Closed,

        /** A session is running; the strip shows the waveform. */
        Listening,

        /**
         * Between sessions: the last one ended on its own and the next has
         * been asked for but has not started yet — at once, or after the busy
         * pause. Nothing is listening, so a stop here needs no engine answer.
         */
        Restarting,

        /** ■, ↑ or the ceiling ended the take; the last session's text is on the way. */
        Stopping,
    }

    data class State(
        val phase: Phase = Phase.Closed,
        /** When the take opened, in the caller's clock. */
        val openedAt: Long = 0L,
        /** The running session's latest hypothesis; already in the draft as a partial. */
        val partial: String = "",
        /** Whether an earlier session of this take has put text into the draft. */
        val committed: Boolean = false,
        /** Sessions started so far, the first one included. */
        val sessions: Int = 1,
        /** Busy retries spent since the last session that produced anything. */
        val busyRetries: Int = 0,
        /** The ceiling, not the owner, asked for the stop in progress. */
        val ceiling: Boolean = false,
    )

    sealed interface Event {
        /** The recognizer accepted the first session. */
        data class Opened(val now: Long) : Event

        /** The recognizer accepted a restarted session. */
        data object SessionStarted : Event

        /** A hypothesis from the running session. */
        data class Partial(val text: String) : Event

        /** The running session delivered its final text (possibly blank). */
        data class SessionEnded(val text: String, val now: Long) : Event

        /** The running session, or the attempt to start one, failed with a platform code. */
        data class SessionFailed(val code: Int, val now: Long) : Event

        /** ■ or ↑: end the take, keep the text. The strip decides whether to send. */
        data object Stop : Event

        /** ✕: drop the take. */
        data object Cancel : Event

        /** The take has run for [MAX_TAKE_MILLIS]. */
        data object CeilingReached : Event

        /** [STOP_TIMEOUT_MILLIS] passed after [Effect.StopSession] with no answer. */
        data object StopTimedOut : Event
    }

    /** How a take ended, for the status the mic button shows afterwards. */
    sealed interface Outcome {
        /** ■ or ↑: the text is in the draft. */
        data object Done : Outcome

        /** The ceiling: the text is in the draft and the owner is told why it stopped. */
        data object Ceiling : Outcome

        /** The take ended with no text at all. */
        data object NoSpeech : Outcome

        /** A real platform error; whatever text there was stays in the draft. */
        data class Failed(val code: Int) : Outcome

        /** ✕. */
        data object Cancelled : Outcome
    }

    sealed interface Effect {
        /** Place [text] in the draft; see [VoiceSegmentKind]. */
        data class Emit(val text: String, val kind: VoiceSegmentKind) : Effect

        /**
         * Start the next session with the same intent, after [afterMillis].
         * [fresh] asks for a new recognizer instance rather than reusing the
         * one that just refused. The caller answers with [Event.SessionStarted]
         * or [Event.SessionFailed].
         */
        data class Restart(val fresh: Boolean = false, val afterMillis: Long = 0L) : Effect

        /** Ask the running session for its final text (`stopListening`). */
        data object StopSession : Effect

        /** The take is over; release the recognizer and show [outcome]. */
        data class Close(val outcome: Outcome) : Effect
    }

    data class Step(val state: State, val effects: List<Effect> = emptyList())

    /** The engine heard nothing it could use: not an error during a take, just the end of one session. */
    fun isTransient(code: Int): Boolean =
        code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

    /** The engine (or the client binding) was not ready for the next session yet. */
    fun isBusy(code: Int): Boolean =
        code == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || code == SpeechRecognizer.ERROR_CLIENT

    fun reduce(state: State, event: Event): Step = when (state.phase) {
        Phase.Closed -> when (event) {
            is Event.Opened -> Step(State(phase = Phase.Listening, openedAt = event.now))
            else -> Step(state)
        }

        Phase.Listening -> when (event) {
            is Event.Partial -> Step(
                state.copy(partial = event.text, busyRetries = 0),
                listOf(Effect.Emit(event.text, VoiceSegmentKind.Partial)),
            )
            is Event.SessionEnded -> sessionOver(state, event.text.ifBlank { state.partial }, event.now)
            is Event.SessionFailed -> when {
                isTransient(event.code) -> sessionOver(state, state.partial, event.now)
                isBusy(event.code) && state.busyRetries < BUSY_RETRIES -> retry(state)
                else -> fail(state, event.code)
            }
            Event.Stop -> Step(state.copy(phase = Phase.Stopping), listOf(Effect.StopSession))
            Event.CeilingReached -> Step(state.copy(phase = Phase.Stopping, ceiling = true), listOf(Effect.StopSession))
            Event.Cancel -> cancel()
            Event.SessionStarted, Event.StopTimedOut, is Event.Opened -> Step(state)
        }

        Phase.Restarting -> when (event) {
            Event.SessionStarted -> Step(state.copy(phase = Phase.Listening, sessions = state.sessions + 1))
            is Event.SessionFailed -> when {
                isBusy(event.code) && state.busyRetries < BUSY_RETRIES -> retry(state)
                else -> fail(state, event.code)
            }
            // Nothing is listening, so there is nothing to wait for: the
            // committed text is already in the draft and can go as it is.
            Event.Stop -> finish(state, spoken = "", outcome = Outcome.Done)
            Event.CeilingReached -> finish(state, spoken = "", outcome = Outcome.Ceiling)
            Event.Cancel -> cancel()
            is Event.Partial, is Event.SessionEnded, Event.StopTimedOut, is Event.Opened -> Step(state)
        }

        Phase.Stopping -> when (event) {
            // The engine may still refine the hypothesis after being asked to stop.
            is Event.Partial -> Step(
                state.copy(partial = event.text),
                listOf(Effect.Emit(event.text, VoiceSegmentKind.Partial)),
            )
            is Event.SessionEnded -> finish(state, event.text.ifBlank { state.partial }, stopOutcome(state))
            is Event.SessionFailed -> when {
                isTransient(event.code) -> finish(state, state.partial, stopOutcome(state))
                else -> fail(state, event.code)
            }
            Event.StopTimedOut -> finish(state, state.partial, stopOutcome(state))
            Event.Cancel -> cancel()
            // A second ■, or the ceiling landing after the owner already
            // stopped: the take is ending anyway.
            Event.Stop, Event.CeilingReached, Event.SessionStarted, is Event.Opened -> Step(state)
        }
    }

    private fun stopOutcome(state: State): Outcome = if (state.ceiling) Outcome.Ceiling else Outcome.Done

    /**
     * A session ended on its own while the strip is still up. Its text is
     * committed and the next session is asked for — unless the take has run
     * for the ceiling already, in which case this is where it ends.
     */
    private fun sessionOver(state: State, spoken: String, now: Long): Step {
        if (now - state.openedAt >= MAX_TAKE_MILLIS) return finish(state, spoken, Outcome.Ceiling)
        val effects = buildList {
            if (spoken.isNotBlank()) add(Effect.Emit(spoken, VoiceSegmentKind.Commit))
            add(Effect.Restart())
        }
        return Step(
            state.copy(
                phase = Phase.Restarting,
                partial = "",
                committed = state.committed || spoken.isNotBlank(),
                busyRetries = 0,
            ),
            effects,
        )
    }

    /** The engine refused the next session; commit what the last one had and try once more, on a new instance, after a pause. */
    private fun retry(state: State): Step {
        val effects = buildList {
            if (state.partial.isNotBlank()) add(Effect.Emit(state.partial, VoiceSegmentKind.Commit))
            add(Effect.Restart(fresh = true, afterMillis = BUSY_RETRY_DELAY_MILLIS))
        }
        return Step(
            state.copy(
                phase = Phase.Restarting,
                partial = "",
                committed = state.committed || state.partial.isNotBlank(),
                busyRetries = state.busyRetries + 1,
            ),
            effects,
        )
    }

    /**
     * The take ends and its last text — [spoken] from the current session,
     * plus whatever earlier sessions committed — stays in the draft. A blank
     * final still has to reach the composer when something was committed, so
     * the strip learns the text is complete and ↑ can send it. A take that
     * produced nothing at all is reported as such.
     */
    private fun finish(state: State, spoken: String, outcome: Outcome): Step {
        val closed = state.copy(phase = Phase.Closed, partial = "")
        return when {
            spoken.isNotBlank() -> Step(closed, listOf(Effect.Emit(spoken, VoiceSegmentKind.Final), Effect.Close(outcome)))
            state.committed -> Step(closed, listOf(Effect.Emit("", VoiceSegmentKind.Final), Effect.Close(outcome)))
            else -> Step(closed, listOf(Effect.Emit("", VoiceSegmentKind.Discard), Effect.Close(Outcome.NoSpeech)))
        }
    }

    /** A real error: what the session had so far is kept, and the take ends in the error state. */
    private fun fail(state: State, code: Int): Step {
        val closed = state.copy(phase = Phase.Closed, partial = "")
        val kept = when {
            state.partial.isNotBlank() -> Effect.Emit(state.partial, VoiceSegmentKind.Final)
            state.committed -> Effect.Emit("", VoiceSegmentKind.Final)
            else -> Effect.Emit("", VoiceSegmentKind.Discard)
        }
        return Step(closed, listOf(kept, Effect.Close(Outcome.Failed(code))))
    }

    private fun cancel(): Step = Step(State(), listOf(Effect.Close(Outcome.Cancelled)))
}
