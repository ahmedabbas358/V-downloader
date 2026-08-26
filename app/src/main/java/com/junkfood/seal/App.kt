package com.junkfood.seal

import android.annotation.SuppressLint
import android.app.Application
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.getSystemService
import com.google.android.material.color.DynamicColors
import com.junkfood.seal.download.DownloaderV2
import com.junkfood.seal.download.DownloaderV2Impl
import com.junkfood.seal.ui.page.download.HomePageViewModel
import com.junkfood.seal.ui.page.downloadv2.configure.DownloadDialogViewModel
import com.junkfood.seal.ui.page.settings.directory.Directory
import com.junkfood.seal.ui.page.settings.network.CookiesViewModel
import com.junkfood.seal.ui.page.videolist.VideoListViewModel
import com.junkfood.seal.util.AUDIO_DIRECTORY
import com.junkfood.seal.util.COMMAND_DIRECTORY
import com.junkfood.seal.util.DownloadUtil
import com.junkfood.seal.util.FileUtil
import com.junkfood.seal.util.FileUtil.createEmptyFile
import com.junkfood.seal.util.FileUtil.getCookiesFile
import com.junkfood.seal.util.FileUtil.getExternalDownloadDirectory
import com.junkfood.seal.util.FileUtil.getExternalPrivateDownloadDirectory
import com.junkfood.seal.util.NotificationUtil
import com.junkfood.seal.util.PreferenceUtil
import com.junkfood.seal.util.PreferenceUtil.getString
import com.junkfood.seal.util.PreferenceUtil.updateString
import com.junkfood.seal.util.SDCARD_URI
import com.junkfood.seal.util.UpdateUtil
import com.junkfood.seal.util.UpgradeManager
import com.junkfood.seal.util.VIDEO_DIRECTORY
import com.junkfood.seal.util.YT_DLP_VERSION
import com.junkfood.seal.util.YT_DLP_UPDATE_TIME
import com.junkfood.seal.util.PreferenceUtil.getLong
import com.junkfood.seal.worker.YtDlpUpdateWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.tencent.mmkv.MMKV
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        MMKV.initialize(this)

        startKoin {
            androidLogger()
            androidContext(this@App)
            modules(
                module {
                    single<DownloaderV2> { DownloaderV2Impl(androidContext()) }
                    viewModel { DownloadDialogViewModel(downloader = get()) }
                    viewModel { HomePageViewModel(downloaderV2 = get()) }
                    viewModel { CookiesViewModel() }
                    viewModel { VideoListViewModel() }
                }
            )
        }

        context = applicationContext
        packageInfo =
            packageManager.run {
                if (Build.VERSION.SDK_INT >= 33)
                    getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                else getPackageInfo(packageName, 0)
            }
        applicationScope = CoroutineScope(SupervisorJob())
        DynamicColors.applyToActivitiesIfAvailable(this)

        clipboard = getSystemService()!!
        connectivityManager = getSystemService()!!

        applicationScope.launch((Dispatchers.IO)) {
            try {
                UpgradeManager.ensureNativeEnvironment(this@App)
                UpgradeManager.checkAndRunMigrations(this@App)
                com.junkfood.seal.util.DatabaseUtil.initDatabase()
                DownloadUtil.getCookiesContentFromDatabase().getOrNull()?.let {
                    FileUtil.writeContentToFile(it, getCookiesFile())
                }
                UpdateUtil.deleteOutdatedApk()

                // Keep yt-dlp binary up-to-date with latest Instagram/TikTok/YouTube extractor fixes
                runCatching {
                    val lastUpdateTime = YT_DLP_UPDATE_TIME.getLong(0L)
                    val twelveHours = 12 * 3600 * 1000L
                    if (System.currentTimeMillis() - lastUpdateTime > twelveHours) {
                        UpdateUtil.updateYtDlp()
                    }
                }
            } catch (th: Throwable) {
                withContext(Dispatchers.Main) { startCrashReportActivity(th) }
            }
        }

        videoDownloadDir = VIDEO_DIRECTORY.getString(getExternalDownloadDirectory().absolutePath)

        audioDownloadDir = AUDIO_DIRECTORY.getString(File(videoDownloadDir, "Audio").absolutePath)
        if (!PreferenceUtil.containsKey(COMMAND_DIRECTORY)) {
            COMMAND_DIRECTORY.updateString(videoDownloadDir)
        }
        if (Build.VERSION.SDK_INT >= 26) NotificationUtil.createNotificationChannel()

        Thread.setDefaultUncaughtExceptionHandler { _, e -> startCrashReportActivity(e) }
        
        setupWorkManager()
    }

    private fun setupWorkManager() {
        val updateRequest = PeriodicWorkRequestBuilder<YtDlpUpdateWorker>(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "YtDlpUpdateWork",
            ExistingPeriodicWorkPolicy.KEEP,
            updateRequest
        )
    }

    private fun startCrashReportActivity(th: Throwable) {
        th.printStackTrace()
        startActivity(
            Intent(this, CrashReportActivity::class.java)
                .setAction("$packageName.error_report")
                .apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("error_report", getVersionReport() + "\n" + th.stackTraceToString())
                }
        )
    }

    companion object {
        lateinit var clipboard: ClipboardManager
        lateinit var videoDownloadDir: String
        lateinit var audioDownloadDir: String
        lateinit var applicationScope: CoroutineScope
        lateinit var connectivityManager: ConnectivityManager
        lateinit var packageInfo: PackageInfo

        // Thread-safe service state tracking.
        // isServiceRunning is set to true only after onServiceConnected fires.
        @Volatile var isServiceRunning = false

        // Prevents duplicate concurrent calls to startForegroundService.
        @Volatile internal var isStartingService = false

        private val mainHandler = Handler(Looper.getMainLooper())

        private val connection =
            object : ServiceConnection {
                override fun onServiceConnected(className: ComponentName, service: IBinder) {
                    isServiceRunning = true
                    isStartingService = false
                    Log.d("App", "DownloadService connected.")
                }

                override fun onServiceDisconnected(arg0: ComponentName) {
                    // System killed the service unexpectedly; allow restart on next demand.
                    isServiceRunning = false
                    isStartingService = false
                    Log.w("App", "DownloadService disconnected unexpectedly.")
                }
            }

        /**
         * Starts the foreground download service.
         *
         * IMPORTANT: startForegroundService() MUST be called from the main thread on
         * Android 8+ (API 26+), and Android 16 (API 36) enforces this even more strictly.
         * Calling it from a background coroutine/thread causes
         * ForegroundServiceDidNotStartInTimeException.
         *
         * This method is safe to call from any thread — it posts to the main looper.
         */
        fun startService() {
            if (isServiceRunning || isStartingService) return
            isStartingService = true
            // Always dispatch to the main thread to satisfy the OS 5-second foreground contract.
            mainHandler.post {
                if (isServiceRunning) {
                    isStartingService = false
                    return@post
                }
                val intent = Intent(context.applicationContext, DownloadService::class.java)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.applicationContext.startForegroundService(intent)
                    } else {
                        context.applicationContext.startService(intent)
                    }
                } catch (e: Exception) {
                    isStartingService = false
                    Log.e("App", "Failed to startForegroundService: ${e.message}", e)
                    return@post
                }
                try {
                    context.applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                } catch (e: Exception) {
                    isStartingService = false
                    Log.e("App", "Failed to bindService: ${e.message}", e)
                }
            }
        }

        fun stopService() {
            if (!isServiceRunning && !isStartingService) return
            mainHandler.post {
                try {
                    isServiceRunning = false
                    isStartingService = false
                    context.applicationContext.unbindService(connection)
                } catch (e: Exception) {
                    Log.e("App", "stopService error: ${e.message}", e)
                }
            }
        }

        val privateDownloadDir: String
            get() =
                getExternalPrivateDownloadDirectory().run {
                    createEmptyFile(".nomedia")
                    absolutePath
                }

        fun updateDownloadDir(uri: Uri, directoryType: Directory) {
            when (directoryType) {
                Directory.AUDIO -> {
                    val path = FileUtil.getRealPath(uri)
                    audioDownloadDir = path
                    PreferenceUtil.encodeString(AUDIO_DIRECTORY, path)
                }

                Directory.VIDEO -> {
                    val path = FileUtil.getRealPath(uri)
                    videoDownloadDir = path
                    PreferenceUtil.encodeString(VIDEO_DIRECTORY, path)
                }

                Directory.CUSTOM_COMMAND -> {
                    val path = FileUtil.getRealPath(uri)
                }

                Directory.SDCARD -> {
                    context.contentResolver?.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                    PreferenceUtil.encodeString(SDCARD_URI, uri.toString())
                }
            }
        }

        fun getVersionReport(): String {
            val versionName = packageInfo.versionName
            val page = packageInfo
            val versionCode =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    packageInfo.versionCode.toLong()
                }
            val release =
                if (Build.VERSION.SDK_INT >= 30) {
                    Build.VERSION.RELEASE_OR_CODENAME
                } else {
                    Build.VERSION.RELEASE
                }
            return StringBuilder()
                .append("App version: $versionName ($versionCode)\n")
                .append("Device information: Android $release (API ${Build.VERSION.SDK_INT})\n")
                .append("Supported ABIs: ${Build.SUPPORTED_ABIS.contentToString()}\n")
                .append("Yt-dlp version: ${YT_DLP_VERSION.getString()}\n")
                .toString()
        }

        fun isFDroidBuild(): Boolean = BuildConfig.FLAVOR == "fdroid"

        fun isDebugBuild(): Boolean = BuildConfig.DEBUG

        @SuppressLint("StaticFieldLeak") lateinit var context: Context
    }
}
