package com.streamapp.core.broadcaster.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WebOverlayRenderer(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var webView: WebView? = null
    private var captureJob: Job? = null

    // Reusable double-buffer to eliminate 30 Allocations/sec GC pressure
    private var reusableBackBitmap: Bitmap? = null
    private var reusableFrontBitmap: Bitmap? = null
    private var reusableCanvas: Canvas? = null
    private val bufferLock = Any()

    private val _bitmapFlow = MutableStateFlow<Bitmap?>(null)
    val bitmapFlow: StateFlow<Bitmap?> = _bitmapFlow.asStateFlow()

    fun loadUrl(url: String, width: Int, height: Int) {
        if (webView == null) {
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                settings.domStorageEnabled = true
                setBackgroundColor(Color.TRANSPARENT)
                webViewClient = WebViewClient()
            }
        }

        synchronized(bufferLock) {
            reusableBackBitmap?.recycle()
            reusableFrontBitmap?.recycle()
            val w = width.coerceAtLeast(100)
            val h = height.coerceAtLeast(50)
            reusableBackBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            reusableFrontBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            reusableCanvas = Canvas(reusableBackBitmap!!)
        }

        webView?.layout(0, 0, width, height)
        webView?.loadUrl(url)
    }

    fun startCapturing(fps: Int = 30) {
        captureJob?.cancel()
        captureJob = scope.launch(Dispatchers.Main) {
            val delayMs = 1000L / fps
            while (isActive) {
                webView?.let { wv ->
                    if (wv.width > 0 && wv.height > 0) {
                        synchronized(bufferLock) {
                            try {
                                val canvas = reusableCanvas
                                if (canvas != null && reusableBackBitmap != null && reusableFrontBitmap != null) {
                                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                                    wv.draw(canvas)

                                    // Copy into front buffer
                                    val frontCanvas = Canvas(reusableFrontBitmap!!)
                                    frontCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                                    frontCanvas.drawBitmap(reusableBackBitmap!!, 0f, 0f, null)

                                    _bitmapFlow.value = reusableFrontBitmap
                                }
                            } catch (e: Exception) {
                                // Ignore capture transient errors
                            }
                        }
                    }
                }
                delay(delayMs)
            }
        }
    }

    fun stopCapturing() {
        captureJob?.cancel()
        captureJob = null
        _bitmapFlow.value = null
    }

    fun release() {
        stopCapturing()
        webView?.destroy()
        webView = null
        synchronized(bufferLock) {
            reusableBackBitmap?.recycle()
            reusableFrontBitmap?.recycle()
            reusableBackBitmap = null
            reusableFrontBitmap = null
            reusableCanvas = null
        }
    }
}
