package code.opensource0000.justnotes.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// One row per note. isLocked marks a note as protected by the secondary lock;
// the actual at-rest encryption of its content is a later step, not yet wired here.
@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            // Deleting a folder deletes the notes inside it.
            onDelete = ForeignKey.CASCADE
        )
    ],
    // Room recommends indexing foreign key columns for query performance.
    indices = [Index("folderId")]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val folderId: Long,
    val title: String,
    val content: String = "",
    val isLocked: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)
