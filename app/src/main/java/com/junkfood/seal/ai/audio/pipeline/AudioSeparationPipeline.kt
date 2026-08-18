package com.junkfood.seal.ai.audio.pipeline

import android.content.Context
import android.util.Log
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.ai.audio.dsp.ResidualMusicSuppressor
import com.junkfood.seal.ai.audio.dsp.SpeechEnhancer
import com.junkfood.seal.ai.audio.model.ModelManager
import com.junkfood.seal.ai.audio.model.ModelRegistry
import com.junkfood.seal.ai.audio.separation.AudioInput
import com.junkfood.seal.ai.audio.separation.AudioSeparationEngine
import com.junkfood.seal.ai.audio.separation.DemucsSeparationEngine
import com.junkfood.seal.ai.audio.separation.EnsembleSeparationEngine
import com.junkfood.seal.ai.audio.separation.MdxSeparationEngine
import com.junkfood.seal.ai.audio.separation.NativeDspFallbackSeparationEngine
import com.junkfood.seal.util.FFmpegManager
import com.junkfood.seal.util.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AudioSeparationPipeline
 *
 * Orchestrates end-to-end AI music removal and vocal isolation:
 * Decode -> Neural Separation -> Post-processing -> Lossless Remux / Encode.
 */
object AudioSeparationPipeline {

    private const val TAG = "AudioSeparationPipeline"

    /**
     * Processes a list of media file paths.
     * Overwrites or replaces files with isolated vocal / speech tracks.
     */
    suspend fun processFiles(
        filePaths: List<String>,
        isAudioOnly: Boolean,
        options: SeparationOptions = SeparationOptions(),
        appContext: Context = context,
        onProgress: ((Float, String) -> Unit)? = null,
    ): List<String> = withContext(Dispatchers.IO) {
        val resultPaths = mutableListOf<String>()
        val totalFiles = filePaths.size

        filePaths.forEachIndexed { index, path ->
            val file = File(path)
            if (!file.exists() || file.length() == 0L) {
                Log.w(TAG, "Skipping missing or empty file: $path")
                return@forEachIndexed
            }

            val fileProgressWeight = 1.0f / totalFiles
            val fileProgressBase = index * fileProgressWeight

            onProgress?.invoke(
                fileProgressBase,
                "جاري بدء المعالجة بالذكاء الاصطناعي للملف (${index + 1}/$totalFiles)..."
            )

            try {
                val processedFile = processSingleFile(
                    inputFile = file,
                    isAudioOnly = isAudioOnly,
                    options = options,
                    appContext = appContext,
                    onProgress = { p, msg ->
                        val overallP = fileProgressBase + (p * fileProgressWeight)
                        onProgress?.invoke(overallP, msg)
                    }
                )
                resultPaths.add(processedFile.absolutePath)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to separate audio for ${file.name}", e)
                // Fallback to preserving original file
                resultPaths.add(file.absolutePath)
            }
        }

        onProgress?.invoke(1.0f, "اكتملت معالجة جميع الملفات بنجاح.")
        resultPaths
    }

    /**
     * Processes a single media file.
     */
    suspend fun processSingleFile(
        inputFile: File,
        isAudioOnly: Boolean,
        options: SeparationOptions = SeparationOptions(),
        appContext: Context = context,
        onProgress: ((Float, String) -> Unit)? = null,
    ): File = withContext(Dispatchers.IO) {
        val isVideo = !isAudioOnly && FileUtil.isVideoFile(inputFile)
        val tempDir = File(appContext.cacheDir, "audio_ai_temp_${System.currentTimeMillis()}").apply { mkdirs() }

        val decodedWav = File(tempDir, "decoded_input.wav")
        val cleanWav = File(tempDir, "separated_vocals.wav")
        val finalOutput = File(inputFile.parentFile, "${inputFile.nameWithoutExtension}_clean.${inputFile.extension}")

        try {
            // 1. Decode to 44.1kHz stereo PCM WAV
            onProgress?.invoke(0.05f, "جاري فك تشفير وتجهيز المسار الصوتي...")
            FFmpegManager.decodeToPcmWav(inputFile, decodedWav, sampleRate = 44100, channels = 2)
                .getOrThrow()

            // 2. Read PCM Float samples
            onProgress?.invoke(0.12f, "تحميل إشارات الصوت للذاكرة...")
            val (rawLeft, rawRight) = readPcmWav(decodedWav)

            // 3. Select separation engine
            val isModelReady = ModelManager.isModelAvailable(ModelRegistry.MDX_VOCALS_DEFAULT, appContext)
            val engine: AudioSeparationEngine = if (isModelReady) {
                when {
                    options.enableEnsemble || options.qualityMode == QualityMode.MAX_QUALITY -> {
                        EnsembleSeparationEngine(
                            primaryEngine = MdxSeparationEngine(ModelRegistry.MDX_VOCALS_DEFAULT),
                            secondaryEngine = NativeDspFallbackSeparationEngine
                        )
                    }
                    else -> {
                        MdxSeparationEngine(ModelRegistry.MDX_VOCALS_DEFAULT)
                    }
                }
            } else {
                NativeDspFallbackSeparationEngine
            }

            // 4. Run Separation
            onProgress?.invoke(0.20f, "تشغيل محرك ${engine.engineName}...")
            val audioInput = AudioInput(rawLeft, rawRight, sampleRate = 44100)
            val sepResult = engine.separate(
                input = audioInput,
                options = options,
                onProgress = { p, msg ->
                    onProgress?.invoke(0.20f + p * 0.55f, msg)
                }
            )

            // 5. Post-processing: Strong Residual Music Suppression & Speech Enhancement
            onProgress?.invoke(0.80f, "تطبيق تصفية الترددات المتبقية وتعزيز نقاء الكلام...")
            val suppressionStrength = when (options.qualityMode) {
                QualityMode.FAST -> 0.65f
                QualityMode.BALANCED -> 0.85f
                QualityMode.MAX_QUALITY -> 0.95f
            }
            val (suppLeft, suppRight) = ResidualMusicSuppressor.suppressResidualsStereo(
                leftChannel = sepResult.vocalLeft,
                rightChannel = sepResult.vocalRight,
                sampleRate = 44100,
                suppressionStrength = suppressionStrength
            )

            val (finalLeft, finalRight) = SpeechEnhancer.enhanceStereo(
                left = suppLeft,
                right = suppRight,
                presenceBoostDb = options.speechEnhancementDb.coerceAtLeast(3.0f)
            )

            // 6. Write to clean output WAV
            onProgress?.invoke(0.88f, "تصدير الموجة الصوتية المعالجة...")
            writePcmWav(cleanWav, finalLeft, finalRight, sampleRate = 44100)

            // 7. Re-encode or Lossless Video Remux
            if (isVideo) {
                onProgress?.invoke(0.92f, "دمج المسار الصوتي النقي مع الفيديو (Lossless Remux)...")
                FFmpegManager.remuxVideoWithNewAudio(
                    originalVideo = inputFile,
                    newAudioWav = cleanWav,
                    outputFile = finalOutput
                ).getOrThrow()
            } else {
                onProgress?.invoke(0.92f, "ترميز الملف الصوتي النهائي بدقة عالية...")
                FFmpegManager.encodePcmToAudio(
                    wavFile = cleanWav,
                    outputFile = finalOutput
                ).getOrThrow()
            }

            // Replace original file atomically
            if (finalOutput.exists() && finalOutput.length() > 0L) {
                inputFile.delete()
                finalOutput.renameTo(inputFile)
                Log.d(TAG, "Separation pipeline completed successfully for ${inputFile.name}")
                inputFile
            } else {
                throw IllegalStateException("Final output file generation failed")
            }
        } finally {
            // Clean up temporary workspace
            tempDir.deleteRecursively()
        }
    }

    /**
     * Reads a standard 16-bit PCM stereo WAV file into normalized FloatArray channels (-1.0f..1.0f).
     */
    private fun readPcmWav(wavFile: File): Pair<FloatArray, FloatArray> {
        val bytes = wavFile.readBytes()
        if (bytes.size < 44) throw IllegalArgumentException("Invalid WAV file header")

        // Find "data" chunk
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
            dataOffset += 8 + chunkSize
        }

        if (dataSize <= 0) {
            dataOffset = 44
            dataSize = bytes.size - 44
        }

        val numSamples = dataSize / 4 // 2 channels * 2 bytes per sample (16-bit)
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

    /**
     * Writes stereo FloatArray samples to a standard 16-bit PCM WAV file.
     */
    private fun writePcmWav(
        outputFile: File,
        left: FloatArray,
        right: FloatArray,
        sampleRate: Int = 44100
    ) {
        val numSamples = minOf(left.size, right.size)
        val dataSize = numSamples * 4 // 2 channels * 2 bytes
        val totalSize = 36 + dataSize

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray())
            putInt(totalSize)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16) // Subchunk1Size for PCM
            putShort(1) // AudioFormat: 1 = PCM
            putShort(2) // NumChannels: 2 = Stereo
            putInt(sampleRate) // SampleRate
            putInt(sampleRate * 4) // ByteRate: SampleRate * NumChannels * BitsPerSample/8
            putShort(4) // BlockAlign: NumChannels * BitsPerSample/8
            putShort(16) // BitsPerSample: 16
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
