package com.streamapp.core.streaming.webrtc.reconnect

import com.streamapp.core.model.connection.StreamingConnectionState
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WebRtcReconnectManagerTest {

    private lateinit var manager: WebRtcReconnectManager

    @BeforeEach
    fun setUp() {
        manager = WebRtcReconnectManager()
    }

    @Test
    fun `when reconnect succeeds on first attempt, return true and state resets`() = runTest {
        val success = manager.attemptReconnect { true }
        assertTrue(success)
        assertTrue(manager.reconnectState.value is StreamingConnectionState.Connected)
    }

    @Test
    fun `when reconnect attempts exceed max, transition to Error state`() = runTest {
        // Attempt 5 failures
        repeat(5) {
            val res = manager.attemptReconnect { false }
            assertFalse(res)
        }

        // 6th attempt exceeds maxAttempts
        val finalRes = manager.attemptReconnect { false }
        assertFalse(finalRes)
        assertTrue(manager.reconnectState.value is StreamingConnectionState.Error)
    }
}
