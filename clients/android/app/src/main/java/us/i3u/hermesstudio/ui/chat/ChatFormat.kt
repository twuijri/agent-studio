package us.i3u.hermesstudio.ui.chat

import java.util.Locale

/**
 * Pure formatting rules shared by the chat surfaces. They mirror the web
 * client (`ChatInput.vue`, `MessageItem.vue`) so the phone prints the same
 * numbers as the browser for the same data. Everything here is unit-tested.
 */

/** `formatTokens` from the web: ≥ 1e6 → "1.2M", ≥ 1e3 → "45.0k", else the integer. */
fun formatTokens(value: Long): String = when {
    value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format(Locale.US, "%.1fk", value / 1_000.0)
    else -> value.coerceAtLeast(0).toString()
}

/**
 * The context indicator text: `"{used} / {limit} · {remaining} {rest}"`, where
 * [remainingLabel] is "remaining" or "متبقٍ". The remainder never goes negative.
 */
fun contextIndicatorText(used: Long, limit: Long, remainingLabel: String): String {
    val rest = (limit - used).coerceAtLeast(0)
    return "${formatTokens(used)} / ${formatTokens(limit)} · $remainingLabel ${formatTokens(rest)}"
}

/** Share of the window in use, clamped to 0..1; zero when the limit is unknown. */
fun contextRatio(used: Long, limit: Long): Float =
    if (limit <= 0) 0f else (used.toDouble() / limit.toDouble()).toFloat().coerceIn(0f, 1f)

/**
 * The tool summary header names: up to three unique tool names joined by
 * " · ", then "+N" for the rest, exactly like the web's collapsed tool group.
 */
fun toolSummaryNames(names: List<String>, maxNames: Int = 3): String {
    val unique = names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (unique.isEmpty()) return ""
    val shown = unique.take(maxNames).joinToString(" · ")
    val more = unique.size - maxNames
    return if (more > 0) "$shown +$more" else shown
}

/** `formatDuration` from MessageItem.vue: "12s", "2m", "2m 5s". */
fun formatThinkingDuration(milliseconds: Long): String {
    val seconds = (milliseconds.coerceAtLeast(0) / 1000)
    if (seconds < 60) return "${seconds}s"
    val minutes = seconds / 60
    val rest = seconds % 60
    return if (rest == 0L) "${minutes}m" else "${minutes}m ${rest}s"
}

/** Characters of reasoning shown in the thinking header; whitespace runs count once. */
fun thinkingCharCount(reasoning: String?): Int =
    reasoning.orEmpty().trim().replace(Regex("\\s+"), " ").length

enum class ChatMediaKind { Video, Audio, Other }

private val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mov", "m4v")
private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "ogg", "m4a", "aac", "flac")

/** Whether a file linked from a message plays inline (video/audio) or is a download card. */
fun chatMediaKind(fileName: String): ChatMediaKind {
    val extension = fileName.substringAfterLast('.', "").lowercase(Locale.US)
    return when (extension) {
        in VIDEO_EXTENSIONS -> ChatMediaKind.Video
        in AUDIO_EXTENSIONS -> ChatMediaKind.Audio
        else -> ChatMediaKind.Other
    }
}

/** A file on a linked device: `device://<deviceId>/<absolute path>`. */
data class DeviceFileRef(val deviceId: String, val path: String)

/**
 * Splits a `device://` link into its device id and the path on that device;
 * null for ordinary server paths. The server also accepts `?device=<id>`, which
 * the download route resolves itself, so only the URI form is parsed here.
 */
fun parseDeviceFile(link: String): DeviceFileRef? {
    val trimmed = link.trim()
    if (!trimmed.startsWith("device://", ignoreCase = true)) return null
    val rest = trimmed.substring("device://".length)
    val slash = rest.indexOf('/')
    if (slash <= 0) return null
    val id = rest.substring(0, slash).trim()
    val path = rest.substring(slash)
    if (id.isBlank() || path.length < 2) return null
    return DeviceFileRef(id, path)
}

/** Whether a link inside assistant Markdown points at a downloadable file (server or device). */
fun isChatFileLink(target: String): Boolean =
    parseDeviceFile(target) != null || target.startsWith('/') || Regex("""^[A-Za-z]:[\\/]""").containsMatchIn(target)
