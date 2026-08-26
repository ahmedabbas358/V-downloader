package com.junkfood.seal.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.junkfood.seal.App.Companion.packageInfo
import com.junkfood.seal.BuildConfig
import com.junkfood.seal.download.engine.builder.NetworkOptionBuilder
import com.junkfood.seal.util.FileUtil.getCookiesFile
import com.junkfood.seal.util.PreferenceUtil.getBoolean
import com.junkfood.seal.util.PreferenceUtil.getInt
import com.junkfood.seal.util.PreferenceUtil.getLong
import com.junkfood.seal.util.PreferenceUtil.getString
import com.junkfood.seal.util.PreferenceUtil.updateBoolean
import com.junkfood.seal.util.PreferenceUtil.updateLong
import com.junkfood.seal.util.PreferenceUtil.updateString
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object UpgradeManager {

    private const val TAG = "UpgradeManager"
    const val LAST_KNOWN_VERSION = "last_known_version_code"

    suspend fun checkAndRunMigrations(context: Context) = withContext(Dispatchers.IO) {
        val currentVersionCode = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo?.longVersionCode ?: BuildConfig.VERSION_CODE.toLong()
            } else {
                @Suppress("DEPRECATION")
                packageInfo?.versionCode?.toLong() ?: BuildConfig.VERSION_CODE.toLong()
            }
        } catch (_: Exception) {
            BuildConfig.VERSION_CODE.toLong()
        }

        val lastKnownVersionCode = LAST_KNOWN_VERSION.getLong(0L)

        if (lastKnownVersionCode == 0L || currentVersionCode != lastKnownVersionCode) {
            Log.i(TAG, "App updated detected: migrating from versionCode $lastKnownVersionCode to $currentVersionCode")
            runMigrations(context, lastKnownVersionCode, currentVersionCode)
            LAST_KNOWN_VERSION.updateLong(currentVersionCode)
        }
    }

    private suspend fun runMigrations(context: Context, oldVersion: Long, newVersion: Long) {
        Log.i(TAG, "Executing upgrade cleanups and state resets...")

        // 1. Clear temporary cache files, partial downloads, and stale lockfiles
        try {
            val tempDirs = listOfNotNull(
                FileUtil.getExternalTempDir(),
                context.cacheDir,
                context.externalCacheDir,
                context.filesDir,
                context.noBackupFilesDir,
                File(context.noBackupFilesDir, "tmp"),
                File(context.cacheDir, "yt-dlp"),
                File(context.filesDir, "youtubedl-android/cache"),
                File(context.filesDir, ".cache"),
                File(context.noBackupFilesDir, ".cache"),
            )
            for (dir in tempDirs) {
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.forEach { file ->
                        if (file.name.startsWith("sub_temp_") ||
                            file.name.startsWith("sub_direct_") ||
                            file.name.endsWith(".part") ||
                            file.name.endsWith(".ytdl") ||
                            file.name.endsWith(".tmp") ||
                            file.name.endsWith(".aria2")
                        ) {
                            file.deleteRecursively()
                        }
                    }
                }
            }
            FileUtil.clearTempFiles(FileUtil.getExternalTempDir())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean temporary cache directories", e)
        }

        // 2. Clear yt-dlp extractor internal cache to prevent stale player JS / cipher errors
        try {
            val ytDlpCacheDir = File(context.cacheDir, "yt-dlp")
            if (ytDlpCacheDir.exists()) {
                ytDlpCacheDir.deleteRecursively()
            }
            val homeCacheDir = File(context.filesDir, ".cache")
            if (homeCacheDir.exists()) {
                homeCacheDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear yt-dlp cache on upgrade", e)
        }

        // 3. Re-extract yt-dlp and FFmpeg native packages cleanly
        try {
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to re-initialize YoutubeDL/FFmpeg on upgrade", e)
        }

        // 4. Sanitize Storage & SAF Preferences
        try {
            if (SDCARD_DOWNLOAD.getBoolean(false)) {
                val safUri = SDCARD_URI.getString()
                if (!PermissionManager.verifySafPermission(context, safUri)) {
                    Log.w(TAG, "Revoked or invalid SAF URI detected on upgrade. Resetting SD card download setting.")
                    SDCARD_DOWNLOAD.updateBoolean(false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sanitize SAF preferences", e)
        }

        // 5. Sanitize Subtitle Language Preference
        try {
            val currentSubLang = SUBTITLE_LANGUAGE.getString()
            if (currentSubLang.isBlank()) {
                SUBTITLE_LANGUAGE.updateString("ar.*,en.*,.*-orig")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sanitize subtitle language preference", e)
        }

        // 6. Sync & Flush Stored Cookies
        try {
            NetworkOptionBuilder.getCookiesContentFromDatabase().getOrNull()?.let { content ->
                FileUtil.writeContentToFile(content, context.getCookiesFile())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh cookies on upgrade", e)
        }

        // 7. Trigger yt-dlp update check in background
        try {
            UpdateUtil.updateYtDlp()
        } catch (e: Exception) {
            Log.w(TAG, "yt-dlp background update on upgrade skipped or failed: ${e.message}")
        }
    }
}
