package com.junkfood.seal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class PlaylistVerifierTest {

    private fun cleanFileName(fileName: String): String {
        var name = if (fileName.contains('.')) fileName.substringBeforeLast('.') else fileName
        name = name.replace(Regex("""\.(?:[a-zA-Z]{2}(?:-[a-zA-Z]{2,4})*|auto|orig)$""", RegexOption.IGNORE_CASE), "")
        name = name.replace(Regex("""[\.\[\(]\d{3,4}p[\.\]\)]""", RegexOption.IGNORE_CASE), "")
        return name
    }

    private fun normalizeText(text: String): String {
        return text.lowercase(Locale.US)
            .replace(Regex("[\\p{Punct}\\s\\u064B-\\u0652]+"), " ")
            .trim()
    }

    @Test
    fun testSubtitleLanguageStripping() {
        val fileName1 = "035 - #34 Event-Driven Programming Part-2 [l69ghMpsp6w].ar-en-US.srt"
        val cleaned1 = cleanFileName(fileName1)
        assertEquals("035 - #34 Event-Driven Programming Part-2 [l69ghMpsp6w]", cleaned1)

        val fileName2 = "035 - #34 Event-Driven Programming Part-2 [l69ghMpsp6w].ar.srt"
        val cleaned2 = cleanFileName(fileName2)
        assertEquals("035 - #34 Event-Driven Programming Part-2 [l69ghMpsp6w]", cleaned2)
    }

    @Test
    fun testTextNormalization() {
        val rawTitle = "#34 Event-Driven Programming Part-2: Best practices for concurrency & Active Object pattern"
        val normalized = normalizeText(rawTitle)
        assertTrue(normalized.contains("34 event driven programming part 2"))

        val arabicTitle = "أمثلة شائعة وتطبيقات عمليّة"
        val normalizedArabic = normalizeText(arabicTitle)
        assertTrue("Arabic text normalization should preserve words", normalizedArabic.contains("تطبيقات"))
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
        // Simulating the user scenario: 1 subtitle file on disk for a 57-item playlist
        val localFiles = mutableListOf(
            "005 - #5 Preprocessor and the volatile keyword [abc12345678].ar.srt"
        )

        val playlistEntries = (1..57).map { i ->
            "Track #$i" to if (i == 5) "abc12345678" else "other_id_$i"
        }

        val foundCount = playlistEntries.count { (title, id) ->
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
}
