package com.streamapp.core.broadcaster.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.streamapp.core.common.logger.AppLogger
import com.streamapp.core.common.logger.LogCategory
import com.streamapp.core.database.entity.LayerType
import com.streamapp.core.database.entity.SceneLayerEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class CompositeSceneRenderer(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _compositeBitmap = MutableStateFlow<Bitmap?>(null)
    val compositeBitmap: StateFlow<Bitmap?> = _compositeBitmap.asStateFlow()

    private val _layersFlow = MutableStateFlow<List<SceneLayerEntity>>(emptyList())
    private val webViews = mutableMapOf<String, WebView>()
    private val webViewSnapshots = ConcurrentHashMap<String, Bitmap>()
    private val imageCache = ConcurrentHashMap<String, Bitmap>()

    private var renderJob: Job? = null
    private var webViewCaptureJob: Job? = null

    private var targetWidth = 1920
    private var targetHeight = 1080

    // Reusable Framebuffer to eliminate heap allocations and GC thrashing
    private var reusableFramebuffer: Bitmap? = null
    private var reusableCanvas: Canvas? = null
    private val framebufferLock = Any()

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 20, 20, 25)
        style = Paint.Style.FILL
    }

    private val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 0, 122, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setDimensions(width: Int, height: Int) {
        if (targetWidth == width && targetHeight == height) return
        targetWidth = width
        targetHeight = height

        synchronized(framebufferLock) {
            reusableFramebuffer?.recycle()
            reusableFramebuffer = null
            reusableCanvas = null
        }

        // Re-layout existing WebViews with new dimensions on Main thread
        scope.launch(Dispatchers.Main) {
            val layers = _layersFlow.value
            layers.filter { it.type == LayerType.WEB }.forEach { layer ->
                webViews[layer.id]?.let { wv ->
                    val w = (layer.width * targetWidth).toInt().coerceAtLeast(100)
                    val h = (layer.height * targetHeight).toInt().coerceAtLeast(50)
                    wv.layout(0, 0, w, h)
                }
            }
        }
    }

    fun updateLayers(layers: List<SceneLayerEntity>) {
        val visibleLayers = layers.filter { it.isVisible }
        _layersFlow.value = visibleLayers

        // Preload and cache IMAGE layers
        scope.launch(Dispatchers.IO) {
            val imagePaths = visibleLayers.filter { it.type == LayerType.IMAGE }.map { it.content }.toSet()
            // Evict unused images
            val unusedPaths = imageCache.keys.filter { !imagePaths.contains(it) }
            unusedPaths.forEach { imageCache.remove(it) }

            // Preload new images
            imagePaths.forEach { path ->
                if (!imageCache.containsKey(path)) {
                    val file = File(path)
                    if (file.exists()) {
                        try {
                            val decoded = BitmapFactory.decodeFile(file.absolutePath)
                            if (decoded != null) {
                                imageCache[path] = decoded
                            }
                        } catch (e: Exception) {
                            AppLogger.e(LogCategory.UI, "Failed to decode overlay image: $path", e)
                        }
                    }
                }
            }
        }

        // Manage WebViews on Main thread
        scope.launch(Dispatchers.Main) {
            val currentWebIds = visibleLayers.filter { it.type == LayerType.WEB }.map { it.id }.toSet()

            // Remove old web views
            val toRemove = webViews.keys.filter { !currentWebIds.contains(it) }
            toRemove.forEach { id ->
                webViews[id]?.destroy()
                webViews.remove(id)
                webViewSnapshots.remove(id)?.recycle()
            }

            // Create or update active web views
            visibleLayers.filter { it.type == LayerType.WEB }.forEach { layer ->
                if (!webViews.containsKey(layer.id) && layer.content.isNotBlank()) {
                    if (isUrlAllowed(layer.content)) {
                        val wv = WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.cacheMode = WebSettings.LOAD_NO_CACHE
                            settings.domStorageEnabled = true
                            setBackgroundColor(Color.TRANSPARENT)
                            webViewClient = WebViewClient()
                        }
                        val w = (layer.width * targetWidth).toInt().coerceAtLeast(100)
                        val h = (layer.height * targetHeight).toInt().coerceAtLeast(50)
                        wv.layout(0, 0, w, h)
                        wv.loadUrl(layer.content)
                        webViews[layer.id] = wv
                    } else {
                        AppLogger.w(LogCategory.UI, "Blocked potentially unsafe overlay URL: ${layer.content}")
                    }
                }
            }
        }
    }

    private fun isUrlAllowed(url: String): Boolean {
        return try {
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase() ?: return false
            if (scheme == "https") return true
            if (scheme == "http") {
                val host = uri.host?.lowercase() ?: return false
                return host == "localhost" || host == "127.0.0.1" || host.startsWith("192.168.") || host.startsWith("10.")
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    fun startRendering(fps: Int = 30) {
        stopRendering()

        // 1. Main-Thread WebView snapshot capture loop (throttled at ~20 FPS)
        webViewCaptureJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                webViews.forEach { (id, wv) ->
                    if (wv.width > 0 && wv.height > 0) {
                        try {
                            val snapshot = webViewSnapshots.getOrPut(id) {
                                Bitmap.createBitmap(wv.width, wv.height, Bitmap.Config.ARGB_8888)
                            }
                            val wvCanvas = Canvas(snapshot)
                            wvCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                            wv.draw(wvCanvas)
                        } catch (e: Exception) {
                            AppLogger.w(LogCategory.UI, "Error drawing WebView overlay: ${e.message}")
                        }
                    }
                }
                delay(50) // 20 FPS capture for web animations / alert widgets
            }
        }

        // 2. Default-Thread zero-allocation composition loop
        renderJob = scope.launch(Dispatchers.Default) {
            val delayMs = 1000L / fps
            while (isActive) {
                val layersSnapshot = _layersFlow.value
                if (layersSnapshot.isNotEmpty()) {
                    val frameBitmap = renderCompositeFrame(layersSnapshot)
                    _compositeBitmap.value = frameBitmap
                } else {
                    _compositeBitmap.value = null
                }
                delay(delayMs)
            }
        }
    }

    private fun renderCompositeFrame(layers: List<SceneLayerEntity>): Bitmap? {
        return synchronized(framebufferLock) {
            try {
                if (reusableFramebuffer == null || reusableFramebuffer?.isRecycled == true) {
                    reusableFramebuffer = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    reusableCanvas = Canvas(reusableFramebuffer!!)
                }

                val canvas = reusableCanvas ?: return null
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                layers.forEach { layer ->
                    val x = layer.posX * targetWidth
                    val y = layer.posY * targetHeight
                    val w = layer.width * targetWidth
                    val h = layer.height * targetHeight

                    canvas.save()
                    canvas.clipRect(x, y, x + w, y + h)

                    when (layer.type) {
                        LayerType.TEXT -> {
                            val rectF = RectF(x, y, x + w, y + h)
                            canvas.drawRoundRect(rectF, 16f, 16f, badgePaint)
                            canvas.drawRoundRect(rectF, 16f, 16f, badgeBorderPaint)
                            val textY = y + (h / 2) + (textPaint.textSize / 3)
                            canvas.drawText(layer.content, x + 24f, textY, textPaint)
                        }
                        LayerType.IMAGE -> {
                            val cachedBmp = imageCache[layer.content]
                            if (cachedBmp != null && !cachedBmp.isRecycled) {
                                imagePaint.alpha = (layer.alpha * 255).toInt().coerceIn(0, 255)
                                val srcRect = Rect(0, 0, cachedBmp.width, cachedBmp.height)
                                val dstRect = RectF(x, y, x + w, y + h)
                                canvas.drawBitmap(cachedBmp, srcRect, dstRect, imagePaint)
                            }
                        }
                        LayerType.WEB -> {
                            val wvBitmap = webViewSnapshots[layer.id]
                            if (wvBitmap != null && !wvBitmap.isRecycled) {
                                val srcRect = Rect(0, 0, wvBitmap.width, wvBitmap.height)
                                val dstRect = RectF(x, y, x + w, y + h)
                                canvas.drawBitmap(wvBitmap, srcRect, dstRect, null)
                            }
                        }
                        LayerType.CAMERA_PIP -> {
                            // Reserved for Camera PiP overlay frame
                        }
                    }
                    canvas.restore()
                }
                reusableFramebuffer
            } catch (e: Exception) {
                AppLogger.e(LogCategory.UI, "Error rendering composite overlay frame", e)
                null
            }
        }
    }

    fun stopRendering() {
        renderJob?.cancel()
        renderJob = null
        webViewCaptureJob?.cancel()
        webViewCaptureJob = null
        _compositeBitmap.value = null
    }

    suspend fun release() {
        stopRendering()
        withContext(Dispatchers.Main) {
            webViews.values.forEach { it.destroy() }
            webViews.clear()
            webViewSnapshots.values.forEach { it.recycle() }
            webViewSnapshots.clear()
        }
        imageCache.clear()
        synchronized(framebufferLock) {
            reusableFramebuffer?.recycle()
            reusableFramebuffer = null
            reusableCanvas = null
        }
    }
}
