package code.opensource0000.justnotes

import android.app.Application
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import code.opensource0000.justnotes.security.PinManager
import code.opensource0000.justnotes.settings.SettingsManager
import code.opensource0000.justnotes.settings.ThemeMode
import code.opensource0000.justnotes.ui.SimpleViewModelFactory
import code.opensource0000.justnotes.ui.screens.AuthSetupViewModel
import code.opensource0000.justnotes.ui.screens.AuthUnlockViewModel
import code.opensource0000.justnotes.ui.screens.HomeScreen
import code.opensource0000.justnotes.ui.screens.LockScreen
import code.opensource0000.justnotes.ui.screens.NoteEditorScreen
import code.opensource0000.justnotes.ui.screens.PinSetupScreen
import code.opensource0000.justnotes.ui.screens.SettingsScreen
import code.opensource0000.justnotes.ui.theme.JustNotesTheme

// Route names as plain strings for now (kept simple while there are only a
// handful of screens); worth revisiting for type safety once navigation grows.
private const val ROUTE_PIN_SETUP = "pin_setup"
private const val ROUTE_LOCK = "lock"
private const val ROUTE_HOME = "home"
private const val ROUTE_EDITOR = "editor?noteId={noteId}"
private const val ARG_NOTE_ID = "noteId"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_REAUTH_FOR_CHANGE_PIN = "reauth_change_pin"
private const val ROUTE_CHANGE_PIN = "change_pin"
private const val ROUTE_REAUTH_FOR_SECONDARY_PIN = "reauth_secondary_pin"
private const val ROUTE_SECONDARY_PIN_SETUP = "secondary_pin_setup"
private const val ROUTE_SECONDARY_LOCK = "secondary_lock/{noteId}"

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val pinManager = remember { PinManager.forPrimary(context) }
            val settingsManager = remember { SettingsManager.getInstance(context) }

            // Collected here, above JustNotesTheme, so a change in Settings
            // recomputes darkTheme and recolors the whole app immediately.
            val themeMode by settingsManager.themeMode.collectAsState()
            val useDarkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            JustNotesTheme(darkTheme = useDarkTheme) {
                // Decided once, when the Activity is created: no PIN yet means
                // this is a fresh install and setup must happen first; a PIN
                // that exists but with auth disabled skips straight to Home.
                val startDestination = remember {
                    when {
                        !pinManager.hasPin() -> ROUTE_PIN_SETUP
                        pinManager.isAuthEnabled() -> ROUTE_LOCK
                        else -> ROUTE_HOME
                    }
                }
                val navController = rememberNavController()
                // Each screen below manages its own Scaffold (top bar, FAB...)
                // as needed, rather than one shared Scaffold here for every screen.
                NavHost(navController = navController, startDestination = startDestination) {
                    composable(ROUTE_PIN_SETUP) {
                        PinSetupScreen(
                            onComplete = {
                                navController.navigate(ROUTE_HOME) {
                                    popUpTo(ROUTE_PIN_SETUP) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(ROUTE_LOCK) {
                        LockScreen(
                            onUnlocked = {
                                navController.navigate(ROUTE_HOME) {
                                    popUpTo(ROUTE_LOCK) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(ROUTE_HOME) {
                        HomeScreen(
                            onCreateNote = { navController.navigate("editor?noteId=-1") },
                            onOpenNote = { noteId, isLocked ->
                                if (isLocked) {
                                    navController.navigate("secondary_lock/$noteId")
                                } else {
                                    navController.navigate("editor?noteId=$noteId")
                                }
                            },
                            onOpenSettings = { navController.navigate(ROUTE_SETTINGS) }
                        )
                    }
                    composable(
                        route = ROUTE_EDITOR,
                        arguments = listOf(
                            navArgument(ARG_NOTE_ID) {
                                type = NavType.LongType
                                defaultValue = -1L
                            }
                        )
                    ) { backStackEntry ->
                        val noteId = backStackEntry.arguments?.getLong(ARG_NOTE_ID) ?: -1L
                        NoteEditorScreen(
                            noteId = noteId,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(ROUTE_SETTINGS) {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                            onChangePin = { navController.navigate(ROUTE_REAUTH_FOR_CHANGE_PIN) },
                            onSetSecondaryPin = { navController.navigate(ROUTE_REAUTH_FOR_SECONDARY_PIN) }
                        )
                    }
                    composable(ROUTE_REAUTH_FOR_CHANGE_PIN) {
                        // Changing the PIN is a security-sensitive action: reuse
                        // the same lock screen (biometric auto-prompt + PIN
                        // fallback) to confirm identity before allowing it, so a
                        // briefly unattended unlocked phone can't be used to
                        // silently take over the code.
                        LockScreen(
                            onUnlocked = {
                                navController.navigate(ROUTE_CHANGE_PIN) {
                                    popUpTo(ROUTE_REAUTH_FOR_CHANGE_PIN) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(ROUTE_CHANGE_PIN) {
                        // Reuses the same create-a-PIN flow as first-run setup;
                        // here it just pops back to Settings instead of going Home.
                        PinSetupScreen(onComplete = { navController.popBackStack() })
                    }
                    composable(ROUTE_REAUTH_FOR_SECONDARY_PIN) {
                        // Same reasoning as ROUTE_REAUTH_FOR_CHANGE_PIN: setting
                        // the secondary code is also security-sensitive, gated
                        // by the primary lock (the only one guaranteed to exist).
                        LockScreen(
                            onUnlocked = {
                                navController.navigate(ROUTE_SECONDARY_PIN_SETUP) {
                                    popUpTo(ROUTE_REAUTH_FOR_SECONDARY_PIN) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(ROUTE_SECONDARY_PIN_SETUP) {
                        val application = context.applicationContext as Application
                        // Needs a custom factory: the default viewModel() factory
                        // can't be told to use PinManager.forSecondary() instead
                        // of the AuthSetupViewModel's primary-PIN default.
                        val secondarySetupViewModel: AuthSetupViewModel = viewModel(
                            factory = SimpleViewModelFactory {
                                AuthSetupViewModel(application, PinManager.forSecondary(application))
                            }
                        )
                        PinSetupScreen(
                            onComplete = { navController.popBackStack() },
                            viewModel = secondarySetupViewModel
                        )
                    }
                    composable(
                        route = ROUTE_SECONDARY_LOCK,
                        arguments = listOf(navArgument(ARG_NOTE_ID) { type = NavType.LongType })
                    ) { backStackEntry ->
                        val noteId = backStackEntry.arguments?.getLong(ARG_NOTE_ID) ?: -1L
                        val application = context.applicationContext as Application
                        val secondaryUnlockViewModel: AuthUnlockViewModel = viewModel(
                            factory = SimpleViewModelFactory {
                                AuthUnlockViewModel(application, PinManager.forSecondary(application))
                            }
                        )
                        LockScreen(
                            onUnlocked = {
                                navController.navigate("editor?noteId=$noteId") {
                                    popUpTo(ROUTE_SECONDARY_LOCK) { inclusive = true }
                                }
                            },
                            titleRes = R.string.secondary_lock_title,
                            viewModel = secondaryUnlockViewModel
                        )
                    }
                }
            }
        }
    }
}