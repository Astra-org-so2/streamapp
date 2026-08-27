package com.streamapp.core.model

data class BroadcastDestination(
    val id: String,
    val platform: Platform,
    val name: String,
    val rtmpUrl: String,
    val streamKey: String,
    val isEnabled: Boolean
)
