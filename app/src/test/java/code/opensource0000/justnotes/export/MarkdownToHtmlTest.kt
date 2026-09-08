package code.opensource0000.justnotes.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// HTML export is the one place where note text leaves the app as markup rather
// than as plain text, so escaping is the thing worth pinning down: a note is
// arbitrary user input, and it ends up inside a document someone opens in a
// browser.
class MarkdownToHtmlTest {

    @Test
    fun `heading becomes h2 and the note title stays the h1`() {
        val html = MarkdownToHtml.render("My note", "# Section")
        assertTrue(html.contains("<h1>My note</h1>"))
        assertTrue(html.contains("<h2>Section</h2>"))
    }

    @Test
    fun `bold and italic become strong and em`() {
        val html = MarkdownToHtml.render("t", "**bold** and *italic*")
        assertTrue(html.contains("<strong>bold</strong>"))
        assertTrue(html.contains("<em>italic</em>"))
    }

    @Test
    fun `bullets become a list that closes on a blank line`() {
        val html = MarkdownToHtml.render("t", "- one\n- two\n\nafter")
        assertTrue(html.contains("<li>one</li>"))
        assertTrue(html.contains("<li>two</li>"))
        assertTrue(html.contains("</ul>"))
        assertTrue(html.contains("<p>after</p>"))
        // One list, not one per item.
        assertEquals(1, Regex("<ul>").findAll(html).count())
    }

    @Test
    fun `checkboxes render checked and unchecked`() {
        val html = MarkdownToHtml.render("t", "- [ ] todo\n- [x] done")
        assertTrue(html.contains("""<input type="checkbox" disabled> todo"""))
        assertTrue(html.contains("""<input type="checkbox" checked disabled> done"""))
    }

    @Test
    fun `an unclosed list is closed at the end of the document`() {
        val html = MarkdownToHtml.render("t", "- one\n- two")
        assertEquals(
            Regex("<ul>").findAll(html).count(),
            Regex("</ul>").findAll(html).count()
        )
    }

    @Test
    fun `markup in the note body is escaped, not emitted`() {
        val html = MarkdownToHtml.render("t", "<script>alert(1)</script>")
        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;"))
    }

    @Test
    fun `markup in the title is escaped too`() {
        val html = MarkdownToHtml.render("<b>hi</b>", "body")
        assertFalse(html.contains("<h1><b>hi</b></h1>"))
        assertTrue(html.contains("&lt;b&gt;hi&lt;/b&gt;"))
    }

    @Test
    fun `ampersands are escaped once, not twice`() {
        val html = MarkdownToHtml.render("t", "Tom & Jerry")
        assertTrue(html.contains("Tom &amp; Jerry"))
        assertFalse(html.contains("&amp;amp;"))
    }

    @Test
    fun `a blank title falls back rather than leaving an empty heading`() {
        val html = MarkdownToHtml.render("   ", "body")
        assertTrue(html.contains("<title>Note</title>"))
        assertTrue(html.contains("<h1>Note</h1>"))
    }

    @Test
    fun `an empty note still produces a whole document`() {
        val html = MarkdownToHtml.render("t", "")
        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue(html.trimEnd().endsWith("</html>"))
    }
}
