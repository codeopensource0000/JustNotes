package code.opensource0000.justnotes.ui.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import code.opensource0000.justnotes.editing.EditedText
import code.opensource0000.justnotes.editing.MarkdownEditing
import code.opensource0000.justnotes.data.NotesRepository
import code.opensource0000.justnotes.data.RoomNotesRepository
import code.opensource0000.justnotes.data.local.FolderEntity
import code.opensource0000.justnotes.data.local.NoteEntity
import code.opensource0000.justnotes.security.NoteEncryption
import code.opensource0000.justnotes.security.NoteKeySession
import code.opensource0000.justnotes.security.NoteSecrets
import code.opensource0000.justnotes.security.PinManager
import code.opensource0000.justnotes.settings.SettingsManager
import code.opensource0000.justnotes.stt.VoiceDictationManager
import code.opensource0000.justnotes.stt.VoskModelManager
import code.opensource0000.justnotes.stt.VoskModelState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException

class NoteEditorViewModel @JvmOverloads constructor(
    private val application: Application,
    // Defaulted so the reflection-based viewModel() factory keeps working, and
    // overridable so a test can hand in a fake instead of a real database.
    private val repository: NotesRepository = RoomNotesRepository.get(application)
) : AndroidViewModel(application) {

    private val settingsManager = SettingsManager.getInstance(application)
    private val voskModelManager = VoskModelManager.getInstance(application)
    private val dictationManager = VoiceDictationManager.getInstance(application)

    val isAutosaveEnabled: Boolean
        get() = settingsManager.autosaveEnabled.value

    // Holding Compose State directly in the ViewModel (rather than a Flow)
    // keeps this simple: title/content only ever change from this screen,
    // there is no other source of truth to stay in sync with.
    var title by mutableStateOf("")
        private set

    // TextFieldValue (rather than a plain String) also tracks the cursor
    // position/selection, which the formatting buttons below need to know
    // where to insert or wrap Markdown markers. Always plain, readable text —
    // encryption is applied only at the storage boundary (see save()/load()).
    var contentField by mutableStateOf(TextFieldValue(""))
        private set

    val content: String
        get() = contentField.text

    var isLocked by mutableStateOf(false)
        private set

    // The folder this note belongs to. For an existing note this comes from
    // load(); for a new one it's resolved to the default folder as soon as
    // the screen opens (initializeNewNoteFolderIfNeeded()), so the picker
    // always has a meaningful current value even before the first save.
    var folderId by mutableLongStateOf(0L)
        private set

    var folderName by mutableStateOf("")
        private set

    // Tracked alongside the name so the screen can show the localised label
    // for the catch-all folder rather than its frozen stored name.
    var folderIsDefault by mutableStateOf(false)
        private set

    // All folders, for the picker dialog — plain list (no note counts, unlike
    // HomeScreen's Folders tab), since that number would just be noise here.
    val folders: StateFlow<List<FolderEntity>> = repository.observeFolders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList()
        )

    fun selectFolder(folder: FolderEntity) {
        folderId = folder.id
        folderName = folder.name
        folderIsDefault = folder.isDefault
    }

    fun createFolderAndSelect(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val newId = repository.createFolder(name)
            folderId = newId
            folderName = name
            // A folder the user just created is never the catch-all one.
            folderIsDefault = false
        }
    }

    private var newNoteFolderResolved = false

    // Called from the screen alongside load() — a no-op for an existing note
    // (noteId > 0) or if this has already run once for this screen instance.
    // overrideFolderId lets "new note" opened from inside a folder (rather
    // than from Recents) land there directly, instead of the default folder.
    fun initializeNewNoteFolderIfNeeded(noteId: Long, overrideFolderId: Long = -1L) {
        if (noteId > 0 || newNoteFolderResolved) return
        newNoteFolderResolved = true
        viewModelScope.launch {
            val folder = (if (overrideFolderId > 0) repository.folder(overrideFolderId) else null)
                ?: repository.defaultFolder()
                ?: return@launch
            folderId = folder.id
            folderName = folder.name
            folderIsDefault = folder.isDefault
            lastSavedFolderId = folder.id
        }
    }

    // Null means "this is a new, not-yet-saved note". Set once an existing
    // note has been loaded, or once a new note has been inserted for the
    // first time, so a later save() always updates instead of inserting again.
    // Exposed read-only: the screen needs it to navigate to this note's own
    // PIN-setup flow when locking it for the first time.
    private var editingNoteId: Long? = null
    val currentNoteId: Long? get() = editingNoteId
    private var editingCreatedAt: Long = 0

    // Guards against a second save() firing (e.g. a double tap on the save
    // button) while the first one is still in flight, which used to cause
    // duplicate notes: the first insert hadn't updated editingNoteId yet,
    // so the second call still thought it was inserting a brand new note.
    private var saveInFlight = false

    // Set when a locked note's stored bytes could not be turned back into
    // text. With the key derived from the code the user just typed, a wrong
    // key is no longer possible here, so in practice this means damaged data —
    // rare, but it must not be silent: the editor would otherwise show an
    // empty note and cheerfully save that emptiness over the real content.
    // Everything that writes checks this flag first.
    var contentUnreadable by mutableStateOf(false)
        private set

    // Snapshot of title/content/isLocked as of the last successful load or
    // save. isDirty compares the live editor state against it; reading these
    // properties here (rather than a separate flag toggled on every change)
    // means it can never drift out of sync with what's actually on screen.
    // isLocked matters too: toggling only the lock with no text change used
    // to leave isDirty false, so the toggle silently never got saved.
    private var lastSavedTitle = ""
    private var lastSavedContent = ""
    private var lastSavedIsLocked = false
    private var lastSavedFolderId = 0L

    // Always false once the content could not be read: there is nothing on
    // screen worth keeping, so leaving must not offer to save it.
    val isDirty: Boolean
        get() = !contentUnreadable && (
            title != lastSavedTitle ||
                content != lastSavedContent ||
                isLocked != lastSavedIsLocked ||
                folderId != lastSavedFolderId
            )

    // Refreshes the snapshot isDirty compares against, after a load or a save.
    private fun snapshotSavedState() {
        lastSavedTitle = title
        lastSavedContent = content
        lastSavedIsLocked = isLocked
        lastSavedFolderId = folderId
    }

    fun onTitleChange(value: String) {
        title = value
    }

    fun onContentChange(value: TextFieldValue) {
        contentField = value
    }

    // placeholder is passed in from the screen (rather than hardcoded here)
    // since it's user-facing, localized text — the ViewModel shouldn't own
    // that string.
    fun applyBold(placeholder: String) = wrapSelection("**", placeholder)

    fun applyItalic(placeholder: String) = wrapSelection("*", placeholder)

    fun applyHeading() = prefixCurrentLine("# ")

    fun applyBulletList() = prefixCurrentLine("- ")

    fun applyChecklist() = prefixCurrentLine("- [ ] ")

    // Thin adapters over MarkdownEditing: the index arithmetic lives there,
    // where it can be tested without a phone.
    private fun wrapSelection(marker: String, placeholderForEmpty: String) {
        val value = contentField
        contentField = MarkdownEditing.wrapSelection(
            text = value.text,
            selectionStart = value.selection.start,
            selectionEnd = value.selection.end,
            marker = marker,
            placeholderForEmpty = placeholderForEmpty
        ).toTextFieldValue()
    }

    private fun prefixCurrentLine(prefix: String) {
        val value = contentField
        contentField = MarkdownEditing
            .prefixCurrentLine(value.text, value.selection.start, prefix)
            .toTextFieldValue()
    }

    // One-shot signal: this note has just been marked locked but has no PIN
    // of its own yet, so the screen needs to navigate to that note's own
    // PIN-setup flow (mandatory — see PinSetupScreen's blockSystemBack).
    // isLocked is set true optimistically right away; the setup flow can't be
    // escaped without completing, so it's never left in a locked-but-codeless state.
    var requestNotePinSetup by mutableStateOf(false)
        private set

    fun onNotePinSetupRequested() {
        requestNotePinSetup = false
    }

    // Each note needs a saved id before it can be locked — that id is what
    // both its PIN and its encryption key are keyed by. Rather than blocking
    // with a "save first" message (which used to mean tapping the toolbar's
    // Save button and being kicked back out to Home), a brand new note is
    // saved silently here, staying on this screen throughout.
    fun toggleLocked() {
        if (contentUnreadable) return
        if (isLocked) {
            val id = editingNoteId ?: return
            unlockNote(id)
            return
        }
        val id = editingNoteId
        if (id == null) {
            save(onFinished = { editingNoteId?.let(::lockNoteWithId) })
            return
        }
        lockNoteWithId(id)
    }

    // Turning protection off. The order below is the whole point: the
    // database still holds the encrypted copy and the plain text only exists
    // in memory, so this note's code and key have to outlive the write that
    // puts the plain text back.
    //
    // Clearing them first — as this used to — meant that leaving without
    // saving ("Ne pas enregistrer", or the app being killed) left behind a
    // ciphertext whose key no longer existed, still marked locked, with its
    // PIN already wiped. The note was then unreadable *and* unopenable, with
    // nothing on screen to say so.
    //
    // Consequence, accepted deliberately: unlocking saves the note there and
    // then, in-progress text edits included. Locking a brand new note already
    // works that way (see the comment above toggleLocked), so the editor
    // stays consistent with itself. Keeping the key instead of destroying it
    // would avoid the save, but then re-locking the note later would silently
    // reuse the old code the user believes they removed — worse.
    private fun unlockNote(id: Long) {
        if (saveInFlight || contentUnreadable) return
        saveInFlight = true
        isLocked = false
        viewModelScope.launch {
            try {
                // isLocked is false by now, so this stores plain text.
                persistNote(content)
                // Only here is the code no longer protecting anything. Since
                // the code *is* the key, clearing it is the whole cleanup —
                // there is no second secret to keep in step any more. If the
                // write above threw we never get this far, and the note stays
                // locked and readable: the failure leans the safe way.
                withContext(Dispatchers.Default) {
                    NoteSecrets.forget(application, id)
                }
            } finally {
                saveInFlight = false
            }
        }
    }

    private fun lockNoteWithId(id: Long) {
        isLocked = true
        if (!PinManager.forNote(application, id).hasPin()) {
            requestNotePinSetup = true
        }
    }

    var isListening by mutableStateOf(false)
        private set

    enum class MicBlockReason { MODEL_NOT_READY }

    // One-shot signal: the screen shows a message once, then calls
    // onMicBlockMessageShown() to clear it so it doesn't reappear on its
    // own after a recomposition.
    var micBlockReason by mutableStateOf<MicBlockReason?>(null)
        private set

    fun onMicBlockMessageShown() {
        micBlockReason = null
    }

    // Called once the screen has already confirmed the RECORD_AUDIO
    // permission is granted; only the voice model's readiness is checked here.
    fun toggleVoiceDictation() {
        if (isListening) {
            isListening = false
            dictationManager.stopListening()
            return
        }
        val language = settingsManager.dictationLanguage.value
        if (voskModelManager.state(language).value != VoskModelState.READY) {
            micBlockReason = MicBlockReason.MODEL_NOT_READY
            return
        }
        isListening = true
        viewModelScope.launch(Dispatchers.IO) {
            dictationManager.startListening(
                modelPath = voskModelManager.modelDir(language).absolutePath,
                onUtterance = ::insertDictatedText,
                onError = { isListening = false }
            )
        }
    }

    private fun insertDictatedText(phrase: String) {
        val value = contentField
        contentField = MarkdownEditing
            .insertDictated(value.text, value.selection.start, phrase)
            .toTextFieldValue()
    }

    override fun onCleared() {
        super.onCleared()
        if (isListening) {
            dictationManager.stopListening()
        }
        // The derived key must not outlive the screen that needed it: leaving
        // the editor means the next visit has to go through the lock screen
        // and type the code again.
        editingNoteId?.let(NoteKeySession::forget)
    }

    // Called once, when the editor opens for an existing note. Safe to call
    // multiple times with the same id (e.g. on recomposition): it only loads once.
    fun load(noteId: Long) {
        if (noteId <= 0 || editingNoteId == noteId) return
        performLoad(noteId)
    }

    private fun performLoad(noteId: Long) {
        viewModelScope.launch {
            val note = repository.note(noteId) ?: return@launch
            editingNoteId = note.id
            editingCreatedAt = note.createdAt
            isLocked = note.isLocked
            title = note.title
            folderId = note.folderId
            val folder = repository.folder(note.folderId)
            folderName = folder?.name ?: ""
            folderIsDefault = folder?.isDefault == true
            if (!note.isLocked) {
                contentField = TextFieldValue(note.content)
                snapshotSavedState()
                return@launch
            }
            // Put there by the secondary-lock screen when the user typed this
            // note's code. Arriving here without it should be impossible —
            // every route to a locked note goes through that screen — so treat
            // it as unreadable rather than showing a blank note that could
            // then be saved over the real one.
            val key = NoteKeySession.get(note.id)
            if (key == null) {
                contentUnreadable = true
                return@launch
            }
            try {
                // Decryption is fast but still real crypto work; keep it off
                // the main thread like every other PBKDF2/cipher operation.
                val decrypted = withContext(Dispatchers.Default) {
                    NoteEncryption.decrypt(key, note.content)
                }
                contentField = TextFieldValue(decrypted)
                snapshotSavedState()
            } catch (_: GeneralSecurityException) {
                // Covers the whole family at once: a failed GCM tag check
                // (AEADBadTagException), a malformed key, anything the cipher
                // refuses. None of them are recoverable, and all of them mean
                // the same thing to the person holding the phone.
                contentUnreadable = true
            }
        }
    }

    fun save(onFinished: () -> Unit) {
        // Nothing on screen is worth writing, and writing it would overwrite
        // the stored bytes we failed to read. Let the caller navigate away.
        if (contentUnreadable) {
            onFinished()
            return
        }
        if (saveInFlight) return
        saveInFlight = true
        performSave(onFinished)
    }

    private fun performSave(onFinished: () -> Unit) {
        viewModelScope.launch {
            try {
                // The in-memory content is always plain text; encrypt it only
                // for the copy that actually reaches the database. isLocked
                // can only be true once editingNoteId is set (toggleLocked()
                // requires the note to already be saved), so this id is safe.
                val storedContent = if (isLocked) {
                    val id = editingNoteId ?: return@launch
                    // No key means no way to write this note back without
                    // destroying it: bail out rather than store plain text in
                    // a row flagged as locked.
                    val key = NoteKeySession.get(id) ?: return@launch
                    withContext(Dispatchers.Default) { NoteEncryption.encrypt(key, content) }
                } else {
                    content
                }
                persistNote(storedContent)
                onFinished()
            } finally {
                saveInFlight = false
            }
        }
    }

    // The database half of a save, shared by performSave() and unlockNote():
    // insert the first time, update from then on, then refresh the snapshot
    // isDirty compares against. storedContent is a parameter rather than read
    // from `content` directly because only the caller knows whether it was
    // supposed to be encrypted on the way in.
    private suspend fun persistNote(storedContent: String) {
        val now = System.currentTimeMillis()
        val currentId = editingNoteId
        if (currentId == null) {
            // folderId is already resolved by initializeNewNoteFolderIfNeeded()
            // (the default folder, unless the user picked a different one).
            val insertedId = repository.insertNote(
                NoteEntity(
                    folderId = folderId,
                    title = title,
                    content = storedContent,
                    isLocked = isLocked,
                    createdAt = now,
                    updatedAt = now
                )
            )
            // From here on this note exists in the database: remember its id
            // so a follow-up save() updates it instead of inserting a second row.
            editingNoteId = insertedId
            editingCreatedAt = now
        } else {
            repository.updateNote(
                NoteEntity(
                    id = currentId,
                    folderId = folderId,
                    title = title,
                    content = storedContent,
                    isLocked = isLocked,
                    createdAt = editingCreatedAt,
                    updatedAt = now
                )
            )
        }
        snapshotSavedState()
    }

    // A note that was never saved (editingNoteId still null) has nothing in
    // the database to remove — just finish, equivalent to discarding it.
    fun deleteNote(onFinished: () -> Unit) {
        val id = editingNoteId
        if (id == null) {
            onFinished()
            return
        }
        viewModelScope.launch {
            // The repository owns the rule that a locked note's code, shortcut
            // and key go with its row.
            repository.deleteNote(
                NoteEntity(
                    id = id,
                    folderId = folderId,
                    title = title,
                    content = "",
                    isLocked = isLocked,
                    createdAt = editingCreatedAt,
                    updatedAt = editingCreatedAt
                )
            )
            onFinished()
        }
    }
}

private fun EditedText.toTextFieldValue() =
    TextFieldValue(text, TextRange(selectionStart, selectionEnd))
