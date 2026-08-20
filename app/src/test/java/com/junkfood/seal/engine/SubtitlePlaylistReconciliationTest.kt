package com.junkfood.seal.engine

import com.junkfood.seal.download.engine.identity.ContentRequirement
import com.junkfood.seal.download.engine.identity.ContentState
import com.junkfood.seal.download.engine.identity.ContentType
import com.junkfood.seal.download.engine.identity.SubtitleIdentity
import com.junkfood.seal.download.engine.identity.VideoIdentity
import com.junkfood.seal.download.engine.integrity.ContentIntegrityScanner
import com.junkfood.seal.download.engine.subtitle.discovery.LanguageMatcher
import com.junkfood.seal.download.engine.subtitle.model.SubtitleInventory
import com.junkfood.seal.download.engine.subtitle.model.SubtitleSource
import com.junkfood.seal.download.engine.subtitle.model.SubtitleTrack
import com.junkfood.seal.download.engine.subtitle.model.SubtitleTypePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SubtitlePlaylistReconciliationTest {

    private fun validSrtContent(text: String = "مرحبا بكم"): String =
        """
        1
        00:00:01,000 --> 00:00:04,000
        $text
        
        2
        00:00:05,000 --> 00:00:08,000
        هذا مقطع اختباري للترجمة
        """.trimIndent()

    @Test
    fun testReconciliation57Expected42Valid15Missing() {
        val dir = createTempDir(prefix = "reconciliation-57-42")
        try {
            val requirements = (1..57).map { index ->
                val videoId = "vid%08d".format(index)
                ContentRequirement(
                    video = VideoIdentity(
                        videoId = videoId,
                        canonicalUrl = "https://www.youtube.com/watch?v=$videoId",
                        playlistId = "PL_57_test",
                        playlistIndex = index,
                        title = "Lesson $index",
                        durationSeconds = 600
                    ),
                    contentType = ContentType.SUBTITLE,
                    subtitle = SubtitleIdentity(
                        videoId = videoId,
                        playlistId = "PL_57_test",
                        language = "ar",
                        source = SubtitleSource.MANUAL
                    ),
                    expectedFormat = "srt"
                )
            }

            // Create 42 valid subtitle files
            (1..42).forEach { index ->
                val videoId = "vid%08d".format(index)
                File(dir, "%03d - Lesson %d [%s].ar.srt".format(index, index, videoId))
                    .writeText(validSrtContent("الدرس $index"))
            }

            val report = ContentIntegrityScanner.scan(requirements, listOf(dir))

            assertEquals("Expected total items", 57, report.summary.expected)
            assertEquals("Found valid items", 42, report.summary.found)
            assertEquals("Missing items count", 15, report.summary.missing)
            assertEquals("Duplicate items", 0, report.summary.duplicate)

            // Verify that the 15 missing video IDs are specifically vid00000043 to vid00000057
            val missingVideoIds = report.results
                .filter { it.state == ContentState.MISSING }
                .map { it.requirement.video.videoId }

            assertEquals(15, missingVideoIds.size)
            (43..57).forEach { idx ->
                val expectedId = "vid%08d".format(idx)
                assertTrue("Missing list must contain $expectedId", missingVideoIds.contains(expectedId))
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun testReconciliation10Expected10Valid0Missing() {
        val dir = createTempDir(prefix = "reconciliation-10-10")
        try {
            val requirements = (1..10).map { index ->
                val videoId = "v10_%04d".format(index)
                ContentRequirement(
                    video = VideoIdentity(
                        videoId = videoId,
                        canonicalUrl = "https://www.youtube.com/watch?v=$videoId",
                        playlistId = "PL_10_full",
                        playlistIndex = index,
                        title = "Chapter $index"
                    ),
                    contentType = ContentType.SUBTITLE,
                    subtitle = SubtitleIdentity(
                        videoId = videoId,
                        playlistId = "PL_10_full",
                        language = "ar",
                        source = SubtitleSource.MANUAL
                    ),
                    expectedFormat = "srt"
                )
            }

            (1..10).forEach { index ->
                val videoId = "v10_%04d".format(index)
                File(dir, "%03d - Chapter %d [%s].ar.srt".format(index, index, videoId))
                    .writeText(validSrtContent("الفصل $index"))
            }

            val report = ContentIntegrityScanner.scan(requirements, listOf(dir))

            assertEquals(10, report.summary.expected)
            assertEquals(10, report.summary.found)
            assertEquals(0, report.summary.missing)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun testReconciliation10Expected9Valid1Missing() {
        val dir = createTempDir(prefix = "reconciliation-10-9")
        try {
            val requirements = (1..10).map { index ->
                val videoId = "v10_%04d".format(index)
                ContentRequirement(
                    video = VideoIdentity(
                        videoId = videoId,
                        canonicalUrl = "https://www.youtube.com/watch?v=$videoId",
                        playlistId = "PL_10_9",
                        playlistIndex = index,
                        title = "Chapter $index"
                    ),
                    contentType = ContentType.SUBTITLE,
                    subtitle = SubtitleIdentity(
                        videoId = videoId,
                        playlistId = "PL_10_9",
                        language = "ar",
                        source = SubtitleSource.MANUAL
                    ),
                    expectedFormat = "srt"
                )
            }

            // Create only 1..9 (skip 10)
            (1..9).forEach { index ->
                val videoId = "v10_%04d".format(index)
                File(dir, "%03d - Chapter %d [%s].ar.srt".format(index, index, videoId))
                    .writeText(validSrtContent())
            }

            val report = ContentIntegrityScanner.scan(requirements, listOf(dir))

            assertEquals(10, report.summary.expected)
            assertEquals(9, report.summary.found)
            assertEquals(1, report.summary.missing)

            val missingItem = report.results.single { it.state == ContentState.MISSING }
            assertEquals("v10_0010", missingItem.requirement.video.videoId)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun testReconciliation10Expected8Valid2Duplicates() {
        val dir = createTempDir(prefix = "reconciliation-duplicates")
        try {
            val requirements = (1..10).map { index ->
                val videoId = "dup_%04d".format(index)
                ContentRequirement(
                    video = VideoIdentity(
                        videoId = videoId,
                        canonicalUrl = "https://www.youtube.com/watch?v=$videoId",
                        playlistId = "PL_dup",
                        playlistIndex = index,
                        title = "Item $index"
                    ),
                    contentType = ContentType.SUBTITLE,
                    subtitle = SubtitleIdentity(
                        videoId = videoId,
                        playlistId = "PL_dup",
                        language = "ar",
                        source = SubtitleSource.MANUAL
                    ),
                    expectedFormat = "srt"
                )
            }

            // 8 valid unique items (1..8)
            (1..8).forEach { index ->
                val videoId = "dup_%04d".format(index)
                File(dir, "%03d - Item %d [%s].ar.srt".format(index, index, videoId))
                    .writeText(validSrtContent())
            }

            // 2 duplicate files for item 1 and item 2
            File(dir, "901 - Copy of Item 1 [dup_0001].ar.srt").writeText(validSrtContent())
            File(dir, "902 - Copy of Item 2 [dup_0002].ar.srt").writeText(validSrtContent())

            val report = ContentIntegrityScanner.scan(requirements, listOf(dir))

            assertEquals(10, report.summary.expected)
            assertEquals(8, report.summary.found)
            assertEquals(2, report.summary.missing)
            assertEquals(2, report.summary.duplicate)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun testReconciliationIgnoresForeignPlaylistFiles() {
        val dir = createTempDir(prefix = "reconciliation-foreign")
        try {
            val requirementsA = (1..57).map { index ->
                val videoId = "pA_%04d".format(index)
                ContentRequirement(
                    video = VideoIdentity(
                        videoId = videoId,
                        canonicalUrl = "https://www.youtube.com/watch?v=$videoId",
                        playlistId = "PL_A",
                        playlistIndex = index,
                        title = "Playlist A Item $index"
                    ),
                    contentType = ContentType.SUBTITLE,
                    subtitle = SubtitleIdentity(
                        videoId = videoId,
                        playlistId = "PL_A",
                        language = "ar",
                        source = SubtitleSource.MANUAL
                    ),
                    expectedFormat = "srt"
                )
            }

            // 42 valid files for Playlist A
            (1..42).forEach { index ->
                val videoId = "pA_%04d".format(index)
                File(dir, "%03d - Playlist A Item %d [%s].ar.srt".format(index, index, videoId))
                    .writeText(validSrtContent())
            }

            // 10 files belonging to Playlist B
            (1..10).forEach { index ->
                val foreignId = "pB_%04d".format(index)
                File(dir, "%03d - Playlist B Item %d [%s].ar.srt".format(index, index, foreignId))
                    .writeText(validSrtContent())
            }

            val report = ContentIntegrityScanner.scan(requirementsA, listOf(dir))

            assertEquals(57, report.summary.expected)
            assertEquals(42, report.summary.found)
            assertEquals(15, report.summary.missing)
            assertEquals(10, report.summary.stale)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun testCorrupted0ByteSubtitleIdentifiedAsInvalid() {
        val dir = createTempDir(prefix = "reconciliation-corrupt")
        try {
            val requirement = ContentRequirement(
                video = VideoIdentity(
                    videoId = "corrupt1234",
                    canonicalUrl = "https://www.youtube.com/watch?v=corrupt1234",
                    playlistId = "PL_corrupt",
                    playlistIndex = 1,
                    title = "Corrupt Item"
                ),
                contentType = ContentType.SUBTITLE,
                subtitle = SubtitleIdentity(
                    videoId = "corrupt1234",
                    playlistId = "PL_corrupt",
                    language = "ar",
                    source = SubtitleSource.MANUAL
                ),
                expectedFormat = "srt"
            )

            // Create 0-byte corrupted subtitle file
            val file = File(dir, "001 - Corrupt Item [corrupt1234].ar.srt")
            file.writeBytes(ByteArray(0))

            val report = ContentIntegrityScanner.scan(listOf(requirement), listOf(dir))

            assertEquals(1, report.summary.expected)
            assertEquals(0, report.summary.found)
            assertEquals(1, report.summary.missing)
            assertEquals(1, report.summary.invalid)
            assertEquals(ContentState.INVALID, report.results.single().state)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun test5TierLanguageResolutionPolicy() {
        val tracks = listOf(
            SubtitleTrack(languageCode = "ar-SA", languageName = "Arabic (Saudi Arabia)", directUrl = "http://cdn/ar-sa", source = SubtitleSource.MANUAL),
            SubtitleTrack(languageCode = "ar", languageName = "Arabic (Standard)", directUrl = "http://cdn/ar", source = SubtitleSource.MANUAL),
            SubtitleTrack(languageCode = "ar", languageName = "Arabic (Auto-generated)", directUrl = "http://cdn/ar-auto", isAutomatic = true, source = SubtitleSource.AUTO_GENERATED),
            SubtitleTrack(languageCode = "ar-EG", languageName = "Arabic (Egypt Auto)", directUrl = "http://cdn/ar-eg-auto", isAutomatic = true, source = SubtitleSource.AUTO_GENERATED),
            SubtitleTrack(languageCode = "ar", languageName = "Arabic (Translated)", directUrl = "http://cdn/ar-trans", isTranslated = true, source = SubtitleSource.TRANSLATED)
        )

        // 1. Query "ar" with Exact Manual available -> Must pick Exact Manual ("ar")
        val matchExactManual = LanguageMatcher.matchTracks("ar", tracks, SubtitleTypePolicy.ANY)
        assertEquals(1, matchExactManual.size)
        assertEquals("ar", matchExactManual[0].languageCode)
        assertEquals(SubtitleSource.MANUAL, matchExactManual[0].source)

        // 2. Query "ar" without Exact Manual, but with Manual Variant ("ar-SA") -> Must pick Manual Variant
        val tracksNoExactManual = tracks.filterNot { it.languageCode == "ar" && it.source == SubtitleSource.MANUAL }
        val matchManualVariant = LanguageMatcher.matchTracks("ar", tracksNoExactManual, SubtitleTypePolicy.ANY)
        assertEquals(1, matchManualVariant.size)
        assertEquals("ar-SA", matchManualVariant[0].languageCode)
        assertEquals(SubtitleSource.MANUAL, matchManualVariant[0].source)

        // 3. Query "ar" without Manual tracks, but with Exact Auto ("ar") -> Must pick Exact Auto
        val tracksAutoOnly = tracks.filter { it.source != SubtitleSource.MANUAL }
        val matchExactAuto = LanguageMatcher.matchTracks("ar", tracksAutoOnly, SubtitleTypePolicy.ANY)
        assertEquals(1, matchExactAuto.size)
        assertEquals("ar", matchExactAuto[0].languageCode)
        assertEquals(SubtitleSource.AUTO_GENERATED, matchExactAuto[0].source)

        // 4. Query "ar" with only Auto Variant ("ar-EG") -> Must pick Auto Variant
        val tracksAutoVariantOnly = listOf(tracks[3], tracks[4])
        val matchAutoVariant = LanguageMatcher.matchTracks("ar", tracksAutoVariantOnly, SubtitleTypePolicy.ANY)
        assertEquals(1, matchAutoVariant.size)
        assertEquals("ar-EG", matchAutoVariant[0].languageCode)
        assertEquals(SubtitleSource.AUTO_GENERATED, matchAutoVariant[0].source)

        // 5. Query "ar" with only Translated -> Must pick Translated
        val tracksTranslatedOnly = listOf(tracks[4])
        val matchTranslated = LanguageMatcher.matchTracks("ar", tracksTranslatedOnly, SubtitleTypePolicy.ANY)
        assertEquals(1, matchTranslated.size)
        assertEquals(SubtitleSource.TRANSLATED, matchTranslated[0].source)
    }
}
