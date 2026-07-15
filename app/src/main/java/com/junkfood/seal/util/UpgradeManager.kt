package com.junkfood.seal.util

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.util.Log
import com.junkfood.seal.App.Companion.packageInfo
import com.junkfood.seal.util.PreferenceUtil.getInt
import com.junkfood.seal.util.PreferenceUtil.updateInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

        if (lastKnownVersionCode == 0) {
            // First install or upgrade from a version that didn't have UpgradeManager
            Log.d(TAG, "First time running UpgradeManager. Current version: $currentVersionCode")
            LAST_KNOWN_VERSION.updateInt(currentVersionCode)
            return@withContext
        }

        if (currentVersionCode > lastKnownVersionCode) {
            Log.d(TAG, "Upgrading from $lastKnownVersionCode to $currentVersionCode")
            
            // Run migrations based on version
            runMigrations(lastKnownVersionCode, currentVersionCode)
            
            // Update last known version
            LAST_KNOWN_VERSION.updateInt(currentVersionCode)
        }
    }

    private fun runMigrations(oldVersion: Int, newVersion: Int) {
        // Example: if (oldVersion < 30) { migrateSomething() }
        Log.d(TAG, "Running migrations...")
    }
}
