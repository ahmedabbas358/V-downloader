package com.junkfood.seal.util

import kotlin.math.roundToInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed interface YoutubeDLInfo

@Serializable
data class VideoInfo(
    val id: String = "",
    val title: String = "",
    val formats: List<Format>? = emptyList(),
    //    val thumbnails: List<Thumbnail> = emptyList(),
    val thumbnail: String? = null,
    val description: String? = null,
    val uploader: String? = null,
    @SerialName("uploader_id") val uploaderId: String? = null,
    val subtitles: Map<String, List<SubtitleFormat>> = emptyMap(),
    @SerialName("automatic_captions")
    val automaticCaptions: Map<String, List<SubtitleFormat>> = emptyMap(),
    //    @SerialName("uploader_id") val uploaderId: String? = null,
    //    @SerialName("uploader_url") val uploaderUrl: String? = null,
    //    @SerialName("channel_id") val channelId: Int? = null,
    //    @SerialName("channel_url") val channelUrl: String? = null,
    val duration: Double? = null,
    @SerialName("view_count") val viewCount: Long? = null,
    @SerialName("webpage_url") val webpageUrl: String? = null,
    //    @SerialName("categories") val categories: List<String> = emptyList(),
    val tags: List<String>? = emptyList(),
    @SerialName("live_status") val liveStatus: String? = null,
    //    @SerialName("release_timestamp") val releaseTimestamp: Int? = null,
    @SerialName("comment_count") val commentCount: Int? = null,
    val chapters: List<Chapter>? = null,
    @SerialName("like_count") val likeCount: Int? = null,
    val channel: String? = null,
    //    @SerialName("channel_follower_count") val channelFollowerCount: Int? = null,
    @SerialName("upload_date") val uploadDate: String? = null,
    val availability: String? = null,
    @SerialName("original_url") val originalUrl: String? = null,
    @SerialName("webpage_url_basename") val webpageUrlBasename: String? = null,
    @SerialName("webpage_url_domain") val webpageUrlDomain: String? = null,
    val extractor: String? = null,
    @SerialName("extractor_key") val extractorKey: String = "",
    val playlist: String? = null,
    @SerialName("playlist_index") val playlistIndex: Int? = null,
    @SerialName("display_id") val displayId: String? = null,
    val fulltitle: String? = null,
    @SerialName("duration_string") val durationString: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val format: String? = null,
    @SerialName("format_id") val formatId: String? = null,
    val ext: String = "",
    val protocol: String? = null,
    @SerialName("format_note") val formatNote: String? = null,
    @SerialName("filesize_approx") val fileSizeApprox: Double? = null,
    @SerialName("filesize") val fileSize: Double? = null,
    val tbr: Double? = null,
    val width: Double? = null,
    val height: Double? = null,
    val resolution: String? = null,
    val fps: Double? = null,
    @SerialName("dynamic_range") val dynamicRange: String? = null,
    val vcodec: String? = null,
    val vbr: Double? = null,
    val acodec: String? = null,
    val abr: Double? = null,
    val asr: Int? = null,
    val epoch: Int? = null,
    @SerialName("requested_downloads") val requestedDownloads: List<RequestedDownload>? = null,
    @SerialName("requested_formats") val requestedFormats: List<Format>? = null,
    val filename: String? = null,
    @SerialName("_type") val type: String? = null,
) : YoutubeDLInfo {

    /**
     * All formats yt-dlp actually reported for this video, filtered to those that are
     * realistically downloadable (must have a resolvable URL and either audio or video).
     * This NEVER invents formats that aren't present in the original JSON — it only
     * filters out junk entries (e.g. "storyboard" mhtml formats, formats with no url).
     */
    fun availableFormats(): List<Format> =
        formats.orEmpty().filter { f ->
            !f.url.isNullOrBlank() && !f.isStoryboard()
        }

    /**
     * Formats sorted by *real* quality, highest first.
     * Priority: resolution (height) > fps > bitrate (tbr, falling back to vbr+abr) > codec quality.
     * Combined (video+audio) formats and video-only formats are compared on the same scale so
     * that e.g. a 2160p video-only stream correctly ranks above a 720p combined stream.
     */
    fun sortedFormatsByQuality(): List<Format> =
        availableFormats().sortedWith(FormatQualityComparator)

    /**
     * Distinct resolutions (heights) actually available from yt-dlp, sorted descending.
     * Audio-only formats are excluded since they have no meaningful resolution.
     * Example: if yt-dlp only reports 2160p/1440p/1080p/720p, this returns exactly those,
     * never a resolution that wasn't actually offered.
     */
    fun availableResolutions(): List<Int> =
        availableFormats()
            .filter { it.containsVideo() }
            .mapNotNull { it.height?.roundToInt() }
            .filter { it > 0 }
            .distinct()
            .sortedDescending()

    /** Best video-only format for a given target height, or the best overall if height is null. */
    fun bestVideoOnlyFormat(targetHeight: Int? = null): Format? =
        availableFormats()
            .filter { it.isVideoOnly() && it.containsVideo() }
            .let { list -> targetHeight?.let { h -> list.filter { (it.height?.roundToInt() ?: 0) <= h } } ?: list }
            .sortedWith(FormatQualityComparator)
            .firstOrNull()

    /** Best audio-only format available. */
    fun bestAudioOnlyFormat(): Format? =
        availableFormats()
            .filter { it.isAudioOnly() && it.containsAudio() }
            .sortedWith(FormatQualityComparator)
            .firstOrNull()
}

enum class StreamType {
    VIDEO_ONLY,
    AUDIO_ONLY,
    COMBINED,
    UNKNOWN,
}

@Serializable
data class Format(
    @SerialName("format_id") val formatId: String? = null,
    @SerialName("format_note") val formatNote: String? = null,
    val ext: String? = null,
    @SerialName("acodec") val acodec: String? = null,
    @SerialName("vcodec") val vcodec: String? = null,
    val url: String? = null,
    val width: Double? = null,
    val height: Double? = null,
    val fps: Double? = null,
    @SerialName("audio_ext") val audioExt: String? = null,
    @SerialName("video_ext") val videoExt: String? = null,
    val format: String? = null,
    val resolution: String? = null,
    val vbr: Double? = null,
    val abr: Double? = null,
    val tbr: Double? = null,
    @SerialName("filesize") val fileSize: Double? = null,
    @SerialName("filesize_approx") val fileSizeApprox: Double? = null,
) {
    /**
     * yt-dlp reports "no codec" in two different ways depending on extractor/version:
     * an actual JSON null, OR the literal string "none". Some extractors also emit "" .
     * Treat all three the same way everywhere instead of duplicating this check.
     */
    private fun String?.isAbsentCodec(): Boolean =
        this == null || this.equals("none", ignoreCase = true) || this.isBlank()

    fun isAudioOnly(): Boolean = vcodec.isAbsentCodec() && !acodec.isAbsentCodec()

    fun isVideoOnly(): Boolean = acodec.isAbsentCodec() && !vcodec.isAbsentCodec()

    fun containsVideo(): Boolean = !vcodec.isAbsentCodec() || (width != null && width > 0.0) || (height != null && height > 0.0) || (vcodec.isAbsentCodec() && acodec.isAbsentCodec() && resolution != "audio only")

    fun containsAudio(): Boolean = !acodec.isAbsentCodec() || (vcodec.isAbsentCodec() && acodec.isAbsentCodec())

    fun isCombined(): Boolean = containsVideo() && containsAudio()

    fun isStoryboard(): Boolean =
        formatNote?.contains("storyboard", ignoreCase = true) == true ||
            ext.equals("mhtml", ignoreCase = true)

    val streamType: StreamType
        get() =
            when {
                isCombined() -> StreamType.COMBINED
                isVideoOnly() -> StreamType.VIDEO_ONLY
                isAudioOnly() -> StreamType.AUDIO_ONLY
                else -> StreamType.UNKNOWN
            }

    /** Effective bitrate in kbps: prefer tbr, otherwise sum whatever video/audio bitrate exists. */
    val effectiveBitrate: Double
        get() = tbr ?: ((vbr ?: 0.0) + (abr ?: 0.0)).takeIf { it > 0.0 } ?: 0.0

    val effectiveHeight: Int
        get() = height?.roundToInt() ?: 0

    val effectiveFps: Double
        get() = fps ?: 0.0

    /**
     * Rough codec-quality rank used only as the last tie-breaker (after resolution/fps/bitrate
     * already matched). Newer, more efficient codecs rank slightly higher when everything else
     * is equal — but this NEVER overrides resolution or bitrate differences.
     */
    private val videoCodecRank: Int
        get() =
            when {
                vcodec == null -> 0
                vcodec.startsWith("av01") -> 4
                vcodec.startsWith("vp9") -> 3
                vcodec.startsWith("avc1") || vcodec.startsWith("h264") -> 2
                vcodec.startsWith("vp8") -> 1
                else -> 0
            }

    private val audioCodecRank: Int
        get() =
            when {
                acodec == null -> 0
                acodec.startsWith("opus") -> 3
                acodec.startsWith("mp4a") || acodec.startsWith("aac") -> 2
                acodec.startsWith("mp3") -> 1
                else -> 0
            }

    internal val codecRank: Int
        get() = videoCodecRank + audioCodecRank

    fun estimatedSizeBytes(): Double? = fileSize ?: fileSizeApprox
}

/**
 * Real-quality comparator: resolution > fps > bitrate > codec, exactly as requested.
 * Never used to invent or hide formats — only to order what yt-dlp actually returned.
 */
object FormatQualityComparator : Comparator<Format> {
    override fun compare(a: Format, b: Format): Int {
        // 1) Resolution (height first, then width as a tie-breaker for equal height)
        compareValues(b.effectiveHeight, a.effectiveHeight).let { if (it != 0) return it }
        compareValues(b.width ?: 0.0, a.width ?: 0.0).let { if (it != 0) return it }

        // 2) FPS
        compareValues(b.effectiveFps, a.effectiveFps).let { if (it != 0) return it }

        // 3) Bitrate
        compareValues(b.effectiveBitrate, a.effectiveBitrate).let { if (it != 0) return it }

        // 4) Codec quality (tie-breaker only)
        return compareValues(b.codecRank, a.codecRank)
    }
}

@Serializable
data class VideoClip(val start: Int = 0, val end: Int = 0) {
    constructor(
        range: ClosedFloatingPointRange<Float>
    ) : this(range.start.roundToInt(), range.endInclusive.roundToInt())
}

@Serializable
data class Chapter(
    val title: String? = null,
    @SerialName("start_time") val startTime: Double? = null,
    @SerialName("end_time") val endTime: Double? = null,
)

@Serializable
data class RequestedDownload(
    @SerialName("requested_formats") val requestedFormats: List<Format>? = emptyList(),
    @SerialName("format_id") val formatId: String? = null,
    @SerialName("format_note") val formatNote: String? = null,
    val ext: String? = null,
    @SerialName("acodec") val acodec: String? = null,
    @SerialName("vcodec") val vcodec: String? = null,
    val url: String? = null,
    val width: Double? = null,
    val height: Double? = null,
    val fps: Double? = null,
    @SerialName("audio_ext") val audioExt: String? = null,
    @SerialName("video_ext") val videoExt: String? = null,
    val format: String? = null,
    val resolution: String? = null,
    val vbr: Double? = null,
    val abr: Double? = null,
    val tbr: Double? = null,
    @SerialName("filesize") val fileSize: Double? = null,
    @SerialName("filesize_approx") val fileSizeApprox: Double? = null,
    val filename: String? = null,
) {
    fun toFormat(): Format =
        Format(
            formatId = formatId,
            formatNote = formatNote,
            ext = ext,
            acodec = acodec,
            vcodec = vcodec,
            url = url,
            width = width,
            height = height,
            fps = fps,
            audioExt = audioExt,
            videoExt = videoExt,
            format = format,
            resolution = resolution,
            vbr = vbr,
            abr = abr,
            tbr = tbr,
            fileSize = fileSize,
            fileSizeApprox = fileSizeApprox,
        )
}

@Serializable
data class PlaylistResult(
    val uploader: String? = null,
    val availability: String? = null,
    val channel: String? = null,
    val title: String? = null,
    val description: String? = null,
    @SerialName("_type") val type: String? = null,
    val entries: List<PlaylistEntry>? = emptyList(),
    @SerialName("webpage_url") val webpageUrl: String? = null,
    @SerialName("original_url") val originalUrl: String? = null,
    @SerialName("extractor_key") val extractorKey: String? = null,
) : YoutubeDLInfo

@Serializable
data class Thumbnail(val url: String, val height: Double = .0, val width: Double = .0)

@Serializable
data class PlaylistEntry(
    @SerialName("_type") val type: String? = null,
    val ieKey: String? = null,
    val id: String? = null,
    val url: String? = null,
    val title: String? = null,
    val duration: Double? = .0,
    val uploader: String? = null,
    val channel: String? = null,
    val thumbnails: List<Thumbnail>? = emptyList(),
)

@Serializable
data class SubtitleFormat(
    val ext: String,
    val url: String,
    val name: String? = null,
    val protocol: String? = null,
)
