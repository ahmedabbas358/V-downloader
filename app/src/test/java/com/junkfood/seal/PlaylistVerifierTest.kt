package com.junkfood.seal

import com.junkfood.seal.download.PlaylistVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class PlaylistVerifierTest {

    @Test
    fun testSubtitleLanguageStripping() {
        val fileName1 = "035 - #34 Event-Driven Programming Part-2 [l69ghMpsp6w].ar-en-US.srt"
        val cleaned1 = PlaylistVerifier.cleanFileNameForMatching(fileName1)
        assertEquals("#34 Event-Driven Programming Part-2", cleaned1)

        val fileName2 = "035 - #34 Event-Driven Programming Part-2 [l69ghMpsp6w].ar.srt"
        val cleaned2 = PlaylistVerifier.cleanFileNameForMatching(fileName2)
        assertEquals("#34 Event-Driven Programming Part-2", cleaned2)

        val fileName3 = "001 - Introduction to Algorithms [1080p].synced.vtt"
        val cleaned3 = PlaylistVerifier.cleanFileNameForMatching(fileName3)
        assertEquals("Introduction to Algorithms", cleaned3)
    }

    @Test
    fun testTimestampParsingForMediaTrimmer() {
        assertEquals(65.0, com.junkfood.seal.util.MediaProcessingEngine.parseTimestampSeconds("00:01:05")!!, 0.001)
        assertEquals(3661.5, com.junkfood.seal.util.MediaProcessingEngine.parseTimestampSeconds("01:01:01.5")!!, 0.001)
        assertEquals(12.25, com.junkfood.seal.util.MediaProcessingEngine.parseTimestampSeconds("12.25")!!, 0.001)
        assertEquals(null, com.junkfood.seal.util.MediaProcessingEngine.parseTimestampSeconds("invalid"))
    }

    @Test
    fun testTextNormalization() {
        val rawTitle = "#34 Event-Driven Programming Part-2: Best practices for concurrency & Active Object pattern"
        val normalized = PlaylistVerifier.normalizeText(rawTitle)
        assertTrue(normalized.contains("34 event driven programming part 2"))

        val arabicTitle = "أمثلة شائعة وتطبيقات عمليّة؛ مع الشرح التوضيحي..."
        val normalizedArabic = PlaylistVerifier.normalizeText(arabicTitle)
        assertTrue("Arabic text normalization should normalize Alef and Taa Marbuta and Yaa", normalizedArabic.contains("امثله شايعه وتطبيقات عمليه"))

        val arabicWithTashkeel = "شَرْحُ لُغَةِ الـكُوتْلِنْ (مُتَقَدِّم)"
        val normalizedTashkeel = PlaylistVerifier.normalizeText(arabicWithTashkeel)
        assertTrue(normalizedTashkeel.contains("شرح لغه الكوتلن"))
    }

    @Test
    fun testVideoIdAndIndexMatching() {
        val fileName = "035 - #34 Event-Driven Programming Part-2 [l69ghMpsp6w].ar-en-US.srt"
        val extractedId = "l69ghMpsp6w"
        val index = 35

        // Strategy 1: Video ID Match
        val idMatch = fileName.contains(extractedId, ignoreCase = true)
        assertTrue("Video ID should match filename", idMatch)

        // Strategy 2: Numeric Index Match
        val formattedIndex3 = String.format(Locale.US, "%03d", index)
        val indexMatch = fileName.contains(formattedIndex3)
        assertTrue("Playlist index 035 should match filename", indexMatch)
    }

    @Test
    fun testOneToOneFileMatching() {
        // Simulating 1 subtitle file on disk for a 57-item playlist
        val localFiles = mutableListOf(
            "005 - #5 Preprocessor and the volatile keyword [abc12345678].ar.srt"
        )

        val playlistEntries = (1..57).map { i ->
            "Track #$i" to if (i == 5) "abc12345678" else "other_id_$i"
        }

        val foundCount = playlistEntries.count { (_, id) ->
            val match = localFiles.firstOrNull { it.contains(id) }
            if (match != null) {
                localFiles.remove(match) // 1-to-1 consumption
                true
            } else {
                false
            }
        }

        assertEquals("Only 1 track should match the single local file on disk", 1, foundCount)
        assertTrue("Available local files pool should be empty after matching", localFiles.isEmpty())
    }

    @Test
    fun testLevenshteinSimilarity() {
        val s1 = "01 introduction to kotlin coroutines"
        val s2 = "01 introduction to kotlin coroutines and flow"
        val sim = PlaylistVerifier.calculateLevenshteinSimilarity(s1, s2)
        assertTrue("Similarity should be high for shared prefix", sim >= 0.75)

        val identicalSim = PlaylistVerifier.calculateLevenshteinSimilarity("test", "test")
        assertEquals(1.0, identicalSim, 0.001)
    }

    @Test
    fun testArabicAlefAndHamzaVariants() {
        val entryTitle = "إعداد البيئة وتثبيت الأدوات"
        val fileName = "001 - اعداد البيئه وتثبيت الادوات.mp4"
        val normEntry = PlaylistVerifier.normalizeText(entryTitle)
        val normFile = PlaylistVerifier.normalizeText(PlaylistVerifier.cleanFileNameForMatching(fileName))
        assertTrue(normFile.contains(normEntry))
    }
}
