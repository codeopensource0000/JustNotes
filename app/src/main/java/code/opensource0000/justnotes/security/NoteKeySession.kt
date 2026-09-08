package code.opensource0000.justnotes.security

import javax.crypto.SecretKey

// Holds the content keys of the notes unlocked during this run of the app.
//
// A note's key is derived from its own code (PinManager.unlock) the
// moment the user types it on the secondary-lock screen, and is deliberately
// written nowhere: not to SharedPreferences, not to the database, and not
// through a navigation argument — a route string ends up in saved instance
// state, which is exactly where key material must not be. It lives in process
// memory between the unlock screen and the editor, and dies with the process.
//
// The consequence is the intended one: without the code, nobody can rebuild
// the key — this app included.
object NoteKeySession {

    private val keys = mutableMapOf<Long, SecretKey>()

    // Synchronized because the keys are put from a background dispatcher
    // (PBKDF2 runs on Dispatchers.Default) and read from another one when the
    // editor encrypts or decrypts.
    @Synchronized
    fun put(noteId: Long, key: SecretKey) {
        keys[noteId] = key
    }

    @Synchronized
    fun get(noteId: Long): SecretKey? = keys[noteId]

    @Synchronized
    fun forget(noteId: Long) {
        keys.remove(noteId)
    }
}
