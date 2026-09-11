package code.opensource0000.justnotes.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import code.opensource0000.justnotes.R
import code.opensource0000.justnotes.ui.components.EmptyState
import code.opensource0000.justnotes.ui.components.NoteActionDialogs
import code.opensource0000.justnotes.ui.components.NoteRow
import code.opensource0000.justnotes.ui.components.rememberNoteActions
import code.opensource0000.justnotes.ui.components.folderLabel

@Composable
fun FolderScreen(
    onBack: () -> Unit,
    onOpenNote: (noteId: Long, isLocked: Boolean) -> Unit,
    onCreateNote: () -> Unit,
    viewModel: FolderViewModel
) {
    val notes by viewModel.notes.collectAsState()
    val allFolders by viewModel.allFolders.collectAsState()

    val noteActions = rememberNoteActions()
    NoteActionDialogs(
        state = noteActions,
        folders = allFolders,
        onMove = viewModel::moveNote,
        onDelete = viewModel::deleteNote
    )

    // Scaffold (rather than a bare Column) paints the themed background and
    // keeps content clear of the status bar / notch, matching every other screen.
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateNote) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.home_new_note))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.editor_back))
                }
                Text(
                    text = folderLabel(viewModel.folderName, viewModel.folderIsDefault),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (notes.isEmpty()) {
                EmptyState(icon = Icons.Filled.Description, text = stringResource(R.string.home_empty_recent))
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(notes) { note ->
                        NoteRow(
                            note,
                            onClick = { onOpenNote(note.id, note.isLocked) },
                            onLongClick = { noteActions.show(note) }
                        )
                    }
                }
            }
        }
    }
}
