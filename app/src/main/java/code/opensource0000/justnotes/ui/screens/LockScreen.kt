package code.opensource0000.justnotes.ui.screens

import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import code.opensource0000.justnotes.R
import code.opensource0000.justnotes.security.PinManager
import code.opensource0000.justnotes.ui.components.PinDots
import code.opensource0000.justnotes.ui.components.PinPad
import code.opensource0000.justnotes.ui.theme.WordmarkStyle
import javax.crypto.Cipher

@Composable
fun LockScreen(
    onUnlocked: () -> Unit,
    // Defaults to the app name for the primary lock; the secondary-lock gate
    // passes R.string.secondary_lock_title instead, both as the on-screen
    // heading and as the title of the OS biometric prompt.
    titleRes: Int = R.string.app_name,
    // The primary lock is a gate and nothing else, so the fingerprint sensor
    // can open it. A note's secondary lock cannot work that way: its content
    // key is derived from the code itself (see PinManager.unlock),
    // and no biometric check can reconstruct it. Offering the sensor there
    // would open a note the app is then unable to decrypt — and would quietly
    // make the per-note code optional, which is the opposite of the point.
    allowBiometrics: Boolean = true,
    // The re-lock that appears on returning to a backgrounded app sits on top
    // of whatever the user was doing; letting system back dismiss it would
    // make the lock decorative.
    blockSystemBack: Boolean = false,
    viewModel: AuthUnlockViewModel
) {
    if (blockSystemBack) {
        BackHandler {}
    }

    val activity = LocalContext.current as? FragmentActivity

    // Runs once when this screen first appears (not on every recomposition,
    // e.g. not again after a wrong PIN triggers the error state): auto-prompt
    // the sensor immediately, Revolut-style, rather than waiting for a tap.
    LaunchedEffect(Unit) {
        if (!allowBiometrics || activity == null) return@LaunchedEffect
        if (!canAuthenticateWithBiometrics(activity)) return@LaunchedEffect

        if (!viewModel.biometricsUnlockContent) {
            // Primary lock: nothing to decrypt, so a plain yes/no check is all
            // this needs.
            showBiometricPrompt(activity, titleRes, cipher = null) { onUnlocked() }
            return@LaunchedEffect
        }
        // A note: the fingerprint has to unwrap this note's content key, so it
        // is bound to a specific cipher. No cipher means no shortcut was
        // enrolled, or its Keystore key died — the code pad below covers both.
        val cipher = viewModel.biometricUnlockCipher() ?: return@LaunchedEffect
        showBiometricPrompt(activity, titleRes, cipher) { authenticated ->
            if (authenticated != null) {
                viewModel.onBiometricUnlocked(authenticated, onUnlocked)
            }
        }
    }

    // Scaffold (rather than a bare Column) paints the themed background,
    // matching the other screens.
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = stringResource(titleRes), style = WordmarkStyle)
            Spacer(modifier = Modifier.height(8.dp))
            val lockoutSeconds = viewModel.lockoutSeconds
            val isLockedOut = lockoutSeconds > 0
            Text(
                text = when {
                    isLockedOut -> stringResource(R.string.lock_too_many_attempts, lockoutSeconds)
                    viewModel.error -> stringResource(R.string.lock_incorrect_code)
                    else -> stringResource(R.string.lock_enter_code)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isLockedOut || viewModel.error) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.height(24.dp))
            PinDots(filledCount = viewModel.pin.length, total = PinManager.PIN_LENGTH)
            Spacer(modifier = Modifier.height(32.dp))
            PinPad(
                onDigit = { digit -> viewModel.onDigit(digit, onUnlocked = onUnlocked) },
                onDelete = viewModel::onDelete,
                enabled = !isLockedOut
            )
        }
    }
}

private fun canAuthenticateWithBiometrics(activity: FragmentActivity): Boolean {
    val manager = BiometricManager.from(activity)
    return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
        BiometricManager.BIOMETRIC_SUCCESS
}

// cipher non-null ties the scan to one cipher operation: the OS only releases
// the authenticated cipher on success, which is what lets a fingerprint
// produce a note's content key instead of merely asserting who is holding the
// phone. onSuccess receives it back, or null for a plain unbound check.
private fun showBiometricPrompt(
    activity: FragmentActivity,
    titleRes: Int,
    cipher: Cipher?,
    onSuccess: (Cipher?) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onSuccess(result.cryptoObject?.cipher)
        }
        // onAuthenticationError and onAuthenticationFailed are intentionally
        // left as no-ops: the PIN pad is already on screen as the fallback,
        // so a cancelled or failed scan just leaves the user there.
    }
    // Title is required by the platform (build() throws if empty); the app
    // name (or "Secondary lock") is the least redundant thing to put there,
    // since the OS already shows its own built-in fingerprint icon/animation.
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(activity.getString(titleRes))
        .setNegativeButtonText(activity.getString(R.string.lock_use_code))
        .build()
    val prompt = BiometricPrompt(activity, executor, callback)
    if (cipher != null) {
        prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    } else {
        prompt.authenticate(promptInfo)
    }
}
