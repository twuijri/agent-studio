package us.i3u.hermesstudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** The WAV container sent to the server and the live text insertion in the composer. */
class VoiceInputTest {

    @Test
    fun `wav header describes 16 kHz mono 16-bit PCM`() {
        val pcm = ByteArray(32_000) { (it % 7).toByte() }
        val wav = WavFormat.wrap(pcm)
        val buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(44 + pcm.size, wav.size)
        assertEquals("RIFF", ascii(wav, 0))
        assertEquals(36 + pcm.size, buffer.getInt(4))
        assertEquals("WAVE", ascii(wav, 8))
        assertEquals("fmt ", ascii(wav, 12))
        assertEquals(16, buffer.getInt(16))
        assertEquals(1, buffer.getShort(20).toInt())
        assertEquals(1, buffer.getShort(22).toInt())
        assertEquals(16_000, buffer.getInt(24))
        assertEquals(32_000, buffer.getInt(28))
        assertEquals(2, buffer.getShort(32).toInt())
        assertEquals(16, buffer.getShort(34).toInt())
        assertEquals("data", ascii(wav, 36))
        assertEquals(pcm.size, buffer.getInt(40))
        assertEquals(pcm.toList(), wav.copyOfRange(44, wav.size).toList())
    }

    @Test
    fun `a partial segment replaces the previous partial at the caret`() {
        val first = applyVoiceSegment("Hello", anchor = 5, previousLength = 0, segment = "wor", kind = VoiceSegmentKind.Partial)
        assertEquals("Hello wor", first.text)
        assertEquals(9, first.caret)
        assertEquals(4, first.segmentLength)

        val second = applyVoiceSegment(first.text, anchor = 5, previousLength = first.segmentLength, segment = "world today", kind = VoiceSegmentKind.Partial)
        assertEquals("Hello world today", second.text)
        assertEquals(17, second.caret)
    }

    @Test
    fun `a final segment stays and is separated from the text after the caret`() {
        val edit = applyVoiceSegment("Hello|there", anchor = 5, previousLength = 1, segment = "dear", kind = VoiceSegmentKind.Final)
        assertEquals("Hello dear there", edit.text)
        assertEquals(10, edit.caret)
        assertEquals(6, edit.segmentLength)
    }

    @Test
    fun `discarding removes the interim text and blank results insert nothing`() {
        val discarded = applyVoiceSegment("Hello wor", anchor = 5, previousLength = 4, segment = "", kind = VoiceSegmentKind.Discard)
        assertEquals("Hello", discarded.text)
        assertEquals(5, discarded.caret)

        val blank = applyVoiceSegment("", anchor = 0, previousLength = 0, segment = "   ", kind = VoiceSegmentKind.Final)
        assertEquals("", blank.text)
        assertEquals(0, blank.segmentLength)
    }

    @Test
    fun `an out of range anchor is clamped instead of throwing`() {
        val edit = applyVoiceSegment("ab", anchor = 10, previousLength = 3, segment = "c", kind = VoiceSegmentKind.Partial)
        assertEquals("ab c", edit.text)
        assertNull(runCatching { applyVoiceSegment("", -1, 5, "x", VoiceSegmentKind.Partial) }.exceptionOrNull())
    }

    private fun ascii(bytes: ByteArray, offset: Int) = String(bytes, offset, 4, Charsets.US_ASCII)
}
