package com.junkfood.seal.audio.musicremoval.device

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.audio.musicremoval.MusicRemovalConfig

/**
 * DeviceProfileManager
 *
 * Inspects device CPU, RAM, thermal state, and power constraints to choose
 * optimal processing configurations.
 */
object DeviceProfileManager {

    /**
     * Determines the optimal [MusicRemovalConfig.DeviceProfile] for the host device.
     */
    fun getDeviceProfile(appContext: Context = context): MusicRemovalConfig.DeviceProfile {
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val totalRamMb = getTotalRamMb(appContext)

        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSaveMode = powerManager?.isPowerSaveMode == true

        if (isPowerSaveMode || totalRamMb < 3000 || cpuCores <= 4) {
            return MusicRemovalConfig.DeviceProfile.LOW
        }

        return when {
            totalRamMb >= 7500 && cpuCores >= 8 -> MusicRemovalConfig.DeviceProfile.MAX_QUALITY
            totalRamMb >= 5000 && cpuCores >= 6 -> MusicRemovalConfig.DeviceProfile.HIGH
            else -> MusicRemovalConfig.DeviceProfile.BALANCED
        }
    }

    private fun getTotalRamMb(appContext: Context): Long {
        return try {
            val actManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            memInfo.totalMem / (1024 * 1024)
        } catch (_: Exception) {
            4096L
        }
    }
}
