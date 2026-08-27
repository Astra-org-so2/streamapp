package com.streamapp.core.features.smartmute

import com.streamapp.core.common.logger.AppLogger
import com.streamapp.core.common.logger.LogCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartMuteController @Inject constructor() {

    private var isAutomaticMuteActive = false
    private var manualOverrideMuteState: Boolean? = null

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    fun onScreenAnalyzed(isApparentLoadingScreen: Boolean) {
        isAutomaticMuteActive = isApparentLoadingScreen
        recomputeEffectiveMute()
    }

    fun manualOverrideMute(mute: Boolean?) {
        manualOverrideMuteState = mute
        recomputeEffectiveMute()
        AppLogger.i(LogCategory.FEATURES, "SmartMute manual override set to: $mute")
    }

    fun clearManualOverride() {
        manualOverrideMuteState = null
        recomputeEffectiveMute()
    }

    private fun recomputeEffectiveMute() {
        val effective = manualOverrideMuteState ?: isAutomaticMuteActive
        _isMuted.value = effective
    }
}
