package code.opensource0000.justnotes.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import code.opensource0000.justnotes.data.NotesRepository
import code.opensource0000.justnotes.data.RoomNotesRepository
import code.opensource0000.justnotes.data.local.FolderEntity
import code.opensource0000.justnotes.data.local.FolderWithNoteCount
import code.opensource0000.justnotes.data.local.NoteEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// AndroidViewModel (rather than plain ViewModel) gives us access to an
// Application context, which the repository needs to open its SQLite file.
class HomeViewModel @JvmOverloads constructor(
    application: Application,
    // Defaulted so the reflection-based viewModel() factory keeps working, and
    // overridable so a test can hand in a fake instead of a real database.
    private val repository: NotesRepository = RoomNotesRepository.get(application)
) : AndroidViewModel(application) {

    // stateIn turns the repository's Flow into a StateFlow: Compose can read
    // its .value directly, and it keeps the latest list in memory even when
    // briefly nobody is observing (e.g. during a screen rotation).
    val recentNotes: StateFlow<List<NoteEntity>> = repository.observeRecentNotes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList()
        )

    val folders: StateFlow<List<FolderWithNoteCount>> = repository.observeFoldersWithNoteCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList()
        )

    // Flat list (no note counts) for the "move to folder" picker.
    val allFolders: StateFlow<List<FolderEntity>> = repository.observeFolders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList()
        )

    init {
        // Runs on every app start; does nothing once the default folder already
        // exists, so it is safe to call unconditionally instead of only on
        // first install (simpler than wiring a Room creation callback).
        viewModelScope.launch { repository.ensureDefaultFolder() }
    }

    fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.createFolder(name) }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch { repository.deleteNote(note) }
    }

    fun moveNote(noteId: Long, folderId: Long) {
        viewModelScope.launch { repository.moveNote(noteId, folderId) }
    }

    // The default folder is never offered for deletion (see HomeScreen):
    // nothing here special-cases it, but callers must not route it here.
    fun deleteFolder(folder: FolderWithNoteCount) {
        viewModelScope.launch { repository.deleteFolder(folder.id) }
    }
}
