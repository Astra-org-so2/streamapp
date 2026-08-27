package com.streamapp.core.broadcaster.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenPreviewManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null

    private val _isPreviewActive = MutableStateFlow(false)
    val isPreviewActive: StateFlow<Boolean> = _isPreviewActive.asStateFlow()

    private val _latestFrameBitmap = MutableStateFlow<Bitmap?>(null)
    val latestFrameBitmap: StateFlow<Bitmap?> = _latestFrameBitmap.asStateFlow()

    private var lastResultCode: Int = 0
    private var lastResultData: Intent? = null

    fun startPreview(resultCode: Int, resultData: Intent) {
        if (resultCode != Activity.RESULT_OK) return
        lastResultCode = resultCode
        lastResultData = resultData
        stopPreview()

        try {
            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

            val displayMetrics = context.resources.displayMetrics
            val width = 720
            val height = 1280
            val dpi = displayMetrics.densityDpi

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            
            val thread = HandlerThread("ScreenPreviewThread").apply { start() }
            handlerThread = thread
            val handler = Handler(thread.looper)

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "StreamAppScreenPreview",
                width,
                height,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface,
                null,
                handler
            )

            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    try {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * width

                        val bmp = Bitmap.createBitmap(
                            width + rowPadding / pixelStride,
                            height,
                            Bitmap.Config.ARGB_8888
                        )
                        bmp.copyPixelsFromBuffer(buffer)

                        val finalBmp = if (rowPadding == 0) {
                            bmp
                        } else {
                            Bitmap.createBitmap(bmp, 0, 0, width, height)
                        }
                        _latestFrameBitmap.value = finalBmp
                        _isPreviewActive.value = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        image.close()
                    }
                }
            }, handler)

            _isPreviewActive.value = true
        } catch (e: Exception) {
            e.printStackTrace()
            _isPreviewActive.value = false
        }
    }

    fun stopPreview() {
        _isPreviewActive.value = false
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            mediaProjection?.stop()
            mediaProjection = null
            handlerThread?.quitSafely()
            handlerThread = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
