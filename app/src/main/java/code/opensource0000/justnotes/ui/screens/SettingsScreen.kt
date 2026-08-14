package code.opensource0000.justnotes.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import code.opensource0000.justnotes.R
import code.opensource0000.justnotes.settings.AppLanguage
import code.opensource0000.justnotes.settings.ThemeMode
import code.opensource0000.justnotes.stt.DictationLanguage
import code.opensource0000.justnotes.stt.VoskModelState
import code.opensource0000.justnotes.ui.components.RadioRow

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onChangePin: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val autosaveEnabled by viewModel.autosaveEnabled.collectAsState()
    val dictationLanguage by viewModel.dictationLanguage.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()
    val activity = LocalContext.current as? FragmentActivity

    // Switching the app's display language forces Android to recreate the
    // Activity to apply the new resources, which re-shows the lock screen if
    // authentication is on — surprising if it just happens on tap. Asking for
    // confirmation first (with the message already shown in the *target*
    // language) makes that transition expected instead of looking broken.
    var pendingAppLanguage by remember { mutableStateOf<AppLanguage?>(null) }

    // Scaffold (rather than a bare Column) paints the themed background and
    // keeps content clear of the status bar / notch, since the app draws
    // edge-to-edge; HomeScreen gets both for free the same way.
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
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
                Text(text = stringResource(R.string.settings_title), style = MaterialTheme.typography.titleMedium)
            }

            SectionLabel(text = stringResource(R.string.settings_section_general))
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(text = stringResource(R.string.settings_appearance), style = MaterialTheme.typography.bodyMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size)
                        ) {
                            Text(text = themeModeLabel(mode))
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.settings_app_language),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 20.dp)
                )
                AppLanguage.entries.forEach { language ->
                    RadioRow(
                        text = appLanguageLabel(language),
                        selected = appLanguage == language,
                        onClick = { pendingAppLanguage = language }
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.settings_autosave), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = stringResource(R.string.settings_autosave_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = autosaveEnabled, onCheckedChange = viewModel::setAutosaveEnabled)
            }

            SectionLabel(text = stringResource(R.string.settings_section_security))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.settings_auth_toggle), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = stringResource(R.string.settings_auth_toggle_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = viewModel.authEnabled, onCheckedChange = viewModel::onAuthEnabledChange)
            }
            NavigationRow(text = stringResource(R.string.settings_change_pin), onClick = onChangePin)

            SectionLabel(text = stringResource(R.string.settings_section_stt))
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = stringResource(R.string.settings_stt_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.settings_stt_language),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
                DictationLanguage.entries.forEach { language ->
                    RadioRow(
                        text = dictationLanguageLabel(language),
                        selected = dictationLanguage == language,
                        onClick = { viewModel.setDictationLanguage(language) }
                    )
                }
                DictationLanguage.entries.forEach { language ->
                    VoskLanguageRow(language = language, viewModel = viewModel)
                }
            }
        }
    }

    val targetLanguage = pendingAppLanguage
    if (targetLanguage != null) {
        // Deliberately not run through stringResource(): the whole point is
        // to stay legible regardless of which language the app is currently
        // displayed in, so both languages are spelled out together here.
        AlertDialog(
            onDismissRequest = { pendingAppLanguage = null },
            title = { Text("Changer la langue ? / Change language?") },
            text = { Text("L'application va redémarrer et vous redemandera votre code si l'authentification est activée.\nThe app will restart and will ask for your code again if authentication is on.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onAppLanguageChange(targetLanguage)
                    pendingAppLanguage = null
                    // Forces attachBaseContext() to run again with the newly
                    // persisted language, which is where it's actually applied.
                    activity?.recreate()
                }) {
                    Text("Confirmer / Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAppLanguage = null }) {
                    Text("Annuler / Cancel")
                }
            }
        )
    }
}

@Composable
private fun VoskLanguageRow(language: DictationLanguage, viewModel: SettingsViewModel) {
    val state by viewModel.voskModelState(language).collectAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = dictationLanguageLabel(language), style = MaterialTheme.typography.bodyMedium)
            Text(
                text = voskModelStateLabel(state),
                style = MaterialTheme.typography.bodySmall,
                color = if (state == VoskModelState.ERROR) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        when (state) {
            VoskModelState.NOT_INSTALLED -> TextButton(onClick = { viewModel.installVoskModel(language) }) {
                Text(stringResource(R.string.settings_stt_install))
            }

            VoskModelState.INSTALLING -> CircularProgressIndicator(modifier = Modifier.size(20.dp))

            VoskModelState.READY -> TextButton(onClick = { viewModel.deleteVoskModel(language) }) {
                Text(stringResource(R.string.settings_stt_delete))
            }

            VoskModelState.ERROR -> TextButton(onClick = { viewModel.installVoskModel(language) }) {
                Text(stringResource(R.string.settings_stt_retry))
            }
        }
    }
}

@Composable
private fun dictationLanguageLabel(language: DictationLanguage): String = when (language) {
    DictationLanguage.FRENCH -> stringResource(R.string.language_french)
    DictationLanguage.ENGLISH -> stringResource(R.string.language_english)
}

@Composable
private fun appLanguageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.SYSTEM -> stringResource(R.string.settings_app_language_system)
    AppLanguage.FRENCH -> stringResource(R.string.language_french)
    AppLanguage.ENGLISH -> stringResource(R.string.language_english)
}

@Composable
private fun voskModelStateLabel(state: VoskModelState): String = when (state) {
    VoskModelState.NOT_INSTALLED -> stringResource(R.string.settings_stt_not_installed)
    VoskModelState.INSTALLING -> stringResource(R.string.settings_stt_installing)
    VoskModelState.READY -> stringResource(R.string.settings_stt_ready)
    VoskModelState.ERROR -> stringResource(R.string.settings_stt_error)
}

@Composable
private fun NavigationRow(text: String, onClick: () -> Unit, subText: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
            if (subText != null) {
                Text(
                    text = subText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Uppercase + letter-spacing (from Type.kt's labelLarge) reads as a section
// eyebrow rather than a heading — a common convention for grouped settings
// lists, kept deliberately quieter than the note-editing screens' typography.
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.settings_appearance_system)
    ThemeMode.LIGHT -> stringResource(R.string.settings_appearance_light)
    ThemeMode.DARK -> stringResource(R.string.settings_appearance_dark)
}
