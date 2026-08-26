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
        val areNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!areNotificationsEnabled) {
            return CapabilityStatus.DENIED
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isRuntimeGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!isRuntimeGranted) {
                return CapabilityStatus.DENIED
            }
        }
        val isPrefEnabled = NOTIFICATION.getBoolean(true)
        if (!isPrefEnabled) {
            return CapabilityStatus.DENIED
        }
        return CapabilityStatus.GRANTED
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
     * Centralized way to open Notification Settings directly.
     */
    fun openNotificationSettings(context: Context) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            } else {
                Intent("android.settings.APP_NOTIFICATION_SETTINGS").apply {
                    putExtra("app_package", context.packageName)
                    putExtra("app_uid", context.applicationInfo.uid)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openAppSettings(context)
        }
    }

    /**
     * Centralized way to open Battery Optimization Settings directly.
     */
    fun openBatteryOptimizationSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                if (pm != null && !pm.isIgnoringBatteryOptimizations(context.packageName)) {
                    val reqIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(reqIntent)
                    return
                }
            } catch (_: Exception) {}

            try {
                val listIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(listIntent)
                return
            } catch (_: Exception) {}
        }
        openAppSettings(context)
    }

    /**
     * Centralized way to open Storage Permission Settings directly for the app.
     */
    fun openStoragePermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return
            } catch (_: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    return
                } catch (_: Exception) {}
            }
        }
        openAppSettings(context)
    }

    /**
     * Centralized way to open App Settings.
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Centralized check for storage / media permissions with accurate system verification.
     */
    fun checkStorageCapability(context: Context): CapabilityStatus {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Check All Files Access
            if (android.os.Environment.isExternalStorageManager()) {
                return CapabilityStatus.GRANTED
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Check Media Permissions on Android 13+
                val hasVideo = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (hasVideo && hasAudio) {
                    return CapabilityStatus.GRANTED
                }
            } else {
                // Check Read Storage on Android 11-12
                val hasRead = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                if (hasRead) {
                    return CapabilityStatus.GRANTED
                }
            }

            // Check SAF permission if user configured custom SD card
            if (SDCARD_DOWNLOAD.getBoolean(false)) {
                if (verifySafPermission(context, SDCARD_URI.getString())) {
                    return CapabilityStatus.GRANTED
                }
            }

            return CapabilityStatus.DENIED
        } else {
            // Android <= 10 (API <= 29)
            val hasWrite = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            return if (hasWrite) CapabilityStatus.GRANTED else CapabilityStatus.DENIED
        }
    }

    /**
     * Gets the list of storage permissions needed based on Android SDK level.
     */
    fun getRequiredStoragePermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_IMAGES,
                )
            }
            Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q -> {
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                )
            }
            else -> {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
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
