package com.streamapp.core.model

data class BroadcastStats(
    val bitrate: Int,
    val fps: Int,
    val duration: Long,
    val droppedFrames: Int
)
