package code.opensource0000.justnotes.ui.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    // Read once into local state rather than observed as a Flow: nothing
    // else in the app changes this value concurrently while Settings is open.
    var authEnabled by mutableStateOf(pinManager.isAuthEnabled())
        private set

    fun onAuthEnabledChange(enabled: Boolean) {
        authEnabled = enabled
        pinManager.setAuthEnabled(enabled)
    }
}
