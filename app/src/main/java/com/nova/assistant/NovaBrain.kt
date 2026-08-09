package com.nova.assistant

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import java.io.File

/**
 * NovaBrain — runs entirely ON-DEVICE. No API key, no internet, no cost.
 *
 * Uses Google's MediaPipe LLM Inference API to run a local Gemma 4 model file.
 * The model itself (a multi-GB .task file) is NOT bundled in this project —
 * you download it once and push it to the phone (see README: "Downloading the model").
 *
 * Tradeoff vs. cloud (documented honestly, not hidden):
 * - Free forever, fully private, works with no signal / airplane mode
 * - Noticeably less sharp than Claude on complex, multi-step reasoning
 * - Cannot know live information (news, weather, current events) — see
 *   NEEDS_INTERNET_KEYWORDS below, where Nova says so instead of guessing
 */
class NovaBrain(private val context: Context) {

    private var llmInference: LlmInference? = null
    private var modelReady = false

    // Model file location on the phone. Defaults here, but can be overridden by
    // copyPickedModelFile() if the user selects it via the in-app file picker
    // instead of pushing it with adb (phone-only setups need this path).
    private var modelPath = "${context.getExternalFilesDir(null)}/models/gemma-4-e2b.task"

    /** Call this after the user picks the downloaded .task file via Storage Access Framework.
     *  Copies it into the app's own storage so it persists and MediaPipe can read it directly. */
    fun copyPickedModelFile(uri: android.net.Uri, onDone: (Boolean) -> Unit) {
        try {
            val destDir = File("${context.getExternalFilesDir(null)}/models")
            if (!destDir.exists()) destDir.mkdirs()
            val destFile = File(destDir, "gemma-4-e2b.task")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            modelPath = destFile.absolutePath
            onDone(true)
        } catch (e: Exception) {
            onDone(false)
        }
    }

    private val systemPrompt = """
        You are Nova, a calm, warm, precise personal voice assistant.
        Keep replies short (1-3 sentences) since they are spoken aloud, not read.
        Speak English, Hindi, or Hinglish depending on how the user speaks to you.
        Never claim to have done something you weren't actually told was executed.
    """.trimIndent()

    // Topics the local model can't answer accurately because it has no live data access.
    // Rather than let it guess/hallucinate a "current" answer, Nova is honest about the gap.
    private val needsInternetKeywords = listOf(
        "weather", "today's news", "latest news", "current price", "score today",
        "kal ka mausam", "aaj ka mausam"
    )

    fun initialize(onReady: (Boolean) -> Unit) {
        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            onReady(false)
            return
        }
        try {
            val options = LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(512)
                .setTopK(40)
                .setTemperature(0.7f)
                .build()
            llmInference = LlmInference.createFromOptions(context, options)
            modelReady = true
            onReady(true)
        } catch (e: Exception) {
            modelReady = false
            onReady(false)
        }
    }

    fun ask(userText: String, contextBlock: String, onReply: (String) -> Unit) {
        if (!modelReady || llmInference == null) {
            onReply("My local AI model isn't loaded yet — check the README's model setup step.")
            return
        }

        val lower = userText.lowercase()
        if (needsInternetKeywords.any { lower.contains(it) }) {
            onReply("I can't check that live — I run fully offline. Connect me to a weather/news source later if you want that added.")
            return
        }

        try {
            val prompt = buildString {
                append(systemPrompt)
                if (contextBlock.isNotBlank()) {
                    append("\n\n")
                    append(contextBlock)
                }
                append("\n\nUser: $userText\nNova:")
            }
            val result = llmInference?.generateResponse(prompt) ?: "I didn't catch that — could you repeat it?"
            onReply(result.trim())
        } catch (e: Exception) {
            onReply("I had trouble thinking that through — could you try rephrasing?")
        }
    }

    fun shutdown() {
        llmInference?.close()
    }
}
