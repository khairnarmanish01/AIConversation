package com.example.aiconversation.utils

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages CameraX lifecycle binding and exposes a [SurfaceRequest] for rendering.
 *
 * - Uses the front-facing camera by default.
 * - Binds / unbinds from the provided [LifecycleOwner].
 * - The composable observes [surfaceRequest] to draw the preview.
 */
class CameraController(private val context: Context) {

    private val tag = "CameraController"

    private val _isBound = MutableStateFlow(false)
    val isBound: StateFlow<Boolean> = _isBound.asStateFlow()

    // ── CameraX internals ────────────────────────────────────────────────────
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null

    /**
     * Binds the front camera preview to the given [lifecycleOwner] with a [Preview.SurfaceProvider].
     */
    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: androidx.camera.core.Preview.SurfaceProvider
    ) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                cameraProvider = providerFuture.get()

                val previewUseCase = Preview.Builder().build().also {
                    it.setSurfaceProvider(surfaceProvider)
                }
                preview = previewUseCase

                val selector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build()

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    previewUseCase
                )

                _isBound.value = true
                Log.d(tag, "Camera bound to lifecycle with SurfaceProvider")

            } catch (e: Exception) {
                Log.e(tag, "Camera binding failed: ${e.message}", e)
                _isBound.value = false
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /** Unbind all camera use-cases and clear the surface request. */
    fun unbindCamera() {
        cameraProvider?.unbindAll()
        _isBound.value = false
        Log.d(tag, "Camera unbound")
    }

    /** Full cleanup — call when done with the controller. */
    fun release() {
        unbindCamera()
        cameraProvider = null
    }
}