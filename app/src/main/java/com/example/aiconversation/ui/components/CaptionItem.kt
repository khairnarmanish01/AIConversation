package com.example.aiconversation.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.aiconversation.data.model.Message
import com.example.aiconversation.data.model.Sender
import com.example.aiconversation.ui.theme.PrimaryPurple

/**
 * Renders a single [Message] as a styled chat bubble.
 *
 * - AI messages: left-aligned, blue tint.
 * - User messages: right-aligned, green tint.
 * - Partial messages: italic, slightly transparent to hint at live transcription.
 *
 * Slides in from the bottom when first composed.
 */
@Composable
fun CaptionItem(
    message: Message,
    highlightRange: Pair<Int, Int>? = null,
    modifier: Modifier = Modifier
) {
    val isAi = message.sender == Sender.AI

    val annotatedText = buildAnnotatedString {
        val rawText = message.text
        if (highlightRange != null && highlightRange.first < rawText.length && highlightRange.second <= rawText.length) {
            val (start, end) = highlightRange
            append(rawText.substring(0, start))
            withStyle(
                style = SpanStyle(
                    color = PrimaryPurple,
                    fontWeight = FontWeight.Bold,
                    background = PrimaryPurple.copy(alpha = 0.1f)
                )
            ) {
                append(rawText.substring(start, end))
            }
            append(rawText.substring(end))
        } else {
            append(rawText)
        }
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = if (isAi) Arrangement.Start else Arrangement.End,
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isAi) Color.Black.copy(alpha = 0.5f) else PrimaryPurple)
                    .alpha(if (message.isPartial) 0.65f else 1f)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = annotatedText,
                    color = Color.White,
                    style = if (message.isPartial)
                        MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic)
                    else
                        MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun PartialCaptionBubble(
    text: String,
    modifier: Modifier = Modifier
) {
    if (text.isBlank()) return

    AnimatedVisibility(
        visible = text.isNotBlank(),
        enter = fadeIn(),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PrimaryPurple)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "$text…",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic)
                )
            }
        }
    }
}
