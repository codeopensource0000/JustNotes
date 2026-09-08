package code.opensource0000.justnotes.ui.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import code.opensource0000.justnotes.data.local.FolderEntity
import code.opensource0000.justnotes.data.local.JustNotesDatabase
import code.opensource0000.justnotes.data.local.NoteEntity
import code.opensource0000.justnotes.security.NoteSecrets
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// Always constructed via a custom factory (SimpleViewModelFactory) with a
// specific folderId, so — unlike the other screens' ViewModels — this one
// has no @JvmOverloads/default-args concern: the reflection-based default
// viewModel() factory is never used to create it.
class FolderViewModel(application: Application, private val folderId: Long) : AndroidViewModel(application) {

    private val database = JustNotesDatabase.getInstance(application)

    val notes: StateFlow<List<NoteEntity>> = database.noteDao()
        .observeByFolder(folderId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList()
        )

    // Flat list of every folder (not just this one's siblings), for the
    // "move to folder" picker — a note can move anywhere, not just out of
    // the one it's currently in.
    val allFolders: StateFlow<List<FolderEntity>> = database.folderDao()
        .observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList()
        )

    var folderName by mutableStateOf("")
        private set

    var folderIsDefault by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            val folder = database.folderDao().getById(folderId)
            folderName = folder?.name ?: ""
            folderIsDefault = folder?.isDefault == true
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

    fun moveNote(noteId: Long, targetFolderId: Long) {
        viewModelScope.launch {
            database.noteDao().updateFolderId(noteId, targetFolderId)
        }
    }
}
