package code.opensource0000.justnotes.security

import android.content.Context

// Everything protecting one note, retired in a single call.
//
// A locked note accumulates three secrets, created at three different moments:
// its code (PinManager), the optional fingerprint shortcut (BiometricShortcut),
// and the key derived from the code and held in memory for the session
// (NoteKeySession). They must always die together.
//
// There are five places where a note stops being protected — the lock being
// switched off, and deletion from the editor, the home list, a folder, or as
// part of deleting a whole folder. Repeating three cleanups across five call
// sites is how one of them eventually gets missed, leaving a live code or an
// orphaned Keystore entry behind for a note that no longer exists. The rule
// lives here instead, once.
object NoteSecrets {

    fun forget(context: Context, noteId: Long) {
        PinManager.forNote(context, noteId).clearPin()
        BiometricShortcut.delete(context, noteId)
        NoteKeySession.forget(noteId)
    }
}
