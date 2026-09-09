package code.opensource0000.justnotes.ui.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import code.opensource0000.justnotes.data.NotesRepository
import code.opensource0000.justnotes.data.RoomNotesRepository
import code.opensource0000.justnotes.data.local.FolderEntity
import code.opensource0000.justnotes.data.local.NoteEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Always constructed via a custom factory (SimpleViewModelFactory) with a
// specific folderId, so — unlike the other screens' ViewModels — this one
// needs no default-argument juggling: the reflection-based default
// viewModel() factory is never used to create it.
class FolderViewModel(
    application: Application,
    private val folderId: Long,
    private val repository: NotesRepository = RoomNotesRepository.get(application)
) : AndroidViewModel(application) {

    val notes: StateFlow<List<NoteEntity>> = repository.observeNotesInFolder(folderId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList()
        )

    // Flat list of every folder (not just this one's siblings), for the
    // "move to folder" picker — a note can move anywhere, not just out of
    // the one it's currently in.
    val allFolders: StateFlow<List<FolderEntity>> = repository.observeFolders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList()
        )

    var folderName by mutableStateOf("")
        private set

    // Tracked alongside the name so the screen can show the localised label
    // for the catch-all folder rather than its frozen stored name.
    var folderIsDefault by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val folder = repository.folder(folderId)
            folderName = folder?.name ?: ""
            folderIsDefault = folder?.isDefault == true
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch { repository.deleteNote(note) }
    }

    fun moveNote(noteId: Long, targetFolderId: Long) {
        viewModelScope.launch { repository.moveNote(noteId, targetFolderId) }
    }
}
