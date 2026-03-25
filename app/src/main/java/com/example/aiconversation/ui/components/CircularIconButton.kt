package com.example.aiconversation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.aiconversation.ui.theme.PrimaryPurple

@Composable
fun CircularIconButton(
    modifier: Modifier = Modifier,
    icon: Painter,
    backgroundColor: Color = Color.White,
    buttonSize: Dp = 36.dp,
    iconSize: Dp = 20.dp,
    contentDescription: String? = null,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(buttonSize)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = PrimaryPurple
        )
    }
}