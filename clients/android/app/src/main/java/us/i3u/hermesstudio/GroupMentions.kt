package us.i3u.hermesstudio

/**
 * Who a room message is addressed to.
 *
 * A room routes on what the message visibly says: the server reads `@name`
 * out of the text (`services/group-chat/mention-routing.ts`) and refuses a
 * structured mention that the text does not also carry. So the composer
 * cannot keep "mention everyone" as a private flag — the token has to be in
 * the field, exactly as the web client puts it there. The boundary rule below
 * is the server's own, ported so the two cannot disagree about what counts as
 * a mention.
 */
object GroupMentions {

    /** The reserved name that addresses every agent in the room. */
    const val ALL = "all"

    private val AFTER_BOUNDARY = setOf(
        '.', ',', '!', '?', ';', ':', '，', '。', '！', '？', '；', '：', ')', ']', '}', '>',
    )

    private val QUOTED_BLOCK = Regex("""<quoted_message(?:\s[^>]*)?>[\s\S]*?</quoted_message>""", RegexOption.IGNORE_CASE)

    /** A quoted message is not addressed to anyone; the server blanks it before routing. */
    private fun mask(text: String): String = QUOTED_BLOCK.replace(text) { match ->
        match.value.map { if (it == '\n') it else ' ' }.joinToString("")
    }

    private fun beforeBoundary(char: Char?): Boolean = char == null || !(char.isLetterOrDigit() && char.code < 128 || char == '_')

    private fun afterBoundary(char: Char?): Boolean = char == null || char.isWhitespace() || char in AFTER_BOUNDARY

    /** Every place [name] is mentioned in [text], as half-open ranges over [text]. */
    fun ranges(text: String, name: String): List<IntRange> {
        if (text.isEmpty() || name.isEmpty()) return emptyList()
        val routable = mask(text)
        val needle = "@${name.lowercase()}"
        val haystack = routable.lowercase()
        val found = mutableListOf<IntRange>()
        var from = 0
        while (from < text.length) {
            val at = haystack.indexOf(needle, from)
            if (at < 0) break
            val end = at + name.length + 1
            if (beforeBoundary(routable.getOrNull(at - 1)) && afterBoundary(routable.getOrNull(end))) {
                found += at until end
            }
            from = at + 1
        }
        return found
    }

    /** Whether [text] addresses [name] the way the server reads it. */
    fun mentions(text: String, name: String): Boolean = ranges(text, name).isNotEmpty()

    /** Whether [text] addresses every agent. */
    fun mentionsAll(text: String): Boolean = mentions(text, ALL)

    /**
     * Puts `@name ` in front of the draft, like the web composer's mention
     * insertion. A name already mentioned is left alone rather than repeated.
     */
    fun insert(text: String, caret: Int, name: String): MentionEdit {
        if (name.isBlank() || mentions(text, name)) return MentionEdit(text, caret.coerceIn(0, text.length))
        val token = "@$name "
        return MentionEdit(token + text, caret.coerceIn(0, text.length) + token.length)
    }

    /** Removes the first `@name` and the single space that follows it. */
    fun remove(text: String, caret: Int, name: String): MentionEdit {
        val range = ranges(text, name).firstOrNull() ?: return MentionEdit(text, caret.coerceIn(0, text.length))
        val end = if (text.getOrNull(range.last + 1) == ' ') range.last + 2 else range.last + 1
        val cut = end - range.first
        val position = caret.coerceIn(0, text.length)
        return MentionEdit(
            text.substring(0, range.first) + text.substring(end),
            if (position <= range.first) position else (position - cut).coerceAtLeast(range.first),
        )
    }

    /** The `@all` chip: adds the token when it is missing, takes it out when it is there. */
    fun toggleAll(text: String, caret: Int): MentionEdit =
        if (mentionsAll(text)) remove(text, caret, ALL) else insert(text, caret, ALL)
}

/** A draft after a mention edit, with the caret that goes with it. */
data class MentionEdit(val text: String, val caret: Int)
