package us.i3u.hermesstudio

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt
import us.i3u.hermesstudio.ui.theme.CoreHubTokens

/**
 * The sound level behind the recording strip's waveform.
 *
 * There is no second audio source: while the device recognizer runs, its own
 * `onRmsChanged` callback is the only measurement, and while the server
 * recorder runs, the PCM it is already capturing is measured in place. Both
 * end up as one number in 0…1 that [AudioLevelMeter] smooths and [Waveform]
 * keeps a short history of. Everything here is arithmetic, so the shape of the
 * strip can be read in a test rather than by speaking into a phone. The
 * numbers are the same as the iOS client's, so the two phones draw one strip.
 */
object DictationLevels {

    /**
     * Google's recognizer reports RMS in dB roughly between −2 (silence) and
     * 10 (a loud voice close to the phone). Anything outside is clamped.
     */
    const val MIN_DB = -2f
    const val MAX_DB = 10f

    /**
     * A server take is measured from raw 16-bit PCM instead: silence in a
     * quiet room sits around −50 dBFS and normal speech reaches about −10.
     */
    const val PCM_FLOOR_DBFS = -50f
    const val PCM_CEILING_DBFS = -10f

    /** One frame of the waveform every 50 ms, which is the ~20 Hz the strip redraws at. */
    const val TICK_MS = CoreHubTokens.Metrics.waveformTickMs

    /** How many bars the strip shows; the oldest falls off the far end. */
    const val BARS = CoreHubTokens.Metrics.waveformBars

    /** The recognizer's dB reading as a level in 0…1. */
    fun normalize(rmsDb: Float): Float {
        if (rmsDb.isNaN()) return 0f
        return ((rmsDb - MIN_DB) / (MAX_DB - MIN_DB)).coerceIn(0f, 1f)
    }

    /** A dBFS reading of raw PCM as a level in 0…1. */
    fun normalizeDbfs(dbfs: Float): Float {
        if (dbfs.isNaN()) return 0f
        return ((dbfs - PCM_FLOOR_DBFS) / (PCM_CEILING_DBFS - PCM_FLOOR_DBFS)).coerceIn(0f, 1f)
    }

    /**
     * The level of one chunk of little-endian 16-bit mono PCM, as the server
     * recorder produces it: the RMS of the samples in dBFS, normalised.
     */
    fun fromPcm16(chunk: ByteArray, length: Int = chunk.size): Float {
        val samples = length / 2
        if (samples <= 0) return 0f
        var sum = 0.0
        var index = 0
        while (index + 1 < length) {
            val sample = ((chunk[index + 1].toInt() shl 8) or (chunk[index].toInt() and 0xFF)).toShort().toDouble()
            sum += sample * sample
            index += 2
        }
        val rms = sqrt(sum / samples) / Short.MAX_VALUE
        val dbfs = 20.0 * log10(max(rms, 1e-6))
        return normalizeDbfs(dbfs.toFloat())
    }

    /** The flat strip before anything was heard. */
    fun idle(): List<Float> = List(BARS) { 0f }

    /** The last [BARS] levels, newest last; a fresh one is all dots. */
    class Waveform(private val size: Int = BARS) {
        private var levels: List<Float> = List(size) { 0f }

        val bars: List<Float> get() = levels

        /** Appends one frame and returns the bars to draw. */
        fun push(level: Float): List<Float> {
            levels = levels.drop(1) + level.coerceIn(0f, 1f)
            return levels
        }
    }
}

/**
 * Smooths the raw readings into the level one bar shows.
 *
 * Readings arrive on whatever thread the audio source uses and at its own
 * pace; [tick] runs on the strip's clock. A louder reading is taken at once
 * and a quieter one is approached by an exponential release — [RELEASE] of
 * the level is left after each tick — so speech reads as a burst rather than
 * a flicker and silence settles back to a dot instead of dropping to it.
 * Under [IDLE_THRESHOLD] the bar is the dot outright, never a sliver.
 */
class AudioLevelMeter {

    @Volatile
    private var latest = 0f
    private var current = 0f

    /** The smoothed level as of the last [tick]. */
    val level: Float get() = current

    fun feedDb(rmsDb: Float) = feed(DictationLevels.normalize(rmsDb))

    fun feed(level: Float) {
        latest = if (level.isNaN()) 0f else level.coerceIn(0f, 1f)
    }

    /** Advances one frame and returns the level to draw. */
    fun tick(): Float {
        val input = latest
        current = if (input > current) input else max(input, current * RELEASE)
        if (current < IDLE_THRESHOLD && input < IDLE_THRESHOLD) current = 0f
        return current
    }

    fun reset() {
        latest = 0f
        current = 0f
    }

    companion object {
        /** What is left of a level after one quiet tick; ~0.65 s from full to the dot. */
        const val RELEASE = 0.8f

        /** Below this a bar is drawn as the idle dot rather than a bar. */
        const val IDLE_THRESHOLD = 0.06f
    }
}
