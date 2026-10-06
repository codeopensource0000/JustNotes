package code.opensource0000.justnotes.export

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import code.opensource0000.justnotes.data.NotesRepository
import code.opensource0000.justnotes.data.local.NoteEntity
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Brings outside text into the app as notes. It lives next to NoteExporter
// because the two have to agree on the format JustNotes itself writes, but it
// is deliberately not only its mirror: the common case is a Markdown file
// produced somewhere else entirely — a chat transcript, a generated summary,
// notes from another tool — and such a file follows no convention of ours.
//
// So the parsing is permissive by design. Nothing about a file is required,
// and nothing is ever refused for failing to look like an export: a file with
// no heading simply becomes a note with no heading in it. The only refusals
// are files that are not text at all.
object NoteImporter {

    // What the file picker is asked to show. "text/*" covers .txt and the
    // devices that know .md, but Android has no MIME type for Markdown that
    // file managers agree on, so a .md very often arrives typed as
    // application/octet-stream — excluding that would hide exactly the files
    // this feature exists for. Letting unknown types through is safe because
    // the content is checked before anything is imported.
    val ACCEPTED_MIME_TYPES = arrayOf(
        "text/*",
        "application/octet-stream",
        "application/markdown",
        "application/x-markdown"
    )

    // A note is text a human reads. A megabyte of it is already far past what
    // the editor renders comfortably, so a bigger file is much more likely to
    // be something that merely looks like text.
    private const val MAX_FILE_BYTES = 1024 * 1024

    // Read size, and also how much of the file is inspected before deciding
    // it is text at all.
    private const val CHUNK_BYTES = 8 * 1024

    // A heading longer than this is prose that happens to start with "#", not
    // a title. It stays in the body rather than being hoisted out of it.
    private const val MAX_TITLE_LENGTH = 120

    data class Outcome(val imported: Int, val rejected: List<String>)

    data class ParsedNote(val title: String, val content: String)

    // Every file is handled on its own: one unreadable file among ten should
    // not cost the user the other nine, so failures are counted and reported
    // rather than thrown.
    suspend fun importAll(
        context: Context,
        repository: NotesRepository,
        uris: List<Uri>
    ): Outcome = withContext(Dispatchers.IO) {
        repository.ensureDefaultFolder()
        val folderId = repository.defaultFolder()?.id
            ?: return@withContext Outcome(0, uris.map { it.lastPathSegment ?: "?" })

        var imported = 0
        val rejected = mutableListOf<String>()
        for (uri in uris) {
            val displayName = displayNameOf(context, uri)
            val text = readTextOrNull(context, uri)
            if (text == null) {
                rejected += displayName
                continue
            }
            val parsed = parseNote(displayName, text)
            // An imported file is plain text: it carries no code and no
            // Keystore key, so it cannot arrive locked. The user can lock it
            // afterwards from the editor, which is also what creates the
            // secrets a file could never have held.
            val now = System.currentTimeMillis()
            repository.insertNote(
                NoteEntity(
                    folderId = folderId,
                    title = parsed.title,
                    content = parsed.content,
                    isLocked = false,
                    createdAt = now,
                    updatedAt = now
                )
            )
            imported++
        }
        Outcome(imported = imported, rejected = rejected)
    }

    // Returns null for anything that is not usable text, which covers what the
    // MIME filter cannot: a file manager that types everything loosely, or a
    // binary renamed to .md.
    private fun readTextOrNull(context: Context, uri: Uri): String? = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { readCapped(it) } ?: return null
        if (!looksLikeText(bytes)) return null
        bytes.decodeToString()
    }.getOrNull()

    // Reads in chunks and gives up past the cap, rather than buffering a file
    // of unknown size into memory first. InputStream.readNBytes would say this
    // in one line, but it only exists from Android 13 and this app supports 8.
    private fun readCapped(stream: InputStream): ByteArray? {
        val collected = ByteArrayOutputStream()
        val chunk = ByteArray(CHUNK_BYTES)
        while (true) {
            val read = stream.read(chunk)
            if (read < 0) break
            collected.write(chunk, 0, read)
            if (collected.size() > MAX_FILE_BYTES) return null
        }
        return collected.toByteArray()
    }

    // The heuristic file(1) and git use: a NUL byte near the start means this
    // is not text. Cheap, and it catches the realistic mistakes — an image or
    // an archive picked by accident — without pretending to detect every
    // encoding in existence.
    internal fun looksLikeText(bytes: ByteArray?): Boolean {
        if (bytes == null || bytes.isEmpty()) return false
        return bytes.take(CHUNK_BYTES).none { it == 0.toByte() }
    }

    private fun displayNameOf(context: Context, uri: Uri): String {
        val fromProvider = runCatching {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
        return fromProvider ?: uri.lastPathSegment ?: "note"
    }

    private data class Heading(val title: String, val rest: String)

    private data class FrontMatter(val title: String?, val rest: String)

    // internal so it can be tested directly, like NoteExporter.fileNameFor:
    // it is pure, so it needs no device to verify, and it is the part whose
    // behaviour the user actually sees.
    //
    // Order of preference for the title: a heading at the top of the file,
    // then a title: field in YAML front matter, then the file name. The file
    // name is the last resort rather than an error, because a note whose title
    // is blank shows as an empty row in the list — which reads as a bug.
    // Nothing is ever taken out of the body to serve as a title.
    internal fun parseNote(fileName: String, text: String): ParsedNote {
        val baseName = fileName.substringBeforeLast('.', fileName).trim().ifBlank { "note" }
        // A UTF-8 BOM is invisible and would stop "# " from matching.
        val withoutBom = text.removePrefix("\uFEFF")
        val frontMatter = stripFrontMatter(withoutBom)
        val heading = headingOf(frontMatter.rest)
        return ParsedNote(
            title = heading?.title ?: frontMatter.title ?: baseName,
            content = (heading?.rest ?: frontMatter.rest).trimStart('\n', '\r')
        )
    }

    // Accepts any ATX level (# to ######) and the Setext underline form. Level
    // is not checked: when a file's first heading is "##", that heading is
    // still what the document calls itself, and insisting on "#" would just
    // leave the title in the body.
    private fun headingOf(text: String): Heading? {
        val lines = text.lines()
        val start = lines.indexOfFirst { it.isNotBlank() }
        if (start < 0) return null
        val first = lines[start].trim()
        return when {
            // A fenced code block opening the file: a "#" inside it is a
            // comment in someone's code, not the title of a note.
            first.startsWith("```") || first.startsWith("~~~") -> null
            first.startsWith("#") -> cleanTitle(first.trimStart('#'))
                ?.let { Heading(it, lines.drop(start + 1).joinToString("\n")) }
            isSetextUnderline(lines.getOrNull(start + 1)) -> cleanTitle(first)
                ?.let { Heading(it, lines.drop(start + 2).joinToString("\n")) }
            else -> null
        }
    }

    // The underlined heading form: a row of '=' or '-' directly below the text.
    private fun isSetextUnderline(line: String?): Boolean {
        val trimmed = line?.trim().orEmpty()
        return trimmed.length >= 2 && (trimmed.all { it == '=' } || trimmed.all { it == '-' })
    }

    // Strips the markers a heading may be dressed in — closing hashes, bold or
    // italic emphasis — so "# **Plan**  #" titles the note "Plan". Returns
    // null when what is left is not usable as a title, which sends parseNote
    // back to its next option instead of showing the user markup.
    private fun cleanTitle(raw: String): String? {
        val cleaned = raw.trim().trimEnd('#').trim().trim('*', '_').trim()
        return cleaned.takeIf { it.isNotEmpty() && it.length <= MAX_TITLE_LENGTH }
    }

    // YAML front matter is metadata for other tools, and showing it as the
    // first lines of a note is exactly the kind of noise this guards against.
    private fun stripFrontMatter(text: String): FrontMatter {
        val lines = text.lines()
        val closing = frontMatterEnd(lines)
        if (closing < 0) return FrontMatter(null, text)
        val title = lines.subList(1, closing + 1)
            .firstOrNull { it.trim().startsWith("title:") }
            ?.substringAfter("title:")
            ?.trim()
            ?.trim('"', '\'')
            ?.let { cleanTitle(it) }
        return FrontMatter(title, lines.drop(closing + 2).joinToString("\n"))
    }

    // Index of the line closing the block, counted from after the opening
    // "---", or -1 when this file has no front matter. A block only counts as
    // front matter when it actually holds "key: value" lines, so a file that
    // merely opens with "---" as a horizontal rule keeps it.
    private fun frontMatterEnd(lines: List<String>): Int {
        if (lines.firstOrNull()?.trim() != "---") return -1
        val closing = lines.drop(1).indexOfFirst { it.trim() == "---" || it.trim() == "..." }
        val holdsKeys = closing >= 0 &&
            lines.subList(1, closing + 1).any { KEY_VALUE.containsMatchIn(it) }
        return if (holdsKeys) closing else -1
    }

    private val KEY_VALUE = Regex("^[A-Za-z][\\w-]*\\s*:")
}
