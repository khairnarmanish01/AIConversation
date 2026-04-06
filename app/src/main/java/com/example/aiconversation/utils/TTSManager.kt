package com.example.aiconversation.utils

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Manages Android TextToSpeech.
 *
 * Exposes:
 *  - [isSpeaking]            — true while TTS engine is actively speaking.
 *  - [currentVisemeWeights]  — 21-viseme weight map, updated per word during speech.
 *  - [speak]                 — enqueues text for speech.
 *  - [stop]                  — interrupts current speech.
 *  - [shutdown]              — releases TTS engine resources.
 */
class TTSManager(private val context: Context) {

    private val tag = "TTSManager"

    // ── State ────────────────────────────────────────────────────────────────
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentRange = MutableStateFlow<Pair<Int, Int>?>(null)
    val currentRange: StateFlow<Pair<Int, Int>?> = _currentRange.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    // ── TTS Engine ───────────────────────────────────────────────────────────
    private var tts: TextToSpeech? = null
    private val visemeGenerator = VisemeGenerator()

    init {
        initEngine()
    }

    private fun initEngine() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Log.e(tag, "Language not supported")
                    _isReady.value = false
                } else {
                    configureSpeechRate()
                    attachProgressListener()
                    _isReady.value = true
                    Log.d(tag, "TTS engine ready")
                }
            } else {
                Log.e(tag, "TTS initialisation failed, status=$status")
                _isReady.value = false
            }
        }
    }

    private fun configureSpeechRate() {
        tts?.setSpeechRate(0.95f)   // Slightly slower for clarity
        tts?.setPitch(1.05f)        // Slightly higher pitch for AI feel
    }

    private fun attachProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
                _currentRange.value = null
                Log.d(tag, "TTS started: $utteranceId")
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                _currentRange.value = null
                visemeGenerator.stop()
                Log.d(tag, "TTS done: $utteranceId")
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                visemeGenerator.stop()
                Log.e(tag, "TTS error: $utteranceId")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
                visemeGenerator.stop()
                Log.e(tag, "TTS error code=$errorCode for: $utteranceId")
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                super.onRangeStart(utteranceId, start, end, frame)
                _currentRange.value = start to end
            }
        })
    }

    /**
     * Speaks the provided [text]. Flushes any current speech.
     */
    fun speak(text: String, onWordReady: (String, Long) -> Unit = { _, _ -> }) {
        if (!_isReady.value) {
            Log.w(tag, "TTS not ready, speak() ignored")
            return
        }
        val params = Bundle().apply {
            putString(
                TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID,
                "utt_${System.currentTimeMillis()}"
            )
        }
        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            params,
            params.getString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID)
        )
    }

    /**
     * Called when a TTS word range event fires. Extracts the word and
     * triggers viseme animation for both legacy [Viseme] and [VisemeWeights].
     */
    fun startVisemeForText(text: String, start: Int, end: Int) {
        if (start < text.length && end <= text.length) {
            val word = text.substring(start, end)
            val duration = (word.length * 150).toLong()
            visemeGenerator.startVisemesForWord(word, duration)
        }
    }

    /** Legacy viseme StateFlow (kept for backwards compat). */
    fun getCurrentViseme(): StateFlow<Viseme> = visemeGenerator.currentViseme

    /** 21-viseme weight StateFlow for the new PNG avatar. */
    fun getCurrentVisemeWeights(): StateFlow<VisemeWeights> = visemeGenerator.currentVisemeWeights

    /** Immediately stops any ongoing TTS. */
    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
        visemeGenerator.stop()
    }

    /** Call from ViewModel's onCleared() or Activity's onDestroy(). */
    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        _isReady.value = false
        Log.d(tag, "TTS shut down")
    }
}