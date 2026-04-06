package com.example.aiconversation.data.model

import java.util.UUID

/**
 * Represents a single message in the conversation.
 *
 * @param id        Unique identifier for the message.
 * @param text      The message text content.
 * @param sender    Who sent the message — AI or USER.
 * @param isPartial True while STT is still transcribing (partial result).
 */
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val sender: Sender,
    val isPartial: Boolean = false,
    val translatedText: String? = null,
    val hintText: String? = null,
    val isTranslated: Boolean = false
)

enum class Sender { AI, USER }