package code.opensource0000.justnotes.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// A note's title is arbitrary user input that becomes a filename, so the
// sanitising here is what keeps an export inside the cache directory it is
// meant to land in.
class ExportFileNameTest {

    @Test
    fun `an ordinary title is kept as-is`() {
        assertEquals("Courses.txt", NoteExporter.fileNameFor("Courses", ExportFormat.PLAIN_TEXT))
    }

    @Test
    fun `the extension follows the chosen format`() {
        assertTrue(NoteExporter.fileNameFor("n", ExportFormat.MARKDOWN).endsWith(".md"))
        assertTrue(NoteExporter.fileNameFor("n", ExportFormat.HTML).endsWith(".html"))
    }

    @Test
    fun `path separators cannot escape the export directory`() {
        val name = NoteExporter.fileNameFor("../../etc/passwd", ExportFormat.PLAIN_TEXT)
        assertFalse(name.contains("/"))
        assertFalse(name.contains(".."))
    }

    @Test
    fun `a blank title falls back to a usable name`() {
        assertEquals("note.txt", NoteExporter.fileNameFor("   ", ExportFormat.PLAIN_TEXT))
        assertEquals("note.txt", NoteExporter.fileNameFor("", ExportFormat.PLAIN_TEXT))
    }

    @Test
    fun `accented letters and digits survive, punctuation does not`() {
        // \p{L} and \p{N} keep this readable for French titles rather than
        // reducing them to underscores.
        assertEquals("Réunion 3 équipe.txt", NoteExporter.fileNameFor("Réunion 3 équipe", ExportFormat.PLAIN_TEXT))
        assertFalse(NoteExporter.fileNameFor("a:b*c?d", ExportFormat.PLAIN_TEXT).contains(":"))
    }

    @Test
    fun `a very long title is capped`() {
        val name = NoteExporter.fileNameFor("x".repeat(200), ExportFormat.PLAIN_TEXT)
        assertEquals(60 + ".txt".length, name.length)
    }

    @Test
    fun `newlines in a title do not survive into the filename`() {
        val name = NoteExporter.fileNameFor("line\nbreak", ExportFormat.PLAIN_TEXT)
        assertFalse(name.contains("\n"))
    }
}
