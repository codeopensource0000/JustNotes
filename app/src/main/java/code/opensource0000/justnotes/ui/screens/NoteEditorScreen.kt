package code.opensource0000.justnotes.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import code.opensource0000.justnotes.R

// noteId <= 0 means "new note"; a real id means "editing an existing note".
@Composable
fun NoteEditorScreen(
    noteId: Long,
    onBack: () -> Unit,
    viewModel: NoteEditorViewModel = viewModel()
) {
    // LaunchedEffect(noteId) runs once per distinct noteId value: exactly
    // what we want, since load() itself already guards against reloading.
    LaunchedEffect(noteId) {
        viewModel.load(noteId)
    }

    // Scaffold (rather than a bare Column) paints the themed background and
    // keeps content clear of the status bar / notch, since the app draws
    // edge-to-edge; HomeScreen gets both for free the same way.
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.editor_back))
                }
                Text(
                    text = stringResource(R.string.editor_header_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = viewModel::toggleLocked) {
                    Icon(
                        imageVector = if (viewModel.isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                        contentDescription = stringResource(
                            if (viewModel.isLocked) R.string.editor_lock_on else R.string.editor_lock_off
                        )
                    )
                }
                IconButton(onClick = { viewModel.save(onFinished = onBack) }) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.editor_save))
                }
            }

            TextField(
                value = viewModel.title,
                onValueChange = viewModel::onTitleChange,
                placeholder = { Text(stringResource(R.string.editor_title_placeholder)) },
                textStyle = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            )

            TextField(
                value = viewModel.content,
                onValueChange = viewModel::onContentChange,
                placeholder = { Text(stringResource(R.string.editor_content_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            )
        }
    }
}
