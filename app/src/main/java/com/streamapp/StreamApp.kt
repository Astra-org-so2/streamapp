package com.streamapp

import android.app.Application
import com.streamapp.core.common.logger.AppLogger
import com.streamapp.core.common.logger.LogCategory
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class StreamApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppLogger.setDebugEnabled(BuildConfig.DEBUG)
        AppLogger.i(LogCategory.UI, "StreamApp initialized in ${if (BuildConfig.DEBUG) "DEBUG" else "RELEASE"} mode")
    }
}
