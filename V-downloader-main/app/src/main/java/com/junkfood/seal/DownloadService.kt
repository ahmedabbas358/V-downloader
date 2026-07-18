package com.junkfood.seal

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.junkfood.seal.util.NotificationUtil
import android.net.wifi.WifiManager
import android.os.PowerManager
import com.junkfood.seal.download.DownloaderV2
import com.junkfood.seal.util.PreferenceUtil
import org.koin.android.ext.android.inject

import com.junkfood.seal.util.NotificationUtil.SERVICE_NOTIFICATION_ID

private const val TAG = "DownloadService"
private const val BACKGROUND_LOCK_TIMEOUT_MS = 2 * 60 * 60 * 1000L

/** This `Service` does nothing */
class DownloadService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val downloader: DownloaderV2 by inject()

    override fun onBind(intent: Intent): IBinder {
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            }
        val notification = NotificationUtil.makeServiceNotification(pendingIntent)
        try {
            startForeground(SERVICE_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Failed to start foreground service", e)
        }
        acquireLocks()
        return DownloadServiceBinder()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind: ")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
        stopSelf()
        releaseLocks()
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
            stopForeground(true)
        }
        stopSelf()
        releaseLocks()
    }

    private fun acquireLocks() {
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
            // Use HIGH_PERF instead of LOW_LATENCY as downloading requires throughput, not low latency.
            val wifiMode = WifiManager.WIFI_MODE_FULL_HIGH_PERF
            wifiLock =
                wifiManager
                    .createWifiLock(wifiMode, "VDownloader::DownloadWifiLock")
                    .apply { setReferenceCounted(false) }
        }
        if (wifiLock?.isHeld == false) {
            wifiLock?.acquire()
        }
    }

    private fun releaseLocks() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null

        wifiLock?.let {
            if (it.isHeld) it.release()
        }
        wifiLock = null
    }

    inner class DownloadServiceBinder : Binder() {
        fun getService(): DownloadService = this@DownloadService
    }
}
