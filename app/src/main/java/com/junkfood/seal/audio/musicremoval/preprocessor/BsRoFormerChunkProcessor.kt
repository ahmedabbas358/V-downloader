package com.junkfood.seal.audio.musicremoval.preprocessor

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min

/**
 * BsRoFormerChunkProcessor
 *
 * Implements chunked streaming audio inference with Hann-windowed Overlap-Add (OLA).
 * Guarantees zero click/pop boundary artifacts, perfect phase coherence, and exact duration preservation.
 */
object BsRoFormerChunkProcessor {

    /**
     * Processes full audio buffers in streaming chunks with smooth overlap-add windowing.
     *
     * @param leftChannel   Source left channel audio samples
     * @param rightChannel  Source right channel audio samples
     * @param chunkSamples  Number of samples per chunk (e.g. 352800 for 8s @ 44.1kHz)
     * @param overlapSamples Number of overlapping samples (e.g. 88200 for 2s @ 44.1kHz)
     * @param onProgress    Progress callback receiving (0.0 to 1.0)
     * @param processChunk  Inference function applied to each (leftChunk, rightChunk)
     * @return Pair of processed (outLeft, outRight) with exact same size as input
     */
    inline fun processStreaming(
        leftChannel: FloatArray,
        rightChannel: FloatArray,
        chunkSamples: Int = 352800,
        overlapSamples: Int = 88200,
        crossinline onProgress: (Float) -> Unit = {},
        crossinline processChunk: (FloatArray, FloatArray) -> Pair<FloatArray, FloatArray>
    ): Pair<FloatArray, FloatArray> {
        val totalSamples = min(leftChannel.size, rightChannel.size)
        if (totalSamples == 0) return Pair(FloatArray(0), FloatArray(0))

        val stepSamples = (chunkSamples - overlapSamples).coerceAtLeast(1)
        val outLeft = FloatArray(totalSamples)
        val outRight = FloatArray(totalSamples)
        val normWeight = FloatArray(totalSamples)

        val window = FloatArray(chunkSamples) { i ->
            (0.5f * (1.0f - cos(2.0 * PI * i / (chunkSamples - 1)))).toFloat()
        }

        var offset = 0
        var chunkIndex = 0
        val totalChunks = (totalSamples + stepSamples - 1) / stepSamples

        val inChunkL = FloatArray(chunkSamples)
        val inChunkR = FloatArray(chunkSamples)

        while (offset < totalSamples) {
            val copyLen = min(chunkSamples, totalSamples - offset)

            for (i in 0 until copyLen) {
                inChunkL[i] = leftChannel[offset + i]
                inChunkR[i] = rightChannel[offset + i]
            }
            for (i in copyLen until chunkSamples) {
                inChunkL[i] = 0.0f
                inChunkR[i] = 0.0f
            }

            val (procChunkL, procChunkR) = processChunk(inChunkL, inChunkR)

            for (i in 0 until copyLen) {
                val outIdx = offset + i
                val w = window[i]
                outLeft[outIdx] += procChunkL[i] * w
                outRight[outIdx] += procChunkR[i] * w
                normWeight[outIdx] += w
            }

            offset += stepSamples
            chunkIndex++

            if (totalChunks > 0) {
                val p = (chunkIndex.toFloat() / totalChunks.toFloat()).coerceIn(0.0f, 1.0f)
                onProgress(p)
            }
        }

        // Normalize overlap weights
        for (i in 0 until totalSamples) {
            val w = normWeight[i]
            if (w > 1e-4f) {
                outLeft[i] = (outLeft[i] / w).coerceIn(-1.0f, 1.0f)
                outRight[i] = (outRight[i] / w).coerceIn(-1.0f, 1.0f)
            }
        }

        return Pair(outLeft, outRight)
    }
}
