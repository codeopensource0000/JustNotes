package code.opensource0000.justnotes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE folderId = :folderId ORDER BY updatedAt DESC")
    fun observeByFolder(folderId: Long): Flow<List<NoteEntity>>

    // One-shot (not a Flow): used when deleting a folder, to find which of
    // its notes were locked before the cascade removes their rows.
    @Query("SELECT * FROM notes WHERE folderId = :folderId")
    suspend fun getByFolder(folderId: Long): List<NoteEntity>

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE notes SET folderId = :folderId WHERE id = :id")
    suspend fun updateFolderId(id: Long, folderId: Long)
}
