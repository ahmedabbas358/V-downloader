package com.junkfood.seal.audio.musicremoval.engine

import android.util.Log
import com.junkfood.seal.util.FFmpegManager
import com.junkfood.seal.util.FileUtil
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * BsRoFormerAudioExporter
 *
 * Handles export, format conversion, WAV serialization, video remuxing, and atomic file finalization.
 */
object BsRoFormerAudioExporter {

    private const val TAG = "BsRoFormerAudioExporter"

    /**
     * Exports processed PCM channels to the final media output file with atomic guarantees.
     */
    suspend fun exportMedia(
        leftChannel: FloatArray,
        rightChannel: FloatArray,
        sourceFile: File,
        targetOutputFile: File,
        isAudioOnly: Boolean,
        tempWorkDir: File,
        sampleRate: Int = 44100,
        bitrate: String = "256k"
    ): File {
        val cleanWav = File(tempWorkDir, "bs_roformer_clean.wav")
        val finalTempOutput = File(tempWorkDir, "bs_roformer_final.${sourceFile.extension}")

        // 1. Serialize float channels to standard 16-bit PCM WAV
        writePcmWav(
            outputFile = cleanWav,
            left = leftChannel,
            right = rightChannel,
            sampleRate = sampleRate
        )

        if (!cleanWav.exists() || cleanWav.length() < 100L) {
            throw BsRoFormerException(
                BsRoFormerErrorCode.OUTPUT_WRITE_FAILED,
                "Failed to write intermediate clean WAV file"
            )
        }

        val isVideo = !isAudioOnly && FileUtil.isVideoFile(sourceFile)

        // 2. Encode or Remux via FFmpeg
        if (isVideo) {
            Log.d(TAG, "Remuxing clean audio with original video: ${sourceFile.name}")
            FFmpegManager.remuxVideoWithNewAudio(
                originalVideo = sourceFile,
                newAudioWav = cleanWav,
                outputFile = finalTempOutput,
                audioBitrate = bitrate
            ).getOrThrow()
        } else {
            Log.d(TAG, "Encoding clean audio to target format: ${sourceFile.name}")
            FFmpegManager.encodePcmToAudio(
                wavFile = cleanWav,
                outputFile = finalTempOutput,
                bitrate = bitrate
            ).getOrThrow()
        }

        if (!finalTempOutput.exists() || finalTempOutput.length() == 0L) {
            throw BsRoFormerException(
                BsRoFormerErrorCode.OUTPUT_INVALID,
                "Encoded output file is empty or missing"
            )
        }

        // 3. Atomic finalize to target output file
        atomicFinalize(finalTempOutput, targetOutputFile)

        return targetOutputFile
    }

    /**
     * Atomically replaces target file with source temporary file.
     */
    fun atomicFinalize(tempFile: File, destinationFile: File) {
        if (!tempFile.exists() || tempFile.length() == 0L) {
            throw IllegalArgumentException("Cannot finalize non-existent or empty temp file: ${tempFile.name}")
        }

        val backupFile = File(destinationFile.parentFile, "${destinationFile.name}.bak_${System.currentTimeMillis()}")
        var backupCreated = false

        try {
            if (destinationFile.exists()) {
                if (destinationFile.renameTo(backupFile)) {
                    backupCreated = true
                }
            }

            if (!tempFile.renameTo(destinationFile)) {
                tempFile.copyTo(destinationFile, overwrite = true)
                tempFile.delete()
            }

            if (backupCreated && backupFile.exists()) {
                backupFile.delete()
            }
        } catch (e: Exception) {
            if (backupCreated && backupFile.exists() && !destinationFile.exists()) {
                backupFile.renameTo(destinationFile)
            }
            throw BsRoFormerException(
                BsRoFormerErrorCode.OUTPUT_WRITE_FAILED,
                "Atomic finalization failed: ${e.message}",
                e
            )
        }
    }

    /**
     * Writes stereo float arrays to standard 16-bit little-endian PCM WAV.
     */
    fun writePcmWav(
        outputFile: File,
        left: FloatArray,
        right: FloatArray,
        sampleRate: Int = 44100
    ) {
        val numSamples = minOf(left.size, right.size)
        val dataSize = numSamples * 4
        val totalSize = 36 + dataSize

        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(totalSize)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1) // PCM
            putShort(2) // Stereo
            putInt(sampleRate)
            putInt(sampleRate * 4)
            putShort(4)
            putShort(16)
            put("data".toByteArray())
            putInt(dataSize)
        }

        FileOutputStream(outputFile).use { fos ->
            fos.write(header.array())
            val sampleBuffer = ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until numSamples) {
                val sL = (left[i].coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()
                val sR = (right[i].coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()
                sampleBuffer.putShort(sL)
                sampleBuffer.putShort(sR)

                if (!sampleBuffer.hasRemaining()) {
                    fos.write(sampleBuffer.array())
                    sampleBuffer.clear()
                }
            }
            if (sampleBuffer.position() > 0) {
                fos.write(sampleBuffer.array(), 0, sampleBuffer.position())
            }
        }
    }
}
