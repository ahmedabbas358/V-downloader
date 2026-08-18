package com.junkfood.seal.ai.audio.dsp

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class OverlapAddProcessorTest {

    @Test
    fun testOverlapAddIdentity() = runBlocking {
        val totalSamples = 44100 * 3 // 3 seconds
        val left = FloatArray(totalSamples) { i -> sin(2.0 * PI * 300.0 * i / 44100.0).toFloat() }
        val right = FloatArray(totalSamples) { i -> sin(2.0 * PI * 600.0 * i / 44100.0).toFloat() }

        // Process chunks with identity transform
        val (outL, outR) = OverlapAddProcessor.processStereo(
            leftChannel = left,
            rightChannel = right,
            chunkSamples = 44100, // 1 second
            overlapSamples = 11025 // 0.25 second
        ) { chunkL, chunkR ->
            Pair(chunkL, chunkR)
        }

        assertEquals(totalSamples, outL.size)
        assertEquals(totalSamples, outR.size)

        var maxErrL = 0.0f
        for (i in 0 until totalSamples) {
            val err = abs(outL[i] - left[i])
            if (err > maxErrL) maxErrL = err
        }

        assertTrue("Overlap-Add identity error should be < 1e-4, got $maxErrL", maxErrL < 1e-4f)
    }
}
