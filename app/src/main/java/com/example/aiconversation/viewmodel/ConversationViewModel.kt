package com.example.aiconversation.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiconversation.R
import com.example.aiconversation.data.model.Message
import com.example.aiconversation.data.model.Sender
import com.example.aiconversation.utils.AudioAnalyzer
import com.example.aiconversation.utils.CameraController
import com.example.aiconversation.utils.LipSyncEngine
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
 * Central ViewModel — coordinates TTS, STT, Camera, AudioAnalyzer, LipSyncEngine,
 * and the message list.
 *
 * Lip sync sources:
 *  • TTS path  → [TTSManager.currentRange] → [TTSManager.startVisemeForText]
 *                  → [TTSManager.getCurrentVisemeWeights] → uiState.currentVisemeWeights
 *  • Audio path → [AudioAnalyzer] → [LipSyncEngine] → uiState.currentVisemeWeights
 *                  (active while the mic is listening and TTS is not speaking)
 */
class ConversationViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "ConversationViewModel"

    // ── Managers ─────────────────────────────────────────────────────────────
    val ttsManager = TTSManager(application)
    val sttManager = STTManager(application)
    val cameraController = CameraController(application)

    private val audioAnalyzer = AudioAnalyzer(application)
    private val lipSyncEngine = LipSyncEngine(audioAnalyzer)

    private val preferencesRepository =
        com.example.aiconversation.data.UserPreferencesRepository(application)

    // ── UI State ─────────────────────────────────────────────────────────────
    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    // ── Sample AI responses for demo ─────────────────────────────────────────
    private val aiResponseIds = listOf(
        R.string.ai_response_1,
        R.string.ai_response_2,
        R.string.ai_response_3
    )
    private var responseIndex = 0
    private var currentLanguage = "en"

    init {
        // Initial permission check
        val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(
            application, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasCam = androidx.core.content.ContextCompat.checkSelfPermission(
            application, android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        _uiState.update { it.copy(hasAudioPermission = hasMic, hasCameraPermission = hasCam) }

        preferencesRepository.languageFlow
            .onEach { lang -> currentLanguage = lang }
            .launchIn(viewModelScope)

        observeManagers()
        addInitialGreeting(application)

        preferencesRepository.isCameraOnFlow
            .onEach { isOn -> _uiState.update { it.copy(isCameraOn = isOn) } }
            .launchIn(viewModelScope)
    }

    // ── Observation ───────────────────────────────────────────────────────────

    private fun observeManagers() {

        // ── TTS speaking state ────────────────────────────────────────────────
        ttsManager.isSpeaking
            .onEach { speaking ->
                _uiState.update {
                    it.copy(
                        isSpeaking = speaking,
                        currentExpression = if (speaking) Expression.SPEAKING
                        else if (it.isListening) Expression.LISTENING
                        else Expression.NEUTRAL,
                        // Clear playingMessageId when speaking stops
                        playingMessageId = if (speaking) it.playingMessageId else null
                    )
                }
                if (!speaking) {
                    // TTS finished — return avatar to rest pose
                    lipSyncEngine.setRest()
                    _uiState.update { it.copy(currentVisemeWeights = com.example.aiconversation.utils.restWeightsMap()) }
                }
            }
            .launchIn(viewModelScope)

        ttsManager.isReady
            .onEach { ready -> _uiState.update { it.copy(isTtsReady = ready) } }
            .launchIn(viewModelScope)

        // ── TTS word-range → VisemeGenerator (TTS lip sync path) ────────────
        ttsManager.currentRange
            .onEach { range ->
                _uiState.update { it.copy(currentAiRange = range) }
                range?.let { (start, end) ->
                    val playingId = _uiState.value.playingMessageId
                    val msg = _uiState.value.messages.find { it.id == playingId }
                    msg?.let { m ->
                        // Use translation for visemes if that's what's currently being spoken
                        val textToUse = if (_uiState.value.isSpeakingTranslated) m.translatedText
                            ?: m.text else m.text
                        ttsManager.startVisemeForText(textToUse, start, end)
                    }
                }
            }
            .launchIn(viewModelScope)

        // ── TTS viseme weights → uiState (TTS lip sync path) ────────────────
        ttsManager.getCurrentVisemeWeights()
            .onEach { weights ->
                // Only push TTS weights when AI is speaking (not overriding mic weights)
                if (_uiState.value.isSpeaking) {
                    _uiState.update { it.copy(currentVisemeWeights = weights) }
                }
            }
            .launchIn(viewModelScope)

        // ── Legacy viseme (kept for any backward-compat component) ───────────
        ttsManager.getCurrentViseme()
            .onEach { viseme -> _uiState.update { it.copy(currentViseme = viseme) } }
            .launchIn(viewModelScope)

        // ── Audio/Mic → LipSyncEngine → uiState (mic lip sync path) ─────────
        lipSyncEngine.visemeWeights
            .onEach { weights ->
                // Only apply audio-driven weights when mic is active and TTS isn't speaking
                if (_uiState.value.isListening && !_uiState.value.isSpeaking) {
                    _uiState.update { it.copy(currentVisemeWeights = weights) }
                }
            }
            .launchIn(viewModelScope)

        // ── STT listening state & visuals ─────────────────────────────────────
        sttManager.isListening
            .onEach { listening ->
                _uiState.update {
                    it.copy(
                        isListening = listening,
                        currentExpression = if (listening) Expression.LISTENING
                        else if (it.isSpeaking) Expression.SPEAKING
                        else Expression.NEUTRAL
                    )
                }
                if (listening) {
                    // Start the engine, but DO NOT start AudioAnalyzer (conflicting mic)
                    // LipSyncEngine will now be driven by STT's rmsFlow instead of AudioRecord.
                    lipSyncEngine.start()
                } else {
                    // Stop audio-driven lip sync when mic is released
                    lipSyncEngine.stop()
                    audioAnalyzer.stop()
                    _uiState.update { it.copy(currentVisemeWeights = com.example.aiconversation.utils.restWeightsMap()) }
                }
            }
            .launchIn(viewModelScope)

        // ── STT RMS → LipSync (mic sync path) ─────────────────────────────────
        sttManager.rmsFlow
            .onEach { rms ->
                // Drive the engine with STT volume while listening
                if (_uiState.value.isListening && !_uiState.value.isSpeaking) {
                    lipSyncEngine.updateFromRMS(rms)
                }
            }
            .launchIn(viewModelScope)

        // ── Live partial transcription ────────────────────────────────────────
        sttManager.partialResult
            .onEach { partial -> _uiState.update { it.copy(partialUserText = partial) } }
            .launchIn(viewModelScope)

        // ── STT errors ───────────────────────────────────────────────────────
        sttManager.errorMessage
            .onEach { err -> _uiState.update { it.copy(sttError = err) } }
            .launchIn(viewModelScope)

        // ── Final STT result → add user message → AI replies ─────────────────
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

    fun onCameraPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasCameraPermission = granted) }
        Log.d(tag, "Camera permission: $granted")
    }

    fun onAudioPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasAudioPermission = granted) }
        Log.d(tag, "Audio permission: $granted")
    }

    fun clearSttError() {
        _uiState.update { it.copy(sttError = null) }
    }

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

    fun toggleListening() {
        if (_uiState.value.isListening) {
            sttManager.stopListening()
        } else {
            if (_uiState.value.isSpeaking) {
                ttsManager.stop()
            }
            sttManager.startListening()
        }
    }

    fun toggleTranslation(messageId: String) {
        _uiState.update { state ->
            val updatedMessages = state.messages.map { msg ->
                if (msg.id == messageId) {
                    msg.copy(isTranslated = !msg.isTranslated)
                } else msg
            }
            state.copy(messages = updatedMessages)
        }
    }

    fun playMessage(messageId: String) {
        val msg = _uiState.value.messages.find { it.id == messageId } ?: return
        val isUsingTranslation = msg.isTranslated && !msg.translatedText.isNullOrBlank()
        val textToSpeak = if (isUsingTranslation) {
            msg.translatedText ?: msg.text
        } else {
            msg.text
        }
        if (_uiState.value.isSpeaking) {
            ttsManager.stop()
        }
        _uiState.update {
            it.copy(
                isSpeakingTranslated = isUsingTranslation,
                playingMessageId = messageId
            )
        }
        ttsManager.speak(textToSpeak)
    }

    fun triggerAiSpeak() {
        val (text, translation) = getLocalizedAiResponse(getApplication())
        speakAsAi(text, translation)
    }

    // ── Internal Helpers ─────────────────────────────────────────────────────

    private fun getLocalizedString(application: Application, resId: Int, language: String): String {
        val locale = java.util.Locale(language)
        val config = android.content.res.Configuration(application.resources.configuration)
        config.setLocale(locale)
        val localizedContext = application.createConfigurationContext(config)
        return localizedContext.getString(resId)
    }

    private fun getLocalizedAiResponse(application: Application): Pair<String, String> {
        val resId = aiResponseIds[responseIndex % aiResponseIds.size]
        responseIndex++

        val currentText = getLocalizedString(application, resId, currentLanguage)
        // For the demo, if current is ES, translation is EN. If current is EN, translation is ES.
        val translationLang = if (currentLanguage == "en") "es" else "en"
        val translatedText = getLocalizedString(application, resId, translationLang)

        return Pair(currentText, translatedText)
    }

    private fun addInitialGreeting(application: Application) {
        viewModelScope.launch {
            val lang = preferencesRepository.languageFlow.first()
            ttsManager.isReady.first { it }

            val greeting = getLocalizedString(application, R.string.ai_greeting, lang)
            val translationLang = if (lang == "en") "es" else "en"
            val translation = getLocalizedString(application, R.string.ai_greeting, translationLang)

            speakAsAi(text = greeting, translation = translation)
        }
    }

    private fun speakAsAi(text: String, translation: String? = null, hint: String? = null) {
        val msg = Message(
            text = text,
            sender = Sender.AI,
            translatedText = translation,
            hintText = hint
        )
        _uiState.update {
            it.copy(
                isSpeakingTranslated = false,
                messages = it.messages + msg,
                playingMessageId = msg.id
            )
        }
        ttsManager.speak(text)
    }

    private fun addUserMessage(text: String) {
        val msg = Message(text = text, sender = Sender.USER)
        _uiState.update { it.copy(messages = it.messages + msg, partialUserText = "") }
    }

    private fun triggerAiReply() {
        viewModelScope.launch {
            _uiState.update { it.copy(currentExpression = Expression.THINKING) }
            kotlinx.coroutines.delay(1200)

            // Special case for demo matching the image prompt's context
            val lastUserMsg =
                _uiState.value.messages.lastOrNull { it.sender == Sender.USER }?.text?.lowercase()
            if (lastUserMsg != null && (lastUserMsg.contains("english") || lastUserMsg.contains("ready"))) {
                val topic =
                    getLocalizedString(getApplication(), R.string.ai_demo_topic, currentLanguage)
                val promptPattern = getLocalizedString(
                    getApplication(),
                    R.string.ai_demo_ready_prompt,
                    currentLanguage
                )
                val prompt = String.format(promptPattern, topic)

                val translationLang = if (currentLanguage == "en") "es" else "en"
                val translation = getLocalizedString(
                    getApplication(),
                    R.string.ai_demo_ready_translation,
                    translationLang
                )
                val hint = getLocalizedString(
                    getApplication(),
                    R.string.ai_demo_ready_hint,
                    currentLanguage
                )

                speakAsAi(text = prompt, translation = translation, hint = hint)
            } else {
                val (response, translation) = getLocalizedAiResponse(getApplication())
                speakAsAi(response, translation)
            }
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────
    override fun onCleared() {
        super.onCleared()
        lipSyncEngine.stop()
        audioAnalyzer.stop()
        ttsManager.shutdown()
        sttManager.destroy()
        cameraController.release()
        Log.d(tag, "ViewModel cleared — all managers released")
    }
}