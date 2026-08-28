package com.junkfood.seal.download.engine.postprocess

import android.content.Context
import android.util.Log
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.database.objects.DownloadedVideoInfo
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
 * 1. Output file discovery and validation  ← Now prioritizes yt-dlp reported paths
 * 2. SD card / SAF transfer
 * 3. MediaStore library scanning & indexing
 * 4. Download history database insertion
 *
 * CRITICAL FIX:
 * - discoveredPaths (from yt-dlp --newline output) are now the PRIMARY source of truth.
 * - MediaStorageScanner directory scan is ONLY used as a fallback when yt-dlp reported nothing.
 * - Merging post-processors (FFmpeg, Merger, ExtractAudio) produce a NEW file; we keep
 *   the LAST reported path from yt-dlp as the final output, not the first.
 * - .part, .ytdl, .tmp files are automatically excluded.
 */
object PostDownloadCoordinator {

    private const val TAG = "PostDownloadCoordinator"

    private val SUBTITLE_REGEX = Regex("(?i)\\.(lrc|vtt|srt|ass|json3|srv\\d?|ttml|sub|ssa)$")
    private val THUMBNAIL_REGEX = Regex("(?i)\\.(jpe?g|png|webp|bmp)$")
    private val TEMP_FILE_REGEX = Regex("(?i)\\.(part|ytdl|tmp)$")
    private val MEDIA_REGEX = Regex("(?i)\\.(mp4|mkv|webm|m4a|mp3|opus|flac|wav|ogg|m4b|mka|avi|mov|ts|3gp|m4v)$")

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

        val isSubtitleOnly = preferences.skipDownload && preferences.downloadSubtitle

        val fileName = preferences.newTitle.ifEmpty {
            val fn = videoInfo.filename ?: videoInfo.requestedDownloads?.firstOrNull()?.filename
            if (isSubtitleOnly && fn != null) fn.substringBeforeLast(".") else fn ?: videoInfo.title
        }

        Log.d(TAG, "handleDownloadCompletion: '$fileName' | discovered=${discoveredPaths.size} | subOnly=$isSubtitleOnly")
        if (discoveredPaths.isNotEmpty()) {
            Log.d(TAG, "Raw discoveredPaths: $discoveredPaths")
        }

        // 1. SD Card path — handled separately
        if (preferences.sdcard) {
            val movedPaths = moveFilesToSdcard(
                tempPath = appContext.getSdcardTempDir(videoInfo.id),
                sdcardUri = sdcardUri,
            ).getOrThrow()
            if (preferences.privateMode) return@runCatching emptyList()
            if (preferences.splitByChapter) insertSplitChapterIntoHistory(videoInfo, movedPaths)
            else insertInfoIntoDownloadHistory(videoInfo, movedPaths)
            return@runCatching movedPaths
        }

        // 2. Validate discoveredPaths — exclude temp/thumbnail/wrong-type files
        //    Keep the LATEST path of each final output file type (merger output replaces source)
        val validDiscovered = discoveredPaths
            .filter { path ->
                val f = File(path)
                val name = f.name
                !TEMP_FILE_REGEX.containsMatchIn(name) &&
                !THUMBNAIL_REGEX.containsMatchIn(name) &&
                f.exists() &&
                f.isFile &&
                f.length() > (if (isSubtitleOnly) 5L else 512L)
            }
            .distinctBy { File(it).name } // deduplicate by filename
            .sortedByDescending { File(it).lastModified() } // newest first (final merged file)

        Log.d(TAG, "Valid discovered after filtering: ${validDiscovered.size} → $validDiscovered")

        // 3. Separate subtitles from media
        val (subPaths, mediaPaths) = if (!isSubtitleOnly) {
            validDiscovered.partition { SUBTITLE_REGEX.containsMatchIn(it) }
        } else {
            Pair(validDiscovered, emptyList<String>())
        }

        // Register subtitles with MediaStore (fire-and-forget)
        subPaths.forEach { MediaStorageScanner.scanSingleFile(File(it)) }

        // 4. Determine final paths — discoveredPaths are authoritative
        var finalPaths: List<String>

        if (isSubtitleOnly) {
            finalPaths = if (subPaths.isNotEmpty()) subPaths else {
                Log.w(TAG, "No subtitle paths discovered, falling back to directory scan")
                fallbackDirectoryScan(fileName, downloadPath, isSubtitleOnly = true, videoId = videoInfo.id)
            }
        } else {
            // Media download: use yt-dlp-reported media paths (not subtitles)
            val reportedMedia = mediaPaths.filter { MEDIA_REGEX.containsMatchIn(it) }

            finalPaths = if (reportedMedia.isNotEmpty()) {
                Log.d(TAG, "Using yt-dlp reported media paths: $reportedMedia")
                reportedMedia.onEach { MediaStorageScanner.scanSingleFile(File(it)) }
            } else {
                Log.w(TAG, "No media paths from yt-dlp output. Falling back to directory scan.")
                fallbackDirectoryScan(fileName, downloadPath, isSubtitleOnly = false, videoId = videoInfo.id)
            }
        }

        // 5. Hard validation — fail honestly if no media was downloaded
        if (!isSubtitleOnly && finalPaths.isEmpty()) {
            throw IllegalStateException(
                "فشل التنزيل: لم يتم إنتاج ملف وسائط من قِبل أداة التنزيل في: $downloadPath\n" +
                "تأكد من توفر الرابط وصلاحية الوصول."
            )
        }

        // 6. Validate each found file actually exists and is non-empty
        val existingFinalPaths = finalPaths.filter { path ->
            val f = File(path)
            val ok = f.exists() && f.isFile && f.length() > (if (isSubtitleOnly) 5L else 512L)
            if (!ok) Log.w(TAG, "Path reported but file missing or empty: $path")
            ok
        }

        if (!isSubtitleOnly && existingFinalPaths.isEmpty() && finalPaths.isNotEmpty()) {
            throw IllegalStateException(
                "الملف أُبلغ عن تنزيله لكنه غير موجود على القرص: ${finalPaths.first()}"
            )
        }

        val processedPaths = if (isSubtitleOnly && existingFinalPaths.isEmpty() && finalPaths.isNotEmpty()) finalPaths else existingFinalPaths

        // 7. Database History Insertion
        if (preferences.privateMode) {
            emptyList()
        } else {
            if (preferences.splitByChapter) {
                insertSplitChapterIntoHistory(videoInfo, processedPaths)
            } else {
                insertInfoIntoDownloadHistory(videoInfo, processedPaths)
            }
            processedPaths
        }
    }

    /**
     * Fallback: scans the download directory for recently modified matching files.
     */
    private fun fallbackDirectoryScan(
        title: String,
        downloadPath: String,
        isSubtitleOnly: Boolean,
        videoId: String?,
        windowMinutes: Int = 10,
    ): List<String> {
        val result = MediaStorageScanner.scanAndRegister(
            title = title,
            downloadDir = downloadPath,
            isSubtitleOnly = isSubtitleOnly,
            videoId = videoId,
            windowMinutes = windowMinutes,
        )
        if (result.isEmpty()) {
            // Try parent directory as additional fallback
            val parentDir = File(downloadPath).parentFile?.absolutePath
            if (parentDir != null && parentDir != downloadPath) {
                Log.w(TAG, "Trying parent directory: $parentDir")
                return MediaStorageScanner.scanAndRegister(
                    title = title,
                    downloadDir = parentDir,
                    isSubtitleOnly = isSubtitleOnly,
                    videoId = videoId,
                    windowMinutes = windowMinutes,
                )
            }
        }
        return result
    }

    /**
     * Last-resort file finder: returns the most recently modified media file
     * in the given directory within the specified time window.
     */
    private fun findMostRecentMediaFile(downloadPath: String, windowMinutes: Int): String? {
        val dir = File(downloadPath)
        if (!dir.exists()) return null
        val cutoff = System.currentTimeMillis() - (windowMinutes * 60_000L)
        return dir.walkTopDown()
            .filter { f ->
                f.isFile &&
                MEDIA_REGEX.containsMatchIn(f.name) &&
                !TEMP_FILE_REGEX.containsMatchIn(f.name) &&
                f.length() > 512L &&
                f.lastModified() >= cutoff
            }
            .maxByOrNull { it.lastModified() }
            ?.absolutePath
    }

    private fun findMostRecentSubtitleFile(downloadPath: String, windowMinutes: Int): String? {
        val dir = File(downloadPath)
        if (!dir.exists()) return null
        val cutoff = System.currentTimeMillis() - (windowMinutes * 60_000L)
        return dir.walkTopDown()
            .filter { f ->
                f.isFile &&
                SUBTITLE_REGEX.containsMatchIn(f.name) &&
                !TEMP_FILE_REGEX.containsMatchIn(f.name) &&
                f.length() > 5L &&
                f.lastModified() >= cutoff
            }
            .maxByOrNull { it.lastModified() }
            ?.absolutePath
    }

    private fun insertInfoIntoDownloadHistory(
        videoInfo: VideoInfo,
        filePaths: List<String>,
    ): List<String> = filePaths.onEach {
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
