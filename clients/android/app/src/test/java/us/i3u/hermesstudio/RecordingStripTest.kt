package us.i3u.hermesstudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import us.i3u.hermesstudio.RecordingStrip.Effect
import us.i3u.hermesstudio.RecordingStrip.Event
import us.i3u.hermesstudio.RecordingStrip.Phase
import us.i3u.hermesstudio.RecordingStrip.State

/**
 * The strip has three ways to end a take and they must not blur into each
 * other: ✕ restores the draft, ■ keeps the text, ↑ keeps it and sends it —
 * but only once the engine's final text has landed, because the engine
 * answers after the tap, not during it.
 */
class RecordingStripTest {

    private fun recording(draft: String = "before"): State =
        RecordingStrip.reduce(State(), Event.Start(draft)).state

    @Test
    fun `the mic tap starts a take and remembers the draft it found`() {
        val step = RecordingStrip.reduce(State(), Event.Start("hello"))
        assertEquals(Phase.Recording, step.state.phase)
        assertEquals("hello", step.state.draftBeforeTake)
        assertFalse(step.state.sendWhenDone)
        assertEquals(listOf(Effect.StartTake), step.effects)
        assertTrue(RecordingStrip.showsStrip(step.state.phase))
    }

    @Test
    fun `cancel drops the take and restores the pre-take draft`() {
        val step = RecordingStrip.reduce(recording("kept as it was"), Event.Cancel)
        assertEquals(Phase.Idle, step.state.phase)
        assertEquals(listOf(Effect.CancelTake, Effect.RestoreDraft("kept as it was")), step.effects)
        assertFalse("the pills are back", RecordingStrip.showsStrip(step.state.phase))
    }

    @Test
    fun `stop ends the take, keeps the text and sends nothing`() {
        val stopped = RecordingStrip.reduce(recording(), Event.Stop)
        assertEquals(Phase.Finishing, stopped.state.phase)
        assertEquals(listOf(Effect.StopTake), stopped.effects)
        assertFalse("the strip is gone as soon as the take is ended", RecordingStrip.showsStrip(stopped.state.phase))
        val finished = RecordingStrip.reduce(stopped.state, Event.Finished(hasPayload = true))
        assertEquals(Phase.Idle, finished.state.phase)
        assertTrue("nothing is restored and nothing is sent", finished.effects.isEmpty())
    }

    @Test
    fun `send ends the take and sends once the final text has landed`() {
        val sending = RecordingStrip.reduce(recording(), Event.Send)
        assertEquals(Phase.Finishing, sending.state.phase)
        assertTrue(sending.state.sendWhenDone)
        assertEquals("the engine is stopped first, the send waits", listOf(Effect.StopTake), sending.effects)
        val finished = RecordingStrip.reduce(sending.state, Event.Finished(hasPayload = true))
        assertEquals(Phase.Idle, finished.state.phase)
        assertEquals(listOf(Effect.SendDraft), finished.effects)
    }

    @Test
    fun `send with nothing to send sends nothing`() {
        val sending = RecordingStrip.reduce(recording(""), Event.Send).state
        val finished = RecordingStrip.reduce(sending, Event.Finished(hasPayload = false))
        assertEquals(Phase.Idle, finished.state.phase)
        assertTrue(finished.effects.isEmpty())
    }

    @Test
    fun `a take the engine ends by itself keeps its text and sends nothing`() {
        // Silence timed out, or the engine failed: the text it produced is
        // already in the draft and nobody asked for it to be sent.
        val timedOut = RecordingStrip.reduce(recording(), Event.Finished(hasPayload = true))
        assertEquals(Phase.Idle, timedOut.state.phase)
        assertTrue(timedOut.effects.isEmpty())
        val failed = RecordingStrip.reduce(recording(), Event.Failed)
        assertEquals(Phase.Idle, failed.state.phase)
        assertTrue(failed.effects.isEmpty())
    }

    @Test
    fun `a failure after stop or send sends nothing either`() {
        val sending = RecordingStrip.reduce(recording(), Event.Send).state
        val failed = RecordingStrip.reduce(sending, Event.Failed)
        assertEquals(Phase.Idle, failed.state.phase)
        assertTrue(failed.effects.isEmpty())
    }

    @Test
    fun `taps that do not belong to the current phase change nothing`() {
        listOf(Event.Stop, Event.Cancel, Event.Send, Event.Failed, Event.Finished(true)).forEach { event ->
            val step = RecordingStrip.reduce(State(), event)
            assertEquals("idle + $event", State(), step.state)
            assertTrue(step.effects.isEmpty())
        }
        val live = recording()
        assertEquals("a second mic tap while recording is ignored", live, RecordingStrip.reduce(live, Event.Start("other")).state)
        val finishing = RecordingStrip.reduce(live, Event.Stop).state
        listOf(Event.Stop, Event.Cancel, Event.Send, Event.Start("x")).forEach { event ->
            val step = RecordingStrip.reduce(finishing, event)
            assertEquals("finishing + $event", finishing, step.state)
            assertTrue("no restore once the take was kept", step.effects.isEmpty())
        }
    }

    @Test
    fun `only the recording phase shows the strip`() {
        assertTrue(RecordingStrip.showsStrip(Phase.Recording))
        assertFalse(RecordingStrip.showsStrip(Phase.Idle))
        assertFalse(RecordingStrip.showsStrip(Phase.Finishing))
    }

    /**
     * ✕ restores the pre-take draft, and the draft it restores must be the
     * one the dictation merge would also arrive at by removing what it had
     * inserted — including the space it added before the segment.
     */
    @Test
    fun `cancel restores the draft with the same separator rules the merge uses`() {
        data class Case(val draft: String, val caret: Int)
        listOf(
            Case("Hello", 5), // the merge adds a space; cancel takes it away
            Case("Hello ", 6), // already spaced; nothing extra was added
            Case("", 0), // an empty draft stays empty
            Case("Hello there", 5), // dictated into the middle of a draft
            Case("مرحبا", 5), // an Arabic draft
        ).forEach { case ->
            val partial = applyVoiceSegment(case.draft, case.caret, 0, "world", VoiceSegmentKind.Partial)
            assertTrue("the take inserted something into ${case.draft}", partial.text != case.draft)
            val discarded = applyVoiceSegment(partial.text, case.caret, partial.segmentLength, "", VoiceSegmentKind.Discard)
            val restored = RecordingStrip.reduce(recording(case.draft), Event.Cancel)
                .effects.filterIsInstance<Effect.RestoreDraft>().single().draft
            assertEquals("the merge's own removal for '${case.draft}'", case.draft, discarded.text)
            assertEquals("the strip's restore for '${case.draft}'", case.draft, restored)
        }
    }
}
