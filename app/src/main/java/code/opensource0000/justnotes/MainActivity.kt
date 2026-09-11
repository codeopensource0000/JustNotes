package code.opensource0000.justnotes

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import code.opensource0000.justnotes.export.NoteExporter
import code.opensource0000.justnotes.security.PinManager
import code.opensource0000.justnotes.settings.LocaleContextWrapper
import code.opensource0000.justnotes.settings.SettingsManager
import code.opensource0000.justnotes.settings.ThemeMode
import code.opensource0000.justnotes.ui.SimpleViewModelFactory
import code.opensource0000.justnotes.ui.screens.AboutScreen
import code.opensource0000.justnotes.ui.screens.AuthSetupViewModel
import code.opensource0000.justnotes.ui.screens.AuthUnlockViewModel
import code.opensource0000.justnotes.ui.screens.FolderScreen
import code.opensource0000.justnotes.ui.screens.FolderViewModel
import code.opensource0000.justnotes.ui.screens.HomeScreen
import code.opensource0000.justnotes.ui.screens.LockScreen
import code.opensource0000.justnotes.ui.screens.NoteEditorScreen
import code.opensource0000.justnotes.ui.screens.PinSetupScreen
import code.opensource0000.justnotes.ui.screens.SettingsScreen
import code.opensource0000.justnotes.ui.theme.JustNotesTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Route names as plain strings for now (kept simple while there are only a
// handful of screens); worth revisiting for type safety once navigation grows.
private const val ROUTE_PIN_SETUP = "pin_setup"
private const val ROUTE_LOCK = "lock"
private const val ROUTE_HOME = "home"
private const val ROUTE_EDITOR = "editor?noteId={noteId}&folderId={folderId}"
private const val ARG_NOTE_ID = "noteId"
private const val ARG_FOLDER_ID = "folderId"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_REAUTH_FOR_CHANGE_PIN = "reauth_change_pin"
private const val ROUTE_REAUTH_FOR_DISABLE_AUTH = "reauth_disable_auth"
private const val ROUTE_REAUTH_FOR_SCREENSHOTS = "reauth_screenshots"
private const val ROUTE_RELOCK = "relock"
private const val ROUTE_ABOUT = "about"

// How long the app may sit in the background before the lock screen comes
// back. Long enough to glance at a notification or paste something in from
// another app without being challenged; short enough that a phone left on a
// table is not left open.
private const val RELOCK_GRACE_MILLIS = 60_000L
private const val ROUTE_CHANGE_PIN = "change_pin"
private const val ROUTE_SECONDARY_LOCK = "secondary_lock/{noteId}"
private const val ROUTE_NOTE_PIN_SETUP = "note_pin_setup/{noteId}"
private const val ROUTE_FOLDER = "folder/{folderId}"

// The primary code, spelled out. These exist so that no screen can fall back
// to it by accident: AuthUnlockViewModel and AuthSetupViewModel have no
// default PinManager any more, so every route has to say which code it means.
@Composable
private fun primaryUnlockViewModel(): AuthUnlockViewModel {
    val application = LocalContext.current.applicationContext as Application
    return viewModel(
        factory = SimpleViewModelFactory {
            AuthUnlockViewModel(application, PinManager.forPrimary(application), noteId = null)
        }
    )
}

@Composable
private fun primarySetupViewModel(): AuthSetupViewModel {
    val application = LocalContext.current.applicationContext as Application
    return viewModel(
        factory = SimpleViewModelFactory {
            AuthSetupViewModel(application, PinManager.forPrimary(application), noteId = null)
        }
    )
}

class MainActivity : FragmentActivity() {

    // When the app was last backgrounded, and whether coming back should put
    // the lock screen up. elapsedRealtime rather than the wall clock: the
    // grace period must not be extendable by changing the phone's time.
    private var backgroundedAt = 0L
    private val relockRequested = mutableStateOf(false)

    // Applies the user's chosen app display language (Settings > Général),
    // independent of the phone's system language — read before onCreate,
    // so every string resource resolved from here on already uses it.
    override fun attachBaseContext(newBase: Context) {
        val language = SettingsManager.getInstance(newBase).appLanguage.value
        super.attachBaseContext(LocaleContextWrapper.wrap(newBase, language))
    }

    override fun onStop() {
        super.onStop()
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    // Re-asserted on every resume, not just at creation and when the setting
    // changes. Window flags are state held by the system on our behalf, and
    // the one failure that matters here is silent: nothing tells the app if
    // the flag is not in force, it just quietly becomes screenshottable.
    // Re-applying costs nothing and removes a whole class of "it was set once
    // and something dropped it" doubt.
    override fun onResume() {
        super.onResume()
        applyScreenshotPolicy()
    }

    // The single place that decides whether this window is capturable, so the
    // three callers cannot drift apart.
    private fun applyScreenshotPolicy() {
        val allowed = SettingsManager.getInstance(this).screenshotsAllowed.value
        if (allowed) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
    }

    override fun onStart() {
        super.onStart()
        // backgroundedAt is 0 on the very first start, which is already
        // handled by startDestination below.
        if (backgroundedAt == 0L) return
        val awayMillis = SystemClock.elapsedRealtime() - backgroundedAt
        if (awayMillis < RELOCK_GRACE_MILLIS) return
        val pinManager = PinManager.forPrimary(this)
        if (pinManager.hasPin() && pinManager.authEnabled.value) {
            relockRequested.value = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Applied before any content is drawn, so the window is never briefly
        // capturable at launch.
        applyScreenshotPolicy()
        // Exported notes are written to the cache and handed to the share
        // sheet, which means the plain text of a locked note can outlive the
        // note itself. Clearing at launch bounds how long that lasts; the
        // files cannot be deleted right after sharing, since the receiving
        // app may still be reading them.
        lifecycleScope.launch(Dispatchers.IO) { NoteExporter.purgeCache(this@MainActivity) }
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val pinManager = remember { PinManager.forPrimary(context) }
            val settingsManager = remember { SettingsManager.getInstance(context) }

            // Collected here, above JustNotesTheme, so a change in Settings
            // recomputes darkTheme and recolors the whole app immediately.
            val themeMode by settingsManager.themeMode.collectAsState()
            val biometricForNotes by settingsManager.biometricForNotesEnabled.collectAsState()

            // Screenshots (and the task-switcher thumbnail, which is the part
            // that leaks without anyone meaning to) are blocked unless the
            // user has explicitly allowed them — a change that costs a trip
            // through the lock screen, see ROUTE_REAUTH_FOR_SCREENSHOTS.
            val screenshotsAllowed by settingsManager.screenshotsAllowed.collectAsState()
            LaunchedEffect(screenshotsAllowed) { applyScreenshotPolicy() }
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
                        pinManager.authEnabled.value -> ROUTE_LOCK
                        else -> ROUTE_HOME
                    }
                }
                val navController = rememberNavController()

                // Pushed on top of whatever was open rather than replacing it:
                // the editor stays on the back stack with its ViewModel alive,
                // so unlocking returns to the note exactly as it was, unsaved
                // changes included. Clearing the stack here would quietly
                // discard them.
                LaunchedEffect(relockRequested.value) {
                    if (relockRequested.value) {
                        relockRequested.value = false
                        navController.navigate(ROUTE_RELOCK)
                    }
                }
                // Each screen below manages its own Scaffold (top bar, FAB...)
                // as needed, rather than one shared Scaffold here for every screen.
                NavHost(navController = navController, startDestination = startDestination) {
                    composable(ROUTE_PIN_SETUP) {
                        PinSetupScreen(
                            onComplete = {
                                navController.navigate(ROUTE_HOME) {
                                    popUpTo(ROUTE_PIN_SETUP) { inclusive = true }
                                }
                            },
                            viewModel = primarySetupViewModel()
                        )
                    }
                    composable(ROUTE_LOCK) {
                        LockScreen(
                            onUnlocked = {
                                navController.navigate(ROUTE_HOME) {
                                    popUpTo(ROUTE_LOCK) { inclusive = true }
                                }
                            },
                            viewModel = primaryUnlockViewModel()
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
                            onOpenFolder = { folderId -> navController.navigate("folder/$folderId") },
                            onOpenSettings = { navController.navigate(ROUTE_SETTINGS) }
                        )
                    }
                    composable(
                        route = ROUTE_EDITOR,
                        arguments = listOf(
                            navArgument(ARG_NOTE_ID) {
                                type = NavType.LongType
                                defaultValue = -1L
                            },
                            navArgument(ARG_FOLDER_ID) {
                                type = NavType.LongType
                                defaultValue = -1L
                            }
                        )
                    ) { backStackEntry ->
                        val noteId = backStackEntry.arguments?.getLong(ARG_NOTE_ID) ?: -1L
                        val folderId = backStackEntry.arguments?.getLong(ARG_FOLDER_ID) ?: -1L
                        NoteEditorScreen(
                            noteId = noteId,
                            initialFolderId = folderId,
                            onBack = { navController.popBackStack() },
                            onRequestNotePinSetup = { lockedNoteId ->
                                navController.navigate("note_pin_setup/$lockedNoteId")
                            }
                        )
                    }
                    composable(
                        route = ROUTE_FOLDER,
                        arguments = listOf(navArgument(ARG_FOLDER_ID) { type = NavType.LongType })
                    ) { backStackEntry ->
                        val folderId = backStackEntry.arguments?.getLong(ARG_FOLDER_ID) ?: return@composable
                        val application = context.applicationContext as Application
                        val folderViewModel: FolderViewModel = viewModel(
                            // Custom factory: FolderViewModel needs the folderId from
                            // this specific navigation entry, which the default
                            // reflection-based viewModel() factory has no way to supply.
                            factory = SimpleViewModelFactory { FolderViewModel(application, folderId) }
                        )
                        FolderScreen(
                            onBack = { navController.popBackStack() },
                            onOpenNote = { noteId, isLocked ->
                                if (isLocked) {
                                    navController.navigate("secondary_lock/$noteId")
                                } else {
                                    navController.navigate("editor?noteId=$noteId")
                                }
                            },
                            onCreateNote = { navController.navigate("editor?noteId=-1&folderId=$folderId") },
                            viewModel = folderViewModel
                        )
                    }
                    composable(ROUTE_RELOCK) {
                        // Same lock screen, but it returns where it came from
                        // instead of going Home, and cannot be dismissed with
                        // the back gesture.
                        LockScreen(
                            onUnlocked = { navController.popBackStack() },
                            blockSystemBack = true,
                            viewModel = primaryUnlockViewModel()
                        )
                    }
                    composable(ROUTE_SETTINGS) {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                            onChangePin = { navController.navigate(ROUTE_REAUTH_FOR_CHANGE_PIN) },
                            onDisableAuth = { navController.navigate(ROUTE_REAUTH_FOR_DISABLE_AUTH) },
                            onAllowScreenshots = { navController.navigate(ROUTE_REAUTH_FOR_SCREENSHOTS) },
                            onOpenAbout = { navController.navigate(ROUTE_ABOUT) }
                        )
                    }
                    composable(ROUTE_ABOUT) {
                        AboutScreen(onBack = { navController.popBackStack() })
                    }
                    composable(ROUTE_REAUTH_FOR_SCREENSHOTS) {
                        // Allowing captures is the loosening direction, so it
                        // is the one that has to be proved. Turning them back
                        // off tightens and needs nothing.
                        LockScreen(
                            onUnlocked = {
                                settingsManager.setScreenshotsAllowed(true)
                                navController.popBackStack()
                            },
                            viewModel = primaryUnlockViewModel()
                        )
                    }
                    composable(ROUTE_REAUTH_FOR_DISABLE_AUTH) {
                        // Turning the lock off is as security-sensitive as
                        // changing the code — arguably more so, since it
                        // removes the gate entirely. It used to be a single
                        // tap on a switch, which left the phone's own owner
                        // as the only thing standing in the way.
                        LockScreen(
                            onUnlocked = {
                                PinManager.forPrimary(context).setAuthEnabled(false)
                                navController.popBackStack()
                            },
                            viewModel = primaryUnlockViewModel()
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
                            },
                            viewModel = primaryUnlockViewModel()
                        )
                    }
                    composable(ROUTE_CHANGE_PIN) {
                        // Reuses the same create-a-PIN flow as first-run setup;
                        // here it just pops back to Settings instead of going Home.
                        PinSetupScreen(
                            onComplete = { navController.popBackStack() },
                            viewModel = primarySetupViewModel()
                        )
                    }
                    composable(
                        route = ROUTE_NOTE_PIN_SETUP,
                        arguments = listOf(navArgument(ARG_NOTE_ID) { type = NavType.LongType })
                    ) { backStackEntry ->
                        val noteId = backStackEntry.arguments?.getLong(ARG_NOTE_ID) ?: return@composable
                        val application = context.applicationContext as Application
                        // Needs a custom factory: the default viewModel() factory
                        // can't be told which note's PinManager.forNote() to use.
                        val notePinSetupViewModel: AuthSetupViewModel = viewModel(
                            factory = SimpleViewModelFactory {
                                AuthSetupViewModel(
                                    application,
                                    PinManager.forNote(application, noteId),
                                    noteId
                                )
                            }
                        )
                        PinSetupScreen(
                            onComplete = { navController.popBackStack() },
                            hintTextRes = R.string.pin_setup_hint_note,
                            blockSystemBack = true,
                            viewModel = notePinSetupViewModel
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
                                AuthUnlockViewModel(
                                    application,
                                    PinManager.forNote(application, noteId),
                                    noteId
                                )
                            }
                        )
                        LockScreen(
                            onUnlocked = {
                                navController.navigate("editor?noteId=$noteId") {
                                    popUpTo(ROUTE_SECONDARY_LOCK) { inclusive = true }
                                }
                            },
                            titleRes = R.string.secondary_lock_title,
                            // A fingerprint cannot derive this note's key, but
                            // it can unwrap a stored copy of it — provided the
                            // user left that shortcut switched on.
                            allowBiometrics = biometricForNotes,
                            viewModel = secondaryUnlockViewModel
                        )
                    }
                }
            }
        }
    }
}
