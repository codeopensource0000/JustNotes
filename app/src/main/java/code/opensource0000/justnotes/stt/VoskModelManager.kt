package code.opensource0000.justnotes.stt

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class VoskModelState { NOT_INSTALLED, INSTALLING, READY, ERROR }

// Each entry ships as its own bundled asset (assets/vosk-model-*.zip,
// downloaded once at build time, never at runtime) and unpacks to its own
// private storage directory, independent of the other language.
enum class DictationLanguage(
    val assetName: String,
    private val dirName: String,
    private val readyMarkerName: String
) {
    FRENCH("vosk-model-fr.zip", "vosk-model-fr", "vosk-model-fr.ready"),
    ENGLISH("vosk-model-en.zip", "vosk-model-en", "vosk-model-en.ready");

    fun modelDir(context: Context): File = File(context.filesDir, dirName)
    fun readyMarker(context: Context): File = File(context.filesDir, readyMarkerName)
}

// Unpacks the bundled Vosk speech models for fully offline dictation, one
// per supported language. The app never requests network access, at any
// point, for anything — every model ships inside the APK itself.
class VoskModelManager private constructor(context: Context) {

    private val appContext = context.applicationContext

    private val states: Map<DictationLanguage, MutableStateFlow<VoskModelState>> =
        DictationLanguage.entries.associateWith { language ->
            val ready = language.readyMarker(appContext).exists()
            MutableStateFlow(if (ready) VoskModelState.READY else VoskModelState.NOT_INSTALLED)
        }

    fun state(language: DictationLanguage): StateFlow<VoskModelState> = states.getValue(language)

    fun modelDir(language: DictationLanguage): File = language.modelDir(appContext)

    // Caller decides the dispatcher (viewModelScope.launch(Dispatchers.IO)),
    // matching how NoteEditorViewModel wraps its own crypto calls.
    suspend fun install(language: DictationLanguage) {
        val stateFlow = states.getValue(language)
        if (stateFlow.value == VoskModelState.INSTALLING) return
        stateFlow.value = VoskModelState.INSTALLING
        try {
            extractZip(language)
            language.readyMarker(appContext).createNewFile()
            stateFlow.value = VoskModelState.READY
        } catch (e: IOException) {
            language.modelDir(appContext).deleteRecursively()
            stateFlow.value = VoskModelState.ERROR
        }
    }

    // The zip's entries are all nested under a single top-level folder (e.g.
    // "vosk-model-small-fr-0.22/"); that prefix is stripped away so the model
    // always lands directly at modelDir(), regardless of the model's version.
    private fun extractZip(language: DictationLanguage) {
        val targetDir = language.modelDir(appContext)
        targetDir.deleteRecursively()
        targetDir.mkdirs()
        ZipInputStream(appContext.assets.open(language.assetName)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val relativePath = entry.name.substringAfter('/', missingDelimiterValue = "")
                if (relativePath.isNotEmpty()) {
                    val outFile = File(targetDir, relativePath)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { output -> zip.copyTo(output) }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    fun delete(language: DictationLanguage) {
        language.modelDir(appContext).deleteRecursively()
        language.readyMarker(appContext).delete()
        states.getValue(language).value = VoskModelState.NOT_INSTALLED
    }

    companion object {
        @Volatile
        private var instance: VoskModelManager? = null

        fun getInstance(context: Context): VoskModelManager {
            return instance ?: synchronized(this) {
                instance ?: VoskModelManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
