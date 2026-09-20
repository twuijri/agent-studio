package us.i3u.hermesstudio

/**
 * What the composer's recording strip does with each tap.
 *
 * While dictating, the row of pills under the text becomes ✕ · waveform ·
 * ■ · ↑. The three controls mean three different endings for the take:
 *
 *  - ✕ cancels: recognition stops and the draft goes back to what it was
 *    before the take began, whatever the engine had inserted meanwhile;
 *  - ■ stops: the take ends, its text stays, the pills come back;
 *  - ↑ sends: the take ends and, once its final text has landed in the
 *    draft, the draft is sent.
 *
 * The engine answers asynchronously — a stopped take still has a final
 * result on the way — so "send" is remembered until [Event.Finished] says the
 * text is in place. The reducer is pure so those rules can be read in a test;
 * the composer runs the [Effect]s it returns.
 */
object RecordingStrip {

    enum class Phase {
        /** The pill row is showing. */
        Idle,

        /** The strip is showing; the engine is listening. */
        Recording,

        /** The take was ended by ■ or ↑ and its final text is on the way. */
        Finishing,
    }

    data class State(
        val phase: Phase = Phase.Idle,
        /** The draft as it was when the take began; what ✕ restores. */
        val draftBeforeTake: String = "",
        /** ↑ was tapped: send once the final text has landed. */
        val sendWhenDone: Boolean = false,
    )

    sealed interface Event {
        /** The mic was tapped with [draft] in the field. */
        data class Start(val draft: String) : Event

        /** ■: end the take, keep the text. */
        data object Stop : Event

        /** ✕: drop the take, restore the draft. */
        data object Cancel : Event

        /** ↑: end the take, then send. */
        data object Send : Event

        /** The take's final (or discarded) text has been placed in the draft. */
        data class Finished(val hasPayload: Boolean) : Event

        /** The take ended in an error; whatever was inserted stays. */
        data object Failed : Event
    }

    sealed interface Effect {
        data object StartTake : Effect
        data object StopTake : Effect
        data object CancelTake : Effect
        data class RestoreDraft(val draft: String) : Effect
        data object SendDraft : Effect
    }

    data class Step(val state: State, val effects: List<Effect> = emptyList())

    /** Whether the strip replaces the pill row in this [phase]. */
    fun showsStrip(phase: Phase): Boolean = phase == Phase.Recording

    fun reduce(state: State, event: Event): Step = when (state.phase) {
        Phase.Idle -> when (event) {
            is Event.Start -> Step(
                State(phase = Phase.Recording, draftBeforeTake = event.draft, sendWhenDone = false),
                listOf(Effect.StartTake),
            )
            else -> Step(state)
        }

        Phase.Recording -> when (event) {
            Event.Cancel -> Step(
                State(),
                listOf(Effect.CancelTake, Effect.RestoreDraft(state.draftBeforeTake)),
            )
            Event.Stop -> Step(state.copy(phase = Phase.Finishing, sendWhenDone = false), listOf(Effect.StopTake))
            Event.Send -> Step(state.copy(phase = Phase.Finishing, sendWhenDone = true), listOf(Effect.StopTake))
            // The take ended without a tap: the ten-minute ceiling, or a real
            // error. A session the engine ends at a pause never reaches here —
            // ContinuousDictation restarts it and commits its text without a
            // Finished. The text stays and nothing was asked to be sent.
            is Event.Finished, Event.Failed -> Step(State())
            is Event.Start -> Step(state)
        }

        Phase.Finishing -> when (event) {
            is Event.Finished -> Step(
                State(),
                if (state.sendWhenDone && event.hasPayload) listOf(Effect.SendDraft) else emptyList(),
            )
            Event.Failed -> Step(State())
            // The strip is already gone; there is nothing left to cancel or
            // stop, and a second ↑ has nothing new to say.
            else -> Step(state)
        }
    }
}
