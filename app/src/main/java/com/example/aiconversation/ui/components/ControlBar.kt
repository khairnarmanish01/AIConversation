package com.example.aiconversation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.aiconversation.R
import com.example.aiconversation.ui.theme.PrimaryPurple
import com.example.aiconversation.ui.theme.PurpleDialog

/**
 * Bottom control bar with a large central Microphone button.
 */
@Composable
fun ControlBar(
    isListening: Boolean,
    hasAudioPermission: Boolean,
    onMicToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    // ── Mic button color ───────────────────────────────────────────────────
    val micColor by animateColorAsState(
        targetValue = if (isListening) Color(0xFFE53935) else PrimaryPurple,
        animationSpec = tween(300),
        label = "mic_color"
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background( if (hasAudioPermission) micColor else micColor.copy(alpha = 0.35f))
                .clickable { onMicToggle.invoke() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_mic),
                contentDescription = androidx.compose.ui.res.stringResource(id = R.string.microphone),
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
