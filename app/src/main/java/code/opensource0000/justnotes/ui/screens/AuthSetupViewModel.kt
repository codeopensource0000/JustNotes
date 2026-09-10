package code.opensource0000.justnotes.ui.screens

import android.app.Application
import androidx.biometric.BiometricManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import code.opensource0000.justnotes.security.BiometricShortcut
import code.opensource0000.justnotes.security.NoteKeySession
import code.opensource0000.justnotes.security.PinManager
import code.opensource0000.justnotes.settings.SettingsManager
import javax.crypto.Cipher
import javax.crypto.SecretKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthSetupViewModel(
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

    enum class Stage { ENTER_NEW, CONFIRM }

    var stage by mutableStateOf(Stage.ENTER_NEW)
        private set
    var pin by mutableStateOf("")
        private set
    var mismatchError by mutableStateOf(false)
        private set

    // Kept only in memory for the few seconds between the two steps, never persisted.
    private var firstEntry = ""

    // One-shot: non-null when the screen must show a fingerprint prompt bound
    // to this cipher, to enrol the note's shortcut. A symmetric Keystore key
    // needs authentication to *write* as well as to read, which is why setting
    // the shortcut up asks for a fingerprint once per note.
    var enrolmentCipher by mutableStateOf<Cipher?>(null)
        private set

    private var pendingContentKey: SecretKey? = null
    private var pendingComplete: (() -> Unit)? = null

    fun onDigit(digit: Int, onComplete: () -> Unit) {
        if (pin.length >= PinManager.PIN_LENGTH) return
        mismatchError = false
        pin += digit.toString()
        if (pin.length < PinManager.PIN_LENGTH) return

        when (stage) {
            Stage.ENTER_NEW -> {
                firstEntry = pin
                pin = ""
                stage = Stage.CONFIRM
            }
            Stage.CONFIRM -> {
                if (pin == firstEntry) {
                    val finalPin = pin
                    viewModelScope.launch {
                        // PBKDF2 hashing is deliberately slow; keep it off the
                        // main thread. setPin() returns the content key from
                        // the same derivation that produced the stored hash,
                        // so choosing a code costs one pass, not two.
                        val contentKey = withContext(Dispatchers.Default) {
                            pinManager.setPin(finalPin)
                        }
                        if (noteId == null) {
                            onComplete()
                            return@launch
                        }
                        NoteKeySession.put(noteId, contentKey)

                        val cipher = if (canOfferBiometrics()) {
                            BiometricShortcut.enrolmentCipher(noteId)
                        } else {
                            null
                        }
                        if (cipher == null) {
                            onComplete()
                            return@launch
                        }
                        // Only the screen can put a prompt on the display, so
                        // completion waits until it reports back.
                        pendingContentKey = contentKey
                        pendingComplete = onComplete
                        enrolmentCipher = cipher
                    }
                } else {
                    mismatchError = true
                    firstEntry = ""
                    pin = ""
                    stage = Stage.ENTER_NEW
                }
            }
        }
    }

    // Called whether the prompt succeeded (an authenticated cipher) or was
    // cancelled or failed (null). Setup is over either way: a note whose
    // shortcut was declined is perfectly usable, it just always asks for its
    // code. Declining is not an error and must not block the flow.
    fun onEnrolmentFinished(authenticatedCipher: Cipher?) {
        val id = noteId
        val contentKey = pendingContentKey
        val complete = pendingComplete
        enrolmentCipher = null
        pendingContentKey = null
        pendingComplete = null
        if (authenticatedCipher != null && id != null && contentKey != null) {
            BiometricShortcut.store(getApplication(), id, authenticatedCipher, contentKey)
        }
        complete?.invoke()
    }

    private fun canOfferBiometrics(): Boolean {
        val settings = SettingsManager.getInstance(getApplication())
        if (!settings.biometricForNotesEnabled.value) return false
        return BiometricManager.from(getApplication())
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun onDelete() {
        if (pin.isNotEmpty()) pin = pin.dropLast(1)
    }
}
