package com.junkfood.seal.audio.musicremoval

import com.junkfood.seal.audio.musicremoval.detection.MusicDetector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class MusicDetectorTest {

    @Test
    fun testSilenceDetection() {
        val left = FloatArray(44100) { 0.0f }
        val right = FloatArray(44100) { 0.0f }

        val result = MusicDetector.analyze(left, right, sampleRate = 44100)
        assertFalse(result.hasMusic)
        assertTrue(result.isSpeechOnly)
    }

    @Test
    fun testPureSpeechSimulation() {
        // Mono center-panned signal with voice-like fundamental (150Hz + formants)
        val left = FloatArray(44100) { i ->
            (sin(2.0 * PI * 150.0 * i / 44100) * 0.5 + sin(2.0 * PI * 600.0 * i / 44100) * 0.3).toFloat()
        }
        val right = left.clone() // Identical stereo = zero side energy

        val result = MusicDetector.analyze(left, right, sampleRate = 44100)
        assertFalse(result.isMusicHeavy)
        assertTrue(result.speechScore > 0.3f)
    }

    @Test
    fun testStereoMusicSimulation() {
        // Broad dynamic stereo side-panned music signal
        val left = FloatArray(44100) { i ->
            (sin(2.0 * PI * 80.0 * i / 44100) * 0.6 + sin(2.0 * PI * 2000.0 * i / 44100) * 0.4).toFloat()
        }
        val right = FloatArray(44100) { i ->
            (sin(2.0 * PI * 120.0 * i / 44100) * 0.6 - sin(2.0 * PI * 2000.0 * i / 44100) * 0.4).toFloat()
        }

        val result = MusicDetector.analyze(left, right, sampleRate = 44100)
        assertTrue(result.hasMusic)
    }
}
