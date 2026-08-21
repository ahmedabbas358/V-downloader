package com.junkfood.seal.audio.musicremoval.preprocessor

import android.util.Log
import com.junkfood.seal.util.FFmpegManager
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

/**
 * BsRoFormerAudioPreprocessor
 *
 * Decodes, converts, and normalizes arbitrary media files into clean, calibrated stereo float PCM
 * ready for BS-RoFormer neural inference.
 */
object BsRoFormerAudioPreprocessor {

    private const val TAG = "BsRoFormerAudioPreproc"

    data class PreprocessedAudio(
        val leftChannel: FloatArray,
        val rightChannel: FloatArray,
        val sampleRate: Int,
        val channels: Int,
        val metadata: AudioMetadata,
        val normalizationGain: Float
    )

    /**
     * Decodes and pre-processes input media into calibrated stereo float arrays.
     */
    suspend fun preprocess(
        inputFile: File,
        tempDecodedWav: File,
        targetSampleRate: Int = 44100
    ): PreprocessedAudio {
        Log.d(TAG, "Preprocessing ${inputFile.name} for BS-RoFormer separation...")

        // Decode source container to standard 16-bit Float PCM stereo WAV via FFmpeg
        FFmpegManager.decodeToPcmWav(
            inputFile = inputFile,
            outputWav = tempDecodedWav,
            sampleRate = targetSampleRate,
            channels = 2
        ).getOrThrow()

        val (rawLeft, rawRight) = readPcmWav(tempDecodedWav)

        val totalSamples = minOf(rawLeft.size, rawRight.size)
        val durationMs = if (targetSampleRate > 0) ((totalSamples.toLong() * 1000L) / targetSampleRate) else 0L

        val metadata = AudioMetadata(
            codec = inputFile.extension.uppercase(),
            sampleRate = targetSampleRate,
            channels = 2,
            durationMs = durationMs,
            format = inputFile.extension.lowercase(),
            bitDepth = 16,
            fileSizeBytes = inputFile.length()
        )

        // Peak normalization
        var peak = 0.0f
        for (i in 0 until totalSamples) {
            val aL = abs(rawLeft[i])
            val aR = abs(rawRight[i])
            if (aL > peak) peak = aL
            if (aR > peak) peak = aR
        }

        val targetPeak = 0.95f
        val gain = if (peak > 1e-4f && peak < targetPeak) {
            (targetPeak / peak).coerceIn(1.0f, 3.0f)
        } else if (peak > 1.0f) {
            targetPeak / peak
        } else {
            1.0f
        }

        val normLeft = FloatArray(totalSamples)
        val normRight = FloatArray(totalSamples)
        for (i in 0 until totalSamples) {
            normLeft[i] = (rawLeft[i] * gain).coerceIn(-1.0f, 1.0f)
            normRight[i] = (rawRight[i] * gain).coerceIn(-1.0f, 1.0f)
        }

        return PreprocessedAudio(
            leftChannel = normLeft,
            rightChannel = normRight,
            sampleRate = targetSampleRate,
            channels = 2,
            metadata = metadata,
            normalizationGain = gain
        )
    }

    /**
     * Directly pre-processes in-memory channel arrays.
     */
    fun preprocessChannels(
        leftChannel: FloatArray,
        rightChannel: FloatArray,
        sampleRate: Int = 44100
    ): PreprocessedAudio {
        val totalSamples = minOf(leftChannel.size, rightChannel.size)
        var peak = 0.0f
        for (i in 0 until totalSamples) {
            val aL = abs(leftChannel[i])
            val aR = abs(rightChannel[i])
            if (aL > peak) peak = aL
            if (aR > peak) peak = aR
        }

        val targetPeak = 0.95f
        val gain = if (peak > 1e-4f && peak < targetPeak) {
            (targetPeak / peak).coerceIn(1.0f, 3.0f)
        } else if (peak > 1.0f) {
            targetPeak / peak
        } else {
            1.0f
        }

        val normLeft = FloatArray(totalSamples)
        val normRight = FloatArray(totalSamples)
        for (i in 0 until totalSamples) {
            normLeft[i] = (leftChannel[i] * gain).coerceIn(-1.0f, 1.0f)
            normRight[i] = (rightChannel[i] * gain).coerceIn(-1.0f, 1.0f)
        }

        val durationMs = if (sampleRate > 0) ((totalSamples.toLong() * 1000L) / sampleRate) else 0L

        return PreprocessedAudio(
            leftChannel = normLeft,
            rightChannel = normRight,
            sampleRate = sampleRate,
            channels = 2,
            metadata = AudioMetadata(
                codec = "PCM",
                sampleRate = sampleRate,
                channels = 2,
                durationMs = durationMs,
                format = "wav"
            ),
            normalizationGain = gain
        )
    }

    /**
     * Inverts the normalization gain applied during preprocessing.
     */
    fun restoreGain(
        leftChannel: FloatArray,
        rightChannel: FloatArray,
        normalizationGain: Float
    ): Pair<FloatArray, FloatArray> {
        if (normalizationGain <= 0.0f || abs(normalizationGain - 1.0f) < 1e-4f) {
            return Pair(leftChannel, rightChannel)
        }

        val invGain = 1.0f / normalizationGain
        val numSamples = minOf(leftChannel.size, rightChannel.size)
        val outL = FloatArray(numSamples)
        val outR = FloatArray(numSamples)

        for (i in 0 until numSamples) {
            outL[i] = (leftChannel[i] * invGain).coerceIn(-1.0f, 1.0f)
            outR[i] = (rightChannel[i] * invGain).coerceIn(-1.0f, 1.0f)
        }

        return Pair(outL, outR)
    }

    /**
     * Reads a standard 16-bit little-endian stereo PCM WAV file into normalized Float arrays (-1.0 to 1.0).
     */
    fun readPcmWav(wavFile: File): Pair<FloatArray, FloatArray> {
        val bytes = wavFile.readBytes()
        if (bytes.size < 44) throw IllegalArgumentException("Invalid WAV header: file too small")

        var dataOffset = 12
        var dataSize = 0
        while (dataOffset < bytes.size - 8) {
            val chunkId = String(bytes, dataOffset, 4)
            val chunkSize = ByteBuffer.wrap(bytes, dataOffset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (chunkId.equals("data", ignoreCase = true)) {
                dataOffset += 8
                dataSize = chunkSize.coerceAtMost(bytes.size - dataOffset)
                break
            }
            dataOffset += 8 + max(0, chunkSize)
        }

        if (dataSize <= 0) {
            dataOffset = 44
            dataSize = max(0, bytes.size - 44)
        }

        val numSamples = dataSize / 4
        val left = FloatArray(numSamples)
        val right = FloatArray(numSamples)

        val buffer = ByteBuffer.wrap(bytes, dataOffset, dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until numSamples) {
            if (buffer.remaining() >= 4) {
                left[i] = (buffer.short.toFloat() / 32768.0f).coerceIn(-1.0f, 1.0f)
                right[i] = (buffer.short.toFloat() / 32768.0f).coerceIn(-1.0f, 1.0f)
            }
        }

        return Pair(left, right)
    }
}
