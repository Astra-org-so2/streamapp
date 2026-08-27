package com.streamapp.core.broadcaster.overlay

import android.content.Context
import android.graphics.*
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.streamapp.core.database.entity.LayerType
import com.streamapp.core.database.entity.SceneLayerEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class CompositeSceneRenderer(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _compositeBitmap = MutableStateFlow<Bitmap?>(null)
    val compositeBitmap: StateFlow<Bitmap?> = _compositeBitmap.asStateFlow()

    private val webViews = mutableMapOf<String, WebView>()
    private var renderJob: Job? = null

    private var targetWidth = 1920
    private var targetHeight = 1080
    private var currentLayers = listOf<SceneLayerEntity>()

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

    fun setDimensions(width: Int, height: Int) {
        targetWidth = width
        targetHeight = height
    }

    fun updateLayers(layers: List<SceneLayerEntity>) {
        currentLayers = layers.filter { it.isVisible }
        
        // Prepare WebViews for WEB layers
        scope.launch(Dispatchers.Main) {
            val currentWebIds = currentLayers.filter { it.type == LayerType.WEB }.map { it.id }.toSet()
            
            // Remove old web views
            val toRemove = webViews.keys.filter { !currentWebIds.contains(it) }
            toRemove.forEach { id ->
                webViews[id]?.destroy()
                webViews.remove(id)
            }

            // Create or update active web views
            currentLayers.filter { it.type == LayerType.WEB }.forEach { layer ->
                if (!webViews.containsKey(layer.id) && layer.content.isNotBlank()) {
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
                }
            }
        }
    }

    fun startRendering(fps: Int = 30) {
        renderJob?.cancel()
        renderJob = scope.launch(Dispatchers.Default) {
            val delayMs = 1000L / fps
            while (isActive) {
                if (currentLayers.isNotEmpty()) {
                    val frameBitmap = renderCompositeFrame()
                    _compositeBitmap.value = frameBitmap
                } else {
                    _compositeBitmap.value = null
                }
                delay(delayMs)
            }
        }
    }

    private fun renderCompositeFrame(): Bitmap? {
        return try {
            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            currentLayers.forEach { layer ->
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
                        val file = File(layer.content)
                        if (file.exists()) {
                            val imgBitmap = BitmapFactory.decodeFile(file.absolutePath)
                            if (imgBitmap != null) {
                                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    alpha = (layer.alpha * 255).toInt().coerceIn(0, 255)
                                }
                                val srcRect = Rect(0, 0, imgBitmap.width, imgBitmap.height)
                                val dstRect = RectF(x, y, x + w, y + h)
                                canvas.drawBitmap(imgBitmap, srcRect, dstRect, paint)
                            }
                        }
                    }
                    LayerType.WEB -> {
                        webViews[layer.id]?.let { wv ->
                            val wvBitmap = Bitmap.createBitmap(wv.width, wv.height, Bitmap.Config.ARGB_8888)
                            val wvCanvas = Canvas(wvBitmap)
                            wv.draw(wvCanvas)
                            val srcRect = Rect(0, 0, wvBitmap.width, wvBitmap.height)
                            val dstRect = RectF(x, y, x + w, y + h)
                            canvas.drawBitmap(wvBitmap, srcRect, dstRect, null)
                        }
                    }
                    LayerType.CAMERA_PIP -> {
                        // Camera PiP frame placeholder
                    }
                }
                canvas.restore()
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun stopRendering() {
        renderJob?.cancel()
        renderJob = null
    }

    fun release() {
        stopRendering()
        scope.launch(Dispatchers.Main) {
            webViews.values.forEach { it.destroy() }
            webViews.clear()
        }
    }
}
