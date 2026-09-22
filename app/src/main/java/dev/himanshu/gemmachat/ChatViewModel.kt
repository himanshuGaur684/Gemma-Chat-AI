package dev.himanshu.gemmachat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatMessage(
    val text: String,
    val fromUser: Boolean,
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


    private var gemmaEngine: GemmaEngine? = null

    init {
        viewModelScope.launch {
            try {
                _status.value = "Preparing model..."
                ModelDownloader.ensureModel(getApplication())

                _status.value = "Loading model..."
                gemmaEngine = GemmaEngine.create(getApplication())

                _status.value = ""
                _isReady.value = true

            } catch (e: Exception) {
                _status.value = "Something went wrong ${e.message}"
            }
        }
    }


    fun send(userText: String) {

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
            }
        }

    }

    override fun onCleared() {
        gemmaEngine?.close()
    }


}