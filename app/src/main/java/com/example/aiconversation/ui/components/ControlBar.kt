package com.example.aiconversation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.aiconversation.R
import com.example.aiconversation.ui.theme.PrimaryPurple

/**
 * Bottom control bar with Type, Mic, and Hint buttons.
 */
@Composable
fun ControlBar(
    isListening: Boolean,
    onMicToggle: () -> Unit,
    onTypeClick: () -> Unit = {},
    onHintClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        // Type Button
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(PrimaryPurple.copy(alpha = 0.2f))
                    .clickable { onTypeClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_keyboard),
                    contentDescription = stringResource(R.string.label_type),
                    tint = PrimaryPurple,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.label_type),
                color = Color.Gray,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
            )
        }

        // Mic Button
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(CircleShape)
                .background(if (isListening) Color(0xFFFABD00) else PrimaryPurple)
                .border(
                    8.dp,
                    if (isListening) Color(0xFFFFEBEE) else Color(0xFFF3E8FF),
                    CircleShape
                )
                .clickable { onMicToggle() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_mic),
                contentDescription = stringResource(R.string.microphone),
                tint = Color.White,
                modifier = Modifier.size(38.dp)
            )
        }

        // Hint Button
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(PrimaryPurple.copy(alpha = 0.2f))
                    .clickable { onHintClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_lightbulb),
                    contentDescription = stringResource(R.string.label_hint),
                    tint = PrimaryPurple,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.label_hint),
                color = Color.Gray,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
            )
        }
    }
}

