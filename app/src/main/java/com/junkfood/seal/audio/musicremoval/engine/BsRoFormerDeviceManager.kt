package com.junkfood.seal.audio.musicremoval.engine

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * BsRoFormerDeviceManager
 *
 * Discovers device hardware capabilities, configures optimal ONNX execution providers
 * (NNAPI for NPU/GPU hardware acceleration where supported, and tuned multi-core CPU),
 * handles thread allocation, and manages memory and OOM recovery.
 */
object BsRoFormerDeviceManager {

    private const val TAG = "BsRoFormerDeviceManager"

    enum class ExecutionDevice {
        GPU_NNAPI,
        CPU_MULTICORE,
        CPU_LOW_POWER
    }

    data class DeviceProfile(
        val device: ExecutionDevice,
        val recommendedThreads: Int,
        val hasNnapiSupport: Boolean,
        val availableRamMb: Long,
        val isLowMemoryDevice: Boolean
    )

    /**
     * Determines the optimal device profile for BS-RoFormer execution on this device.
     */
    fun getOptimalProfile(context: Context? = null): DeviceProfile {
        val cores = Runtime.getRuntime().availableProcessors()
        val maxMemoryMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        val isLowMem = maxMemoryMb < 256

        val hasNnapi = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && !isLowMem

        val device = when {
            hasNnapi -> ExecutionDevice.GPU_NNAPI
            cores >= 4 -> ExecutionDevice.CPU_MULTICORE
            else -> ExecutionDevice.CPU_LOW_POWER
        }

        val threads = when (device) {
            ExecutionDevice.GPU_NNAPI -> 2
            ExecutionDevice.CPU_MULTICORE -> cores.coerceIn(2, 4)
            ExecutionDevice.CPU_LOW_POWER -> 1
        }

        return DeviceProfile(
            device = device,
            recommendedThreads = threads,
            hasNnapiSupport = hasNnapi,
            availableRamMb = maxMemoryMb,
            isLowMemoryDevice = isLowMem
        )
    }

    /**
     * Configures ONNX Session Options based on optimal device profile.
     */
    fun createSessionOptions(
        env: OrtEnvironment,
        profile: DeviceProfile = getOptimalProfile()
    ): OrtSession.SessionOptions {
        val options = OrtSession.SessionOptions()
        options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        options.setIntraOpNumThreads(profile.recommendedThreads)

        if (profile.hasNnapiSupport) {
            try {
                options.addNnapi()
                Log.d(TAG, "NNAPI acceleration enabled for BS-RoFormer inference.")
            } catch (e: Throwable) {
                Log.w(TAG, "NNAPI provider unavailable; falling back to high-performance CPU runtime: ${e.message}")
            }
        }

        return options
    }

    /**
     * Attempts to reclaim memory when approaching resource limits or after an OOM event.
     */
    fun reclaimMemory() {
        Log.i(TAG, "Reclaiming memory for BS-RoFormer engine...")
        System.gc()
    }
}
