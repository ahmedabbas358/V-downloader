package com.junkfood.seal

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.util.Log
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.download.DownloaderV2
import com.junkfood.seal.util.FileUtil
import com.junkfood.seal.util.NotificationUtil
import com.junkfood.seal.util.ToastUtil
import com.yausername.youtubedl_android.YoutubeDL
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class NotificationActionReceiver : BroadcastReceiver(), KoinComponent {
    val downloader = get<DownloaderV2>()

    companion object {
        private const val TAG = "NotificationActionReceiver"
        private const val PACKAGE_NAME_PREFIX = "com.vdownloader.app."

        const val ACTION_CANCEL_TASK = 0
        const val ACTION_ERROR_REPORT = 1
        const val ACTION_EXIT_APP = 2
        const val ACTION_RETRY_TASK = 3
        const val ACTION_OPEN_FILE = 4
        const val ACTION_SHARE_FILE = 5

        const val ACTION_KEY = PACKAGE_NAME_PREFIX + "action"
        const val TASK_ID_KEY = PACKAGE_NAME_PREFIX + "taskId"
        const val FILE_PATH_KEY = PACKAGE_NAME_PREFIX + "filePath"
        const val NOTIFICATION_ID_KEY = PACKAGE_NAME_PREFIX + "notificationId"
        const val ERROR_REPORT_KEY = PACKAGE_NAME_PREFIX + "error_report"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) return
        val notificationId = intent.getIntExtra(NOTIFICATION_ID_KEY, 0)
        val action = intent.getIntExtra(ACTION_KEY, ACTION_CANCEL_TASK)
        Log.d(TAG, "onReceive action: $action, notificationId: $notificationId")

        when (action) {
            ACTION_CANCEL_TASK -> {
                val taskId = intent.getStringExtra(TASK_ID_KEY)
                cancelTask(taskId, notificationId)
            }

            ACTION_ERROR_REPORT -> {
                val errorReport = intent.getStringExtra(ERROR_REPORT_KEY)
                if (!errorReport.isNullOrEmpty()) copyErrorReport(errorReport, notificationId)
            }

            ACTION_EXIT_APP -> {
                App.stopService()
                NotificationUtil.cancelNotification(notificationId)
                System.exit(0)
            }

            ACTION_RETRY_TASK -> {
                val taskId = intent.getStringExtra(TASK_ID_KEY)
                if (!taskId.isNullOrEmpty()) {
                    val task = downloader.getTaskStateMap().keys.find { it.id == taskId }
                    if (task != null) {
                        downloader.restart(task)
                        NotificationUtil.cancelNotification(notificationId)
                        ToastUtil.makeToastSuspend(context?.getString(R.string.resume) ?: "Retrying…")
                    }
                }
            }

            ACTION_OPEN_FILE -> {
                val path = intent.getStringExtra(FILE_PATH_KEY)
                if (!path.isNullOrEmpty()) {
                    FileUtil.openFile(path) {
                        context?.let { ctx -> ToastUtil.makeToastSuspend(ctx.getString(R.string.file_unavailable)) }
                    }
                }
            }

            ACTION_SHARE_FILE -> {
                val path = intent.getStringExtra(FILE_PATH_KEY)
                if (!path.isNullOrEmpty()) {
                    FileUtil.createIntentForSharingFile(path)?.let { shareIntent ->
                        val chooser = Intent.createChooser(shareIntent, context?.getString(R.string.share)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context?.startActivity(chooser)
                    }
                }
            }
        }
    }

    private fun cancelTask(taskId: String?, notificationId: Int) {
        if (taskId.isNullOrEmpty()) return
        NotificationUtil.cancelNotification(notificationId)
        val res = downloader.cancel(taskId)
        if (res) {
            Log.d(TAG, "Task (id:$taskId) was canceled.")
        } else {
            YoutubeDL.destroyProcessById(taskId)
        }
    }

    private fun copyErrorReport(error: String, notificationId: Int) {
        App.clipboard.setPrimaryClip(ClipData.newPlainText(null, error))
        context.let { ToastUtil.makeToastSuspend(it.getString(R.string.error_copied)) }
        NotificationUtil.cancelNotification(notificationId)
    }
}
