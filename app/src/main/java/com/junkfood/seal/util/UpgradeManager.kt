package com.junkfood.seal.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.junkfood.seal.App.Companion.packageInfo
import com.junkfood.seal.BuildConfig
import com.junkfood.seal.download.engine.builder.NetworkOptionBuilder
import com.junkfood.seal.util.FileUtil.getCookiesFile
import com.junkfood.seal.util.PreferenceUtil.getBoolean
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

    // Temp/lock/partial-download file extensions to delete on upgrade
    private val STALE_EXTENSIONS = setOf(".part", ".ytdl", ".tmp", ".aria2", ".cache")
    // Temp file prefixes created by our subtitle engine
    private val STALE_PREFIXES = setOf("sub_temp_", "sub_direct_")

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
            Log.i(TAG, "App update detected: migrating from versionCode $lastKnownVersionCode to $currentVersionCode")
            runMigrations(context, lastKnownVersionCode, currentVersionCode)
            LAST_KNOWN_VERSION.updateLong(currentVersionCode)
        }
    }

    private suspend fun runMigrations(context: Context, oldVersion: Long, newVersion: Long) {
        Log.i(TAG, "Running upgrade cleanups and resets ($oldVersion → $newVersion)...")

        // 1. Purge stale partial downloads and temp lock files from app cache dirs
        try {
            val internalCacheDirs = listOfNotNull(
                context.cacheDir,
                context.externalCacheDir,
                context.filesDir,
                context.noBackupFilesDir,
                File(context.cacheDir, "yt-dlp"),
                File(context.filesDir, ".cache"),
                File(context.noBackupFilesDir, ".cache"),
                File(context.noBackupFilesDir, "tmp"),
            )
            for (dir in internalCacheDirs) {
                purgeStaleFiles(dir)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean internal cache stale files", e)
        }

        // 2. Purge stale files from external temp dir (safe — wrapped in try/catch)
        try {
            val externalTempDir = FileUtil.getExternalTempDir()
            purgeStaleFiles(externalTempDir)
            externalTempDir.listFiles()?.forEach { it.deleteRecursively() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clean external temp dir (possibly no storage permission)", e)
        }

        // 3. Nuke yt-dlp extractor cache to force fresh player.js / cipher re-fetch
        try {
            File(context.cacheDir, "yt-dlp").deleteRecursively()
            File(context.filesDir, ".cache").deleteRecursively()
            File(context.noBackupFilesDir, ".cache").deleteRecursively()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear yt-dlp extractor cache", e)
        }

        // 4. CRITICAL: Delete stale youtubedl-android Python package trees so they are
        //    re-extracted cleanly from the new APK. This fixes binary corruption and
        //    version mismatches that cause failures when updating over an old installation.
        try {
            val ytdlPackageDirs = listOfNotNull(
                File(context.filesDir, "youtubedl-android"),
                File(context.noBackupFilesDir, "youtubedl-android"),
                File(context.filesDir, "packages"),
                File(context.noBackupFilesDir, "packages"),
            )
            for (dir in ytdlPackageDirs) {
                if (dir.exists()) {
                    Log.i(TAG, "Deleting stale package dir for re-extraction: ${dir.absolutePath}")
                    dir.deleteRecursively()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete stale youtubedl-android package dirs", e)
        }

        // 5. Re-extract yt-dlp and FFmpeg binaries cleanly from the new APK assets
        try {
            YoutubeDL.getInstance().init(context)
            Log.i(TAG, "YoutubeDL re-initialized successfully")
        } catch (e: Exception) {
            Log.w(TAG, "YoutubeDL re-init failed (will retry at next launch): ${e.message}")
        }
        try {
            FFmpeg.getInstance().init(context)
            Log.i(TAG, "FFmpeg re-initialized successfully")
        } catch (e: Exception) {
            Log.w(TAG, "FFmpeg re-init failed (will retry at next launch): ${e.message}")
        }

        // 6. Sanitize Storage & SAF Preferences
        try {
            if (SDCARD_DOWNLOAD.getBoolean(false)) {
                val safUri = SDCARD_URI.getString()
                if (!PermissionManager.verifySafPermission(context, safUri)) {
                    Log.w(TAG, "Revoked or invalid SAF URI on upgrade — resetting SD card download.")
                    SDCARD_DOWNLOAD.updateBoolean(false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sanitize SAF preferences", e)
        }

        // 7. Sanitize Subtitle Language Preference (reset if blank/corrupted)
        try {
            val currentSubLang = SUBTITLE_LANGUAGE.getString()
            if (currentSubLang.isBlank()) {
                SUBTITLE_LANGUAGE.updateString("ar.*,en.*,.*-orig")
                Log.i(TAG, "Reset blank SUBTITLE_LANGUAGE to default")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sanitize SUBTITLE_LANGUAGE preference", e)
        }

        // 8. Sync cached cookies to file system
        try {
            NetworkOptionBuilder.getCookiesContentFromDatabase().getOrNull()?.let { content ->
                FileUtil.writeContentToFile(content, context.getCookiesFile())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh cookies on upgrade", e)
        }

        // 9. Trigger yt-dlp update check in background
        try {
            UpdateUtil.updateYtDlp()
        } catch (e: Exception) {
            Log.w(TAG, "yt-dlp background update on upgrade skipped: ${e.message}")
        }

        Log.i(TAG, "Upgrade migration complete.")
    }

    /**
     * Deletes stale temp/lock/partial files from a directory without deleting
     * the directory itself or valid downloaded files.
     */
    private fun purgeStaleFiles(dir: File?) {
        if (dir == null || !dir.exists() || !dir.isDirectory) return
        dir.listFiles()?.forEach { file ->
            val name = file.name
            val isStale = STALE_PREFIXES.any { name.startsWith(it) } ||
                    STALE_EXTENSIONS.any { name.endsWith(it) }
            if (isStale) {
                file.deleteRecursively()
            }
        }
    }
}
