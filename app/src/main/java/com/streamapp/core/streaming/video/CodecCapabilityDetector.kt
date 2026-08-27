package com.streamapp.core.streaming.video

import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecCapabilities
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
    val maxResolutionHeight: Int,
    val maxBitrateBps: Int,
    val maxFrameRate: Int,
    val supportsSurfaceInput: Boolean,
    val supportedProfiles: List<Int> = emptyList()
)

@Singleton
class CodecCapabilityDetector @Inject constructor() {

    fun getSupportedCodecs(): List<CodecProfileCapability> {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        val codecInfos = codecList.codecInfos

        return VideoCodec.entries.map { codec ->
            checkEncoderSupport(codec, codecInfos)
        }
    }

    fun getPreferredCodec(): VideoCodec {
        val capabilities = getSupportedCodecs()
        AppLogger.i(LogCategory.VIDEO, "Device Hardware Encoder Capabilities: $capabilities")

        // Preferred order for broadcasting: AV1 -> HEVC -> H264 -> VP9
        val preferred = capabilities
            .filter { it.isHardwareAccelerated && it.supportsSurfaceInput }
            .sortedByDescending { cap ->
                when (cap.codec) {
                    VideoCodec.AV1 -> 4
                    VideoCodec.HEVC -> 3
                    VideoCodec.H264 -> 2
                    VideoCodec.VP9 -> 1
                }
            }
            .firstOrNull()?.codec ?: VideoCodec.H264

        AppLogger.i(LogCategory.VIDEO, "Selected preferred hardware video encoder: $preferred")
        return preferred
    }

    private fun checkEncoderSupport(
        codec: VideoCodec,
        codecInfos: Array<MediaCodecInfo>
    ): CodecProfileCapability {
        for (info in codecInfos) {
            // Must inspect ENCODER capabilities for streaming broadcast
            if (!info.isEncoder) continue

            val types = info.supportedTypes
            for (type in types) {
                if (type.equals(codec.mimeType, ignoreCase = true)) {
                    val caps = try {
                        info.getCapabilitiesForType(type)
                    } catch (e: Exception) {
                        null
                    } ?: continue

                    val isHw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        info.isHardwareAccelerated
                    } else {
                        !info.name.startsWith("OMX.google.", ignoreCase = true) &&
                                !info.name.startsWith("c2.android.", ignoreCase = true)
                    }

                    val isLowLatency = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        caps.isFeatureSupported(CodecCapabilities.FEATURE_LowLatency)
                    } else {
                        false
                    }

                    val videoCaps = caps.videoCapabilities
                    val maxWidth = videoCaps?.supportedWidths?.upper ?: 1920
                    val maxHeight = videoCaps?.supportedHeights?.upper ?: 1080
                    val maxBitrate = videoCaps?.bitrateRange?.upper ?: 20_000_000
                    val maxFps = videoCaps?.supportedFrameRates?.upper?.toInt() ?: 60

                    val supportsSurface = caps.colorFormats.contains(CodecCapabilities.COLOR_FormatSurface)
                    val profiles = caps.profileLevels?.map { it.profile } ?: emptyList()

                    return CodecProfileCapability(
                        codec = codec,
                        isHardwareAccelerated = isHw,
                        isLowLatencySupported = isLowLatency,
                        maxResolutionWidth = maxWidth,
                        maxResolutionHeight = maxHeight,
                        maxBitrateBps = maxBitrate,
                        maxFrameRate = maxFps,
                        supportsSurfaceInput = supportsSurface,
                        supportedProfiles = profiles
                    )
                }
            }
        }

        return CodecProfileCapability(
            codec = codec,
            isHardwareAccelerated = false,
            isLowLatencySupported = false,
            maxResolutionWidth = 1280,
            maxResolutionHeight = 720,
            maxBitrateBps = 6_000_000,
            maxFrameRate = 30,
            supportsSurfaceInput = false,
            supportedProfiles = emptyList()
        )
    }
}
