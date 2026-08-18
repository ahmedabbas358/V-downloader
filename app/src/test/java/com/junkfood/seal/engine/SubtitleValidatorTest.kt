package com.junkfood.seal.engine

import com.junkfood.seal.download.engine.subtitle.model.SubtitleFailure
import com.junkfood.seal.download.engine.subtitle.model.SubtitleOutputFormat
import com.junkfood.seal.download.engine.subtitle.validation.SubtitleValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SubtitleValidatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testValidSrtFile() {
        val srtFile = tempFolder.newFile("test.srt").apply {
            writeText(
                """
                1
                00:00:01,000 --> 00:00:04,000
                مرحبًا بكم في دورة أندرويد 2026

                2
                00:00:04,500 --> 00:00:08,000
                Welcome to Android Architecture
                """.trimIndent()
            )
        }

        val result = SubtitleValidator.validateFile(srtFile, SubtitleOutputFormat.SRT)
        assertTrue(result.isSuccess)
    }

    @Test
    fun testValidVttFile() {
        val vttFile = tempFolder.newFile("test.vtt").apply {
            writeText(
                """
                WEBVTT

                00:01.000 --> 00:04.000
                Subtitle text in WebVTT format
                """.trimIndent()
            )
        }

        val result = SubtitleValidator.validateFile(vttFile, SubtitleOutputFormat.VTT)
        assertTrue(result.isSuccess)
    }

    @Test
    fun testValidAssFile() {
        val assFile = tempFolder.newFile("test.ass").apply {
            writeText(
                """
                [Script Info]
                Title: Test
                ScriptType: v4.00+

                [Events]
                Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
                Dialogue: 0,0:00:01.00,0:00:04.00,Default,,0,0,0,,Hello ASS
                """.trimIndent()
            )
        }

        val result = SubtitleValidator.validateFile(assFile, SubtitleOutputFormat.ASS)
        assertTrue(result.isSuccess)
    }

    @Test
    fun testRejectHtml403ErrorPage() {
        val fakeFile = tempFolder.newFile("error_403.srt").apply {
            writeText(
                """
                <!DOCTYPE html>
                <html>
                <head><title>403 Forbidden</title></head>
                <body><h1>403 Forbidden - Access Denied</h1></body>
                </html>
                """.trimIndent()
            )
        }

        val result = SubtitleValidator.validateFile(fakeFile, SubtitleOutputFormat.SRT)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SubtitleFailure.InvalidSubtitle)
    }

    @Test
    fun testRejectJsonErrorPayload() {
        val jsonErrorFile = tempFolder.newFile("error.json").apply {
            writeText("""{"error": {"code": 429, "message": "Too Many Requests"}}""")
        }

        val result = SubtitleValidator.validateFile(jsonErrorFile, SubtitleOutputFormat.SRT)
        assertTrue(result.isFailure)
    }

    @Test
    fun testRejectEmptyFile() {
        val emptyFile = tempFolder.newFile("empty.srt")
        val result = SubtitleValidator.validateFile(emptyFile)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SubtitleFailure.EmptySubtitleFile)
    }
}
