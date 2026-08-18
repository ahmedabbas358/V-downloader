package com.junkfood.seal.engine

import com.junkfood.seal.download.engine.subtitle.conversion.SubtitleConverter
import com.junkfood.seal.download.engine.subtitle.model.SubtitleOutputFormat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SubtitleConverterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testVttToSrtConversion() = runBlocking {
        val vttFile = tempFolder.newFile("sample.vtt").apply {
            writeText(
                """
                WEBVTT
                Kind: captions
                Language: ar

                00:01.500 --> 00:04.200
                مرحبًا بكم في المعمارية المتقدمة

                00:05.000 --> 00:08.500
                <c.yellow>النص باللون الأصفر</c>
                """.trimIndent()
            )
        }

        val srtDest = File(tempFolder.root, "sample.srt")
        val converted = SubtitleConverter.convert(vttFile, SubtitleOutputFormat.SRT, srtDest).getOrThrow()

        assertTrue(converted.exists())
        val srtText = converted.readText()
        assertTrue(srtText.contains("00:00:01,500 --> 00:00:04,200"))
        assertTrue(srtText.contains("مرحبًا بكم في المعمارية المتقدمة"))
        // Formatting tag stripped
        assertTrue(srtText.contains("النص باللون الأصفر"))
        assertFalse(srtText.contains("<c.yellow>"))
    }

    @Test
    fun testSrtToVttConversion() = runBlocking {
        val srtFile = tempFolder.newFile("sample.srt").apply {
            writeText(
                """
                1
                00:00:01,500 --> 00:00:04,200
                Hello World

                2
                00:00:05,000 --> 00:00:08,000
                Second Line
                """.trimIndent()
            )
        }

        val vttDest = File(tempFolder.root, "sample.vtt")
        val converted = SubtitleConverter.convert(srtFile, SubtitleOutputFormat.VTT, vttDest).getOrThrow()

        val vttText = converted.readText()
        assertTrue(vttText.startsWith("WEBVTT"))
        assertTrue(vttText.contains("00:00:01.500 --> 00:00:04.200"))
    }

    @Test
    fun testTtmlToSrtConversion() = runBlocking {
        val ttmlFile = tempFolder.newFile("sample.ttml").apply {
            writeText(
                """
                <tt xmlns="http://www.w3.org/ns/ttml">
                  <body>
                    <div>
                      <p begin="00:00:01.000" end="00:00:03.500">TTML to SRT Translation</p>
                      <p begin="00:00:04.000" end="00:00:07.000">Second paragraph &amp; symbols</p>
                    </div>
                  </body>
                </tt>
                """.trimIndent()
            )
        }

        val srtDest = File(tempFolder.root, "sample.srt")
        val converted = SubtitleConverter.convert(ttmlFile, SubtitleOutputFormat.SRT, srtDest).getOrThrow()

        val srtText = converted.readText()
        assertTrue(srtText.contains("00:00:01,000 --> 00:00:03,500"))
        assertTrue(srtText.contains("TTML to SRT Translation"))
        assertTrue(srtText.contains("Second paragraph & symbols"))
    }

    @Test
    fun testShiftSubtitleTiming() = runBlocking {
        val srtFile = tempFolder.newFile("timing.srt").apply {
            writeText(
                """
                1
                00:00:02,000 --> 00:00:04,000
                Shift Test
                """.trimIndent()
            )
        }

        // Shift by +1.5 seconds (+1500ms)
        SubtitleConverter.shiftSubtitleTiming(srtFile, 1500L).getOrThrow()

        val shiftedText = srtFile.readText()
        assertTrue(shiftedText.contains("00:00:03,500 --> 00:00:05,500"))
    }
}
