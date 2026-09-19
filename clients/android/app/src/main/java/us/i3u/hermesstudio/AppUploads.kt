package us.i3u.hermesstudio

import java.security.SecureRandom

/**
 * The chunked attachment upload used by the Core Hub apps
 * (`/api/studio/app-uploads`): open a session, PUT raw chunks of at most
 * [DEFAULT_CHUNK_BYTES] at increasing offsets, then complete it. The server
 * keeps an open upload for five minutes and accepts files up to 50 MB.
 */
object AppUploads {
    const val DEFAULT_CHUNK_BYTES = 256 * 1024
    const val MAX_BYTES = 50L * 1024 * 1024

    private const val ID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-"
    private val random = SecureRandom()

    /** A client-generated upload id: 32 characters from `[A-Za-z0-9_-]` (the server allows 8–128). */
    fun newId(length: Int = 32): String {
        require(length in 8..128)
        return buildString(length) { repeat(length) { append(ID_ALPHABET[random.nextInt(ID_ALPHABET.length)]) } }
    }

    fun isValidId(id: String): Boolean = id.length in 8..128 && id.all { it in ID_ALPHABET }
}

/** One PUT of the chunked upload. */
data class UploadChunk(val offset: Long, val length: Int)

/**
 * Splits [size] bytes into chunks no larger than [maxChunkBytes], starting at
 * [fromOffset] (the server's `nextOffset` when resuming). An empty file still
 * yields no chunk; completing it is enough.
 */
fun planUploadChunks(size: Long, maxChunkBytes: Int, fromOffset: Long = 0L): List<UploadChunk> {
    require(maxChunkBytes > 0) { "chunk size must be positive" }
    if (size <= fromOffset) return emptyList()
    val chunks = mutableListOf<UploadChunk>()
    var offset = fromOffset.coerceAtLeast(0)
    while (offset < size) {
        val length = minOf(maxChunkBytes.toLong(), size - offset).toInt()
        chunks += UploadChunk(offset, length)
        offset += length
    }
    return chunks
}

/** Whole-number percent for a progress chip; never above 100. */
fun uploadPercent(sent: Long, total: Long): Int =
    if (total <= 0) 100 else ((sent.coerceIn(0, total) * 100) / total).toInt()

/** What the composer shows for a file that is still going up. */
data class UploadProgress(
    val id: String,
    val name: String,
    val sent: Long,
    val total: Long,
) {
    val percent: Int get() = uploadPercent(sent, total)
}

data class AppUploadSession(val id: String, val nextOffset: Long, val maxChunkBytes: Int)
