package us.i3u.hermesstudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import us.i3u.hermesstudio.ui.chat.ChatMediaKind
import us.i3u.hermesstudio.ui.chat.DeviceFileRef
import us.i3u.hermesstudio.ui.chat.chatMediaKind
import us.i3u.hermesstudio.ui.chat.contextIndicatorText
import us.i3u.hermesstudio.ui.chat.contextRatio
import us.i3u.hermesstudio.ui.chat.formatThinkingDuration
import us.i3u.hermesstudio.ui.chat.formatTokens
import us.i3u.hermesstudio.ui.chat.parseDeviceFile
import us.i3u.hermesstudio.ui.chat.thinkingCharCount
import us.i3u.hermesstudio.ui.chat.toolSummaryNames

/** The formatters must print exactly what the web client prints for the same data. */
class ChatFormatTest {

    @Test
    fun `formatTokens matches the web formatter`() {
        assertEquals("0", formatTokens(0))
        assertEquals("999", formatTokens(999))
        assertEquals("1.0k", formatTokens(1_000))
        assertEquals("45.0k", formatTokens(45_000))
        assertEquals("256.0k", formatTokens(256_000))
        assertEquals("1.2M", formatTokens(1_234_567))
    }

    @Test
    fun `context indicator reads used slash limit and the remainder`() {
        assertEquals("45.0k / 256.0k · remaining 211.0k", contextIndicatorText(45_000, 256_000, "remaining"))
        assertEquals("45.0k / 256.0k · متبقٍ 211.0k", contextIndicatorText(45_000, 256_000, "متبقٍ"))
        // Over budget never prints a negative remainder.
        assertEquals("300.0k / 256.0k · remaining 0", contextIndicatorText(300_000, 256_000, "remaining"))
    }

    @Test
    fun `context ratio is clamped and zero without a limit`() {
        assertEquals(0f, contextRatio(10, 0), 0f)
        assertEquals(0.5f, contextRatio(50, 100), 0.0001f)
        assertEquals(1f, contextRatio(500, 100), 0f)
        assertTrue(contextRatio(81, 100) > 0.80f)
    }

    @Test
    fun `tool summary shows three unique names then a plus count`() {
        assertEquals("", toolSummaryNames(emptyList()))
        assertEquals("read_file", toolSummaryNames(listOf("read_file", "read_file")))
        assertEquals("terminal · read_file · web_search", toolSummaryNames(listOf("terminal", "read_file", "terminal", "web_search")))
        assertEquals("a · b · c +2", toolSummaryNames(listOf("a", "b", "c", "d", "e", "d")))
    }

    @Test
    fun `thinking duration formats like MessageItem`() {
        assertEquals("0s", formatThinkingDuration(400))
        assertEquals("12s", formatThinkingDuration(12_900))
        assertEquals("2m", formatThinkingDuration(120_000))
        assertEquals("2m 5s", formatThinkingDuration(125_000))
    }

    @Test
    fun `thinking char count collapses whitespace`() {
        assertEquals(0, thinkingCharCount(null))
        assertEquals(0, thinkingCharCount("   "))
        assertEquals(11, thinkingCharCount("  hello \n\n  world  "))
    }

    @Test
    fun `media kind follows the extension`() {
        assertEquals(ChatMediaKind.Video, chatMediaKind("clip.MP4"))
        assertEquals(ChatMediaKind.Video, chatMediaKind("clip.webm"))
        assertEquals(ChatMediaKind.Video, chatMediaKind("clip.mov"))
        assertEquals(ChatMediaKind.Audio, chatMediaKind("note.m4a"))
        assertEquals(ChatMediaKind.Audio, chatMediaKind("note.flac"))
        assertEquals(ChatMediaKind.Other, chatMediaKind("report.pdf"))
        assertEquals(ChatMediaKind.Other, chatMediaKind("noext"))
    }

    @Test
    fun `device links split into id and path`() {
        assertEquals(DeviceFileRef("dev-42", "/Users/me/Movies/out.mp4"), parseDeviceFile("device://dev-42/Users/me/Movies/out.mp4"))
        assertNull(parseDeviceFile("/srv/files/out.mp4"))
        assertNull(parseDeviceFile("device://"))
        assertNull(parseDeviceFile("device://only-id"))
    }

    @Test
    fun `device links in assistant markdown become file cards`() {
        val parsed = parseChatMessage("Done. [Rendered video](device://phone-1/storage/emulated/0/Movies/out.mp4)")
        assertEquals(1, parsed.files.size)
        assertEquals("device://phone-1/storage/emulated/0/Movies/out.mp4", parsed.files.single().path)
        assertEquals("out.mp4", parsed.files.single().fileName)
        assertEquals("Done.", parsed.text)
    }

    @Test
    fun `speech text drops markdown scaffolding and file links`() {
        val spoken = plainSpeechText("# Title\n\n**Bold** and `code` here.\n\n```\nignored\n```\n\n- item one\n\n[Report](/srv/out/report.pdf)")
        assertTrue(spoken, spoken.startsWith("Title"))
        assertTrue(spoken, spoken.contains("Bold and code here."))
        assertTrue(spoken, spoken.contains("item one"))
        listOf("**", "`", "#", "ignored", "/srv", "- item").forEach { token -> assertFalse("$token in $spoken", spoken.contains(token)) }
    }
}
