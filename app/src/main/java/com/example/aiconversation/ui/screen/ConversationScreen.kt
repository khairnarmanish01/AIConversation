package com.example.aiconversation.ui.screen

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiconversation.R
import com.example.aiconversation.ui.components.AvatarView
import com.example.aiconversation.ui.components.CameraPreview
import com.example.aiconversation.ui.components.CaptionItem
import com.example.aiconversation.ui.components.CircularIconButton
import com.example.aiconversation.ui.components.ControlBar
import com.example.aiconversation.ui.components.PartialCaptionBubble
import com.example.aiconversation.ui.dialogs.CameraAccessDialog
import com.example.aiconversation.ui.dialogs.MicAccessDialog
import com.example.aiconversation.ui.theme.AccentCyan
import com.example.aiconversation.ui.theme.MainBgGradient
import com.example.aiconversation.ui.theme.PrimaryPurple
import com.example.aiconversation.utils.Viseme
import com.example.aiconversation.viewmodel.ConversationViewModel
import kotlinx.coroutines.launch

@Composable
fun ConversationScreen(
    onNavigateToSettings: () -> Unit = {},
    viewModel:  ConversationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackBarHost = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Re-check permissions when returning from background (user might have changed them in settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                val hasAud = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                
                viewModel.onCameraPermissionResult(hasCam)
                viewModel.onAudioPermissionResult(hasAud)

                // If permission revoked while in background, turn off active features
                if (!hasCam && uiState.isCameraOn) {
                    viewModel.toggleCamera()
                }
                if (!hasAud && uiState.isListening) {
                    viewModel.toggleListening()
                }
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                // If the app is minimized or user navigates away, stop active listening to prevent errors
                if (uiState.isListening) {
                    viewModel.toggleListening()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val showCameraDialog = remember { mutableStateOf(false) }
    val showMicDialog = remember { mutableStateOf(false) }

    val cameraPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onCameraPermissionResult(granted)
        if (granted) {
            viewModel.toggleCamera()
        } else {
            val activity = context as? Activity
            val isPermanentlyDenied = activity?.let {
                !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
            } ?: false
            if (isPermanentlyDenied) {
                showCameraDialog.value = true
            }
        }
    }

    val audioPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onAudioPermissionResult(granted)
        if (granted) {
            viewModel.toggleListening()
        } else {
            val activity = context as? Activity
            val isPermanentlyDenied = activity?.let {
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    it, Manifest.permission.RECORD_AUDIO
                )
            } ?: false
            if (isPermanentlyDenied) {
                showMicDialog.value = true
            }
        }
    }

    LaunchedEffect(uiState.messages.size, uiState.partialUserText) {
        if (uiState.messages.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(index = uiState.messages.size - 1)
            }
        }
    }

    // Camera binding is now handled by TopPanel when SurfaceProvider is ready

    LaunchedEffect(uiState.sttError) {
        uiState.sttError?.let {
            snackBarHost.showSnackbar(it)
            viewModel.clearSttError()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = MainBgGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Box(modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)) {
                
                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(id = R.string.settings_title),
                        tint = Color.White
                    )
                }

                CircularIconButton(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp),
                    icon = if (uiState.isCameraOn) painterResource(R.drawable.ic_cam_off) else painterResource(
                        R.drawable.ic_cam_on
                    )
                ) {
                    if (uiState.hasCameraPermission) {
                        viewModel.toggleCamera()
                    } else {
                        cameraPermLauncher.launch(Manifest.permission.CAMERA)
                    }
                }
            }

            TopPanel(
                isSpeaking = uiState.isSpeaking,
                viseme = uiState.currentViseme,
                expression = uiState.currentExpression
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    items(
                        items = uiState.messages, key = { it.id }) { message ->
                        CaptionItem(
                            message = message,
                            highlightRange = if (message.sender == com.example.aiconversation.data.model.Sender.AI && uiState.isSpeaking) {
                                if (message.id == uiState.messages.lastOrNull { it.sender == com.example.aiconversation.data.model.Sender.AI }?.id) {
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

                if (uiState.isCameraOn) {
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(160.dp)
                            .padding(end = 16.dp, bottom = 12.dp)
                            .border(
                                width = 2.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(AccentCyan, PrimaryPurple)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        CameraPreview(
                            modifier = Modifier.fillMaxSize(),
                            onSurfaceProviderReady = { previewView ->
                                viewModel.cameraController.bindCamera(
                                    lifecycleOwner, previewView.surfaceProvider
                                )
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            ControlBar(
                isListening = uiState.isListening,
                hasAudioPermission = uiState.hasAudioPermission,
                onMicToggle = {
                    if (uiState.hasAudioPermission) {
                        viewModel.toggleListening()
                    } else {
                        audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp)
            )
        }

        if (showCameraDialog.value) {
            CameraAccessDialog(onDismiss = { showCameraDialog.value = false }, onAllow = {
                showCameraDialog.value = false
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }, onSkip = { showCameraDialog.value = false })
        }

        if (showMicDialog.value) {
            MicAccessDialog(onDismiss = { showMicDialog.value = false }, onAllow = {
                showMicDialog.value = false
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }, onSkip = { showMicDialog.value = false })
        }

        SnackbarHost(
            hostState = snackBarHost, modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun TopPanel(
    isSpeaking: Boolean,
    viseme: Viseme,
    expression: com.example.aiconversation.viewmodel.Expression,
) {
    Box(
        contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AvatarView(
                isSpeaking = isSpeaking,
                viseme = viseme,
                expression = expression,
                avatarSize = 170.dp
            )

        }
    }
}