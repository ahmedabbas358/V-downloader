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
    private val BRACKETED_YOUTUBE_ID = Regex("""\[([A-Za-z0-9_-]{4,20})\]""")
    private val LANGUAGE_SUFFIX =
        Regex("""\.([a-zA-Z]{2,3}(?:-[a-zA-Z0-9_]+){0,3})$""", RegexOption.IGNORE_CASE)
    private val INDEX_PREFIX = Regex("""^\s*(?:#|\[|\()?0*(\d{1,5})(?:\]|\))?\s*[-_. ]""")

    private val STOP_WORDS = setOf(
        "the", "and", "or", "for", "in", "on", "at", "to", "a", "an", "is", "of", "with",
        "this", "that", "from", "by", "video", "audio", "hd", "mp4", "m4a", "ep", "part",
        "vol", "ch", "chapter", "episode", "full", "official", "arabic", "english", "course",
        "tutorial", "lesson", "free", "hq", "1080p", "720p", "4k", "2024", "2025", "2026",
        "فيديو", "صوت", "شرح", "درس", "حلقة", "كامل", "الجزء", "دورة", "كورس", "مترجم", "ترجمة"
    )

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
        val resultByKey = linkedMapOf<String, RequirementResult>()
        val duplicateFiles = mutableListOf<ExistingContent>()
        val invalidFiles = mutableListOf<ExistingContent>()
        val ambiguousFiles = mutableListOf<ExistingContent>()
        val staleFiles = mutableListOf<ExistingContent>()

        val files = collectCandidateFiles(directories, uniqueRequirements.map { it.contentType }.toSet(), mode).toMutableList()

        // Match each requirement in sequence using robust 4-tier matching
        uniqueRequirements.forEach { requirement ->
            val video = requirement.video
            val videoId = video.videoId.trim()
            val index = video.playlistIndex
            val rawTitle = video.title.trim()
            val normalizedTitle = normalizeTitle(rawTitle)
            val titleTokens = normalizedTitle.split(" ").filter { it.length >= 2 && it !in STOP_WORDS }

            val formattedIndex1 = index?.toString() ?: ""
            val formattedIndex2 = if (index != null) String.format(Locale.US, "%02d", index) else ""
            val formattedIndex3 = if (index != null) String.format(Locale.US, "%03d", index) else ""

            var matchedFile: File? = null
            var matchedConfidence = MatchConfidence.HIGH
            var matchReason = ""

            val iterator = files.iterator()
            while (iterator.hasNext()) {
                val file = iterator.next()
                val fileName = file.name
                val normalizedFileName = normalizeTitle(cleanFileNameForMatching(fileName))
                val existing = inspectFile(file, video.playlistId, mode)

                if (existing.contentType != requirement.contentType) continue
                if (!matchesSubtitleRequirement(requirement, existing.identity)) continue

                val fileVideoId = existing.identity.videoId ?: extractVideoId(fileName)
                if (videoId.isNotEmpty() && fileVideoId != null && fileVideoId != videoId) {
                    continue
                }

                // Strategy 1: Video ID match
                if (videoId.isNotEmpty() && (fileName.contains(videoId) || fileName.contains("[$videoId]") || fileName.contains("_$videoId"))) {
                    matchedFile = file
                    matchedConfidence = MatchConfidence.HIGH
                    matchReason = "Matched by embedded videoId ($videoId)"
                    iterator.remove()
                    break
                }

                // If requirement has a specific non-empty videoId, title/index matches alone are lower confidence (AMBIGUOUS)
                val isTitleOnlyMatchAllowed = videoId.isEmpty() || mode == ScanMode.FAST

                // Strategy 2: Exact Normalized Title Match or Levenshtein Similarity >= 0.75
                if (normalizedTitle.isNotBlank() && (normalizedFileName == normalizedTitle ||
                        normalizedFileName.contains(normalizedTitle) ||
                        calculateLevenshteinSimilarity(normalizedFileName, normalizedTitle) >= 0.75)) {
                    matchedFile = file
                    matchedConfidence = if (isTitleOnlyMatchAllowed) MatchConfidence.HIGH else MatchConfidence.MEDIUM
                    matchReason = "Matched by title similarity"
                    iterator.remove()
                    break
                }

                // Strategy 3: Index Prefix Match + Title Token Overlap
                if (index != null) {
                    val indexMatches = fileName.startsWith(formattedIndex3) ||
                            fileName.startsWith(formattedIndex2) ||
                            fileName.startsWith("#$formattedIndex1") ||
                            fileName.startsWith("#$formattedIndex2") ||
                            fileName.startsWith("#$formattedIndex3") ||
                            Regex("""(?:^|[\[\(\_\-\s#])$formattedIndex3(?:[\s\-\_\.\]\)]|$)""").containsMatchIn(fileName) ||
                            Regex("""(?:^|[\[\(\_\-\s#])$formattedIndex2(?:[\s\-\_\.\]\)]|$)""").containsMatchIn(fileName) ||
                            (index < 10 && Regex("""(?:^|[\[\(\_\-\s#])$formattedIndex1(?:[\s\-\_\.\]\)]|$)""").containsMatchIn(fileName))

                    if (indexMatches) {
                        if (titleTokens.isNotEmpty()) {
                            val matchedCount = titleTokens.count { normalizedFileName.contains(it) }
                            val requiredCount = (titleTokens.size * 0.3).toInt().coerceAtLeast(1)
                            if (matchedCount >= requiredCount || normalizedFileName.contains(normalizedTitle.take(15))) {
                                matchedFile = file
                                matchedConfidence = if (isTitleOnlyMatchAllowed) MatchConfidence.HIGH else MatchConfidence.MEDIUM
                                matchReason = "Matched by index ($index) + title overlap"
                                iterator.remove()
                                break
                            }
                        } else {
                            matchedFile = file
                            matchedConfidence = if (isTitleOnlyMatchAllowed) MatchConfidence.HIGH else MatchConfidence.MEDIUM
                            matchReason = "Matched by index ($index)"
                            iterator.remove()
                            break
                        }
                    }
                }

                // Strategy 4: High Significant Token Overlap (>= 60%)
                if (titleTokens.size >= 2) {
                    val matchedTokenCount = titleTokens.count { token -> normalizedFileName.contains(token) }
                    val ratio = matchedTokenCount.toDouble() / titleTokens.size.toDouble()
                    if (ratio >= 0.60 && normalizedFileName.length >= normalizedTitle.length * 0.35) {
                        matchedFile = file
                        matchedConfidence = MatchConfidence.LOW
                        matchReason = "Matched by high token overlap (${(ratio * 100).toInt()}%)"
                        iterator.remove()
                        break
                    }
                }
            }

            if (matchedFile != null) {
                val existing = inspectFile(matchedFile, video.playlistId, mode)
                if (!existing.isValid) {
                    invalidFiles += existing
                    resultByKey[requirement.stableKey] =
                        RequirementResult(
                            requirement = requirement,
                            state = ContentState.INVALID,
                            matchedFile = matchedFile,
                            confidence = matchedConfidence,
                            reason = existing.reason,
                        )
                } else if (matchedConfidence != MatchConfidence.HIGH && videoId.isNotEmpty()) {
                    ambiguousFiles += existing.copy(reason = "Matched by title/index only without videoId [$videoId]")
                    resultByKey[requirement.stableKey] =
                        RequirementResult(
                            requirement = requirement,
                            state = ContentState.AMBIGUOUS,
                            matchedFile = matchedFile,
                            confidence = matchedConfidence,
                            reason = "Matched by title/index only without videoId [$videoId]",
                        )
                } else {
                    resultByKey[requirement.stableKey] =
                        RequirementResult(
                            requirement = requirement,
                            state = ContentState.VALID,
                            matchedFile = matchedFile,
                            confidence = matchedConfidence,
                            reason = matchReason,
                        )
                }
            } else {
                resultByKey[requirement.stableKey] =
                    RequirementResult(
                        requirement = requirement,
                        state = ContentState.MISSING,
                        reason = "No local file matched index ${index ?: 0} (${video.title})",
                    )
            }
        }

        // Classify remaining unconsumed files
        val foundVideoIds = resultByKey.values.filter { it.state == ContentState.VALID }.map { it.requirement.video.videoId }.filter { it.isNotBlank() }.toSet()
        val allPlaylistVideoIds = uniqueRequirements.map { it.video.videoId }.filter { it.isNotBlank() }.toSet()

        files.forEach { remainingFile ->
            val existing = inspectFile(remainingFile, uniqueRequirements.firstOrNull()?.video?.playlistId, mode)
            val extractedId = existing.identity.videoId ?: extractVideoId(remainingFile.name)
            if (extractedId != null && extractedId in foundVideoIds) {
                duplicateFiles += existing.copy(reason = "Duplicate file for videoId $extractedId")
            } else if (extractedId != null && extractedId !in allPlaylistVideoIds) {
                staleFiles += existing.copy(reason = "File does not belong to this playlist")
            } else {
                staleFiles += existing.copy(reason = "Unmatched leftover file or superseded variant")
            }
        }

        val results = uniqueRequirements.map { resultByKey[it.stableKey] ?: RequirementResult(it, ContentState.MISSING) }

        val summary =
            MissingSummary(
                expected = uniqueRequirements.size,
                found = results.count { it.state == ContentState.VALID },
                missing = results.count { it.state != ContentState.VALID && it.state != ContentState.UNAVAILABLE },
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

    private fun cleanFileNameForMatching(fileName: String): String {
        var name = fileName.substringBeforeLast('.')
        name = name.replace(Regex("""\.(?:[a-zA-Z]{2}(?:-[a-zA-Z]{2,4})*|auto|orig)$""", RegexOption.IGNORE_CASE), "")
        name = name.replace(Regex("""[\.\[\(]\d{3,4}p[\.\]\)]""", RegexOption.IGNORE_CASE), "")
        return name
    }

    private fun calculateLevenshteinSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0
        val len1 = s1.length
        val len2 = s2.length
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }
        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j
        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        val maxLen = maxOf(len1, len2)
        return 1.0 - (dp[len1][len2].toDouble() / maxLen)
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
            if (file.length() <= 0L) {
                false
            } else {
                when (type) {
                    ContentType.SUBTITLE -> SubtitleValidator.validateFile(file).isSuccess
                    ContentType.AUDIO, ContentType.VIDEO -> {
                        val minSize = if (mode == ScanMode.FAST) 256L else 1024L
                        file.canRead() && file.length() >= minSize
                    }
                }
            }
        val reason =
            when {
                !valid -> if (file.length() <= 0L) "Corrupted 0-byte file" else "File failed validation"
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
        val existingLanguage = existing.normalizedLanguage ?: return true
        val requested = subtitle.normalizedLanguage
        val langMatches = existingLanguage.isEmpty() ||
            requested.isEmpty() ||
            existingLanguage.equals(requested, ignoreCase = true) ||
            LanguageMatcher.getBaseLanguageCode(existingLanguage).equals(LanguageMatcher.getBaseLanguageCode(requested), ignoreCase = true)
        if (!langMatches) return false

        // Match source (MANUAL, AUTO_GENERATED, etc.) if explicitly specified
        if (subtitle.source != SubtitleSource.UNKNOWN && existing.source != SubtitleSource.UNKNOWN) {
            if (subtitle.source != existing.source) return false
        }
        return true
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
