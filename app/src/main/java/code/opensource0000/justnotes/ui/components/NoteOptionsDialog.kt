package code.opensource0000.justnotes.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import code.opensource0000.justnotes.R

// The menu a long-press on a note opens: move it elsewhere, or delete it.
@Composable
fun NoteOptionsDialog(
    noteTitle: String,
    onDismiss: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(noteTitle) },
        text = {
            Column {
                OptionRow(
                    icon = Icons.AutoMirrored.Filled.DriveFileMove,
                    text = stringResource(R.string.note_move_to_folder),
                    onClick = onMove
                )
                OptionRow(
                    icon = Icons.Filled.DeleteOutline,
                    text = stringResource(R.string.action_delete),
                    onClick = onDelete,
                    tint = MaterialTheme.colorScheme.error
                )
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

@Composable
private fun OptionRow(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = text, color = tint)
    }
}
