package com.junkfood.seal.audio.musicremoval

import com.junkfood.seal.audio.musicremoval.analysis.SeparationQualityEvaluator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeparationQualityEvaluatorTest {

    @Test
    fun testQualityEvaluationWithCleanSignal() {
        val n = 44100
        val origL = FloatArray(n) { 0.5f }
        val origR = FloatArray(n) { 0.5f }
        val sepL = FloatArray(n) { 0.45f }
        val sepR = FloatArray(n) { 0.45f }

        val report = SeparationQualityEvaluator.evaluate(origL, origR, sepL, sepR, sampleRate = 44100)
        assertTrue(report.isAcceptable)
        assertFalse(report.isClippingDetected)
        assertTrue(report.overallQualityScore > 0.5f)
    }

    @Test
    fun testQualityEvaluationWithEmptySignal() {
        val report = SeparationQualityEvaluator.evaluate(FloatArray(0), FloatArray(0), FloatArray(0), FloatArray(0))
        assertFalse(report.isAcceptable)
    }
}
