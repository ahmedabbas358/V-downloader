package com.junkfood.seal.download.engine.playlist

import com.junkfood.seal.download.engine.identity.ContentState
import com.junkfood.seal.download.engine.identity.ContentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class PlaylistManifest(
    val playlistId: String,
    val title: String,
    val canonicalUrl: String,
    val totalItems: Int,
    val updatedAt: Long = System.currentTimeMillis(),
    val orderedItems: List<PlaylistManifestItem>,
)

@Serializable
data class PlaylistManifestItem(
    val index: Int,
    val videoId: String,
    val title: String,
    val canonicalUrl: String,
    val state: ContentState = ContentState.MISSING,
    val contentType: ContentType = ContentType.VIDEO,
    val localPath: String? = null,
    val language: String? = null,
    val source: String? = null,
    val format: String? = null,
)

object PlaylistManifestStore {
    const val FILE_NAME = "playlist-manifest.json"

    private val json =
        Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
        }

    fun manifestFile(directory: File): File = File(directory, FILE_NAME)

    fun read(directory: File): PlaylistManifest? {
        val file = manifestFile(directory)
        if (!file.exists() || file.length() == 0L) return null
        return runCatching { json.decodeFromString<PlaylistManifest>(file.readText()) }.getOrNull()
    }

    fun writeAtomic(directory: File, manifest: PlaylistManifest): Result<File> =
        runCatching {
            directory.mkdirs()
            val finalFile = manifestFile(directory)
            val tmpFile = File(directory, "$FILE_NAME.tmp")
            tmpFile.writeText(json.encodeToString(manifest))
            if (finalFile.exists()) finalFile.delete()
            if (!tmpFile.renameTo(finalFile)) {
                tmpFile.copyTo(finalFile, overwrite = true)
                tmpFile.delete()
            }
            finalFile
        }
}
