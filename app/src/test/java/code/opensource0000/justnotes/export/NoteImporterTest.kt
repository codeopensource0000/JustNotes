package code.opensource0000.justnotes.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Pure parsing only: parseNote and looksLikeText need no device, which is why
// they are kept apart from the file and database work.
//
// These cases are the specification of "permissive". The point of the feature
// is importing Markdown written somewhere else, so the tests are mostly about
// files that never came from this app.
class NoteImporterTest {

    @Test
    fun `a file this app exported comes back as its title and body`() {
        val parsed = NoteImporter.parseNote("Courses.md", "# Courses\n\nPain\nLait")

        assertEquals("Courses", parsed.title)
        assertEquals("Pain\nLait", parsed.content)
    }

    @Test
    fun `a heading of any level is accepted as the title`() {
        // The document's first heading is what it calls itself, whatever its
        // level; requiring "#" would leave this title sitting in the body.
        val parsed = NoteImporter.parseNote("notes.md", "## Partie 2\n\nsuite")

        assertEquals("Partie 2", parsed.title)
        assertEquals("suite", parsed.content)
    }

    @Test
    fun `an underlined heading is recognised too`() {
        val parsed = NoteImporter.parseNote("notes.md", "Plan de match\n=====\n\ncorps")

        assertEquals("Plan de match", parsed.title)
        assertEquals("corps", parsed.content)
    }

    @Test
    fun `emphasis and closing hashes are stripped from the title`() {
        val parsed = NoteImporter.parseNote("x.md", "# **Plan** #\n\ncorps")

        assertEquals("Plan", parsed.title)
    }

    @Test
    fun `body-only text is imported whole, titled after the file`() {
        // The main case for a file from elsewhere: no heading at all. Nothing
        // is refused and nothing is taken out of the body to invent a title.
        val parsed = NoteImporter.parseNote("idées en vrac.txt", "première ligne\nseconde")

        assertEquals("idées en vrac", parsed.title)
        assertEquals("première ligne\nseconde", parsed.content)
    }

    @Test
    fun `yaml front matter is removed and can supply the title`() {
        val text = "---\ntitle: Réunion\ndate: 2026-10-06\n---\n\nordre du jour"

        val parsed = NoteImporter.parseNote("export.md", text)

        assertEquals("Réunion", parsed.title)
        assertEquals("ordre du jour", parsed.content)
    }

    @Test
    fun `a heading wins over front matter`() {
        val text = "---\ntitle: Métadonnée\n---\n\n# Vrai titre\n\ncorps"

        val parsed = NoteImporter.parseNote("export.md", text)

        assertEquals("Vrai titre", parsed.title)
        assertEquals("corps", parsed.content)
    }

    @Test
    fun `a leading horizontal rule is not mistaken for front matter`() {
        val text = "---\n\ndu texte\n\n---\n"

        val parsed = NoteImporter.parseNote("regle.md", text)

        assertEquals("regle", parsed.title)
        assertTrue(parsed.content.startsWith("---"))
    }

    @Test
    fun `a hash inside an opening code fence is not a title`() {
        val text = "```python\n# ceci est un commentaire\nprint(1)\n```"

        val parsed = NoteImporter.parseNote("bout de code.md", text)

        assertEquals("bout de code", parsed.title)
        assertEquals(text, parsed.content)
    }

    @Test
    fun `a very long heading stays in the body`() {
        val longLine = "# " + "mot ".repeat(60)

        val parsed = NoteImporter.parseNote("prose.md", longLine)

        assertEquals("prose", parsed.title)
        assertEquals(longLine, parsed.content)
    }

    @Test
    fun `an empty file keeps a usable title`() {
        val parsed = NoteImporter.parseNote("vide.txt", "")

        assertEquals("vide", parsed.title)
        assertEquals("", parsed.content)
    }

    @Test
    fun `a byte order mark does not hide the heading`() {
        val parsed = NoteImporter.parseNote("x.md", "\uFEFF# Titre\n\ncorps")

        assertEquals("Titre", parsed.title)
        assertEquals("corps", parsed.content)
    }

    @Test
    fun `text is accepted and binary is not`() {
        assertTrue(NoteImporter.looksLikeText("une note".encodeToByteArray()))
        // A PNG header: the NUL byte is what gives it away.
        assertFalse(NoteImporter.looksLikeText(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x00, 0x0D)))
        // An oversized file arrives here as null, and nothing to import is not
        // text either.
        assertFalse(NoteImporter.looksLikeText(null))
        assertFalse(NoteImporter.looksLikeText(ByteArray(0)))
    }
}
