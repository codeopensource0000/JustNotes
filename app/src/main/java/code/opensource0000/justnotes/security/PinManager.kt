package code.opensource0000.justnotes.security

import android.content.Context
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

// Stores only a salted PBKDF2 hash of the PIN, never the PIN itself — the same
// principle as a server storing password hashes, applied locally.
//
// One instance manages exactly one PIN. The app has the primary app-open PIN
// plus one independent secondary-lock PIN per protected note (each note's
// code is unrelated to every other note's — cracking one doesn't help with
// any other), each backed by its own SharedPreferences file — construct via
// forPrimary()/forNote() rather than directly.
class PinManager private constructor(context: Context, prefsName: String) {

    private val prefs = context.applicationContext
        .getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun hasPin(): Boolean = prefs.contains(KEY_HASH)

    fun isAuthEnabled(): Boolean = prefs.getBoolean(KEY_AUTH_ENABLED, true)

    fun setAuthEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTH_ENABLED, enabled).apply()
    }

    // Slow on purpose (PBKDF2 with many iterations): call from a background
    // thread, never the main thread.
    fun setPin(pin: String) {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = hash(pin, salt)
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val storedSalt = prefs.getString(KEY_SALT, null) ?: return false
        val storedHash = prefs.getString(KEY_HASH, null) ?: return false
        val salt = Base64.decode(storedSalt, Base64.NO_WRAP)
        val candidateHash = Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP)
        return candidateHash == storedHash
    }

    // Called when a note's secondary lock is turned off — the code becomes
    // meaningless once nothing is encrypted with the key it used to gate, so
    // there's no reason to keep it around.
    fun clearPin() {
        prefs.edit().remove(KEY_SALT).remove(KEY_HASH).apply()
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    companion object {
        const val PIN_LENGTH = 4

        // Unchanged from before this class supported per-note PINs, so the
        // primary PIN already saved on installed devices keeps working.
        private const val PREFS_NAME_PRIMARY = "justnotes_security"
        private const val PREFS_NAME_NOTE_PREFIX = "justnotes_note_pin_"

        private const val KEY_SALT = "pin_salt"
        private const val KEY_HASH = "pin_hash"
        private const val KEY_AUTH_ENABLED = "auth_enabled"
        private const val SALT_LENGTH_BYTES = 16
        private const val PBKDF2_ITERATIONS = 210_000
        private const val PBKDF2_KEY_LENGTH_BITS = 256

        fun forPrimary(context: Context): PinManager = PinManager(context, PREFS_NAME_PRIMARY)

        fun forNote(context: Context, noteId: Long): PinManager =
            PinManager(context, "$PREFS_NAME_NOTE_PREFIX$noteId")
    }
}
