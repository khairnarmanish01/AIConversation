package com.example.aiconversation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aiconversation.R
import com.example.aiconversation.data.model.Message
import com.example.aiconversation.data.model.Sender
import com.example.aiconversation.ui.theme.PrimaryPurple

@Composable
fun CaptionItem(
    message: Message,
    onTranslateClick: (String) -> Unit = {},
    onPlayClick: (String) -> Unit = {},
    isSpeakingTranslated: Boolean = false,
    highlightRange: Pair<Int, Int>? = null,
    modifier: Modifier = Modifier
) {
    val isAi = message.sender == Sender.AI

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 16.dp),
        horizontalAlignment = if (isAi) Alignment.Start else Alignment.End
    ) {
        if (isAi) {
            AiMessageBubble(
                message = message,
                onTranslateClick = onTranslateClick,
                onPlayClick = onPlayClick,
                isSpeakingTranslated = isSpeakingTranslated,
                highlightRange = highlightRange
            )
        } else {
            UserMessageBubble(message = message)
        }
    }
}

@Composable
private fun AiMessageBubble(
    message: Message,
    onTranslateClick: (String) -> Unit,
    onPlayClick: (String) -> Unit,
    isSpeakingTranslated: Boolean,
    highlightRange: Pair<Int, Int>?
) {
    // Determine which text gets the highlighting
    val mainHighlight = if (!isSpeakingTranslated) highlightRange else null
    val transHighlight = if (isSpeakingTranslated) highlightRange else null

    val annotatedMain = buildHighlightedText(message.text, mainHighlight)
    val annotatedTrans = message.translatedText?.let { buildHighlightedText(it, transHighlight) }

    Box(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .clip(
                RoundedCornerShape(
                    topStart = 4.dp,
                    topEnd = 24.dp,
                    bottomStart = 24.dp,
                    bottomEnd = 24.dp
                )
            )
            .background(Color.White)
            .border(
                1.dp,
                Color(0xFFF0F0F0),
                RoundedCornerShape(
                    topStart = 4.dp,
                    topEnd = 24.dp,
                    bottomStart = 24.dp,
                    bottomEnd = 24.dp
                )
            )
            .padding(16.dp)
    ) {
        Column {
            // Main Text
            Text(
                text = annotatedMain,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color(0xFF333333),
                    lineHeight = 24.sp
                )
            )

            // "Say this:" Box (Hint)
            if (!message.hintText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF3E8FF)) // Very light purple
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = PrimaryPurple
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.hint_label),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = PrimaryPurple,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message.hintText,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1F1F1F)
                        )
                    )
                }
            }

            // Translation Section
            if (message.isTranslated && annotatedTrans != null) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFEEEEEE), thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = annotatedTrans,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = if (isSpeakingTranslated) Color(0xFF333333) else PrimaryPurple.copy(
                            alpha = 0.7f
                        ),
                        lineHeight = 22.sp
                    )
                )
            }

            // Action Buttons
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    iconName = "ic_play", // User will add these
                    onClick = { onPlayClick(message.id) }
                )
                ActionButton(
                    iconName = "ic_translate",
                    onClick = { onTranslateClick(message.id) },
                    isActive = message.isTranslated
                )
            }
        }
    }
}

private fun buildHighlightedText(
    rawText: String,
    highlightRange: Pair<Int, Int>?
): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
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
}

@Composable
private fun UserMessageBubble(message: Message) {
    Box(
        modifier = Modifier
            .widthIn(max = 280.dp)
            .clip(
                RoundedCornerShape(
                    topStart = 24.dp,
                    topEnd = 4.dp,
                    bottomStart = 24.dp,
                    bottomEnd = 24.dp
                )
            )
            .background(PrimaryPurple)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(
            text = message.text,
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun ActionButton(
    iconName: String,
    onClick: () -> Unit,
    isActive: Boolean = false
) {
    val resId = when (iconName) {
        "ic_play" -> R.drawable.ic_play_arrow
        "ic_translate" -> R.drawable.ic_translate
        else -> R.drawable.ic_translate
    }

    Surface(
        modifier = Modifier
            .size(24.dp),
        shape = CircleShape,
        color = if (isActive) {
            PrimaryPurple.copy(alpha = 0.2f)
        } else {
            Color(0xFFE1DFE2)
        },
        onClick = onClick // requires Material3 Surface
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                painter = painterResource(id = resId),
                contentDescription = null,
                modifier = Modifier.size(if (iconName == "ic_play") 17.dp else 14.dp),
                tint = if (isActive) PrimaryPurple else Color.Gray
            )
        }
    }
}

@Composable
fun PartialCaptionBubble(
    text: String,
    modifier: Modifier = Modifier
) {
    if (text.isBlank()) return

    Row(
        horizontalArrangement = Arrangement.End,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 24.dp,
                        topEnd = 4.dp,
                        bottomStart = 24.dp,
                        bottomEnd = 24.dp
                    )
                )
                .background(PrimaryPurple.copy(alpha = 0.6f))
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Text(
                text = "$text…",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic)
            )
        }
    }
}

