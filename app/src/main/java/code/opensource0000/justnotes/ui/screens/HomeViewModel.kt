package code.opensource0000.justnotes.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import code.opensource0000.justnotes.data.local.FolderEntity
import code.opensource0000.justnotes.data.local.FolderWithNoteCount
import code.opensource0000.justnotes.data.local.JustNotesDatabase
import code.opensource0000.justnotes.data.local.NoteEntity
import code.opensource0000.justnotes.security.NoteSecrets
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// AndroidViewModel (rather than plain ViewModel) gives us access to an
// Application context, which the database needs to open its SQLite file.
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val database = JustNotesDatabase.getInstance(application)

    // stateIn turns the DAO's Flow into a StateFlow: Compose can read its
    // .value directly, and it keeps the latest list in memory even when
    // briefly nobody is observing (e.g. during a screen rotation).
    val recentNotes: StateFlow<List<NoteEntity>> = database.noteDao()
        .observeRecent()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList()
        )

    val folders: StateFlow<List<FolderWithNoteCount>> = database.folderDao()
        .observeAllWithNoteCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList()
        )

    // Flat list (no note counts) for the "move to folder" picker.
    val allFolders: StateFlow<List<FolderEntity>> = database.folderDao()
        .observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList()
        )

    init {
        ensureDefaultFolderExists()
    }

    // Runs on every app start; does nothing once the default folder already
    // exists, so it is safe to call unconditionally instead of only on first
    // install (simpler than wiring a Room creation callback).
    private fun ensureDefaultFolderExists() {
        viewModelScope.launch {
            if (database.folderDao().getDefaultFolder() == null) {
                database.folderDao().insert(
                    FolderEntity(name = FolderEntity.DEFAULT_FOLDER_NAME, isDefault = true)
                )
            }
        }
    }

    fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            database.folderDao().insert(FolderEntity(name = name))
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch {
            if (note.isLocked) {
                NoteSecrets.forget(getApplication(), note.id)
            }
            database.noteDao().deleteById(note.id)
        }
    }

    fun moveNote(noteId: Long, folderId: Long) {
        viewModelScope.launch {
            database.noteDao().updateFolderId(noteId, folderId)
        }
    }

    // The default "Non classé" folder is never offered for deletion (see
    // HomeScreen) — nothing here special-cases it, but callers must not
    // route it here.
    fun deleteFolder(folder: FolderWithNoteCount) {
        viewModelScope.launch {
            // The DB cascade removes the notes themselves, but not their
            // codes — those live outside Room, in their own preferences file,
            // and need clearing explicitly for any note here that was locked.
            database.noteDao().getByFolder(folder.id)
                .filter { it.isLocked }
                .forEach { note -> NoteSecrets.forget(getApplication(), note.id) }
            database.folderDao().deleteById(folder.id)
        }
    }
}
