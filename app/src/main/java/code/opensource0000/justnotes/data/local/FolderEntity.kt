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
    // The single catch-all folder every install starts with. Notes created
    // without an explicit folder land here. isDefault, not the name, is what
    // identifies it — see DEFAULT_FOLDER_NAME below.
    val isDefault: Boolean = false
) {
    companion object {
        // Written once at first launch and never shown: the UI resolves the
        // default folder through folderLabel(), which returns a localised
        // string instead. Kept as a stable internal marker so existing
        // databases keep matching.
        const val DEFAULT_FOLDER_NAME = "Non classé"
    }
}
