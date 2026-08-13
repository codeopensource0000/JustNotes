package code.opensource0000.justnotes.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import code.opensource0000.justnotes.R
import code.opensource0000.justnotes.data.local.FolderWithNoteCount
import code.opensource0000.justnotes.data.local.NoteEntity
import code.opensource0000.justnotes.ui.theme.JustNotesTheme

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
    onCreateNote: () -> Unit = {},
    onOpenNote: (noteId: Long, isLocked: Boolean) -> Unit = { _, _ -> },
    onOpenSettings: () -> Unit = {}
) {
    // remember keeps this value alive across recompositions; mutableIntStateOf
    // is a state holder specialised for Int, slightly more efficient than the
    // generic mutableStateOf.
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf(
        stringResource(R.string.home_tab_recent),
        stringResource(R.string.home_tab_folders)
    )

    // collectAsState subscribes to the ViewModel's StateFlow and turns its
    // latest value into Compose State: whenever the database changes, this
    // triggers a recomposition with the fresh list, automatically.
    val recentNotes by viewModel.recentNotes.collectAsState()
    val folders by viewModel.folders.collectAsState()

    Scaffold(
        modifier = modifier,
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
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 16.dp)
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                }
            }

            SecondaryTabRow(selectedTabIndex = selectedTabIndex) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> RecentNotesList(recentNotes, onOpenNote)
                else -> FoldersList(folders)
            }
        }
    }
}

@Composable
private fun RecentNotesList(notes: List<NoteEntity>, onOpenNote: (Long, Boolean) -> Unit) {
    // LazyColumn only creates the rows currently visible on screen (plus a
    // small buffer), instead of all of them at once like a plain Column would.
    // Essential once a folder can contain hundreds of notes.
    LazyColumn {
        items(notes) { note -> NoteRow(note, onClick = { onOpenNote(note.id, note.isLocked) }) }
    }
}

@Composable
private fun FoldersList(folders: List<FolderWithNoteCount>) {
    LazyColumn {
        items(folders) { folder -> FolderRow(folder) }
    }
}

@Composable
private fun NoteRow(note: NoteEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Description,
            // The adjacent text already describes the row, so this icon is purely
            // decorative for a sighted user; null tells screen readers to skip it.
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = note.title, style = MaterialTheme.typography.bodyMedium)
            Text(
                // TODO: once folder navigation exists, join in the folder name
                // here too (needs a query joining notes to folders).
                text = DateUtils.getRelativeTimeSpanString(
                    note.updatedAt,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS
                ).toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = if (note.isLocked) Icons.Filled.Lock else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (note.isLocked) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun FolderRow(folder: FolderWithNoteCount) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = folder.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${folder.noteCount} notes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// No @Preview here anymore: HomeScreen now requires a real HomeViewModel
// (backed by an Android Application context to open the database), which
// the lightweight Preview sandbox cannot provide. Verifying this screen now
// means running the app for real, on an emulator or device.
@Preview(showBackground = true)
@Composable
private fun HomeScreenTitlePreview() {
    JustNotesTheme {
        Text(text = "JustNotes", style = MaterialTheme.typography.headlineSmall)
    }
}
