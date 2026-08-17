package com.junkfood.seal.util

import android.media.MediaCodecList
import android.os.Build
import android.util.Log
import androidx.annotation.CheckResult
import com.junkfood.seal.App
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.CustomCommandRunner
import com.junkfood.seal.CustomCommandRunner.onProcessEnded
import com.junkfood.seal.CustomCommandRunner.onProcessStarted
import com.junkfood.seal.CustomCommandRunner.onTaskEnded
import com.junkfood.seal.CustomCommandRunner.onTaskError
import com.junkfood.seal.CustomCommandRunner.onTaskStarted
import com.junkfood.seal.CustomCommandRunner.toNotificationId
import com.junkfood.seal.R
import com.junkfood.seal.database.objects.CommandTemplate
import com.junkfood.seal.database.objects.DownloadedVideoInfo
import com.junkfood.seal.download.engine.DownloadTaskExecutor
import com.junkfood.seal.download.engine.builder.DownloadCommandBuilder
import com.junkfood.seal.download.engine.builder.FormatSelectorBuilder
import com.junkfood.seal.download.engine.builder.NetworkOptionBuilder
import com.junkfood.seal.download.engine.builder.OutputTemplateBuilder
import com.junkfood.seal.download.engine.postprocess.PostDownloadCoordinator
import com.junkfood.seal.download.engine.resilience.SocialMediaFallbackHandler
import com.junkfood.seal.ui.page.settings.network.Cookie
import com.junkfood.seal.util.FileUtil.getConfigFile
import com.junkfood.seal.util.PreferenceUtil.getBoolean
import com.junkfood.seal.util.PreferenceUtil.getInt
import com.junkfood.seal.util.PreferenceUtil.getString
import com.junkfood.seal.util.PreferenceUtil.updateBoolean
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object DownloadUtil {

    private val jsonFormat =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }

    private const val TAG = "DownloadUtil"

    const val BASENAME = OutputTemplateBuilder.BASENAME
    const val EXTENSION = OutputTemplateBuilder.EXTENSION
    const val OUTPUT_TEMPLATE_DEFAULT = OutputTemplateBuilder.OUTPUT_TEMPLATE_DEFAULT
    const val OUTPUT_TEMPLATE_ID = OutputTemplateBuilder.OUTPUT_TEMPLATE_ID

    @CheckResult
    fun getPlaylistOrVideoInfo(
        playlistURL: String,
        downloadPreferences: DownloadPreferences = DownloadPreferences.createFromPreferences(),
    ): Result<YoutubeDLInfo> =
        YoutubeDL.runCatching {
            ToastUtil.makeToastSuspend(context.getString(R.string.fetching_playlist_info))
            val request = DownloadCommandBuilder.buildInfoFetchRequest(
                url = playlistURL,
                preferences = downloadPreferences,
                playlistIndex = null,
                isFlatPlaylist = true,
            )
            execute(request, playlistURL).out.run {
                val playlistInfo = jsonFormat.decodeFromString<PlaylistResult>(this)
                if (playlistInfo.type != "playlist") {
                    jsonFormat.decodeFromString<VideoInfo>(this)
                } else playlistInfo
            }
        }

    @CheckResult
    fun fetchVideoInfoFromUrl(
        url: String,
        playlistIndex: Int? = null,
        taskKey: String? = null,
        preferences: DownloadPreferences = DownloadPreferences.createFromPreferences(),
    ): Result<VideoInfo> {
        val request = DownloadCommandBuilder.buildInfoFetchRequest(
            url = url,
            preferences = preferences,
            playlistIndex = playlistIndex,
            isFlatPlaylist = false,
        )

        val result = runCatching {
            val response: YoutubeDLResponse =
                YoutubeDL.getInstance().execute(request, taskKey, null)
            jsonFormat.decodeFromString<VideoInfo>(response.out)
        }

        if (result.isFailure && SocialMediaFallbackHandler.isSocialMediaUrl(url)) {
            val fallback = kotlinx.coroutines.runBlocking {
                SocialMediaFallbackHandler.resolveFallbackVideoInfo(url)
            }
            if (fallback != null) {
                return Result.success(fallback)
            }
        }

        return result
    }

    @Serializable
    data class DownloadPreferences(
        val skipDownload: Boolean,
        val extractAudio: Boolean,
        val createThumbnail: Boolean,
        val downloadPlaylist: Boolean,
        val playlistNumbering: Boolean,
        val subdirectoryExtractor: Boolean,
        val subdirectoryPlaylistTitle: Boolean,
        val commandDirectory: String,
        val downloadSubtitle: Boolean,
        val embedSubtitle: Boolean,
        val keepSubtitle: Boolean,
        val subtitleLanguage: String,
        val autoSubtitle: Boolean,
        val autoTranslatedSubtitles: Boolean,
        val convertSubtitle: Int,
        val concurrentFragments: Int,
        val sponsorBlock: Boolean,
        val sponsorBlockCategory: String,
        val cookies: Boolean,
        val aria2c: Boolean,
        val useCustomAudioPreset: Boolean,
        val audioFormat: Int,
        val audioQuality: Int,
        val convertAudio: Boolean,
        val formatSorting: Boolean,
        val sortingFields: String,
        val audioConvertFormat: Int,
        val videoFormat: Int,
        val formatIdString: String,
        val videoResolution: Int,
        val privateMode: Boolean,
        val rateLimit: Boolean,
        val maxDownloadRate: String,
        val privateDirectory: Boolean,
        val cropArtwork: Boolean,
        val sdcard: Boolean,
        val sdcardUri: String,
        val embedThumbnail: Boolean,
        val videoClips: List<VideoClip>,
        val splitByChapter: Boolean,
        val debug: Boolean,
        val proxy: Boolean,
        val proxyUrl: String,
        val newTitle: String,
        val userAgentString: String,
        val outputTemplate: String,
        val useDownloadArchive: Boolean,
        val embedMetadata: Boolean,
        val restrictFilenames: Boolean,
        val supportAv1HardwareDecoding: Boolean,
        val forceIpv4: Boolean,
        val mergeAudioStream: Boolean,
        val mergeToMkv: Boolean,
        val removeMusic: Boolean = false,
    ) {
        companion object {
            val EMPTY =
                DownloadPreferences(
                    skipDownload = false,
                    extractAudio = false,
                    createThumbnail = false,
                    downloadPlaylist = false,
                    playlistNumbering = false,
                    subdirectoryExtractor = false,
                    subdirectoryPlaylistTitle = false,
                    commandDirectory = "",
                    downloadSubtitle = false,
                    embedSubtitle = false,
                    keepSubtitle = false,
                    subtitleLanguage = "",
                    autoSubtitle = false,
                    autoTranslatedSubtitles = false,
                    convertSubtitle = 0,
                    concurrentFragments = 0,
                    sponsorBlock = false,
                    sponsorBlockCategory = "",
                    cookies = false,
                    aria2c = false,
                    audioFormat = 0,
                    audioQuality = 0,
                    convertAudio = false,
                    formatSorting = false,
                    sortingFields = "",
                    audioConvertFormat = 0,
                    videoFormat = 0,
                    formatIdString = "",
                    videoResolution = 0,
                    privateMode = false,
                    rateLimit = false,
                    maxDownloadRate = "",
                    privateDirectory = false,
                    cropArtwork = false,
                    sdcard = false,
                    sdcardUri = "",
                    embedThumbnail = false,
                    videoClips = emptyList(),
                    splitByChapter = false,
                    debug = false,
                    proxy = false,
                    proxyUrl = "",
                    newTitle = "",
                    userAgentString = "",
                    outputTemplate = "",
                    useDownloadArchive = false,
                    embedMetadata = false,
                    restrictFilenames = false,
                    supportAv1HardwareDecoding = false,
                    forceIpv4 = false,
                    mergeAudioStream = false,
                    mergeToMkv = false,
                    useCustomAudioPreset = false,
                    removeMusic = false,
                )

            fun createFromPreferences(): DownloadPreferences {
                val downloadSubtitle = SUBTITLE.getBoolean()
                val embedSubtitle = EMBED_SUBTITLE.getBoolean()
                return DownloadPreferences(
                    skipDownload = false,
                    extractAudio = EXTRACT_AUDIO.getBoolean(),
                    createThumbnail = THUMBNAIL.getBoolean(),
                    downloadPlaylist = PLAYLIST.getBoolean(),
                    playlistNumbering = PLAYLIST_NUMBERING.getBoolean(),
                    subdirectoryExtractor = SUBDIRECTORY_EXTRACTOR.getBoolean(),
                    subdirectoryPlaylistTitle = SUBDIRECTORY_PLAYLIST_TITLE.getBoolean(),
                    commandDirectory = COMMAND_DIRECTORY.getString(),
                    downloadSubtitle = downloadSubtitle,
                    embedSubtitle = embedSubtitle,
                    keepSubtitle = KEEP_SUBTITLE_FILES.getBoolean(),
                    subtitleLanguage = SUBTITLE_LANGUAGE.getString(),
                    autoSubtitle = AUTO_SUBTITLE.getBoolean(),
                    autoTranslatedSubtitles = AUTO_TRANSLATED_SUBTITLES.getBoolean(),
                    convertSubtitle = CONVERT_SUBTITLE.getInt(),
                    concurrentFragments = CONCURRENT.getInt(),
                    sponsorBlock = SPONSORBLOCK.getBoolean(),
                    sponsorBlockCategory = PreferenceUtil.getSponsorBlockCategories(),
                    cookies = COOKIES.getBoolean(),
                    aria2c = ARIA2C.getBoolean(),
                    useCustomAudioPreset = USE_CUSTOM_AUDIO_PRESET.getBoolean(),
                    audioFormat = AUDIO_FORMAT.getInt(),
                    audioQuality = AUDIO_QUALITY.getInt(),
                    convertAudio = AUDIO_CONVERT.getBoolean(),
                    formatSorting = FORMAT_SORTING.getBoolean(),
                    sortingFields = SORTING_FIELDS.getString(),
                    audioConvertFormat = PreferenceUtil.getAudioConvertFormat(),
                    videoFormat = PreferenceUtil.getVideoFormat(),
                    formatIdString = "",
                    videoResolution = PreferenceUtil.getVideoResolution(),
                    privateMode = PRIVATE_MODE.getBoolean(),
                    rateLimit = RATE_LIMIT.getBoolean(),
                    maxDownloadRate = PreferenceUtil.getMaxDownloadRate(),
                    privateDirectory = PRIVATE_DIRECTORY.getBoolean(),
                    cropArtwork = CROP_ARTWORK.getBoolean(),
                    sdcard = SDCARD_DOWNLOAD.getBoolean(),
                    sdcardUri = SDCARD_URI.getString(),
                    embedThumbnail = EMBED_THUMBNAIL.getBoolean(),
                    videoClips = emptyList(),
                    splitByChapter = false,
                    debug = DEBUG.getBoolean(),
                    proxy = PROXY.getBoolean(),
                    proxyUrl = PROXY_URL.getString(),
                    newTitle = "",
                    userAgentString =
                        USER_AGENT_STRING.run { if (USER_AGENT.getBoolean()) getString() else "" },
                    outputTemplate = OUTPUT_TEMPLATE.getString(),
                    useDownloadArchive = DOWNLOAD_ARCHIVE.getBoolean(),
                    embedMetadata = EMBED_METADATA.getBoolean(),
                    restrictFilenames = RESTRICT_FILENAMES.getBoolean(),
                    supportAv1HardwareDecoding = checkIfAv1HardwareAccelerated(),
                    forceIpv4 = FORCE_IPV4.getBoolean(),
                    mergeAudioStream = false,
                    mergeToMkv =
                        (downloadSubtitle && embedSubtitle) || MERGE_OUTPUT_MKV.getBoolean(),
                    removeMusic = REMOVE_MUSIC.getBoolean(),
                )
            }
        }
    }

    @CheckResult
    fun getCookieListFromDatabase(): Result<List<Cookie>> =
        NetworkOptionBuilder.getCookieListFromDatabase()

    @CheckResult
    fun getCookiesContentFromDatabase(): Result<String> =
        NetworkOptionBuilder.getCookiesContentFromDatabase()

    @CheckResult
    fun DownloadPreferences.toFormatSorter(): String =
        FormatSelectorBuilder.toFormatSorter(this)

    fun isAudioOnlyDownload(preferences: DownloadPreferences, videoInfo: VideoInfo): Boolean {
        if (preferences.skipDownload) return false
        if (preferences.extractAudio) return true
        val fmtId = preferences.formatIdString
        if (fmtId.isNotEmpty()) {
            val selectedFmts = fmtId.split("+")
            val formats = videoInfo.formats.orEmpty()
            val hasVideo = selectedFmts.any { id ->
                formats.find { it.formatId.toString() == id }?.containsVideo() == true
            }
            val hasAudio = selectedFmts.any { id ->
                formats.find { it.formatId.toString() == id }?.isAudioOnly() == true
            }
            if (!hasVideo && hasAudio) return true
        }
        return videoInfo.vcodec == "none" && videoInfo.formats.orEmpty().none { it.containsVideo() }
    }

    @CheckResult
    fun downloadVideo(
        videoInfo: VideoInfo? = null,
        playlistUrl: String = "",
        playlistItem: Int = 0,
        taskId: String,
        downloadPreferences: DownloadPreferences,
        skipDownload: Boolean = false,
        isFallback: Boolean = false,
        fallbackPlaylistTitle: String = "",
        progressCallback: ((Float, Long, String) -> Unit)?,
    ): Result<List<String>> {
        if (videoInfo == null) {
            return Result.failure(Throwable(context.getString(R.string.fetch_info_error_msg)))
        }

        val url = videoInfo.originalUrl ?: videoInfo.webpageUrl ?: playlistUrl.ifEmpty {
            return Result.failure(Throwable(context.getString(R.string.fetch_info_error_msg)))
        }

        val isAudio = isAudioOnlyDownload(downloadPreferences, videoInfo)
        val request = DownloadCommandBuilder.buildDownloadRequest(
            url = url,
            videoInfo = videoInfo,
            preferences = downloadPreferences,
            isAudioDownload = isAudio,
            playlistItem = playlistItem,
            playlistUrl = playlistUrl,
            fallbackPlaylistTitle = fallbackPlaylistTitle,
            isFallback = isFallback,
            appContext = context,
        )

        val execResult = runCatching {
            YoutubeDL.getInstance().execute(
                request = request,
                processId = taskId,
                callback = { progress, eta, text ->
                    progressCallback?.invoke(progress, eta, text)
                }
            )
        }

        if (execResult.isFailure) {
            val th = execResult.exceptionOrNull()
            if (downloadPreferences.sponsorBlock && th?.message?.contains("SponsorBlock", ignoreCase = true) == true) {
                // Continue to post-processing
            } else {
                return Result.failure(th ?: Throwable("Download failed"))
            }
        }

        val basePath = OutputTemplateBuilder.resolveBaseDirectory(downloadPreferences, isAudio)
        return kotlinx.coroutines.runBlocking {
            PostDownloadCoordinator.handleDownloadCompletion(
                preferences = downloadPreferences,
                videoInfo = videoInfo,
                downloadPath = basePath,
                sdcardUri = downloadPreferences.sdcardUri,
                playlistItem = playlistItem,
                fallbackPlaylistTitle = fallbackPlaylistTitle,
                appContext = context,
            )
        }
    }

    @CheckResult
    fun executeCustomCommandTask(
        urlString: String,
        taskId: String,
        template: CommandTemplate,
        preferences: DownloadPreferences,
        progressCallback: ((Float, Long, String) -> Unit),
    ): Result<YoutubeDLResponse> {
        val urlList = urlString.split(Regex("[\n ]")).filter { it.isNotBlank() }
        val request = DownloadCommandBuilder.buildCustomCommandRequest(
            urlList = urlList,
            template = template,
            preferences = preferences,
            appContext = context,
        )

        return runCatching {
            YoutubeDL.getInstance().execute(
                request = request,
                processId = taskId,
                callback = { progress, eta, text ->
                    progressCallback(progress, eta, text)
                }
            )
        }
    }

    suspend fun executeCommandInBackground(
        url: String,
        template: CommandTemplate = PreferenceUtil.getTemplate(),
        downloadPreferences: DownloadPreferences = DownloadPreferences.createFromPreferences(),
    ) {
        val taskId = CustomCommandRunner.makeKey(url = url, templateName = template.name)
        val notificationId = taskId.toNotificationId()
        val urlList = url.split(Regex("[\n ]")).filter { it.isNotBlank() }

        ToastUtil.makeToastSuspend(context.getString(R.string.start_execute))
        val request = DownloadCommandBuilder.buildCustomCommandRequest(
            urlList = urlList,
            template = template,
            preferences = downloadPreferences,
            appContext = context,
        )

        onProcessStarted()
        withContext(Dispatchers.Main) { onTaskStarted(template, url) }
        runCatching {
            val response = YoutubeDL.getInstance().execute(request = request, processId = taskId) {
                progress, _, text ->
                NotificationUtil.makeNotificationForCustomCommand(
                    notificationId = notificationId,
                    taskId = taskId,
                    progress = progress.toInt(),
                    templateName = template.name,
                    taskUrl = url,
                    text = text,
                )
                CustomCommandRunner.updateTaskOutput(
                    template = template,
                    url = url,
                    line = text,
                    progress = progress,
                )
            }
            onTaskEnded(template, url, response.out + "\n" + response.err)
        }.onFailure {
            it.printStackTrace()
            if (it is YoutubeDL.CanceledException) return@onFailure
            it.message.run {
                if (isNullOrEmpty()) onTaskEnded(template, url)
                else onTaskError(this, template, url)
            }
        }
        onProcessEnded()
    }

    private fun checkIfAv1HardwareAccelerated(): Boolean {
        if (PreferenceUtil.containsKey(AV1_HARDWARE_ACCELERATED)) {
            return AV1_HARDWARE_ACCELERATED.getBoolean()
        } else {
            val res =
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    false
                } else {
                    MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
                        info.supportedTypes.any { it.equals("video/av01", ignoreCase = true) } &&
                            info.isHardwareAccelerated
                    }
                }
            AV1_HARDWARE_ACCELERATED.updateBoolean(res)
            return res
        }
    }
}
