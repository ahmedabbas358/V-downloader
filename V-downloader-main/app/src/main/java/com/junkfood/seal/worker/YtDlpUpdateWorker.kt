package com.junkfood.seal.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.junkfood.seal.util.PreferenceUtil
import com.junkfood.seal.util.UpdateUtil
import com.yausername.youtubedl_android.YoutubeDL

class YtDlpUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val isAutoUpdateEnabled = PreferenceUtil.isAutoUpdateEnabled()

        if (!isAutoUpdateEnabled) {
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
