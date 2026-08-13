package code.opensource0000.justnotes.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import code.opensource0000.justnotes.data.local.FolderEntity
import code.opensource0000.justnotes.data.local.FolderWithNoteCount
import code.opensource0000.justnotes.data.local.JustNotesDatabase
import code.opensource0000.justnotes.data.local.NoteEntity
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
}
