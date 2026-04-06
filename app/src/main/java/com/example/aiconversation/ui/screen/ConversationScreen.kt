package com.example.aiconversation.ui.screen

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiconversation.R
import com.example.aiconversation.data.model.Sender
import com.example.aiconversation.ui.components.AvatarView
import com.example.aiconversation.ui.components.CameraPreview
import com.example.aiconversation.ui.components.CaptionItem
import com.example.aiconversation.ui.components.ControlBar
import com.example.aiconversation.ui.components.PartialCaptionBubble
import com.example.aiconversation.ui.dialogs.CameraAccessDialog
import com.example.aiconversation.ui.dialogs.MicAccessDialog
import com.example.aiconversation.utils.Viseme
import com.example.aiconversation.utils.VisemeWeights
import com.example.aiconversation.utils.restWeightsMap
import com.example.aiconversation.viewmodel.ConversationViewModel
import com.example.aiconversation.viewmodel.Expression
import kotlinx.coroutines.launch

// ─── SharedPreferences-backed denial counter ─────────────────────────────────
// shouldShowRequestPermissionRationale() is unreliable inside launcher callbacks
// (activity window hasn't fully regained focus) and breaks when findActivity()
// returns null on certain devices/configs. Counting denials in SharedPreferences
// is the only robust, cross-version approach.
//
// Denial count semantics:
//   0 → never asked
//   1 → denied once  (system will still show the dialog again)
//   2 → permanently denied ("Don't ask again" / second denial on Android 11+)
private const val PREFS_NAME = "permission_prefs"
private const val KEY_CAMERA_DENIALS = "camera_denial_count"
private const val KEY_AUDIO_DENIALS = "audio_denial_count"

private fun getDenialCount(context: Context, key: String): Int =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(key, 0)

private fun incrementDenialCount(context: Context, key: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
}

private fun resetDenialCount(context: Context, key: String) =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putInt(key, 0).apply()

/** ≥ 2 denials without a subsequent grant == permanently denied on all Android versions. */
private fun isPermanentlyDenied(context: Context, key: String): Boolean =
    getDenialCount(context, key) >= 2
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ConversationScreen(
    onNavigateToSettings: () -> Unit = {},
    viewModel: ConversationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackBarHost = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val showCameraDialog = remember { mutableStateOf(false) }
    val showMicDialog = remember { mutableStateOf(false) }

    // Re-check permissions on resume (user may have granted them via Settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val hasCam = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                val hasAud = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                // Granted via Settings → reset denial counter so future
                // revocations start fresh from 0.
                if (hasCam) resetDenialCount(context, KEY_CAMERA_DENIALS)
                if (hasAud) resetDenialCount(context, KEY_AUDIO_DENIALS)

                viewModel.onCameraPermissionResult(hasCam)
                viewModel.onAudioPermissionResult(hasAud)

                if (!hasCam && uiState.isCameraOn) viewModel.toggleCamera()
                if (!hasAud && uiState.isListening) viewModel.toggleListening()
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                if (uiState.isListening) viewModel.toggleListening()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ── Camera permission launcher ──────────────────────────────────────────
    val cameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onCameraPermissionResult(granted)
        if (granted) {
            resetDenialCount(context, KEY_CAMERA_DENIALS)
            viewModel.toggleCamera()
        } else {
            // Increment first, then read — avoids off-by-one.
            incrementDenialCount(context, KEY_CAMERA_DENIALS)
            if (isPermanentlyDenied(context, KEY_CAMERA_DENIALS)) {
                showCameraDialog.value = true
            }
            // else: denied once — system will show dialog again on next tap.
        }
    }

    // ── Audio permission launcher ───────────────────────────────────────────
    val audioPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onAudioPermissionResult(granted)
        if (granted) {
            resetDenialCount(context, KEY_AUDIO_DENIALS)
            viewModel.toggleListening()
        } else {
            incrementDenialCount(context, KEY_AUDIO_DENIALS)
            if (isPermanentlyDenied(context, KEY_AUDIO_DENIALS)) {
                showMicDialog.value = true
            }
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(index = uiState.messages.size - 1) }
        }
    }

    LaunchedEffect(uiState.sttError) {
        uiState.sttError?.let {
            snackBarHost.showSnackbar(it)
            viewModel.clearSttError()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9F7FF))
    ) {
        // --- 1. Top Section: Avatar Area with Gradient ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.40f)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF9181F2), Color(0xFF5E49E1))
                    )
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            TopPanel(
                isSpeaking = uiState.isSpeaking,
                viseme = uiState.currentViseme,
                expression = uiState.currentExpression,
                visemeWeights = uiState.currentVisemeWeights,
                isCameraOn = uiState.isCameraOn,
                hasCameraPermission = uiState.hasCameraPermission,
                viewModel = viewModel
            )

            // Top Bar Overlay
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.Black.copy(alpha = 0.12f))
                                .height(32.dp)
                                .width(62.dp)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_timer),
                                contentDescription = stringResource(R.string.content_desc_playback_speed),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "1x",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Light
                            )
                        }

                        // ── Camera button ───────────────────────────────────
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable {
                                    when {
                                        // Granted → toggle camera normally
                                        uiState.hasCameraPermission ->
                                            viewModel.toggleCamera()

                                        // Permanently denied (counted in prefs) →
                                        // launching the system dialog would silently
                                        // no-op, so show our custom dialog instead.
                                        isPermanentlyDenied(context, KEY_CAMERA_DENIALS) ->
                                            showCameraDialog.value = true

                                        // Never asked or denied only once → ask system
                                        else ->
                                            cameraPermLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                }
                                .background(
                                    if (uiState.isCameraOn) Color(0xFFFABD00)
                                    else Color.Black.copy(alpha = 0.12f)
                                )
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_videocam),
                                contentDescription = stringResource(R.string.content_desc_video),
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                            tint = Color.White,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable { onNavigateToSettings.invoke() }
                        )
                    }
                }
            }
        }

        // --- 2. Middle Section: Chat Container ---
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.60f),
            color = Color(0xFFFDFBFF)
        ) {
            Column {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 16.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    items(items = uiState.messages, key = { it.id }) { message ->
                        CaptionItem(
                            message = message,
                            onTranslateClick = { viewModel.toggleTranslation(it) },
                            onPlayClick = { viewModel.playMessage(it) },
                            isSpeakingTranslated = uiState.isSpeakingTranslated,
                            highlightRange = if (message.sender == Sender.AI && uiState.isSpeaking) {
                                if (message.id == uiState.playingMessageId) {
                                    uiState.currentAiRange
                                } else null
                            } else null
                        )
                    }

                    if (uiState.partialUserText.isNotBlank()) {
                        item(key = "partial") {
                            PartialCaptionBubble(text = uiState.partialUserText)
                        }
                    }
                }
            }
        }

        // --- 3. Bottom Section: Controls ---
        // ── Mic button ──────────────────────────────────────────────────────
        ControlBar(
            isListening = uiState.isListening,
            onMicToggle = {
                when {
                    uiState.hasAudioPermission ->
                        viewModel.toggleListening()

                    isPermanentlyDenied(context, KEY_AUDIO_DENIALS) ->
                        showMicDialog.value = true

                    else ->
                        audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 8.dp)
        )

        // ── Custom dialogs shown on permanent denial ──────────────────────────
        if (showCameraDialog.value) {
            CameraAccessDialog(
                onDismiss = { showCameraDialog.value = false },
                onAllow = {
                    showCameraDialog.value = false
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    val activity = context.findActivity()
                    activity?.startActivity(intent)
                        ?: context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                onSkip = { showCameraDialog.value = false }
            )
        }

        if (showMicDialog.value) {
            MicAccessDialog(
                onDismiss = { showMicDialog.value = false },
                onAllow = {
                    showMicDialog.value = false
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    val activity = context.findActivity()
                    activity?.startActivity(intent)
                        ?: context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                onSkip = { showMicDialog.value = false }
            )
        }

        SnackbarHost(
            hostState = snackBarHost,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun TopPanel(
    isSpeaking: Boolean,
    viseme: Viseme,
    expression: Expression,
    visemeWeights: VisemeWeights = restWeightsMap(),
    isCameraOn: Boolean = false,
    hasCameraPermission: Boolean = false,
    viewModel: ConversationViewModel
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        if (isCameraOn && hasCameraPermission) {
            CameraPreview(
                modifier = Modifier
                    .padding(top = 80.dp, end = 24.dp)
                    .size(110.dp, 150.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .align(Alignment.TopEnd),
                onSurfaceProviderReady = { previewView ->
                    viewModel.cameraController.bindCamera(
                        lifecycleOwner = lifecycleOwner,
                        surfaceProvider = previewView.surfaceProvider
                    )
                }
            )
        }

        AvatarView(
            isSpeaking = isSpeaking,
            viseme = viseme,
            expression = expression,
            visemeWeights = visemeWeights,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/** Extension to unwrap Activity from a [android.content.Context]. */
fun android.content.Context.findActivity(): android.app.Activity? {
    var context = this
    while (context is android.content.ContextWrapper) {
        if (context is android.app.Activity) return context
        context = context.baseContext
    }
    return null
}