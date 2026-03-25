package com.example.aiconversation.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiconversation.R
import com.example.aiconversation.data.model.Message
import com.example.aiconversation.data.model.Sender
import com.example.aiconversation.utils.CameraController
import com.example.aiconversation.utils.STTManager
import com.example.aiconversation.utils.TTSManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Central ViewModel — coordinates TTS, STT, Camera, and the message list.
 *
 * Uses [AndroidViewModel] so it can construct the managers that need a [Context].
 */
class ConversationViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "ConversationViewModel"

    // ── Managers ─────────────────────────────────────────────────────────────
    val ttsManager = TTSManager(application)
    val sttManager = STTManager(application)
    val cameraController = CameraController(application)
    private val preferencesRepository =
        com.example.aiconversation.data.UserPreferencesRepository(application)

    // ── UI State ─────────────────────────────────────────────────────────────
    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    // ── Sample AI responses for demo ─────────────────────────────────────────
    private val aiResponses = listOf(
        application.getString(R.string.ai_response_1),
        application.getString(R.string.ai_response_2),
        application.getString(R.string.ai_response_3)
    )
    private var responseIndex = 0

    init {
        observeManagers()
        addInitialGreeting(application)

        preferencesRepository.isCameraOnFlow
            .onEach { isOn -> _uiState.update { it.copy(isCameraOn = isOn) } }
            .launchIn(viewModelScope)
    }

    // ── Observation ───────────────────────────────────────────────────────────

    private fun observeManagers() {
        // Merge TTS state into uiState
        ttsManager.isSpeaking
            .onEach { speaking ->
                _uiState.update {
                    it.copy(
                        isSpeaking = speaking,
                        currentExpression = if (speaking) Expression.SPEAKING else if (it.isListening) Expression.LISTENING else Expression.NEUTRAL
                    )
                }
            }
            .launchIn(viewModelScope)

        ttsManager.isReady
            .onEach { ready -> _uiState.update { it.copy(isTtsReady = ready) } }
            .launchIn(viewModelScope)

        ttsManager.getCurrentViseme()
            .onEach { viseme -> _uiState.update { it.copy(currentViseme = viseme) } }
            .launchIn(viewModelScope)

        ttsManager.currentRange
            .onEach { range ->
                _uiState.update { it.copy(currentAiRange = range) }
                range?.let { (start, end) ->
                    val lastMsg = _uiState.value.messages.lastOrNull { it.sender == Sender.AI }
                    lastMsg?.let { msg ->
                        ttsManager.startVisemeForText(msg.text, start, end)
                    }
                }
            }
            .launchIn(viewModelScope)

        // Merge STT listening state
        sttManager.isListening
            .onEach { listening ->
                _uiState.update {
                    it.copy(
                        isListening = listening,
                        currentExpression = if (listening) Expression.LISTENING else if (it.isSpeaking) Expression.SPEAKING else Expression.NEUTRAL
                    )
                }
            }
            .launchIn(viewModelScope)

        // Live partial transcription
        sttManager.partialResult
            .onEach { partial -> _uiState.update { it.copy(partialUserText = partial) } }
            .launchIn(viewModelScope)

        // STT errors
        sttManager.errorMessage
            .onEach { err -> _uiState.update { it.copy(sttError = err) } }
            .launchIn(viewModelScope)

        // Final STT result → add user message → AI replies
        sttManager.finalResult
            .onEach { result ->
                if (result.isNotBlank()) {
                    addUserMessage(result)
                    triggerAiReply()
                }
            }
            .launchIn(viewModelScope)
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Called by the UI to grant camera permission state. */
    fun onCameraPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasCameraPermission = granted) }
        Log.d(tag, "Camera permission: $granted")
    }

    /** Called by the UI to grant audio permission state. */
    fun onAudioPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasAudioPermission = granted) }
        Log.d(tag, "Audio permission: $granted")
    }

    /** Clear the STT error message after it has been displayed. */
    fun clearSttError() {
        _uiState.update { it.copy(sttError = null) }
    }

    /** Toggle front-camera preview ON / OFF. */
    fun toggleCamera() {
        val current = _uiState.value.isCameraOn
        val newState = !current
        viewModelScope.launch {
            preferencesRepository.setCameraOn(newState)
        }
        if (newState) {
            Log.d(tag, "Camera toggled ON — binding deferred to UI with LifecycleOwner")
        } else {
            cameraController.unbindCamera()
        }
    }

    /** Toggle microphone / STT listening. */
    fun toggleListening() {
        if (_uiState.value.isListening) {
            sttManager.stopListening()
        } else {
            // Stop AI if it's currently speaking to clear audio for recognition
            if (_uiState.value.isSpeaking) {
                ttsManager.stop()
            }
            sttManager.startListening()
        }
    }

    /** Trigger AI speaking the next canned response. */
    fun triggerAiSpeak() {
        val response = aiResponses[responseIndex % aiResponses.size]
        responseIndex++
        speakAsAi(response)
    }

    // ── Internal Helpers ─────────────────────────────────────────────────────

    private fun addInitialGreeting(application: Application) {
        val greeting = application.getString(com.example.aiconversation.R.string.ai_greeting)
        viewModelScope.launch {
            // Wait for TTS engine to be ready before speaking
            ttsManager.isReady.first { it }
            addAiMessage(greeting)
            ttsManager.speak(greeting)
        }
    }

    private fun addAiMessage(text: String) {
        val msg = Message(text = text, sender = Sender.AI)
        _uiState.update { it.copy(messages = it.messages + msg) }
    }

    private fun addUserMessage(text: String) {
        val msg = Message(text = text, sender = Sender.USER)
        _uiState.update { it.copy(messages = it.messages + msg, partialUserText = "") }
    }

    private fun speakAsAi(text: String) {
        addAiMessage(text)
        ttsManager.speak(text)
    }

    private fun triggerAiReply() {
        val response = aiResponses[responseIndex % aiResponses.size]
        responseIndex++
        viewModelScope.launch {
            _uiState.update { it.copy(currentExpression = Expression.THINKING) }
            // Small delay to mimic "thinking"
            kotlinx.coroutines.delay(600)
            speakAsAi(response)
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────
    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
        sttManager.destroy()
        cameraController.release()
        Log.d(tag, "ViewModel cleared — all managers released")
    }
}