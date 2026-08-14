package code.opensource0000.justnotes.ui.components

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

// A one-off "prove it's really you" check, backed by the OS itself
// (biometric or the device's own lock screen credential) rather than any of
// our own PIN screens. Used wherever an action is security-sensitive enough
// to deserve a fresh confirmation: reading a Keystore-gated encrypted note,
// or flipping a security toggle in Settings.
//
// No CryptoObject is involved — this is a plain authentication check, not
// tied to a specific cipher operation.
fun showSystemAuthPrompt(activity: FragmentActivity, titleRes: Int, onSuccess: () -> Unit) {
    val executor = ContextCompat.getMainExecutor(activity)
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess()
        }
    }
    // setNegativeButtonText() can't be combined with DEVICE_CREDENTIAL as an
    // allowed authenticator — the device credential option itself acts as
    // the fallback, so there is no separate cancel-with-text button here.
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(activity.getString(titleRes))
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        .build()
    BiometricPrompt(activity, executor, callback).authenticate(promptInfo)
}
