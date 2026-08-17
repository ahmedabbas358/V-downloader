package com.junkfood.seal

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.junkfood.seal.download.DownloaderV2
import com.junkfood.seal.util.NotificationUtil
import com.junkfood.seal.util.NotificationUtil.SERVICE_NOTIFICATION_ID
import com.junkfood.seal.util.PreferenceUtil
import org.koin.android.ext.android.inject

private const val TAG = "DownloadService"
private const val BACKGROUND_LOCK_TIMEOUT_MS = 2 * 60 * 60 * 1000L

class DownloadService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val downloader: DownloaderV2 by inject()

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        acquireLocks()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder {
        startForegroundNotification()
        acquireLocks()
        return DownloadServiceBinder()
    }

    /**
     * Calls startForeground() immediately — this MUST happen within 5 seconds of
     * startForegroundService() on Android 8+ (API 26+) to avoid ANR/crash.
     * On Android 10+ (API 29) we supply the foreground service type for clarity.
     */
    private fun startForegroundNotification() {
        try {
            val pendingIntent: PendingIntent =
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }.let { notificationIntent ->
                    PendingIntent.getActivity(
                        this,
                        0,
                        notificationIntent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                }
            val notification = NotificationUtil.makeServiceNotification(pendingIntent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    SERVICE_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(SERVICE_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground notification: ${e.message}", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    NotificationUtil.createNotificationChannel()
                }
                val fallback = NotificationUtil.makeMinimalServiceNotification(this)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        SERVICE_NOTIFICATION_ID,
                        fallback,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                    )
                } else {
                    startForeground(SERVICE_NOTIFICATION_ID, fallback)
                }
            } catch (inner: Exception) {
                Log.e(TAG, "Failed to start fallback foreground notification: ${inner.message}", inner)
            }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind")
        // Do NOT stop the service here — stopping immediately after unbind can trigger
        // ForegroundServiceDidNotStartInTimeException on the next startForegroundService() call
        // because the OS may recycle this service instance before the new one fully starts.
        // The DownloadQueueManager.hasActiveTasks() observer will call stopService() when
        // the queue is actually empty.
        App.isServiceRunning = false
        App.isStartingService = false
        return super.onUnbind(intent)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "onTaskRemoved: ")
        // Synchronously save the task list before the service is killed
        PreferenceUtil.encodeTaskListBackup(downloader.getTaskStateMap())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
        releaseLocks()
    }

    override fun onDestroy() {
        super.onDestroy()
        App.isServiceRunning = false
        App.isStartingService = false
        releaseLocks()
    }

    private fun acquireLocks() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(POWER_SERVICE) as PowerManager
                wakeLock =
                    powerManager
                        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VDownloader::DownloadWakeLock")
                        .apply { setReferenceCounted(false) }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(BACKGROUND_LOCK_TIMEOUT_MS)
            }

            if (wifiLock == null) {
                val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                val wifiMode = WifiManager.WIFI_MODE_FULL_HIGH_PERF
                wifiLock =
                    wifiManager
                        .createWifiLock(wifiMode, "VDownloader::DownloadWifiLock")
                        .apply { setReferenceCounted(false) }
            }
            if (wifiLock?.isHeld == false) {
                wifiLock?.acquire()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake/wifi locks: ${e.message}", e)
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wakeLock = null

            wifiLock?.let {
                if (it.isHeld) it.release()
            }
            wifiLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release locks: ${e.message}", e)
        }
    }

    inner class DownloadServiceBinder : Binder() {
        fun getService(): DownloadService = this@DownloadService
    }
}
