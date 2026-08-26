package com.junkfood.seal.download.engine

import android.content.Context
import android.util.Log
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.database.objects.CommandTemplate
import com.junkfood.seal.download.Task
import com.junkfood.seal.download.Task.TypeInfo
import com.junkfood.seal.download.engine.builder.DownloadCommandBuilder
import com.junkfood.seal.download.engine.builder.OutputTemplateBuilder
import com.junkfood.seal.download.engine.postprocess.PostDownloadCoordinator

import com.junkfood.seal.download.engine.subtitle.SubtitleManager
import com.junkfood.seal.util.DownloadUtil
import com.junkfood.seal.util.VideoInfo
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * DownloadTaskExecutor
 *
 * Executes individual download operations:
 * - Metadata & VideoInfo fetching with resilient retries and Cobalt fallback
 * - Media stream downloading with real-time progress parsing and merging state detection
 * - Custom yt-dlp command templates execution
 *
 * KEY FIX: Added --newline to all download requests so that yt-dlp outputs one line
 * per callback invocation, enabling reliable "Destination:" path discovery.
 */
object DownloadTaskExecutor {

    private const val TAG = "DownloadTaskExecutor"

    private val jsonFormat =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }

    // Patterns that extract the actual output file path from yt-dlp stdout lines.
    // These must match ONE path per line (--newline guarantees line-by-line output).
    private val PATH_PATTERNS = listOf(
        // Standard download destination
        Regex("""^\[download\] Destination:\s*(.+)$"""),
        // Subtitle output patterns
        Regex("""^\[info\] Writing (?:video |automatic )?subtitles(?: \(all\))? to:?\s*["']?(.+?)["']?$""", RegexOption.IGNORE_CASE),
        Regex("""^\[ffmpeg\] Converting subtitles.* to ["']?(.+?)["']?$""", RegexOption.IGNORE_CASE),
        Regex("""^\[(?:Subtitle|info)\] Destination:\s*["']?(.+?)["']?$""", RegexOption.IGNORE_CASE),
        // Already downloaded
        Regex("""^\[download\]\s+(.+\.(?:mp4|mkv|webm|m4a|mp3|opus|flac|wav|ogg|m4b|mka|srt|vtt|ass|lrc))\s+has already been downloaded""", RegexOption.IGNORE_CASE),
        // Merger output
        Regex("""^\[Merger\] Merging formats into "(.+)"$"""),
        // FFmpegVideoConverter, FFmpegExtractAudio, etc.
        Regex("""^\[(\w+)\] Destination:\s*(.+)$"""),
        // Moving file
        Regex("""^\[download\] Moving file.+ to: (.+)$"""),
        // Correcting container
        Regex("""^\[ffmpeg\] Correcting container in "(.+)"$"""),
        // ExtractAudio output
        Regex("""^\[ExtractAudio\] Destination:\s*(.+)$"""),
        // VideoRemuxer
        Regex("""^\[VideoRemuxer\] Destination:\s*(.+)$"""),
    )

    /**
     * Extracts a file path from a single yt-dlp output line.
     * Returns null if the line doesn't contain a recognizable file path.
     */
    private fun extractPathFromLine(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null

        for (pattern in PATH_PATTERNS) {
            val match = pattern.find(trimmed) ?: continue
            // Last group always has the path (some patterns have 2 groups, some 1)
            val path = match.groupValues.last().trim()
                .removeSurrounding("\"")
                .removeSurrounding("'")
            if (path.isNotBlank() && (path.startsWith("/") || path.contains(":/") || path.contains(":\\"))) {
                val f = File(path)
                // Accept the path even if file doesn't exist yet (still being written)
                return path
            }
        }
        return null
    }

    /**
     * Fetches metadata for a given task.
     */
    suspend fun fetchVideoInfo(
        task: Task,
        appContext: Context = context,
    ): Result<VideoInfo> = withContext(Dispatchers.IO) {
        val taskInfo = task.type
        val isPlaylist = taskInfo is TypeInfo.Playlist && !taskInfo.isFallback
        val fetchUrl = task.url

        val isDirectVideoUrl =
            fetchUrl.contains("watch?v=", ignoreCase = true) ||
            fetchUrl.contains("youtu.be/", ignoreCase = true) ||
            fetchUrl.contains("/shorts/", ignoreCase = true)

        val playlistIndex = if (isPlaylist && !isDirectVideoUrl) {
            (taskInfo as TypeInfo.Playlist).index
        } else {
            null
        }

        val isSubtitleTask = task.preferences.skipDownload && task.preferences.downloadSubtitle
        var acquiredSubtitleLock = false

        try {
            if (isSubtitleTask) {
                SubtitleManager.subtitleMutex.lock()
                acquiredSubtitleLock = true
                delay(50L)
            }

            var retryCount = 0
            val maxRetries = 3
            var result: Result<VideoInfo>? = null

            while (retryCount <= maxRetries) {
                if (retryCount > 0) {
                    delay((1000L * retryCount).coerceAtMost(3000L))
                }

                val executionRes = DownloadUtil.fetchVideoInfoFromUrl(
                    url = fetchUrl,
                    playlistIndex = playlistIndex,
                    taskKey = task.id,
                    preferences = task.preferences,
                )

                if (executionRes.isSuccess) {
                    result = executionRes
                    break
                } else {
                    val th = executionRes.exceptionOrNull()
                    if (th is YoutubeDL.CanceledException) {
                        result = executionRes
                        break
                    }
                    if (retryCount == maxRetries) {
                        result = executionRes
                        break
                    }
                    retryCount++
                }
            }

            return@withContext result ?: Result.failure(IllegalStateException("لم يتم الحصول على نتيجة"))
        } finally {
            if (acquiredSubtitleLock) {
                SubtitleManager.subtitleMutex.unlock()
            }
        }
    }

    /**
     * Executes the download process for a media task.
     *
     * The subtitle-lock is ONLY used inside fetchVideoInfo for subtitle-only tasks.
     * Regular media downloads never acquire the subtitle lock, avoiding deadlocks.
     */
    suspend fun executeDownload(
        task: Task,
        videoInfo: VideoInfo,
        onProgressUpdate: (Float, String) -> Unit,
        appContext: Context = context,
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        val playlistItem = (task.type as? TypeInfo.Playlist)?.index ?: 0
        val sourcePlaylistUrl = if (playlistItem != 0) {
            (task.type as? TypeInfo.Playlist)?.playlistUrl ?: ""
        } else {
            ""
        }
        val isFallback = (task.type as? TypeInfo.Playlist)?.isFallback ?: false
        val fallbackPlaylistTitle = (task.type as? TypeInfo.Playlist)?.playlistTitle ?: ""

        val isAudioDownload = DownloadUtil.isAudioOnlyDownload(task.preferences, videoInfo)

        // ── Subtitle-only path ──────────────────────────────────────────────────
        val isSubtitleTask = task.preferences.skipDownload && task.preferences.downloadSubtitle
        if (isSubtitleTask) {
            val targetDir = OutputTemplateBuilder.resolveTargetDirectory(
                preferences = task.preferences,
                isAudioDownload = false,
                playlistItem = playlistItem,
                fallbackPlaylistTitle = fallbackPlaylistTitle,
                videoPlaylistTitle = if (playlistItem > 0) videoInfo.playlist else null,
                videoInfo = videoInfo,
                taskUrl = task.url
            )
            targetDir.mkdirs()

            val subtitleRes = SubtitleManager.downloadSubtitles(
                url = videoInfo.originalUrl ?: videoInfo.webpageUrl ?: task.url,
                videoInfo = videoInfo,
                preferences = task.preferences,
                destinationDir = targetDir,
                playlistIndex = playlistItem,
                onProgress = { progress ->
                    onProgressUpdate(progress.progress, progress.statusMessage)
                }
            )

            return@withContext when (subtitleRes) {
                is com.junkfood.seal.download.engine.subtitle.model.SubtitleDownloadResult.Success -> {
                    val paths = subtitleRes.downloadedFiles.map { it.absolutePath }
                    PostDownloadCoordinator.handleDownloadCompletion(
                        preferences = task.preferences,
                        videoInfo = videoInfo,
                        downloadPath = targetDir.absolutePath,
                        sdcardUri = task.preferences.sdcardUri,
                        playlistItem = playlistItem,
                        fallbackPlaylistTitle = fallbackPlaylistTitle,
                        discoveredPaths = paths,
                        appContext = appContext,
                    )
                }
                is com.junkfood.seal.download.engine.subtitle.model.SubtitleDownloadResult.Failure -> {
                    Result.failure(subtitleRes.error)
                }
            }
        }

        // ── Regular media download path ─────────────────────────────────────────
        val request = DownloadCommandBuilder.buildDownloadRequest(
            url = videoInfo.originalUrl ?: videoInfo.webpageUrl ?: task.url,
            videoInfo = videoInfo,
            preferences = task.preferences,
            isAudioDownload = isAudioDownload,
            playlistItem = playlistItem,
            playlistUrl = sourcePlaylistUrl,
            fallbackPlaylistTitle = fallbackPlaylistTitle,
            isFallback = isFallback,
            appContext = appContext,
        )

        var lastUpdateTime = 0L
        val discoveredPaths = mutableSetOf<String>()

        val downloadExecResult = runCatching {
            YoutubeDL.getInstance().execute(
                request = request,
                processId = task.id,
                callback = { progressPercentage, _, text ->
                    // Extract file path from this line (--newline guarantees one path per line)
                    val extractedPath = extractPathFromLine(text)
                    if (extractedPath != null) {
                        discoveredPaths.add(extractedPath)
                        Log.d(TAG, "Discovered path: $extractedPath")
                    }

                    val isMerging = text.contains("Merger", ignoreCase = true) ||
                            text.contains("[ffmpeg]", ignoreCase = true) ||
                            text.contains("Merging formats", ignoreCase = true) ||
                            text.contains("ExtractAudio", ignoreCase = true) ||
                            text.contains("VideoRemux", ignoreCase = true)

                    val effectiveProgress = when {
                        isMerging -> 0.95f
                        else -> (progressPercentage / 100f).coerceIn(0f, 0.99f)
                    }
                    val effectiveText = when {
                        isMerging -> "جاري دمج الصوت والفيديو..."
                        else -> text
                    }

                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastUpdateTime > 200L || isMerging) {
                        lastUpdateTime = currentTime
                        onProgressUpdate(effectiveProgress, effectiveText)
                    }
                }
            )
        }

        if (downloadExecResult.isFailure) {
            val error = downloadExecResult.exceptionOrNull()
            if (error is YoutubeDL.CanceledException) {
                return@withContext Result.failure(error)
            }

            // SponsorBlock post-processor error — media was already saved, continue
            if (task.preferences.sponsorBlock &&
                error?.message?.contains("SponsorBlock", ignoreCase = true) == true) {
                Log.w(TAG, "SponsorBlock failed (non-fatal), proceeding: ${error.message}")
                // Fall through to post-processing
            } else if (task.preferences.downloadSubtitle && !task.preferences.skipDownload &&
                (error?.message?.contains("subtitles", ignoreCase = true) == true ||
                 error?.message?.contains("subtitle", ignoreCase = true) == true ||
                 error?.message?.contains("429", ignoreCase = true) == true)) {
                // Subtitle failure during media download: retry without subtitles
                Log.w(TAG, "Subtitle download failed (${error?.message}), retrying media-only...")
                val fallbackPreferences = task.preferences.copy(downloadSubtitle = false)
                val fallbackRequest = DownloadCommandBuilder.buildDownloadRequest(
                    url = videoInfo.originalUrl ?: videoInfo.webpageUrl ?: task.url,
                    videoInfo = videoInfo,
                    preferences = fallbackPreferences,
                    isAudioDownload = isAudioDownload,
                    playlistItem = playlistItem,
                    playlistUrl = sourcePlaylistUrl,
                    fallbackPlaylistTitle = fallbackPlaylistTitle,
                    isFallback = isFallback,
                    appContext = appContext,
                )
                val retryResult = runCatching {
                    YoutubeDL.getInstance().execute(
                        request = fallbackRequest,
                        processId = task.id,
                        callback = { progressPercentage, _, text ->
                            val extractedPath = extractPathFromLine(text)
                            if (extractedPath != null) {
                                discoveredPaths.add(extractedPath)
                                Log.d(TAG, "Retry discovered path: $extractedPath")
                            }
                            val isMerging = text.contains("Merger", ignoreCase = true) ||
                                    text.contains("[ffmpeg]", ignoreCase = true) ||
                                    text.contains("Merging formats", ignoreCase = true)
                            val effectiveProgress = if (isMerging) 0.95f else (progressPercentage / 100f).coerceIn(0f, 0.99f)
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastUpdateTime > 200L || isMerging) {
                                lastUpdateTime = currentTime
                                onProgressUpdate(effectiveProgress, if (isMerging) "جاري دمج الصوت والفيديو..." else text)
                            }
                        }
                    )
                }
                if (retryResult.isFailure) {
                    return@withContext Result.failure(
                        retryResult.exceptionOrNull() ?: IllegalStateException("فشل التحميل")
                    )
                }
            } else {
                return@withContext Result.failure(error ?: IllegalStateException("فشل التحميل"))
            }
        }

        Log.d(TAG, "Download completed. Discovered ${discoveredPaths.size} path(s): $discoveredPaths")

        // Post-Download Processing Pipeline
        val basePath = OutputTemplateBuilder.resolveBaseDirectory(task.preferences, isAudioDownload)
        return@withContext PostDownloadCoordinator.handleDownloadCompletion(
            preferences = task.preferences,
            videoInfo = videoInfo,
            downloadPath = basePath,
            sdcardUri = task.preferences.sdcardUri,
            playlistItem = playlistItem,
            fallbackPlaylistTitle = fallbackPlaylistTitle,
            discoveredPaths = discoveredPaths.toList(),
            appContext = appContext,
            onProgress = { percent, msg ->
                val scaledProgress = 0.95f + (percent / 100f) * 0.04f
                onProgressUpdate(scaledProgress, msg)
            }
        )
    }

    /**
     * Executes custom command template.
     */
    suspend fun executeCustomCommand(
        task: Task,
        template: CommandTemplate,
        onProgressUpdate: (Float, String) -> Unit,
        appContext: Context = context,
    ): Result<YoutubeDLResponse> = withContext(Dispatchers.IO) {
        val urlList = task.url.split(Regex("[\n ]")).filter { it.isNotBlank() }
        val request = DownloadCommandBuilder.buildCustomCommandRequest(
            urlList = urlList,
            template = template,
            preferences = task.preferences,
            appContext = appContext,
        )

        return@withContext runCatching {
            YoutubeDL.getInstance().execute(
                request = request,
                processId = task.id,
                callback = { progressPercentage, _, text ->
                    onProgressUpdate(progressPercentage / 100f, text)
                }
            )
        }
    }
}
