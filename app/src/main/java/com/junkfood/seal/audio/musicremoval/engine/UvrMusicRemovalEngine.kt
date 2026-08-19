package com.junkfood.seal.audio.musicremoval.engine

import android.content.Context
import android.util.Log
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.audio.musicremoval.MusicRemovalCapabilities
import com.junkfood.seal.audio.musicremoval.MusicRemovalConfig
import com.junkfood.seal.audio.musicremoval.MusicRemovalEngine
import com.junkfood.seal.audio.musicremoval.analysis.SeparationQualityEvaluator
import com.junkfood.seal.audio.musicremoval.analysis.UvrModelSelector
import com.junkfood.seal.audio.musicremoval.cache.UvrFileManager
import com.junkfood.seal.audio.musicremoval.model.UvrModelRegistry
import com.junkfood.seal.audio.musicremoval.postprocessor.UvrResidualSuppression
import com.junkfood.seal.audio.musicremoval.postprocessor.UvrSpeechProtection
import com.junkfood.seal.audio.musicremoval.preprocessor.UvrAudioPreprocessor
import com.junkfood.seal.util.FFmpegManager
import com.junkfood.seal.util.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException

/**
 * UvrMusicRemovalEngine
 *
 * The unified, production-grade Ultimate Vocal Remover (UVR) engine implementation.
 * Performs deep offline neural music removal and speech preservation.
 */
object UvrMusicRemovalEngine : MusicRemovalEngine {

    private const val TAG = "UvrMusicRemovalEngine"

    override val capabilities: MusicRemovalCapabilities
        get() = MusicRemovalCapabilities(
            engineName = "Ultimate Vocal Remover (UVR)",
            isNeuralAccelerated = true,
            supportedModels = UvrModelRegistry.ALL_UVR_MODELS
        )

    /**
     * Processes multiple files sequentially with progress reporting.
     */
    override suspend fun processFiles(
        filePaths: List<String>,
        isAudioOnly: Boolean,
        config: MusicRemovalConfig,
        appContext: Context,
        onProgress: ((Float, String) -> Unit)?
    ): List<String> = withContext(Dispatchers.IO) {
        val resultPaths = mutableListOf<String>()
        val totalFiles = filePaths.size

        filePaths.forEachIndexed { index, path ->
            val file = File(path)
            if (!file.exists() || file.length() == 0L) {
                Log.w(TAG, "Skipping non-existent or empty file: $path")
                return@forEachIndexed
            }

            val fileBaseProgress = index.toFloat() / totalFiles
            val fileWeight = 1.0f / totalFiles

            onProgress?.invoke(
                fileBaseProgress,
                "بدء إزالة الموسيقى عبر UVR للملف (${index + 1}/$totalFiles)..."
            )

            try {
                val processedFile = processSingleFile(
                    inputFile = file,
                    isAudioOnly = isAudioOnly,
                    config = config,
                    appContext = appContext,
                    onProgress = { p, msg ->
                        val overallP = fileBaseProgress + (p * fileWeight)
                        onProgress?.invoke(overallP, msg)
                    }
                )
                resultPaths.add(processedFile.absolutePath)
            } catch (e: CancellationException) {
                Log.i(TAG, "UVR music removal cancelled by user for ${file.name}")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "UVR music removal failed for ${file.name}, preserving original", e)
                resultPaths.add(file.absolutePath)
            }
        }

        onProgress?.invoke(1.0f, "اكتملت إزالة الموسيقى بنجاح عبر UVR.")
        resultPaths
    }

    /**
     * Processes a single media file through the complete UVR pipeline.
     */
    override suspend fun processSingleFile(
        inputFile: File,
        isAudioOnly: Boolean,
        config: MusicRemovalConfig,
        appContext: Context,
        onProgress: ((Float, String) -> Unit)?
    ): File = withContext(Dispatchers.IO) {
        if (!currentCoroutineContext().isActive) throw CancellationException("Processing cancelled")

        // 1. Cache lookup
        if (config.useResultCaching) {
            val cached = UvrFileManager.getCachedFile(inputFile, config, appContext)
            if (cached != null && cached.exists() && cached.length() > 0) {
                Log.d(TAG, "Using cached UVR result for ${inputFile.name}")
                onProgress?.invoke(1.0f, "استعادة النتيجة من الذاكرة المؤقتة (UVR)...")
                return@withContext cached
            }
        }

        val isVideo = !isAudioOnly && FileUtil.isVideoFile(inputFile)
        val tempDir = File(appContext.cacheDir, "uvr_work_${System.currentTimeMillis()}").apply { mkdirs() }

        val decodedWav = File(tempDir, "extracted_raw.wav")
        val cleanWav = File(tempDir, "clean_uvr_vocals.wav")
        val finalTempOutput = File(tempDir, "final_remuxed.${inputFile.extension}")

        try {
            // 2. Decode 16-bit 44.1kHz Stereo PCM WAV via FFmpeg
            onProgress?.invoke(0.05f, "استخراج الإشارة الصوتية بدقة 16-bit PCM...")
            FFmpegManager.decodeToPcmWav(inputFile, decodedWav, sampleRate = 44100, channels = 2)
                .getOrThrow()

            if (!currentCoroutineContext().isActive) throw CancellationException("Processing cancelled")

            // 3. Read PCM WAV Buffer
            onProgress?.invoke(0.10f, "قراءة تدفق الصوت...")
            val (rawL, rawR) = readPcmWav(decodedWav)

            // 4. Preprocess & Normalize
            val preprocessed = UvrAudioPreprocessor.preprocess(rawL, rawR, sampleRate = 44100)

            // 5. Execute UVR Model Strategy
            val strategy = UvrModelSelector.selectStrategy(config)
            Log.d(TAG, "Executing UVR strategy: $strategy")

            val uvrResult: UvrSeparationResult = when (strategy) {
                is UvrModelSelector.Strategy.SingleUvrModel -> {
                    UvrInferenceRunner.runInference(
                        leftChannel = preprocessed.leftChannel,
                        rightChannel = preprocessed.rightChannel,
                        modelSpec = strategy.spec,
                        config = config,
                        sampleRate = 44100,
                        onProgress = onProgress
                    )
                }
                is UvrModelSelector.Strategy.EnsembleUvrModels -> {
                    UvrEnsembleEngine.separateEnsemble(
                        leftChannel = preprocessed.leftChannel,
                        rightChannel = preprocessed.rightChannel,
                        primarySpec = strategy.primarySpec,
                        secondarySpec = strategy.secondarySpec,
                        config = config,
                        sampleRate = 44100,
                        onProgress = onProgress
                    )
                }
            }

            if (!currentCoroutineContext().isActive) throw CancellationException("Processing cancelled")

            // 6. Speech Protection (Formant retention & sibilant protection)
            onProgress?.invoke(0.80f, "حماية مخارج الحروف والكلام البشري...")
            val (protectedL, protectedR) = UvrSpeechProtection.protectSpeech(
                originalLeft = preprocessed.leftChannel,
                originalRight = preprocessed.rightChannel,
                processedLeft = uvrResult.vocalLeft,
                processedRight = uvrResult.vocalRight,
                sampleRate = 44100,
                level = config.speechPreservationLevel,
                presenceBoostDb = config.speechEnhancementDb
            )

            // 7. Residual Suppression (Deep sub-bass & high-frequency cutoff)
            onProgress?.invoke(0.85f, "تصفية البقايا الموسيقية العميقة...")
            val suppressionStrength = when (config.qualityMode) {
                MusicRemovalConfig.QualityMode.FAST -> 0.70f
                MusicRemovalConfig.QualityMode.BALANCED -> 0.85f
                MusicRemovalConfig.QualityMode.HIGH_QUALITY -> 0.90f
                MusicRemovalConfig.QualityMode.MAX_REMOVAL -> 0.95f
            }
            val (cleanL, cleanR) = UvrResidualSuppression.suppressResiduals(
                leftChannel = protectedL,
                rightChannel = protectedR,
                suppressionStrength = suppressionStrength
            )

            // 8. Restore Dynamic Gain
            val (finalL, finalR) = UvrAudioPreprocessor.restoreGain(cleanL, cleanR, preprocessed.normalizationGain)

            // 9. Write Clean WAV
            onProgress?.invoke(0.90f, "تصدير الملف الصوتي الصافي...")
            writePcmWav(cleanWav, finalL, finalR, sampleRate = 44100)

            // 10. Mux or Encode via FFmpeg
            onProgress?.invoke(0.94f, if (isVideo) "دمج الصوت الصافي مع الفيديو الأصلي..." else "تشفير الملف الصوتي النهائي...")
            if (isVideo) {
                FFmpegManager.remuxVideoWithNewAudio(
                    originalVideo = inputFile,
                    newAudioWav = cleanWav,
                    outputFile = finalTempOutput
                ).getOrThrow()
            } else {
                FFmpegManager.encodePcmToAudio(
                    wavFile = cleanWav,
                    outputFile = finalTempOutput
                ).getOrThrow()
            }

            // 11. Atomic Finalize to Target File
            UvrFileManager.atomicFinalize(finalTempOutput, inputFile).getOrThrow()

            // 12. Cache Result
            if (config.useResultCaching) {
                val finalFile = File(inputFile.absolutePath)
                if (finalFile.exists() && finalFile.length() > 0) {
                    UvrFileManager.putInCache(finalFile, finalFile, config, appContext)
                }
            }

            onProgress?.invoke(1.0f, "اكتملت إزالة الموسيقى بنجاح عبر UVR.")
            inputFile
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun readPcmWav(wavFile: File): Pair<FloatArray, FloatArray> {
        val bytes = wavFile.readBytes()
        if (bytes.size < 44) throw IllegalArgumentException("Invalid WAV header")

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

    private fun writePcmWav(
        outputFile: File,
        left: FloatArray,
        right: FloatArray,
        sampleRate: Int = 44100
    ) {
        val numSamples = minOf(left.size, right.size)
        val dataSize = numSamples * 4
        val totalSize = 36 + dataSize

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
