package code.opensource0000.justnotes.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FolderEntity::class, NoteEntity::class],
    version = 1,
    // We are not shipping schema history files yet; fine for early development.
    exportSchema = false
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
