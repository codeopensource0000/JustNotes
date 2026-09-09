package code.opensource0000.justnotes.data

import android.content.Context
import code.opensource0000.justnotes.data.local.FolderEntity
import code.opensource0000.justnotes.data.local.FolderWithNoteCount
import code.opensource0000.justnotes.data.local.JustNotesDatabase
import code.opensource0000.justnotes.data.local.NoteEntity
import code.opensource0000.justnotes.security.NoteSecrets
import kotlinx.coroutines.flow.Flow

// Everything the screens are allowed to do to notes and folders.
//
// It exists for one rule above all: deleting a note is not "delete the row".
// A locked note also has a code, a fingerprint shortcut and a derived key
// living outside Room, and all four have to go together. That rule used to be
// written out at every call site — three copies of it, plus a fourth variant
// for folder deletion — which is how one of them eventually gets forgotten and
// leaves a live code behind for a note that no longer exists.
//
// The second reason is that it is an interface: a ViewModel that talks to this
// can be tested against a fake, where one that reaches for
// JustNotesDatabase.getInstance() needs a real Android context and a real
// SQLite file.
interface NotesRepository {

    fun observeRecentNotes(): Flow<List<NoteEntity>>

    fun observeNotesInFolder(folderId: Long): Flow<List<NoteEntity>>

    fun observeFolders(): Flow<List<FolderEntity>>

    fun observeFoldersWithNoteCount(): Flow<List<FolderWithNoteCount>>

    suspend fun note(id: Long): NoteEntity?

    suspend fun folder(id: Long): FolderEntity?

    suspend fun defaultFolder(): FolderEntity?

    // Creates the catch-all folder if this install does not have one yet.
    suspend fun ensureDefaultFolder()

    suspend fun createFolder(name: String): Long

    suspend fun insertNote(note: NoteEntity): Long

    suspend fun updateNote(note: NoteEntity)

    suspend fun moveNote(noteId: Long, folderId: Long)

    // Retires the note's secrets as well as its row — see the note above.
    suspend fun deleteNote(note: NoteEntity)

    // Room's ON DELETE CASCADE removes the folder's notes, but it knows
    // nothing about the secrets those notes owned, so they are retired here
    // before the cascade fires.
    suspend fun deleteFolder(folderId: Long)
}

class RoomNotesRepository(
    context: Context,
    // Injectable so tests can run against an in-memory database instead of
    // the real notes file sitting on the device.
    database: JustNotesDatabase = JustNotesDatabase.getInstance(context)
) : NotesRepository {

    private val appContext = context.applicationContext
    private val notes = database.noteDao()
    private val folders = database.folderDao()

    override fun observeRecentNotes(): Flow<List<NoteEntity>> = notes.observeRecent()

    override fun observeNotesInFolder(folderId: Long): Flow<List<NoteEntity>> =
        notes.observeByFolder(folderId)

    override fun observeFolders(): Flow<List<FolderEntity>> = folders.observeAll()

    override fun observeFoldersWithNoteCount(): Flow<List<FolderWithNoteCount>> =
        folders.observeAllWithNoteCount()

    override suspend fun note(id: Long): NoteEntity? = notes.getById(id)

    override suspend fun folder(id: Long): FolderEntity? = folders.getById(id)

    override suspend fun defaultFolder(): FolderEntity? = folders.getDefaultFolder()

    override suspend fun ensureDefaultFolder() {
        if (folders.getDefaultFolder() != null) return
        folders.insert(FolderEntity(name = FolderEntity.DEFAULT_FOLDER_NAME, isDefault = true))
    }

    override suspend fun createFolder(name: String): Long =
        folders.insert(FolderEntity(name = name))

    override suspend fun insertNote(note: NoteEntity): Long = notes.insert(note)

    override suspend fun updateNote(note: NoteEntity) = notes.update(note)

    override suspend fun moveNote(noteId: Long, folderId: Long) =
        notes.updateFolderId(noteId, folderId)

    override suspend fun deleteNote(note: NoteEntity) {
        if (note.isLocked) NoteSecrets.forget(appContext, note.id)
        notes.deleteById(note.id)
    }

    override suspend fun deleteFolder(folderId: Long) {
        notes.getByFolder(folderId)
            .filter { it.isLocked }
            .forEach { NoteSecrets.forget(appContext, it.id) }
        folders.deleteById(folderId)
    }

    companion object {
        @Volatile
        private var instance: NotesRepository? = null

        // A single instance, like the database it wraps. Held here rather than
        // built per ViewModel so that swapping it for a fake in a test is one
        // change, not one per screen.
        fun get(context: Context): NotesRepository {
            return instance ?: synchronized(this) {
                instance ?: RoomNotesRepository(context).also { instance = it }
            }
        }
    }
}
