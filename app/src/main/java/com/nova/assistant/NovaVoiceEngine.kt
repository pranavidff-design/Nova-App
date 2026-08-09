package com.nova.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Wraps text-to-speech so the rest of the app never talks to a TTS provider directly.
 * Part 1: Android's built-in TTS, tuned toward the "calm, slightly low, unhurried" voice
 * described in the spec. Swapping in a premium engine (e.g. ElevenLabs) later means
 * rewriting only this file — nothing else in the app needs to change.
 */
class NovaVoiceEngine(context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setPitch(0.92f)   // slightly lower pitch, per spec
                tts?.setSpeechRate(0.93f) // slightly slower, calm delivery
                pickFemaleVoice()
                ready = true
            }
        }
    }

    private fun pickFemaleVoice() {
        val voices = tts?.voices ?: return
        val preferred = voices.firstOrNull {
            it.name.contains("female", ignoreCase = true) && it.locale.language == "en"
        }
        preferred?.let { tts?.voice = it }
    }

    fun speak(text: String) {
        if (!ready) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nova_utterance")
    }

    /** Stops mid-sentence — backs the "interruptible speech" requirement from the spec. */
    fun stopSpeaking() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
