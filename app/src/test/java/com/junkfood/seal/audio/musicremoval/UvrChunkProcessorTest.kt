package com.junkfood.seal.audio.musicremoval

import com.junkfood.seal.audio.musicremoval.preprocessor.UvrChunkProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class UvrChunkProcessorTest {

    @Test
    fun testOverlapAddPreservesLengthAndSignal() {
        val sampleRate = 44100
        val durationSeconds = 3
        val numSamples = sampleRate * durationSeconds

        val inL = FloatArray(numSamples) { i -> sin(2.0 * PI * 440.0 * i / sampleRate).toFloat() }
        val inR = FloatArray(numSamples) { i -> sin(2.0 * PI * 880.0 * i / sampleRate).toFloat() }

        var chunksProcessed = 0
        val (outL, outR) = UvrChunkProcessor.processStreaming(
            leftChannel = inL,
            rightChannel = inR,
            chunkSamples = 44100, // 1.0s
            overlapSamples = 11025, // 0.25s
            onProgress = {}
        ) { chunkL, chunkR ->
            chunksProcessed++
            // Identity passthrough
            Pair(chunkL, chunkR)
        }

        assertEquals(numSamples, outL.size)
        assertEquals(numSamples, outR.size)
        assertTrue("Must process multiple chunks", chunksProcessed >= 3)

        // Verify signal reconstruction fidelity (middle region where OLA is steady)
        for (i in 11025 until numSamples - 11025) {
            val errL = abs(inL[i] - outL[i])
            val errR = abs(inR[i] - outR[i])
            assertTrue("Reconstruction error should be minimal at index $i: $errL", errL < 0.05f)
            assertTrue("Reconstruction error should be minimal at index $i: $errR", errR < 0.05f)
        }
    }
}
