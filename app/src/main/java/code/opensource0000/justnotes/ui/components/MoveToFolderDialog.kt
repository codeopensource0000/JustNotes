package code.opensource0000.justnotes.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import code.opensource0000.justnotes.R
import code.opensource0000.justnotes.data.local.FolderEntity

// Opened from NoteOptionsDialog's "move" action — a flat radio list of every
// folder, selecting one moves the note there immediately (no separate confirm
// step; picking is the confirmation).
@Composable
fun MoveToFolderDialog(
    folders: List<FolderEntity>,
    currentFolderId: Long,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_picker_title)) },
        text = {
            Column {
                folders.forEach { folder ->
                    RadioRow(
                        text = folder.name,
                        selected = folder.id == currentFolderId,
                        onClick = { onSelect(folder.id) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
