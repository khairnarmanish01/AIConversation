package com.example.aiconversation.ui.components

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.aiconversation.R
import com.example.aiconversation.ui.theme.SurfaceDark
import com.example.aiconversation.ui.theme.TextSecondary

/**
 * Renders the live camera preview using a [PreviewView] wrapped in [AndroidView].
 *
 * @param onSurfaceProviderReady Callback providing the [PreviewView.SurfaceProvider]
 *                               so the caller can attach it to CameraX Preview use-case.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onSurfaceProviderReady: (PreviewView) -> Unit
) {
    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                onSurfaceProviderReady(this)
            }
        },
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
    )
}

/**
 * Shown when camera is disabled — friendly placeholder card.
 */
@Composable
fun CameraPlaceholder(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = TextSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.placeholder_camera_off),
                color = TextSecondary.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.tap_to_enable_camera),
                color = TextSecondary.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall
            )
        }
    }
}