package com.streamapp.core.model

enum class Platform(
    val defaultRtmpUrl: String,
    val defaultName: String
) {
    TWITCH("rtmp://live.twitch.tv/app/", "Twitch"),
    KICK("rtmp://fa723fc1b171.global-contribute.live-video.net/app/", "Kick"),
    YOUTUBE("rtmp://a.rtmp.youtube.com/live2", "YouTube Live"),
    TIKTOK("rtmp://live-push.tiktok.com/live/", "TikTok Live"),
    CUSTOM("", "Custom RTMP")
}
