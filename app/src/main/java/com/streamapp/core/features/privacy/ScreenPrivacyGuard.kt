package com.streamapp.core.features.privacy

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Singleton

@AndroidEntryPoint
class ScreenPrivacyGuard : NotificationListenerService() {

    @Inject
    lateinit var privacyNotifier: PrivacyNotifier

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let {
            privacyNotifier.notifyNotificationVisible(true)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        privacyNotifier.notifyNotificationVisible(false)
    }
}

@Singleton
class PrivacyNotifier @Inject constructor() {
    private val _shouldBlurScreen = MutableStateFlow(false)
    val shouldBlurScreen: StateFlow<Boolean> = _shouldBlurScreen.asStateFlow()
    
    private var activeNotifications = 0

    fun notifyNotificationVisible(isVisible: Boolean) {
        if (isVisible) {
            activeNotifications++
        } else {
            activeNotifications = (activeNotifications - 1).coerceAtLeast(0)
        }
        _shouldBlurScreen.value = activeNotifications > 0
    }
}
