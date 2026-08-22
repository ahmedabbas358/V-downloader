package com.junkfood.seal.download.engine.playlist

import com.junkfood.seal.download.engine.identity.ContentState
import com.junkfood.seal.download.engine.identity.MatchConfidence
import com.junkfood.seal.util.DownloadUtil.DownloadPreferences
import java.io.File

data class PlaylistAuditResult(
    val playlistTitle: String,
    val targetDirectory: String,
    val totalCount: Int,
    val items: List<PlaylistAuditItem>,
    val summary: AuditSummary
)

data class AuditSummary(
    val expected: Int,
    val downloaded: Int,
    val missing: Int,
    val partial: Int,
    val corrupted: Int,
    val unknown: Int,
    val unavailable: Int
)

enum class AuditState {
    DOWNLOADED,
    NOT_DOWNLOADED,
    PARTIAL,
    CORRUPTED,
    UNKNOWN,
    UNAVAILABLE
}

data class PlaylistAuditItem(
    val index: Int,
    val title: String,
    val url: String,
    val playlistUrl: String,
    val playlistTitle: String,
    val preferences: DownloadPreferences,
    val videoId: String = "",
    val matchedFile: File? = null,
    val matchedFileSize: Long = 0L,
    val state: AuditState = AuditState.UNKNOWN,
    val confidence: MatchConfidence = MatchConfidence.UNKNOWN
) {
    /**
     * Maps to legacy ContentState for backwards compatibility if needed elsewhere.
     */
    fun toLegacyContentState(): ContentState {
        return when (state) {
            AuditState.DOWNLOADED -> ContentState.VALID
            AuditState.NOT_DOWNLOADED -> ContentState.MISSING
            AuditState.PARTIAL -> ContentState.STALE
            AuditState.CORRUPTED -> ContentState.INVALID
            AuditState.UNKNOWN -> ContentState.AMBIGUOUS
            AuditState.UNAVAILABLE -> ContentState.UNAVAILABLE
        }
    }
}
