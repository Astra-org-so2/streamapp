package com.streamapp.core.broadcaster.stream

import android.content.Context
import android.content.Intent
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.gl.render.filters.`object`.ImageObjectFilterRender
import com.pedro.library.rtmp.RtmpCamera1
import com.pedro.library.rtmp.RtmpDisplay
import com.pedro.library.view.OpenGlView
import com.streamapp.core.broadcaster.audio.AudioMixer
import com.streamapp.core.broadcaster.overlay.CompositeSceneRenderer
import com.streamapp.core.broadcaster.overlay.OverlayEngine
import com.streamapp.core.common.dispatchers.AppDispatchers
import com.streamapp.core.common.logger.AppLogger
import com.streamapp.core.common.logger.LogCategory
import com.streamapp.core.datastore.SettingsRepository
import com.streamapp.core.model.AppSettings
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

enum class BroadcastState {
    IDLE, CONNECTING, STREAMING, ERROR, DISCONNECTED
}

@Singleton
class BroadcastManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val overlayEngineLazy: Lazy<OverlayEngine>,
    private val audioMixerLazy: Lazy<AudioMixer>,
    private val dispatchers: AppDispatchers
) : ConnectChecker {

    private var rtmpCamera1: RtmpCamera1? = null
    private var rtmpDisplay: RtmpDisplay? = null

    private val _broadcastState = MutableStateFlow(BroadcastState.IDLE)
    val broadcastState: StateFlow<BroadcastState> = _broadcastState.asStateFlow()

    private val _bitrate = MutableStateFlow(0L)
    val bitrate: StateFlow<Long> = _bitrate.asStateFlow()

    private val _fps = MutableStateFlow(60)
    val fps: StateFlow<Int> = _fps.asStateFlow()

    private val _droppedFrames = MutableStateFlow(0)
    val droppedFrames: StateFlow<Int> = _droppedFrames.asStateFlow()

    private val _cachedSettings = MutableStateFlow(AppSettings())
    val cachedSettings: StateFlow<AppSettings> = _cachedSettings.asStateFlow()

    private val bitrateMutex = Mutex()
    private var managerScope = CoroutineScope(SupervisorJob() + dispatchers.main)
    val compositeSceneRenderer = CompositeSceneRenderer(context, managerScope)
    private var compositeGlFilter: ImageObjectFilterRender? = null

    private var fpsMonitorJob: Job? = null
    private val renderedFrameCounter = AtomicInteger(0)

    init {
        observeSettings()
        observeCompositeOverlays()
    }

    private fun observeSettings() {
        managerScope.launch(dispatchers.io) {
            settingsRepository.settings.collect { settings ->
                _cachedSettings.value = settings
                if (_broadcastState.value == BroadcastState.STREAMING) {
                    val targetBitrateBps = settings.videoBitrate * 1024
                    applyBitrateOnFly(targetBitrateBps)
                }
            }
        }
    }

    private fun observeCompositeOverlays() {
        // Feed active scene layers from OverlayEngine into CompositeSceneRenderer
        managerScope.launch(dispatchers.io) {
            try {
                val overlayEngine = overlayEngineLazy.get()
                overlayEngine.activeLayers.collect { layers ->
                    compositeSceneRenderer.updateLayers(layers)
                }
            } catch (e: Exception) {
                AppLogger.e(LogCategory.STREAMING, "Error observing overlay layers", e)
            }
        }

        // Apply generated composite bitmap to RootEncoder OpenGL filters
        managerScope.launch(dispatchers.main) {
            compositeSceneRenderer.compositeBitmap.collect { bitmap ->
                if (bitmap != null && _broadcastState.value == BroadcastState.STREAMING) {
                    try {
                        if (compositeGlFilter == null) {
                            val filter = ImageObjectFilterRender()
                            compositeGlFilter = filter
                            rtmpCamera1?.glInterface?.setFilter(filter)
                            rtmpDisplay?.glInterface?.setFilter(filter)
                        }
                        compositeGlFilter?.setImage(bitmap)
                        renderedFrameCounter.incrementAndGet()
                    } catch (e: Exception) {
                        AppLogger.w(LogCategory.STREAMING, "Could not apply overlay frame to GL filter: ${e.message}")
                    }
                }
            }
        }
    }

    fun initCameraStreaming(openGlView: OpenGlView) {
        rtmpCamera1 = RtmpCamera1(openGlView, this)
        rtmpCamera1?.glInterface?.setForceRender(true)
    }

    fun initScreenStreaming(resultCode: Int, resultData: Intent) {
        rtmpDisplay = RtmpDisplay(context, true, this)
        rtmpDisplay?.setIntentResult(resultCode, resultData)
        rtmpDisplay?.glInterface?.setForceRender(true)
    }

    fun startStreaming(url: String, isScreenStream: Boolean = false) {
        val settings = _cachedSettings.value
        val (width, height) = parseResolution(settings.videoResolution)
        val videoFps = settings.videoFps.coerceIn(15, 60)
        val videoBitrateBps = (settings.videoBitrate * 1024).coerceIn(500_000, 30_000_000)
        val audioBitrateBps = (settings.audioBitrate * 1024).coerceIn(64_000, 320_000)

        // 1. Activate multi-channel audio capture and mixing pipeline (Mic + Game + Music + Alerts)
        try {
            audioMixerLazy.get().startRecording()
            AppLogger.i(LogCategory.AUDIO, "AudioMixer multi-channel capture started for RTMP broadcast")
        } catch (e: Exception) {
            AppLogger.e(LogCategory.AUDIO, "Failed to initialize AudioMixer capture", e)
        }

        // 2. Start off-screen overlay compositor
        compositeSceneRenderer.setDimensions(width, height)
        compositeSceneRenderer.startRendering(fps = 30)

        AppLogger.i(
            LogCategory.STREAMING,
            "Starting broadcast ($width x $height @ ${videoFps}fps, Video: ${videoBitrateBps / 1024} kbps, Audio: ${audioBitrateBps / 1024} kbps)"
        )

        val prepared = if (isScreenStream) {
            val videoOk = rtmpDisplay?.prepareVideo(width, height, videoFps, videoBitrateBps, 2, 0) ?: false
            val audioOk = rtmpDisplay?.prepareAudio(
                audioBitrateBps,
                48000,
                true,
                settings.isEchoCancellationEnabled,
                settings.noiseSuppressionEnabled
            ) ?: false
            if (videoOk && audioOk) {
                rtmpDisplay?.startStream(url)
                true
            } else {
                false
            }
        } else {
            val videoOk = rtmpCamera1?.prepareVideo(width, height, videoFps, videoBitrateBps, 2, 0) ?: false
            val audioOk = rtmpCamera1?.prepareAudio(
                audioBitrateBps,
                48000,
                true,
                settings.isEchoCancellationEnabled,
                settings.noiseSuppressionEnabled
            ) ?: false
            if (videoOk && audioOk) {
                rtmpCamera1?.startStream(url)
                true
            } else {
                false
            }
        }

        if (prepared) {
            startFpsMonitor(videoFps)
        } else {
            AppLogger.e(LogCategory.STREAMING, "Failed to prepare video/audio encoders for stream")
            _broadcastState.value = BroadcastState.ERROR
        }
    }

    fun stopStreaming(isScreenStream: Boolean = false) {
        fpsMonitorJob?.cancel()
        fpsMonitorJob = null
        compositeSceneRenderer.stopRendering()

        // Stop AudioMixer pipeline
        try {
            audioMixerLazy.get().stopRecording()
        } catch (e: Exception) {
            AppLogger.w(LogCategory.AUDIO, "Error stopping AudioMixer: ${e.message}")
        }

        try {
            if (isScreenStream) {
                rtmpDisplay?.stopStream()
            } else {
                rtmpCamera1?.stopStream()
            }
        } catch (e: Exception) {
            AppLogger.e(LogCategory.STREAMING, "Error stopping stream", e)
        }

        try {
            rtmpCamera1?.glInterface?.clearFilters()
            rtmpDisplay?.glInterface?.clearFilters()
        } catch (e: Exception) {
            AppLogger.w(LogCategory.STREAMING, "Error clearing GL filters: ${e.message}")
        }
        compositeGlFilter = null

        _broadcastState.value = BroadcastState.IDLE
        _bitrate.value = 0L
    }

    fun switchCamera() {
        try {
            rtmpCamera1?.switchCamera()
        } catch (e: Exception) {
            AppLogger.e(LogCategory.VIDEO, "Error switching camera", e)
        }
    }

    fun toggleTorch() {
        try {
            val isTorch = rtmpCamera1?.isLanternEnabled ?: false
            if (isTorch) {
                rtmpCamera1?.disableLantern()
            } else {
                rtmpCamera1?.enableLantern()
            }
        } catch (e: Exception) {
            AppLogger.w(LogCategory.VIDEO, "Error toggling torch: ${e.message}")
        }
    }

    fun updateBitrateOnFly(bitrateKbps: Int) {
        val bps = bitrateKbps * 1024
        managerScope.launch(dispatchers.default) {
            applyBitrateOnFly(bps)
        }
    }

    private suspend fun applyBitrateOnFly(bitrateBps: Int) {
        bitrateMutex.withLock {
            try {
                rtmpCamera1?.setVideoBitrateOnFly(bitrateBps)
                rtmpDisplay?.setVideoBitrateOnFly(bitrateBps)
                AppLogger.d(LogCategory.STREAMING, "Updated video bitrate on fly to ${bitrateBps / 1024} kbps")
            } catch (e: Exception) {
                AppLogger.w(LogCategory.STREAMING, "Failed to update bitrate on the fly: ${e.message}")
            }
        }
    }

    private fun startFpsMonitor(targetFps: Int) {
        fpsMonitorJob?.cancel()
        renderedFrameCounter.set(0)
        fpsMonitorJob = managerScope.launch(dispatchers.default) {
            var lastCalculationTime = System.currentTimeMillis()
            var prevFrameCount = 0

            while (isActive) {
                delay(1000)
                val now = System.currentTimeMillis()
                val deltaSec = max(0.001f, (now - lastCalculationTime) / 1000f)
                lastCalculationTime = now

                val currentFrames = renderedFrameCounter.get()
                val deltaFrames = (currentFrames - prevFrameCount).coerceAtLeast(0)
                prevFrameCount = currentFrames

                // Measure real frame delivery rate from rendering loop
                val measuredFps = (deltaFrames / deltaSec).toInt()
                _fps.value = if (measuredFps > 0) measuredFps.coerceIn(10, 60) else targetFps
            }
        }
    }

    private fun parseResolution(resolutionStr: String): Pair<Int, Int> {
        return when (resolutionStr.uppercase()) {
            "4K", "2160P" -> 3840 to 2160
            "1440P", "2K" -> 2560 to 1440
            "1080P" -> 1920 to 1080
            "720P" -> 1280 to 720
            "480P" -> 854 to 480
            "360P" -> 640 to 360
            else -> 1920 to 1080
        }
    }

    fun release() {
        stopStreaming(isScreenStream = true)
        stopStreaming(isScreenStream = false)
        managerScope.launch(dispatchers.main) {
            compositeSceneRenderer.release()
            managerScope.cancel()
        }
    }

    override fun onConnectionStarted(url: String) {
        _broadcastState.value = BroadcastState.CONNECTING
        AppLogger.i(LogCategory.STREAMING, "RTMP Connection started to $url")
    }

    override fun onConnectionSuccess() {
        _broadcastState.value = BroadcastState.STREAMING
        AppLogger.i(LogCategory.STREAMING, "RTMP Connection established successfully")
    }

    override fun onConnectionFailed(reason: String) {
        _broadcastState.value = BroadcastState.ERROR
        AppLogger.e(LogCategory.STREAMING, "RTMP Connection failed: $reason")
    }

    override fun onNewBitrate(bitrate: Long) {
        _bitrate.value = bitrate
    }

    override fun onDisconnect() {
        _broadcastState.value = BroadcastState.DISCONNECTED
        AppLogger.i(LogCategory.STREAMING, "RTMP Broadcast disconnected")
    }

    override fun onAuthError() {
        _broadcastState.value = BroadcastState.ERROR
        AppLogger.e(LogCategory.STREAMING, "RTMP Authentication error")
    }

    override fun onAuthSuccess() {
        AppLogger.i(LogCategory.STREAMING, "RTMP Authentication successful")
    }
}
