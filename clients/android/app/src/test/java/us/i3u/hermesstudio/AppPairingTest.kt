package us.i3u.hermesstudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The QR payload Core Hub renders and the launch-time refresh policy. */
class AppPairingTest {

    @Test
    fun `a Core Hub direct connection code is parsed`() {
        val payload = AppConnectionPayload.parse(
            """{"type":"hermes-studio.app-connection","version":1,"connection_type":"lan","backend_url":"http://192.168.1.20:3000/","machine_id":"hwui_abc","authorization_code":"c29tZS1jb2Rl","expires_at":1800000000}""",
        )

        requireNotNull(payload)
        assertEquals("http://192.168.1.20:3000", payload.backendUrl)
        assertEquals("c29tZS1jb2Rl", payload.authorizationCode)
        assertEquals("hwui_abc", payload.machineId)
        assertEquals("lan", payload.connectionType)
        assertFalse(payload.isExpired(nowSeconds = 1799999999))
        assertTrue(payload.isExpired(nowSeconds = 1800000000))
    }

    @Test
    fun `foreign or incomplete codes are rejected`() {
        assertNull(AppConnectionPayload.parse("https://example.test/not-json"))
        assertNull(AppConnectionPayload.parse("""{"type":"something-else","backend_url":"http://h","authorization_code":"x"}"""))
        assertNull(AppConnectionPayload.parse("""{"type":"hermes-studio.app-connection","backend_url":"","authorization_code":"x"}"""))
        assertNull(AppConnectionPayload.parse("""{"type":"hermes-studio.app-connection","backend_url":"ftp://h","authorization_code":"x"}"""))
    }

    @Test
    fun `refresh happens under a week before expiry or a day after the last refresh`() {
        val now = 1_800_000_000_000L
        val day = 24L * 60 * 60 * 1000
        val fresh = now - 2 * 60 * 60 * 1000
        val farExpiry = (now + 30 * day) / 1000

        assertFalse(AppTokenPolicy.shouldRefresh(farExpiry, fresh, now))
        assertTrue(AppTokenPolicy.shouldRefresh((now + 6 * day) / 1000, fresh, now))
        assertTrue(AppTokenPolicy.shouldRefresh(farExpiry, now - 25 * 60 * 60 * 1000, now))
        // A password login stores no expiry and is never refreshed.
        assertFalse(AppTokenPolicy.shouldRefresh(0L, 0L, now))
    }
}
