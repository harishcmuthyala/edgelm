package com.example.edgelm_summarizer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SummarizerUiState(
    val inputText: String     = "",
    val summary: String       = "",
    val isLoading: Boolean    = false,
    val isModelReady: Boolean = false,
    val error: String?        = null,
    val wordCount: Int        = 0,
    val isOverLimit: Boolean  = false
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
            _uiState.update { it.copy(isLoading = true) }
            val success = runner.load()
            _uiState.update {
                it.copy(
                    isLoading    = false,
                    isModelReady = success,
                    error        = if (!success) "Failed to load model" else null
                )
            }
        }
    }

    fun updateInput(text: String) {
        val count     = LlamaRunner.wordCount(text)
        val overLimit = count > LlamaRunner.MAX_INPUT_WORDS
        _uiState.update {
            it.copy(
                inputText   = text,
                wordCount   = count,
                isOverLimit = overLimit
            )
        }
    }

    fun autoSummarize(text: String) {
        updateInput(text)
        viewModelScope.launch(Dispatchers.IO) {
            var waited = 0
            while (!_uiState.value.isModelReady && waited < 10000) {
                delay(100)
                waited += 100
            }
            if (_uiState.value.isModelReady) {
                startSummarize()
            }
        }
    }

    fun summarize() {
        viewModelScope.launch(Dispatchers.IO) {
            startSummarize()
        }
    }

    private fun startSummarize() {
        val text = _uiState.value.inputText
        if (text.isBlank()) return

        _uiState.update {
            it.copy(
                summary   = "",
                isLoading = true,
                error     = null
            )
        }

        runner.summarize(
            text       = text,
            onToken    = { token ->
                _uiState.update { it.copy(summary = it.summary + token) }
            },
            onComplete = {
                _uiState.update { it.copy(isLoading = false) }
            }
        )
    }

    fun stop() {
        runner.stop()
        _uiState.update { it.copy(isLoading = false) }
    }

    override fun onCleared() {
        super.onCleared()
        runner.stop()
    }
}