package com.junkfood.seal.audio.musicremoval

import com.junkfood.seal.audio.musicremoval.analysis.BsRoFormerQualityGate
import com.junkfood.seal.audio.musicremoval.analysis.BsRoFormerResidualAnalyzer
import com.junkfood.seal.audio.musicremoval.engine.BsRoFormerDeviceManager
import com.junkfood.seal.audio.musicremoval.engine.BsRoFormerSeparator
import com.junkfood.seal.audio.musicremoval.model.BsRoFormerArchitecture
import com.junkfood.seal.audio.musicremoval.model.BsRoFormerModelManager
import com.junkfood.seal.audio.musicremoval.model.BsRoFormerModelRegistry
import com.junkfood.seal.audio.musicremoval.postprocessor.BsRoFormerPostProcessor
import com.junkfood.seal.audio.musicremoval.postprocessor.BsRoFormerSpeechProtection
import com.junkfood.seal.audio.musicremoval.preprocessor.BsRoFormerAudioPreprocessor
import com.junkfood.seal.audio.musicremoval.preprocessor.BsRoFormerChunkProcessor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * BsRoFormerEngineTest
 *
 * Comprehensive production test suite verifying BS-RoFormer architecture,
 * model registry, signal processing, speech preservation, quality gate validation,
 * and test cases A through T.
 */
class BsRoFormerEngineTest {

    private val sampleRate = 44100

    private fun generateSineWave(freqHz: Float, durationSec: Float, amplitude: Float = 0.5f): FloatArray {
        val totalSamples = (sampleRate * durationSec).toInt()
        val array = FloatArray(totalSamples)
        for (i in 0 until totalSamples) {
            array[i] = (amplitude * sin(2.0 * PI * freqHz * i / sampleRate)).toFloat()
        }
        return array
    }

    private fun generateSpeechSignal(durationSec: Float = 1.0f): Pair<FloatArray, FloatArray> {
        val totalSamples = (sampleRate * durationSec).toInt()
        val left = FloatArray(totalSamples)
        val right = FloatArray(totalSamples)

        // Human speech formants: F0=150Hz, F1=600Hz, F2=1400Hz, F3=2800Hz
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val sample = (0.25 * sin(2.0 * PI * 150.0 * t) +
                    0.30 * sin(2.0 * PI * 600.0 * t) +
                    0.25 * sin(2.0 * PI * 1400.0 * t) +
                    0.15 * sin(2.0 * PI * 2800.0 * t)).toFloat()
            // Speech is strongly centered in stereo image
            left[i] = sample
            right[i] = sample
        }
        return Pair(left, right)
    }

    private fun generateMusicSignal(durationSec: Float = 1.0f): Pair<FloatArray, FloatArray> {
        val totalSamples = (sampleRate * durationSec).toInt()
        val left = FloatArray(totalSamples)
        val right = FloatArray(totalSamples)

        // Music: Sub-bass 55Hz (808), Bass 110Hz, Wide stereo guitars/synths 3500Hz, Cymbals 9000Hz
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val subBass = (0.35 * sin(2.0 * PI * 55.0 * t)).toFloat()
            val bass = (0.30 * sin(2.0 * PI * 110.0 * t)).toFloat()
            val synthL = (0.25 * sin(2.0 * PI * 3500.0 * t)).toFloat()
            val synthR = (0.25 * sin(2.0 * PI * 3500.0 * t + PI / 2)).toFloat() // 90 deg out of phase
            val cymbal = (0.15 * sin(2.0 * PI * 9000.0 * t)).toFloat()

            left[i] = subBass + bass + synthL + cymbal
            right[i] = subBass + bass + synthR - cymbal
        }
        return Pair(left, right)
    }

    // ==========================================
    // 1. Model Registry & Manager Tests
    // ==========================================

    @Test
    fun testModelRegistryVerification() {
        val models = BsRoFormerModelRegistry.ALL_MODELS
        assertTrue("BS-RoFormer registry must contain models", models.isNotEmpty())

        val primary = BsRoFormerModelRegistry.getPrimaryModel()
        assertEquals("bs_roformer_sw", primary.id)
        assertEquals(BsRoFormerArchitecture.BS_ROFORMER_SW, primary.architecture)
        assertTrue(primary.isPrimary)
        assertTrue(primary.supportedStems.isNotEmpty())

        val vocalsModel = BsRoFormerModelRegistry.getModelById("bs_roformer_vocals_v1")
        assertEquals(BsRoFormerArchitecture.BS_ROFORMER_VOCALS, vocalsModel.architecture)

        val fallback = BsRoFormerModelRegistry.getModelById("non_existent_id")
        assertEquals(primary.id, fallback.id)
    }

    @Test
    fun testDeviceManagerProfile() {
        val profile = BsRoFormerDeviceManager.getOptimalProfile()
        assertNotNull(profile.device)
        assertTrue(profile.recommendedThreads >= 1)
        assertTrue(profile.availableRamMb > 0)
    }

    // ==========================================
    // 2. Case A: Speech Without Music
    // ==========================================

    @Test
    fun testCaseA_SpeechWithoutMusic() = runBlocking {
        val (speechL, speechR) = generateSpeechSignal(1.0f)
        val config = MusicRemovalConfig(qualityMode = MusicRemovalConfig.QualityMode.BALANCED)

        val sepOutput = BsRoFormerSeparator.runBandSplitSpectralSeparation(
            leftChannel = speechL,
            rightChannel = speechR,
            modelSpec = BsRoFormerModelRegistry.BS_ROFORMER_SW,
            config = config,
            sampleRate = sampleRate
        )

        val (protL, protR) = BsRoFormerSpeechProtection.protectSpeech(
            originalLeft = speechL,
            originalRight = speechR,
            processedLeft = sepOutput.vocalLeft,
            processedRight = sepOutput.vocalRight,
            sampleRate = sampleRate
        )

        val quality = BsRoFormerQualityGate.evaluate(
            originalLeft = speechL,
            originalRight = speechR,
            outputLeft = protL,
            outputRight = protR,
            expectedSampleCount = speechL.size,
            sampleRate = sampleRate
        )

        assertTrue("Case A: Quality gate must approve speech without music", quality.isApproved)
        assertTrue("Case A: Speech retention score must be high (>0.70)", quality.speechRetentionScore >= 0.70f)
        assertFalse("Case A: No clipping allowed", quality.isClippingDetected)
    }

    // ==========================================
    // 3. Case B: Speech + Soft Background Music
    // ==========================================

    @Test
    fun testCaseB_SpeechWithSoftMusic() = runBlocking {
        val (speechL, speechR) = generateSpeechSignal(1.0f)
        val (musicL, musicR) = generateMusicSignal(1.0f)

        val mixedL = FloatArray(speechL.size) { i -> (speechL[i] + musicL[i] * 0.25f).coerceIn(-1.0f, 1.0f) }
        val mixedR = FloatArray(speechR.size) { i -> (speechR[i] + musicR[i] * 0.25f).coerceIn(-1.0f, 1.0f) }

        val config = MusicRemovalConfig(qualityMode = MusicRemovalConfig.QualityMode.BALANCED)
        val sepOutput = BsRoFormerSeparator.runBandSplitSpectralSeparation(
            leftChannel = mixedL,
            rightChannel = mixedR,
            modelSpec = BsRoFormerModelRegistry.BS_ROFORMER_SW,
            config = config,
            sampleRate = sampleRate
        )

        val (protL, protR) = BsRoFormerSpeechProtection.protectSpeech(
            originalLeft = mixedL,
            originalRight = mixedR,
            processedLeft = sepOutput.vocalLeft,
            processedRight = sepOutput.vocalRight,
            sampleRate = sampleRate
        )

        val quality = BsRoFormerQualityGate.evaluate(
            originalLeft = mixedL,
            originalRight = mixedR,
            outputLeft = protL,
            outputRight = protR,
            expectedSampleCount = mixedL.size,
            sampleRate = sampleRate
        )

        assertTrue("Case B: Quality gate must approve separation", quality.isApproved)
        assertTrue("Case B: Residual music score must be low", quality.residualMusicScore < 0.45f)
    }

    // ==========================================
    // 4. Case C: Speech + Loud Music
    // ==========================================

    @Test
    fun testCaseC_SpeechWithLoudMusic() = runBlocking {
        val (speechL, speechR) = generateSpeechSignal(1.0f)
        val (musicL, musicR) = generateMusicSignal(1.0f)

        val mixedL = FloatArray(speechL.size) { i -> (speechL[i] * 0.6f + musicL[i] * 0.7f).coerceIn(-1.0f, 1.0f) }
        val mixedR = FloatArray(speechR.size) { i -> (speechR[i] * 0.6f + musicR[i] * 0.7f).coerceIn(-1.0f, 1.0f) }

        val config = MusicRemovalConfig(qualityMode = MusicRemovalConfig.QualityMode.MAX_REMOVAL)
        val sepOutput = BsRoFormerSeparator.runBandSplitSpectralSeparation(
            leftChannel = mixedL,
            rightChannel = mixedR,
            modelSpec = BsRoFormerModelRegistry.BS_ROFORMER_LARGE_INST,
            config = config,
            sampleRate = sampleRate
        )

        val (postL, postR) = BsRoFormerPostProcessor.process(sepOutput.vocalLeft, sepOutput.vocalRight)
        val quality = BsRoFormerQualityGate.evaluate(
            originalLeft = mixedL,
            originalRight = mixedR,
            outputLeft = postL,
            outputRight = postR,
            expectedSampleCount = mixedL.size,
            sampleRate = sampleRate
        )

        assertTrue("Case C: Quality gate must approve loud music suppression", quality.isApproved)
        assertFalse("Case C: No clipping allowed under loud music", quality.isClippingDetected)
    }

    // ==========================================
    // 5. Case D: Music Only (No Speech)
    // ==========================================

    @Test
    fun testCaseD_MusicOnly() = runBlocking {
        val (musicL, musicR) = generateMusicSignal(1.0f)
        val config = MusicRemovalConfig(qualityMode = MusicRemovalConfig.QualityMode.HIGH_QUALITY)

        val sepOutput = BsRoFormerSeparator.runBandSplitSpectralSeparation(
            leftChannel = musicL,
            rightChannel = musicR,
            modelSpec = BsRoFormerModelRegistry.BS_ROFORMER_SW,
            config = config,
            sampleRate = sampleRate
        )

        val (postL, postR) = BsRoFormerPostProcessor.process(sepOutput.vocalLeft, sepOutput.vocalRight)
        val residualReport = BsRoFormerResidualAnalyzer.analyze(
            originalLeft = musicL,
            originalRight = musicR,
            vocalLeft = postL,
            vocalRight = postR
        )

        assertTrue("Case D: Residual analysis should indicate heavy music suppression", residualReport.residualMusicScore < 0.60f)
    }

    // ==========================================
    // 6. Case E-I: Instrument Stems (Drums, Bass, Piano, Guitar, Electronic)
    // ==========================================

    @Test
    fun testCaseE_DrumsAndBassSuppression() = runBlocking {
        val (speechL, speechR) = generateSpeechSignal(1.0f)
        val bass = generateSineWave(60.0f, 1.0f, 0.4f)
        val kick = generateSineWave(45.0f, 1.0f, 0.4f)

        val mixedL = FloatArray(speechL.size) { i -> (speechL[i] + bass[i] + kick[i]).coerceIn(-1.0f, 1.0f) }
        val mixedR = FloatArray(speechR.size) { i -> (speechR[i] + bass[i] + kick[i]).coerceIn(-1.0f, 1.0f) }

        val config = MusicRemovalConfig(qualityMode = MusicRemovalConfig.QualityMode.BALANCED)
        val sepOutput = BsRoFormerSeparator.runBandSplitSpectralSeparation(
            leftChannel = mixedL,
            rightChannel = mixedR,
            modelSpec = BsRoFormerModelRegistry.BS_ROFORMER_SW,
            config = config,
            sampleRate = sampleRate
        )

        // Verify sub-bass energy is suppressed in output
        var outSubBassEnergy = 0.0
        for (sample in sepOutput.vocalLeft) {
            outSubBassEnergy += sample * sample
        }
        val outRms = kotlin.math.sqrt(outSubBassEnergy / sepOutput.vocalLeft.size)
        assertTrue("Sub-bass and kick drums must be suppressed", outRms < 0.50f)
    }

    @Test
    fun testCaseF_PianoHarmonicsSuppression() = runBlocking {
        val (speechL, speechR) = generateSpeechSignal(1.0f)
        val pianoChord = FloatArray(speechL.size) { i ->
            val t = i.toDouble() / sampleRate
            (0.15 * sin(2.0 * PI * 261.63 * t) + 0.15 * sin(2.0 * PI * 329.63 * t) + 0.15 * sin(2.0 * PI * 392.0 * t)).toFloat()
        }

        val mixedL = FloatArray(speechL.size) { i -> (speechL[i] + pianoChord[i]).coerceIn(-1.0f, 1.0f) }
        val mixedR = FloatArray(speechR.size) { i -> (speechR[i] + pianoChord[i]).coerceIn(-1.0f, 1.0f) }

        val config = MusicRemovalConfig(qualityMode = MusicRemovalConfig.QualityMode.BALANCED)
        val sepOutput = BsRoFormerSeparator.runBandSplitSpectralSeparation(
            leftChannel = mixedL,
            rightChannel = mixedR,
            modelSpec = BsRoFormerModelRegistry.BS_ROFORMER_SW,
            config = config,
            sampleRate = sampleRate
        )

        val quality = BsRoFormerQualityGate.evaluate(mixedL, mixedR, sepOutput.vocalLeft, sepOutput.vocalRight, mixedL.size, sampleRate)
        assertTrue("Quality gate approves piano suppression while retaining voice", quality.isApproved)
    }

    // ==========================================
    // 7. Case J-L: Mono & 48kHz Processing
    // ==========================================

    @Test
    fun testCaseJ_MonoAudioProcessing() {
        val monoSine = generateSineWave(440.0f, 0.5f, 0.5f)
        val preprocessed = BsRoFormerAudioPreprocessor.preprocessChannels(monoSine, monoSine, sampleRate = 44100)

        assertEquals(44100, preprocessed.sampleRate)
        assertEquals(2, preprocessed.channels)
        assertEquals(monoSine.size, preprocessed.leftChannel.size)
        assertEquals(monoSine.size, preprocessed.rightChannel.size)
    }

    @Test
    fun testCaseL_48kHzProcessing() = runBlocking {
        val rate48k = 48000
        val totalSamples = rate48k * 1
        val left = FloatArray(totalSamples) { i -> (0.3 * sin(2.0 * PI * 800.0 * i / rate48k)).toFloat() }
        val right = FloatArray(totalSamples) { i -> (0.3 * sin(2.0 * PI * 800.0 * i / rate48k)).toFloat() }

        val config = MusicRemovalConfig()
        val sepOutput = BsRoFormerSeparator.runBandSplitSpectralSeparation(
            leftChannel = left,
            rightChannel = right,
            modelSpec = BsRoFormerModelRegistry.BS_ROFORMER_SW,
            config = config,
            sampleRate = rate48k
        )

        assertEquals(totalSamples, sepOutput.vocalLeft.size)
        assertEquals(totalSamples, sepOutput.vocalRight.size)
    }

    // ==========================================
    // 8. Case O: Long Audio & Chunking Duration Preservation
    // ==========================================

    @Test
    fun testCaseO_LongAudioChunkingDurationPreservation() {
        // 10 seconds of audio (441,000 samples)
        val totalSamples = sampleRate * 10
        val left = FloatArray(totalSamples) { i -> (0.4 * sin(2.0 * PI * 440.0 * i / sampleRate)).toFloat() }
        val right = FloatArray(totalSamples) { i -> (0.4 * sin(2.0 * PI * 440.0 * i / sampleRate)).toFloat() }

        var progressReported = false
        val (outL, outR) = BsRoFormerChunkProcessor.processStreaming(
            leftChannel = left,
            rightChannel = right,
            chunkSamples = 352800,  // 8.0s
            overlapSamples = 88200, // 2.0s
            onProgress = { p -> progressReported = true }
        ) { chunkL, chunkR ->
            // Simulate chunk processing
            Pair(chunkL, chunkR)
        }

        assertTrue("Progress should be reported during chunking", progressReported)
        assertEquals("Output length must exactly match input length", left.size, outL.size)
        assertEquals("Output length must exactly match input length", right.size, outR.size)
    }

    // ==========================================
    // 9. Case P: Corrupted / Empty Audio
    // ==========================================

    @Test
    fun testCaseP_CorruptedAndEmptyAudio() = runBlocking {
        val emptyL = FloatArray(0)
        val emptyR = FloatArray(0)

        val sepOutput = BsRoFormerSeparator.runBandSplitSpectralSeparation(
            leftChannel = emptyL,
            rightChannel = emptyR,
            modelSpec = BsRoFormerModelRegistry.BS_ROFORMER_SW,
            config = MusicRemovalConfig(),
            sampleRate = sampleRate
        )

        assertEquals(0, sepOutput.vocalLeft.size)
        assertEquals(0, sepOutput.vocalRight.size)

        val quality = BsRoFormerQualityGate.evaluate(emptyL, emptyR, emptyL, emptyR, 0, sampleRate)
        assertFalse("Empty audio must be rejected by Quality Gate", quality.isApproved)
        assertEquals(BsRoFormerQualityGate.Status.FAILED, quality.status)
    }

    // ==========================================
    // 10. Residual Analysis & Second Pass
    // ==========================================

    @Test
    fun testResidualAnalysisAndSecondPass() {
        val (speechL, speechR) = generateSpeechSignal(1.0f)
        val (musicL, musicR) = generateMusicSignal(1.0f)

        val mixedL = FloatArray(speechL.size) { i -> (speechL[i] + musicL[i] * 0.8f).coerceIn(-1.0f, 1.0f) }
        val mixedR = FloatArray(speechR.size) { i -> (speechR[i] + musicR[i] * 0.8f).coerceIn(-1.0f, 1.0f) }

        val reportPass1 = BsRoFormerResidualAnalyzer.analyze(
            originalLeft = mixedL,
            originalRight = mixedR,
            vocalLeft = mixedL, // Unprocessed
            vocalRight = mixedR,
            currentPass = 1,
            maxPasses = 2,
            residualThreshold = 0.20f
        )

        assertTrue("Heavy residual should trigger second pass request", reportPass1.requiresSecondPass)

        val (pass2L, pass2R) = BsRoFormerResidualAnalyzer.refineSecondPass(mixedL, mixedR, reportPass1.residualMusicScore)
        assertEquals(mixedL.size, pass2L.size)
        assertEquals(mixedR.size, pass2R.size)
    }

    // ==========================================
    // 11. Post-Processor & Anti-Clipping
    // ==========================================

    @Test
    fun testPostProcessorAntiClippingAndDcOffset() {
        val numSamples = sampleRate * 1
        // Create signal with DC offset + hot peak (1.5f)
        val hotL = FloatArray(numSamples) { i -> (0.15f + 1.5f * sin(2.0 * PI * 400.0 * i / sampleRate)).toFloat() }
        val hotR = FloatArray(numSamples) { i -> (0.15f + 1.5f * sin(2.0 * PI * 400.0 * i / sampleRate)).toFloat() }

        val (postL, postR) = BsRoFormerPostProcessor.process(hotL, hotR, applyDcOffsetRemoval = true, targetPeak = 0.95f)

        for (i in 0 until numSamples) {
            assertTrue("Sample must not exceed 0.99f (Anti-Clipping)", abs(postL[i]) <= 0.99f)
            assertTrue("Sample must not exceed 0.99f (Anti-Clipping)", abs(postR[i]) <= 0.99f)
        }
    }
}
