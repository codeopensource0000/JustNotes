package code.opensource0000.justnotes.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// One row per folder. Nested folders are supported by parentFolderId pointing
// to another folder's id; a null parentFolderId means a top-level folder.
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val parentFolderId: Long? = null,
    // The single catch-all folder ("Non classé") every install starts with.
    // Notes created without an explicit folder land here.
    val isDefault: Boolean = false
) {
    companion object {
        const val DEFAULT_FOLDER_NAME = "Non classé"
    }
}
