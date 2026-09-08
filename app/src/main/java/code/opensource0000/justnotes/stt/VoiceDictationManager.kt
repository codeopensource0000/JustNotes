package code.opensource0000.justnotes.stt

import android.content.Context
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

// Wraps the Vosk model/recognizer lifecycle for voice dictation. The Model
// is expensive to load (parses the extracted model files from disk) so it's
// cached here and reused across dictation sessions; the Recognizer and
// SpeechService are cheap and recreated fresh for each session.
class VoiceDictationManager private constructor(context: Context) {

    // Keyed by model path rather than a single field, so switching between
    // the French and English models doesn't force a reload every time —
    // each one loads once and stays cached for the rest of the app session.
    private val cachedModels = mutableMapOf<String, Model>()

    // The cache is filled from a background dispatcher and read from another,
    // so every touch of it goes through this lock. A plain map here was a race
    // waiting for the day two dictation sessions started at once.
    private val modelLock = Any()

    private var recognizer: Recognizer? = null
    private var speechService: SpeechService? = null

    // Blocking (reads the model files from disk) — call from a background
    // dispatcher, matching how NoteEditorViewModel wraps its own crypto calls.
    private fun loadModel(modelPath: String): Model = synchronized(modelLock) {
        cachedModels.getOrPut(modelPath) { Model(modelPath) }
    }

    // Closes the cached model for a path and forgets it. Called before the
    // files under it are deleted: a Model holds native memory and an open
    // view of those files, so dropping the reference alone would both leak
    // tens of megabytes and leave a handle onto a directory about to vanish.
    fun releaseModel(modelPath: String) = synchronized(modelLock) {
        cachedModels.remove(modelPath)?.close()
    }

    // onUtterance fires once per recognized phrase (Vosk detects sentence
    // boundaries on pauses); onError surfaces any failure back to the caller.
    // Must be called off the main thread, since loadModel() is blocking —
    // the RecognitionListener callbacks themselves are always delivered on
    // the main thread by SpeechService internally, so it's safe to mutate
    // Compose state directly from onUtterance/onError.
    fun startListening(modelPath: String, onUtterance: (String) -> Unit, onError: (Exception) -> Unit) {
        // Starting twice used to strand the previous SpeechService and
        // Recognizer, holding the microphone and native memory with nothing
        // left pointing at them. The editor's isListening flag makes that
        // unlikely, but the guard belongs here, next to what it protects.
        stopListening()
        try {
            val model = loadModel(modelPath)
            val newRecognizer = Recognizer(model, SAMPLE_RATE)
            recognizer = newRecognizer
            val service = SpeechService(newRecognizer, SAMPLE_RATE)
            speechService = service
            service.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String) {}
                override fun onResult(hypothesis: String) {
                    extractText(hypothesis)?.let(onUtterance)
                }
                override fun onFinalResult(hypothesis: String) {
                    extractText(hypothesis)?.let(onUtterance)
                }
                override fun onError(exception: Exception) {
                    onError(exception)
                }
                override fun onTimeout() {}
            })
        } catch (e: Exception) {
            onError(e)
        }
    }

    fun stopListening() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
        // shutdown() above only releases the AudioRecord, not the
        // Recognizer's own native resources — close it explicitly.
        recognizer?.close()
        recognizer = null
    }

    private fun extractText(hypothesisJson: String): String? {
        val text = JSONObject(hypothesisJson).optString("text").trim()
        return text.ifEmpty { null }
    }

    companion object {
        // Vosk models are trained on 16kHz audio; this must match what
        // AudioRecord captures internally in SpeechService.
        private const val SAMPLE_RATE = 16000.0f

        @Volatile
        private var instance: VoiceDictationManager? = null

        fun getInstance(context: Context): VoiceDictationManager {
            return instance ?: synchronized(this) {
                instance ?: VoiceDictationManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
