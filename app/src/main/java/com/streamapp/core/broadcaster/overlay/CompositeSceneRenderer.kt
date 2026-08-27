package com.streamapp.core.broadcaster.overlay

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.streamapp.core.common.logger.AppLogger
import com.streamapp.core.common.logger.LogCategory
import com.streamapp.core.database.entity.LayerType
import com.streamapp.core.database.entity.SceneLayerEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val webViewLocks = ConcurrentHashMap<String, Any>()
    private val imageCache = ConcurrentHashMap<String, Bitmap>()

    private var renderJob: Job? = null
    private var webViewCaptureJob: Job? = null

    private var targetWidth = 1920
    private var targetHeight = 1080

    // Double-buffered framebuffers to prevent tearing & concurrent GL texture access
    private var backFramebuffer: Bitmap? = null
    private var frontFramebuffer: Bitmap? = null
    private var backCanvas: Canvas? = null
    private val bufferSwapLock = Any()

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

        synchronized(bufferSwapLock) {
            backFramebuffer?.recycle()
            frontFramebuffer?.recycle()
            backFramebuffer = null
            frontFramebuffer = null
            backCanvas = null
        }

        scope.launch(Dispatchers.Main) {
            val layers = _layersFlow.value
            layers.filter { it.type == LayerType.WEB }.forEach { layer ->
                webViews[layer.id]?.let { wv ->
                    val w = (layer.width * layer.scale * targetWidth).toInt().coerceAtLeast(100)
                    val h = (layer.height * layer.scale * targetHeight).toInt().coerceAtLeast(50)
                    wv.layout(0, 0, w, h)
                }
            }
        }
    }

    fun updateLayers(layers: List<SceneLayerEntity>) {
        val visibleLayers = layers.filter { it.isVisible }
        _layersFlow.value = visibleLayers

        scope.launch(Dispatchers.IO) {
            val imagePaths = visibleLayers.filter { it.type == LayerType.IMAGE }.map { it.content }.toSet()
            val unusedPaths = imageCache.keys.filter { !imagePaths.contains(it) }
            unusedPaths.forEach { imageCache.remove(it) }

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

        scope.launch(Dispatchers.Main) {
            val currentWebIds = visibleLayers.filter { it.type == LayerType.WEB }.map { it.id }.toSet()

            val toRemove = webViews.keys.filter { !currentWebIds.contains(it) }
            toRemove.forEach { id ->
                webViews[id]?.destroy()
                webViews.remove(id)
                webViewLocks.remove(id)
                webViewSnapshots.remove(id)?.recycle()
            }

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
                        val w = (layer.width * layer.scale * targetWidth).toInt().coerceAtLeast(100)
                        val h = (layer.height * layer.scale * targetHeight).toInt().coerceAtLeast(50)
                        wv.layout(0, 0, w, h)
                        wv.loadUrl(layer.content)
                        webViews[layer.id] = wv
                        webViewLocks[layer.id] = Any()
                    } else {
                        AppLogger.w(LogCategory.UI, "Blocked unsafe overlay URL: ${layer.content}")
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

        // 1. Main-Thread WebView snapshot capture loop (throttled at ~20 FPS) with lock protection
        webViewCaptureJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                webViews.forEach { (id, wv) ->
                    if (wv.width > 0 && wv.height > 0) {
                        val lock = webViewLocks.getOrPut(id) { Any() }
                        try {
                            val snapshot = webViewSnapshots.getOrPut(id) {
                                Bitmap.createBitmap(wv.width, wv.height, Bitmap.Config.ARGB_8888)
                            }
                            synchronized(lock) {
                                val wvCanvas = Canvas(snapshot)
                                wvCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                                wv.draw(wvCanvas)
                            }
                        } catch (e: Exception) {
                            AppLogger.w(LogCategory.UI, "Error capturing WebView snapshot: ${e.message}")
                        }
                    }
                }
                delay(50)
            }
        }

        // 2. Default-Thread double-buffered composition loop
        renderJob = scope.launch(Dispatchers.Default) {
            val delayMs = 1000L / fps
            while (isActive) {
                val layersSnapshot = _layersFlow.value
                if (layersSnapshot.isNotEmpty()) {
                    val frameBitmap = renderDoubleBufferedFrame(layersSnapshot)
                    _compositeBitmap.value = frameBitmap
                } else {
                    _compositeBitmap.value = null
                }
                delay(delayMs)
            }
        }
    }

    private fun renderDoubleBufferedFrame(layers: List<SceneLayerEntity>): Bitmap? {
        return synchronized(bufferSwapLock) {
            try {
                if (backFramebuffer == null || backFramebuffer?.isRecycled == true) {
                    backFramebuffer = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    frontFramebuffer = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    backCanvas = Canvas(backFramebuffer!!)
                }

                val canvas = backCanvas ?: return null
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                layers.forEach { layer ->
                    val x = layer.posX * targetWidth
                    val y = layer.posY * targetHeight
                    val w = layer.width * layer.scale * targetWidth
                    val h = layer.height * layer.scale * targetHeight

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
                            val lock = webViewLocks[layer.id]
                            val wvBitmap = webViewSnapshots[layer.id]
                            if (wvBitmap != null && !wvBitmap.isRecycled && lock != null) {
                                synchronized(lock) {
                                    val srcRect = Rect(0, 0, wvBitmap.width, wvBitmap.height)
                                    val dstRect = RectF(x, y, x + w, y + h)
                                    canvas.drawBitmap(wvBitmap, srcRect, dstRect, null)
                                }
                            }
                        }
                        LayerType.CAMERA_PIP -> {
                            // Reserved for PiP
                        }
                    }
                    canvas.restore()
                }

                // Swap front and back buffers safely
                val temp = frontFramebuffer
                frontFramebuffer = backFramebuffer
                backFramebuffer = temp
                backCanvas = Canvas(backFramebuffer!!)

                frontFramebuffer
            } catch (e: Exception) {
                AppLogger.e(LogCategory.UI, "Error in double-buffered frame composition", e)
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
            webViewLocks.clear()
        }
        imageCache.clear()
        synchronized(bufferSwapLock) {
            backFramebuffer?.recycle()
            frontFramebuffer?.recycle()
            backFramebuffer = null
            frontFramebuffer = null
            backCanvas = null
        }
    }
}
