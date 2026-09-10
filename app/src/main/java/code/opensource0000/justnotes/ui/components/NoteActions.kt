package code.opensource0000.justnotes.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import code.opensource0000.justnotes.R
import code.opensource0000.justnotes.data.local.FolderEntity
import code.opensource0000.justnotes.data.local.NoteEntity

// What a long press on a note offers, and the dialogs behind it.
//
// The home screen and a folder's screen both show a list of notes and both
// offered exactly the same three-step flow — options, then move or delete —
// so both carried the same three pieces of state and the same three dialog
// blocks, copied word for word. Any change to the menu had to be made twice,
// and the day it was made once the two screens would quietly disagree.
class NoteActionsState {
    // Only one of these is ever non-null: each step hands over to the next.
    var optionsFor by mutableStateOf<NoteEntity?>(null)
        internal set
    internal var moveTarget by mutableStateOf<NoteEntity?>(null)
    internal var deleteTarget by mutableStateOf<NoteEntity?>(null)

    fun show(note: NoteEntity) {
        optionsFor = note
    }
}

@Composable
fun rememberNoteActionsState(): NoteActionsState = remember { NoteActionsState() }

@Composable
fun NoteActionDialogs(
    state: NoteActionsState,
    folders: List<FolderEntity>,
    onMove: (noteId: Long, folderId: Long) -> Unit,
    onDelete: (NoteEntity) -> Unit
) {
    state.optionsFor?.let { note ->
        NoteOptionsDialog(
            noteTitle = note.title.ifBlank { stringResource(R.string.editor_title_placeholder) },
            onDismiss = { state.optionsFor = null },
            onMove = {
                state.moveTarget = note
                state.optionsFor = null
            },
            onDelete = {
                state.deleteTarget = note
                state.optionsFor = null
            }
        )
    }

    state.moveTarget?.let { note ->
        MoveToFolderDialog(
            folders = folders,
            currentFolderId = note.folderId,
            onDismiss = { state.moveTarget = null },
            onSelect = { folderId ->
                onMove(note.id, folderId)
                state.moveTarget = null
            }
        )
    }

    state.deleteTarget?.let { note ->
        DeleteConfirmDialog(
            title = stringResource(R.string.note_delete_confirm_title),
            message = stringResource(R.string.note_delete_confirm_message),
            onDismiss = { state.deleteTarget = null },
            onConfirm = {
                onDelete(note)
                state.deleteTarget = null
            }
        )
    }
}
