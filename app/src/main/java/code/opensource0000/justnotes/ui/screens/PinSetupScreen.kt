package code.opensource0000.justnotes.ui.screens

import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.lifecycle.viewmodel.compose.viewModel
import code.opensource0000.justnotes.R
import code.opensource0000.justnotes.security.PinManager
import code.opensource0000.justnotes.ui.components.PinDots
import code.opensource0000.justnotes.ui.components.PinPad
import javax.crypto.Cipher

@Composable
fun PinSetupScreen(
    onComplete: () -> Unit,
    // hintTextRes lets a note's own PIN setup show note-specific wording
    // instead of the primary-lock default ("asked every time you open the app").
    hintTextRes: Int = R.string.pin_setup_hint,
    // Primary setup (fresh install) already has nothing to go back to, so it
    // never needed this; a note's lock, opened from an already-open editor,
    // does — otherwise the system back button could leave the note marked
    // locked with no code ever actually set for it.
    blockSystemBack: Boolean = false,
    viewModel: AuthSetupViewModel = viewModel()
) {
    if (blockSystemBack) {
        BackHandler {}
    }

    // Enrolling a note's fingerprint shortcut needs the user to authenticate
    // once, so the ViewModel hands the cipher up here — only a screen can put
    // a prompt on the display. Cancelling is a normal outcome, not an error:
    // onEnrolmentFinished(null) simply finishes setup without a shortcut.
    val activity = LocalContext.current as? FragmentActivity
    val enrolmentCipher = viewModel.enrolmentCipher
    LaunchedEffect(enrolmentCipher) {
        if (enrolmentCipher == null) return@LaunchedEffect
        if (activity == null) {
            viewModel.onEnrolmentFinished(null)
            return@LaunchedEffect
        }
        showBiometricEnrolmentPrompt(activity, enrolmentCipher) { authenticated ->
            viewModel.onEnrolmentFinished(authenticated)
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
            Text(
                text = if (viewModel.stage == AuthSetupViewModel.Stage.ENTER_NEW) {
                    stringResource(R.string.pin_setup_choose)
                } else {
                    stringResource(R.string.pin_setup_confirm)
                },
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (viewModel.mismatchError) {
                    stringResource(R.string.pin_setup_mismatch)
                } else {
                    stringResource(hintTextRes)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (viewModel.mismatchError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.height(24.dp))
            PinDots(filledCount = viewModel.pin.length, total = PinManager.PIN_LENGTH)
            Spacer(modifier = Modifier.height(32.dp))
            PinPad(
                onDigit = { digit -> viewModel.onDigit(digit, onComplete = onComplete) },
                onDelete = viewModel::onDelete
            )
        }
    }
}

// Bound to the cipher that will wrap the note's content key: the OS hands the
// authenticated cipher back only on success. Errors and cancellations both
// arrive as null — declining the shortcut is a legitimate choice, and setup
// has to finish either way rather than stranding the user on this screen.
private fun showBiometricEnrolmentPrompt(
    activity: FragmentActivity,
    cipher: Cipher,
    onFinished: (Cipher?) -> Unit
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onFinished(result.cryptoObject?.cipher)
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            onFinished(null)
        }
        // onAuthenticationFailed is deliberately not overridden: an
        // unrecognised finger leaves the prompt open for another try, and only
        // a real error or cancellation should end setup.
    }
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(activity.getString(R.string.note_biometric_enrol_title))
        .setSubtitle(activity.getString(R.string.note_biometric_enrol_subtitle))
        .setNegativeButtonText(activity.getString(R.string.note_biometric_enrol_skip))
        .build()
    BiometricPrompt(activity, executor, callback).authenticate(
        promptInfo,
        BiometricPrompt.CryptoObject(cipher)
    )
}
