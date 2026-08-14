package code.opensource0000.justnotes.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import code.opensource0000.justnotes.R

// Shared by the home screen's Folders tab and the editor's folder picker —
// both just need a name and somewhere to send it once confirmed.
@Composable
fun NewFolderDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val trimmed = name.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_new_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(stringResource(R.string.folder_name_placeholder)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(trimmed) },
                enabled = trimmed.isNotEmpty()
            ) {
                Text(stringResource(R.string.folder_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
