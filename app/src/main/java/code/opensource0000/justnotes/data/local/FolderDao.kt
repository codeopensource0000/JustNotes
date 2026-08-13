package code.opensource0000.justnotes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// Not a @Entity: a plain result shape for the joined query below, combining
// a folder with a count computed from the notes table.
data class FolderWithNoteCount(
    val id: Long,
    val name: String,
    val isDefault: Boolean,
    val noteCount: Int
)

@Dao
interface FolderDao {

    @Insert
    suspend fun insert(folder: FolderEntity): Long

    // Flow: Room re-runs this query and emits a new list automatically
    // whenever the folders or notes tables change, no manual refresh needed.
    // LEFT JOIN keeps folders with zero notes (COUNT would otherwise drop them).
    @Query(
        """
        SELECT folders.id AS id, folders.name AS name, folders.isDefault AS isDefault,
               COUNT(notes.id) AS noteCount
        FROM folders
        LEFT JOIN notes ON notes.folderId = folders.id
        GROUP BY folders.id
        ORDER BY folders.isDefault ASC, folders.name ASC
        """
    )
    fun observeAllWithNoteCount(): Flow<List<FolderWithNoteCount>>

    @Query("SELECT * FROM folders WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultFolder(): FolderEntity?
}
