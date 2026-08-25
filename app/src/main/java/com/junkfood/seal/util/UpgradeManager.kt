package com.junkfood.seal.util

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.util.Log
import com.junkfood.seal.App.Companion.packageInfo
import com.junkfood.seal.download.engine.builder.NetworkOptionBuilder
import com.junkfood.seal.util.FileUtil.getCookiesFile
import com.junkfood.seal.util.PreferenceUtil.getBoolean
import com.junkfood.seal.util.PreferenceUtil.getInt
import com.junkfood.seal.util.PreferenceUtil.getString
import com.junkfood.seal.util.PreferenceUtil.updateBoolean
import com.junkfood.seal.util.PreferenceUtil.updateInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object UpgradeManager {

    private const val TAG = "UpgradeManager"
    const val LAST_KNOWN_VERSION = "last_known_version"

    suspend fun checkAndRunMigrations(context: Context) = withContext(Dispatchers.IO) {
        val currentVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo?.longVersionCode?.toInt() ?: 0
        } else {
            @Suppress("DEPRECATION")
            packageInfo?.versionCode ?: 0
        }

        val lastKnownVersionCode = LAST_KNOWN_VERSION.getInt(0)

        if (lastKnownVersionCode == 0 || currentVersionCode > lastKnownVersionCode) {
            Log.d(TAG, "Running app upgrade migrations from version $lastKnownVersionCode to $currentVersionCode")
            runMigrations(context, lastKnownVersionCode, currentVersionCode)
            LAST_KNOWN_VERSION.updateInt(currentVersionCode)
        }
    }

    private suspend fun runMigrations(context: Context, oldVersion: Int, newVersion: Int) {
        Log.d(TAG, "Executing upgrade cleanups and state resets...")

        // 1. Clear temporary cache files, partial downloads, and stale lockfiles
        try {
            val tempDirs = listOfNotNull(
                FileUtil.getExternalTempDir(),
                context.cacheDir,
                context.externalCacheDir,
                File(context.noBackupFilesDir, "tmp")
            )
            for (dir in tempDirs) {
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.forEach { file ->
                        if (file.name.startsWith("sub_temp_") ||
                            file.name.startsWith("sub_direct_") ||
                            file.name.endsWith(".part") ||
                            file.name.endsWith(".ytdl") ||
                            file.name.endsWith(".tmp")
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

        // 2. Sanitize Storage & SAF Preferences
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

        // 3. Sync & Flush Stored Cookies
        try {
            NetworkOptionBuilder.getCookiesContentFromDatabase().getOrNull()?.let { content ->
                FileUtil.writeContentToFile(content, context.getCookiesFile())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh cookies on upgrade", e)
        }

        // 4. Force yt-dlp binary update on upgrade to immediately apply latest extractors
        try {
            UpdateUtil.updateYtDlp()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update yt-dlp binary on upgrade", e)
        }
    }
}
