package com.example.aiconversation.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.aiconversation.R
import com.example.aiconversation.utils.DetailedViseme
import com.example.aiconversation.utils.Viseme
import com.example.aiconversation.utils.VisemeWeights
import com.example.aiconversation.viewmodel.Expression

@Composable
fun AvatarView(
    modifier: Modifier = Modifier,
    isSpeaking: Boolean,
    viseme: Viseme,
    expression: Expression,
    visemeWeights: VisemeWeights = emptyMap()
) {
    val contentDesc = stringResource(R.string.content_desc_avatar)

    // Remove 'remember' to ensure the animation updates if the map is mutated
    val openAmount = visemeOpenAmount(visemeWeights)
    val roundAmount = visemeRoundAmount(visemeWeights)

    val chinDropRaw by animateFloatAsState(
        targetValue = openAmount,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "chin_drop"
    )
    val lipsScaleY by animateFloatAsState(
        targetValue = 1f + chinDropRaw * 0.35f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "lips_scale_y"
    )
    val lipsScaleX by animateFloatAsState(
        targetValue = 1f - roundAmount * 0.25f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "lips_scale_x"
    )

    // Calculate dynamic physical movement for the various parts
    val dynamicChinDrop = (chinDropRaw * 8f).dp
    val dynamicHeadSway = (chinDropRaw * 2f).dp
    val dynamicBodySway = (chinDropRaw * 1.5f).dp
    val dynamicEarSway = (chinDropRaw * 1f).dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = contentDesc }
    ) {
        // ── Body ──────────────────────────────────────────────────────────────
        Image(
            painter = painterResource(R.drawable.body),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(220.dp)
                .offset(x = 4.dp, y = dynamicBodySway)
        )

        // ── Forehead ──────────────────────────────────────────────────────────
        Image(
            painter = painterResource(R.drawable.forehead),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 174.dp)
                .width(84.dp)
                .offset(y = -dynamicHeadSway * 0.5f)
                .graphicsLayer {
                    scaleX = 1f + (chinDropRaw * 0.02f)
                    scaleY = 1f + (chinDropRaw * 0.02f)
                }
        )

        // ── Eyes / Glasses ────────────────────────────────────────────────────
        Image(
            painter = painterResource(R.drawable.eye_glasses),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 136.dp)
                .width(88.dp)
                .offset(y = -dynamicHeadSway * 0.8f)
                .graphicsLayer {
                    scaleX = 1f + (chinDropRaw * 0.015f)
                    scaleY = 1f + (chinDropRaw * 0.015f)
                }
        )

        // ── Nose Area ─────────────────────────────────────────────────────────
        Image(
            painter = painterResource(R.drawable.nose_area),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 125.dp)
                .width(60.dp)
                .offset(y = -dynamicHeadSway)
        )

        // ── Cheeks ────────────────────────────────────────────────────────────
        Row(
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 126.dp)
                .offset(x = 1.dp, y = -dynamicHeadSway * 0.7f)
        ) {
            Image(
                modifier = Modifier
                    .height(58.dp)
                    .graphicsLayer {
                        scaleX = 1f + (chinDropRaw * 0.05f)
                        scaleY = 1f + (chinDropRaw * 0.03f)
                    },
                painter = painterResource(R.drawable.cheek_left),
                contentDescription = null,
                contentScale = ContentScale.Fit
            )
            Image(
                modifier = Modifier
                    .height(58.dp)
                    .graphicsLayer {
                        scaleX = 1f + (chinDropRaw * 0.05f)
                        scaleY = 1f + (chinDropRaw * 0.03f)
                    },
                painter = painterResource(R.drawable.cheek_right),
                contentDescription = null,
                contentScale = ContentScale.Fit
            )
        }

        // ── Ears ──────────────────────────────────────────────────────────────
        Row(
            horizontalArrangement = Arrangement.spacedBy(53.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 132.dp)
                .offset(y = -dynamicEarSway)
        ) {
            Image(
                modifier = Modifier.height(52.dp),
                painter = painterResource(R.drawable.ear_left),
                contentDescription = null,
                contentScale = ContentScale.Fit
            )
            Image(
                modifier = Modifier.height(52.dp),
                painter = painterResource(R.drawable.ear_right),
                contentDescription = null,
                contentScale = ContentScale.Fit
            )
        }

        // ── Animated Lips ─────────────────────────────────────────────────────
        Image(
            painter = painterResource(R.drawable.bottom_lips),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 108.dp)
                .width(48.dp)
                .offset(y = dynamicChinDrop * 0.7f) // Follow the chin down
                .graphicsLayer {
                    scaleX = lipsScaleX
                    scaleY = lipsScaleY
                }
        )

        // ── Animated Chin ─────────────────────────────────────────────────────
        Image(
            painter = painterResource(R.drawable.chin),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 88.dp)
                .width(68.dp)
                .offset(y = dynamicChinDrop)
                .graphicsLayer {
                    scaleX = lipsScaleX
                    scaleY = lipsScaleY
                }
        )
    }
}

private fun visemeOpenAmount(weights: VisemeWeights): Float {
    if (weights.isEmpty()) return 0f
    val ah = weights[DetailedViseme.AH] ?: 0f
    val aa = weights[DetailedViseme.AA] ?: 0f
    val oh = weights[DetailedViseme.OH] ?: 0f
    val ay = weights[DetailedViseme.AY] ?: 0f
    val eh = weights[DetailedViseme.EH] ?: 0f
    val iy = weights[DetailedViseme.IY] ?: 0f
    return ((ah * 1.0f) + (aa * 0.85f) + (oh * 0.65f) + (ay * 0.55f) + (eh * 0.40f) + (iy * 0.30f)).coerceIn(
        0f,
        1f
    )
}

private fun visemeRoundAmount(weights: VisemeWeights): Float {
    if (weights.isEmpty()) return 0f
    val oo = weights[DetailedViseme.OO] ?: 0f
    val oh = weights[DetailedViseme.OH] ?: 0f
    val w = weights[DetailedViseme.W] ?: 0f
    return ((oo * 1.0f) + (oh * 0.6f) + (w * 0.8f)).coerceIn(0f, 1f)
}