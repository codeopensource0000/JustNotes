package code.opensource0000.justnotes.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import code.opensource0000.justnotes.security.BiometricShortcut
import code.opensource0000.justnotes.security.PinManager
import code.opensource0000.justnotes.settings.AppLanguage
import code.opensource0000.justnotes.settings.SettingsManager
import code.opensource0000.justnotes.settings.ThemeMode
import code.opensource0000.justnotes.stt.DictationLanguage
import code.opensource0000.justnotes.stt.VoskModelManager
import code.opensource0000.justnotes.stt.VoskModelState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsManager = SettingsManager.getInstance(application)
    private val pinManager = PinManager.forPrimary(application)
    private val voskModelManager = VoskModelManager.getInstance(application)

    // Applying the choice (MainActivity.attachBaseContext + activity.recreate())
    // is handled by the screen, which has the Activity reference; this just
    // persists the choice.
    val appLanguage: StateFlow<AppLanguage> = settingsManager.appLanguage

    fun onAppLanguageChange(language: AppLanguage) {
        settingsManager.setAppLanguage(language)
    }

    fun voskModelState(language: DictationLanguage): StateFlow<VoskModelState> = voskModelManager.state(language)

    fun installVoskModel(language: DictationLanguage) {
        viewModelScope.launch(Dispatchers.IO) { voskModelManager.install(language) }
    }

    fun deleteVoskModel(language: DictationLanguage) {
        voskModelManager.delete(language)
    }

    val dictationLanguage: StateFlow<DictationLanguage> = settingsManager.dictationLanguage

    fun setDictationLanguage(language: DictationLanguage) {
        settingsManager.setDictationLanguage(language)
    }

    val themeMode: StateFlow<ThemeMode> = settingsManager.themeMode

    fun setThemeMode(mode: ThemeMode) {
        settingsManager.setThemeMode(mode)
    }

    val autosaveEnabled: StateFlow<Boolean> = settingsManager.autosaveEnabled

    fun setAutosaveEnabled(enabled: Boolean) {
        settingsManager.setAutosaveEnabled(enabled)
    }

    val biometricForNotesEnabled: StateFlow<Boolean> = settingsManager.biometricForNotesEnabled

    // Switching this off erases every stored shortcut immediately rather than
    // just hiding the prompt: a wrapped copy of a note's key left lying around
    // after the user asked for it to stop existing would be the wrong default.
    // Nothing is lost — the code still opens every note.
    fun setBiometricForNotesEnabled(enabled: Boolean) {
        settingsManager.setBiometricForNotesEnabled(enabled)
        if (!enabled) {
            viewModelScope.launch(Dispatchers.IO) {
                BiometricShortcut.deleteAll(getApplication())
            }
        }
    }

    // Observed rather than read once: turning the lock off now happens on a
    // separate re-authentication screen, so the value can change while this
    // one is still on the back stack.
    val authEnabled: StateFlow<Boolean> = pinManager.authEnabled

    // Only switching it on. Switching it off is routed through the lock screen
    // first — see SettingsScreen and MainActivity's reauth route.
    fun enableAuth() {
        pinManager.setAuthEnabled(true)
    }

    val screenshotsAllowed: StateFlow<Boolean> = settingsManager.screenshotsAllowed

    // Only the re-protecting direction. Allowing captures goes through the
    // lock screen, in MainActivity.
    fun blockScreenshots() {
        settingsManager.setScreenshotsAllowed(false)
    }
}
