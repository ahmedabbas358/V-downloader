package com.junkfood.seal.audio.musicremoval

import android.content.Context
import android.util.Log
import com.junkfood.seal.App.Companion.context
import com.junkfood.seal.audio.musicremoval.analysis.AdaptiveSelector
import com.junkfood.seal.audio.musicremoval.analysis.ResidualAnalyzer
import com.junkfood.seal.audio.musicremoval.cache.AudioProcessingCache
import com.junkfood.seal.audio.musicremoval.detection.MusicDetector
import com.junkfood.seal.audio.musicremoval.engine.AudioInput
import com.junkfood.seal.audio.musicremoval.engine.NativeDspEngine
import com.junkfood.seal.audio.musicremoval.engine.SourceSeparationEngine
import com.junkfood.seal.audio.musicremoval.postprocessor.ResidualSuppression
import com.junkfood.seal.audio.musicremoval.postprocessor.SpeechProtection
import com.junkfood.seal.audio.musicremoval.preprocessor.AudioPreprocessor
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
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.CancellationException

/**
 * MusicRemovalEngine
 *
 * The unified facade orchestrating the complete offline-first, device-aware,
 * neural & spectral music removal and speech protection pipeline.
 */
object MusicRemovalEngine {

    private const val TAG = "MusicRemovalEngine"

    /**
     * Processes a list of media file paths.
     */
    suspend fun processFiles(
        filePaths: List<String>,
        isAudioOnly: Boolean,
        config: MusicRemovalConfig = MusicRemovalConfig(),
        appContext: Context = context,
        onProgress: ((Float, String) -> Unit)? = null
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
                "بدء إزالة الموسيقى للملف (${index + 1}/$totalFiles)..."
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
                Log.i(TAG, "Music removal cancelled by user for ${file.name}")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Music removal failed for ${file.name}, preserving original", e)
                resultPaths.add(file.absolutePath)
            }
        }

        onProgress?.invoke(1.0f, "اكتملت إزالة الموسيقى بنجاح.")
        resultPaths
    }

    /**
     * Processes a single audio or video file.
     */
    suspend fun processSingleFile(
        inputFile: File,
        isAudioOnly: Boolean,
        config: MusicRemovalConfig = MusicRemovalConfig(),
        appContext: Context = context,
        onProgress: ((Float, String) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        if (!currentCoroutineContext().isActive) throw CancellationException("Processing cancelled")

        // 1. Cache lookup
        if (config.useResultCaching) {
            val cached = AudioProcessingCache.getCachedFile(inputFile, config, appContext)
            if (cached != null && cached.exists() && cached.length() > 0) {
                Log.d(TAG, "Using cached result for ${inputFile.name}")
                onProgress?.invoke(1.0f, "استعادة النتيجة من الذاكرة المؤقتة...")
                return@withContext cached
            }
        }

        val isVideo = !isAudioOnly && FileUtil.isVideoFile(inputFile)
        val tempDir = File(appContext.cacheDir, "music_rem_${System.currentTimeMillis()}").apply { mkdirs() }

        val decodedWav = File(tempDir, "extracted_raw.wav")
        val cleanWav = File(tempDir, "clean_vocals.wav")
        val finalOutput = File(inputFile.parentFile, "${inputFile.nameWithoutExtension}_temp_clean.${inputFile.extension}")

        try {
            // 2. Extract 16-bit Stereo PCM WAV via FFmpeg
            onProgress?.invoke(0.05f, "استخراج وتجهيز الصوت بدقة 16-bit PCM...")
            FFmpegManager.decodeToPcmWav(inputFile, decodedWav, sampleRate = 44100, channels = 2)
                .getOrThrow()

            if (!currentCoroutineContext().isActive) throw CancellationException("Processing cancelled")

            // 3. Read PCM buffer
            onProgress?.invoke(0.10f, "قراءة الإشارات الصوتية...")
            val (rawL, rawR) = readPcmWav(decodedWav)

            // 4. Preprocess & Normalize
            val preprocessed = AudioPreprocessor.preprocess(rawL, rawR, sampleRate = 44100)

            // 5. Music & Speech Detection Gate
            onProgress?.invoke(0.15f, "تحليل خصائص الموسيقى والكلام...")
            val detection = MusicDetector.analyze(
                leftChannel = preprocessed.leftChannel,
                rightChannel = preprocessed.rightChannel,
                sampleRate = 44100,
                threshold = config.musicDetectionThreshold
            )

            Log.d(TAG, "Music detection: hasMusic=${detection.hasMusic}, score=${detection.musicScore}, speech=${detection.speechScore}")

            val (separatedL, separatedR) = when (val strategy = AdaptiveSelector.selectStrategy(detection, config)) {
                is AdaptiveSelector.Strategy.SkipSeparation -> {
                    Log.d(TAG, "Input has no significant music. Fast-tracking speech audio.")
                    onProgress?.invoke(0.70f, "المقطع خالٍ من الموسيقى، تطبيق تحسين الكلام...")
                    Pair(preprocessed.leftChannel, preprocessed.rightChannel)
                }

                is AdaptiveSelector.Strategy.ExecuteEngine -> {
                    val engine: SourceSeparationEngine = strategy.engine
                    Log.d(TAG, "Executing source separation engine: ${engine.engineName}")
                    onProgress?.invoke(0.20f, "تشغيل محرك ${engine.engineName}...")

                    val audioInput = AudioInput(
                        leftChannel = preprocessed.leftChannel,
                        rightChannel = preprocessed.rightChannel,
                        sampleRate = 44100
                    )

                    var result = engine.separate(audioInput, config) { p, msg ->
                        onProgress?.invoke(0.20f + p * 0.55f, msg)
                    }

                    // 6. Residual Evaluation & Secondary Model Fallback
                    if (!result.quality.isAcceptable && config.secondaryModelPolicy == MusicRemovalConfig.SecondaryModelPolicy.AUTO && engine !is NativeDspEngine) {
                        Log.w(TAG, "Quality score (${result.quality.overallQualityScore}) below threshold, engaging Native DSP fallback...")
                        onProgress?.invoke(0.75f, "تحسين العزل الطيفي عبر المحرك الثانوي...")
                        val dspResult = NativeDspEngine.separate(audioInput, config)
                        result = result.copy(
                            vocalLeft = blendBuffers(result.vocalLeft, dspResult.vocalLeft, 0.60f),
                            vocalRight = blendBuffers(result.vocalRight, dspResult.vocalRight, 0.60f)
                        )
                    }

                    Pair(result.vocalLeft, result.vocalRight)
                }
            }

            if (!currentCoroutineContext().isActive) throw CancellationException("Processing cancelled")

            // 7. Speech Protection (Preserve formants, vowels, consonants, and sibilants)
            onProgress?.invoke(0.80f, "تطبيق حماية مخارج الحروف والكلام البشري...")
            val (protectedL, protectedR) = SpeechProtection.protectSpeech(
                originalLeft = preprocessed.leftChannel,
                originalRight = preprocessed.rightChannel,
                processedLeft = separatedL,
                processedRight = separatedR,
                sampleRate = 44100,
                level = config.speechPreservationLevel,
                presenceBoostDb = config.speechEnhancementDb
            )

            // 8. Residual Suppression
            onProgress?.invoke(0.85f, "تصفية البقايا الموسيقية والضوضاء...")
            val suppressionStrength = when (config.qualityMode) {
                MusicRemovalConfig.QualityMode.FAST -> 0.70f
                MusicRemovalConfig.QualityMode.BALANCED -> 0.85f
                MusicRemovalConfig.QualityMode.HIGH_QUALITY -> 0.90f
                MusicRemovalConfig.QualityMode.MAX_REMOVAL -> 0.95f
            }
            val (cleanL, cleanR) = ResidualSuppression.suppressResiduals(
                leftChannel = protectedL,
                rightChannel = protectedR,
                suppressionStrength = suppressionStrength
            )

            // 9. Restore Dynamic Gain
            val (finalL, finalR) = AudioPreprocessor.restoreGain(cleanL, cleanR, preprocessed.normalizationGain)

            // 10. Write clean WAV
            onProgress?.invoke(0.90f, "تصدير الملف الصوتي الصافي...")
            writePcmWav(cleanWav, finalL, finalR, sampleRate = 44100)

            // 11. Mux or Encode via FFmpeg
            onProgress?.invoke(0.94f, if (isVideo) "دمج الصوت الصافي مع الفيديو الأصلي..." else "تشفير الملف الصوتي النهائي...")
            if (isVideo) {
                FFmpegManager.remuxVideoWithNewAudio(
                    originalVideo = inputFile,
                    newAudioWav = cleanWav,
                    outputFile = finalOutput
                ).getOrThrow()
            } else {
                FFmpegManager.encodePcmToAudio(
                    wavFile = cleanWav,
                    outputFile = finalOutput
                ).getOrThrow()
            }

            // 12. Replace original file atomically (cross-partition safe)
            if (finalOutput.exists() && finalOutput.length() > 0) {
                try {
                    // Try atomic move first (same partition – fast)
                    Files.move(
                        finalOutput.toPath(),
                        inputFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                    )
                } catch (_: Exception) {
                    // Cross-partition fallback: copy then delete
                    finalOutput.inputStream().use { src ->
                        inputFile.outputStream().use { dst -> src.copyTo(dst) }
                    }
                    finalOutput.delete()
                }
            } else {
                throw IllegalStateException("Final output media is empty or missing")
            }

            // 13. Put in cache (re-read file after rename to get correct size)
            if (config.useResultCaching) {
                val cachedFile = File(inputFile.absolutePath)
                if (cachedFile.exists() && cachedFile.length() > 0) {
                    AudioProcessingCache.putInCache(cachedFile, cachedFile, config, appContext)
                }
            }

            onProgress?.invoke(1.0f, "اكتملت إزالة الموسيقى بنجاح.")
            inputFile
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun blendBuffers(b1: FloatArray, b2: FloatArray, w1: Float): FloatArray {
        val n = minOf(b1.size, b2.size)
        val out = FloatArray(n)
        val w2 = 1.0f - w1
        for (i in 0 until n) {
            out[i] = (b1[i] * w1 + b2[i] * w2).coerceIn(-1.0f, 1.0f)
        }
        return out
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
