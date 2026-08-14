package code.opensource0000.justnotes.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

enum class ExportFormat(val extension: String, val mimeType: String) {
    // .txt: opens on any phone with any text viewer, but shows raw "**"/"#"
    // Markdown markers as-is — no app renders them specially.
    PLAIN_TEXT("txt", "text/plain"),
    // .md: semantically correct for the Markdown syntax inside, but many
    // phones have no app registered to open it at all.
    MARKDOWN("md", "text/markdown"),
    // .html: opens in any browser on any phone, and actually renders
    // bold/italic/headings/lists instead of showing raw marker characters.
    HTML("html", "text/html")
}

// Writes a note out to a file and hands it to Android's share sheet — the
// user picks where it goes (email, Drive, another app...) and which format.
// Deliberately unencrypted: by the time this runs the note is already
// decrypted in the editor, and the whole point of exporting is to leave the
// app in a form other tools can read.
object NoteExporter {

    fun export(context: Context, title: String, content: String, format: ExportFormat) {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, fileNameFor(title, format))
        file.writeText(renderContent(title, content, format))

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    private fun renderContent(title: String, content: String, format: ExportFormat): String =
        when (format) {
            ExportFormat.HTML -> MarkdownToHtml.render(title, content)
            ExportFormat.PLAIN_TEXT, ExportFormat.MARKDOWN -> buildString {
                if (title.isNotBlank()) {
                    append("# ").append(title).append("\n\n")
                }
                append(content)
            }
        }

    private fun fileNameFor(title: String, format: ExportFormat): String {
        val base = title.trim()
            .ifBlank { "note" }
            .replace(Regex("[^\\p{L}\\p{N} _-]"), "_")
            .take(60)
        return "$base.${format.extension}"
    }
}
