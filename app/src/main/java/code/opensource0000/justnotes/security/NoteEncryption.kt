package code.opensource0000.justnotes.security

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Encrypts note content at rest for notes protected by the secondary lock.
//
// This object holds no key and knows no note id: the caller passes the key in,
// having got it from NoteKeySession, which got it from the code the user typed.
//
// Why not the Android Keystore, which this replaced: a Keystore key is
// isolated in hardware and cannot be extracted, which is strictly stronger —
// but a key created with setUserAuthenticationRequired is *permanently
// invalidated* the moment the device's screen lock is removed. Every locked
// note went with it, unrecoverably, with no warning and nothing to fall back
// on. Deriving the key from the note's own code trades hardware isolation for
// content that survives anything the device does to itself. The cost is real
// and worth stating plainly: someone holding a copy of the app's private data
// can try codes offline against the ciphertext, which is what the 210,000
// PBKDF2 iterations in PinManager are there to make expensive.
object NoteEncryption {

    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12

    fun encrypt(key: SecretKey, plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        // GCM needs a fresh, random IV (nonce) per encryption; it's not secret,
        // so storing it right alongside the ciphertext is the normal approach —
        // decryption just needs to be given the exact same one back.
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + cipherText, Base64.NO_WRAP)
    }

    // Throws (AEADBadTagException, via GeneralSecurityException) if the key is
    // wrong rather than returning garbage — GCM authenticates as well as
    // encrypts. Since the key comes straight from the code, a wrong key here
    // would mean the code was wrong, which the lock screen has already ruled
    // out; in practice a failure means the stored bytes are damaged.
    fun decrypt(key: SecretKey, encoded: String): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, IV_LENGTH_BYTES)
        val cipherText = combined.copyOfRange(IV_LENGTH_BYTES, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }
}
