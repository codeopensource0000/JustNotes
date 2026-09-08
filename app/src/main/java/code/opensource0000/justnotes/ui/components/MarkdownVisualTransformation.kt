package code.opensource0000.justnotes.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp

// A light live preview of our Markdown-subset formatting, applied only to how
// the text *looks* in the editor — the stored string never changes. Unlike a
// simpler version that just styles the markers in place, this one hides them
// entirely, which means every character position in the displayed text can
// differ from the real text, so cursor/selection positions have to be
// remapped both ways (see buildOffsetMapping). Bullets and checkboxes are
// left alone: a "- " or "- [ ] " prefix already reads fine as plain text.
object MarkdownVisualTransformation : VisualTransformation {

    // DOT_MATCHES_ALL: by default "." never matches a newline, which broke
    // bold/italic the moment the span crossed a line break — the marker
    // pair no longer matched as one, so it re-appeared and lost its style.
    private val boldRegex = Regex("\\*\\*(.+?)\\*\\*", RegexOption.DOT_MATCHES_ALL)
    // Negative look-around keeps this from matching inside "**bold**" pairs.
    private val italicRegex = Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)", RegexOption.DOT_MATCHES_ALL)
    private val headingRegex = Regex("^# (.*)$", RegexOption.MULTILINE)

    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        val hidden = collectHiddenRanges(original)
        val (transformedText, origToTrans, transToOrig) = buildTransformedText(original, hidden)

        val styled = AnnotatedString.Builder(transformedText).apply {
            headingRegex.findAll(original).forEach { match ->
                val group = match.groups[1] ?: return@forEach
                addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                    origToTrans[group.range.first],
                    origToTrans[group.range.last + 1]
                )
            }
            boldRegex.findAll(original).forEach { match ->
                val group = match.groups[1] ?: return@forEach
                addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold),
                    origToTrans[group.range.first],
                    origToTrans[group.range.last + 1]
                )
            }
            italicRegex.findAll(original).forEach { match ->
                val group = match.groups[1] ?: return@forEach
                addStyle(
                    SpanStyle(fontStyle = FontStyle.Italic),
                    origToTrans[group.range.first],
                    origToTrans[group.range.last + 1]
                )
            }
        }.toAnnotatedString()

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                origToTrans[offset.coerceIn(0, original.length)]

            override fun transformedToOriginal(offset: Int): Int =
                transToOrig[offset.coerceIn(0, transformedText.length)]
        }
        return TransformedText(styled, offsetMapping)
    }

    // Marker characters to drop from the displayed text: "**"/"*" pairs
    // around bold/italic content, and the "# " prefix on headings.
    // internal, not private: this and buildTransformedText below are the
    // only genuinely tricky logic in the file — hand-rolled index arithmetic
    // whose mistakes shift a cursor by one character rather than crashing.
    // Unit tests reach them directly, without needing a Compose runtime.
    internal fun collectHiddenRanges(original: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        headingRegex.findAll(original).forEach { match ->
            ranges += match.range.first until (match.range.first + 2)
        }
        boldRegex.findAll(original).forEach { match ->
            val group = match.groups[1] ?: return@forEach
            ranges += match.range.first until group.range.first
            ranges += (group.range.last + 1)..match.range.last
        }
        italicRegex.findAll(original).forEach { match ->
            val group = match.groups[1] ?: return@forEach
            ranges += match.range.first until group.range.first
            ranges += (group.range.last + 1)..match.range.last
        }
        return ranges.sortedBy { it.first }
    }

    internal data class TransformResult(
        val text: String,
        // originalToTransformed[i] = position in the displayed text that
        // corresponds to original character index i (0..original.length).
        val originalToTransformed: IntArray,
        // transformedToOriginal[i] = an original index that maps to
        // displayed position i (0..transformed.length).
        val transformedToOriginal: IntArray
    )

    internal fun buildTransformedText(original: String, hiddenRanges: List<IntRange>): TransformResult {
        val builder = StringBuilder()
        val origToTrans = IntArray(original.length + 1)
        var oi = 0
        var rangeIndex = 0
        while (oi < original.length) {
            val current = hiddenRanges.getOrNull(rangeIndex)
            if (current != null && oi == current.first) {
                while (oi <= current.last) {
                    origToTrans[oi] = builder.length
                    oi++
                }
                rangeIndex++
            } else {
                origToTrans[oi] = builder.length
                builder.append(original[oi])
                oi++
            }
        }
        origToTrans[original.length] = builder.length

        val transToOrig = IntArray(builder.length + 1)
        var lastTransformed = -1
        for (index in 0..original.length) {
            val t = origToTrans[index]
            if (t != lastTransformed) {
                transToOrig[t] = index
                lastTransformed = t
            }
        }
        return TransformResult(builder.toString(), origToTrans, transToOrig)
    }
}
