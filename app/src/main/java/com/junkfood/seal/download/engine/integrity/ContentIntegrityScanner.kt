package com.junkfood.seal.download.engine.integrity

import com.junkfood.seal.download.engine.identity.ContentRequirement
import com.junkfood.seal.download.engine.identity.ContentState
import com.junkfood.seal.download.engine.identity.ContentType
import com.junkfood.seal.download.engine.identity.ExistingContentIdentity
import com.junkfood.seal.download.engine.identity.MatchConfidence
import com.junkfood.seal.download.engine.subtitle.discovery.LanguageMatcher
import com.junkfood.seal.download.engine.subtitle.model.SubtitleOutputFormat
import com.junkfood.seal.download.engine.subtitle.model.SubtitleSource
import com.junkfood.seal.download.engine.subtitle.validation.SubtitleValidator
import java.io.File
import java.text.Normalizer
import java.util.Locale

data class ExistingContent(
    val file: File,
    val contentType: ContentType,
    val identity: ExistingContentIdentity,
    val isValid: Boolean,
    val confidence: MatchConfidence,
    val reason: String,
)

data class RequirementResult(
    val requirement: ContentRequirement,
    val state: ContentState,
    val matchedFile: File? = null,
    val confidence: MatchConfidence = MatchConfidence.UNKNOWN,
    val reason: String = "",
)

data class MissingSummary(
    val expected: Int,
    val found: Int,
    val missing: Int,
    val ambiguous: Int,
    val invalid: Int,
    val duplicate: Int,
    val stale: Int,
    val unavailable: Int,
)

data class IntegrityScanReport(
    val summary: MissingSummary,
    val results: List<RequirementResult>,
    val duplicateFiles: List<ExistingContent>,
    val invalidFiles: List<ExistingContent>,
    val ambiguousFiles: List<ExistingContent>,
    val staleFiles: List<ExistingContent>,
)

enum class ScanMode {
    FAST,
    FULL,
}

object ContentIntegrityScanner {
    private val VIDEO_EXTS = setOf("mp4", "mkv", "webm", "avi", "mov", "flv", "ts", "m4v", "3gp")
    private val AUDIO_EXTS = setOf("mp3", "m4a", "opus", "flac", "wav", "aac", "ogg", "mka", "m4b")
    private val SUBTITLE_EXTS = setOf("srt", "vtt", "ass", "lrc", "sub", "sbv", "ttml")
    private val YOUTUBE_ID = Regex("""(?<![A-Za-z0-9_-])([A-Za-z0-9_-]{11})(?![A-Za-z0-9_-])""")
    private val BRACKETED_YOUTUBE_ID = Regex("""\[([A-Za-z0-9_-]{11})\]""")
    private val LANGUAGE_SUFFIX =
        Regex("""\.([a-zA-Z]{2,3}(?:-[a-zA-Z0-9_]+){0,3})$""", RegexOption.IGNORE_CASE)
    private val INDEX_PREFIX = Regex("""^\s*(?:#|\[|\()?0*(\d{1,5})(?:\]|\))?\s*[-_. ]""")

    fun scan(
        requirements: List<ContentRequirement>,
        directories: Collection<File>,
        mode: ScanMode = ScanMode.FULL,
    ): IntegrityScanReport {
        if (requirements.isEmpty()) {
            return IntegrityScanReport(
                summary = MissingSummary(0, 0, 0, 0, 0, 0, 0, 0),
                results = emptyList(),
                duplicateFiles = emptyList(),
                invalidFiles = emptyList(),
                ambiguousFiles = emptyList(),
                staleFiles = emptyList(),
            )
        }

        val uniqueRequirements = requirements.distinctBy { it.stableKey }
        val requirementByKey = uniqueRequirements.associateBy { it.stableKey }
        val resultByKey = linkedMapOf<String, RequirementResult>()
        val duplicateFiles = mutableListOf<ExistingContent>()
        val invalidFiles = mutableListOf<ExistingContent>()
        val ambiguousFiles = mutableListOf<ExistingContent>()
        val staleFiles = mutableListOf<ExistingContent>()

        val files = collectCandidateFiles(directories, uniqueRequirements.map { it.contentType }.toSet(), mode)
        val indexedRequirements = uniqueRequirements.groupBy { it.video.playlistIndex }
        val idRequirements = uniqueRequirements.groupBy { it.video.videoId }

        files.forEach { file ->
            val existing = inspectFile(file, requirements.first().video.playlistId, mode)
            val videoId = existing.identity.videoId

            if (videoId.isNullOrBlank()) {
                val candidate = matchWithoutIdentity(existing, indexedRequirements)
                if (candidate != null) {
                    val key = candidate.stableKey
                    if (!resultByKey.containsKey(key)) {
                        resultByKey[key] =
                            RequirementResult(
                                requirement = candidate,
                                state = ContentState.AMBIGUOUS,
                                matchedFile = file,
                                confidence = MatchConfidence.MEDIUM,
                                reason = "File has no embedded videoId; matched only by title/index",
                            )
                    }
                }
                ambiguousFiles += existing
                return@forEach
            }

            val candidateRequirements =
                idRequirements[videoId]
                    ?.filter { it.contentType == existing.contentType }
                    ?.filter { matchesSubtitleRequirement(it, existing.identity) }
                    .orEmpty()

            if (candidateRequirements.isEmpty()) {
                staleFiles += existing.copy(reason = "Video id is not part of this playlist snapshot")
                return@forEach
            }

            if (candidateRequirements.size > 1) {
                ambiguousFiles += existing.copy(reason = "Video id maps to multiple requested subtitle identities")
                return@forEach
            }

            val requirement = candidateRequirements.single()
            val key = requirement.stableKey
            val previous = resultByKey[key]

            if (!existing.isValid) {
                invalidFiles += existing
                if (previous == null || previous.state != ContentState.VALID) {
                    resultByKey[key] =
                        RequirementResult(
                            requirement = requirement,
                            state = ContentState.INVALID,
                            matchedFile = file,
                            confidence = MatchConfidence.HIGH,
                            reason = existing.reason,
                        )
                }
                return@forEach
            }

            if (previous?.state == ContentState.VALID) {
                duplicateFiles += existing.copy(reason = "Duplicate for ${requirement.video.videoId}")
                return@forEach
            }

            resultByKey[key] =
                RequirementResult(
                    requirement = requirement,
                    state = ContentState.VALID,
                    matchedFile = file,
                    confidence = MatchConfidence.HIGH,
                    reason = "Matched by embedded videoId",
                )
        }

        val results =
            uniqueRequirements.map { requirement ->
                resultByKey[requirement.stableKey]
                    ?: RequirementResult(
                        requirement = requirement,
                        state = ContentState.MISSING,
                        reason = "No valid local artifact matched ${requirement.video.videoId}",
                    )
            }

        val summary =
            MissingSummary(
                expected = requirementByKey.size,
                found = results.count { it.state == ContentState.VALID },
                missing = results.count { it.state == ContentState.MISSING },
                ambiguous = results.count { it.state == ContentState.AMBIGUOUS },
                invalid = results.count { it.state == ContentState.INVALID },
                duplicate = duplicateFiles.size,
                stale = staleFiles.size,
                unavailable = results.count { it.state == ContentState.UNAVAILABLE },
            )

        return IntegrityScanReport(
            summary = summary,
            results = results,
            duplicateFiles = duplicateFiles,
            invalidFiles = invalidFiles,
            ambiguousFiles = ambiguousFiles,
            staleFiles = staleFiles,
        )
    }

    fun extractPlaylistId(url: String): String =
        Regex("""(?:[?&]list=|/playlist\?list=)([^&#/?]+)""")
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()

    fun canonicalVideoUrl(videoId: String): String =
        if (videoId.isBlank()) "" else "https://www.youtube.com/watch?v=$videoId"

    fun extractVideoId(text: String): String? {
        BRACKETED_YOUTUBE_ID.find(text)?.let { return it.groupValues[1] }
        Regex("""(?:v=|youtu\.be/|/shorts/)([A-Za-z0-9_-]{11})""").find(text)?.let {
            return it.groupValues[1]
        }
        return YOUTUBE_ID.find(text)?.groupValues?.getOrNull(1)
    }

    fun normalizeTitle(text: String): String {
        val normalized =
            Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replace(Regex("[أإآٱ]"), "ا")
                .replace(Regex("[ة]"), "ه")
                .replace(Regex("[ى]"), "ي")
                .replace(Regex("[\u064B-\u0652\u0670\u0640]"), "")
        return normalized
            .lowercase(Locale.US)
            .replace(Regex("""\[[A-Za-z0-9_-]{11}\]"""), " ")
            .replace(INDEX_PREFIX, " ")
            .replace(Regex("[\\p{Punct}\\s]+"), " ")
            .trim()
    }

    private fun collectCandidateFiles(
        directories: Collection<File>,
        contentTypes: Set<ContentType>,
        mode: ScanMode,
    ): List<File> {
        val maxDepth = if (mode == ScanMode.FAST) 2 else 4
        return directories
            .asSequence()
            .filter { it.exists() && it.isDirectory }
            .flatMap { dir ->
                runCatching {
                    dir.walkTopDown()
                        .maxDepth(maxDepth)
                        .filter { file -> file.isFile && isCandidate(file, contentTypes) }
                }.getOrElse { emptySequence() }
            }
            .distinctBy { it.absolutePath }
            .toList()
    }

    private fun isCandidate(file: File, contentTypes: Set<ContentType>): Boolean {
        if (file.length() <= 0L) return false
        val name = file.name.lowercase(Locale.US)
        if (name.endsWith(".part") || name.endsWith(".tmp") || name.endsWith(".ytdl")) return false
        val ext = file.extension.lowercase(Locale.US)
        return contentTypes.any {
            when (it) {
                ContentType.SUBTITLE -> ext in SUBTITLE_EXTS
                ContentType.AUDIO -> ext in AUDIO_EXTS
                ContentType.VIDEO -> ext in VIDEO_EXTS
            }
        }
    }

    private fun inspectFile(file: File, expectedPlaylistId: String?, mode: ScanMode): ExistingContent {
        val type = inferContentType(file)
        val videoId = extractVideoId(file.name)
        val language = if (type == ContentType.SUBTITLE) extractSubtitleLanguage(file) else null
        val source = if (type == ContentType.SUBTITLE) extractSubtitleSource(file) else SubtitleSource.UNKNOWN
        val valid =
            when (type) {
                ContentType.SUBTITLE -> SubtitleValidator.validateFile(file).isSuccess
                ContentType.AUDIO, ContentType.VIDEO -> {
                    val minSize = if (mode == ScanMode.FAST) 256L else 1024L
                    file.canRead() && file.length() >= minSize
                }
            }
        val reason =
            when {
                !valid -> "File failed validation"
                videoId.isNullOrBlank() -> "No videoId in file name"
                else -> "Valid local artifact"
            }

        return ExistingContent(
            file = file,
            contentType = type,
            identity =
                ExistingContentIdentity(
                    videoId = videoId,
                    playlistId = expectedPlaylistId,
                    playlistIndex = extractIndexPrefix(file.name),
                    language = language,
                    source = source,
                ),
            isValid = valid,
            confidence = if (videoId.isNullOrBlank()) MatchConfidence.UNKNOWN else MatchConfidence.HIGH,
            reason = reason,
        )
    }

    private fun inferContentType(file: File): ContentType {
        val ext = file.extension.lowercase(Locale.US)
        return when {
            ext in SUBTITLE_EXTS -> ContentType.SUBTITLE
            ext in AUDIO_EXTS -> ContentType.AUDIO
            else -> ContentType.VIDEO
        }
    }

    private fun extractSubtitleLanguage(file: File): String? {
        val nameWithoutExt = file.nameWithoutExtension
        return LANGUAGE_SUFFIX.find(nameWithoutExt)?.groupValues?.getOrNull(1)
    }

    private fun extractSubtitleSource(file: File): SubtitleSource {
        val name = file.nameWithoutExtension.lowercase(Locale.US)
        return when {
            name.contains(".manual.") || name.endsWith(".manual") -> SubtitleSource.MANUAL
            name.contains(".auto.") || name.endsWith(".auto") -> SubtitleSource.AUTO_GENERATED
            name.contains(".translated.") || name.endsWith(".translated") -> SubtitleSource.TRANSLATED
            else -> SubtitleSource.UNKNOWN
        }
    }

    private fun extractIndexPrefix(fileName: String): Int? =
        INDEX_PREFIX.find(fileName)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun matchesSubtitleRequirement(
        requirement: ContentRequirement,
        existing: ExistingContentIdentity,
    ): Boolean {
        val subtitle = requirement.subtitle ?: return true
        if (subtitle.source != SubtitleSource.UNKNOWN &&
            existing.source != SubtitleSource.UNKNOWN &&
            subtitle.source != existing.source
        ) {
            return false
        }
        val existingLanguage = existing.normalizedLanguage ?: return true
        val requested = subtitle.normalizedLanguage
        return existingLanguage == requested ||
            LanguageMatcher.getBaseLanguageCode(existingLanguage) == LanguageMatcher.getBaseLanguageCode(requested)
    }

    private fun matchWithoutIdentity(
        existing: ExistingContent,
        indexedRequirements: Map<Int?, List<ContentRequirement>>,
    ): ContentRequirement? {
        val index = existing.identity.playlistIndex ?: return null
        val candidates = indexedRequirements[index].orEmpty().filter { it.contentType == existing.contentType }
        if (candidates.size != 1) return null

        val normalizedFile = normalizeTitle(existing.file.nameWithoutExtension)
        val candidate = candidates.single()
        val normalizedTitle = normalizeTitle(candidate.video.title)
        if (normalizedTitle.isBlank()) return null
        return if (normalizedFile.contains(normalizedTitle) || normalizedFile == normalizedTitle) candidate else null
    }
}
