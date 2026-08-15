package com.junkfood.seal.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.junkfood.seal.App
import com.junkfood.seal.MainActivity
import com.junkfood.seal.download.PlaylistVerifier
import com.junkfood.seal.util.DownloadUtil
import com.junkfood.seal.util.NotificationUtil
import com.junkfood.seal.util.PreferenceUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlaylistSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "PlaylistSyncWorker"
        const val WORK_NAME = "playlist_auto_sync_work"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val savedLinks = PreferenceUtil.getSavedLinks()
        val playlistLinks = savedLinks.filter { it.contains("list=", ignoreCase = true) }

        if (playlistLinks.isEmpty()) {
            return@withContext Result.success()
        }

        val defaultPrefs = DownloadUtil.DownloadPreferences.createFromPreferences()
        var totalMissingFound = 0

        playlistLinks.forEach { playlistUrl ->
            try {
                val scanResult = PlaylistVerifier.scanPlaylist(
                    playlistUrl = playlistUrl,
                    preferences = defaultPrefs
                ).getOrNull()

                if (scanResult != null && scanResult.missingItems.isNotEmpty()) {
                    totalMissingFound += scanResult.missingItems.size
                    Log.d(TAG, "Found ${scanResult.missingItems.size} missing items in '${scanResult.playlistTitle}'")

                    val intent = Intent(applicationContext, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        applicationContext,
                        playlistUrl.hashCode(),
                        intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )

                    NotificationUtil.notifyPlaylistCompletion(
                        id = playlistUrl.hashCode(),
                        title = "تحديث في قائمة: ${scanResult.playlistTitle}",
                        text = "يوجد ${scanResult.missingItems.size} عناصر جديدة متاحة للتنزيل",
                        intent = pendingIntent
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing playlist: $playlistUrl", e)
            }
        }

        Result.success()
    }
}
