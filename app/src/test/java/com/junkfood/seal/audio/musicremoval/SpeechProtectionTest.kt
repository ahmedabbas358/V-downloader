package com.junkfood.seal.audio.musicremoval

import com.junkfood.seal.audio.musicremoval.postprocessor.SpeechProtection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechProtectionTest {

    @Test
    fun testSpeechProtectionPreservation() {
        val origL = FloatArray(44100) { 0.6f }
        val origR = FloatArray(44100) { 0.6f }

        val procL = FloatArray(44100) { 0.2f }
        val procR = FloatArray(44100) { 0.2f }

        val (outL, outR) = SpeechProtection.protectSpeech(
            originalLeft = origL,
            originalRight = origR,
            processedLeft = procL,
            processedRight = procR,
            sampleRate = 44100,
            level = MusicRemovalConfig.SpeechPreservationLevel.HIGH
        )

        assertEquals(44100, outL.size)
        // Ensure protected output preserves vocal power above the baseline
        assertTrue(outL[1000] >= 0.2f)
    }
}
