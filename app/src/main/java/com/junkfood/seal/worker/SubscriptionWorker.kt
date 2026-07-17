package com.junkfood.seal.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.junkfood.seal.util.DatabaseUtil
import com.junkfood.seal.util.PreferenceUtil
import com.junkfood.seal.util.PreferenceUtil.getBoolean
import io.github.junkfood02.youtubedl.YoutubeDL
import io.github.junkfood02.youtubedl.YoutubeDLRequest
import java.util.Date

class SubscriptionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!PreferenceUtil.ENABLE_EXPERIMENTAL_FEATURES.getBoolean()) {
            return@withContext Result.success()
        }

        return@withContext try {
            val subscriptions = DatabaseUtil.db.subscriptionDao().getAllSubscriptions()
            
            for (sub in subscriptions) {
                // Basically fetching info with dateafter filter based on lastCheckedTimestamp
                val lastChecked = sub.lastCheckedTimestamp
                val dateStr = java.text.SimpleDateFormat("yyyyMMdd").format(Date(lastChecked))

                val request = YoutubeDLRequest(sub.url).apply {
                    addOption("--dateafter", dateStr)
                    addOption("--dump-json")
                    addOption("--playlist-items", "1-5") // Only check the latest 5 to save time
                }

                try {
                    val response = YoutubeDL.getInstance().execute(request, null)
                    // In a real scenario we'd parse response.out and trigger DownloadTask.
                    // For now, we update the timestamp to reflect we checked.
                    val updatedSub = sub.copy(lastCheckedTimestamp = System.currentTimeMillis())
                    DatabaseUtil.db.subscriptionDao().update(updatedSub)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
