package com.junkfood.seal.audio.musicremoval

import com.junkfood.seal.audio.musicremoval.analysis.ResidualAnalyzer
import org.junit.Assert.assertTrue
import org.junit.Test

class ResidualAnalyzerTest {

    @Test
    fun testQualityEvaluation() {
        val origL = FloatArray(44100) { 0.5f }
        val origR = FloatArray(44100) { 0.5f }

        val sepL = FloatArray(44100) { 0.25f }
        val sepR = FloatArray(44100) { 0.25f }

        val eval = ResidualAnalyzer.evaluate(origL, origR, sepL, sepR, sampleRate = 44100)
        assertTrue(eval.overallQualityScore > 0.5f)
        assertTrue(eval.speechPreservationScore > 0.7f)
    }
}
