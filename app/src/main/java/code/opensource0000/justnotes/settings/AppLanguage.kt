package code.opensource0000.justnotes.settings

// The app's own display language, independent of the phone's system
// language — separate from DictationLanguage, which only controls which
// offline speech model the mic button uses. languageTag null means "follow
// the phone's language" (the OS default, no override applied).
enum class AppLanguage(val languageTag: String?) {
    SYSTEM(null),
    FRENCH("fr"),
    ENGLISH("en")
}
