package com.example.aiconversation.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.aiconversation.R
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

    private val _rmsFlow = MutableStateFlow(0f)
    val rmsFlow: StateFlow<Float> = _rmsFlow.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ── SpeechRecognizer ─────────────────────────────────────────────────────
    private var recognizer: SpeechRecognizer? = null

    init {
        initRecognizer()
    }

    private fun initRecognizer() {
        if (recognizer != null) return
        
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(tag, "Speech recognition not available on this device")
            _errorMessage.value = context.getString(R.string.stt_error_unavailable)
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

    /** Start continuous listening. */
    fun startListening() {
        initRecognizer() // Ensure it's alive
        
        if (recognizer == null) {
            _errorMessage.value = context.getString(R.string.stt_error_not_init)
            Log.e(tag, "startListening() called but recognizer is null")
            return
        }

        _partialResult.value = ""
        _finalResult.value = ""
        _errorMessage.value = null
        _rmsFlow.value = 0f

        try {
            recognizer?.startListening(buildIntent())
            _isListening.value = true
            Log.d(tag, "STT started")
        } catch (e: Exception) {
            _errorMessage.value =
                context.getString(R.string.stt_error_start_failed) + ": ${e.message}"
            Log.e(tag, "Error in startListening", e)
            _isListening.value = false
        }
    }

    /** Stop recognition gracefully. */
    fun stopListening() {
        recognizer?.stopListening()
        _isListening.value = false
        _rmsFlow.value = 0f
        Log.d(tag, "STT stopped")
    }

    /** Release all resources. Call from ViewModel's onCleared(). */
    fun destroy() {
        recognizer?.destroy()
        recognizer = null
        _isListening.value = false
        _rmsFlow.value = 0f
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

        override fun onRmsChanged(rmsdB: Float) {
            // Normalize dB (typically -2 to 10) to 0.0 – 1.0 range for visuals
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            _rmsFlow.value = normalized
        }

        override fun onBufferReceived(buffer: ByteArray?) { /* Raw audio — unused */
        }

        override fun onEndOfSpeech() {
            // Note: Don't set _isListening = false here, wait for results or error
            // to keep the "active" state in UI until processing is done.
            Log.d(tag, "End of speech detected")
        }

        override fun onError(error: Int) {
            _isListening.value = false
            _rmsFlow.value = 0f
            val msg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> context.getString(R.string.stt_error_audio)
                SpeechRecognizer.ERROR_CLIENT -> context.getString(R.string.stt_error_client)
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> context.getString(R.string.stt_error_permissions)
                SpeechRecognizer.ERROR_NETWORK -> context.getString(R.string.stt_error_network)
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> context.getString(R.string.stt_error_network_timeout)
                SpeechRecognizer.ERROR_NO_MATCH -> context.getString(R.string.stt_error_no_match)
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> context.getString(R.string.stt_error_busy)
                SpeechRecognizer.ERROR_SERVER -> context.getString(R.string.stt_error_server)
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> context.getString(R.string.stt_error_timeout)
                else -> context.getString(R.string.stt_error_unknown) + " ($error)"
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
            _rmsFlow.value = 0f
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