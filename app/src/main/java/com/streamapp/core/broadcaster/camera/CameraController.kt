package com.streamapp.core.broadcaster.camera

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.streamapp.core.common.logger.AppLogger
import com.streamapp.core.common.logger.LogCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Singleton
class CameraController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var currentZoomObserver: Observer<ZoomState>? = null

    private var currentLensFacing = CameraSelector.LENS_FACING_BACK

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private val _zoomRatio = MutableStateFlow(1f)
    val zoomRatio: StateFlow<Float> = _zoomRatio.asStateFlow()

    suspend fun initCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        cameraProvider = getCameraProvider()
        bindCameraUseCases(lifecycleOwner, previewView)
    }

    private suspend fun getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                continuation.resume(cameraProviderFuture.get())
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        val cameraProvider = cameraProvider ?: return

        // Clean up previous observers before re-binding to prevent memory leaks
        removeZoomObserver()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(currentLensFacing)
            .build()

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview
            )
            observeCameraState(lifecycleOwner)
            AppLogger.i(LogCategory.VIDEO, "Camera use cases bound successfully (lens: $currentLensFacing)")
        } catch (e: Exception) {
            AppLogger.e(LogCategory.VIDEO, "Use case binding failed", e)
        }
    }

    fun switchCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        currentLensFacing = if (currentLensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        bindCameraUseCases(lifecycleOwner, previewView)
    }

    fun toggleTorch() {
        val camera = camera ?: return
        if (camera.cameraInfo.hasFlashUnit()) {
            val isCurrentlyOn = _isTorchOn.value
            camera.cameraControl.enableTorch(!isCurrentlyOn)
            _isTorchOn.value = !isCurrentlyOn
            AppLogger.d(LogCategory.VIDEO, "Torch toggled to: ${!isCurrentlyOn}")
        }
    }

    fun setZoomRatio(ratio: Float) {
        camera?.cameraControl?.setZoomRatio(ratio)
        _zoomRatio.value = ratio
    }

    private fun observeCameraState(lifecycleOwner: LifecycleOwner) {
        val observer = Observer<ZoomState> { zoomState ->
            _zoomRatio.value = zoomState.zoomRatio
        }
        currentZoomObserver = observer
        camera?.cameraInfo?.zoomState?.observe(lifecycleOwner, observer)
    }

    private fun removeZoomObserver() {
        currentZoomObserver?.let { observer ->
            camera?.cameraInfo?.zoomState?.removeObserver(observer)
        }
        currentZoomObserver = null
    }

    fun release() {
        removeZoomObserver()
        cameraProvider?.unbindAll()
        camera = null
    }
}
