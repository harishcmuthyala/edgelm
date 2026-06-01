package com.example.edgelm_summarizer

import android.content.Context
import org.pytorch.executorch.extension.llm.LlmCallback
import org.pytorch.executorch.extension.llm.LlmModule
import java.io.File
import java.io.FileOutputStream

class LlamaRunner(private val context: Context) {

    private var module: LlmModule? = null
    private var outputBuffer = StringBuilder()
    private var summaryStarted = false

    companion object {
        private const val MODEL_FILE      = "model.pte"
        private const val TOKENIZER_FILE  = "tokenizer.model"
        private const val TEMPERATURE     = 0.8f
        private const val MAX_TOKENS      = 1024
        const val MAX_INPUT_WORDS         = 200

        private const val PROMPT_TEMPLATE = """Summarize the following text in exactly 3 sentences. Write in plain prose, no bullet points, no lists, no headers. Just 3 sentences.

Text:
%s

Summary:"""

        private val STOP_TOKENS = listOf(
            "<|eot_id|>", "<|end_of_text|>", "<|start_header_id|>",
            "<|end_header_id|>", "et_id", "<|"
        )

        fun truncateInput(text: String): Pair<String, Boolean> {
            val words = text.trim().split("\\s+".toRegex())
            return if (words.size > MAX_INPUT_WORDS) {
                Pair(words.take(MAX_INPUT_WORDS).joinToString(" "), true)
            } else {
                Pair(text.trim(), false)
            }
        }

        fun wordCount(text: String): Int =
            text.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
    }

    fun load(): Boolean {
        return try {
            val modelPath     = copyAssetToCache(MODEL_FILE)
            val tokenizerPath = copyAssetToCache(TOKENIZER_FILE)
            module = LlmModule(modelPath, tokenizerPath, TEMPERATURE)
            val result = module!!.load()
            result == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun summarize(
        text: String,
        onToken: (String) -> Unit,
        onComplete: () -> Unit
    ) {
        // Recreate module before each call to reset KV cache pos_ to 0
        try {
            val modelPath     = copyAssetToCache(MODEL_FILE)
            val tokenizerPath = copyAssetToCache(TOKENIZER_FILE)
            module = LlmModule(modelPath, tokenizerPath, TEMPERATURE)
            module!!.load()
            android.util.Log.d("LlamaRunner", "Module recreated, KV cache reset")
        } catch (e: Exception) {
            android.util.Log.e("LlamaRunner", "Failed to recreate module: ${e.message}")
            onComplete()
            return
        }

        val (truncatedText, wasTruncated) = truncateInput(text)
        val prompt = PROMPT_TEMPLATE.format(truncatedText)
        val wordCount = wordCount(truncatedText)

        outputBuffer = StringBuilder()
        summaryStarted = false
        var completed = false
        var tokenCount = 0
        val startTime = System.currentTimeMillis()

        android.util.Log.d("LlamaRunner", "=== Generation Start ===")
        android.util.Log.d("LlamaRunner", "Input words: $wordCount")
        android.util.Log.d("LlamaRunner", "Input truncated: $wasTruncated")
        android.util.Log.d("LlamaRunner", "Prompt length (chars): ${prompt.length}")

        fun safeComplete() {
            if (!completed) {
                completed = true
                val elapsed = System.currentTimeMillis() - startTime
                android.util.Log.d("LlamaRunner", "=== Generation Complete ===")
                android.util.Log.d("LlamaRunner", "Total tokens: $tokenCount")
                android.util.Log.d("LlamaRunner", "Total time: ${elapsed}ms")
                android.util.Log.d("LlamaRunner", "Full output: ${outputBuffer}")
                onComplete()
            }
        }

        if (wasTruncated) {
            onToken("[Input truncated to $MAX_INPUT_WORDS words]\n\n")
        }

        try {
            module?.generate(prompt, MAX_TOKENS, object : LlmCallback {
                override fun onResult(token: String) {
                    if (STOP_TOKENS.any { token.contains(it) }) {
                        android.util.Log.d("LlamaRunner", "Stop token detected: '$token'")
                        safeComplete()
                        return
                    }

                    outputBuffer.append(token)
                    val fullOutput = outputBuffer.toString()

                    if (!summaryStarted) {
                        val markerIndex = fullOutput.indexOf("Summary:")
                        if (markerIndex >= 0) {
                            summaryStarted = true
                            android.util.Log.d("LlamaRunner", "Summary marker found at token $tokenCount")
                            val afterMarker = fullOutput
                                .substring(markerIndex + "Summary:".length)
                                .trimStart('\n', ' ')
                            if (afterMarker.isNotEmpty()) {
                                onToken(afterMarker)
                            }
                        }
                    } else {
                        // Stop if model starts a new section (Key points, bullet points etc)
                        val summaryText = outputBuffer.toString()
                            .substringAfter("Summary:")
                        if (summaryText.contains("\n\nKey") ||
                            summaryText.contains("\n\n*") ||
                            summaryText.contains("\n\nNote") ||
                            summaryText.contains("\n\n-")) {
                            android.util.Log.d("LlamaRunner", "New section detected, stopping")
                            safeComplete()
                            return
                        }
                        onToken(token)
                    }
                }

                override fun onStats(stats: String) {
                    android.util.Log.d("LlamaRunner", "onStats called: $stats")
                    safeComplete()
                }
            }) ?: safeComplete()
        } catch (e: Exception) {
            android.util.Log.e("LlamaRunner", "Generation error: ${e.message}", e)
            safeComplete()
        }
    }

    fun stop() {
        module?.stop()
    }

    private fun copyAssetToCache(filename: String): String {
        val outFile = File(context.cacheDir, filename)
        if (!outFile.exists()) {
            context.assets.open(filename).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return outFile.absolutePath
    }
}