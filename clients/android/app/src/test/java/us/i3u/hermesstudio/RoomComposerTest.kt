package us.i3u.hermesstudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The room writes with the chat's composer, not with a box of its own.
 *
 * The reported bug was a room with a bare "+", an outlined field and an arrow:
 * no dictation, no attachment sheet, none of the card. None of that is visible
 * in a unit test run, so what is checked here is the structure that caused it
 * — that there is one composer and both screens call it, that the room still
 * carries what only a room has, and that the mention rule the room routes on
 * is the server's own.
 */
class RoomComposerTest {

    private fun source(name: String) = File("src/main/java/us/i3u/hermesstudio/$name").readText()

    private val composer = source("ui/chat/Composer.kt")
    private val room = source("ui/groups/RoomScreen.kt")
    private val conversation = source("ui/chat/ConversationScreen.kt")

    // ── one composer ─────────────────────────────────────────────────────

    @Test
    fun `both screens draw the same composer`() {
        assertTrue("the shared composer lives in ui/chat/Composer.kt", composer.contains("internal fun StudioComposer("))
        assertTrue("the chat composer delegates to it", composer.contains("internal fun Composer(") && composer.contains("    StudioComposer("))
        assertTrue("the conversation screen still calls Composer", conversation.contains("Composer("))
        assertTrue("the room calls the shared composer", room.contains("StudioComposer("))
    }

    @Test
    fun `the room no longer hand-rolls a message box`() {
        listOf("OutlinedTextField", "OutlinedTextFieldDefaults", "Icons.AutoMirrored.Filled.Send", "Icons.Filled.Add")
            .forEach { gone -> assertFalse("the room still builds its own $gone", room.contains(gone)) }
        assertFalse("the room no longer owns an attachment picker", room.contains("ActivityResultContracts.GetContent()"))
    }

    @Test
    fun `everything the chat composer carries is carried once`() {
        // The card, the sheet, dictation and the direction rule are written in
        // the shared composer, so a room gets them by calling it.
        listOf(
            "CoreHubTokens.Radius.composer",
            "AttachmentSheet(",
            "MicButton(",
            "SpeechLanguageSheet(",
            "DictationHintPopup(",
            "ContentDirectionBox(field.text)",
            "ComposerActionButton(config, draft, onSend)",
        ).forEach { piece -> assertTrue("the shared composer is missing $piece", composer.contains(piece)) }
        assertFalse("dictation is not re-implemented in the room", room.contains("startVoiceInput"))
    }

    // ── what dictation types into ────────────────────────────────────────

    @Test
    fun `a transcript lands in the field of whichever screen is open`() {
        // The take edits the composer's own TextFieldValue and reports the
        // result through onDraftChange, so the room's draft receives it the
        // same way the chat's does.
        assertTrue(composer.contains("LaunchedEffect(state.voiceSegment)"))
        assertTrue(composer.contains("applyVoiceSegment(field.text, anchor, voiceLength, segment.text, segment.kind)"))
        assertTrue("the edit is published to the screen's draft", composer.contains("onDraftChange(edit.text)"))
        assertTrue("the room owns that draft", room.contains("onDraftChange = { draft = it }"))
        assertTrue("and the mic long-press still opens the language sheet", composer.contains("viewModel.noteDictationLongPress()"))
    }

    // ── what only a room has ─────────────────────────────────────────────

    @Test
    fun `the room keeps its mention chip and its hint`() {
        assertTrue("the room's agents fill the @ chip", room.contains("mentionTargets = room?.agents?.map { it.name }.orEmpty()"))
        assertTrue(room.contains("allMention = ComposerAllMention("))
        assertTrue("the hint that says who answers", room.contains("onHint = R.string.room_mention_all_on"))
        assertTrue(room.contains("offHint = R.string.room_mention_all_off"))
        assertTrue("the chip is drawn by the shared composer", composer.contains("private fun AllMentionRow("))
        assertTrue("and the \"@\" chip only appears where there is someone to mention", composer.contains("if (config.mentionTargets.isNotEmpty())"))
    }

    @Test
    fun `the room's own actions still fire`() {
        listOf(
            "onAttach = viewModel::attachToRoom",
            "onCancelUpload = viewModel::cancelRoomUpload",
            "onRemoveAttachment = viewModel::removeRoomAttachment",
            "viewModel.postToRoom(draft, GroupMentions.mentionsAll(draft))",
            "viewModel.interruptRoomAgent(it.agentName)",
            "viewModel.roomTyping(true)",
        ).forEach { call -> assertTrue("the room no longer calls $call", room.contains(call)) }
        assertTrue("the per-agent interrupt strip stays", room.contains("ActivityStrip(room, onInterrupt ="))
        assertTrue("and the composer can stop whoever is running", room.contains("running = room?.busyAgents?.isNotEmpty() == true"))
        assertTrue("without taking the field away", room.contains("blocksSend = false"))
    }

    @Test
    fun `a room counts tokens instead of a context window`() {
        assertTrue(room.contains("counter = ComposerCounter.Tokens(room?.room?.totalTokens ?: 0L)"))
        assertTrue("the chat keeps its context meter", composer.contains("counter = ComposerCounter.Context"))
    }

    @Test
    fun `the session settings are not faked in a room`() {
        // A room's agents each carry their own model and reasoning effort, and
        // push is a property of one session. The room's settings sheet owns
        // the first two; none of the three is offered by its composer.
        assertTrue("the chat has the session pickers", composer.contains("sessionPickers = true"))
        assertTrue("and the session push row", composer.contains("sessionPush = true"))
        assertFalse("the room must not claim a session model or effort", room.contains("sessionPickers ="))
        assertFalse("nor a session push setting", room.contains("sessionPush ="))
        assertTrue("both are gated in the shared composer", composer.contains("if (config.sessionPickers) {"))
        assertTrue(composer.contains("if (config.sessionPush) {"))
    }

    // ── the mention rule, which is the server's ──────────────────────────

    @Test
    fun `a mention is only a mention on the server's boundaries`() {
        assertTrue(GroupMentions.mentions("hello @ada", "ada"))
        assertTrue(GroupMentions.mentions("@Ada please look", "ada"))
        assertTrue("punctuation closes a mention", GroupMentions.mentions("@ada, look", "ada"))
        assertFalse("a longer name is not a mention", GroupMentions.mentions("@adamant", "ada"))
        assertFalse("an address is not a mention", GroupMentions.mentions("mail me@ada.dev", "ada"))
        assertFalse(GroupMentions.mentions("ada", "ada"))
    }

    @Test
    fun `the @all chip puts the token the room routes on into the draft`() {
        // The server refuses a structured mention the text does not carry, so
        // the chip cannot be a private flag: it writes @all, and clicking it
        // again takes it back out.
        val on = GroupMentions.toggleAll("ship it", caret = 7)
        assertEquals("@all ship it", on.text)
        assertEquals("the caret moves with the text", 12, on.caret)
        assertTrue(GroupMentions.mentionsAll(on.text))

        val off = GroupMentions.toggleAll(on.text, on.caret)
        assertEquals("ship it", off.text)
        assertFalse(GroupMentions.mentionsAll(off.text))
    }

    @Test
    fun `inserting an agent mention never repeats one`() {
        val first = GroupMentions.insert("look at this", caret = 0, name = "Ada")
        assertEquals("@Ada look at this", first.text)
        val again = GroupMentions.insert(first.text, first.caret, "Ada")
        assertEquals("a name already addressed is left alone", first.text, again.text)
    }

    @Test
    fun `a quoted message addresses nobody`() {
        val quoted = "<quoted_message>@all ship it</quoted_message> what do you think?"
        assertFalse(GroupMentions.mentionsAll(quoted))
    }

    // ── strings ──────────────────────────────────────────────────────────

    @Test
    fun `every new line exists in both languages`() {
        val keys = listOf("composer_mention", "composer_mention_title", "room_mention_all_on", "room_mention_all_off", "room_hint")
        val english = File("src/main/res/values/strings.xml").readText()
        val arabic = File("src/main/res/values-ar/strings.xml").readText()
        keys.forEach { key ->
            assertTrue("values/strings.xml is missing $key", english.contains("name=\"$key\""))
            assertTrue("values-ar/strings.xml is missing $key", arabic.contains("name=\"$key\""))
        }
    }

    @Test
    fun `the composer is written in tokens, never in literal colours`() {
        assertFalse("no hardcoded colour in the composer", Regex("""Color\(0x[0-9A-Fa-f]{8}\)""").containsMatchIn(composer))
        assertFalse("nor in the room screen", Regex("""Color\(0x[0-9A-Fa-f]{8}\)""").containsMatchIn(room))
        assertTrue("the @all chip uses the pill radius", composer.contains("RoundedCornerShape(CoreHubTokens.Radius.pill)"))
    }
}
