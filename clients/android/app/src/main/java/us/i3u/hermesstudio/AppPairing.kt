package us.i3u.hermesstudio

import org.json.JSONObject

/**
 * The QR code shown by Core Hub under Device connections → App → Direct
 * connection. It is plain JSON, so the phone can read it with any barcode
 * scanner and then exchange the one-time authorization code for an App token.
 */
data class AppConnectionPayload(
    val backendUrl: String,
    val authorizationCode: String,
    val machineId: String?,
    val connectionType: String,
    /** Epoch seconds; 0 when the server did not say. */
    val expiresAt: Long,
) {
    fun isExpired(nowSeconds: Long = System.currentTimeMillis() / 1000): Boolean =
        expiresAt > 0 && expiresAt <= nowSeconds

    companion object {
        const val TYPE = "hermes-studio.app-connection"

        /** Returns null for anything that is not a Core Hub App connection code. */
        fun parse(raw: String): AppConnectionPayload? {
            val json = runCatching { JSONObject(raw.trim()) }.getOrNull() ?: return null
            if (json.optString("type") != TYPE) return null
            val backendUrl = json.optString("backend_url").trim().trimEnd('/')
            val code = json.optString("authorization_code").trim()
            if (backendUrl.isBlank() || code.isBlank()) return null
            if (!backendUrl.startsWith("http://") && !backendUrl.startsWith("https://")) return null
            return AppConnectionPayload(
                backendUrl = backendUrl,
                authorizationCode = code,
                machineId = json.optString("machine_id").takeIf { it.isNotBlank() },
                connectionType = json.optString("connection_type").ifBlank { "lan" },
                expiresAt = json.optLong("expires_at", 0L),
            )
        }
    }
}

/** When a stored App token should be refreshed silently on launch. */
object AppTokenPolicy {
    const val REFRESH_WHEN_LESS_THAN_MS = 7L * 24 * 60 * 60 * 1000
    const val REFRESH_AFTER_MS = 24L * 60 * 60 * 1000

    fun shouldRefresh(expiresAtSeconds: Long, refreshedAtMillis: Long, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (expiresAtSeconds <= 0L) return false
        val remaining = expiresAtSeconds * 1000 - nowMillis
        if (remaining < REFRESH_WHEN_LESS_THAN_MS) return true
        return nowMillis - refreshedAtMillis > REFRESH_AFTER_MS
    }
}
