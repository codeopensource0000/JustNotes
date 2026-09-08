package code.opensource0000.justnotes.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// The live-preview transform hides Markdown markers, so every character
// position on screen can differ from the real text and cursor positions have
// to be remapped both ways. It is the one piece of hand-rolled index
// arithmetic in the app, and its failure mode is quiet: a cursor that lands
// one character off, never a crash.
//
// buildTransformedText() assumes the hidden ranges it receives are sorted,
// disjoint, and start at distinct offsets. Nothing enforces that —
// collectHiddenRanges() only sorts — and if two ranges ever shared a start
// offset the loop would stop consuming ranges entirely and every marker after
// that point would reappear. These tests pin that assumption down against the
// nested and overlapping-looking inputs that come closest to breaking it.
class MarkdownTransformTest {

    private fun hidden(text: String) = MarkdownVisualTransformation.collectHiddenRanges(text)

    private fun transform(text: String) =
        MarkdownVisualTransformation.buildTransformedText(text, hidden(text))

    private fun assertRangesAreWellFormed(text: String) {
        val ranges = hidden(text)
        ranges.zipWithNext { a, b ->
            assertTrue(
                "ranges overlap or share a start in \"$text\": $a then $b",
                a.last < b.first
            )
        }
        ranges.forEach {
            assertTrue("range out of bounds in \"$text\": $it", it.first >= 0 && it.last < text.length)
        }
    }

    @Test
    fun `bold markers are hidden from the displayed text`() {
        assertEquals("bold", transform("**bold**").text)
    }

    @Test
    fun `italic markers are hidden from the displayed text`() {
        assertEquals("italic", transform("*italic*").text)
    }

    @Test
    fun `heading prefix is hidden but its text is kept`() {
        assertEquals("Title", transform("# Title").text)
    }

    @Test
    fun `bullets and checkboxes are left alone`() {
        val text = "- one\n- [ ] two\n- [x] three"
        assertEquals(text, transform(text).text)
    }

    @Test
    fun `text without markers is untouched`() {
        val text = "plain text, 100% unremarkable"
        assertEquals(text, transform(text).text)
    }

    // The cases that could plausibly produce two ranges starting at the same
    // offset, which is the assumption buildTransformedText silently relies on.
    @Test
    fun `nested and adjacent markers still produce disjoint ranges`() {
        listOf(
            "***a***",
            "**a *b** c*",
            "*a **b* c**",
            "**a**b**c**",
            "*a*b*c*",
            "# **bold heading**",
            "# *a* and **b**",
            "**multi\nline**",
            "****",
            "*",
            "**",
            "***"
        ).forEach { assertRangesAreWellFormed(it) }
    }

    @Test
    fun `offset mapping stays in bounds and never goes backwards`() {
        listOf("**bold** and *italic*", "# Title\n- item\n**b**", "***a***").forEach { text ->
            val result = transform(text)
            val forward = result.originalToTransformed
            assertEquals(text.length + 1, forward.size)
            forward.forEachIndexed { index, mapped ->
                assertTrue(
                    "offset $index of \"$text\" maps outside the displayed text",
                    mapped in 0..result.text.length
                )
                if (index > 0) {
                    assertTrue(
                        "mapping went backwards at offset $index of \"$text\"",
                        mapped >= forward[index - 1]
                    )
                }
            }
        }
    }

    @Test
    fun `a displayed offset maps back to an original offset showing that character`() {
        val text = "**bold** tail"
        val result = transform(text)
        // "tail" begins at index 9 in the original, right after "** ".
        val displayedTailStart = result.originalToTransformed[9]
        assertEquals(9, result.transformedToOriginal[displayedTailStart])
    }

    @Test
    fun `empty text maps cleanly`() {
        val result = transform("")
        assertEquals("", result.text)
        assertEquals(1, result.originalToTransformed.size)
        assertEquals(0, result.originalToTransformed[0])
    }
}
