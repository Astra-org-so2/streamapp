package com.streamapp.core.features.privacy

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.streamapp.core.common.logger.AppLogger
import com.streamapp.core.common.logger.LogCategory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@AndroidEntryPoint
class ScreenPrivacyGuard : NotificationListenerService() {

    @Inject
    lateinit var privacyNotifier: PrivacyNotifier

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let {
            if (it.packageName != packageName) {
                val key = "${it.packageName}_${it.id}_${it.tag ?: ""}"
                privacyNotifier.onNotificationPosted(key, it.packageName)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn?.let {
            val key = "${it.packageName}_${it.id}_${it.tag ?: ""}"
            privacyNotifier.onNotificationRemoved(key)
        }
    }
}

@Singleton
class PrivacyNotifier @Inject constructor() {
    private val _shouldBlurScreen = MutableStateFlow(false)
    val shouldBlurScreen: StateFlow<Boolean> = _shouldBlurScreen.asStateFlow()

    private val activeNotificationKeys = ConcurrentHashMap.newKeySet<String>()

    fun onNotificationPosted(notificationKey: String, packageName: String) {
        activeNotificationKeys.add(notificationKey)
        updateBlurState()
        AppLogger.i(LogCategory.FEATURES, "PrivacyGuard notification active from $packageName (Total active: ${activeNotificationKeys.size})")
    }

    fun onNotificationRemoved(notificationKey: String) {
        activeNotificationKeys.remove(notificationKey)
        updateBlurState()
        AppLogger.i(LogCategory.FEATURES, "PrivacyGuard notification removed (Total active: ${activeNotificationKeys.size})")
    }

    private fun updateBlurState() {
        _shouldBlurScreen.value = activeNotificationKeys.isNotEmpty()
    }
}
