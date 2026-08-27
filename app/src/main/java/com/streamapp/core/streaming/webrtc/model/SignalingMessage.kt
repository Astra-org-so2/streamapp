package com.streamapp.core.streaming.webrtc.model

import kotlinx.serialization.Serializable

@Serializable
sealed class SignalingMessage {
    @Serializable
    data class Offer(val sdp: String, val type: String = "offer") : SignalingMessage()

    @Serializable
    data class Answer(val sdp: String, val type: String = "answer") : SignalingMessage()

    @Serializable
    data class IceCandidateMsg(
        val sdpMid: String,
        val sdpMLineIndex: Int,
        val sdpCandidate: String
    ) : SignalingMessage()

    @Serializable
    data class Ping(val timestamp: Long = System.currentTimeMillis()) : SignalingMessage()

    @Serializable
    data class ErrorMsg(val reason: String) : SignalingMessage()
}
