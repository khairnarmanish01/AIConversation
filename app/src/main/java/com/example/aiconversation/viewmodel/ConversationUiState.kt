package com.example.aiconversation.viewmodel

import com.example.aiconversation.data.model.Message
import com.example.aiconversation.utils.Viseme

enum class Expression {
    NEUTRAL,
    LISTENING,
    THINKING,
    SPEAKING
}

/**
 * Immutable snapshot of every piece of UI-relevant state.
 * The ViewModel holds a [StateFlow] of this class and the UI observes it.
 */
data class ConversationUiState(
    // TTS
    val isSpeaking: Boolean = false,
    val isTtsReady: Boolean = false,
    val currentViseme: Viseme = Viseme.SLIGHTLY_OPEN,
    val currentAiRange: Pair<Int, Int>? = null,

    // STT
    val isListening: Boolean = false,
    val partialUserText: String = "",
    val sttError: String? = null,

    // Camera
    val isCameraOn: Boolean = false,
    val hasCameraPermission: Boolean = false,
    val hasAudioPermission: Boolean = false,

    // Conversation history
    val messages: List<Message> = emptyList(),

    // AI Expression
    val currentExpression: Expression = Expression.NEUTRAL
)