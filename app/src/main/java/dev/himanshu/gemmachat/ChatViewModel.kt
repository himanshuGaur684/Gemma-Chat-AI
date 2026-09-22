package dev.himanshu.gemmachat

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatMessage(val text: String, val fromUser: Boolean)

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady = _isReady.asStateFlow()

    private val _status = MutableStateFlow("Preparing model…")
    val status = _status.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    private var engine: GemmaEngine? = null

    init {
        viewModelScope.launch {
            try {
                _status.value = "Preparing model…"
                ModelDownloader.ensureModel(getApplication())   // copies asset → filesDir (first run only)

                _status.value = "Loading Gemma…"
                engine = GemmaEngine.create(getApplication())  // loads the model into memory

                _isReady.value = true
            } catch (e: Exception) {
                Log.d("TAGGGGGGGG", "Exception: ${e::class.simpleName} ${e.printStackTrace()} ")
                _status.value = "Failed to load model: ${e.message}"
            }
        }
    }

    fun send(userText: String) {
        val engine = engine ?: return
        if (_isGenerating.value) return   // ignore taps while a reply is streaming

        // Add the user's message + an empty AI bubble to fill as tokens stream in.
        _messages.value = _messages.value + ChatMessage(userText, fromUser = true)
        _messages.value = _messages.value + ChatMessage("", fromUser = false)

        viewModelScope.launch {
            _isGenerating.value = true
            try {
                engine.reply(userText).collect { chunk ->
                    val current = _messages.value.toMutableList()
                    val last = current.last()
                    current[current.size - 1] = last.copy(text = last.text + chunk)
                    _messages.value = current
                }
            } catch (e: Exception) {
                val current = _messages.value.toMutableList()
                current[current.size - 1] = ChatMessage("⚠️ Error: ${e.message}", fromUser = false)
                _messages.value = current
            } finally {
                _isGenerating.value = false
            }
        }
    }

    override fun onCleared() {
        engine?.close()   // release native resources
    }
}