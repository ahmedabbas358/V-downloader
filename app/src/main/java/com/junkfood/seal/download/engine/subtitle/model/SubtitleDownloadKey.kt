package com.junkfood.seal.download.engine.subtitle.model

/**
 * SubtitleDownloadKey establishes a unique, deterministic identity for any subtitle task.
 * It is used for strictly deduplicating tasks in the registry before enqueueing,
 * ensuring that the same resource is never downloaded twice concurrently or repeatedly.
 */
data class SubtitleDownloadKey(
    val videoId: String,
    val languageCode: String,
    val source: SubtitleSource,
    val formatExtension: String
) {
    override fun toString(): String {
        return "${videoId}_${languageCode}_${source.name}_${formatExtension}"
    }
}
