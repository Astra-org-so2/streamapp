package com.streamapp.core.features

import androidx.compose.ui.graphics.Color
import com.streamapp.core.broadcaster.stream.BroadcastManager
import com.streamapp.core.broadcaster.stream.BroadcastState
import com.streamapp.core.datastore.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

enum class NetworkHealthLevel {
    EXCELLENT, GOOD, UNSTABLE, CRITICAL
}

data class StreamHealthStats(
    val bitrateKbps: Int = 0,
    val targetBitrateKbps: Int = 6000,
    val fps: Int = 60,
    val droppedFrames: Int = 0,
    val durationSeconds: Long = 0L,
    val stabilityScore: Int = 100, // 0 .. 100%
    val healthLevel: NetworkHealthLevel = NetworkHealthLevel.EXCELLENT
)

@Singleton
class StreamHealthMonitor @Inject constructor(
    private val broadcastManager: BroadcastManager,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _healthStats = MutableStateFlow(StreamHealthStats())
    val healthStats: StateFlow<StreamHealthStats> = _healthStats.asStateFlow()

    private var streamStartTime = 0L
    private var monitorJob: Job? = null

    init {
        scope.launch {
            broadcastManager.broadcastState.collect { state ->
                if (state == BroadcastState.STREAMING) {
                    startMonitoring()
                } else {
                    stopMonitoring()
                }
            }
        }
    }

    private fun startMonitoring() {
        streamStartTime = System.currentTimeMillis()
        monitorJob?.cancel()
        monitorJob = scope.launch {
            var currentTargetBitrate = 6000
            val s = settingsRepository.settings.firstOrNull()
            if (s != null) {
                currentTargetBitrate = s.videoBitrate
            }

            var consecutiveLowBitrateCount = 0

            while (isActive) {
                val rawBitrate = broadcastManager.bitrate.value // bps
                val bitrateKbps = (rawBitrate / 1024).toInt()
                val durationSec = (System.currentTimeMillis() - streamStartTime) / 1000L
                val fps = broadcastManager.fps.value
                val dropped = broadcastManager.droppedFrames.value

                // Stability Calculation
                val ratio = if (currentTargetBitrate > 0) (bitrateKbps.toFloat() / currentTargetBitrate).coerceIn(0f, 1.2f) else 1f
                val score = ((ratio * 100) - (dropped * 2)).toInt().coerceIn(10, 100)

                val level = when {
                    score >= 85 -> NetworkHealthLevel.EXCELLENT
                    score >= 65 -> NetworkHealthLevel.GOOD
                    score >= 40 -> NetworkHealthLevel.UNSTABLE
                    else -> NetworkHealthLevel.CRITICAL
                }

                // Dynamic Adaptive Bitrate logic
                if (s?.adaptiveBitrateEnabled == true && durationSec > 5) {
                    if (level == NetworkHealthLevel.UNSTABLE || level == NetworkHealthLevel.CRITICAL) {
                        consecutiveLowBitrateCount++
                        if (consecutiveLowBitrateCount >= 3) {
                            val lowerBitrate = (currentTargetBitrate * 0.75f).roundToInt().coerceAtLeast(1500)
                            broadcastManager.updateBitrateOnFly(lowerBitrate)
                            consecutiveLowBitrateCount = 0
                        }
                    } else if (level == NetworkHealthLevel.EXCELLENT && consecutiveLowBitrateCount < 0) {
                        broadcastManager.updateBitrateOnFly(currentTargetBitrate)
                    }
                }

                _healthStats.value = StreamHealthStats(
                    bitrateKbps = if (bitrateKbps > 0) bitrateKbps else currentTargetBitrate,
                    targetBitrateKbps = currentTargetBitrate,
                    fps = fps,
                    droppedFrames = dropped,
                    durationSeconds = durationSec,
                    stabilityScore = score,
                    healthLevel = level
                )

                delay(1000)
            }
        }
    }

    private fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        _healthStats.value = StreamHealthStats()
    }
}
