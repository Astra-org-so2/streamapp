package com.streamapp.core.streaming.adaptive

import com.streamapp.core.model.stream.NetworkType
import com.streamapp.core.model.stream.Resolution
import com.streamapp.core.model.stream.StreamStatistics
import com.streamapp.core.model.stream.TargetFps
import com.streamapp.core.model.stream.VideoCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AdaptiveQualityEngineTest {

    private lateinit var engine: AdaptiveQualityEngine

    @BeforeEach
    fun setUp() {
        engine = AdaptiveQualityEngine()
    }

    @Test
    fun `when network has brief single packet loss spike, do not downshift immediately`() {
        val degradedStats = StreamStatistics(
            packetLossPercentage = 5.0f,
            roundTripTimeMs = 120
        )

        val decision1 = engine.evaluate(degradedStats)
        assertNull(decision1, "Single degraded tick should not trigger downshift")

        val decision2 = engine.evaluate(degradedStats)
        assertNull(decision2, "Two degraded ticks should not trigger downshift")
    }

    @Test
    fun `when network is degraded for 3 consecutive ticks, trigger downshift with hysteresis`() {
        val degradedStats = StreamStatistics(
            packetLossPercentage = 4.5f,
            roundTripTimeMs = 95
        )

        engine.evaluate(degradedStats) // tick 1
        engine.evaluate(degradedStats) // tick 2
        val decision = engine.evaluate(degradedStats) // tick 3

        assertNotNull(decision, "Three consecutive degraded ticks must trigger downshift")
        assertEquals(TargetFps.FPS_30, decision?.recommendedFps)
    }

    @Test
    fun `when network is clean for sustained period, trigger upshift`() {
        // First downshift
        val degradedStats = StreamStatistics(packetLossPercentage = 5.0f, roundTripTimeMs = 100)
        repeat(3) { engine.evaluate(degradedStats) }

        // Clean stats
        val cleanStats = StreamStatistics(
            packetLossPercentage = 0.0f,
            roundTripTimeMs = 15,
            jitterMs = 1.0f,
            decodeTimeMs = 2.0f
        )

        // 14 ticks should not upshift yet
        repeat(14) {
            val decision = engine.evaluate(cleanStats)
            assertNull(decision, "Should require 15 ticks before upshift")
        }

        // 15th tick triggers upshift
        val upshiftDecision = engine.evaluate(cleanStats)
        assertNotNull(upshiftDecision, "15th clean tick must trigger quality upshift")
    }
}
