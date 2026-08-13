package code.opensource0000.justnotes.ui.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import code.opensource0000.justnotes.security.PinManager
import code.opensource0000.justnotes.settings.SettingsManager
import code.opensource0000.justnotes.settings.ThemeMode
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsManager = SettingsManager.getInstance(application)
    private val pinManager = PinManager.forPrimary(application)

    val themeMode: StateFlow<ThemeMode> = settingsManager.themeMode

    fun setThemeMode(mode: ThemeMode) {
        settingsManager.setThemeMode(mode)
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
