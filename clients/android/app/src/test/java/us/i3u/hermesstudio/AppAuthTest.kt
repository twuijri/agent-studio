package us.i3u.hermesstudio

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * The App pairing and token-refresh contract: /api/auth/app-login,
 * /api/auth/app-refresh, and the single retry a 401 is allowed to trigger.
 */
class AppAuthTest {
    private lateinit var server: MockWebServer
    private lateinit var api: HermesApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = HermesApi(server.url("/").toString().trimEnd('/'), "old-token")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `app login posts the device identity and parses the connection`() {
        enqueue(
            """{"token":"app-token","userId":3,"profiles":["manager","default"],"theme":{},"appConnection":{"id":12,"device_code":"dev-1","device_name":"Pixel 9","device_brand":"google","device_model":"Pixel 9","connection_type":"lan","cloud_user_id":0,"token_expires_at":1800000000}}""",
        )

        val result = api.appLogin("code-1", "dev-1", "Pixel 9", "google", "Pixel 9")

        assertEquals("app-token", result.token)
        assertEquals(3, result.userId)
        assertEquals(listOf("manager", "default"), result.profiles)
        assertEquals(12, result.connection.id)
        assertEquals(1800000000L, result.connection.tokenExpiresAt)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/auth/app-login", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("code-1", body.getString("authorization_code"))
        assertEquals("dev-1", body.getString("device_code"))
        assertEquals("Pixel 9", body.getString("device_name"))
        assertEquals("google", body.getString("device_brand"))
        assertEquals("Pixel 9", body.getString("device_model"))
    }

    @Test
    fun `app login error statuses are preserved for the message mapping`() {
        for (status in listOf(400, 401, 403, 409, 410)) {
            enqueue("""{"error":"declined"}""", status)
            val failure = assertThrows(HermesException::class.java) {
                api.appLogin("code", "dev", "name", "brand", "model")
            }
            assertEquals(status, failure.statusCode)
            server.takeRequest()
        }
    }

    @Test
    fun `app refresh sends the current token with an empty body and parses the expiry`() {
        enqueue(
            """{"token":"new-token","token_expires_at":1800100000,"appConnection":{"id":12,"device_code":"dev-1","connection_type":"lan","token_expires_at":1800100000}}""",
        )

        val refreshed = api.appRefresh()

        assertEquals("new-token", refreshed.token)
        assertEquals(1800100000L, refreshed.expiresAt)
        assertEquals(12, refreshed.connection.id)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/auth/app-refresh", request.path)
        assertEquals("Bearer old-token", request.getHeader("Authorization"))
        assertEquals("{}", request.body.readUtf8())
    }

    @Test
    fun `a 401 asks for one refresh and retries the request with the new token`() {
        var refreshCalls = 0
        api.onUnauthorized = {
            refreshCalls += 1
            // The view model calls appRefresh here; the API must send it with the old bearer.
            api.appRefresh().token
        }
        enqueue("""{"error":"Unauthorized"}""", 401)
        enqueue("""{"token":"new-token","token_expires_at":1800100000,"appConnection":{"id":1}}""")
        enqueue("""{"user":{"id":1,"username":"owner"}}""")

        assertEquals("owner", api.verifyToken())

        assertEquals(1, refreshCalls)
        val first = server.takeRequest()
        assertEquals("/api/auth/me", first.path)
        assertEquals("Bearer old-token", first.getHeader("Authorization"))
        val refresh = server.takeRequest()
        assertEquals("/api/auth/app-refresh", refresh.path)
        assertEquals("Bearer old-token", refresh.getHeader("Authorization"))
        val retry = server.takeRequest()
        assertEquals("/api/auth/me", retry.path)
        assertEquals("Bearer new-token", retry.getHeader("Authorization"))
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `a refresh that is itself rejected reports the original 401 without looping`() {
        var refreshCalls = 0
        api.onUnauthorized = {
            refreshCalls += 1
            try {
                api.appRefresh().token
            } catch (failure: HermesException) {
                if (failure.statusCode == 401) null else throw failure
            }
        }
        enqueue("""{"error":"Unauthorized"}""", 401)
        enqueue("""{"error":"App token revoked"}""", 401)

        val failure = assertThrows(HermesException::class.java) { api.verifyToken() }

        assertEquals(401, failure.statusCode)
        assertEquals(1, refreshCalls)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `without a refresh hook a 401 is reported as before`() {
        api.onUnauthorized = null
        enqueue("""{"error":"Unauthorized"}""", 401)

        val failure = assertThrows(HermesException::class.java) { api.verifyToken() }

        assertEquals(401, failure.statusCode)
        assertEquals(1, server.requestCount)
        assertNull(failure.code)
    }

    @Test
    fun `multipart uploads are replayed after a refresh`() {
        api.onUnauthorized = { "new-token" }
        enqueue("""{"error":"Unauthorized"}""", 401)
        enqueue("""{"files":[{"name":"a.txt","path":"/uploads/a.txt"}]}""")

        val upload = api.upload("manager", byteArrayOf(1, 2, 3), "a.txt", "text/plain")

        assertEquals("/uploads/a.txt", upload.path)
        server.takeRequest()
        val retry = server.takeRequest()
        assertEquals("Bearer new-token", retry.getHeader("Authorization"))
        assertEquals(true, retry.body.readUtf8().contains("filename=\"a.txt\""))
    }

    private fun enqueue(body: String, code: Int = 200) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body),
        )
    }
}
