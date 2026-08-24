package com.junkfood.seal.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.junkfood.seal.util.PreferenceUtil.getBoolean
import com.junkfood.seal.util.PreferenceUtil.getString

object PermissionManager {

    enum class CapabilityStatus {
        GRANTED,
        DENIED,
        SHOULD_EXPLAIN
    }

    /**
     * Centralized check for notification permissions.
     */
    fun checkNotificationCapability(context: Context): CapabilityStatus {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                CapabilityStatus.GRANTED
            } else {
                CapabilityStatus.DENIED
            }
        } else {
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                CapabilityStatus.GRANTED
            } else {
                CapabilityStatus.DENIED
            }
        }
    }

    /**
     * Checks if the app is currently ignoring battery optimizations.
     */
    fun checkBatteryOptimizationCapability(context: Context): CapabilityStatus {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm?.isIgnoringBatteryOptimizations(context.packageName) == true) {
                CapabilityStatus.GRANTED
            } else {
                CapabilityStatus.DENIED
            }
        } else {
            CapabilityStatus.GRANTED
        }
    }

    /**
     * Returns an intent to open the battery optimization settings for the app.
     */
    fun createBatteryOptimizationIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        return try {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } catch (e: Exception) {
            try {
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            } catch (e: Exception) {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
        }
    }

    /**
     * Centralized way to open App Settings.
     */
    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Centralized check for storage / media permissions.
     */
    fun checkStorageCapability(context: Context): CapabilityStatus {
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                CapabilityStatus.GRANTED
            } else {
                CapabilityStatus.DENIED
            }
        } else {
            if (SDCARD_DOWNLOAD.getBoolean(false)) {
                if (verifySafPermission(context, SDCARD_URI.getString())) {
                    CapabilityStatus.GRANTED
                } else {
                    CapabilityStatus.DENIED
                }
            } else {
                CapabilityStatus.GRANTED
            }
        }
    }

    /**
     * Gets the list of storage permissions needed based on Android SDK level.
     */
    fun getRequiredStoragePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            emptyArray()
        }
    }

    /**
     * Strict check to verify if a persisted Storage Access Framework (SAF) URI is still valid.
     * This guarantees idempotent access and throws NO exceptions if it fails, just returns false safely.
     */
    fun verifySafPermission(context: Context, uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        return try {
            val uri = Uri.parse(uriString)
            val persistedUriPermissions = context.contentResolver.persistedUriPermissions
            persistedUriPermissions.any {
                it.uri == uri &&
                it.isReadPermission &&
                it.isWritePermission
            }
        } catch (e: Exception) {
            false
        }
    }
}
