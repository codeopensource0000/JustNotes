package code.opensource0000.justnotes.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import code.opensource0000.justnotes.R
import code.opensource0000.justnotes.export.ExportFormat
import code.opensource0000.justnotes.export.NoteExporter
import code.opensource0000.justnotes.ui.components.DeleteConfirmDialog
import code.opensource0000.justnotes.ui.components.MarkdownVisualTransformation
import code.opensource0000.justnotes.ui.components.NewFolderDialog
import code.opensource0000.justnotes.ui.components.RadioRow
import code.opensource0000.justnotes.ui.components.folderLabel
import code.opensource0000.justnotes.ui.theme.NoteTitleStyle
import kotlinx.coroutines.launch

// Breathing room above and below the writing area. Named because the minimum
// field height is derived from it — see the content field below.
private val CONTENT_VERTICAL_PADDING = 8.dp

// Transparent container + no underline: the title and content fields read
// as a writing surface, not a boxed form field.
private val PaperFieldColors
    @Composable get() = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent
    )

// noteId <= 0 means "new note"; a real id means "editing an existing note".
// initialFolderId is only used for a new note opened from inside a folder
// (rather than from Recents), so it starts there instead of the default folder.
@Composable
fun NoteEditorScreen(
    noteId: Long,
    onBack: () -> Unit,
    onRequestNotePinSetup: (noteId: Long) -> Unit,
    initialFolderId: Long = -1L,
    viewModel: NoteEditorViewModel = viewModel()
) {
    // LaunchedEffect(noteId) runs once per distinct noteId value: exactly
    // what we want, since load() itself already guards against reloading.
    // Both calls are no-ops for the "wrong" case (load() for a new note,
    // initializeNewNoteFolderIfNeeded() for an existing one).
    LaunchedEffect(noteId) {
        viewModel.load(noteId)
        viewModel.initializeNewNoteFolderIfNeeded(noteId, initialFolderId)
    }

    // Fires right after toggleLocked() optimistically marks a note locked
    // but it has no PIN of its own yet — navigates to that note's mandatory
    // PIN-setup flow (see PinSetupScreen's blockSystemBack).
    LaunchedEffect(viewModel.requestNotePinSetup) {
        val id = viewModel.currentNoteId
        if (viewModel.requestNotePinSetup && id != null) {
            onRequestNotePinSetup(id)
            viewModel.onNotePinSetupRequested()
        }
    }

    // Only needed to hand the share sheet a real Activity when exporting.
    val activity = LocalContext.current as? FragmentActivity

    // The stored bytes of a locked note could not be turned back into text.
    // Nothing here can fix that, so the dialog's job is to say so plainly and
    // offer the only two useful moves — leave the note untouched, or delete
    // it. It is not dismissible: tapping outside would drop the user into an
    // empty-looking editor with no idea the real content is still down there.
    if (viewModel.contentUnreadable) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.editor_content_unreadable_title)) },
            text = { Text(stringResource(R.string.editor_content_unreadable_message)) },
            confirmButton = {
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.editor_back))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.deleteNote(onFinished = onBack) }) {
                    Text(
                        stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // Tapping a toolbar button moves keyboard focus to that button, so the
    // cursor position we set programmatically in the content field doesn't
    // actually take effect until focus returns there — hence the manual
    // re-tap the toolbar used to require. Re-requesting focus after each
    // toolbar action closes that gap.
    val contentFocusRequester = remember { FocusRequester() }

    var showExportDialog by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val folders by viewModel.folders.collectAsState()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val micPermissionDeniedMessage = stringResource(R.string.editor_mic_permission_denied)
    val micModelRequiredMessage = stringResource(R.string.editor_mic_model_required)
    LaunchedEffect(viewModel.micBlockReason) {
        if (viewModel.micBlockReason != null) {
            snackbarHostState.showSnackbar(micModelRequiredMessage)
            viewModel.onMicBlockMessageShown()
        }
    }
    val requestMicPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.toggleVoiceDictation()
        } else {
            coroutineScope.launch { snackbarHostState.showSnackbar(micPermissionDeniedMessage) }
        }
    }
    val onMicClick: () -> Unit = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            viewModel.toggleVoiceDictation()
        } else {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // The single decision point for "the user wants to leave this screen",
    // shared by the toolbar's back arrow and the system back gesture/button
    // below — so swiping back can't silently skip the unsaved-changes check.
    val handleBack: () -> Unit = {
        when {
            !viewModel.isDirty -> onBack()
            viewModel.isAutosaveEnabled -> viewModel.save(onFinished = onBack)
            else -> showUnsavedDialog = true
        }
    }

    BackHandler(onBack = handleBack)

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(stringResource(R.string.editor_export_choose_title)) },
            text = {
                Column {
                    ExportFormatRow(
                        title = stringResource(R.string.editor_export_txt),
                        subtitle = stringResource(R.string.editor_export_txt_sub),
                        onClick = {
                            showExportDialog = false
                            activity?.let { act ->
                                coroutineScope.launch {
                                    NoteExporter.export(act, viewModel.title, viewModel.content, ExportFormat.PLAIN_TEXT)
                                }
                            }
                        }
                    )
                    ExportFormatRow(
                        title = stringResource(R.string.editor_export_md),
                        subtitle = stringResource(R.string.editor_export_md_sub),
                        onClick = {
                            showExportDialog = false
                            activity?.let { act ->
                                coroutineScope.launch {
                                    NoteExporter.export(act, viewModel.title, viewModel.content, ExportFormat.MARKDOWN)
                                }
                            }
                        }
                    )
                    ExportFormatRow(
                        title = stringResource(R.string.editor_export_html),
                        subtitle = stringResource(R.string.editor_export_html_sub),
                        onClick = {
                            showExportDialog = false
                            activity?.let { act ->
                                coroutineScope.launch {
                                    NoteExporter.export(act, viewModel.title, viewModel.content, ExportFormat.HTML)
                                }
                            }
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text(stringResource(R.string.editor_unsaved_title)) },
            text = { Text(stringResource(R.string.editor_unsaved_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    viewModel.save(onFinished = onBack)
                }) {
                    Text(stringResource(R.string.editor_unsaved_save))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    onBack()
                }) {
                    Text(stringResource(R.string.editor_unsaved_discard))
                }
            }
        )
    }

    if (showFolderPicker) {
        AlertDialog(
            onDismissRequest = { showFolderPicker = false },
            title = { Text(stringResource(R.string.folder_picker_title)) },
            text = {
                Column {
                    folders.forEach { folder ->
                        RadioRow(
                            text = folderLabel(folder.name, folder.isDefault),
                            selected = folder.id == viewModel.folderId,
                            onClick = {
                                viewModel.selectFolder(folder)
                                showFolderPicker = false
                            }
                        )
                    }
                    TextButton(
                        onClick = {
                            showFolderPicker = false
                            showNewFolderDialog = true
                        }
                    ) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.home_new_folder))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showFolderPicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showNewFolderDialog) {
        NewFolderDialog(
            onDismiss = { showNewFolderDialog = false },
            onCreate = { name ->
                viewModel.createFolderAndSelect(name)
                showNewFolderDialog = false
            }
        )
    }

    if (showDeleteDialog) {
        DeleteConfirmDialog(
            title = stringResource(R.string.note_delete_confirm_title),
            message = stringResource(R.string.note_delete_confirm_message),
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteNote(onFinished = onBack)
            }
        )
    }

    // Scaffold (rather than a bare Column) paints the themed background and
    // keeps content clear of the status bar / notch, since the app draws
    // edge-to-edge; HomeScreen gets both for free the same way.
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
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
                IconButton(onClick = handleBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.editor_back))
                }
                Text(
                    text = stringResource(R.string.editor_header_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showExportDialog = true }) {
                    Icon(Icons.Filled.IosShare, contentDescription = stringResource(R.string.editor_export))
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.action_delete))
                }
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
                placeholder = { Text(stringResource(R.string.editor_title_placeholder), style = NoteTitleStyle) },
                textStyle = NoteTitleStyle,
                colors = PaperFieldColors,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            )

            TextButton(onClick = { showFolderPicker = true }, modifier = Modifier.padding(start = 4.dp)) {
                Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = folderLabel(viewModel.folderName, viewModel.folderIsDefault),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Inserts Markdown markers into the plain-text content at the
            // cursor/selection — there is no live bold/italic rendering here,
            // the note stays a portable plain-text file either way.
            val formatPlaceholder = stringResource(R.string.editor_format_placeholder)
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                IconButton(onClick = { viewModel.applyBold(formatPlaceholder); contentFocusRequester.requestFocus() }) {
                    Icon(Icons.Filled.FormatBold, contentDescription = stringResource(R.string.editor_format_bold))
                }
                IconButton(onClick = { viewModel.applyItalic(formatPlaceholder); contentFocusRequester.requestFocus() }) {
                    Icon(Icons.Filled.FormatItalic, contentDescription = stringResource(R.string.editor_format_italic))
                }
                IconButton(onClick = { viewModel.applyHeading(); contentFocusRequester.requestFocus() }) {
                    Icon(Icons.Filled.Title, contentDescription = stringResource(R.string.editor_format_heading))
                }
                IconButton(onClick = { viewModel.applyBulletList(); contentFocusRequester.requestFocus() }) {
                    Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = stringResource(R.string.editor_format_list))
                }
                IconButton(onClick = { viewModel.applyChecklist(); contentFocusRequester.requestFocus() }) {
                    Icon(Icons.Filled.CheckBox, contentDescription = stringResource(R.string.editor_format_checklist))
                }
                // Pulses the mic icon while actively listening — a stopped
                // infinite transition (isListening false) just settles the
                // animated value back at its target, no extra branching needed.
                val micPulseTransition = rememberInfiniteTransition(label = "mic_pulse")
                val micPulseAlpha by micPulseTransition.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 600),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "mic_pulse_alpha"
                )
                IconButton(onClick = onMicClick) {
                    Icon(
                        imageVector = if (viewModel.isListening) Icons.Filled.Mic else Icons.Filled.MicOff,
                        contentDescription = stringResource(
                            if (viewModel.isListening) R.string.editor_format_mic_stop else R.string.editor_format_mic
                        ),
                        tint = if (viewModel.isListening) MaterialTheme.colorScheme.error else LocalContentColor.current,
                        modifier = Modifier.alpha(if (viewModel.isListening) micPulseAlpha else 1f)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // The writing area scrolls; the field inside it grows freely.
            //
            // It used to be the other way round: weight(1f) capped the field's
            // height, so a note longer than the screen was simply clipped —
            // no scrolling by hand, and no following the caret as you typed
            // past the bottom. A TextField only scrolls its own content in
            // narrow cases, and this was not one of them.
            //
            // Growing inside a scrollable parent also gets the caret handling
            // for free: the text field asks to be brought into view, and the
            // nearest scrolling ancestor obliges.
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Minus the padding below, so an empty note fills exactly one
                // screen rather than one screen plus a sliver of slack.
                val minFieldHeight = maxHeight - CONTENT_VERTICAL_PADDING * 2
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(
                            horizontal = 12.dp,
                            vertical = CONTENT_VERTICAL_PADDING
                        )
                ) {
                    TextField(
                        value = viewModel.contentField,
                        onValueChange = viewModel::onContentChange,
                        placeholder = { Text(stringResource(R.string.editor_content_placeholder)) },
                        visualTransformation = MarkdownVisualTransformation,
                        colors = PaperFieldColors,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier
                            .fillMaxWidth()
                            // At least a full screen tall, so tapping anywhere
                            // in the empty space below a short note puts the
                            // caret in it rather than doing nothing.
                            .heightIn(min = minFieldHeight)
                            .focusRequester(contentFocusRequester)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportFormatRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
