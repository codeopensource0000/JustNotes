package code.opensource0000.justnotes.editing

// Text surgery for the editor's formatting buttons and for dictated speech.
//
// Deliberately free of Compose and of Android: these take a string and a
// selection and hand back a string and a selection, nothing more. They used to
// live inside NoteEditorViewModel operating on TextFieldValue, which meant the
// one part of the editor that is pure arithmetic on indices could only be
// exercised by running the app on a phone and watching where the cursor
// landed. Here they are ordinary functions with ordinary tests.
data class EditedText(val text: String, val selectionStart: Int, val selectionEnd: Int) {
    constructor(text: String, cursor: Int) : this(text, cursor, cursor)
}

object MarkdownEditing {

    // Wraps the selection in a Markdown marker (bold, italic).
    //
    // With nothing selected, an empty pair like "****" cannot be recognised by
    // the live-preview transform — it needs at least one character between the
    // markers — so the asterisks would sit there visibly until something was
    // typed. Inserting a placeholder word instead, already selected, means the
    // markers hide immediately and the first keystroke replaces the word.
    fun wrapSelection(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        marker: String,
        placeholderForEmpty: String
    ): EditedText {
        val start = selectionStart.coerceIn(0, text.length)
        val end = selectionEnd.coerceIn(start, text.length)
        if (start == end) {
            val newText = text.substring(0, start) +
                marker + placeholderForEmpty + marker +
                text.substring(start)
            val placeholderStart = start + marker.length
            return EditedText(newText, placeholderStart, placeholderStart + placeholderForEmpty.length)
        }
        val newText = text.substring(0, start) +
            marker + text.substring(start, end) + marker +
            text.substring(end)
        return EditedText(newText, end + marker.length * 2)
    }

    // Inserts a line prefix (heading, bullet, checkbox) at the start of the
    // line the cursor is on, leaving the cursor on the same character it was.
    fun prefixCurrentLine(text: String, cursor: Int, prefix: String): EditedText {
        val at = cursor.coerceIn(0, text.length)
        val newlineIndex = if (at == 0) -1 else text.lastIndexOf('\n', at - 1)
        val lineStart = if (newlineIndex == -1) 0 else newlineIndex + 1
        val newText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
        return EditedText(newText, at + prefix.length)
    }

    // Drops a recognised phrase in at the cursor. Vosk hands over one phrase at
    // a time with no spacing of its own, so spaces are added on each side only
    // where one is actually missing.
    //
    // Both sides matter, and the trailing one used to be added unconditionally:
    // dictating into the middle of "a c" produced "a b  c", because the space
    // already sitting after the cursor got a second one in front of it. At the
    // end of the text no trailing space is wanted either — the next utterance
    // adds its own leading one.
    fun insertDictated(text: String, cursor: Int, phrase: String): EditedText {
        val at = cursor.coerceIn(0, text.length)
        val needsLeadingSpace = at > 0 && !text[at - 1].isWhitespace()
        val needsTrailingSpace = at < text.length && !text[at].isWhitespace()
        val insertion = buildString {
            if (needsLeadingSpace) append(' ')
            append(phrase)
            if (needsTrailingSpace) append(' ')
        }
        val newText = text.substring(0, at) + insertion + text.substring(at)
        // Cursor lands right after the phrase, before any trailing space.
        val cursorOffset = insertion.length - if (needsTrailingSpace) 1 else 0
        return EditedText(newText, at + cursorOffset)
    }
}
