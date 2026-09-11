package code.opensource0000.justnotes.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    // Long enough for any real title, short enough to stay well inside the
    // filename limits of whatever app receives the share.
    private const val MAX_FILENAME_LENGTH = 60

    // Every export leaves a readable copy behind in the cache, and for a note
    // protected by the secondary lock that copy is its decrypted text — which
    // used to survive the note being locked again, or deleted outright, for as
    // long as the app stayed installed.
    //
    // Deleting right after sharing is not an option: the receiving app is
    // handed a content:// URI it may not have finished reading. Clearing at
    // launch instead bounds the exposure to a single session, which is the
    // best that can be done without breaking the share itself.
    // Blocking: call it from an IO dispatcher, as MainActivity does.
    fun purgeCache(context: Context) {
        val exportDir = File(context.cacheDir, EXPORT_DIR_NAME)
        if (exportDir.exists()) exportDir.deleteRecursively()
    }

    // suspend, because rendering and writing a note is real file I/O and this
    // is reached straight from a tap: on a long note it stuttered the frame it
    // was called on. Only the write moves off the main thread — startActivity
    // has to stay on it.
    suspend fun export(context: Context, title: String, content: String, format: ExportFormat) {
        val uri = withContext(Dispatchers.IO) {
            val exportDir = File(context.cacheDir, EXPORT_DIR_NAME).apply { mkdirs() }
            val file = File(exportDir, fileNameFor(title, format))
            file.writeText(renderContent(title, content, format))
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
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

    private const val EXPORT_DIR_NAME = "exports"

    // internal so the sanitising below can be tested directly: it is what
    // stops a note title from steering the export out of the cache directory.
    internal fun fileNameFor(title: String, format: ExportFormat): String {
        val base = title.trim()
            .ifBlank { "note" }
            .replace(Regex("[^\\p{L}\\p{N} _-]"), "_")
            .take(MAX_FILENAME_LENGTH)
        return "$base.${format.extension}"
    }
}
