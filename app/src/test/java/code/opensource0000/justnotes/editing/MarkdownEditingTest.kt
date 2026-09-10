package code.opensource0000.justnotes.editing

import org.junit.Assert.assertEquals
import org.junit.Test

// What the formatting buttons and the dictation actually do to the text and to
// the cursor. This logic lived in the ViewModel until now, where the only way
// to check it was to run the app and watch where the caret landed — and its
// mistakes are exactly the kind that go unnoticed: a cursor one character off,
// a doubled space, never a crash.
class MarkdownEditingTest {

    // --- wrapSelection ---------------------------------------------------

    @Test
    fun `wrapping a selection puts markers either side and lands after them`() {
        val r = MarkdownEditing.wrapSelection("hello world", 6, 11, "**", "text")
        assertEquals("hello **world**", r.text)
        // Cursor sits past the closing marker, ready to keep typing.
        assertEquals(15, r.selectionStart)
        assertEquals(15, r.selectionEnd)
    }

    @Test
    fun `wrapping with no selection inserts a placeholder and selects it`() {
        val r = MarkdownEditing.wrapSelection("ab", 1, 1, "*", "texte")
        assertEquals("a*texte*b", r.text)
        // The placeholder is selected so the first keystroke replaces it.
        assertEquals("texte", r.text.substring(r.selectionStart, r.selectionEnd))
    }

    @Test
    fun `wrapping at the very start and the very end both work`() {
        assertEquals("**ab**", MarkdownEditing.wrapSelection("ab", 0, 2, "**", "t").text)
        val atEnd = MarkdownEditing.wrapSelection("ab", 2, 2, "*", "t")
        assertEquals("ab*t*", atEnd.text)
    }

    @Test
    fun `a reversed or out-of-range selection does not throw`() {
        // Defensive: selection ends up clamped rather than crashing on a
        // substring call.
        assertEquals("**ab**", MarkdownEditing.wrapSelection("ab", 0, 99, "**", "t").text)
        val reversed = MarkdownEditing.wrapSelection("ab", 2, 0, "*", "t")
        assertEquals("ab*t*", reversed.text)
    }

    // --- prefixCurrentLine -----------------------------------------------

    @Test
    fun `prefix goes to the start of the line the cursor is on`() {
        val text = "first\nsecond\nthird"
        // Cursor somewhere inside "second".
        val r = MarkdownEditing.prefixCurrentLine(text, 9, "- ")
        assertEquals("first\n- second\nthird", r.text)
    }

    @Test
    fun `prefix on the first line, and the cursor moves with the text`() {
        val r = MarkdownEditing.prefixCurrentLine("title", 3, "# ")
        assertEquals("# title", r.text)
        assertEquals(5, r.selectionStart)
    }

    @Test
    fun `prefix with the cursor at the very start of a line`() {
        val r = MarkdownEditing.prefixCurrentLine("a\nb", 2, "- [ ] ")
        assertEquals("a\n- [ ] b", r.text)
    }

    @Test
    fun `prefix on an empty document`() {
        assertEquals("- ", MarkdownEditing.prefixCurrentLine("", 0, "- ").text)
    }

    // --- insertDictated ---------------------------------------------------

    @Test
    fun `a dictated phrase gets a leading space only when one is missing`() {
        assertEquals("bonjour test", MarkdownEditing.insertDictated("bonjour", 7, "test").text)
        // Already a space there: no second one.
        assertEquals("bonjour test", MarkdownEditing.insertDictated("bonjour ", 8, "test").text)
    }

    @Test
    fun `dictating into an empty note adds no spaces at all`() {
        assertEquals("bonjour", MarkdownEditing.insertDictated("", 0, "bonjour").text)
    }

    // Regression: the trailing space used to be unconditional, so dictating
    // before an existing space produced "a b  c".
    @Test
    fun `dictating before an existing space does not double it`() {
        val r = MarkdownEditing.insertDictated("a c", 1, "b")
        assertEquals("a b c", r.text)
        assertEquals(3, r.selectionStart)
    }

    @Test
    fun `dictating where the next character is not a space adds one`() {
        assertEquals("a b c", MarkdownEditing.insertDictated("ac", 1, "b").text)
    }

    @Test
    fun `consecutive utterances are separated by exactly one space`() {
        var state = MarkdownEditing.insertDictated("", 0, "un")
        state = MarkdownEditing.insertDictated(state.text, state.selectionStart, "deux")
        state = MarkdownEditing.insertDictated(state.text, state.selectionStart, "trois")
        assertEquals("un deux trois", state.text)
    }
}
