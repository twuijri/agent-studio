package us.i3u.hermesstudio

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The chunked App upload: pure chunk planning plus the HTTP sequence against a mock server. */
class AppUploadsTest {
    private lateinit var server: MockWebServer
    private lateinit var api: HermesApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = HermesApi(server.url("/").toString().trimEnd('/'), "saved-token")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `chunks cover the file exactly once at the server chunk size`() {
        val chunks = planUploadChunks(size = 600_000, maxChunkBytes = 262_144)
        assertEquals(listOf(UploadChunk(0, 262_144), UploadChunk(262_144, 262_144), UploadChunk(524_288, 75_712)), chunks)
        assertEquals(600_000L, chunks.sumOf { it.length.toLong() })
    }

    @Test
    fun `resuming from nextOffset skips what the server already has`() {
        assertEquals(listOf(UploadChunk(262_144, 100)), planUploadChunks(262_244, 262_144, fromOffset = 262_144))
        assertTrue(planUploadChunks(0, 262_144).isEmpty())
        assertTrue(planUploadChunks(10, 262_144, fromOffset = 10).isEmpty())
    }

    @Test
    fun `percent is whole and never above one hundred`() {
        assertEquals(0, uploadPercent(0, 100))
        assertEquals(33, uploadPercent(1, 3))
        assertEquals(100, uploadPercent(5, 3))
        assertEquals(100, uploadPercent(0, 0))
    }

    @Test
    fun `generated ids satisfy the server pattern`() {
        repeat(20) {
            val id = AppUploads.newId()
            assertEquals(32, id.length)
            assertTrue(id, AppUploads.isValidId(id))
        }
        assertFalse(AppUploads.isValidId("short"))
        assertFalse(AppUploads.isValidId("has space in it"))
    }

    @Test
    fun `open chunks and complete follow the app-uploads contract`() {
        server.enqueue(MockResponse().setBody("""{"id":"abcdefgh12345678","nextOffset":0,"maxChunkBytes":262144}"""))
        server.enqueue(MockResponse().setBody("""{"id":"abcdefgh12345678","nextOffset":4,"done":true}"""))
        server.enqueue(MockResponse().setBody("""{"files":[{"name":"note.txt","path":"/uploads/manager/note.txt"}]}"""))

        val session = api.openAppUpload("manager", "abcdefgh12345678", "note.txt", 4)
        assertEquals(262_144, session.maxChunkBytes)
        assertEquals(0L, session.nextOffset)
        val next = api.appendAppUploadChunk("manager", session.id, 0, byteArrayOf(1, 2, 3, 4))
        assertEquals(4L, next)
        val upload = api.completeAppUpload("manager", session.id, "text/plain", "note.txt")
        assertEquals(Upload("note.txt", "/uploads/manager/note.txt", "text/plain"), upload)

        val open = server.takeRequest()
        assertEquals("POST", open.method)
        assertEquals("/api/studio/app-uploads", open.path)
        assertEquals("Bearer saved-token", open.getHeader("Authorization"))
        assertEquals("manager", open.getHeader("X-Hermes-Profile"))
        val body = JSONObject(open.body.readUtf8())
        assertEquals("abcdefgh12345678", body.getString("id"))
        assertEquals("note.txt", body.getString("name"))
        assertEquals(4, body.getInt("size"))

        val chunk = server.takeRequest()
        assertEquals("PUT", chunk.method)
        assertEquals("/api/studio/app-uploads/abcdefgh12345678/chunks?offset=0", chunk.path)
        assertEquals("application/octet-stream", chunk.getHeader("Content-Type"))
        assertEquals(4L, chunk.bodySize)

        val complete = server.takeRequest()
        assertEquals("POST", complete.method)
        assertEquals("/api/studio/app-uploads/abcdefgh12345678/complete", complete.path)
    }

    @Test
    fun `abort deletes the upload`() {
        server.enqueue(MockResponse().setBody("""{"ok":true}"""))
        api.abortAppUpload("manager", "abcdefgh12345678")
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/studio/app-uploads/abcdefgh12345678", request.path)
    }

    @Test
    fun `push toggle posts pushEnabled to the session`() {
        server.enqueue(MockResponse().setBody("{}"))
        api.setSessionPushEnabled("session-1", true)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/studio/sessions/session-1/push-enabled", request.path)
        assertTrue(JSONObject(request.body.readUtf8()).getBoolean("pushEnabled"))
    }

    @Test
    fun `stream url keeps the token out of the query and in the headers`() {
        val url = api.streamUrl("device://phone-1/Movies/out.mp4", "out.mp4", "manager")
        assertTrue(url, url.contains("/api/studio/files/download?path=device%3A%2F%2Fphone-1%2FMovies%2Fout.mp4"))
        assertTrue(url, url.contains("name=out.mp4"))
        assertTrue(url, url.contains("profile=manager"))
        assertFalse(url, url.contains("token="))
        val headers = api.mediaHeaders("manager")
        assertEquals("Bearer saved-token", headers["Authorization"])
        assertEquals("manager", headers["X-Hermes-Profile"])
        assertNull(api.mediaHeaders(null)["X-Hermes-Profile"])
    }
}
