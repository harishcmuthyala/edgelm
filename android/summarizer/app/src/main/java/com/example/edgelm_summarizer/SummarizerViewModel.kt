package com.example.edgelm_summarizer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SummarizerUiState(
    val inputText: String  = "",
    val summary: String    = "",
    val isLoading: Boolean = false,
    val isModelReady: Boolean = false,
    val error: String?     = null
)

class SummarizerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SummarizerUiState())
    val uiState: StateFlow<SummarizerUiState> = _uiState

    private val runner = LlamaRunner(application)

    init {
        loadModel()
    }

    private fun loadModel() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val success = runner.load()
            _uiState.value = _uiState.value.copy(
                isLoading    = false,
                isModelReady = success,
                error        = if (!success) "Failed to load model" else null
            )
        }
    }

    fun updateInput(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun summarize() {
        val text = _uiState.value.inputText
        if (text.isBlank()) return

        _uiState.value = _uiState.value.copy(
            summary   = "",
            isLoading = true,
            error     = null
        )

        viewModelScope.launch(Dispatchers.IO) {
            runner.summarize(
                text      = text,
                onToken   = { token ->
                    _uiState.value = _uiState.value.copy(
                        summary = _uiState.value.summary + token
                    )
                },
                onComplete = {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            )
        }
    }

    fun stop() {
        runner.stop()
        _uiState.value = _uiState.value.copy(isLoading = false)
    }

    override fun onCleared() {
        super.onCleared()
        runner.stop()
    }
}