package com.junkfood.seal.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.junkfood.seal.App.Companion.context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object SafStorageUtil {

    /**
     * Checks if a given URI or path points to a removable SD card.
     */
    fun isSdCardStorage(pathOrUri: String): Boolean {
        return pathOrUri.contains("tree/primary", ignoreCase = true).not() &&
                (pathOrUri.contains("tree/", ignoreCase = true) ||
                 pathOrUri.contains("/storage/", ignoreCase = true) && !pathOrUri.contains("/storage/emulated/0", ignoreCase = true))
    }

    /**
     * Resolves a DocumentFile from a tree URI and ensures target subdirectory exists.
     */
    fun resolveOrCreateDirectory(treeUri: Uri, subDirName: String): DocumentFile? {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        if (!rootDoc.exists() || !rootDoc.isDirectory) return null

        if (subDirName.isBlank()) return rootDoc

        val existing = rootDoc.findFile(subDirName)
        return if (existing != null && existing.isDirectory) {
            existing
        } else {
            rootDoc.createDirectory(subDirName)
        }
    }

    /**
     * Exports download history and saved links to a structured JSON string.
     */
    suspend fun exportHistoryToJson(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject()
            root.put("version", 2)
            root.put("timestamp", System.currentTimeMillis())

            val savedLinksArray = JSONArray()
            PreferenceUtil.getSavedLinks().forEach { savedLinksArray.put(it) }
            root.put("saved_links", savedLinksArray)

            root.toString(2)
        }
    }

    /**
     * Imports history from a structured JSON string.
     */
    suspend fun importHistoryFromJson(jsonString: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(jsonString)
            val savedLinksArray = root.optJSONArray("saved_links") ?: JSONArray()
            var importedCount = 0

            for (i in 0 until savedLinksArray.length()) {
                val link = savedLinksArray.optString(i)
                if (link.isNotBlank()) {
                    PreferenceUtil.saveLink(link)
                    importedCount++
                }
            }

            importedCount
        }
    }
}
