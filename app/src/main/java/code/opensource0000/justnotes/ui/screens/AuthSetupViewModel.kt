package code.opensource0000.justnotes.ui.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import code.opensource0000.justnotes.security.PinManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthSetupViewModel @JvmOverloads constructor(
    application: Application,
    // Defaults to the primary PIN so every existing call site (first-run
    // setup, "change code" in Settings) keeps working unchanged; the
    // secondary-lock setup screen supplies PinManager.forSecondary() instead.
    // @JvmOverloads is required for the default viewModel() factory to still
    // find a lone-Application constructor via reflection — see the identical
    // note in AuthUnlockViewModel.
    private val pinManager: PinManager = PinManager.forPrimary(application)
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
                        // PBKDF2 hashing is deliberately slow; keep it off the main thread.
                        withContext(Dispatchers.Default) {
                            pinManager.setPin(finalPin)
                        }
                        onComplete()
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

    fun onDelete() {
        if (pin.isNotEmpty()) pin = pin.dropLast(1)
    }
}
