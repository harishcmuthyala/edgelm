package com.example.edgelm_summarizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.edgelm_summarizer.ui.theme.EdgelmsummarizerTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: SummarizerViewModel by viewModels()

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    private val downloadState: StateFlow<DownloadState> = _downloadState

    sealed class DownloadState {
        object Idle : DownloadState()
        data class Downloading(val fileName: String, val percent: Int) : DownloadState()
        object Done : DownloadState()
        object Error : DownloadState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check if models need downloading
        if (!ModelDownloader.modelsExist(this)) {
            startDownload()
        } else {
            _downloadState.value = DownloadState.Done
        }

        setContent {
            EdgelmsummarizerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val dlState by downloadState.collectAsState()
                    when (dlState) {
                        is DownloadState.Done -> SummaryScreen(viewModel = viewModel)
                        is DownloadState.Error -> ErrorScreen(onRetry = { startDownload() })
                        else -> DownloadScreen(state = dlState)
                    }
                }
            }
        }
    }

    private fun startDownload() {
        _downloadState.value = DownloadState.Downloading("Starting...", 0)
        lifecycleScope.launch {
            val success = ModelDownloader.downloadAll(this@MainActivity) { fileName, percent ->
                _downloadState.value = DownloadState.Downloading(fileName, percent)
            }
            _downloadState.value = if (success) DownloadState.Done else DownloadState.Error
        }
    }
}

@Composable
fun DownloadScreen(state: MainActivity.DownloadState) {
    val downloading = state as? MainActivity.DownloadState.Downloading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .systemBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "edgelm",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "On-device summarization",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Downloading model...",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (downloading != null && downloading.percent > 0) {
            LinearProgressIndicator(
                progress = { downloading.percent / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${downloading.fileName}  ${downloading.percent}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "This only happens once (~1.1 GB)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ErrorScreen(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .systemBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Download failed",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Check your internet connection and try again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

// ── SummaryScreen stays exactly the same as before ────────────────────────
@Composable
fun SummaryScreen(viewModel: SummarizerViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboard = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .systemBarsPadding()
    ) {
        Text(
            text = "edgelm",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "On-device summarization",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (!uiState.isModelReady && !uiState.isLoading) {
            Text(
                text = uiState.error ?: "Model not loaded",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (uiState.isLoading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (!uiState.isModelReady) "Loading model..."
                    else if (uiState.summary.isEmpty()) "Processing input..."
                    else "Generating summary...",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = uiState.inputText,
            onValueChange = { viewModel.updateInput(it) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.45f),
            placeholder = { Text("Paste your article or text here...") },
            enabled = !uiState.isLoading
        )

        if (uiState.inputText.isNotEmpty()) {
            Text(
                text = "${uiState.wordCount} words" +
                        if (uiState.isOverLimit) " — will be truncated to ${LlamaRunner.MAX_INPUT_WORDS}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (uiState.isOverLimit)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (uiState.isLoading) viewModel.stop()
                else viewModel.summarize()
            },
            enabled = uiState.isModelReady,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    !uiState.isModelReady -> "Loading model..."
                    uiState.isLoading     -> "Stop"
                    else                  -> "Summarize"
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Summary", fontWeight = FontWeight.SemiBold)
            if (uiState.summary.isNotEmpty()) {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(uiState.summary))
                }) {
                    Text("Copy")
                }
            }
        }

        if (uiState.isLoading && uiState.summary.isNotEmpty()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(4.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.55f)
        ) {
            Text(
                text = if (uiState.summary.isEmpty() && !uiState.isLoading)
                    "Summary will appear here..."
                else
                    uiState.summary,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
                color = if (uiState.summary.isEmpty())
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.onSurface
            )
        }
    }
}