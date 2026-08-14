package code.opensource0000.justnotes.settings

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

// Wraps a Context with a Locale-overridden Configuration so the app's UI text
// follows the user's chosen AppLanguage instead of the phone's system
// language. Applied in MainActivity.attachBaseContext(), before any string
// resource is resolved for the first time — a manual override rather than
// AppCompatDelegate.setApplicationLocales(), since that API only reliably
// auto-applies on an AppCompatActivity, which MainActivity isn't (it's a
// plain FragmentActivity, needed for androidx.biometric).
object LocaleContextWrapper {
    fun wrap(context: Context, language: AppLanguage): Context {
        val tag = language.languageTag ?: return context
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration)
    }
}
