package code.opensource0000.justnotes.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// tertiary is JustNotes' amber, reserved for anything tied to the secondary
// lock (e.g. the lock icon tint on a protected note in the Recents list) —
// never used as a general-purpose accent, to keep that meaning unambiguous.
private val DarkColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkAccentOn,
    primaryContainer = DarkAccentSoft,
    onPrimaryContainer = DarkAccentSoftOn,
    tertiary = DarkLock,
    onTertiary = DarkLockOn,
    tertiaryContainer = DarkLockSoft,
    onTertiaryContainer = DarkLockSoftOn,
    background = DarkBackground,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkInkMuted,
    outline = DarkInkFaint,
    outlineVariant = DarkBorder,
    error = DarkDanger,
    onError = DarkDangerOn
)

private val LightColorScheme = lightColorScheme(
    primary = LightAccent,
    onPrimary = LightAccentOn,
    primaryContainer = LightAccentSoft,
    onPrimaryContainer = LightAccentSoftOn,
    tertiary = LightLock,
    onTertiary = LightLockOn,
    tertiaryContainer = LightLockSoft,
    onTertiaryContainer = LightLockSoftOn,
    background = LightBackground,
    onBackground = LightInk,
    surface = LightSurface,
    onSurface = LightInk,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightInkMuted,
    outline = LightInkFaint,
    outlineVariant = LightBorder,
    error = LightDanger,
    onError = LightDangerOn
)

// Material You's wallpaper-derived colors are deliberately not offered: the
// palette in Color.kt is the app's identity, and letting the wallpaper
// override it would undo the one rule the design actually enforces — amber
// means the secondary lock, and nothing else.
@Composable
fun JustNotesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}