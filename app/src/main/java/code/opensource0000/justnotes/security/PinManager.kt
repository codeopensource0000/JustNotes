package code.opensource0000.justnotes.security

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Stores only a salted hash of the code, never the code itself — the same
// principle as a server storing password hashes, applied locally.
//
// One instance manages exactly one code. The app has the primary app-open
// code plus one independent secondary-lock code per protected note (each
// note's code is unrelated to every other note's — cracking one doesn't help
// with any other), each backed by its own SharedPreferences file — construct
// via forPrimary()/forNote() rather than directly.
//
// For a note, the code does double duty: it is checked to open the note, and
// it is what the note's content encryption key is derived from. Both come out
// of one derivation — see unlock().
class PinManager private constructor(context: Context, prefsName: String) {

    private val prefs = context.applicationContext
        .getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    // What a successful unlock yields. contentKey only means something for a
    // note's lock; the primary lock gates the app and encrypts nothing, so it
    // simply ignores it.
    class Unlocked internal constructor(val contentKey: SecretKey)

    fun hasPin(): Boolean = prefs.contains(KEY_HASH)

    // A StateFlow rather than a getter because two screens act on this value
    // at once: Settings shows the switch, and the re-authentication screen
    // that guards turning it off is a separate navigation entry. Only
    // forPrimary() is a shared instance, which is what makes this observable
    // across both.
    private val _authEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTH_ENABLED, true))
    val authEnabled: StateFlow<Boolean> = _authEnabled

    fun setAuthEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTH_ENABLED, enabled) }
        _authEnabled.value = enabled
    }

    // Slow on purpose (PBKDF2 with many iterations): call from a background
    // thread, never the main thread.
    //
    // Returns the content key rather than making the caller ask for it
    // separately: whoever has just chosen a code for a note usually wants to
    // encrypt with it immediately, and a second call would mean paying the
    // whole derivation twice.
    fun setPin(pin: String): SecretKey {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val master = deriveMaster(pin, salt)
        prefs.edit {
            putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            putString(KEY_HASH, Base64.encodeToString(subKey(master, LABEL_VERIFY), Base64.NO_WRAP))
            remove(KEY_FAILED_ATTEMPTS)
            remove(KEY_LOCKED_UNTIL)
        }
        return SecretKeySpec(subKey(master, LABEL_CONTENT), "AES")
    }

    // Checks the code and, when it matches, hands back the note's content key
    // — both out of a single PBKDF2 pass. Returns null when the code is wrong,
    // when no code was ever set, or while a lockout is in force.
    //
    // The single pass is the point. Verifying and deriving started out as two
    // separate calls, which meant unlocking a note paid 420,000 iterations
    // instead of 210,000 and was noticeably slower to open. The cost belongs
    // to the master derivation alone; splitting it into two independent
    // values afterwards is essentially free.
    fun unlock(pin: String): Unlocked? {
        if (lockoutRemainingMillis() > 0) return null
        val storedSalt = prefs.getString(KEY_SALT, null) ?: return null
        val storedHash = prefs.getString(KEY_HASH, null) ?: return null
        val master = deriveMaster(pin, Base64.decode(storedSalt, Base64.NO_WRAP))
        // Constant-time: a plain == on the encoded strings would stop at the
        // first differing byte and leak how much of a guess was right.
        val matches = MessageDigest.isEqual(
            subKey(master, LABEL_VERIFY),
            Base64.decode(storedHash, Base64.NO_WRAP)
        )
        if (!matches) {
            registerFailure()
            return null
        }
        prefs.edit { remove(KEY_FAILED_ATTEMPTS); remove(KEY_LOCKED_UNTIL) }
        return Unlocked(SecretKeySpec(subKey(master, LABEL_CONTENT), "AES"))
    }

    // Called when a note's secondary lock is turned off. Dropping the salt is
    // what actually retires the old ciphertext: the key was only ever
    // derivable from code + salt, so once the salt is gone nothing can rebuild
    // it — not even someone who later learns the code.
    fun clearPin() {
        prefs.edit {
            remove(KEY_SALT)
            remove(KEY_HASH)
            remove(KEY_FAILED_ATTEMPTS)
            remove(KEY_LOCKED_UNTIL)
        }
    }

    // --- Throttling ------------------------------------------------------

    // Milliseconds left before another code may be tried, 0 when free.
    //
    // Six digits is a million possibilities, but a patient thumb only needs
    // the few that are likely. After FREE_ATTEMPTS misses the wait doubles
    // each time, which makes guessing at the screen impractical while barely
    // inconveniencing someone who fat-fingered their own code twice.
    //
    // Worth being clear about the limit: this protects against someone poking
    // at the phone, and does nothing against an attacker who has copied the
    // app's data and attacks it offline. Only the PBKDF2 cost and the length
    // of the code matter there.
    fun lockoutRemainingMillis(): Long {
        val until = prefs.getLong(KEY_LOCKED_UNTIL, 0L)
        return (until - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    private fun registerFailure() {
        val failures = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        // The failure count itself is never reset by waiting, only by a
        // correct code: otherwise sitting out one lockout would hand the
        // attacker a fresh batch of free attempts.
        prefs.edit {
            putInt(KEY_FAILED_ATTEMPTS, failures)
            if (failures >= FREE_ATTEMPTS) {
                val step = (failures - FREE_ATTEMPTS).coerceAtMost(MAX_BACKOFF_DOUBLINGS)
                putLong(
                    KEY_LOCKED_UNTIL,
                    System.currentTimeMillis() + (LOCKOUT_BASE_MILLIS shl step)
                )
            }
        }
    }

    // --- Derivation ------------------------------------------------------

    // The one expensive step, and the only one an attacker has to repeat for
    // every code they want to try. Everything else here is cheap by design.
    private fun deriveMaster(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH_BITS)
        return SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
    }

    // Splits the master into one independent value per label. HMAC is a
    // pseudo-random function, so the verification hash that gets written to
    // disk gives away nothing about the content key that never does — which is
    // exactly what allows both to come from a single derivation instead of
    // two salts and two passes.
    private fun subKey(master: ByteArray, label: String): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(master, HMAC_ALGORITHM))
        return mac.doFinal(label.toByteArray(Charsets.UTF_8))
    }

    companion object {
        const val PIN_LENGTH = 6

        // Unchanged from before this class supported per-note codes, so the
        // primary code already saved on installed devices keeps working.
        private const val PREFS_NAME_PRIMARY = "justnotes_security"
        private const val PREFS_NAME_NOTE_PREFIX = "justnotes_note_pin_"

        private const val KEY_SALT = "pin_salt"
        private const val KEY_HASH = "pin_hash"
        private const val KEY_AUTH_ENABLED = "auth_enabled"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKED_UNTIL = "locked_until"
        private const val SALT_LENGTH_BYTES = 16
        private const val PBKDF2_ITERATIONS = 210_000
        private const val PBKDF2_KEY_LENGTH_BITS = 256
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val HMAC_ALGORITHM = "HmacSHA256"

        // Distinct labels are what make the two derived values independent;
        // they must never be changed, or every stored code stops matching.
        private const val LABEL_VERIFY = "justnotes:verify"
        private const val LABEL_CONTENT = "justnotes:content"

        // Misses allowed before the wait starts, then 30s doubling up to
        // roughly 16 minutes (30s << 5).
        private const val FREE_ATTEMPTS = 5
        private const val LOCKOUT_BASE_MILLIS = 30_000L
        private const val MAX_BACKOFF_DOUBLINGS = 5

        // The primary code is one shared instance so that authEnabled is a
        // single observable value across every screen that reads it. Note
        // locks stay per-call — they hold no cross-screen state.
        @Volatile
        private var primaryInstance: PinManager? = null

        fun forPrimary(context: Context): PinManager {
            return primaryInstance ?: synchronized(this) {
                primaryInstance ?: PinManager(context, PREFS_NAME_PRIMARY)
                    .also { primaryInstance = it }
            }
        }

        fun forNote(context: Context, noteId: Long): PinManager =
            PinManager(context, "$PREFS_NAME_NOTE_PREFIX$noteId")
    }
}
