package com.streamapp.core.features

import com.streamapp.core.broadcaster.stream.BroadcastManager
import com.streamapp.core.broadcaster.stream.BroadcastState
import com.streamapp.core.common.logger.AppLogger
import com.streamapp.core.common.logger.LogCategory
import com.streamapp.core.datastore.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

enum class NetworkHealthLevel {
    EXCELLENT, GOOD, UNSTABLE, CRITICAL
}

enum class AdaptiveBitrateState {
    STABLE, DEGRADED, REDUCING, RECOVERING
}

data class StreamHealthStats(
    val bitrateKbps: Int = 0,
    val targetBitrateKbps: Int = 6000,
    val fps: Int = 60,
    val droppedFrames: Int = 0,
    val deltaDroppedFrames: Int = 0,
    val durationSeconds: Long = 0L,
    val stabilityScore: Int = 100, // 0 .. 100%
    val healthLevel: NetworkHealthLevel = NetworkHealthLevel.EXCELLENT,
    val adaptiveState: AdaptiveBitrateState = AdaptiveBitrateState.STABLE
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
            val settings = settingsRepository.settings.firstOrNull()
            val baseTargetBitrate = settings?.videoBitrate ?: 6000
            var currentAdaptiveBitrate = baseTargetBitrate

            var consecutiveUnstableCount = 0
            var consecutiveStableCount = 0
            var previousCumulativeDropped = 0
            var currentAdaptiveState = AdaptiveBitrateState.STABLE

            while (isActive) {
                val rawBitrate = broadcastManager.bitrate.value // bps
                val currentBitrateKbps = (rawBitrate / 1024).toInt()
                val durationSec = (System.currentTimeMillis() - streamStartTime) / 1000L
                val fps = broadcastManager.fps.value
                val totalDropped = broadcastManager.droppedFrames.value

                // 1. Calculate windowed delta dropped frames instead of lifetime cumulative
                val deltaDropped = (totalDropped - previousCumulativeDropped).coerceAtLeast(0)
                previousCumulativeDropped = totalDropped

                // 2. Dynamic Stability Score based on current delivery vs active target & interval drops
                val bitrateRatio = if (currentAdaptiveBitrate > 0) {
                    (currentBitrateKbps.toFloat() / currentAdaptiveBitrate).coerceIn(0f, 1.2f)
                } else {
                    1.0f
                }

                // Penalize for delta dropped in current 1s interval
                val score = ((bitrateRatio * 100) - (deltaDropped * 10)).toInt().coerceIn(10, 100)

                val level = when {
                    score >= 85 && deltaDropped == 0 -> NetworkHealthLevel.EXCELLENT
                    score >= 65 && deltaDropped <= 1 -> NetworkHealthLevel.GOOD
                    score >= 40 || deltaDropped <= 4 -> NetworkHealthLevel.UNSTABLE
                    else -> NetworkHealthLevel.CRITICAL
                }

                // 3. Adaptive Bitrate State Machine
                if (settings?.adaptiveBitrateEnabled == true && durationSec > 5) {
                    if (level == NetworkHealthLevel.UNSTABLE || level == NetworkHealthLevel.CRITICAL || deltaDropped > 2) {
                        consecutiveUnstableCount++
                        consecutiveStableCount = 0

                        if (consecutiveUnstableCount >= 3) {
                            currentAdaptiveState = AdaptiveBitrateState.REDUCING
                            val lowerBitrate = (currentAdaptiveBitrate * 0.75f).roundToInt().coerceAtLeast(1500)
                            if (lowerBitrate != currentAdaptiveBitrate) {
                                currentAdaptiveBitrate = lowerBitrate
                                broadcastManager.updateBitrateOnFly(currentAdaptiveBitrate)
                                AppLogger.w(LogCategory.STREAMING, "Adaptive bitrate reduced to $currentAdaptiveBitrate kbps (Score: $score, DeltaDropped: $deltaDropped)")
                            }
                            consecutiveUnstableCount = 0
                            currentAdaptiveState = AdaptiveBitrateState.DEGRADED
                        }
                    } else if (level == NetworkHealthLevel.EXCELLENT && deltaDropped == 0) {
                        consecutiveStableCount++
                        consecutiveUnstableCount = 0

                        if (consecutiveStableCount >= 6 && currentAdaptiveBitrate < baseTargetBitrate) {
                            currentAdaptiveState = AdaptiveBitrateState.RECOVERING
                            val higherBitrate = (currentAdaptiveBitrate * 1.15f).roundToInt().coerceAtMost(baseTargetBitrate)
                            if (higherBitrate != currentAdaptiveBitrate) {
                                currentAdaptiveBitrate = higherBitrate
                                broadcastManager.updateBitrateOnFly(currentAdaptiveBitrate)
                                AppLogger.i(LogCategory.STREAMING, "Adaptive bitrate recovered to $currentAdaptiveBitrate kbps")
                            }
                            consecutiveStableCount = 0
                            currentAdaptiveState = if (currentAdaptiveBitrate >= baseTargetBitrate) AdaptiveBitrateState.STABLE else AdaptiveBitrateState.RECOVERING
                        } else if (currentAdaptiveBitrate >= baseTargetBitrate) {
                            currentAdaptiveState = AdaptiveBitrateState.STABLE
                        }
                    }
                }

                _healthStats.value = StreamHealthStats(
                    bitrateKbps = if (currentBitrateKbps > 0) currentBitrateKbps else currentAdaptiveBitrate,
                    targetBitrateKbps = currentAdaptiveBitrate,
                    fps = fps,
                    droppedFrames = totalDropped,
                    deltaDroppedFrames = deltaDropped,
                    durationSeconds = durationSec,
                    stabilityScore = score,
                    healthLevel = level,
                    adaptiveState = currentAdaptiveState
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
