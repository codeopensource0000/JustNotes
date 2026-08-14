package code.opensource0000.justnotes.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Encrypts note content at rest for notes protected by the secondary lock.
// Each note gets its own Android Keystore key (never a shared one) — the key
// itself never leaves the Keystore in usable form, and a key belonging to one
// note gives no help at all decrypting any other note's key or content.
object NoteEncryption {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS_PREFIX = "justnotes_note_lock_key_"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12

    // How long a successful system authentication (fingerprint/face, or the
    // secondary-lock code's fallback confirmation) keeps this key usable.
    // Long enough to cover reading/writing a note in one sitting without
    // re-prompting on every keystroke, short enough to actually mean something.
    private const val AUTH_VALIDITY_SECONDS = 300

    fun encrypt(noteId: Long, plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(noteId))
        // GCM needs a fresh, random IV (nonce) per encryption; it's not secret,
        // so storing it right alongside the ciphertext is the normal approach —
        // decryption just needs to be given the exact same one back.
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = iv + cipherText
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(noteId: Long, encoded: String): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, IV_LENGTH_BYTES)
        val cipherText = combined.copyOfRange(IV_LENGTH_BYTES, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(noteId), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }

    // Called when a note's secondary lock is turned off — nothing is
    // encrypted with this key anymore, so there's no reason to keep it.
    fun deleteKey(noteId: Long) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.deleteEntry(keyAlias(noteId))
    }

    private fun keyAlias(noteId: Long) = "$KEY_ALIAS_PREFIX$noteId"

    private fun getOrCreateKey(noteId: Long): SecretKey {
        val alias = keyAlias(noteId)
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // This is the actual hardening: without it, the key is usable by
            // any code running as this app regardless of whether the user
            // ever unlocked anything. With it, the OS itself refuses to use
            // the key unless a real biometric/device-credential check
            // succeeded within AUTH_VALIDITY_SECONDS — our own PIN screen
            // alone can no longer be bypassed to reach decrypted content.
            .setUserAuthenticationRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                AUTH_VALIDITY_SECONDS,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(AUTH_VALIDITY_SECONDS)
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }
}
