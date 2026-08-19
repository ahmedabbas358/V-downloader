package com.junkfood.seal.audio.musicremoval

import com.junkfood.seal.audio.musicremoval.detection.SpeechDetector
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class SpeechDetectorTest {

    @Test
    fun testVadDetection() {
        val signal = FloatArray(44100 * 2) { i ->
            if (i < 44100) {
                // Speech section
                (sin(2.0 * PI * 200.0 * i / 44100) * 0.5 + sin(2.0 * PI * 1200.0 * i / 44100) * 0.3).toFloat()
            } else {
                // Silence section
                0.0001f
            }
        }

        val vad = SpeechDetector.computeVadMask(signal, sampleRate = 44100)
        assertTrue(vad.isSpeechFrame.isNotEmpty())
        assertTrue(vad.overallSpeechRatio in 0.3f..0.7f)
    }
}
