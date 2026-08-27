package com.streamapp.core.features.performance

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class DevicePerformance(
    val ramUsagePercent: Float,
    val batteryPercent: Float,
    val cpuUsagePercent: Float
)

@Singleton
class DevicePerformanceMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    fun monitorPerformance(): Flow<DevicePerformance> = flow {
        while (true) {
            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                context.registerReceiver(null, ifilter)
            }
            val batteryPct: Float? = batteryStatus?.let { intent ->
                val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                level * 100 / scale.toFloat()
            }

            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            val usedRam = memoryInfo.totalMem - memoryInfo.availMem
            val ramUsagePercent = (usedRam.toFloat() / memoryInfo.totalMem.toFloat()) * 100

            // Dummy CPU usage since it's restricted in newer Android versions
            val dummyCpuUsage = (Math.random() * 100).toFloat()

            emit(DevicePerformance(
                ramUsagePercent = ramUsagePercent,
                batteryPercent = batteryPct ?: 0f,
                cpuUsagePercent = dummyCpuUsage
            ))

            delay(2000)
        }
    }
}
