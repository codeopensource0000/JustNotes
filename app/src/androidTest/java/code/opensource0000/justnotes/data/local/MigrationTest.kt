package code.opensource0000.justnotes.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Guards the one failure in this app that has no recovery: a schema change
// shipped without a migration. Room refuses to open a database whose stored
// schema does not match the compiled one, so the app dies on launch for
// everyone who already installed the previous version — and their only way
// out is to uninstall, which takes their notes with it.
//
// There is only version 1 today, so there is no migration to exercise yet.
// What these tests do have is the exported schema in app/schemas, and that is
// enough for the check that matters right now: rebuild a database exactly as
// the released version created it, then open it with the code as it stands.
// Room compares its identity hash and refuses if anything drifted.
//
// Concretely: change a column on NoteEntity, forget to bump `version`, and
// this test fails. That is the mistake it exists to catch.
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, JustNotesDatabase::class.java)

    private fun openWithCurrentCode() = Room.databaseBuilder(
        instrumentation.targetContext,
        JustNotesDatabase::class.java,
        TEST_DB
    )
        .addMigrations(*ALL_MIGRATIONS)
        .build()

    @Test
    fun schemaVersion1_stillOpensWithTheCurrentCode() {
        // Built from app/schemas/…/1.json, not from the current entities —
        // which is the whole point: this is the database a released install
        // actually has on disk.
        helper.createDatabase(TEST_DB, 1).close()

        val database = openWithCurrentCode()
        // Opening is the assertion: Room validates the stored schema against
        // the compiled one here and throws if they disagree.
        val version = database.openHelper.writableDatabase.version
        assertTrue("the database should open at version 1 or later", version >= 1)
        database.close()
    }

    @Test
    fun version1_hasTheTablesTheAppExpects() {
        val db = helper.createDatabase(TEST_DB, 1)
        val tables = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type='table'").use { cursor ->
            while (cursor.moveToNext()) tables += cursor.getString(0)
        }
        db.close()
        assertTrue("notes table missing from the exported schema", "notes" in tables)
        assertTrue("folders table missing from the exported schema", "folders" in tables)
    }

    // The cascade is declared on the entity, but SQLite enforces it, and only
    // when foreign keys are switched on for the connection. Room does that per
    // connection — which is easy to lose in a migration that recreates a table.
    @Test
    fun deletingAFolder_stillTakesItsNotesWithIt() {
        helper.createDatabase(TEST_DB, 1).close()
        val database = openWithCurrentCode()

        val db = database.openHelper.writableDatabase
        db.execSQL("INSERT INTO folders (id, name, isDefault) VALUES (1, 'f', 0)")
        db.execSQL(
            "INSERT INTO notes (id, folderId, title, content, isLocked, createdAt, updatedAt) " +
                "VALUES (1, 1, 't', '', 0, 0, 0)"
        )
        db.execSQL("DELETE FROM folders WHERE id = 1")

        var remaining = -1
        db.query("SELECT COUNT(*) FROM notes").use { cursor ->
            cursor.moveToFirst()
            remaining = cursor.getInt(0)
        }
        database.close()
        assertEquals("the note outlived the folder it belonged to", 0, remaining)
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
