package code.opensource0000.justnotes.ui.screens

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import code.opensource0000.justnotes.R
import code.opensource0000.justnotes.data.local.FolderWithNoteCount
import code.opensource0000.justnotes.data.local.NoteEntity
import code.opensource0000.justnotes.ui.components.DeleteConfirmDialog
import code.opensource0000.justnotes.ui.components.EmptyState
import code.opensource0000.justnotes.ui.components.NewFolderDialog
import code.opensource0000.justnotes.ui.components.NoteActionDialogs
import code.opensource0000.justnotes.ui.components.NoteRow
import code.opensource0000.justnotes.ui.components.folderLabel
import code.opensource0000.justnotes.ui.components.rememberNoteActions
import code.opensource0000.justnotes.ui.theme.WordmarkStyle

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
    onCreateNote: () -> Unit = {},
    onOpenNote: (noteId: Long, isLocked: Boolean) -> Unit = { _, _ -> },
    onOpenFolder: (folderId: Long) -> Unit = {},
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
    val allFolders by viewModel.allFolders.collectAsState()
    var showNewFolderDialog by remember { mutableStateOf(false) }

    // The note half of this is shared with FolderScreen; only folder deletion
    // is particular to this screen.
    val noteActions = rememberNoteActions()
    var folderDeleteTarget by remember { mutableStateOf<FolderWithNoteCount?>(null) }

    if (showNewFolderDialog) {
        NewFolderDialog(
            onDismiss = { showNewFolderDialog = false },
            onCreate = { name ->
                viewModel.createFolder(name)
                showNewFolderDialog = false
            }
        )
    }

    NoteActionDialogs(
        state = noteActions,
        folders = allFolders,
        onMove = viewModel::moveNote,
        onDelete = viewModel::deleteNote
    )

    folderDeleteTarget?.let { folder ->
        DeleteConfirmDialog(
            title = stringResource(R.string.folder_delete_confirm_title),
            message = stringResource(R.string.folder_delete_confirm_message, folder.name),
            onDismiss = { folderDeleteTarget = null },
            onConfirm = {
                viewModel.deleteFolder(folder)
                folderDeleteTarget = null
            }
        )
    }

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
            // Box (rather than a weighted Row) so the wordmark centers on the
            // full header width — a Row with only one side occupied by the
            // settings icon can't balance itself the way two-sided content can.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = WordmarkStyle,
                    modifier = Modifier.align(Alignment.Center)
                )
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
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
                0 -> RecentNotesList(
                    notes = recentNotes,
                    onOpenNote = onOpenNote,
                    onLongClickNote = { note -> noteActions.show(note) }
                )
                else -> FoldersList(
                    folders = folders,
                    onNewFolderClick = { showNewFolderDialog = true },
                    onOpenFolder = onOpenFolder,
                    onLongClickFolder = { folder ->
                        // The default folder always has to exist (notes need
                        // somewhere to land), so it's simply never offered here.
                        if (!folder.isDefault) folderDeleteTarget = folder
                    }
                )
            }
        }
    }
}

@Composable
private fun RecentNotesList(
    notes: List<NoteEntity>,
    onOpenNote: (Long, Boolean) -> Unit,
    onLongClickNote: (NoteEntity) -> Unit
) {
    if (notes.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Description,
            text = stringResource(R.string.home_empty_recent)
        )
        return
    }
    // LazyColumn only creates the rows currently visible on screen (plus a
    // small buffer), instead of all of them at once like a plain Column would.
    // Essential once a folder can contain hundreds of notes.
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(notes) { note ->
            NoteRow(
                note,
                onClick = { onOpenNote(note.id, note.isLocked) },
                onLongClick = { onLongClickNote(note) }
            )
        }
    }
}

@Composable
private fun FoldersList(
    folders: List<FolderWithNoteCount>,
    onNewFolderClick: () -> Unit,
    onOpenFolder: (Long) -> Unit,
    onLongClickFolder: (FolderWithNoteCount) -> Unit
) {
    // "New folder" always shows at the top, even with zero folders yet
    // (the default "Non classé" folder means this list is never truly empty
    // in practice, but nothing else here depends on that).
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        item { NewFolderRow(onClick = onNewFolderClick) }
        if (folders.isEmpty()) {
            item { EmptyState(icon = Icons.Filled.Folder, text = stringResource(R.string.home_empty_folders)) }
        } else {
            items(folders) { folder ->
                FolderRow(
                    folder,
                    onClick = { onOpenFolder(folder.id) },
                    onLongClick = { onLongClickFolder(folder) }
                )
            }
        }
    }
}

@Composable
private fun NewFolderRow(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.CreateNewFolder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.home_new_folder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun FolderRow(folder: FolderWithNoteCount, onClick: () -> Unit, onLongClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folderLabel(folder.name, folder.isDefault),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    // A plurals resource rather than "$n notes": the count was
                    // hardcoded in English and read "1 notes" at one.
                    text = pluralStringResource(
                        R.plurals.folder_note_count,
                        folder.noteCount,
                        folder.noteCount
                    ),
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
}

