package com.junkfood.seal.ai.audio

import com.junkfood.seal.ai.audio.quality.QualityAnalyzer
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class QualityAnalyzerTest {

    @Test
    fun testQualityAnalyzerMetrics() {
        val n = 44100 * 2 // 2 seconds
        // Vocal in 1000Hz speech band
        val original = FloatArray(n) { i -> (sin(2.0 * PI * 1000.0 * i / 44100.0) + sin(2.0 * PI * 100.0 * i / 44100.0)).toFloat() }
        val vocalL = FloatArray(n) { i -> sin(2.0 * PI * 1000.0 * i / 44100.0).toFloat() }
        val vocalR = FloatArray(n) { i -> sin(2.0 * PI * 1000.0 * i / 44100.0).toFloat() }

        val quality = QualityAnalyzer.analyze(original, vocalL, vocalR, 44100)

        assertTrue(quality.speechQuality in 0.0f..1.0f)
        assertTrue(quality.musicResidual in 0.0f..1.0f)
        assertTrue(quality.confidence in 0.0f..1.0f)
    }
}
