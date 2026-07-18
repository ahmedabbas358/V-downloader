package com.junkfood.seal.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.junkfood.seal.util.PreferenceUtil
import com.junkfood.seal.util.UpdateUtil
import com.junkfood.seal.util.ENABLE_EXPERIMENTAL_FEATURES
import com.junkfood.seal.util.PreferenceUtil.getBoolean
import com.yausername.youtubedl_android.YoutubeDL

class YtDlpUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // Only run if the experimental features flag and auto-update flag are enabled
        val isExperimentalEnabled = ENABLE_EXPERIMENTAL_FEATURES.getBoolean()
        val isAutoUpdateEnabled = PreferenceUtil.isAutoUpdateEnabled()

        if (!isExperimentalEnabled || !isAutoUpdateEnabled) {
            return@withContext Result.success()
        }

        return@withContext try {
            val status = UpdateUtil.updateYtDlp()
            if (status == YoutubeDL.UpdateStatus.DONE ||
                status == YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
