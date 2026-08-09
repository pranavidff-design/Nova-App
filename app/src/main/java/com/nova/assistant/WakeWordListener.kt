package com.nova.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Listens continuously for "Hey Nova" while this is active, and hands off the
 * command that follows. This only works while Nova's app is in the foreground —
 * true background wake-word detection needs a dedicated engine (e.g. Picovoice)
 * running as a foreground service, which is a later-phase upgrade, same as the
 * original spec listed it (V1 = mic button, background service = later).
 */
class WakeWordListener(
    private val context: Context,
    private val onWakeDetected: () -> Unit,
    private val onCommandHeard: (String) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private var isActive = false
    private var awake = false

    fun start() {
        if (isActive) return
        isActive = true
        listenOnce()
    }

    fun stop() {
        isActive = false
        awake = false
        recognizer?.destroy()
        recognizer = null
    }

    private fun listenOnce() {
        if (!isActive) return
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val heard = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                val lower = heard.lowercase()

                if (!awake && (lower.contains("hey nova") || lower.contains("hi nova"))) {
                    awake = true
                    onWakeDetected()
                    val remainder = lower.replace(Regex("hey nova|hi nova"), "").trim()
                    if (remainder.length > 2) {
                        awake = false
                        onCommandHeard(remainder)
                    }
                } else if (awake) {
                    awake = false
                    onCommandHeard(heard)
                }
                restart()
            }

            override fun onError(error: Int) { restart() }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
        }
        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            restart()
        }
    }

    private fun restart() {
        recognizer?.destroy()
        if (isActive) listenOnce()
    }
}
