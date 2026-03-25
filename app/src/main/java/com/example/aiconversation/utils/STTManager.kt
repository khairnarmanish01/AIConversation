package com.example.aiconversation.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Wraps Android [SpeechRecognizer] and exposes recognition state as [StateFlow].
 *
 * Exposes:
 *  - [isListening]     — true while recogniser is active.
 *  - [partialResult]   — live partial transcription text.
 *  - [finalResult]     — committed final transcription text.
 *  - [errorMessage]    — last error description or null.
 */
class STTManager(private val context: Context) {

    private val tag = "STTManager"

    // ── State Flows ──────────────────────────────────────────────────────────
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialResult = MutableStateFlow("")
    val partialResult: StateFlow<String> = _partialResult.asStateFlow()

    private val _finalResult = MutableStateFlow("")
    val finalResult: StateFlow<String> = _finalResult.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ── SpeechRecognizer ─────────────────────────────────────────────────────
    private var recognizer: SpeechRecognizer? = null

    init {
        initRecognizer()
    }

    private fun initRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(tag, "Speech recognition not available on this device")
            _errorMessage.value = "Speech recognition not available"
            return
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(buildListener())
        Log.d(tag, "SpeechRecognizer initialised")
    }

    private fun buildIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

    /** Start continuous listening. */
    fun startListening() {
        if (recognizer == null) {
            _errorMessage.value = "Speech recognizer not initialized"
            Log.e(tag, "startListening() called but recognizer is null")
            return
        }

        _partialResult.value = ""
        _finalResult.value = ""
        _errorMessage.value = null

        try {
            recognizer?.startListening(buildIntent())
            _isListening.value = true
            Log.d(tag, "STT started")
        } catch (e: Exception) {
            _errorMessage.value = "Failed to start listening: ${e.message}"
            Log.e(tag, "Error in startListening", e)
        }
    }

    /** Stop recognition gracefully. */
    fun stopListening() {
        recognizer?.stopListening()
        _isListening.value = false
        Log.d(tag, "STT stopped")
    }

    /** Release all resources. Call from ViewModel's onCleared(). */
    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        _isListening.value = false
        Log.d(tag, "STT destroyed")
    }

    // ── Recognition Listener ─────────────────────────────────────────────────
    private fun buildListener() = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            _errorMessage.value = null
            Log.d(tag, "Ready for speech")
        }

        override fun onBeginningOfSpeech() {
            Log.d(tag, "Speech begun")
        }

        override fun onRmsChanged(rmsdB: Float) { /* Volume meter — unused */
        }

        override fun onBufferReceived(buffer: ByteArray?) { /* Raw audio — unused */
        }

        override fun onEndOfSpeech() {
            _isListening.value = false
            Log.d(tag, "End of speech")
        }

        override fun onError(error: Int) {
            _isListening.value = false
            val msg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech match found"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recogniser busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                else -> "Unknown error ($error)"
            }
            _errorMessage.value = msg
            Log.e(tag, "STT error: $msg")
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""
            _finalResult.value = text
            _partialResult.value = ""
            _isListening.value = false
            Log.d(tag, "Final result: $text")
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull() ?: ""
            _partialResult.value = partial
            Log.d(tag, "Partial: $partial")
        }

        override fun onEvent(eventType: Int, params: Bundle?) { /* Unused */
        }
    }
}