package com.junkfood.seal.download.engine.postprocess

import android.content.Context
import android.util.Log
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.database.objects.DownloadOperation
import com.junkfood.seal.database.objects.DownloadedVideoInfo
import com.junkfood.seal.download.Task
import com.junkfood.seal.download.engine.builder.OutputTemplateBuilder
import com.junkfood.seal.util.DatabaseUtil
import com.junkfood.seal.util.DownloadUtil
import com.junkfood.seal.util.DownloadUtil.DownloadPreferences
import com.junkfood.seal.util.FileUtil
import com.junkfood.seal.util.FileUtil.getFileName
import com.junkfood.seal.util.FileUtil.getSdcardTempDir
import com.junkfood.seal.util.FileUtil.moveFilesToSdcard
import com.junkfood.seal.util.VideoInfo
import com.junkfood.seal.util.toHttpsUrl
import java.io.File

/**
 * PostDownloadCoordinator
 *
 * Orchestrates the complete post-download pipeline:
 * 1. Output file validation (detects 0-byte or skipped downloads)
 * 2. Vocal isolation / music removal (if requested)
 * 3. SD card / SAF transfer
 * 4. MediaStore library scanning & indexing
 * 5. Download history database insertion
 */
object PostDownloadCoordinator {

    private const val TAG = "PostDownloadCoordinator"

    /**
     * Executes the post-download pipeline and returns the list of final media file paths.
     */
    suspend fun handleDownloadCompletion(
        preferences: DownloadPreferences,
        videoInfo: VideoInfo,
        downloadPath: String,
        sdcardUri: String,
        playlistItem: Int = 0,
        fallbackPlaylistTitle: String = "",
        discoveredPaths: List<String> = emptyList(),
        appContext: Context = context,
        onProgress: ((Float, String) -> Unit)? = null,
    ): Result<List<String>> = runCatching {
        val fileName = preferences.newTitle.ifEmpty {
            val fn = videoInfo.filename ?: videoInfo.requestedDownloads?.firstOrNull()?.filename
            if (preferences.skipDownload && fn != null) {
                fn.substringBeforeLast(".")
            } else {
                fn ?: videoInfo.title
            }
        }

        val targetScanDir = if (preferences.skipDownload && preferences.downloadSubtitle && playlistItem != 0 && preferences.commandDirectory.isBlank()) {
            val playlistName = fallbackPlaylistTitle.ifEmpty { videoInfo.playlist.orEmpty() }.ifEmpty { "Playlist" }
            "$downloadPath/[Subtitles] ${FileUtil.cleanFileName(playlistName)}"
        } else {
            downloadPath
        }

        Log.d(TAG, "handleDownloadCompletion for '$fileName' in '$targetScanDir' (discovered: ${discoveredPaths.size})")

        // 1. SD Card Handling
        if (preferences.sdcard) {
            val movedPaths = moveFilesToSdcard(
                tempPath = appContext.getSdcardTempDir(videoInfo.id),
                sdcardUri = sdcardUri,
            ).getOrThrow()

            if (preferences.privateMode) {
                return@runCatching emptyList()
            }
            if (preferences.splitByChapter) {
                insertSplitChapterIntoHistory(videoInfo, movedPaths)
            } else {
                insertInfoIntoDownloadHistory(videoInfo, movedPaths)
            }
            return@runCatching movedPaths
        }

        // 2. Resolve final paths (First check discovered paths from yt-dlp execution stream)
        val validDiscovered = discoveredPaths.filter { path ->
            val f = File(path)
            f.exists() && f.isFile && f.length() > (if (preferences.skipDownload) 5L else 512L)
        }

        var finalPaths = if (validDiscovered.isNotEmpty()) {
            validDiscovered.forEach { MediaStorageScanner.scanSingleFile(File(it)) }
            validDiscovered
        } else {
            MediaStorageScanner.scanAndRegister(
                title = fileName,
                downloadDir = targetScanDir,
                isSubtitleOnly = preferences.skipDownload,
                videoId = videoInfo.id,
            )
        }

        // 3. Validation for media downloads
        if (!preferences.skipDownload) {
            if (finalPaths.isEmpty()) {
                throw IllegalStateException("لم يتم العثور على الملفات المحملة. قد يكون التنزيل قد تخطى الملف أو فشل.")
            }
            val firstFile = File(finalPaths.first())
            if (!firstFile.exists() || firstFile.length() == 0L) {
                throw IllegalStateException("الملف المحمل تالف أو فارغ (0 بايت).")
            }
        }

        // 4. Vocal Isolation / Music Removal
        if (preferences.removeMusic && !preferences.skipDownload && finalPaths.isNotEmpty()) {
            val isAudioOnly = DownloadUtil.isAudioOnlyDownload(preferences, videoInfo)
            finalPaths = VocalIsolationProcessor.removeMusicFromFiles(
                filePaths = finalPaths,
                isAudioOnly = isAudioOnly,
                onProgress = onProgress,
            )
        }

        // 5. Database History Insertion
        if (preferences.privateMode) {
            emptyList()
        } else {
            if (preferences.splitByChapter) {
                insertSplitChapterIntoHistory(videoInfo, finalPaths)
            } else {
                insertInfoIntoDownloadHistory(videoInfo, finalPaths)
            }
            finalPaths
        }
    }

    private fun insertInfoIntoDownloadHistory(
        videoInfo: VideoInfo,
        filePaths: List<String>,
    ): List<String> =
        filePaths.onEach {
            DatabaseUtil.insertInfo(videoInfo.toDownloadedVideoInfo(videoPath = it))
        }

    private fun insertSplitChapterIntoHistory(videoInfo: VideoInfo, filePaths: List<String>) =
        filePaths.onEach {
            DatabaseUtil.insertInfo(
                videoInfo.toDownloadedVideoInfo(videoPath = it).copy(videoTitle = it.getFileName())
            )
        }

    private fun VideoInfo.toDownloadedVideoInfo(
        id: Int = 0,
        videoPath: String,
    ): DownloadedVideoInfo =
        DownloadedVideoInfo(
            id = id,
            videoTitle = title,
            videoAuthor = uploader ?: channel ?: uploaderId.toString(),
            videoUrl = webpageUrl ?: originalUrl.toString(),
            thumbnailUrl = thumbnail.toHttpsUrl(),
            videoPath = videoPath,
            extractor = extractorKey,
        )
}
