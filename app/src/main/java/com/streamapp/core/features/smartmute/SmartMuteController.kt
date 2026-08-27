package com.streamapp.core.features.smartmute

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class SmartMuteController @Inject constructor() {

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    fun onScreenAnalyzed(isApparentLoadingScreen: Boolean) {
        if (isApparentLoadingScreen && !_isMuted.value) {
            _isMuted.value = true
        } else if (!isApparentLoadingScreen && _isMuted.value) {
            _isMuted.value = false
        }
    }

    fun manualOverrideMute(mute: Boolean) {
        _isMuted.value = mute
    }
}
