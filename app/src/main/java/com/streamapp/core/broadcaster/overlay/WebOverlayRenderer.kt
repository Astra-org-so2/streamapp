package com.streamapp.core.broadcaster.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
        
        // Ensure WebView layout size is fixed to capture it offscreen
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
                        try {
                            val bitmap = Bitmap.createBitmap(wv.width, wv.height, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            wv.draw(canvas)
                            _bitmapFlow.value = bitmap
                        } catch (e: Exception) {
                            // Ignore capture errors
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
    }

    fun release() {
        stopCapturing()
        webView?.destroy()
        webView = null
    }
}
