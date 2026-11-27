package com.junkfood.seal.util

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.NotificationActionReceiver
import com.junkfood.seal.NotificationActionReceiver.Companion.ACTION_CANCEL_TASK
import com.junkfood.seal.NotificationActionReceiver.Companion.ACTION_ERROR_REPORT
import com.junkfood.seal.NotificationActionReceiver.Companion.ACTION_KEY
import com.junkfood.seal.NotificationActionReceiver.Companion.ACTION_OPEN_FILE
import com.junkfood.seal.NotificationActionReceiver.Companion.ACTION_PAUSE_TASK
import com.junkfood.seal.NotificationActionReceiver.Companion.ACTION_RETRY_TASK
import com.junkfood.seal.NotificationActionReceiver.Companion.ACTION_SHARE_FILE
import com.junkfood.seal.NotificationActionReceiver.Companion.ERROR_REPORT_KEY
import com.junkfood.seal.NotificationActionReceiver.Companion.FILE_PATH_KEY
import com.junkfood.seal.NotificationActionReceiver.Companion.NOTIFICATION_ID_KEY
import com.junkfood.seal.NotificationActionReceiver.Companion.TASK_ID_KEY
import com.junkfood.seal.R
import com.junkfood.seal.util.PreferenceUtil.getBoolean

private const val TAG = "NotificationUtil"

@SuppressLint("StaticFieldLeak")
object NotificationUtil {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private const val PROGRESS_MAX = 100
    private const val PROGRESS_INITIAL = 0
    private const val CHANNEL_ID = "download_notification"
    private const val SERVICE_CHANNEL_ID = "download_service"
    private const val NOTIFICATION_GROUP_ID = "seal.download.notification"
    
    const val SERVICE_NOTIFICATION_ID = 123
    const val DEFAULT_NOTIFICATION_ID = 100
    const val SUMMARY_NOTIFICATION_ID = 101

    private const val COLOR_PROGRESS = 0xFF2E7D32.toInt() // Deep Green/Teal accent
    private const val COLOR_SUCCESS = 0xFF1B5E20.toInt()  // Success Green
    private const val COLOR_ERROR = 0xFFC62828.toInt()    // Error Red

    private lateinit var serviceNotification: Notification

    private val commandNotificationBuilder =
        NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(R.drawable.ic_stat_seal)

    @RequiresApi(Build.VERSION_CODES.O)
    fun createNotificationChannel() {
        val name = context.getString(R.string.channel_name)
        val descriptionText = context.getString(R.string.channel_description)
        val importance = NotificationManager.IMPORTANCE_LOW
        val channelGroup =
            NotificationChannelGroup(NOTIFICATION_GROUP_ID, context.getString(R.string.download))
        val channel =
            NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                group = NOTIFICATION_GROUP_ID
            }
        val serviceChannel =
            NotificationChannel(SERVICE_CHANNEL_ID, name, importance).apply {
                description = context.getString(R.string.service_title)
                group = NOTIFICATION_GROUP_ID
            }
        notificationManager.createNotificationChannelGroup(channelGroup)
        notificationManager.createNotificationChannel(channel)
        notificationManager.createNotificationChannel(serviceChannel)
    }

    private val mainActivityPendingIntent: PendingIntent by lazy {
        val launchIntent = Intent(context, com.junkfood.seal.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun buildActionPendingIntent(
        requestCodeSalt: Int,
        notificationId: Int,
        action: Int,
        taskId: String? = null,
        filePath: String? = null,
        report: String? = null,
    ): PendingIntent {
        val intent = Intent(context.applicationContext, NotificationActionReceiver::class.java).apply {
            putExtra(ACTION_KEY, action)
            putExtra(NOTIFICATION_ID_KEY, notificationId)
            taskId?.let { putExtra(TASK_ID_KEY, it) }
            filePath?.let { putExtra(FILE_PATH_KEY, it) }
            report?.let { putExtra(ERROR_REPORT_KEY, it) }
        }
        val requestCode = (notificationId * 31) + requestCodeSalt
        return PendingIntent.getBroadcast(
            context.applicationContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun notifyProgress(
        title: String,
        notificationId: Int = DEFAULT_NOTIFICATION_ID,
        progress: Int = PROGRESS_INITIAL,
        taskId: String? = null,
        text: String? = null,
    ) {
        if (!NOTIFICATION.getBoolean()) return
        val cancelPendingIntent =
            taskId?.let {
                buildActionPendingIntent(
                    requestCodeSalt = 1,
                    notificationId = notificationId,
                    action = ACTION_CANCEL_TASK,
                    taskId = it,
                )
            }
        val pausePendingIntent =
            taskId?.let {
                buildActionPendingIntent(
                    requestCodeSalt = 2,
                    notificationId = notificationId,
                    action = ACTION_PAUSE_TASK,
                    taskId = it,
                )
            }

        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_seal)
            .setColor(COLOR_PROGRESS)
            .setSubText(context.getString(R.string.app_name))
            .setContentTitle(title)
            .setContentIntent(mainActivityPendingIntent)
            .setProgress(PROGRESS_MAX, progress, progress <= 0)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setGroup("DOWNLOADS_GROUP")
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .run {
                pausePendingIntent?.let {
                    addAction(android.R.drawable.ic_media_pause, context.getString(R.string.pause), it)
                }
                cancelPendingIntent?.let {
                    addAction(R.drawable.outline_cancel_24, context.getString(R.string.cancel), it)
                }
                val notification = build()
                notificationManager.notify(notificationId, notification)
                postGroupSummaryNotification()
            }
    }

    fun notifyPaused(
        title: String,
        notificationId: Int,
        taskId: String,
        statusText: String = context.getString(R.string.notif_paused_text),
    ) {
        if (!NOTIFICATION.getBoolean()) return
        val resumePendingIntent =
            buildActionPendingIntent(
                requestCodeSalt = 3,
                notificationId = notificationId,
                action = ACTION_RETRY_TASK,
                taskId = taskId,
            )
        val cancelPendingIntent =
            buildActionPendingIntent(
                requestCodeSalt = 1,
                notificationId = notificationId,
                action = ACTION_CANCEL_TASK,
                taskId = taskId,
            )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_seal)
            .setColor(COLOR_PROGRESS)
            .setSubText(context.getString(R.string.app_name))
            .setContentTitle(title)
            .setContentText(statusText)
            .setContentIntent(mainActivityPendingIntent)
            .setOngoing(false)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setGroup("DOWNLOADS_GROUP")
            .addAction(R.drawable.outline_restart_alt_24, context.getString(R.string.notif_action_resume), resumePendingIntent)
            .addAction(R.drawable.outline_cancel_24, context.getString(R.string.cancel), cancelPendingIntent)
            .build()
        notificationManager.notify(notificationId, notification)
    }

    fun notifyWaitingForWifi() {
        if (!NOTIFICATION.getBoolean()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_seal)
            .setColor(COLOR_PROGRESS)
            .setContentTitle(context.getString(R.string.notif_waiting_wifi))
            .setContentText(context.getString(R.string.notif_waiting_wifi_desc))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notif_waiting_wifi_desc)))
            .setContentIntent(mainActivityPendingIntent)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setGroup("DOWNLOADS_GROUP")
            .build()
        notificationManager.notify(SUMMARY_NOTIFICATION_ID + 1, notification)
    }

    fun finishNotification(
        notificationId: Int = DEFAULT_NOTIFICATION_ID,
        title: String? = null,
        text: String? = null,
        filePath: String? = null,
        intent: PendingIntent? = null,
    ) {
        Log.d(TAG, "finishNotification id: $notificationId, file: $filePath")
        notificationManager.cancel(notificationId)
        if (!NOTIFICATION.getBoolean()) return

        val builder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_seal)
                .setColor(COLOR_SUCCESS)
                .setSubText(context.getString(R.string.app_name))
                .setContentText(text ?: context.getString(R.string.notif_download_success))
                .setContentIntent(intent ?: mainActivityPendingIntent)
                .setOngoing(false)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        title?.let { builder.setContentTitle(it) }
        builder.setGroup("DOWNLOADS_GROUP")

        if (!filePath.isNullOrBlank()) {
            val openPendingIntent = buildActionPendingIntent(
                requestCodeSalt = 4,
                notificationId = notificationId,
                action = ACTION_OPEN_FILE,
                filePath = filePath,
            )
            val sharePendingIntent = buildActionPendingIntent(
                requestCodeSalt = 5,
                notificationId = notificationId,
                action = ACTION_SHARE_FILE,
                filePath = filePath,
            )

            builder.addAction(R.drawable.outline_open_in_new_24, context.getString(R.string.notif_action_open), openPendingIntent)
            builder.addAction(R.drawable.outline_share_24, context.getString(R.string.notif_action_share), sharePendingIntent)
        }

        notificationManager.notify(notificationId, builder.build())
        postGroupSummaryNotification()
    }

    private fun postGroupSummaryNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val summaryNotification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.app_name))
                .setSmallIcon(R.drawable.ic_stat_seal)
                .setColor(COLOR_PROGRESS)
                .setGroup("DOWNLOADS_GROUP")
                .setGroupSummary(true)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(SUMMARY_NOTIFICATION_ID, summaryNotification)
        }
    }

    fun finishNotificationForCustomCommands(
        notificationId: Int = DEFAULT_NOTIFICATION_ID,
        title: String? = null,
        text: String? = null,
    ) {
        val builder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_seal)
                .setColor(COLOR_SUCCESS)
                .setContentText(text)
                .setProgress(0, 0, false)
                .setAutoCancel(true)
                .setOngoing(false)
                .setStyle(null)
        title?.let { builder.setContentTitle(title) }

        notificationManager.notify(notificationId, builder.build())
    }

    fun notifyPlaylistCompletion(
        id: Int = DEFAULT_NOTIFICATION_ID,
        title: String,
        text: String,
        intent: PendingIntent? = null,
    ) {
        if (!NOTIFICATION.getBoolean()) return
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_seal)
            .setColor(COLOR_PROGRESS)
            .setSubText(context.getString(R.string.app_name))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(intent ?: mainActivityPendingIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        notificationManager.notify(id, builder.build())
    }

    fun makeServiceNotification(intent: PendingIntent, text: String? = null): Notification {
        val exitIntent = Intent(context, NotificationActionReceiver::class.java)
            .putExtra(NotificationActionReceiver.ACTION_KEY, NotificationActionReceiver.ACTION_EXIT_APP)
            .putExtra(NotificationActionReceiver.NOTIFICATION_ID_KEY, SERVICE_NOTIFICATION_ID)

        val exitPendingIntent = PendingIntent.getBroadcast(
            context,
            1001,
            exitIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        serviceNotification =
            NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_seal)
                .setColor(COLOR_PROGRESS)
                .setContentTitle(context.getString(R.string.service_title))
                .setContentText(text)
                .setOngoing(true)
                .setContentIntent(intent)
                .addAction(R.drawable.outline_cancel_24, context.getString(R.string.exit), exitPendingIntent)
                .setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        return serviceNotification
    }

    fun makeMinimalServiceNotification(ctx: Context = context): Notification {
        return NotificationCompat.Builder(ctx, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_seal)
            .setContentTitle(ctx.getString(R.string.service_title))
            .setOngoing(true)
            .setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun updateServiceNotificationForPlaylist(index: Int, itemCount: Int) {
        serviceNotification =
            NotificationCompat.Builder(context, serviceNotification)
                .setContentTitle(context.getString(R.string.service_title) + " ($index/$itemCount)")
                .build()
        notificationManager.notify(SERVICE_NOTIFICATION_ID, serviceNotification)
    }

    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    fun notifyError(
        title: String,
        textId: Int = R.string.download_error_professional,
        notificationId: Int,
        report: String,
        taskId: String? = null,
    ) {
        if (!NOTIFICATION.getBoolean()) return

        val reportPendingIntent = buildActionPendingIntent(
            requestCodeSalt = 6,
            notificationId = notificationId,
            action = ACTION_ERROR_REPORT,
            report = report,
        )

        val appIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val appPendingIntent = appIntent?.let {
            PendingIntent.getActivity(
                context,
                (notificationId * 31) + 7,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_seal)
            .setColor(COLOR_ERROR)
            .setSubText(context.getString(R.string.app_name))
            .setContentTitle(title)
            .setContentText(context.getString(textId))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(textId)))
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(appPendingIntent)
            .setGroup("DOWNLOADS_GROUP")

        if (!taskId.isNullOrBlank()) {
            val retryPendingIntent = buildActionPendingIntent(
                requestCodeSalt = 3,
                notificationId = notificationId,
                action = ACTION_RETRY_TASK,
                taskId = taskId,
            )
            builder.addAction(
                R.drawable.outline_restart_alt_24,
                context.getString(R.string.notif_action_retry),
                retryPendingIntent
            )
        }

        builder.addAction(
            R.drawable.outline_content_copy_24,
            context.getString(R.string.notif_action_report),
            reportPendingIntent,
        )

        notificationManager.cancel(notificationId)
        notificationManager.notify(notificationId, builder.build())
    }

    fun makeNotificationForCustomCommand(
        notificationId: Int,
        taskId: String,
        progress: Int,
        text: String? = null,
        templateName: String,
        taskUrl: String,
    ) {
        if (!NOTIFICATION.getBoolean()) return

        val pendingIntent = buildActionPendingIntent(
            requestCodeSalt = 1,
            notificationId = notificationId,
            action = ACTION_CANCEL_TASK,
            taskId = taskId,
        )

        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_seal)
            .setColor(COLOR_PROGRESS)
            .setContentTitle(
                "[${templateName}_${taskUrl}] " +
                    context.getString(R.string.execute_command_notification)
            )
            .setContentText(text)
            .setOngoing(true)
            .setProgress(PROGRESS_MAX, progress, progress == -1)
            .addAction(
                R.drawable.outline_cancel_24,
                context.getString(R.string.cancel),
                pendingIntent,
            )
            .run { notificationManager.notify(notificationId, build()) }
    }

    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }

    fun areNotificationsEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT <= 24) true
        else notificationManager.areNotificationsEnabled()
    }
}
