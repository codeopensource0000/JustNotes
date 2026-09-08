package code.opensource0000.justnotes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import code.opensource0000.justnotes.R

// Shared by the PIN setup screen and the lock screen: a row of dots showing
// how many digits have been typed so far, without revealing the digits.
@Composable
fun PinDots(filledCount: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(total) { index ->
            val filled = index < filledCount
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(
                        if (filled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
            )
        }
    }
}

@Composable
fun PinPad(
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    // Greyed out while a lockout is counting down, so the wait is visible in
    // the pad itself rather than only in a line of text above it.
    enabled: Boolean = true
) {
    val digitRows = listOf(
        listOf(1, 2, 3),
        listOf(4, 5, 6),
        listOf(7, 8, 9)
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        digitRows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { digit ->
                    PinPadButton(
                        label = digit.toString(),
                        onClick = { onDigit(digit) },
                        enabled = enabled
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.size(64.dp))
            PinPadButton(label = "0", onClick = { onDigit(0) }, enabled = enabled)
            PinPadButton(
                label = "⌫",
                onClick = onDelete,
                enabled = enabled,
                // The only key whose glyph isn't its own name: without this,
                // a screen reader announces nothing useful for it.
                contentDescription = stringResource(R.string.pin_pad_delete)
            )
        }
    }
}

@Composable
private fun PinPadButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    contentDescription: String? = null
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(64.dp)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            ),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.titleLarge)
    }
}
