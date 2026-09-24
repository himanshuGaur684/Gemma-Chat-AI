package dev.himanshu.gemmachat

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMessage(
    val text: String,
    val fromUser: Boolean,
    val imageUri: Uri? = null,
    val id: String = java.util.UUID.randomUUID().toString()
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady = _isReady.asStateFlow()

    private val _status = MutableStateFlow("Preparing model...")
    val status = _status.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening = _isListening.asStateFlow()


    private var gemmaEngine: GemmaEngine? = null
    private val voice = VoiceController(app)

    init {
        voice.onListeningChange = { _isListening.value = it }
        voice.onResult = { spokenText -> send(spokenText, speak = true) }
        voice.onError = { msg -> _status.value = msg }

        viewModelScope.launch {
            try {
                _status.value = "Loading model..."
                gemmaEngine = GemmaEngine.create(getApplication())

                _status.value = ""
                _isReady.value = true

            } catch (e: Exception) {
                _status.value = "Something went wrong ${e.message}"
            }
        }
    }

    fun startListening() {
        if (_isGenerating.value) return
        voice.startListening()
    }

    fun stopListening() = voice.stopListening()


    fun send(userText: String, speak: Boolean = false) {

        val engine = gemmaEngine ?: return
        if (_isGenerating.value) return

        viewModelScope.launch {

            _messages.value += ChatMessage(userText, true)
            _messages.value += ChatMessage("", false)

            _isGenerating.value = true

            try {
                engine.reply(userText).collect { chunk ->
                    val current = _messages.value.toMutableList()
                    val last = current.last()

                    current[current.size - 1] = last.copy(text = last.text.plus(chunk))
                    _messages.value = current

                }
            } catch (e: Exception) {
                val current = _messages.value.toMutableList()
                val last = current.last()
                current[current.size - 1] = last.copy(text = "Something went wrong ${e.message}")
                _messages.value = current
            } finally {
                _isGenerating.value = false
                // Speak Jarvis's reply aloud when the request came from voice.
                if (speak) voice.speak(_messages.value.lastOrNull()?.text.orEmpty())
            }
        }
    }

    fun sendImage(uri: Uri, prompt: String = "Describe this image in detail.") {
        val engine = gemmaEngine ?: return
        if (_isGenerating.value) return

        _messages.value += ChatMessage(prompt, true, imageUri = uri)
        _messages.value += ChatMessage("", false)


        viewModelScope.launch {
            // Read the picked image into bytes (off the main thread).
            val bytes = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver
                    .openInputStream(uri)?.use { it.readBytes() }
            }
            if (bytes == null) {
                _messages.value += ChatMessage("⚠️ Could not read the image", false)
                return@launch
            }

            _isGenerating.value = true

            try {
                engine.replyWithImage(bytes, prompt).collect { chunk ->
                    val current = _messages.value.toMutableList()
                    val last = current.last()
                    current[current.size - 1] = last.copy(text = last.text.plus(chunk))
                    _messages.value = current
                }
            } catch (e: Exception) {
                val current = _messages.value.toMutableList()
                val last = current.last()
                current[current.size - 1] = last.copy(text = "Something went wrong ${e.message}")
                _messages.value = current
            } finally {
                _isGenerating.value = false
            }
        }
    }

    override fun onCleared() {
        gemmaEngine?.close()
        voice.shutdown()
    }


}