package code.opensource0000.justnotes.export

// Converts our Markdown subset (bold, italic, "# " headings, "- " bullets,
// "- [ ] " checkboxes) into a small, self-contained HTML document — used
// only for the optional HTML export format, so a shared note can show real
// formatting in any browser instead of raw "**" characters.
object MarkdownToHtml {

    private val boldRegex = Regex("\\*\\*(.+?)\\*\\*", RegexOption.DOT_MATCHES_ALL)
    private val italicRegex = Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)", RegexOption.DOT_MATCHES_ALL)

    fun render(title: String, content: String): String {
        val safeTitle = escapeHtml(title.ifBlank { "Note" })
        val body = renderBody(content)
        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>$safeTitle</title>
            <style>
                body { font-family: -apple-system, Roboto, sans-serif; max-width: 640px; margin: 24px auto; padding: 0 16px; line-height: 1.5; color: #1a1a1a; }
                h1 { font-size: 1.4em; }
                h2 { font-size: 1.15em; }
                ul { padding-left: 20px; }
            </style>
            </head>
            <body>
            <h1>$safeTitle</h1>
            $body
            </body>
            </html>
        """.trimIndent()
    }

    private fun renderBody(content: String): String = buildString {
        var inList = false
        content.lines().forEach { line ->
            when {
                line.startsWith("# ") -> {
                    if (inList) { append("</ul>\n"); inList = false }
                    append("<h2>").append(inlineHtml(line.removePrefix("# "))).append("</h2>\n")
                }
                line.startsWith("- [x] ", ignoreCase = true) -> {
                    if (!inList) { append("<ul>\n"); inList = true }
                    append("<li><input type=\"checkbox\" checked disabled> ")
                        .append(inlineHtml(line.substring(6)))
                        .append("</li>\n")
                }
                line.startsWith("- [ ] ") -> {
                    if (!inList) { append("<ul>\n"); inList = true }
                    append("<li><input type=\"checkbox\" disabled> ")
                        .append(inlineHtml(line.substring(6)))
                        .append("</li>\n")
                }
                line.startsWith("- ") -> {
                    if (!inList) { append("<ul>\n"); inList = true }
                    append("<li>").append(inlineHtml(line.removePrefix("- "))).append("</li>\n")
                }
                line.isBlank() -> {
                    if (inList) { append("</ul>\n"); inList = false }
                }
                else -> {
                    if (inList) { append("</ul>\n"); inList = false }
                    append("<p>").append(inlineHtml(line)).append("</p>\n")
                }
            }
        }
        if (inList) append("</ul>\n")
    }

    private fun inlineHtml(line: String): String {
        // Escape first so the Markdown markers themselves (plain "*") are
        // untouched by escaping, then convert those markers to real tags.
        val escaped = escapeHtml(line)
        val withBold = boldRegex.replace(escaped) { "<strong>${it.groupValues[1]}</strong>" }
        return italicRegex.replace(withBold) { "<em>${it.groupValues[1]}</em>" }
    }

    private fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
