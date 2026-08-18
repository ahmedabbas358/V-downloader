package com.junkfood.seal.ai.audio.dsp

import kotlin.math.PI
import kotlin.math.sin

/**
 * OverlapAddProcessor
 *
 * Slices long audio signals into fixed-length inference chunks with overlapping boundaries,
 * applies a caller-supplied transformation function per chunk, and stitches the results
 * using Hann cross-fade tapering to eliminate clicks, pops, and phase discontinuities.
 */
object OverlapAddProcessor {

    /**
     * Processes stereo FloatArray channels with streaming overlap-add.
     *
     * @param leftChannel Left audio samples
     * @param rightChannel Right audio samples
     * @param chunkSamples Number of samples per inference window (e.g. 44100 * 4 = 176400 samples)
     * @param overlapSamples Overlap region size (e.g. 44100 / 2 = 22050 samples)
     * @param onProgress Optional progress callback (0f..1f)
     * @param processChunk Suspend function that processes a single stereo chunk and returns the processed stem [left, right]
     * @return Processed stereo audio pair [leftChannelOut, rightChannelOut]
     */
    suspend fun processStereo(
        leftChannel: FloatArray,
        rightChannel: FloatArray,
        chunkSamples: Int = 176400, // 4 seconds at 44.1kHz
        overlapSamples: Int = 22050, // 0.5 seconds at 44.1kHz
        onProgress: ((Float) -> Unit)? = null,
        processChunk: suspend (leftChunk: FloatArray, rightChunk: FloatArray) -> Pair<FloatArray, FloatArray>,
    ): Pair<FloatArray, FloatArray> {
        val totalSamples = leftChannel.size
        require(rightChannel.size == totalSamples) { "Stereo channels must have identical sample length" }

        if (totalSamples <= chunkSamples) {
            val (pL, pR) = processChunk(leftChannel, rightChannel)
            return Pair(pL, pR)
        }

        val step = chunkSamples - overlapSamples
        val outLeft = FloatArray(totalSamples)
        val outRight = FloatArray(totalSamples)
        val weights = FloatArray(totalSamples)

        val taperWindow = FloatArray(chunkSamples) { i ->
            when {
                i < overlapSamples -> {
                    val r = (i.toFloat() / overlapSamples)
                    sin(r * (PI / 2.0)).toFloat()
                }
                i >= chunkSamples - overlapSamples -> {
                    val r = ((chunkSamples - 1 - i).toFloat() / overlapSamples)
                    sin(r * (PI / 2.0)).toFloat()
                }
                else -> 1.0f
            }
        }

        var startSample = 0
        val totalSteps = ((totalSamples - overlapSamples).toFloat() / step).toInt().coerceAtLeast(1)
        var stepCount = 0

        while (startSample < totalSamples) {
            val length = (chunkSamples).coerceAtMost(totalSamples - startSample)
            val chunkL = FloatArray(chunkSamples)
            val chunkR = FloatArray(chunkSamples)

            System.arraycopy(leftChannel, startSample, chunkL, 0, length)
            System.arraycopy(rightChannel, startSample, chunkR, 0, length)

            val (procL, procR) = processChunk(chunkL, chunkR)

            for (i in 0 until length) {
                val destIdx = startSample + i
                val w = taperWindow[i]
                outLeft[destIdx] += procL[i] * w
                outRight[destIdx] += procR[i] * w
                weights[destIdx] += w
            }

            stepCount++
            onProgress?.invoke((stepCount.toFloat() / totalSteps).coerceAtMost(1.0f))

            if (startSample + chunkSamples >= totalSamples) break
            startSample += step
        }

        // Normalize overlap regions by accumulated weight
        for (i in 0 until totalSamples) {
            val w = weights[i]
            if (w > 1e-5f) {
                outLeft[i] /= w
                outRight[i] /= w
            }
        }

        return Pair(outLeft, outRight)
    }
}
