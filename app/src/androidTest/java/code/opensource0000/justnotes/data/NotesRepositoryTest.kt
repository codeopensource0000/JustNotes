package code.opensource0000.justnotes.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import code.opensource0000.justnotes.data.local.JustNotesDatabase
import code.opensource0000.justnotes.data.local.NoteEntity
import code.opensource0000.justnotes.security.PinManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// The repository exists mainly to own one rule: a locked note's secrets die
// with its row. These tests are that rule, written down.
//
// Against an in-memory database, so they never touch the notes actually on the
// device — but with the real SharedPreferences behind PinManager, since that is
// precisely the half of the deletion that used to be forgotten.
@RunWith(AndroidJUnit4::class)
class NotesRepositoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: JustNotesDatabase
    private lateinit var repository: NotesRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, JustNotesDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomNotesRepository(context, database)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun lockedNoteIn(folderId: Long): NoteEntity {
        val id = repository.insertNote(
            NoteEntity(
                folderId = folderId,
                title = "locked",
                content = "cipher",
                isLocked = true,
                createdAt = 0,
                updatedAt = 0
            )
        )
        PinManager.forNote(context, id).setPin("123456")
        return repository.note(id)!!
    }

    @Test
    fun ensureDefaultFolder_creates_one_then_stops() = runBlocking {
        assertNull(repository.defaultFolder())
        repository.ensureDefaultFolder()
        val first = repository.defaultFolder()
        assertTrue(first != null && first.isDefault)
        // Called on every app start, so it has to be idempotent.
        repository.ensureDefaultFolder()
        assertEquals(first!!.id, repository.defaultFolder()!!.id)
    }

    @Test
    fun deletingALockedNote_also_retires_its_code() = runBlocking {
        val folderId = repository.createFolder("f")
        val note = lockedNoteIn(folderId)
        assertTrue(PinManager.forNote(context, note.id).hasPin())

        repository.deleteNote(note)

        assertNull("the row should be gone", repository.note(note.id))
        assertFalse(
            "the code outlived the note it protected",
            PinManager.forNote(context, note.id).hasPin()
        )
    }

    @Test
    fun deletingAnUnlockedNote_leaves_other_notes_secrets_alone() = runBlocking {
        val folderId = repository.createFolder("f")
        val plainId = repository.insertNote(
            NoteEntity(folderId = folderId, title = "plain", createdAt = 0, updatedAt = 0)
        )
        val stillLocked = lockedNoteIn(folderId)

        repository.deleteNote(repository.note(plainId)!!)

        assertTrue(PinManager.forNote(context, stillLocked.id).hasPin())
        PinManager.forNote(context, stillLocked.id).clearPin()
    }

    // The cascade removes the rows; nothing in Room knows those notes owned
    // secrets living outside it. This is the variant that used to be written
    // out at its own call site, and so the likeliest to have drifted.
    @Test
    fun deletingAFolder_retires_the_codes_of_every_locked_note_inside() = runBlocking {
        val folderId = repository.createFolder("doomed")
        val a = lockedNoteIn(folderId)
        val b = lockedNoteIn(folderId)
        assertTrue(PinManager.forNote(context, a.id).hasPin())
        assertTrue(PinManager.forNote(context, b.id).hasPin())

        repository.deleteFolder(folderId)

        assertNull(repository.note(a.id))
        assertNull(repository.note(b.id))
        assertFalse(PinManager.forNote(context, a.id).hasPin())
        assertFalse(PinManager.forNote(context, b.id).hasPin())
    }

    @Test
    fun movingANote_changes_only_its_folder() = runBlocking {
        val from = repository.createFolder("from")
        val to = repository.createFolder("to")
        val id = repository.insertNote(
            NoteEntity(folderId = from, title = "n", createdAt = 0, updatedAt = 0)
        )
        repository.moveNote(id, to)
        assertEquals(to, repository.note(id)!!.folderId)
        assertEquals("n", repository.note(id)!!.title)
    }
}
