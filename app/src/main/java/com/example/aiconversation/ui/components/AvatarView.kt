package com.example.aiconversation.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.aiconversation.R
import com.example.aiconversation.ui.theme.AccentCyan
import com.example.aiconversation.ui.theme.PrimaryPurple
import com.example.aiconversation.utils.Viseme
import com.example.aiconversation.viewmodel.Expression

@Composable
fun AvatarView(
    isSpeaking: Boolean,
    viseme: Viseme,
    expression: Expression,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 160.dp
) {
    val contentDesc = stringResource(R.string.content_desc_avatar)

    // ── Animations ────────────────────────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "avatar_anim")

    // Outer glow pulse
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = if (isSpeaking) 0.5f else 0.15f,
        targetValue = if (isSpeaking) 1.0f else 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                if (expression == Expression.THINKING) 350 else 900,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    // Eye height based on expression (with bouncy spring physics)
    val eyeHeightPercent by animateFloatAsState(
        targetValue = when (expression) {
            Expression.NEUTRAL -> 1.0f
            Expression.LISTENING -> 1.35f
            Expression.THINKING -> 0.35f
            Expression.SPEAKING -> 0.9f
        },
        animationSpec = spring(
            dampingRatio = 0.6f, // bouncy
            stiffness = Spring.StiffnessLow
        ),
        label = "eye_height"
    )

    val eyeColor = when (expression) {
        Expression.THINKING -> PrimaryPurple
        Expression.LISTENING -> PrimaryPurple
        else -> AccentCyan
    }

    // Morph mouth shapes based on viseme
    val mouthScaleY by animateFloatAsState(
        targetValue = when (viseme) {
            Viseme.CLOSED -> 0.1f
            Viseme.SLIGHTLY_OPEN -> 0.3f
            Viseme.OPEN -> 1.0f
            Viseme.O_SHAPE -> 0.8f
            Viseme.WIDE -> 0.4f
            Viseme.F_V -> 0.2f
            Viseme.TH -> 0.4f
            Viseme.L_R -> 0.5f
            Viseme.SH_CH -> 0.6f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "mouth_y"
    )

    val mouthScaleX by animateFloatAsState(
        targetValue = when (viseme) {
            Viseme.CLOSED -> 0.5f
            Viseme.SLIGHTLY_OPEN -> 0.6f
            Viseme.OPEN -> 0.7f
            Viseme.O_SHAPE -> 0.4f
            Viseme.WIDE -> 0.9f
            Viseme.F_V -> 0.6f
            Viseme.TH -> 0.7f
            Viseme.L_R -> 0.8f
            Viseme.SH_CH -> 0.8f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "mouth_x"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(avatarSize)
            .semantics { contentDescription = contentDesc }
    ) {
        Canvas(modifier = Modifier.size(avatarSize)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.minDimension / 2f

            // ── Outer Ring (Scanner / Halo) ──────────────────────────────────────
            val ringRadius = r * 0.95f
            drawCircle(
                color = eyeColor.copy(alpha = glowAlpha * 0.8f),
                radius = ringRadius + 4.dp.toPx() + (12.dp.toPx() * glowAlpha),
                center = Offset(cx, cy),
                style = Stroke(width = 4.dp.toPx())
            )

            // Inner chassis ring
            drawCircle(
                color = Color(0xFF130820), // Deep purple chassis
                radius = ringRadius,
                center = Offset(cx, cy)
            )

            // Screen faceplate (Dark glass)
            val faceplateRadius = r * 0.85f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF26123D), Color(0xFF0B0413)), // Dark rich purple gradient
                    center = Offset(cx, cy),
                    radius = faceplateRadius
                ),
                radius = faceplateRadius,
                center = Offset(cx, cy)
            )

            // Hexagonal highlight or grid overlay hint
            drawLine(
                color = Color.White.copy(alpha = 0.05f),
                start = Offset(cx - faceplateRadius, cy),
                end = Offset(cx + faceplateRadius, cy),
                strokeWidth = 1.dp.toPx()
            )

            // ── Visor / Eyes ──────────────────────────────────────────────────────
            val eyeWidth = r * 0.4f
            val baseEyeHeight = r * 0.15f
            val eyeH = baseEyeHeight * eyeHeightPercent
            val eyeSpacing = r * 0.12f
            val eyeY = cy - r * 0.2f

            // Draw glowing eyes (left and right)
            listOf(-1, 1).forEach { dir ->
                val eyeCx = cx + dir * (eyeWidth / 2f + eyeSpacing)
                val rectLeft = eyeCx - eyeWidth / 2f
                val rectTop = eyeY - eyeH / 2f

                drawRoundRect(
                    color = eyeColor,
                    topLeft = Offset(rectLeft, rectTop),
                    size = Size(eyeWidth, eyeH),
                    cornerRadius = CornerRadius(24f, 24f) // Rounder, friendlier eyes
                )

                // Bright center bloom
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.6f),
                    topLeft = Offset(rectLeft + eyeWidth * 0.1f, rectTop + eyeH * 0.2f),
                    size = Size(eyeWidth * 0.8f, eyeH * 0.6f),
                    cornerRadius = CornerRadius(6f, 6f)
                )
            }

            // ── Audio-Wave / Digital Mouth ────────────────────────────────────────
            val mouthBaseWidth = r * 0.5f
            val mouthBaseHeight = r * 0.25f

            val mW = mouthBaseWidth * mouthScaleX
            val mH = mouthBaseHeight * mouthScaleY
            val mouthY = cy + r * 0.25f

            // A segmented digital mouth (like an equalizer wave)
            val segments = 5
            val segmentSpacing = mW * 0.1f
            val segmentWidth = (mW - (segments - 1) * segmentSpacing) / segments

            val activeColor = if (isSpeaking) AccentCyan.copy(alpha = 0.95f) else Color.Gray.copy(alpha = 0.5f)

            for (i in 0 until segments) {
                // vary the height of segments to look like a wave
                val heightMultiplier = when (i) {
                    0, 4 -> 0.4f
                    1, 3 -> 0.7f
                    else -> 1.0f
                }

                val currentSegH = mH * heightMultiplier
                val segX = cx - mW / 2f + i * (segmentWidth + segmentSpacing)
                val segY = mouthY - currentSegH / 2f

                drawRoundRect(
                    color = activeColor.copy(alpha = 0.9f),
                    topLeft = Offset(segX, segY),
                    size = Size(segmentWidth, currentSegH),
                    cornerRadius = CornerRadius(4f, 4f)
                )
            }
        }
    }
}