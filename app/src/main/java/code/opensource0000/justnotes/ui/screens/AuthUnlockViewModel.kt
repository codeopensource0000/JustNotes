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

class AuthUnlockViewModel @JvmOverloads constructor(
    application: Application,
    // Defaults to the primary PIN; the secondary-lock gate screen supplies
    // PinManager.forSecondary() via a custom ViewModelProvider.Factory instead
    // (the default viewModel() factory can't pass extra constructor args).
    // @JvmOverloads is required for that default factory to keep working at
    // all: without it, Kotlin compiles a single (Application, PinManager)
    // constructor, and the factory's reflection lookup for a lone-Application
    // constructor fails with NoSuchMethodException.
    private val pinManager: PinManager = PinManager.forPrimary(application)
) : AndroidViewModel(application) {

    var pin by mutableStateOf("")
        private set
    var error by mutableStateOf(false)
        private set

    fun onDigit(digit: Int, onUnlocked: () -> Unit) {
        if (pin.length >= PinManager.PIN_LENGTH) return
        error = false
        pin += digit.toString()
        if (pin.length < PinManager.PIN_LENGTH) return

        val candidate = pin
        viewModelScope.launch {
            val correct = withContext(Dispatchers.Default) {
                pinManager.verifyPin(candidate)
            }
            if (correct) {
                onUnlocked()
            } else {
                error = true
                pin = ""
            }
        }
    }

    fun onDelete() {
        if (pin.isNotEmpty()) pin = pin.dropLast(1)
    }
}
