package com.streamapp.core.streaming.adaptive

import com.streamapp.core.common.logger.AppLogger
import com.streamapp.core.common.logger.LogCategory
import com.streamapp.core.model.stream.Resolution
import com.streamapp.core.model.stream.StreamConfiguration
import com.streamapp.core.model.stream.StreamStatistics
import com.streamapp.core.model.stream.TargetFps
import javax.inject.Inject
import javax.inject.Singleton

data class QualityAdaptationDecision(
    val recommendedResolution: Resolution,
    val recommendedFps: TargetFps,
    val recommendedBitrateKbps: Int,
    val reason: String
)

@Singleton
class AdaptiveQualityEngine @Inject constructor() {

    private var consecutiveDegradedTicks = 0
    private var consecutiveCleanTicks = 0

    private var activeConfig: StreamConfiguration? = null
    private var currentResolution: Resolution = Resolution.R_1080P
    private var currentFps: TargetFps = TargetFps.FPS_60
    private var currentBitrateKbps: Int = 20_000

    /**
     * Initializes adaptive engine with the active stream's target configuration.
     */
    fun initialize(config: StreamConfiguration) {
        activeConfig = config
        currentResolution = config.targetResolution
        currentFps = config.targetFps
        currentBitrateKbps = config.maxBitrateKbps
        consecutiveDegradedTicks = 0
        consecutiveCleanTicks = 0
        AppLogger.i(
            LogCategory.STREAMING,
            "AdaptiveQualityEngine initialized with ${config.targetResolution} @ ${config.targetFps.value} FPS (Max: ${config.maxBitrateKbps} kbps)"
        )
    }

    /**
     * Evaluates streaming telemetry and applies hysteresis to prevent flapping.
     * Downshift requires 3 consecutive degraded ticks (~1.5s).
     * Upshift requires 15 consecutive clean ticks (~7.5s).
     */
    fun evaluate(stats: StreamStatistics): QualityAdaptationDecision? {
        val isDegraded = stats.packetLossPercentage > 2.5f ||
                stats.roundTripTimeMs > 75 ||
                stats.jitterMs > 15f ||
                stats.decodeTimeMs > 16.6f

        val isClean = stats.packetLossPercentage < 0.3f &&
                stats.roundTripTimeMs < 35 &&
                stats.jitterMs < 4f &&
                stats.decodeTimeMs < 6f

        if (isDegraded) {
            consecutiveDegradedTicks++
            consecutiveCleanTicks = 0

            if (consecutiveDegradedTicks >= 3) {
                consecutiveDegradedTicks = 0
                return downshiftQuality("High packet loss (${stats.packetLossPercentage}%) or RTT (${stats.roundTripTimeMs}ms)")
            }
        } else if (isClean) {
            consecutiveCleanTicks++
            consecutiveDegradedTicks = 0

            if (consecutiveCleanTicks >= 15) {
                consecutiveCleanTicks = 0
                return upshiftQuality("Stable clean network detected for 7.5s")
            }
        } else {
            // Neutral state: reset hysteresis counters
            consecutiveDegradedTicks = (consecutiveDegradedTicks - 1).coerceAtLeast(0)
            consecutiveCleanTicks = (consecutiveCleanTicks - 1).coerceAtLeast(0)
        }

        return null
    }

    private fun downshiftQuality(reason: String): QualityAdaptationDecision {
        when {
            currentResolution == Resolution.R_4K -> {
                currentResolution = Resolution.R_1440P
                currentBitrateKbps = 25_000
            }
            currentResolution == Resolution.R_1440P -> {
                currentResolution = Resolution.R_1080P
                currentBitrateKbps = 15_000
            }
            currentResolution == Resolution.R_1080P && currentFps == TargetFps.FPS_60 -> {
                currentFps = TargetFps.FPS_30
                currentBitrateKbps = 10_000
            }
            currentResolution == Resolution.R_1080P && currentFps == TargetFps.FPS_30 -> {
                currentResolution = Resolution.R_720P
                currentFps = TargetFps.FPS_60
                currentBitrateKbps = 8_000
            }
            currentResolution == Resolution.R_720P && currentFps == TargetFps.FPS_60 -> {
                currentFps = TargetFps.FPS_30
                currentBitrateKbps = 4_000
            }
        }

        AppLogger.i(LogCategory.STREAMING, "Adaptive Engine Downshift: $currentResolution @ ${currentFps.value} FPS ($currentBitrateKbps kbps) - $reason")
        return QualityAdaptationDecision(currentResolution, currentFps, currentBitrateKbps, reason)
    }

    private fun upshiftQuality(reason: String): QualityAdaptationDecision {
        when {
            currentResolution == Resolution.R_720P && currentFps == TargetFps.FPS_30 -> {
                currentFps = TargetFps.FPS_60
                currentBitrateKbps = 8_000
            }
            currentResolution == Resolution.R_720P && currentFps == TargetFps.FPS_60 -> {
                currentResolution = Resolution.R_1080P
                currentFps = TargetFps.FPS_30
                currentBitrateKbps = 10_000
            }
            currentResolution == Resolution.R_1080P && currentFps == TargetFps.FPS_30 -> {
                currentFps = TargetFps.FPS_60
                currentBitrateKbps = 18_000
            }
            currentResolution == Resolution.R_1080P && currentFps == TargetFps.FPS_60 -> {
                currentResolution = Resolution.R_1440P
                currentBitrateKbps = 25_000
            }
        }

        AppLogger.i(LogCategory.STREAMING, "Adaptive Engine Upshift: $currentResolution @ ${currentFps.value} FPS ($currentBitrateKbps kbps) - $reason")
        return QualityAdaptationDecision(currentResolution, currentFps, currentBitrateKbps, reason)
    }
}
