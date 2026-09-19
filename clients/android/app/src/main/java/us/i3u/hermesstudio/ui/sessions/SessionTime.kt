package us.i3u.hermesstudio.ui.sessions

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The time shown at the end of a session row, exactly as the web sidebar does
 * it: `HH:mm` when the session was active today, otherwise a short date such
 * as "Sep 18". Accepts epoch seconds, epoch milliseconds or ISO 8601.
 */
fun formatStamp(raw: String?, nowMillis: Long = System.currentTimeMillis(), locale: Locale = Locale.getDefault()): String {
    if (raw.isNullOrBlank()) return ""
    val millis = sessionEpochMillis(raw) ?: return raw.take(10)
    val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    val sameDay = now.get(Calendar.ERA) == then.get(Calendar.ERA) &&
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val pattern = if (sameDay) "HH:mm" else "MMM d"
    return SimpleDateFormat(pattern, locale).format(Date(millis))
}

/** Epoch milliseconds for a numeric (s or ms) or ISO 8601 stamp; null when unreadable. */
fun sessionEpochMillis(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    raw.toLongOrNull()?.let { value ->
        return if (value < 100_000_000_000L) value * 1000 else value
    }
    raw.toDoubleOrNull()?.let { value ->
        return if (value < 100_000_000_000.0) (value * 1000).toLong() else value.toLong()
    }
    val iso = raw.trim()
    val candidates = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd",
    )
    for (pattern in candidates) {
        val parser = SimpleDateFormat(pattern, Locale.ROOT).apply {
            isLenient = false
            if (pattern.endsWith("'Z'")) timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        runCatching { parser.parse(iso) }.getOrNull()?.let { return it.time }
    }
    return null
}
