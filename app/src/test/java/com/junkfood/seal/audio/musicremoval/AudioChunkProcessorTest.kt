package com.junkfood.seal.audio.musicremoval

import com.junkfood.seal.audio.musicremoval.preprocessor.AudioChunkProcessor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class AudioChunkProcessorTest {

    @Test
    fun testStreamingReconstruction() = runBlocking {
        val n = 44100 * 3
        val left = FloatArray(n) { i -> (sin(2.0 * Math.PI * 440.0 * i / 44100) * 0.8).toFloat() }
        val right = left.clone()

        val (outL, outR) = AudioChunkProcessor.processStreaming(
            leftChannel = left,
            rightChannel = right,
            chunkSamples = 44100,
            overlapSamples = 11025
        ) { chunkL, chunkR ->
            // Pass-through identity
            Pair(chunkL, chunkR)
        }

        assertEquals(n, outL.size)
        // Verify signal was linearly reconstructed with low error (< 0.05 max diff)
        var maxDiff = 0.0f
        for (i in 11025 until n - 11025) {
            maxDiff = maxOf(maxDiff, abs(left[i] - outL[i]))
        }
        assertTrue("Max reconstruction error should be small, was $maxDiff", maxDiff < 0.08f)
    }
}
