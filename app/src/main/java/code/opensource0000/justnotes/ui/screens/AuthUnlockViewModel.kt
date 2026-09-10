package code.opensource0000.justnotes.ui.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import code.opensource0000.justnotes.security.BiometricShortcut
import code.opensource0000.justnotes.security.NoteKeySession
import code.opensource0000.justnotes.security.PinManager
import javax.crypto.Cipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthUnlockViewModel(
    application: Application,
    // No default. It used to fall back to the primary code, which compiled
    // and ran perfectly well on a note's lock screen — and silently opened
    // that note with the wrong secret. Every caller now says which code it
    // means, so the compiler holds the invariant instead of a comment.
    private val pinManager: PinManager,
    // Null means the primary lock, which gates the app and encrypts nothing.
    // A note id means the code typed here is what its content key is derived
    // from, so the key has to be handed to NoteKeySession now.
    private val noteId: Long?
) : AndroidViewModel(application) {

    var pin by mutableStateOf("")
        private set
    var error by mutableStateOf(false)
        private set

    // Seconds left before another code may be tried; 0 means the pad is live.
    // Counted down here rather than in the screen so it survives rotation.
    var lockoutSeconds by mutableIntStateOf(0)
        private set

    private var countdownJob: Job? = null

    init {
        // A lockout outlives the process — it is stored, not just held in
        // memory — so a screen opening onto one has to pick it up.
        startCountdown(pinManager.lockoutRemainingMillis())
    }

    private fun startCountdown(remainingMillis: Long) {
        countdownJob?.cancel()
        if (remainingMillis <= 0) {
            lockoutSeconds = 0
            return
        }
        countdownJob = viewModelScope.launch {
            var left = remainingMillis
            while (left > 0) {
                lockoutSeconds = ((left + 999) / 1000).toInt()
                delay(1_000)
                left -= 1_000
            }
            lockoutSeconds = 0
        }
    }

    fun onDigit(digit: Int, onUnlocked: () -> Unit) {
        if (lockoutSeconds > 0) return
        if (pin.length >= PinManager.PIN_LENGTH) return
        error = false
        pin += digit.toString()
        if (pin.length < PinManager.PIN_LENGTH) return

        val candidate = pin
        viewModelScope.launch {
            val correct = withContext(Dispatchers.Default) {
                // One call, one derivation: unlock() checks the code and hands
                // back the content key together. Stashed here, while the code
                // is still in hand — past this point the plain code is gone
                // for good and the key cannot be rebuilt.
                val unlocked = pinManager.unlock(candidate)
                if (unlocked != null && noteId != null) {
                    NoteKeySession.put(noteId, unlocked.contentKey)
                }
                unlocked != null
            }
            if (correct) {
                onUnlocked()
            } else {
                error = true
                pin = ""
                // unlock() has just recorded the miss; a lockout may have
                // started on this very attempt.
                startCountdown(pinManager.lockoutRemainingMillis())
            }
        }
    }

    fun onDelete() {
        if (pin.isNotEmpty()) pin = pin.dropLast(1)
    }

    // True when a fingerprint here has to be tied to a cipher rather than
    // being a plain yes/no check: opening a note means producing its content
    // key, and only an authenticated cipher can hand that over. The primary
    // lock has nothing to decrypt, so a plain prompt is enough for it.
    val biometricsUnlockContent: Boolean get() = noteId != null

    // Null means the fingerprint must not be offered for this note — either no
    // shortcut was ever enrolled, or its Keystore key has been invalidated (in
    // which case the dead shortcut has just been cleaned up). The code pad is
    // already on screen either way.
    fun biometricUnlockCipher(): Cipher? {
        val id = noteId ?: return null
        return BiometricShortcut.unlockCipher(getApplication(), id)
    }

    fun onBiometricUnlocked(cipher: Cipher, onUnlocked: () -> Unit) {
        val id = noteId ?: return
        val contentKey = BiometricShortcut.unwrap(getApplication(), id, cipher)
        if (contentKey == null) {
            // The wrapped copy is unusable; drop it so the prompt stops being
            // offered, and let the user fall back to the code.
            BiometricShortcut.delete(getApplication(), id)
            error = true
            return
        }
        NoteKeySession.put(id, contentKey)
        onUnlocked()
    }
}
