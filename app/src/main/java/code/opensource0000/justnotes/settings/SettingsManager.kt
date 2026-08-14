package code.opensource0000.justnotes.settings

import android.content.Context
import code.opensource0000.justnotes.stt.DictationLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

// Small app-wide preferences store. Exposes theme as a StateFlow (rather than
// a plain getter) so that changing it in Settings updates the UI everywhere
// else immediately, without restarting the app.
class SettingsManager private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    private fun loadThemeMode(): ThemeMode {
        val stored = prefs.getString(KEY_THEME_MODE, null) ?: return ThemeMode.SYSTEM
        return runCatching { ThemeMode.valueOf(stored) }.getOrDefault(ThemeMode.SYSTEM)
    }

    // Off by default: the editor's explicit save button + an "unsaved
    // changes?" prompt on the way out is the current baseline behaviour.
    private val _autosaveEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTOSAVE_ENABLED, false))
    val autosaveEnabled: StateFlow<Boolean> = _autosaveEnabled

    fun setAutosaveEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOSAVE_ENABLED, enabled).apply()
        _autosaveEnabled.value = enabled
    }

    // Defaults to French, matching the app's own primary language; the model
    // still has to be installed separately regardless of which one is selected.
    private val _dictationLanguage = MutableStateFlow(loadDictationLanguage())
    val dictationLanguage: StateFlow<DictationLanguage> = _dictationLanguage

    fun setDictationLanguage(language: DictationLanguage) {
        prefs.edit().putString(KEY_DICTATION_LANGUAGE, language.name).apply()
        _dictationLanguage.value = language
    }

    private fun loadDictationLanguage(): DictationLanguage {
        val stored = prefs.getString(KEY_DICTATION_LANGUAGE, null) ?: return DictationLanguage.FRENCH
        return runCatching { DictationLanguage.valueOf(stored) }.getOrDefault(DictationLanguage.FRENCH)
    }

    // The app's own display language, independent of the phone's system
    // language. Read synchronously from MainActivity.attachBaseContext() —
    // before any string resource is resolved — so this must stay a plain
    // SharedPreferences read, not something requiring a running ViewModel.
    private val _appLanguage = MutableStateFlow(loadAppLanguage())
    val appLanguage: StateFlow<AppLanguage> = _appLanguage

    fun setAppLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_APP_LANGUAGE, language.name).apply()
        _appLanguage.value = language
    }

    private fun loadAppLanguage(): AppLanguage {
        val stored = prefs.getString(KEY_APP_LANGUAGE, null) ?: return AppLanguage.SYSTEM
        return runCatching { AppLanguage.valueOf(stored) }.getOrDefault(AppLanguage.SYSTEM)
    }

    companion object {
        private const val PREFS_NAME = "justnotes_settings"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_AUTOSAVE_ENABLED = "autosave_enabled"
        private const val KEY_DICTATION_LANGUAGE = "dictation_language"
        private const val KEY_APP_LANGUAGE = "app_language"

        @Volatile
        private var instance: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
