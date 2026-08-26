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
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object UpgradeManager {

    private const val TAG = "UpgradeManager"
    const val LAST_KNOWN_VERSION = "last_known_version_code"

    // Stale temporary/download artifacts to purge on upgrade (safe to delete)
    private val STALE_EXTENSIONS = setOf(".part", ".ytdl", ".tmp", ".aria2")
    private val STALE_PREFIXES = setOf("sub_temp_", "sub_direct_")

    /**
     * Checks if the Python runtime and its essential support libraries (including libandroid-support.so)
     * are properly extracted on disk.
     */
    fun isPythonRuntimeIntact(context: Context): Boolean {
        return try {
            val pythonLibDir = File(context.noBackupFilesDir, "youtubedl-android/packages/python/usr/lib")
            val libSupport = File(pythonLibDir, "libandroid-support.so")
            pythonLibDir.exists() && pythonLibDir.isDirectory && (libSupport.exists() || (pythonLibDir.listFiles()?.isNotEmpty() == true))
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Ensures all native binaries (Python, yt-dlp, FFmpeg, Aria2c) are extracted and ready.
     * If the runtime is missing or was corrupted by a previous faulty update, it performs
     * a clean, self-healing re-extraction.
     */
    fun ensureNativeEnvironment(context: Context) {
        val runtimeIntact = isPythonRuntimeIntact(context)
        if (!runtimeIntact) {
            Log.w(TAG, "Native Python environment or libandroid-support.so is missing/corrupted. Repairing...")
            repairNativeEnvironment(context)
        } else {
            // Normal initialization
            initNativeLibrariesSafely(context)
        }
    }

    /**
     * Forces clean re-extraction of native packages by clearing cached package metadata
     * and resetting library state.
     */
    private fun repairNativeEnvironment(context: Context) {
        try {
            // 1. Clear youtubedl-android shared preferences so it re-extracts packages
            val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            if (sharedPrefsDir.exists()) {
                sharedPrefsDir.listFiles()?.forEach { file ->
                    val name = file.name
                    if (name.contains("youtubedl", ignoreCase = true) || name.contains("yausername", ignoreCase = true)) {
                        val prefName = name.removeSuffix(".xml")
                        context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().clear().commit()
                        file.delete()
                    }
                }
            }

            // 2. Delete broken packages directory
            val packagesDir = File(context.noBackupFilesDir, "youtubedl-android/packages")
            if (packagesDir.exists()) {
                packagesDir.deleteRecursively()
            }

            // 3. Reset in-memory initialized flags via reflection
            resetInitializedFlags()

            // 4. Clean re-init
            initNativeLibrariesSafely(context)

            Log.i(TAG, "Native environment repair completed. Runtime intact: ${isPythonRuntimeIntact(context)}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed during native environment repair", e)
        }
    }

    private fun initNativeLibrariesSafely(context: Context) {
        try {
            YoutubeDL.init(context)
            Log.i(TAG, "YoutubeDL initialized")
        } catch (e: Exception) {
            Log.e(TAG, "YoutubeDL.init failed", e)
        }
        try {
            FFmpeg.init(context)
            Log.i(TAG, "FFmpeg initialized")
        } catch (e: Exception) {
            Log.e(TAG, "FFmpeg.init failed", e)
        }
        try {
            Aria2c.init(context)
            Log.i(TAG, "Aria2c initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Aria2c.init failed", e)
        }
    }

    private fun resetInitializedFlags() {
        listOf(
            YoutubeDL::class.java,
            FFmpeg::class.java,
            Aria2c::class.java
        ).forEach { clazz ->
            runCatching {
                val field = clazz.getDeclaredField("initialized")
                field.isAccessible = true
                try {
                    field.set(null, false)
                } catch (_: Exception) {
                    val instance = clazz.getField("INSTANCE").get(null)
                    field.set(instance, false)
                }
            }
        }
    }

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

        // 1. Purge stale partial downloads and temporary files
        try {
            val cacheDirs = listOfNotNull(
                context.cacheDir,
                context.externalCacheDir,
                File(context.filesDir, "tmp"),
                File(context.noBackupFilesDir, "tmp"),
            )
            for (dir in cacheDirs) {
                purgeStaleFiles(dir)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean internal cache stale files", e)
        }

        // 2. Purge stale files from external temp dir
        try {
            val externalTempDir = FileUtil.getExternalTempDir()
            purgeStaleFiles(externalTempDir)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clean external temp dir", e)
        }

        // 3. Clear yt-dlp extractor internal cache (forces fresh cipher / player.js)
        try {
            File(context.cacheDir, "yt-dlp").deleteRecursively()
            File(context.filesDir, ".cache").deleteRecursively()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear yt-dlp extractor cache", e)
        }

        // 4. Sanitize Storage & SAF Preferences
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

        // 5. Sanitize Subtitle Language Preference (reset if blank)
        try {
            val currentSubLang = SUBTITLE_LANGUAGE.getString()
            if (currentSubLang.isBlank()) {
                SUBTITLE_LANGUAGE.updateString("ar.*,en.*,.*-orig")
                Log.i(TAG, "Reset blank SUBTITLE_LANGUAGE to default")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sanitize SUBTITLE_LANGUAGE preference", e)
        }

        // 6. Sync cached cookies to file system
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
            Log.w(TAG, "yt-dlp background update on upgrade skipped: ${e.message}")
        }

        Log.i(TAG, "Upgrade migration complete.")
    }

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

