package com.example.edgelm_summarizer

import android.content.Context
import org.pytorch.executorch.extension.llm.LlmCallback
import org.pytorch.executorch.extension.llm.LlmModule
import java.io.File
import java.io.FileOutputStream

class LlamaRunner(private val context: Context) {

    private var module: LlmModule? = null

    companion object {
        private const val MODEL_FILE     = "model.pte"
        private const val TOKENIZER_FILE = "tokenizer.model"
        private const val TEMPERATURE    = 0.8f
        private const val MAX_TOKENS     = 150

        private const val PROMPT_TEMPLATE = """Summarize the following text in 3 sentences. Be concise and capture the key points.

Text:
%s

Summary:"""
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

    fun summarize(text: String, onToken: (String) -> Unit, onComplete: () -> Unit) {
        val prompt = PROMPT_TEMPLATE.format(text.trim())
        module?.generate(prompt, MAX_TOKENS, object : LlmCallback {
            override fun onResult(token: String) {
                onToken(token)
            }
            override fun onStats(stats: String) {
                onComplete()
            }
        }) ?: onComplete()
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