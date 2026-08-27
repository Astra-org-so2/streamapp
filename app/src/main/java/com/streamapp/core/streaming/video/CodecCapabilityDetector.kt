package com.streamapp.core.streaming.video

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import com.streamapp.core.common.logger.AppLogger
import com.streamapp.core.common.logger.LogCategory
import com.streamapp.core.model.stream.VideoCodec
import javax.inject.Inject
import javax.inject.Singleton

data class CodecProfileCapability(
    val codec: VideoCodec,
    val isHardwareAccelerated: Boolean,
    val isLowLatencySupported: Boolean,
    val maxResolutionWidth: Int,
    val maxResolutionHeight: Int
)

@Singleton
class CodecCapabilityDetector @Inject constructor() {

    fun getSupportedCodecs(): List<CodecProfileCapability> {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        val codecInfos = codecList.codecInfos

        return VideoCodec.entries.map { codec ->
            checkCodecSupport(codec, codecInfos)
        }
    }

    fun getPreferredCodec(): VideoCodec {
        val capabilities = getSupportedCodecs()
        AppLogger.i(LogCategory.VIDEO, "Device Codec Capabilities: $capabilities")

        // Preferred order: AV1 -> HEVC -> H264 -> VP9
        val preferred = capabilities
            .filter { it.isHardwareAccelerated }
            .sortedByDescending { cap ->
                when (cap.codec) {
                    VideoCodec.AV1 -> 4
                    VideoCodec.HEVC -> 3
                    VideoCodec.H264 -> 2
                    VideoCodec.VP9 -> 1
                }
            }
            .firstOrNull()?.codec ?: VideoCodec.H264

        AppLogger.i(LogCategory.VIDEO, "Selected preferred hardware codec: $preferred")
        return preferred
    }

    private fun checkCodecSupport(
        codec: VideoCodec,
        codecInfos: Array<MediaCodecInfo>
    ): CodecProfileCapability {
        for (info in codecInfos) {
            if (info.isEncoder) continue
            val types = info.supportedTypes
            for (type in types) {
                if (type.equals(codec.mimeType, ignoreCase = true)) {
                    val caps = info.getCapabilitiesForType(type)
                    val isHw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        info.isHardwareAccelerated
                    } else {
                        !info.name.startsWith("OMX.google.") && !info.name.startsWith("c2.android.")
                    }

                    val isLowLatency = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        caps.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency)
                    } else {
                        false
                    }

                    val videoCaps = caps.videoCapabilities
                    val maxWidth = videoCaps?.supportedWidths?.upper ?: 1920
                    val maxHeight = videoCaps?.supportedHeights?.upper ?: 1080

                    return CodecProfileCapability(
                        codec = codec,
                        isHardwareAccelerated = isHw,
                        isLowLatencySupported = isLowLatency,
                        maxResolutionWidth = maxWidth,
                        maxResolutionHeight = maxHeight
                    )
                }
            }
        }

        return CodecProfileCapability(
            codec = codec,
            isHardwareAccelerated = false,
            isLowLatencySupported = false,
            maxResolutionWidth = 1280,
            maxResolutionHeight = 720
        )
    }
}
