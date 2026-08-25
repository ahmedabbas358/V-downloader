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
import com.junkfood.seal.util.PLAYLIST_NUMBERING
import com.junkfood.seal.util.PreferenceUtil.getBoolean
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
        playlistIndex: Int = 0,
        appContext: Context = context,
        onProgress: (SubtitleProgress) -> Unit = {}
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            if (tracks.isEmpty()) {
                throw SubtitleFailure.NoSubtitles
            }

            destinationDir.mkdirs()
            val tempWorkDir = File(appContext.cacheDir, "sub_temp_${videoId}_${UUID.randomUUID().toString().take(8)}")
            tempWorkDir.mkdirs()

            val downloadedValidFiles = mutableListOf<File>()
            val targetFormat = SubtitleOutputFormat.fromExtension(
                SubtitleOptionBuilder.getConvertSubsValue(preferences.convertSubtitle)
            )
            val cleanBaseTitle = FileUtil.cleanFileName(title).ifBlank { "Video_$videoId" }
            val existingValidFiles =
                tracks.map { track ->
                    val expectedName =
                        buildSafeSubtitleFileName(
                            baseTitle = cleanBaseTitle,
                            tempGeneratedName = "${videoId}.${track.languageCode}.${targetFormat.extension}",
                            targetFormat = targetFormat,
                            videoId = videoId,
                            source = track.source,
                            playlistIndex = playlistIndex,
                            includePlaylistNumbering = preferences.playlistNumbering,
                        )
                    val existingFile = File(destinationDir, expectedName)
                    if (existingFile.exists() &&
                        SubtitleValidator.validateFile(existingFile, targetFormat).isSuccess
                    ) {
                        existingFile
                    } else {
                        null
                    }
                }

            val missingTracks = tracks.filterIndexed { index, _ -> 
                existingValidFiles[index] == null 
            }
            
            val validFiles = existingValidFiles.filterNotNull().toMutableList()

            if (missingTracks.isEmpty()) {
                tempWorkDir.deleteRecursively()
                onProgress(SubtitleProgress.Completed(validFiles.size))
                return@runCatching validFiles
            }

            try {
                // 1. Attempt ultra-fast Direct CDN HTTP Stream Download if direct URLs are available
                var directDownloadSucceeded = false
                val directDownloadedTempFiles = mutableListOf<File>()

                val tracksWithUrls = missingTracks.filter { it.directUrl != null || it.formats.any { f -> f.url.isNotBlank() } }
                if (tracksWithUrls.size == missingTracks.size) {
                    onProgress(SubtitleProgress.Downloading(missingTracks.joinToString(",") { it.languageCode }, 0.5f))
                    var allDirectOk = true
                    for (track in missingTracks) {
                        val downloadedFile = downloadTrackDirectlyViaHttp(track, tempWorkDir, videoId)
                        if (downloadedFile != null && SubtitleValidator.validateFile(downloadedFile).isSuccess) {
                            directDownloadedTempFiles.add(downloadedFile)
                        } else {
                            allDirectOk = false
                            break
                        }
                    }
                    if (allDirectOk && directDownloadedTempFiles.size == missingTracks.size) {
                        directDownloadSucceeded = true
                    } else {
                        directDownloadedTempFiles.forEach { it.delete() }
                        directDownloadedTempFiles.clear()
                    }
                }

                var extractorOutput: String? = null

                // 2. If direct download was not available or failed, fallback to yt-dlp process
                if (!directDownloadSucceeded) {
                    val rawTargetLangs = missingTracks.joinToString(",") { it.languageCode }
                    val targetLangs = buildSubLangsOption(rawTargetLangs)
                    val hasAutoTrack = missingTracks.any { it.source == SubtitleSource.AUTO_GENERATED || it.source == SubtitleSource.TRANSLATED }
                    val hasManualTrack = missingTracks.any { it.source == SubtitleSource.MANUAL }

                    onProgress(SubtitleProgress.Downloading(rawTargetLangs, 0.4f))

                    val request = YoutubeDLRequest(url).apply {
                        addOption("--skip-download")
                        addOption("--no-playlist")
                        addOption("--no-mtime")
                        addOption("--force-overwrites")
                        addOption("--no-check-certificates")

                        if (hasManualTrack) addOption("--write-subs")
                        if (hasAutoTrack) addOption("--write-auto-subs")

                        addOption("--sub-langs", targetLangs)
                        addOption("--sub-format", "best/vtt/srt/ass/lrc/srv3/srv2/srv1")

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

                        // Output to temp working directory in cacheDir
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

                    SubtitleValidator.validateFile(convertedFile, targetFormat).getOrThrow()

                    // 3. Move atomically to final destination with safe sanitized filename
                    val finalFileName =
                        buildSafeSubtitleFileName(
                            baseTitle = cleanBaseTitle,
                            tempGeneratedName = convertedFile.name,
                            targetFormat = targetFormat,
                            videoId = videoId,
                            source = findSourceForGeneratedFile(convertedFile, tracks),
                            playlistIndex = playlistIndex,
                            includePlaylistNumbering = preferences.playlistNumbering,
                        )
                    val finalFile = File(destinationDir, finalFileName)
                    val finalPartFile = File(destinationDir, "$finalFileName.part")

                    if (finalPartFile.exists()) {
                        finalPartFile.delete()
                    }
                    convertedFile.copyTo(finalPartFile, overwrite = true)
                    SubtitleValidator.validateFile(finalPartFile, targetFormat).getOrThrow()

                    if (finalFile.exists()) {
                        finalFile.delete()
                    }
                    if (!finalPartFile.renameTo(finalFile)) {
                        finalPartFile.copyTo(finalFile, overwrite = true)
                        finalPartFile.delete()
                    }
                    SubtitleValidator.validateFile(finalFile, targetFormat).getOrThrow()
                    convertedFile.delete()

                    downloadedValidFiles.add(finalFile)
                    validFiles.add(finalFile)
                }

                onProgress(SubtitleProgress.Completed(validFiles.size))
                validFiles
            } finally {
                // Ensure temporary work directory is cleanly deleted
                tempWorkDir.deleteRecursively()
            }
        }
    }

    /**
     * Builds the expanded subtitle language option string for yt-dlp.
     */
    fun buildSubLangsOption(rawLang: String): String {
        val trimmed = rawLang.trim()
        if (trimmed.isEmpty() || trimmed.equals("all", ignoreCase = true)) return "all"
        val langs = trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (langs.isEmpty()) return "all"
        return langs.flatMap { l ->
            if (l == "all" || l.contains("-") || l.contains(".*")) {
                listOf(l)
            } else {
                listOf(l, "$l-.*", "$l-orig")
            }
        }.distinct().joinToString(",")
    }

    /**
     * Fallback direct download executing yt-dlp with --write-subs and --write-auto-subs
     * (the proven approach from commit 5849a919) to ensure 100% download reliability.
     */
    suspend fun downloadSubtitlesDirectly(
        url: String,
        videoId: String,
        title: String,
        destinationDir: File,
        preferences: DownloadPreferences,
        playlistIndex: Int = 0,
        appContext: Context = context,
        onProgress: (SubtitleProgress) -> Unit = {}
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            destinationDir.mkdirs()
            val tempWorkDir = File(appContext.cacheDir, "sub_direct_${videoId}_${UUID.randomUUID().toString().take(8)}")
            tempWorkDir.mkdirs()

            try {
                val rawLang = preferences.subtitleLanguage.ifBlank { "ar,en" }
                val subLangs = buildSubLangsOption(rawLang)
                val targetFormatStr = SubtitleOptionBuilder.getConvertSubsValue(preferences.convertSubtitle).ifBlank { "srt" }
                val targetFormat = SubtitleOutputFormat.fromExtension(targetFormatStr)

                onProgress(SubtitleProgress.Downloading(subLangs, 0.3f))

                val request = YoutubeDLRequest(url).apply {
                    addOption("--skip-download")
                    addOption("--no-playlist")
                    addOption("--no-mtime")
                    addOption("--force-overwrites")
                    addOption("--no-check-certificates")
                    addOption("--write-subs")
                    addOption("--write-auto-subs")
                    addOption("--sub-langs", subLangs)
                    addOption("--sub-format", "best/vtt/srt/ass/lrc/srv3/srv2/srv1")
                    val extractorArgs = YoutubeClientStrategy.buildExtractorArgs(clientChain = listOf(YoutubeClient.ANDROID, YoutubeClient.DEFAULT, YoutubeClient.WEB))
                    addOption("--extractor-args", extractorArgs)

                    if (preferences.cookies) {
                        NetworkOptionBuilder.applyCookies(this, preferences.userAgentString, appContext)
                    }
                    if (preferences.proxy) {
                        NetworkOptionBuilder.applyProxy(this, preferences.proxyUrl)
                    }
                    NetworkOptionBuilder.applyNetworkResilience(this, preferences.forceIpv4, preferences.debug)

                    addOption("-P", tempWorkDir.absolutePath)
                    addOption("-o", OutputTemplateBuilder.BASENAME)
                }

                val processId = "sub_direct_${videoId}_${System.currentTimeMillis()}"
                YoutubeDL.getInstance().execute(request, processId) { progress, _, _ ->
                    onProgress(SubtitleProgress.Downloading(subLangs, 0.3f + (progress / 100f) * 0.5f))
                }

                val tempFiles = tempWorkDir.listFiles()?.filter { file ->
                    file.isFile && !file.name.endsWith(".part") && !file.name.endsWith(".ytdl") && !file.name.endsWith(".tmp") && file.length() > 10L
                } ?: emptyList()

                if (tempFiles.isEmpty()) {
                    throw SubtitleFailure.NoSubtitles
                }

                // Deduplicate generated subtitle files: select only 1 best file per language code
                val deduplicatedTempFiles = tempFiles
                    .groupBy { file ->
                        val match = Regex("""\.([a-zA-Z]{2,3}(?:-[a-zA-Z0-9_-]+)?)\.[a-zA-Z0-9]+$""").find(file.name)
                        match?.groupValues?.get(1)?.substringBefore('-') ?: file.nameWithoutExtension
                    }
                    .values
                    .mapNotNull { langGroup ->
                        langGroup.minByOrNull { file ->
                            val isOrig = file.name.contains("-orig", ignoreCase = true)
                            val isAuto = file.name.contains("auto", ignoreCase = true)
                            val length = file.name.length
                            (if (isOrig) 100 else 0) + (if (isAuto) 50 else 0) + length
                        }
                    }

                val downloadedFiles = mutableListOf<File>()
                val cleanBaseTitle = FileUtil.cleanFileName(title)
                    .removePrefix("[Subtitles] ")
                    .removePrefix("[Subtitle] ")
                    .replace(Regex("""^\d{2,4}\s*-\s*"""), "")
                    .trim()
                    .ifBlank { "Video_$videoId" }

                for (tempFile in deduplicatedTempFiles) {
                    val convertedFile = if (SubtitleOutputFormat.fromExtension(tempFile.extension) != targetFormat) {
                        onProgress(SubtitleProgress.Converting(targetFormat.extension))
                        SubtitleConverter.convert(tempFile, targetFormat).getOrElse { tempFile }
                    } else {
                        tempFile
                    }

                    val finalFileName = buildSafeSubtitleFileName(
                        baseTitle = cleanBaseTitle,
                        tempGeneratedName = convertedFile.name,
                        targetFormat = targetFormat,
                        videoId = videoId,
                        source = SubtitleSource.AUTO_GENERATED,
                        playlistIndex = playlistIndex,
                        includePlaylistNumbering = preferences.playlistNumbering,
                    )
                    val finalFile = File(destinationDir, finalFileName)
                    convertedFile.copyTo(finalFile, overwrite = true)
                    convertedFile.delete()
                    downloadedFiles.add(finalFile)
                }

                onProgress(SubtitleProgress.Completed(downloadedFiles.size))
                downloadedFiles
            } finally {
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
            targetFormat: SubtitleOutputFormat,
            videoId: String = "",
            source: SubtitleSource = SubtitleSource.UNKNOWN,
            playlistIndex: Int = 0,
            includePlaylistNumbering: Boolean = false,
        ): String {
            // Extract language suffix from generated temp file name (e.g., "title.ar.srt" -> ".ar")
            val langSuffix = Regex("""\.([a-zA-Z]{2,3}(?:-[a-zA-Z0-9_-]+)?)\.[a-zA-Z0-9]+$""")
                .find(tempGeneratedName)?.groupValues?.get(1)?.let { ".$it" } ?: ""

            val cleanTitle = FileUtil.cleanFileName(baseTitle)
                .removePrefix("[Subtitles] ")
                .removePrefix("[Subtitle] ")
                .replace(Regex("""[/\\:*?"<>|]"""), "_")
                .replace("..", "_")
                .trim()
                .ifBlank { "Video_${videoId.ifBlank { "subtitle" }}" }

            val shouldNumber = (includePlaylistNumbering || PLAYLIST_NUMBERING.getBoolean(true)) && playlistIndex > 0
            val indexPrefix = if (shouldNumber && !Regex("""^\d{2,4}\s*-\s*""").containsMatchIn(cleanTitle)) {
                "%03d - ".format(Locale.US, playlistIndex)
            } else {
                ""
            }

            return "$indexPrefix$cleanTitle$langSuffix.${targetFormat.extension}"
        }

    private fun findSourceForGeneratedFile(
        file: File,
        tracks: List<SubtitleTrack>,
    ): SubtitleSource {
        val name = file.name.lowercase(Locale.US)
        return tracks.firstOrNull { track ->
            val lang = track.languageCode.lowercase(Locale.US)
            name.contains(".$lang.") || name.contains(".$lang-") || name.contains("-$lang.")
        }?.source ?: tracks.firstOrNull()?.source ?: SubtitleSource.UNKNOWN
    }

    private fun downloadTrackDirectlyViaHttp(
        track: SubtitleTrack,
        tempWorkDir: File,
        videoId: String
    ): File? {
        val targetUrl = track.directUrl ?: track.formats.firstOrNull { it.url.isNotBlank() }?.url ?: return null
        return try {
            val ext = track.formats.firstOrNull { it.url == targetUrl }?.ext ?: "vtt"
            val partFile = File(tempWorkDir, "${videoId}.${track.languageCode}.${ext}.part")
            val tempFile = File(tempWorkDir, "${videoId}.${track.languageCode}.${ext}")
            val urlObj = java.net.URL(targetUrl)
            val conn = urlObj.openConnection() as java.net.HttpURLConnection
            try {
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                conn.connect()
                if (conn.responseCode == 200) {
                    conn.inputStream.use { input ->
                        partFile.outputStream().use { output ->
                            input.copyTo(output)
                            output.fd.sync()
                        }
                    }
                    if (partFile.exists() && partFile.length() > 20L && partFile.renameTo(tempFile)) {
                        tempFile
                    } else {
                        partFile.delete()
                        tempFile.delete()
                        null
                    }
                } else {
                    null
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }
}
