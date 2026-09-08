package code.opensource0000.justnotes.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FolderEntity::class, NoteEntity::class],
    version = 1,
    // Writes app/schemas/<db>/<version>.json at build time, and those files are
    // committed. They are the reference every future migration is written and
    // tested against — and version 1's schema can only be captured before
    // version 1 ships. Without them, changing the schema later means either
    // guessing at the migration or wiping everyone's notes.
    exportSchema = true
)
abstract class JustNotesDatabase : RoomDatabase() {

    abstract fun folderDao(): FolderDao
    abstract fun noteDao(): NoteDao

    companion object {
        private const val DATABASE_NAME = "justnotes.db"

        @Volatile
        private var instance: JustNotesDatabase? = null

        // Simple double-checked-locking singleton: the whole app must share one
        // database connection instead of opening the SQLite file multiple times.
        fun getInstance(context: Context): JustNotesDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    JustNotesDatabase::class.java,
                    DATABASE_NAME
                ).build().also { instance = it }
            }
        }
    }
}
