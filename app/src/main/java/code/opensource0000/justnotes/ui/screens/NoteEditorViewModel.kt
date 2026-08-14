package code.opensource0000.justnotes.ui.screens

import android.app.Application
import android.security.keystore.UserNotAuthenticatedException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import code.opensource0000.justnotes.data.local.FolderEntity
import code.opensource0000.justnotes.data.local.JustNotesDatabase
import code.opensource0000.justnotes.data.local.NoteEntity
import code.opensource0000.justnotes.security.NoteEncryption
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

class NoteEditorViewModel(private val application: Application) : AndroidViewModel(application) {

    private val database = JustNotesDatabase.getInstance(application)
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
    var folderId by mutableStateOf(0L)
        private set

    var folderName by mutableStateOf("")
        private set

    // All folders, for the picker dialog — plain list (no note counts, unlike
    // HomeScreen's Folders tab), since that number would just be noise here.
    val folders: StateFlow<List<FolderEntity>> = database.folderDao()
        .observeAll()
        .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), initialValue = emptyList())

    fun selectFolder(id: Long, name: String) {
        folderId = id
        folderName = name
    }

    fun createFolderAndSelect(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val newId = database.folderDao().insert(FolderEntity(name = name))
            selectFolder(newId, name)
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
            val folder = (if (overrideFolderId > 0) database.folderDao().getById(overrideFolderId) else null)
                ?: database.folderDao().getDefaultFolder()
                ?: return@launch
            folderId = folder.id
            folderName = folder.name
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

    // True when the Keystore refused to encrypt/decrypt because the last
    // system authentication is too old (or never happened this session) —
    // the screen should show a system confirm-identity prompt, then call
    // onSystemAuthSucceeded() to retry whatever was interrupted.
    var needsSystemAuth by mutableStateOf(false)
        private set

    private var pendingRetry: (() -> Unit)? = null

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

    val isDirty: Boolean
        get() = title != lastSavedTitle ||
            content != lastSavedContent ||
            isLocked != lastSavedIsLocked ||
            folderId != lastSavedFolderId

    fun onSystemAuthSucceeded() {
        needsSystemAuth = false
        val retry = pendingRetry
        pendingRetry = null
        retry?.invoke()
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

    // Wraps the selected text in a Markdown marker (bold/italic). With an
    // actual selection, wraps it directly. With no selection, an empty pair
    // like "****" can't be hidden by the live-preview transform (it requires
    // at least one character inside to recognise the span at all) and would
    // show literal asterisks until something is typed — so instead this
    // inserts a placeholder word, already selected, which hides immediately
    // and gets replaced the moment the user starts typing.
    private fun wrapSelection(marker: String, placeholderForEmpty: String) {
        val value = contentField
        val text = value.text
        val selection = value.selection
        if (selection.collapsed) {
            val newText = text.substring(0, selection.start) +
                marker + placeholderForEmpty + marker +
                text.substring(selection.start)
            val placeholderStart = selection.start + marker.length
            contentField = TextFieldValue(
                newText,
                TextRange(placeholderStart, placeholderStart + placeholderForEmpty.length)
            )
            return
        }
        val newText = text.substring(0, selection.start) +
            marker +
            text.substring(selection.start, selection.end) +
            marker +
            text.substring(selection.end)
        contentField = TextFieldValue(newText, TextRange(selection.end + marker.length * 2))
    }

    // Inserts a Markdown line prefix (heading/bullet/checkbox) at the start
    // of the line the cursor is currently on.
    private fun prefixCurrentLine(prefix: String) {
        val value = contentField
        val text = value.text
        val cursor = value.selection.start
        val newlineIndex = if (cursor == 0) -1 else text.lastIndexOf('\n', cursor - 1)
        val lineStart = if (newlineIndex == -1) 0 else newlineIndex + 1
        val newText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
        contentField = TextFieldValue(newText, TextRange(cursor + prefix.length))
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
        if (isLocked) {
            val id = editingNoteId ?: return
            // Turning protection off: this note's code and key become
            // meaningless once nothing is encrypted with them — clear both
            // rather than leaving stale secrets around for no reason.
            PinManager.forNote(application, id).clearPin()
            NoteEncryption.deleteKey(id)
            isLocked = false
            return
        }
        val id = editingNoteId
        if (id == null) {
            save(onFinished = { editingNoteId?.let(::lockNoteWithId) })
            return
        }
        lockNoteWithId(id)
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

    // Inserts a recognized phrase at the cursor, adding a separating space
    // only when needed so consecutive utterances don't run into each other.
    private fun insertDictatedText(text: String) {
        val value = contentField
        val cursor = value.selection.start
        val needsLeadingSpace = cursor > 0 && !value.text[cursor - 1].isWhitespace()
        val insertion = (if (needsLeadingSpace) " " else "") + text + " "
        val newText = value.text.substring(0, cursor) + insertion + value.text.substring(cursor)
        contentField = TextFieldValue(newText, TextRange(cursor + insertion.length))
    }

    override fun onCleared() {
        super.onCleared()
        if (isListening) {
            dictationManager.stopListening()
        }
    }

    // Called once, when the editor opens for an existing note. Safe to call
    // multiple times with the same id (e.g. on recomposition): it only loads once.
    fun load(noteId: Long) {
        if (noteId <= 0 || editingNoteId == noteId) return
        performLoad(noteId)
    }

    private fun performLoad(noteId: Long) {
        viewModelScope.launch {
            val note = database.noteDao().getById(noteId) ?: return@launch
            editingNoteId = note.id
            editingCreatedAt = note.createdAt
            isLocked = note.isLocked
            title = note.title
            folderId = note.folderId
            folderName = database.folderDao().getById(note.folderId)?.name ?: ""
            if (!note.isLocked) {
                contentField = TextFieldValue(note.content)
                lastSavedTitle = title
                lastSavedContent = content
                lastSavedIsLocked = isLocked
                lastSavedFolderId = folderId
                return@launch
            }
            try {
                // Decryption is fast but still real crypto work; keep it off
                // the main thread like every other Keystore/PBKDF2 operation.
                val decrypted = withContext(Dispatchers.Default) { NoteEncryption.decrypt(note.id, note.content) }
                contentField = TextFieldValue(decrypted)
                lastSavedTitle = title
                lastSavedContent = content
                lastSavedIsLocked = isLocked
                lastSavedFolderId = folderId
            } catch (e: UserNotAuthenticatedException) {
                // The biometric prompt on the secondary-lock screen usually
                // already satisfies this; this only triggers when the code
                // fallback was used instead, or the validity window lapsed.
                needsSystemAuth = true
                pendingRetry = { performLoad(noteId) }
            }
        }
    }

    fun save(onFinished: () -> Unit) {
        if (saveInFlight) return
        saveInFlight = true
        performSave(onFinished)
    }

    private fun performSave(onFinished: () -> Unit) {
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                // The in-memory content is always plain text; encrypt it only
                // for the copy that actually reaches the database. isLocked
                // can only be true once editingNoteId is set (toggleLocked()
                // requires the note to already be saved), so this id is safe.
                val storedContent = if (isLocked) {
                    val id = editingNoteId ?: return@launch
                    try {
                        withContext(Dispatchers.Default) { NoteEncryption.encrypt(id, content) }
                    } catch (e: UserNotAuthenticatedException) {
                        needsSystemAuth = true
                        pendingRetry = { performSave(onFinished) }
                        return@launch
                    }
                } else {
                    content
                }
                val currentId = editingNoteId
                if (currentId == null) {
                    // folderId is already resolved by initializeNewNoteFolderIfNeeded()
                    // (the default folder, unless the user picked a different one).
                    val insertedId = database.noteDao().insert(
                        NoteEntity(
                            folderId = folderId,
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
                    editingCreatedAt = now
                } else {
                    database.noteDao().update(
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
                lastSavedTitle = title
                lastSavedContent = content
                lastSavedIsLocked = isLocked
                lastSavedFolderId = folderId
                onFinished()
            } finally {
                saveInFlight = false
            }
        }
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
            if (isLocked) {
                PinManager.forNote(application, id).clearPin()
                NoteEncryption.deleteKey(id)
            }
            database.noteDao().deleteById(id)
            onFinished()
        }
    }
}
