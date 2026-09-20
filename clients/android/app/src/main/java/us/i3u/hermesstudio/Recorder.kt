package us.i3u.hermesstudio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Records 16 kHz mono 16-bit PCM straight from the microphone and hands back a
 * complete WAV file for `POST /api/studio/stt/transcribe`. Plain PCM WAV is the
 * one format every server-side STT provider accepts unchanged.
 */
class Recorder {

    private var record: AudioRecord? = null
    private var reader: Thread? = null
    private val pcm = ByteArrayOutputStream()
    @Volatile
    private var running = false

    val isRecording: Boolean get() = record != null

    /**
     * The level (0…1) of each chunk as it is captured, measured from the PCM
     * this recorder is already writing — the recording strip's waveform for a
     * server take, without opening the microphone a second time. Called on
     * the reader thread.
     */
    @Volatile
    var onLevel: ((Float) -> Unit)? = null

    /** Opens the microphone; throws with a reason when the device refuses. */
    @SuppressLint("MissingPermission") // RECORD_AUDIO is requested by the composer before this runs.
    fun start() {
        if (record != null) return
        val minimum = AudioRecord.getMinBufferSize(WavFormat.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minimum <= 0) throw IllegalStateException("16 kHz mono PCM capture is not supported on this device")
        val instance = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            WavFormat.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minimum, 8_192) * 2,
        )
        if (instance.state != AudioRecord.STATE_INITIALIZED) {
            instance.release()
            throw IllegalStateException("the microphone could not be opened")
        }
        pcm.reset()
        instance.startRecording()
        running = true
        record = instance
        reader = Thread({
            val chunk = ByteArray(4_096)
            while (running) {
                val read = instance.read(chunk, 0, chunk.size)
                if (read > 0) {
                    synchronized(pcm) { pcm.write(chunk, 0, read) }
                    onLevel?.invoke(DictationLevels.fromPcm16(chunk, read))
                } else if (read < 0) {
                    break
                }
            }
        }, "voice-recorder").also { it.start() }
    }

    /** Returns the WAV bytes, or null when the take was too short to hold speech. */
    fun stop(): ByteArray? {
        val instance = record ?: return null
        finish(instance)
        val samples = synchronized(pcm) { pcm.toByteArray() }
        if (samples.size < WavFormat.MIN_PCM_BYTES) return null
        return WavFormat.wrap(samples)
    }

    fun cancel() {
        val instance = record ?: return
        finish(instance)
        synchronized(pcm) { pcm.reset() }
    }

    private fun finish(instance: AudioRecord) {
        running = false
        reader?.join(1_000)
        reader = null
        runCatching { instance.stop() }
        instance.release()
        record = null
    }
}

/** The WAV container for the recorder, kept separate so the header is unit-testable. */
object WavFormat {
    const val SAMPLE_RATE = 16_000
    const val CHANNELS = 1
    const val BITS_PER_SAMPLE = 16

    /** About 300 ms of audio: anything shorter is a stray tap, not speech. */
    const val MIN_PCM_BYTES = SAMPLE_RATE * CHANNELS * (BITS_PER_SAMPLE / 8) * 3 / 10

    fun header(pcmLength: Int, sampleRate: Int = SAMPLE_RATE, channels: Int = CHANNELS, bitsPerSample: Int = BITS_PER_SAMPLE): ByteArray {
        val blockAlign = channels * bitsPerSample / 8
        val byteRate = sampleRate * blockAlign
        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray(Charsets.US_ASCII))
            .putInt(36 + pcmLength)
            .put("WAVE".toByteArray(Charsets.US_ASCII))
            .put("fmt ".toByteArray(Charsets.US_ASCII))
            .putInt(16)
            .putShort(1) // PCM
            .putShort(channels.toShort())
            .putInt(sampleRate)
            .putInt(byteRate)
            .putShort(blockAlign.toShort())
            .putShort(bitsPerSample.toShort())
            .put("data".toByteArray(Charsets.US_ASCII))
            .putInt(pcmLength)
            .array()
    }

    fun wrap(pcm: ByteArray): ByteArray = header(pcm.size) + pcm
}
