package com.junkfood.seal.audio.musicremoval

import com.junkfood.seal.audio.musicremoval.postprocessor.UvrResidualSuppression
import com.junkfood.seal.audio.musicremoval.postprocessor.UvrSpeechProtection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UvrPostProcessingTest {

    @Test
    fun testResidualSuppressionReducesLowAmplitudeNoise() {
        val n = 44100
        val inL = FloatArray(n) { 0.001f } // Low amplitude noise floor
        val inR = FloatArray(n) { 0.001f }

        val (outL, outR) = UvrResidualSuppression.suppressResiduals(inL, inR, suppressionStrength = 0.9f)
        assertEquals(n, outL.size)
        assertEquals(n, outR.size)
        assertTrue("Residual suppression should attenuate sub-threshold noise", outL[1000] < inL[1000])
    }

    @Test
    fun testSpeechProtectionPreservesBufferDimensions() {
        val n = 4096
        val origL = FloatArray(n) { 0.4f }
        val origR = FloatArray(n) { 0.4f }
        val procL = FloatArray(n) { 0.3f }
        val procR = FloatArray(n) { 0.3f }

        val (protL, protR) = UvrSpeechProtection.protectSpeech(origL, origR, procL, procR, sampleRate = 44100)
        assertEquals(n, protL.size)
        assertEquals(n, protR.size)
    }
}
