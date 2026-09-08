package code.opensource0000.justnotes.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// Instrumented rather than a plain JVM test: this exercises real
// SharedPreferences and the platform's real PBKDF2, which is the point. The
// properties below are the ones the whole secondary lock rests on — if any of
// them stops holding, notes either become unreadable or stop being protected.
//
// Each test uses its own note id so the preference files never collide.
@RunWith(AndroidJUnit4::class)
class PinManagerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun manager(noteId: Long) = PinManager.forNote(context, noteId)

    @Before
    fun clearTestNotes() {
        (900L..920L).forEach { manager(it).clearPin() }
    }

    @Test
    fun setPin_then_hasPin() {
        val pin = manager(900)
        assertFalse(pin.hasPin())
        pin.setPin("123456")
        assertTrue(manager(900).hasPin())
    }

    @Test
    fun correctCode_unlocks_and_yields_a_key() {
        manager(901).setPin("123456")
        val unlocked = manager(901).unlock("123456")
        assertNotNull(unlocked)
        assertEquals("AES", unlocked!!.contentKey.algorithm)
        // AES-256.
        assertEquals(32, unlocked.contentKey.encoded.size)
    }

    @Test
    fun wrongCode_does_not_unlock() {
        manager(902).setPin("123456")
        assertNull(manager(902).unlock("654321"))
    }

    @Test
    fun unlock_before_any_code_is_set_returns_null() {
        assertNull(manager(903).unlock("123456"))
    }

    // The content key is never stored, only rebuilt — so the same code has to
    // produce the same bytes every time, or a note becomes unreadable the
    // moment it is closed.
    @Test
    fun sameCode_rebuilds_the_same_key() {
        manager(904).setPin("123456")
        val first = manager(904).unlock("123456")!!.contentKey.encoded
        val second = manager(904).unlock("123456")!!.contentKey.encoded
        assertArrayEquals(first, second)
    }

    // Per-note isolation: identical codes on two notes must still give
    // different keys, otherwise cracking one note would open the other.
    @Test
    fun sameCode_on_two_notes_gives_different_keys() {
        manager(905).setPin("123456")
        manager(906).setPin("123456")
        val a = manager(905).unlock("123456")!!.contentKey.encoded
        val b = manager(906).unlock("123456")!!.contentKey.encoded
        assertFalse(a.contentEquals(b))
    }

    // Changing the code must re-salt, so the old ciphertext is not silently
    // still decryptable with the new key.
    @Test
    fun settingANewCode_produces_a_different_key() {
        manager(907).setPin("123456")
        val before = manager(907).unlock("123456")!!.contentKey.encoded
        manager(907).setPin("123456")
        val after = manager(907).unlock("123456")!!.contentKey.encoded
        assertFalse(before.contentEquals(after))
    }

    @Test
    fun clearPin_makes_the_note_unopenable() {
        manager(908).setPin("123456")
        manager(908).clearPin()
        assertFalse(manager(908).hasPin())
        assertNull(manager(908).unlock("123456"))
    }

    @Test
    fun repeatedFailures_trigger_a_lockout() {
        val pin = manager(909)
        pin.setPin("123456")
        assertEquals(0L, pin.lockoutRemainingMillis())
        repeat(5) { pin.unlock("000000") }
        assertTrue("a lockout should be in force after 5 misses", pin.lockoutRemainingMillis() > 0)
    }

    @Test
    fun lockout_blocks_even_the_correct_code() {
        val pin = manager(910)
        pin.setPin("123456")
        repeat(5) { pin.unlock("000000") }
        assertNull("the right code must not bypass a lockout", pin.unlock("123456"))
    }

    @Test
    fun a_correct_code_before_the_limit_clears_the_failure_count() {
        val pin = manager(911)
        pin.setPin("123456")
        repeat(4) { pin.unlock("000000") }
        assertNotNull(pin.unlock("123456"))
        assertEquals(0L, pin.lockoutRemainingMillis())
        // The counter is back to zero, so four more misses stay under the limit.
        repeat(4) { pin.unlock("000000") }
        assertEquals(0L, pin.lockoutRemainingMillis())
    }

    @Test
    fun settingACode_clears_any_lockout() {
        val pin = manager(912)
        pin.setPin("123456")
        repeat(5) { pin.unlock("000000") }
        assertTrue(pin.lockoutRemainingMillis() > 0)
        pin.setPin("654321")
        assertEquals(0L, pin.lockoutRemainingMillis())
        assertNotNull(pin.unlock("654321"))
    }
}
