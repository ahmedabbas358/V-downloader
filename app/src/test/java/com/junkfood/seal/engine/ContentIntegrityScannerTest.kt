package com.junkfood.seal.engine

import com.junkfood.seal.download.engine.identity.ContentRequirement
import com.junkfood.seal.download.engine.identity.ContentState
import com.junkfood.seal.download.engine.identity.ContentType
import com.junkfood.seal.download.engine.identity.SubtitleIdentity
import com.junkfood.seal.download.engine.identity.VideoIdentity
import com.junkfood.seal.download.engine.integrity.ContentIntegrityScanner
import com.junkfood.seal.download.engine.subtitle.model.SubtitleSource
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentIntegrityScannerTest {

    @Test
    fun playlistScanCountsUniqueVideoIdsNotFiles() {
        val dir = createTempDir(prefix = "playlist-integrity")
        try {
            val requirements =
                (1..57).map { index ->
                    val videoId = "vid%08d".format(index)
                    ContentRequirement(
                        video =
                            VideoIdentity(
                                videoId = videoId,
                                canonicalUrl = "https://www.youtube.com/watch?v=$videoId",
                                playlistId = "PL_test",
                                playlistIndex = index,
                                title = "Lecture $index",
                            ),
                        contentType = ContentType.VIDEO,
                    )
                }

            (1..42).forEach { index ->
                val videoId = "vid%08d".format(index)
                File(dir, "%03d - Lecture %d [%s].mp4".format(index, index, videoId))
                    .writeBytes(ByteArray(2048) { 1 })
            }
            File(dir, "999 - Duplicate [vid00000001].mp4").writeBytes(ByteArray(2048) { 1 })

            val report = ContentIntegrityScanner.scan(requirements, listOf(dir))

            assertEquals(57, report.summary.expected)
            assertEquals(42, report.summary.found)
            assertEquals(15, report.summary.missing)
            assertEquals(1, report.summary.duplicate)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun playlistScanMarksTitleOnlyMatchesAsAmbiguous() {
        val dir = createTempDir(prefix = "playlist-ambiguous")
        try {
            val requirement =
                ContentRequirement(
                    video =
                        VideoIdentity(
                            videoId = "abc12345678",
                            canonicalUrl = "https://www.youtube.com/watch?v=abc12345678",
                            playlistId = "PL_test",
                            playlistIndex = 1,
                            title = "Lesson One",
                        ),
                    contentType = ContentType.VIDEO,
                )
            File(dir, "001 - Lesson One.mp4").writeBytes(ByteArray(2048) { 1 })

            val report = ContentIntegrityScanner.scan(listOf(requirement), listOf(dir))

            assertEquals(0, report.summary.found)
            assertEquals(1, report.summary.ambiguous)
            assertEquals(ContentState.AMBIGUOUS, report.results.single().state)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun playlistScanDoesNotCountDuplicateFilesAsSeparateItems() {
        val dir = createTempDir(prefix = "playlist-duplicate")
        try {
            val requirements =
                listOf(1, 2).map { index ->
                    val videoId = "dup%08d".format(index)
                    ContentRequirement(
                        video =
                            VideoIdentity(
                                videoId = videoId,
                                canonicalUrl = "https://www.youtube.com/watch?v=$videoId",
                                playlistId = "PL_test",
                                playlistIndex = index,
                                title = "Lesson $index",
                            ),
                        contentType = ContentType.VIDEO,
                    )
                }

            File(dir, "001 - Lesson 1 [dup00000001].mp4").writeBytes(ByteArray(2048) { 1 })
            File(dir, "001 - Lesson 1 copy [dup00000001].mp4").writeBytes(ByteArray(2048) { 1 })

            val report = ContentIntegrityScanner.scan(requirements, listOf(dir))

            assertEquals(2, report.summary.expected)
            assertEquals(1, report.summary.found)
            assertEquals(1, report.summary.missing)
            assertEquals(1, report.summary.duplicate)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun playlistScanIgnoresForeignPlaylistFiles() {
        val dir = createTempDir(prefix = "playlist-foreign")
        try {
            val requirements =
                (1..57).map { index ->
                    val videoId = "a%010d".format(index)
                    ContentRequirement(
                        video =
                            VideoIdentity(
                                videoId = videoId,
                                canonicalUrl = "https://www.youtube.com/watch?v=$videoId",
                                playlistId = "PL_A",
                                playlistIndex = index,
                                title = "A Lesson $index",
                            ),
                        contentType = ContentType.VIDEO,
                    )
                }

            (1..42).forEach { index ->
                val videoId = "a%010d".format(index)
                File(dir, "%03d - A Lesson %d [%s].mp4".format(index, index, videoId))
                    .writeBytes(ByteArray(2048) { 1 })
            }
            (1..10).forEach { index ->
                val foreignId = "b%010d".format(index)
                File(dir, "%03d - Foreign %d [%s].mp4".format(index, index, foreignId))
                    .writeBytes(ByteArray(2048) { 1 })
            }

            val report = ContentIntegrityScanner.scan(requirements, listOf(dir))

            assertEquals(57, report.summary.expected)
            assertEquals(42, report.summary.found)
            assertEquals(15, report.summary.missing)
            assertEquals(10, report.summary.stale)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun playlistScanKeepsManualAndAutoSubtitlesSeparate() {
        val dir = createTempDir(prefix = "subtitle-source")
        try {
            val requirement =
                ContentRequirement(
                    video =
                        VideoIdentity(
                            videoId = "sub12345678",
                            canonicalUrl = "https://www.youtube.com/watch?v=sub12345678",
                            playlistId = "PL_sub",
                            playlistIndex = 1,
                            title = "Subtitle Lesson",
                        ),
                    contentType = ContentType.SUBTITLE,
                    subtitle =
                        SubtitleIdentity(
                            videoId = "sub12345678",
                            playlistId = "PL_sub",
                            language = "ar",
                            source = SubtitleSource.MANUAL,
                        ),
                    expectedFormat = "srt",
                )
            File(dir, "001 - Subtitle Lesson [sub12345678].auto.ar.srt")
                .writeText(
                    """
                    1
                    00:00:01,000 --> 00:00:03,000
                    مرحبا
                    """.trimIndent()
                )

            val report = ContentIntegrityScanner.scan(listOf(requirement), listOf(dir))

            assertEquals(0, report.summary.found)
            assertEquals(1, report.summary.missing)
            assertEquals(1, report.summary.stale)
        } finally {
            dir.deleteRecursively()
        }
    }
}
