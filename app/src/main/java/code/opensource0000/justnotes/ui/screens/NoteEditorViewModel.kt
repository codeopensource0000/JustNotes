package code.opensource0000.justnotes.ui.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import code.opensource0000.justnotes.data.local.JustNotesDatabase
import code.opensource0000.justnotes.data.local.NoteEntity
import code.opensource0000.justnotes.security.NoteEncryption
import code.opensource0000.justnotes.security.PinManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NoteEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val database = JustNotesDatabase.getInstance(application)
    private val secondaryPinManager = PinManager.forSecondary(application)

    // Holding Compose State directly in the ViewModel (rather than a Flow)
    // keeps this simple: title/content only ever change from this screen,
    // there is no other source of truth to stay in sync with.
    var title by mutableStateOf("")
        private set

    // Always the plain, readable text in memory — encryption is applied only
    // at the storage boundary (see save()/load()), never to in-editor state.
    var content by mutableStateOf("")
        private set

    var isLocked by mutableStateOf(false)
        private set

    // Null means "this is a new, not-yet-saved note". Set once an existing
    // note has been loaded, or once a new note has been inserted for the
    // first time, so a later save() always updates instead of inserting again.
    private var editingNoteId: Long? = null
    private var editingFolderId: Long = 0
    private var editingCreatedAt: Long = 0

    // Guards against a second save() firing (e.g. a double tap on the save
    // button) while the first one is still in flight, which used to cause
    // duplicate notes: the first insert hadn't updated editingNoteId yet,
    // so the second call still thought it was inserting a brand new note.
    private var saveInFlight = false

    fun onTitleChange(value: String) {
        title = value
    }

    fun onContentChange(value: String) {
        content = value
    }

    // A note can only be locked if a secondary code already exists — otherwise
    // it could get locked with no code able to ever open it again.
    fun toggleLocked() {
        if (!isLocked && !secondaryPinManager.hasPin()) return
        isLocked = !isLocked
    }

    // Called once, when the editor opens for an existing note. Safe to call
    // multiple times with the same id (e.g. on recomposition): it only loads once.
    fun load(noteId: Long) {
        if (noteId <= 0 || editingNoteId == noteId) return
        viewModelScope.launch {
            val note = database.noteDao().getById(noteId) ?: return@launch
            editingNoteId = note.id
            editingFolderId = note.folderId
            editingCreatedAt = note.createdAt
            isLocked = note.isLocked
            title = note.title
            content = if (note.isLocked) {
                // Decryption is fast but still real crypto work; keep it off
                // the main thread like every other Keystore/PBKDF2 operation.
                withContext(Dispatchers.Default) { NoteEncryption.decrypt(note.content) }
            } else {
                note.content
            }
        }
    }

    fun save(onFinished: () -> Unit) {
        if (saveInFlight) return
        saveInFlight = true
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                // The in-memory content is always plain text; encrypt it only
                // for the copy that actually reaches the database.
                val storedContent = if (isLocked) {
                    withContext(Dispatchers.Default) { NoteEncryption.encrypt(content) }
                } else {
                    content
                }
                val currentId = editingNoteId
                if (currentId == null) {
                    // New note: no folder chosen yet in the UI, so it goes into
                    // the default catch-all folder, as documented in the app design.
                    val defaultFolderId = database.folderDao().getDefaultFolder()?.id
                        ?: return@launch
                    val insertedId = database.noteDao().insert(
                        NoteEntity(
                            folderId = defaultFolderId,
                            title = title,
                            content = storedContent,
                            isLocked = isLocked,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                    // From here on this note exists in the database: remember
                    // its id so a follow-up save() updates it instead of
                    // inserting a second row.
                    editingNoteId = insertedId
                    editingFolderId = defaultFolderId
                    editingCreatedAt = now
                } else {
                    database.noteDao().update(
                        NoteEntity(
                            id = currentId,
                            folderId = editingFolderId,
                            title = title,
                            content = storedContent,
                            isLocked = isLocked,
                            createdAt = editingCreatedAt,
                            updatedAt = now
                        )
                    )
                }
                onFinished()
            } finally {
                saveInFlight = false
            }
        }
    }
}
