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
import com.junkfood.seal.download.engine.resilience.SocialMediaFallbackHandler
import com.junkfood.seal.download.engine.subtitle.SubtitleManager
import com.junkfood.seal.util.DownloadUtil
import com.junkfood.seal.util.VideoInfo
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * DownloadTaskExecutor
 *
 * Executes individual download operations:
 * - Metadata & VideoInfo fetching with resilient retries and Cobalt fallback
 * - Media stream downloading with real-time progress parsing and merging state detection
 * - Custom yt-dlp command templates execution
 */
object DownloadTaskExecutor {

    private const val TAG = "DownloadTaskExecutor"

    private val jsonFormat =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
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

                val fetchRequest = DownloadCommandBuilder.buildInfoFetchRequest(
                    url = fetchUrl,
                    preferences = task.preferences,
                    playlistIndex = playlistIndex,
                    isFlatPlaylist = false,
                )

                val executionRes = runCatching {
                    val response = YoutubeDL.getInstance().execute(fetchRequest, task.id, null)
                    jsonFormat.decodeFromString<VideoInfo>(response.out)
                }

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

            // If yt-dlp failed, attempt emergency fallback for social media platforms
            if (result?.isFailure == true && SocialMediaFallbackHandler.isSocialMediaUrl(fetchUrl)) {
                val fallbackInfo = SocialMediaFallbackHandler.resolveFallbackVideoInfo(fetchUrl)
                if (fallbackInfo != null) {
                    return@withContext Result.success(fallbackInfo)
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
     */
    suspend fun executeDownload(
        task: Task,
        onProgressUpdate: (Float, String) -> Unit,
        appContext: Context = context,
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        val videoInfo = task.info ?: return@withContext Result.failure(
            IllegalStateException("بيانات المقطع غير متوفرة")
        )

        val playlistItem = (task.type as? TypeInfo.Playlist)?.index ?: 0
        val sourcePlaylistUrl = if (playlistItem != 0) {
            (task.type as? TypeInfo.Playlist)?.playlistUrl ?: ""
        } else {
            ""
        }
        val isFallback = (task.type as? TypeInfo.Playlist)?.isFallback ?: false
        val fallbackPlaylistTitle = (task.type as? TypeInfo.Playlist)?.playlistTitle ?: ""

        val isAudioDownload = DownloadUtil.isAudioOnlyDownload(task.preferences, videoInfo)

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

        val isSubtitleTask = task.preferences.skipDownload && task.preferences.downloadSubtitle
        var acquiredSubtitleLock = false

        try {
            if (isSubtitleTask) {
                SubtitleManager.subtitleMutex.lock()
                acquiredSubtitleLock = true
                delay(50L)
            }

            var lastUpdateTime = 0L
            val discoveredPaths = mutableSetOf<String>()
            val pathPatterns = listOf(
                Regex("""(?:Destination:\s+|Merging formats into\s+"?|Correcting container in\s+"?|Writing video subtitles to:\s+|Embedding subtitles in\s+"?|Moving file.+to\s+"?)([^"\r\n]+)"""),
                Regex("""\[download\]\s+(/[^\s]+\.[a-zA-Z0-9]+)\s+has already been downloaded"""),
                Regex("""\[download\]\s+(/storage/[^\s]+\.[a-zA-Z0-9]+)"""),
            )

            val downloadExecResult = runCatching {
                YoutubeDL.getInstance().execute(
                    request = request,
                    processId = task.id,
                    callback = { progressPercentage, _, text ->
                        for (pattern in pathPatterns) {
                            val match = pattern.find(text)
                            if (match != null) {
                                val captured = match.groupValues[1].trim().removeSurrounding("\"").removeSurrounding("'")
                                if (captured.isNotBlank() && (captured.startsWith("/") || captured.contains(":\\"))) {
                                    discoveredPaths.add(captured)
                                }
                            }
                        }

                        val isMerging = text.contains("Merger", ignoreCase = true) ||
                                text.contains("ffmpeg", ignoreCase = true) ||
                                text.contains("Postprocessor", ignoreCase = true) ||
                                text.contains("Merging", ignoreCase = true) ||
                                text.contains("ExtractAudio", ignoreCase = true)

                        val effectiveProgress = when {
                            isMerging -> 0.95f
                            else -> (progressPercentage / 100f).coerceIn(0f, 0.99f)
                        }
                        val effectiveText = if (isMerging) "جاري دمج الصوت والفيديو..." else text

                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastUpdateTime > 250L || isMerging) {
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
                // Handle SponsorBlock failure gracefully
                if (task.preferences.sponsorBlock && error?.message?.contains("SponsorBlock", ignoreCase = true) == true) {
                    Log.w(TAG, "SponsorBlock failed, proceeding with post-processing: ${error.message}")
                } else {
                    return@withContext Result.failure(error ?: IllegalStateException("فشل التحميل"))
                }
            }

            // Post-Download Processing Pipeline
            val basePath = OutputTemplateBuilder.resolveBaseDirectory(task.preferences, isAudioDownload)
            val finalPathResult = PostDownloadCoordinator.handleDownloadCompletion(
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

            return@withContext finalPathResult
        } finally {
            if (acquiredSubtitleLock) {
                SubtitleManager.subtitleMutex.unlock()
            }
        }
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
