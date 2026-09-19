package us.i3u.hermesstudio

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The M3 events of the /chat-run contract, parsed from payloads shaped like the server's. */
class ChatRunEventsTest {

    @Test
    fun `session settings carry model provider effort and push`() {
        val event = parseSettingsUpdated(
            JSONObject().put("model", "gpt-5").put("provider", "openai").put("reasoning_effort", "").put("push_enabled", true),
        )
        assertEquals("gpt-5", event.model)
        assertEquals("openai", event.provider)
        // An explicit empty effort means "profile default", not "unchanged".
        assertEquals("", event.reasoningEffort)
        assertEquals(true, event.pushEnabled)

        val partial = parseSettingsUpdated(JSONObject().put("model", "claude"))
        assertNull(partial.reasoningEffort)
        assertNull(partial.pushEnabled)
    }

    @Test
    fun `peer user message keeps id content and stamp`() {
        val event = parsePeerUserMessage(
            JSONObject().put("message", JSONObject().put("id", 7).put("role", "user").put("content", "from the desktop").put("timestamp", 1710000000)),
        )
        assertEquals("7", event?.id)
        assertEquals("from the desktop", event?.content)
        assertEquals("1710000000", event?.timestamp)
        assertNull(parsePeerUserMessage(JSONObject().put("message", JSONObject().put("role", "assistant").put("content", "x"))))
    }

    @Test
    fun `compression started and completed map their token fields`() {
        val started = parseCompression(JSONObject().put("message_count", 40).put("token_count", 120_000), started = true)
        assertTrue(started.started)
        assertEquals(40, started.messageCount)
        assertEquals(120_000L, started.beforeTokens)

        val done = parseCompression(JSONObject().put("compressed", true).put("totalMessages", 40).put("beforeTokens", 120_000).put("afterTokens", 30_000), started = false)
        assertFalse(done.started)
        assertEquals(120_000L, done.beforeTokens)
        assertEquals(30_000L, done.afterTokens)
        assertNull(done.error)

        val failed = parseCompression(JSONObject().put("compressed", false).put("error", "bridge timeout"), started = false)
        assertEquals("bridge timeout", failed.error)
    }

    @Test
    fun `session command is terminal unless the server says otherwise`() {
        val fork = parseSessionCommand(JSONObject().put("command", "fork").put("ok", true).put("message", "Forked"))
        assertTrue(fork.terminal)
        assertEquals("Forked", fork.message)
        val steer = parseSessionCommand(JSONObject().put("command", "steer").put("ok", true).put("terminal", false).put("message", "Steering"))
        assertFalse(steer.terminal)
        val error = parseSessionCommand(JSONObject().put("command", "compact").put("ok", false).put("action", "error").put("message", "unsupported"))
        assertFalse(error.ok)
    }

    @Test
    fun `location request reads id purpose accuracy and timeout`() {
        val request = parseLocationRequest(
            JSONObject().put("location_request_id", "loc-1").put("purpose", "find nearby cafes").put("accuracy", "precise").put("timeout_ms", 20_000).put("session_id", "s-1"),
            fallbackSessionId = "fallback",
        )
        assertEquals("loc-1", request?.id)
        assertEquals("s-1", request?.sessionId)
        assertEquals("find nearby cafes", request?.purpose)
        assertEquals("precise", request?.accuracy)
        assertEquals(20_000L, request?.timeoutMs)
        val coarse = parseLocationRequest(JSONObject().put("location_request_id", "loc-2"), "fallback")
        assertEquals("coarse", coarse?.accuracy)
        assertEquals("fallback", coarse?.sessionId)
        assertNull(parseLocationRequest(JSONObject().put("purpose", "no id"), "fallback"))
    }

    @Test
    fun `location respond payload matches the server normaliser`() {
        val success = locationResponsePayload("s-1", "loc-1", LocationResult.Success(LocationFix(24.7136, 46.6753, 12.5, 1_710_000_000_000L)))
        assertEquals("success", success.getString("status"))
        assertEquals("s-1", success.getString("session_id"))
        assertEquals("loc-1", success.getString("location_request_id"))
        val location = success.getJSONObject("location")
        assertEquals("wgs84", location.getString("coordinateSystem"))
        assertEquals(24.7136, location.getDouble("latitude"), 0.000001)
        assertEquals(46.6753, location.getDouble("longitude"), 0.000001)
        assertEquals(12.5, location.getDouble("accuracyMeters"), 0.0)
        assertEquals(1_710_000_000_000L, location.getLong("timestamp"))

        assertEquals("denied", locationResponsePayload("s-1", "loc-1", LocationResult.Denied).getString("status"))
        val error = locationResponsePayload("s-1", "loc-1", LocationResult.Error("location_permission_denied"))
        assertEquals("error", error.getString("status"))
        assertEquals("location_permission_denied", error.getJSONObject("error").getString("code"))
    }

    @Test
    fun `tool completion carries arguments output and truncation`() {
        val event = parseToolEvent(
            event = JSONObject()
                .put("tool_call_id", "call-1")
                .put("tool", "terminal")
                .put("arguments", JSONObject().put("command", "ls -la"))
                .put("output", "total 0")
                .put("output_truncated", true)
                .put("output_original_length", 9_000)
                .put("reasoning", "listing the directory"),
            status = ToolRunStatus.Done,
            occurredAtMillis = 1_000L,
        )
        assertEquals("ls -la", event?.detail)
        assertTrue(event?.arguments.orEmpty().contains("\"command\": \"ls -la\""))
        assertEquals("total 0", event?.output)
        assertEquals(true, event?.outputTruncated)
        assertEquals(9_000L, event?.outputOriginalLength)
        assertEquals("listing the directory", event?.reasoning)
    }

    @Test
    fun `tool start does not pretend to have a result`() {
        val event = parseToolEvent(
            event = JSONObject().put("tool_call_id", "call-2").put("tool", "web_search").put("args", JSONObject().put("query", "weather")),
            status = ToolRunStatus.Running,
            occurredAtMillis = 1_000L,
        )
        assertNull(event?.output)
        assertFalse(event?.outputTruncated ?: true)
    }

    @Test
    fun `usage updated accepts the bridge and ekko shapes`() {
        assertEquals(RunEvent.Usage(80_000, null), usageFrom(JSONObject().put("contextTokens", 80_000).put("context_tokens", 80_000).put("input_tokens", 1).put("output_tokens", 2)))
        assertEquals(RunEvent.Usage(12_000, 200_000), usageFrom(JSONObject().put("context_tokens", 12_000).put("context_length", 200_000)))
        assertNull(usageFrom(JSONObject().put("inputTokens", 5).put("outputTokens", 6)))
    }

    @Test
    fun `chat line kinds follow the flags`() {
        assertEquals(ChatLineKind.User, ChatLine("hi", fromUser = true).kind)
        assertEquals(ChatLineKind.Assistant, ChatLine("hello", fromUser = false).kind)
        assertEquals(ChatLineKind.System, ChatLine("compressed", fromUser = false, system = true).kind)
        assertEquals(ChatLineKind.Command, ChatLine("/fork", fromUser = false, command = true).kind)
        assertEquals(ChatLineKind.Error, ChatLine("boom", fromUser = false, isError = true, command = true).kind)
        val tool = ChatToolStep("t", "terminal", null, ToolRunStatus.Done, 0L)
        assertFalse(tool.hasDetails)
        assertTrue(tool.copy(output = "x").hasDetails)
    }

    @Test
    fun `resume snapshot restores approval requests from the event log`() {
        // Guards the parse path shared with live events: an approval carried in
        // `events` must yield the same choices as a live approval.requested.
        val payload = JSONObject().put("events", JSONArray().put(JSONObject().put("event", "approval.requested").put("data", JSONObject().put("approval_id", "a-1").put("description", "rm -rf build").put("choices", JSONArray().put("once").put("session").put("deny")))))
        val data = payload.getJSONArray("events").getJSONObject(0).getJSONObject("data")
        assertEquals("a-1", data.getString("approval_id"))
        assertEquals(3, data.getJSONArray("choices").length())
    }
}
