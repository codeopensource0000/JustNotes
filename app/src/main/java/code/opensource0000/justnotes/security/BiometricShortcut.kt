package code.opensource0000.justnotes.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// An optional fingerprint shortcut to a locked note.
//
// A fingerprint cannot derive anything — PinManager.unlock() turns the note's
// code into its content key, and no biometric check can reproduce that. What a
// fingerprint *can* do is open a safe. So this keeps a copy of the content key
// encrypted under an Android Keystore key that the OS will only use after a
// successful biometric check, and hands the key back once that check passes.
//
// The important property is what happens when it breaks. The Keystore key is
// permanently invalidated whenever the device's screen lock is removed or a
// fingerprint is enrolled — and here that costs nothing but convenience: the
// copy becomes unreadable, we throw it away, and the code still opens the note
// exactly as before. An earlier version of this app kept the *only* copy of
// the key in the Keystore, which turned the same everyday event into
// unrecoverable data loss. The shortcut must never become the only way in.
object BiometricShortcut {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS_PREFIX = "justnotes_bio_key_"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128

    // One file for every note's wrapped key, rather than one file per note:
    // turning the setting off has to erase all of them at once, and clearing a
    // single file is far more dependable than hunting down a set of them.
    private const val PREFS_NAME = "justnotes_note_bio"

    fun exists(context: Context, noteId: Long): Boolean =
        prefs(context).contains(wrappedKeyPref(noteId))

    // --- Enrolment: storing the wrapped key ------------------------------

    // The cipher to hand BiometricPrompt as a CryptoObject when setting the
    // shortcut up. Symmetric Keystore keys require authentication to encrypt
    // as well as to decrypt, which is why enabling the shortcut asks for a
    // fingerprint once — the alternative, an asymmetric key whose public half
    // needs no authentication, buys a silent enrolment for a good deal more
    // machinery.
    fun enrolmentCipher(noteId: Long): Cipher? = runCatching {
        Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, createKey(noteId))
        }
    }.getOrNull()

    // Called with the cipher BiometricPrompt has just authenticated.
    fun store(context: Context, noteId: Long, cipher: Cipher, contentKey: SecretKey): Boolean =
        runCatching {
            val wrapped = cipher.doFinal(contentKey.encoded)
            prefs(context).edit {
                putString(wrappedKeyPref(noteId), Base64.encodeToString(wrapped, Base64.NO_WRAP))
                putString(ivPref(noteId), Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            }
            true
        }.getOrDefault(false)

    // --- Unlock: getting the key back ------------------------------------

    // Null means "don't offer the fingerprint for this note": either there is
    // no shortcut, or the Keystore key behind it has been invalidated. In the
    // second case the dead shortcut is cleaned up on the way out, so the offer
    // stops coming back — the note itself is untouched and its code still works.
    fun unlockCipher(context: Context, noteId: Long): Cipher? {
        val storedIv = prefs(context).getString(ivPref(noteId), null) ?: return null
        return runCatching {
            val key = existingKey(noteId) ?: return null
            Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    key,
                    GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(storedIv, Base64.NO_WRAP))
                )
            }
        }.getOrElse {
            delete(context, noteId)
            null
        }
    }

    fun unwrap(context: Context, noteId: Long, cipher: Cipher): SecretKey? {
        val stored = prefs(context).getString(wrappedKeyPref(noteId), null) ?: return null
        return runCatching {
            SecretKeySpec(cipher.doFinal(Base64.decode(stored, Base64.NO_WRAP)), "AES")
        }.getOrNull()
    }

    // --- Cleanup ---------------------------------------------------------

    fun delete(context: Context, noteId: Long) {
        prefs(context).edit {
            remove(wrappedKeyPref(noteId))
            remove(ivPref(noteId))
        }
        runCatching { keyStore().deleteEntry(alias(noteId)) }
    }

    // Used when the setting is switched off: every wrapped copy goes, and so
    // does every Keystore entry behind them. Aliases are enumerable, so this
    // needs no record of which notes ever had a shortcut.
    fun deleteAll(context: Context) {
        prefs(context).edit { clear() }
        runCatching {
            val keyStore = keyStore()
            keyStore.aliases()
                .toList()
                .filter { it.startsWith(KEY_ALIAS_PREFIX) }
                .forEach { keyStore.deleteEntry(it) }
        }
    }

    // --- Internals -------------------------------------------------------

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun alias(noteId: Long) = "$KEY_ALIAS_PREFIX$noteId"

    private fun wrappedKeyPref(noteId: Long) = "wrapped_$noteId"

    private fun ivPref(noteId: Long) = "iv_$noteId"

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun existingKey(noteId: Long): SecretKey? =
        keyStore().getKey(alias(noteId), null) as? SecretKey

    // Generating against an alias that already exists replaces it, which is
    // what re-enrolling a note should do.
    private fun createKey(noteId: Long): SecretKey {
        val builder = KeyGenParameterSpec.Builder(
            alias(noteId),
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            // Kills the shortcut when a fingerprint is added or removed. Unlike
            // the time-bound key this app used to create, that flag genuinely
            // applies to a per-use key — and here the failure is harmless.
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Timeout 0 means one authentication per use, which is what makes
            // the CryptoObject meaningful: the fingerprint is bound to this
            // exact cipher operation rather than opening a window in which
            // anything could use the key.
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(builder.build()) }
            .generateKey()
    }
}
