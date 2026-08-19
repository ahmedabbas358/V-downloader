package com.junkfood.seal.audio.musicremoval.preprocessor

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.util.concurrent.CancellationException
import kotlin.math.PI
import kotlin.math.sin

/**
 * AudioChunkProcessor
 *
 * Provides chunked Overlap-Add (OLA) streaming synthesis with Hanning/Hamming crossfades
 * to eliminate boundary clicks, volume jumps, and discontinuities while keeping RAM usage flat.
 */
object AudioChunkProcessor {

    /**
     * Processes stereo audio channels chunk-by-chunk using windowed Overlap-Add.
     */
    suspend fun processStreaming(
        leftChannel: FloatArray,
        rightChannel: FloatArray,
        chunkSamples: Int = 176400,   // Default 4.0s @ 44.1kHz
        overlapSamples: Int = 22050,  // Default 0.5s @ 44.1kHz
        onProgress: ((Float) -> Unit)? = null,
        chunkTransform: suspend (leftChunk: FloatArray, rightChunk: FloatArray) -> Pair<FloatArray, FloatArray>
    ): Pair<FloatArray, FloatArray> {
        val totalSamples = minOf(leftChannel.size, rightChannel.size)
        if (totalSamples == 0) return Pair(FloatArray(0), FloatArray(0))

        val hopSize = chunkSamples - overlapSamples
        if (hopSize <= 0) throw IllegalArgumentException("overlapSamples must be smaller than chunkSamples")

        val outputLeft = FloatArray(totalSamples)
        val outputRight = FloatArray(totalSamples)
        val windowSum = FloatArray(totalSamples)

        // Precompute Hanning synthesis window
        val window = FloatArray(chunkSamples)
        for (i in 0 until chunkSamples) {
            window[i] = (0.5f * (1.0f - kotlin.math.cos(2.0 * PI * i / (chunkSamples - 1)))).toFloat()
        }

        val totalChunks = ((totalSamples - overlapSamples + hopSize - 1) / hopSize).coerceAtLeast(1)
        var chunkIndex = 0

        val leftChunk = FloatArray(chunkSamples)
        val rightChunk = FloatArray(chunkSamples)

        var offset = 0
        while (offset < totalSamples) {
            if (!currentCoroutineContext().isActive) {
                throw CancellationException("Audio chunk processing cancelled")
            }

            // Extract windowed chunk
            val available = minOf(chunkSamples, totalSamples - offset)
            for (i in 0 until available) {
                leftChunk[i] = leftChannel[offset + i]
                rightChunk[i] = rightChannel[offset + i]
            }
            // Zero-pad end if needed
            for (i in available until chunkSamples) {
                leftChunk[i] = 0.0f
                rightChunk[i] = 0.0f
            }

            // Apply neural / DSP transformation
            val (procL, procR) = chunkTransform(leftChunk, rightChunk)

            // Overlap-Add to output buffers
            for (i in 0 until available) {
                val w = window[i]
                val outIdx = offset + i
                outputLeft[outIdx] += procL[i] * w
                outputRight[outIdx] += procR[i] * w
                windowSum[outIdx] += w
            }

            chunkIndex++
            onProgress?.invoke((chunkIndex.toFloat() / totalChunks).coerceIn(0f, 1f))
            offset += hopSize
        }

        // Normalize by window sum to achieve perfect linear reconstruction
        for (i in 0 until totalSamples) {
            val weight = windowSum[i]
            if (weight > 1e-4f) {
                outputLeft[i] = (outputLeft[i] / weight).coerceIn(-1.0f, 1.0f)
                outputRight[i] = (outputRight[i] / weight).coerceIn(-1.0f, 1.0f)
            }
        }

        return Pair(outputLeft, outputRight)
    }
}
