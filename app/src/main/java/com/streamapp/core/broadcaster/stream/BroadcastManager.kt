package com.streamapp.core.broadcaster.stream

import android.content.Context
import android.media.projection.MediaProjection
import android.view.SurfaceView
import com.pedro.library.rtmp.RtmpCamera1
import com.pedro.library.rtmp.RtmpDisplay
import com.pedro.common.ConnectChecker
import com.streamapp.core.datastore.SettingsRepository
import com.streamapp.core.broadcaster.overlay.CompositeSceneRenderer
import com.streamapp.core.broadcaster.overlay.OverlayEngine
import com.streamapp.core.broadcaster.overlay.WebOverlayRenderer
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

enum class BroadcastState {
    IDLE, CONNECTING, STREAMING, ERROR, DISCONNECTED
}

@Singleton
class BroadcastManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val overlayEngineLazy: Lazy<OverlayEngine>
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
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    val compositeSceneRenderer = CompositeSceneRenderer(context, scope)
    private var compositeGlFilter: com.pedro.encoder.input.gl.render.filters.`object`.ImageObjectFilterRender? = null

    init {
        observeSettings()
        observeCompositeOverlays()
    }

    private fun observeSettings() {
        scope.launch {
            settingsRepository.settings.collect { settings ->
                if (_broadcastState.value == BroadcastState.STREAMING) {
                    val targetBitrateBps = settings.videoBitrate * 1024
                    rtmpCamera1?.setVideoBitrateOnFly(targetBitrateBps)
                    rtmpDisplay?.setVideoBitrateOnFly(targetBitrateBps)
                }
            }
        }
    }

    private fun observeCompositeOverlays() {
        // Feed active scene layers from OverlayEngine into CompositeSceneRenderer
        scope.launch {
            try {
                val overlayEngine = overlayEngineLazy.get()
                overlayEngine.activeLayers.collect { layers ->
                    compositeSceneRenderer.updateLayers(layers)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Apply generated composite bitmap to RootEncoder OpenGL filters
        scope.launch {
            compositeSceneRenderer.compositeBitmap.collect { bitmap ->
                if (bitmap != null) {
                    if (compositeGlFilter == null) {
                        try {
                            compositeGlFilter = com.pedro.encoder.input.gl.render.filters.`object`.ImageObjectFilterRender()
                            rtmpCamera1?.glInterface?.setFilter(compositeGlFilter!!)
                            rtmpDisplay?.glInterface?.setFilter(compositeGlFilter!!)
                        } catch (e: Exception) {
                            // Filter not supported
                        }
                    }
                    compositeGlFilter?.setImage(bitmap)
                }
            }
        }
    }

    fun initCameraStreaming(openGlView: com.pedro.library.view.OpenGlView) {
        rtmpCamera1 = RtmpCamera1(openGlView, this)
        rtmpCamera1?.glInterface?.setForceRender(true)
    }

    fun initScreenStreaming(resultCode: Int, resultData: android.content.Intent) {
        rtmpDisplay = RtmpDisplay(context, true, this)
        rtmpDisplay?.setIntentResult(resultCode, resultData)
        rtmpDisplay?.glInterface?.setForceRender(true)
    }

    fun startStreaming(url: String, isScreenStream: Boolean = false) {
        val settings = runBlocking { settingsRepository.settings.firstOrNull() }
        val videoBitrate = (settings?.videoBitrate ?: 6000) * 1024
        
        compositeSceneRenderer.startRendering(fps = 30)

        if (isScreenStream) {
            if (rtmpDisplay?.prepareVideo() == true && rtmpDisplay?.prepareAudio() == true) {
                rtmpDisplay?.startStream(url)
            }
        } else {
            if (rtmpCamera1?.prepareAudio() == true && rtmpCamera1?.prepareVideo() == true) {
                rtmpCamera1?.startStream(url)
            }
        }
    }

    fun stopStreaming(isScreenStream: Boolean = false) {
        compositeSceneRenderer.stopRendering()
        if (isScreenStream) {
            rtmpDisplay?.stopStream()
        } else {
            rtmpCamera1?.stopStream()
        }
        _broadcastState.value = BroadcastState.IDLE
    }

    fun switchCamera() {
        try {
            rtmpCamera1?.switchCamera()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateBitrateOnFly(bitrateKbps: Int) {
        val bps = bitrateKbps * 1024
        rtmpCamera1?.setVideoBitrateOnFly(bps)
        rtmpDisplay?.setVideoBitrateOnFly(bps)
    }

    override fun onConnectionStarted(url: String) {
        _broadcastState.value = BroadcastState.CONNECTING
    }

    override fun onConnectionSuccess() {
        _broadcastState.value = BroadcastState.STREAMING
    }

    override fun onConnectionFailed(reason: String) {
        _broadcastState.value = BroadcastState.ERROR
    }

    override fun onNewBitrate(bitrate: Long) {
        _bitrate.value = bitrate
    }

    override fun onDisconnect() {
        _broadcastState.value = BroadcastState.DISCONNECTED
    }

    override fun onAuthError() {
        _broadcastState.value = BroadcastState.ERROR
    }

    override fun onAuthSuccess() {
        // Auth success
    }
}
