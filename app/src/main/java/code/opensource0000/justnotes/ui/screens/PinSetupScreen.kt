package code.opensource0000.justnotes.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import code.opensource0000.justnotes.R
import code.opensource0000.justnotes.security.PinManager
import code.opensource0000.justnotes.ui.components.PinDots
import code.opensource0000.justnotes.ui.components.PinPad

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
