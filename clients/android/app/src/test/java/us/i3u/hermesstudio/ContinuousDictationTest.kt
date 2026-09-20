package us.i3u.hermesstudio

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import us.i3u.hermesstudio.ContinuousDictation.Effect
import us.i3u.hermesstudio.ContinuousDictation.Event
import us.i3u.hermesstudio.ContinuousDictation.Outcome
import us.i3u.hermesstudio.ContinuousDictation.Phase
import us.i3u.hermesstudio.ContinuousDictation.State

/**
 * The defect: Android's recognizer ends a session at the first pause, and
 * build 48 let that end the take. These rules keep the take open across
 * sessions until ■, ↑ or ✕, a real error, or the ceiling, and they are pure
 * so each ending can be read here rather than found on a phone.
 */
class ContinuousDictationTest {

    private val t0 = 1_000L

    private fun opened(): State = ContinuousDictation.reduce(State(), Event.Opened(t0)).state

    /** Runs [events] in order from [start]; returns the final state and every effect in order. */
    private fun run(start: State, vararg events: Event): Pair<State, List<Effect>> {
        var state = start
        val effects = mutableListOf<Effect>()
        events.forEach { event ->
            val step = ContinuousDictation.reduce(state, event)
            state = step.state
            effects += step.effects
        }
        return state to effects
    }

    @Test
    fun `a session that ends on its own while recording is restarted, its text committed first`() {
        val (state, effects) = run(opened(), Event.Partial("أهلاً وسهلاً"), Event.SessionEnded("أهلاً وسهلاً شلونك", t0 + 4_000))
        assertEquals(Phase.Restarting, state.phase)
        assertTrue(state.committed)
        assertEquals("", state.partial)
        assertEquals(
            listOf(
                Effect.Emit("أهلاً وسهلاً", VoiceSegmentKind.Partial),
                Effect.Emit("أهلاً وسهلاً شلونك", VoiceSegmentKind.Commit),
                Effect.Restart(),
            ),
            effects,
        )
        assertFalse("the strip is never told the take ended", effects.any { it is Effect.Close })
        val listening = ContinuousDictation.reduce(state, Event.SessionStarted).state
        assertEquals(Phase.Listening, listening.phase)
        assertEquals(2, listening.sessions)
    }

    @Test
    fun `no match and speech timeout during a take are not errors, the take restarts silently`() {
        listOf(SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT).forEach { code ->
            val (silent, effects) = run(opened(), Event.SessionFailed(code, t0 + 6_000))
            assertEquals("code $code restarts", Phase.Restarting, silent.phase)
            assertEquals("nothing to commit, only a restart", listOf(Effect.Restart()), effects)
            // With a hypothesis in flight, the timeout is how the engine ends
            // a session after the user stopped talking: the hypothesis is kept.
            val (withText, kept) = run(opened(), Event.Partial("hello"), Event.SessionFailed(code, t0 + 6_000))
            assertEquals(Phase.Restarting, withText.phase)
            assertEquals(listOf(Effect.Emit("hello", VoiceSegmentKind.Commit), Effect.Restart()), kept.drop(1))
        }
    }

    @Test
    fun `a session that ends after stop finishes the take with a final`() {
        val (state, effects) = run(opened(), Event.Partial("hel"), Event.Stop, Event.SessionEnded("hello", t0 + 3_000))
        assertEquals(Phase.Closed, state.phase)
        assertEquals(
            listOf(
                Effect.Emit("hel", VoiceSegmentKind.Partial),
                Effect.StopSession,
                Effect.Emit("hello", VoiceSegmentKind.Final),
                Effect.Close(Outcome.Done),
            ),
            effects,
        )
        assertFalse("no restart after a stop", effects.any { it is Effect.Restart })
    }

    @Test
    fun `after stop, a blank final or a timeout keeps the last hypothesis`() {
        val (_, blank) = run(opened(), Event.Partial("hello"), Event.Stop, Event.SessionEnded("", t0 + 3_000))
        assertEquals(listOf(Effect.Emit("hello", VoiceSegmentKind.Final), Effect.Close(Outcome.Done)), blank.drop(2))
        val (_, timedOut) = run(opened(), Event.Partial("hello"), Event.Stop, Event.SessionFailed(SpeechRecognizer.ERROR_SPEECH_TIMEOUT, t0 + 3_000))
        assertEquals(listOf(Effect.Emit("hello", VoiceSegmentKind.Final), Effect.Close(Outcome.Done)), timedOut.drop(2))
        val (_, unanswered) = run(opened(), Event.Partial("hello"), Event.Stop, Event.StopTimedOut)
        assertEquals("the engine never answered; what was heard is kept", listOf(Effect.Emit("hello", VoiceSegmentKind.Final), Effect.Close(Outcome.Done)), unanswered.drop(2))
    }

    @Test
    fun `a take that produced nothing at all ends as no speech`() {
        val (_, effects) = run(opened(), Event.Stop, Event.SessionEnded("", t0 + 2_000))
        assertEquals(listOf(Effect.StopSession, Effect.Emit("", VoiceSegmentKind.Discard), Effect.Close(Outcome.NoSpeech)), effects)
    }

    @Test
    fun `real errors stop the take and keep what was heard`() {
        listOf(
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
            SpeechRecognizer.ERROR_AUDIO,
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        ).forEach { code ->
            val (state, effects) = run(opened(), Event.Partial("so far"), Event.SessionFailed(code, t0 + 2_000))
            assertEquals("code $code closes the take", Phase.Closed, state.phase)
            assertEquals(
                "code $code keeps the hypothesis and reports the error",
                listOf(Effect.Emit("so far", VoiceSegmentKind.Final), Effect.Close(Outcome.Failed(code))),
                effects.drop(1),
            )
            val (_, empty) = run(opened(), Event.SessionFailed(code, t0 + 2_000))
            assertEquals(listOf(Effect.Emit("", VoiceSegmentKind.Discard), Effect.Close(Outcome.Failed(code))), empty)
        }
    }

    @Test
    fun `a busy engine gets one retry on a fresh recognizer, then it is an error`() {
        val busy = SpeechRecognizer.ERROR_RECOGNIZER_BUSY
        val (retrying, effects) = run(opened(), Event.Partial("one"), Event.SessionFailed(busy, t0 + 1_000))
        assertEquals(Phase.Restarting, retrying.phase)
        assertEquals(1, retrying.busyRetries)
        assertEquals(
            listOf(
                Effect.Emit("one", VoiceSegmentKind.Commit),
                Effect.Restart(fresh = true, afterMillis = ContinuousDictation.BUSY_RETRY_DELAY_MILLIS),
            ),
            effects.drop(1),
        )
        // The retry is refused too.
        val (failed, again) = run(retrying, Event.SessionFailed(busy, t0 + 1_400))
        assertEquals(Phase.Closed, failed.phase)
        assertEquals(listOf(Effect.Emit("", VoiceSegmentKind.Final), Effect.Close(Outcome.Failed(busy))), again)
        // A session that produced text resets the budget.
        val (recovered, _) = run(retrying, Event.SessionStarted, Event.Partial("two"))
        assertEquals(0, recovered.busyRetries)
    }

    @Test
    fun `the ceiling ends the take with its text kept and a notice`() {
        val ceiling = ContinuousDictation.MAX_TAKE_MILLIS
        // The timer fires mid-session: the session is stopped and its text becomes the final.
        val (stopping, asked) = run(opened(), Event.Partial("late"), Event.CeilingReached)
        assertEquals(Phase.Stopping, stopping.phase)
        assertTrue(stopping.ceiling)
        assertEquals(listOf(Effect.StopSession), asked.drop(1))
        val (closed, ended) = run(stopping, Event.SessionEnded("late words", t0 + ceiling + 10))
        assertEquals(Phase.Closed, closed.phase)
        assertEquals(listOf(Effect.Emit("late words", VoiceSegmentKind.Final), Effect.Close(Outcome.Ceiling)), ended)
        // A session that ends on its own at or past the ceiling is not restarted.
        val (notRestarted, effects) = run(opened(), Event.SessionEnded("last", t0 + ceiling))
        assertEquals(Phase.Closed, notRestarted.phase)
        assertEquals(listOf(Effect.Emit("last", VoiceSegmentKind.Final), Effect.Close(Outcome.Ceiling)), effects)
        // Just under it, it is.
        val (restarted, _) = run(opened(), Event.SessionEnded("more", t0 + ceiling - 1))
        assertEquals(Phase.Restarting, restarted.phase)
    }

    @Test
    fun `send during a restart gap sends the committed text without waiting for an engine`() {
        val (gap, _) = run(opened(), Event.Partial("first"), Event.SessionEnded("first sentence", t0 + 3_000))
        assertEquals(Phase.Restarting, gap.phase)
        val (closed, effects) = run(gap, Event.Stop)
        assertEquals(Phase.Closed, closed.phase)
        assertEquals(
            "a blank final tells the strip the text is complete, so ↑ can send it",
            listOf(Effect.Emit("", VoiceSegmentKind.Final), Effect.Close(Outcome.Done)),
            effects,
        )
        assertFalse("nothing is listening, so nothing is asked to stop", effects.contains(Effect.StopSession))
        // The restart that was posted for the gap finds the take closed and does nothing.
        assertEquals(closed, ContinuousDictation.reduce(closed, Event.SessionStarted).state)
        assertTrue(ContinuousDictation.reduce(closed, Event.SessionStarted).effects.isEmpty())
    }

    @Test
    fun `stop during a gap with nothing committed is no speech, and cancel closes from anywhere`() {
        val (gap, _) = run(opened(), Event.SessionFailed(SpeechRecognizer.ERROR_NO_MATCH, t0 + 5_000))
        val (_, effects) = run(gap, Event.Stop)
        assertEquals(listOf(Effect.Emit("", VoiceSegmentKind.Discard), Effect.Close(Outcome.NoSpeech)), effects)
        listOf(opened(), gap, run(opened(), Event.Stop).first).forEach { from ->
            val step = ContinuousDictation.reduce(from, Event.Cancel)
            assertEquals("cancel from ${from.phase}", State(), step.state)
            assertEquals(listOf(Effect.Close(Outcome.Cancelled)), step.effects)
        }
        // And a closed take ignores the engine.
        listOf(Event.SessionEnded("late", t0), Event.SessionFailed(SpeechRecognizer.ERROR_AUDIO, t0), Event.Stop, Event.CeilingReached).forEach { event ->
            val step = ContinuousDictation.reduce(State(), event)
            assertEquals(State(), step.state)
            assertTrue(step.effects.isEmpty())
        }
    }

    /**
     * Three sessions, driven through the same merge the composer runs
     * ([applyVoiceSegment] with the anchor reset after every commit), end up
     * as one draft with the merge's own separators: a space between
     * sessions, none doubled, and the caret after the last word.
     */
    @Test
    fun `text is committed across three sessions with the separator rules the composer uses`() {
        var draft = "Note:"
        var caret = draft.length
        var anchor: Int? = null
        var length = 0
        fun place(effect: Effect.Emit) {
            val at = anchor ?: caret
            val edit = applyVoiceSegment(draft, at, length, effect.text, effect.kind)
            draft = edit.text
            caret = edit.caret
            if (effect.kind == VoiceSegmentKind.Partial) {
                anchor = at
                length = edit.segmentLength
            } else {
                anchor = null
                length = 0
            }
        }
        val (_, effects) = run(
            opened(),
            Event.Partial("أهلاً"),
            Event.Partial("أهلاً وسهلاً"),
            Event.SessionEnded("أهلاً وسهلاً شلونك", t0 + 3_000),
            Event.SessionStarted,
            Event.Partial("أنا"),
            Event.SessionFailed(SpeechRecognizer.ERROR_SPEECH_TIMEOUT, t0 + 8_000),
            Event.SessionStarted,
            Event.Partial("بخير"),
            Event.Stop,
            Event.SessionEnded("بخير الحمد لله", t0 + 12_000),
        )
        effects.filterIsInstance<Effect.Emit>().forEach(::place)
        assertEquals("Note: أهلاً وسهلاً شلونك أنا بخير الحمد لله", draft)
        assertEquals(draft.length, caret)
        assertEquals(4, effects.count { it is Effect.Emit && it.kind == VoiceSegmentKind.Partial })
        assertEquals(2, effects.count { it is Effect.Emit && it.kind == VoiceSegmentKind.Commit })
        assertEquals(1, effects.count { it is Effect.Emit && it.kind == VoiceSegmentKind.Final })
        assertEquals(2, effects.count { it is Effect.Restart })
        assertEquals(Effect.Close(Outcome.Done), effects.last())
    }

    @Test
    fun `a commit gets a trailing space before text that follows it, like a final`() {
        // Dictated into the middle of "Hellothere", where nothing separates
        // the caret from what follows.
        val commit = applyVoiceSegment("Hellothere", 5, 0, "world", VoiceSegmentKind.Commit)
        assertEquals("Hello world there", commit.text)
        assertEquals("the caret sits after the committed word, before the trailing space", 11, commit.caret)
        val partial = applyVoiceSegment("Hellothere", 5, 0, "world", VoiceSegmentKind.Partial)
        assertEquals("a partial is still open, so no trailing space yet", "Hello worldthere", partial.text)
    }

    @Test
    fun `the outbox never lets a commit be overwritten before the composer placed it`() {
        val outbox = VoiceSegmentOutbox()
        val p1 = VoiceSegment("hel", VoiceSegmentKind.Partial, 1)
        val p2 = VoiceSegment("hello", VoiceSegmentKind.Partial, 2)
        assertEquals(p1, outbox.offer(p1))
        assertEquals("a newer partial replaces an unplaced partial", p2, outbox.offer(p2))
        val commit = VoiceSegment("hello world", VoiceSegmentKind.Commit, 3)
        assertEquals("a commit replaces an unplaced partial", commit, outbox.offer(commit))
        val next1 = VoiceSegment("and", VoiceSegmentKind.Partial, 4)
        val next2 = VoiceSegment("and then", VoiceSegmentKind.Partial, 5)
        assertNull("the next session's partial waits behind the commit", outbox.offer(next1))
        assertNull(outbox.offer(next2))
        assertEquals("the commit is still what the composer sees", commit, outbox.current)
        assertEquals("a stale serial changes nothing", commit, outbox.placed(2))
        assertEquals("once placed, the newest waiting partial follows", next2, outbox.placed(3))
        val final = VoiceSegment("and then some", VoiceSegmentKind.Final, 6)
        assertEquals(final, outbox.offer(final))
        assertNull(outbox.placed(6))
        assertNull(outbox.current)
        // ✕ throws everything held away.
        outbox.offer(VoiceSegment("x", VoiceSegmentKind.Commit, 7))
        outbox.offer(VoiceSegment("y", VoiceSegmentKind.Partial, 8))
        val discard = VoiceSegment("", VoiceSegmentKind.Discard, 9)
        assertEquals(discard, outbox.replaceAll(discard))
        assertNull(outbox.placed(9))
    }
}
