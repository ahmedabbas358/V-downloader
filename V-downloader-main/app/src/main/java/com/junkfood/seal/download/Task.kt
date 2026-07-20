package com.junkfood.seal.download

import com.junkfood.seal.database.objects.CommandTemplate
import com.junkfood.seal.download.Task.TypeInfo
import com.junkfood.seal.download.Task.ViewState
import com.junkfood.seal.util.DownloadUtil
import com.junkfood.seal.util.Format
import com.junkfood.seal.util.VideoInfo
import com.junkfood.seal.util.toHttpsUrl
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.math.roundToInt

private val TypeInfo.id: String
    get() =
        when (this) {
            is TypeInfo.CustomCommand -> "${template.id}_${template.name}"
            is TypeInfo.Playlist -> "$index"
            TypeInfo.URL -> ""
        }

private fun makeId(
    url: String,
    type: TypeInfo,
    preferences: DownloadUtil.DownloadPreferences,
): String = "${url}_${type.id}_${preferences.hashCode()}_${UUID.randomUUID()}"

@Serializable
data class Task(
    val url: String,
    val type: TypeInfo = TypeInfo.URL,
    val preferences: DownloadUtil.DownloadPreferences,
    val timeCreated: Long = System.currentTimeMillis(),
    val id: String = makeId(url, type, preferences),
) : Comparable<Task> {

    override fun compareTo(other: Task): Int {
        return timeCreated.compareTo(other.timeCreated)
    }

    @Serializable
    sealed interface TypeInfo {

        @Serializable data class Playlist(
            val index: Int = 0,
            val playlistTitle: String = "",
            val playlistUrl: String = "",
            val isFallback: Boolean = false,
            val retryCount: Int = 0,
        ) : TypeInfo

        @Serializable data class CustomCommand(val template: CommandTemplate) : TypeInfo

        @Serializable data object URL : TypeInfo
    }

    @Serializable
    data class State(
        val downloadState: DownloadState,
        val videoInfo: VideoInfo?,
        val viewState: ViewState,
    )

    @Serializable
    sealed interface DownloadState : Comparable<DownloadState> {

        interface Cancelable {
            val job: Job
            val taskId: String
            val action: RestartableAction
        }

        interface Restartable {
            val action: RestartableAction
        }

        @Serializable data object Idle : DownloadState

        @Serializable
        data class FetchingInfo(
            @Transient override val job: Job = Job(),
            override val taskId: String,
        ) : DownloadState, Cancelable {
            override val action: RestartableAction = RestartableAction.FetchInfo
        }

        @Serializable data object ReadyWithInfo : DownloadState

        @Serializable
        data class Running(
            @Transient override val job: Job = Job(),
            override val taskId: String,
            val progress: Float = PROGRESS_INDETERMINATE,
            val progressText: String = "",
        ) : DownloadState, Cancelable {
            override val action: RestartableAction = RestartableAction.Download
        }

        @Serializable
        data class Canceled(override val action: RestartableAction, val progress: Float? = null, val isPaused: Boolean = false) :
            DownloadState, Restartable

        @Serializable
        data class Error(
            @Transient val throwable: Throwable = Throwable(),
            override val action: RestartableAction,
        ) : DownloadState, Restartable

        @Serializable data class Completed(val filePath: String?) : DownloadState

        override fun compareTo(other: DownloadState): Int {
            return ordinal - other.ordinal
        }

        private val ordinal: Int
            get() =
                when (this) {
                    is Canceled -> 4
                    is Error -> 5
                    is Completed -> 6
                    Idle -> 3
                    is FetchingInfo -> 2
                    ReadyWithInfo -> 1
                    is Running -> 0
                }
    }

    @Serializable
    sealed interface RestartableAction {
        @Serializable data object FetchInfo : RestartableAction

        @Serializable data object Download : RestartableAction
    }

    @Serializable
    data class ViewState(
        val url: String = "https://www.example.com",
        val title: String = "",
        val uploader: String = "",
        val extractorKey: String = "",
        val duration: Int = 0,
        val fileSizeApprox: Double = .0,
        val thumbnailUrl: String? = null,
        val videoFormats: List<Format>? = null,
        val audioOnlyFormats: List<Format>? = null,
    ) {
        companion object {
            fun fromVideoInfo(info: VideoInfo): ViewState {
                // Primary: use requestedFormats / requestedDownloads (only available after full download-info fetch)
                val primaryFormats =
                    info.requestedFormats
                        ?: info.requestedDownloads?.map { it.toFormat() }

                // Fallback: use all available formats from yt-dlp JSON for non-YouTube platforms
                val allAvailableFormats = info.availableFormats()

                val formats = if (!primaryFormats.isNullOrEmpty()) primaryFormats else allAvailableFormats

                val videoFormats = formats.filter { it.containsVideo() }
                val audioOnlyFormats = formats.filter { it.isAudioOnly() }

                // Estimate file size: use known size first, then approx, then sum from formats
                val fileSizeEstimate: Double = info.fileSize
                    ?: info.fileSizeApprox
                    ?: allAvailableFormats
                        .filter { it.containsVideo() || it.isAudioOnly() }
                        .maxByOrNull { it.effectiveBitrate }
                        ?.estimatedSizeBytes()
                    ?: .0

                // Duration: prefer numeric, fall back to durationString parsing
                val durationSeconds: Int = info.duration?.roundToInt()
                    ?: parseDurationString(info.durationString)
                    ?: 0

                return ViewState(
                    url = info.webpageUrl ?: info.originalUrl.toString(),
                    title = info.title,
                    uploader = info.uploader ?: info.channel ?: info.uploaderId?.takeIf { it.isNotEmpty() } ?: "",
                    extractorKey = info.extractorKey,
                    duration = durationSeconds,
                    thumbnailUrl = info.thumbnail.toHttpsUrl(),
                    fileSizeApprox = fileSizeEstimate,
                    videoFormats = videoFormats.ifEmpty { allAvailableFormats.filter { it.containsVideo() } },
                    audioOnlyFormats = audioOnlyFormats.ifEmpty { allAvailableFormats.filter { it.isAudioOnly() } },
                )
            }

            /** Parse duration strings like "1:23:45" or "12:34" into total seconds */
            private fun parseDurationString(durationString: String?): Int? {
                if (durationString.isNullOrBlank()) return null
                return try {
                    val parts = durationString.trim().split(":")
                    when (parts.size) {
                        2 -> parts[0].toInt() * 60 + parts[1].toInt()
                        3 -> parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()
                        else -> null
                    }
                } catch (_: NumberFormatException) { null }
            }
        }
    }

    companion object {
        private const val PROGRESS_INDETERMINATE = -1f
    }
}
