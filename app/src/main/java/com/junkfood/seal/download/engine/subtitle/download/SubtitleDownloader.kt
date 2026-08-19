package com.junkfood.seal.download.engine.subtitle.download

import android.content.Context
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.download.engine.builder.NetworkOptionBuilder
import com.junkfood.seal.download.engine.builder.OutputTemplateBuilder
import com.junkfood.seal.download.engine.builder.SubtitleOptionBuilder
import com.junkfood.seal.download.engine.subtitle.conversion.SubtitleConverter
import com.junkfood.seal.download.engine.subtitle.model.SubtitleFailure
import com.junkfood.seal.download.engine.subtitle.model.SubtitleOutputFormat
import com.junkfood.seal.download.engine.subtitle.model.SubtitleProgress
import com.junkfood.seal.download.engine.subtitle.model.SubtitleSource
import com.junkfood.seal.download.engine.subtitle.model.SubtitleTrack
import com.junkfood.seal.download.engine.subtitle.validation.SubtitleValidator
import com.junkfood.seal.download.engine.subtitle.youtube.YoutubeClient
import com.junkfood.seal.download.engine.subtitle.youtube.YoutubeClientStrategy
import com.junkfood.seal.util.DownloadUtil.DownloadPreferences
import com.junkfood.seal.util.FileUtil
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * SubtitleDownloader executes targeted subtitle downloading with atomic writing,
 * format conversion, and strict content validation.
 */
object SubtitleDownloader {

    /**
     * Downloads selected subtitle tracks for a video.
     */
    suspend fun downloadSelectedTracks(
        url: String,
        videoId: String,
        title: String,
        tracks: List<SubtitleTrack>,
        destinationDir: File,
        preferences: DownloadPreferences,
        clientChain: List<YoutubeClient> = listOf(YoutubeClient.ANDROID, YoutubeClient.DEFAULT),
        appContext: Context = context,
        onProgress: (SubtitleProgress) -> Unit = {}
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            if (tracks.isEmpty()) {
                throw SubtitleFailure.NoSubtitles
            }

            destinationDir.mkdirs()
            val tempWorkDir = File(destinationDir, ".sub_temp_${videoId}_${UUID.randomUUID().toString().take(8)}")
            tempWorkDir.mkdirs()

            val downloadedValidFiles = mutableListOf<File>()

            try {
                // 1. Attempt ultra-fast Direct CDN HTTP Stream Download if direct URLs are available
                var directDownloadSucceeded = false
                val directDownloadedTempFiles = mutableListOf<File>()

                val tracksWithUrls = tracks.filter { it.directUrl != null || it.formats.any { f -> f.url.isNotBlank() } }
                if (tracksWithUrls.size == tracks.size) {
                    onProgress(SubtitleProgress.Downloading(tracks.joinToString(",") { it.languageCode }, 0.5f))
                    var allDirectOk = true
                    for (track in tracks) {
                        val downloadedFile = downloadTrackDirectlyViaHttp(track, tempWorkDir, videoId)
                        if (downloadedFile != null && SubtitleValidator.validateFile(downloadedFile).isSuccess) {
                            directDownloadedTempFiles.add(downloadedFile)
                        } else {
                            allDirectOk = false
                            break
                        }
                    }
                    if (allDirectOk && directDownloadedTempFiles.size == tracks.size) {
                        directDownloadSucceeded = true
                    } else {
                        directDownloadedTempFiles.forEach { it.delete() }
                        directDownloadedTempFiles.clear()
                    }
                }

                var extractorOutput: String? = null

                // 2. If direct download was not available or failed, fallback to yt-dlp process
                if (!directDownloadSucceeded) {
                    val targetLangs = tracks.joinToString(",") { it.languageCode }
                    val hasAutoTrack = tracks.any { it.source == SubtitleSource.AUTO_GENERATED || it.source == SubtitleSource.TRANSLATED }
                    val hasManualTrack = tracks.any { it.source == SubtitleSource.MANUAL }

                    onProgress(SubtitleProgress.Downloading(targetLangs, 0.4f))

                    val request = YoutubeDLRequest(url).apply {
                        addOption("--skip-download")
                        addOption("--no-playlist")
                        addOption("--no-part")
                        addOption("--no-mtime")
                        addOption("--force-overwrites")

                        if (hasManualTrack) addOption("--write-subs")
                        if (hasAutoTrack) addOption("--write-auto-subs")

                        addOption("--sub-langs", targetLangs)
                        addOption("--sub-format", "srt/best/ass/vtt/lrc")

                        // Target format
                        val targetFormatStr = SubtitleOptionBuilder.getConvertSubsValue(preferences.convertSubtitle)
                        if (targetFormatStr.isNotBlank()) {
                            addOption("--convert-subs", targetFormatStr)
                        }

                        // Client strategy
                        val extractorArgs = YoutubeClientStrategy.buildExtractorArgs(clientChain = clientChain)
                        addOption("--extractor-args", extractorArgs)

                        // Network options
                        if (preferences.cookies) {
                            NetworkOptionBuilder.applyCookies(this, preferences.userAgentString, appContext)
                        }
                        if (preferences.proxy) {
                            NetworkOptionBuilder.applyProxy(this, preferences.proxyUrl)
                        }
                        NetworkOptionBuilder.applyNetworkResilience(this, preferences.forceIpv4, preferences.debug)

                        // Output to temp working directory
                        addOption("-P", tempWorkDir.absolutePath)
                        addOption("-o", OutputTemplateBuilder.BASENAME)
                    }

                    // Execute yt-dlp process
                    val processId = "sub_${videoId}_${System.currentTimeMillis()}"
                    val response = runCatching {
                        YoutubeDL.getInstance().execute(request, processId) { progress, _, text ->
                            onProgress(SubtitleProgress.Downloading(targetLangs, 0.4f + (progress / 100f) * 0.4f))
                        }
                    }.getOrElse { th ->
                        if (th is YoutubeDL.CanceledException) {
                            throw SubtitleFailure.Canceled
                        }
                        throw SubtitleFailure.fromThrowable(th)
                    }
                    extractorOutput = response.out
                }

                onProgress(SubtitleProgress.Validating("Validating downloaded files..."))

                // 2. Discover and Validate all generated subtitle files in temp dir
                val tempFiles = tempWorkDir.listFiles()?.filter { file ->
                    file.isFile && !file.name.endsWith(".part") && !file.name.endsWith(".ytdl")
                } ?: emptyList()

                if (tempFiles.isEmpty()) {
                    val msg = extractorOutput?.let { " Output: $it" } ?: ""
                    throw SubtitleFailure.InvalidSubtitle("No subtitle files generated by extractor.$msg")
                }

                val targetFormat = SubtitleOutputFormat.fromExtension(
                    SubtitleOptionBuilder.getConvertSubsValue(preferences.convertSubtitle)
                )

                for (tempFile in tempFiles) {
                    // Deep Validation
                    val validationRes = SubtitleValidator.validateFile(tempFile)
                    if (validationRes.isFailure) {
                        val failure = validationRes.exceptionOrNull()
                        if (failure is SubtitleFailure) throw failure
                        throw SubtitleFailure.InvalidSubtitle("Validation failed: ${failure?.message}")
                    }

                    // Format Conversion if needed
                    val convertedFile = if (SubtitleOutputFormat.fromExtension(tempFile.extension) != targetFormat) {
                        onProgress(SubtitleProgress.Converting(targetFormat.extension))
                        SubtitleConverter.convert(tempFile, targetFormat).getOrThrow()
                    } else {
                        tempFile
                    }

                    // 3. Move atomically to final destination with safe sanitized filename
                    val cleanBaseTitle = FileUtil.cleanFileName(title).ifBlank { "Video_$videoId" }
                    val finalFileName = buildSafeSubtitleFileName(cleanBaseTitle, convertedFile.name, targetFormat)
                    val finalFile = File(destinationDir, finalFileName)

                    if (finalFile.exists()) {
                        finalFile.delete()
                    }

                    if (!convertedFile.renameTo(finalFile)) {
                        convertedFile.copyTo(finalFile, overwrite = true)
                        convertedFile.delete()
                    }

                    downloadedValidFiles.add(finalFile)
                }

                onProgress(SubtitleProgress.Completed(downloadedValidFiles.size))
                downloadedValidFiles
            } finally {
                // Ensure temporary work directory is cleanly deleted
                tempWorkDir.deleteRecursively()
            }
        }
    }

    /**
     * Generates a safe, non-traversing, sanitized filename for the subtitle file.
     */
    fun buildSafeSubtitleFileName(
        baseTitle: String,
        tempGeneratedName: String,
        targetFormat: SubtitleOutputFormat
    ): String {
        // Extract language suffix from generated temp file name (e.g., "title.ar.srt" -> ".ar")
        val langSuffix = Regex("""\.([a-zA-Z]{2,3}(?:-[a-zA-Z0-9_-]+)?)\.[a-zA-Z0-9]+$""")
            .find(tempGeneratedName)?.groupValues?.get(1)?.let { ".$it" } ?: ""

        val cleanTitle = FileUtil.cleanFileName(baseTitle)
            .replace(Regex("""[/\\:*?"<>|]"""), "_")
            .replace("..", "_")
            .trim()
            .ifBlank { "subtitle" }

        return "$cleanTitle$langSuffix.${targetFormat.extension}"
    }

    private fun downloadTrackDirectlyViaHttp(
        track: SubtitleTrack,
        tempWorkDir: File,
        videoId: String
    ): File? {
        val targetUrl = track.directUrl ?: track.formats.firstOrNull { it.url.isNotBlank() }?.url ?: return null
        return try {
            val ext = track.formats.firstOrNull { it.url == targetUrl }?.ext ?: "vtt"
            val tempFile = File(tempWorkDir, "${videoId}.${track.languageCode}.${ext}")
            val urlObj = java.net.URL(targetUrl)
            val conn = urlObj.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            conn.connect()
            if (conn.responseCode == 200) {
                conn.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempFile.exists() && tempFile.length() > 20L) {
                    tempFile
                } else {
                    tempFile.delete()
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
