package com.streamapp.core.streaming.fake

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Surface
import com.streamapp.core.common.dispatchers.AppDispatchers
import com.streamapp.core.common.logger.AppLogger
import com.streamapp.core.common.logger.LogCategory
import com.streamapp.core.common.result.AppResult
import com.streamapp.core.input.model.NormalizedInputEvent
import com.streamapp.core.model.connection.StreamingConnectionState
import com.streamapp.core.model.stream.NetworkType
import com.streamapp.core.model.stream.Resolution
import com.streamapp.core.model.stream.StreamConfiguration
import com.streamapp.core.model.stream.StreamStatistics
import com.streamapp.core.model.stream.VideoCodec
import com.streamapp.core.streaming.client.StreamingClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sin
import kotlin.random.Random

/**
 * Realistic Mock/Development implementation of [StreamingClient].
 * Actively renders high-frame-rate synthetic test frames into the [Surface]
 * and generates dynamic network metrics (RTT, Jitter, Bitrate, FPS, Packet Loss).
 */
@Singleton
class FakeStreamingClient @Inject constructor(
    private val dispatchers: AppDispatchers
) : StreamingClient {

    private val scope = CoroutineScope(dispatchers.default)
    private val _connectionState = MutableStateFlow<StreamingConnectionState>(StreamingConnectionState.Disconnected)
    private val _statistics = MutableStateFlow(StreamStatistics())

    private var activeSurface: Surface? = null
    private var renderJob: Job? = null
    private var statsJob: Job? = null
    private var streamConfig: StreamConfiguration? = null

    private var lastInputText: String = "No input yet"
    private var frameCount: Long = 0
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    override fun connectionState(): StateFlow<StreamingConnectionState> = _connectionState.asStateFlow()
    override fun statistics(): StateFlow<StreamStatistics> = _statistics.asStateFlow()

    override suspend fun connect(config: StreamConfiguration): AppResult<Unit> {
        streamConfig = config
        AppLogger.i(LogCategory.STREAMING, "FakeStreamingClient connecting to ${config.serverId} (App: ${config.appId})")

        _connectionState.value = StreamingConnectionState.Connecting
        delay(400)
        _connectionState.value = StreamingConnectionState.Negotiating
        delay(300)
        _connectionState.value = StreamingConnectionState.GatheringCandidates
        delay(300)
        _connectionState.value = StreamingConnectionState.Connected
        AppLogger.i(LogCategory.STREAMING, "FakeStreamingClient connected successfully")

        startRendering()
        startStatsLoop()
        return AppResult.Success(Unit)
    }

    override suspend fun disconnect() {
        AppLogger.i(LogCategory.STREAMING, "FakeStreamingClient disconnecting")
        renderJob?.cancel()
        renderJob = null
        statsJob?.cancel()
        statsJob = null
        _connectionState.value = StreamingConnectionState.Disconnected
    }

    override fun attachVideoSurface(surface: Surface) {
        AppLogger.d(LogCategory.VIDEO, "Surface attached to FakeStreamingClient")
        activeSurface = surface
        if (_connectionState.value is StreamingConnectionState.Connected && renderJob == null) {
            startRendering()
        }
    }

    override fun detachVideoSurface() {
        AppLogger.d(LogCategory.VIDEO, "Surface detached from FakeStreamingClient")
        renderJob?.cancel()
        renderJob = null
        activeSurface = null
    }

    override suspend fun sendInput(event: NormalizedInputEvent) {
        lastInputText = when (event) {
            is NormalizedInputEvent.Touch -> "Touch: action=${event.action}, x=${"%.2f".format(event.x)}, y=${"%.2f".format(event.y)}"
            is NormalizedInputEvent.GamepadButton -> "Gamepad Button: ${event.button}, pressed=${event.isPressed}"
            is NormalizedInputEvent.GamepadAxis -> "Gamepad Axis: ${event.axis} = ${"%.2f".format(event.value)}"
            is NormalizedInputEvent.MouseMove -> "Mouse Move: dx=${event.deltaX}, dy=${event.deltaY}"
            is NormalizedInputEvent.MouseButton -> "Mouse Button: ${event.button}, down=${event.isDown}"
            is NormalizedInputEvent.MouseScroll -> "Mouse Scroll: sx=${event.scrollX}, sy=${event.scrollY}"
            is NormalizedInputEvent.KeyboardKey -> "Key: code=${event.keyCode}, down=${event.isDown}"
            is NormalizedInputEvent.TextInput -> "Text: ${event.text}"
        }
        AppLogger.d(LogCategory.INPUT, "FakeStreamingClient input: $lastInputText")
    }

    override fun setStreamVolume(volume: Float) {
        AppLogger.d(LogCategory.AUDIO, "FakeStreamingClient volume set to $volume")
    }

    private fun startRendering() {
        renderJob?.cancel()
        renderJob = scope.launch(dispatchers.default) {
            val paintBg = Paint().apply { color = Color.rgb(15, 20, 30) }
            val paintGrid = Paint().apply {
                color = Color.rgb(30, 42, 60)
                strokeWidth = 2f
                style = Paint.Style.STROKE
            }
            val paintAccent = Paint().apply {
                color = Color.rgb(0, 229, 255)
                strokeWidth = 4f
                style = Paint.Style.STROKE
            }
            val paintFill = Paint().apply {
                color = Color.rgb(0, 229, 255)
                style = Paint.Style.FILL
            }
            val paintText = Paint().apply {
                color = Color.WHITE
                textSize = 42f
                isAntiAlias = true
            }
            val paintSubText = Paint().apply {
                color = Color.rgb(140, 160, 185)
                textSize = 28f
                isAntiAlias = true
            }

            while (isActive) {
                val surface = activeSurface
                if (surface != null && surface.isValid) {
                    try {
                        val canvas: Canvas? = surface.lockCanvas(null)
                        if (canvas != null) {
                            try {
                                renderFrame(canvas, paintBg, paintGrid, paintAccent, paintFill, paintText, paintSubText)
                            } finally {
                                surface.unlockCanvasAndPost(canvas)
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.w(LogCategory.VIDEO, "Surface render error: ${e.message}")
                    }
                }
                frameCount++
                delay(16) // ~60 FPS
            }
        }
    }

    private fun renderFrame(
        canvas: Canvas,
        paintBg: Paint,
        paintGrid: Paint,
        paintAccent: Paint,
        paintFill: Paint,
        paintText: Paint,
        paintSubText: Paint
    ) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()

        // Background
        canvas.drawRect(0f, 0f, w, h, paintBg)

        // Animated Grid
        val gridSize = 100f
        val offset = (frameCount * 2 % gridSize)
        var x = offset
        while (x < w) {
            canvas.drawLine(x, 0f, x, h, paintGrid)
            x += gridSize
        }
        var y = offset
        while (y < h) {
            canvas.drawLine(0f, y, w, y, paintGrid)
            y += gridSize
        }

        // Center Orbiting Animation
        val centerX = w / 2f
        val centerY = h / 2f
        val orbitRadius = 140f
        val angle = frameCount * 0.05
        val orbX = centerX + orbitRadius * sin(angle).toFloat()
        val orbY = centerY + orbitRadius * kotlin.math.cos(angle).toFloat()

        canvas.drawCircle(centerX, centerY, orbitRadius, paintAccent)
        canvas.drawCircle(orbX, orbY, 18f, paintFill)

        // Stream HUD Info in frame
        val timeStr = dateFormat.format(Date())
        canvas.drawText("STREAMAPP SYNTHETIC MEDIA ENGINE", 60f, 90f, paintText)
        canvas.drawText("Status: LIVE | Target: ${streamConfig?.targetResolution?.label ?: "1080p"} @ 60 FPS | Codec: ${streamConfig?.videoCodec ?: "HEVC"}", 60f, 140f, paintSubText)
        canvas.drawText("Time: $timeStr | Frame #$frameCount", 60f, 180f, paintSubText)
        canvas.drawText("Last Input: $lastInputText", 60f, h - 80f, paintText)

        // Draw crosshair at center
        canvas.drawLine(centerX - 30f, centerY, centerX + 30f, centerY, paintAccent)
        canvas.drawLine(centerX, centerY - 30f, centerX, centerY + 30f, paintAccent)
    }

    private fun startStatsLoop() {
        statsJob?.cancel()
        statsJob = scope.launch(dispatchers.default) {
            while (isActive) {
                val currentConfig = streamConfig
                val rtt = Random.nextLong(12, 24)
                val jitter = 0.8f + Random.nextFloat() * 1.5f
                val fps = 59.4f + Random.nextFloat() * 1.2f
                val bitrate = (currentConfig?.maxBitrateKbps ?: 20000) - Random.nextInt(500, 2000)
                val loss = if (Random.nextInt(100) < 5) Random.nextFloat() * 0.8f else 0.0f

                _statistics.value = StreamStatistics(
                    currentFps = fps,
                    currentBitrateKbps = bitrate,
                    droppedFramesTotal = frameCount / 500,
                    packetLossPercentage = loss,
                    jitterMs = jitter,
                    roundTripTimeMs = rtt,
                    decodeTimeMs = 2.1f + Random.nextFloat() * 0.8f,
                    currentResolution = currentConfig?.targetResolution ?: Resolution.R_1080P,
                    activeCodec = currentConfig?.videoCodec ?: VideoCodec.HEVC,
                    networkType = NetworkType.WIFI
                )
                delay(500)
            }
        }
    }
}
