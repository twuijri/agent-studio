package us.i3u.hermesstudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The recording strip's waveform is arithmetic on the recognizer's own dB
 * readings: normalised to 0…1, taken at once on the way up, released
 * exponentially on the way down, kept as a short history. The numbers are
 * shared with the iOS client (release 0.8 per 50 ms tick, idle under 0.06,
 * 36 bars), so each rule is checked here rather than by speaking into a phone.
 */
class DictationLevelsTest {

    @Test
    fun `the recognizer's dB range maps onto 0 to 1 and is clamped outside it`() {
        assertEquals(0f, DictationLevels.normalize(DictationLevels.MIN_DB), 1e-6f)
        assertEquals(1f, DictationLevels.normalize(DictationLevels.MAX_DB), 1e-6f)
        assertEquals(0.5f, DictationLevels.normalize(4f), 1e-6f)
        assertEquals("quieter than silence is still silence", 0f, DictationLevels.normalize(-30f), 1e-6f)
        assertEquals("louder than the ceiling is the ceiling", 1f, DictationLevels.normalize(40f), 1e-6f)
        assertEquals("NaN never reaches the strip", 0f, DictationLevels.normalize(Float.NaN), 1e-6f)
    }

    @Test
    fun `a louder reading is taken at once`() {
        val meter = AudioLevelMeter()
        meter.feedDb(DictationLevels.MAX_DB)
        assertEquals("instant attack", 1f, meter.tick(), 1e-6f)
        meter.feed(0.3f)
        repeat(10) { meter.tick() }
        meter.feed(0.7f)
        assertEquals("a rise from any level is instant too", 0.7f, meter.tick(), 1e-6f)
    }

    @Test
    fun `a falling level releases exponentially and settles on a dot`() {
        val meter = AudioLevelMeter()
        meter.feed(1f)
        meter.tick()
        meter.feed(0f)
        val trail = List(40) { meter.tick() }
        // Every frame keeps a fixed share of the one before: a fade, not a
        // drop — until the share would fall under the idle threshold, where
        // the bar becomes the dot outright instead of a sliver that never ends.
        val fading = trail.zipWithNext().filter { (before, _) -> before * AudioLevelMeter.RELEASE >= AudioLevelMeter.IDLE_THRESHOLD }
        assertTrue("the fade has several frames, not one: $trail", fading.size >= 8)
        fading.forEach { (before, after) ->
            assertEquals("$before → $after", before * AudioLevelMeter.RELEASE, after, 1e-5f)
        }
        assertEquals(0.8f, trail.first(), 1e-6f)
        assertTrue("no frame goes negative", trail.all { it >= 0f })
        assertEquals("the tail is exactly idle, so the bar is drawn as a dot", 0f, trail.last(), 0f)
        // 0.8^13 ≈ 0.055 < 0.06: about 0.65 s from full to the dot at 20 Hz —
        // long enough to see, short enough that the strip does not lag the voice.
        val framesToIdle = trail.indexOfFirst { it == 0f } + 1
        assertEquals("took $framesToIdle frames", 13, framesToIdle)
    }

    @Test
    fun `a quieter reading never pulls the level below the reading itself`() {
        val meter = AudioLevelMeter()
        meter.feed(1f)
        meter.tick()
        meter.feed(0.5f)
        val trail = List(30) { meter.tick() }
        assertTrue("releases toward the reading, not past it: $trail", trail.all { it >= 0.5f })
        assertEquals(0.5f, trail.last(), 1e-3f)
    }

    @Test
    fun `the meter never exceeds 1 and treats bad input as silence`() {
        val meter = AudioLevelMeter()
        meter.feed(5f)
        assertEquals(1f, meter.tick(), 0f)
        meter.feed(Float.NaN)
        repeat(40) { meter.tick() }
        assertEquals(0f, meter.level, 0f)
        assertEquals(0.8f, AudioLevelMeter.RELEASE, 0f)
        assertEquals(0.06f, AudioLevelMeter.IDLE_THRESHOLD, 0f)
    }

    @Test
    fun `the waveform keeps the last thirty-six frames, newest last`() {
        val waveform = DictationLevels.Waveform()
        assertEquals(36, DictationLevels.BARS)
        assertEquals(DictationLevels.BARS, waveform.bars.size)
        assertTrue("a fresh strip is all dots", waveform.bars.all { it == 0f })
        waveform.push(0.3f)
        val bars = waveform.push(0.9f)
        assertEquals(DictationLevels.BARS, bars.size)
        assertEquals(0.9f, bars.last(), 1e-6f)
        assertEquals(0.3f, bars[bars.size - 2], 1e-6f)
        repeat(DictationLevels.BARS) { waveform.push(0f) }
        assertTrue("the oldest frames fall off the far end", waveform.bars.all { it == 0f })
    }

    @Test
    fun `frames arrive at about twenty hertz`() {
        assertEquals(50L, DictationLevels.TICK_MS)
        assertEquals(DictationLevels.BARS, DictationLevels.idle().size)
    }

    @Test
    fun `a server take is measured from the PCM the recorder already has`() {
        val silence = ByteArray(1_600)
        assertEquals(0f, DictationLevels.fromPcm16(silence), 0f)

        fun square(amplitude: Int): ByteArray {
            val bytes = ByteArray(1_600)
            for (index in bytes.indices step 2) {
                val sample = if ((index / 2) % 2 == 0) amplitude else -amplitude
                bytes[index] = (sample and 0xFF).toByte()
                bytes[index + 1] = ((sample shr 8) and 0xFF).toByte()
            }
            return bytes
        }
        assertEquals("full scale is the ceiling", 1f, DictationLevels.fromPcm16(square(Short.MAX_VALUE.toInt())), 1e-3f)
        val quiet = DictationLevels.fromPcm16(square(300))
        val loud = DictationLevels.fromPcm16(square(6_000))
        assertTrue("quiet $quiet < loud $loud", quiet in 0f..1f && loud in 0f..1f && quiet < loud)
        assertEquals("an empty read is silence, not an exception", 0f, DictationLevels.fromPcm16(ByteArray(0)), 0f)
    }
}
