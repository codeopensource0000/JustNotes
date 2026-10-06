package code.opensource0000.justnotes.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// The one failure in this app that has no recovery: a schema change shipped
// without a migration. Room refuses to open a database whose stored schema
// disagrees with the compiled one, so the app dies on launch for everyone who
// already installed the previous version, and their only way out is to
// uninstall — which takes their notes with it.
//
// A correction to what an earlier version of this file claimed. It asserted
// that rebuilding a database from app/schemas and reopening it would catch a
// drifted entity. It does not: Room *rewrites* that schema file on every
// build, silently, so both sides of the comparison move together and the
// check compares the schema to itself. Verified by doing it — adding a column
// without bumping the version left this test green.
//
// An oracle has to be independent of the thing it measures. Two now are:
//   - .github/workflows/build.yml refuses a commit where a schema file
//     changed, which is where the real invariant lives: a committed schema
//     must never change again. Only git can see that.
//   - the identity hash below, written down here rather than read back from
//     the file Room regenerates.
//
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

    // Room stores the hash of the schema the *compiled code* expects in
    // room_master_table when it opens a database. Comparing that to a constant
    // written down here is independent of app/schemas, which is what makes it
    // a real check rather than a tautology.
    @Test
    fun compiledSchema_stillMatchesTheReleasedVersion1() {
        helper.createDatabase(TEST_DB, 1).close()
        val database = openWithCurrentCode()

        val compiledHash = database.openHelper.writableDatabase
            .query("SELECT identity_hash FROM room_master_table LIMIT 1")
            .use { cursor ->
                cursor.moveToFirst()
                cursor.getString(0)
            }
        database.close()

        if (compiledHash != RELEASED_V1_IDENTITY_HASH) {
            fail("""The schema has changed since last update.
             Please increment DB and release new migration and
             commit new schema""".trimIndent())
        }

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

        // The schema released as 1.0.0, from
        // app/schemas/…JustNotesDatabase/1.json at tag v1.0.0. Frozen on
        // purpose: this is the copy Room cannot rewrite.
        const val RELEASED_V1_IDENTITY_HASH = "a0c878fc46db28f7b7d383289951698d"
    }
}
