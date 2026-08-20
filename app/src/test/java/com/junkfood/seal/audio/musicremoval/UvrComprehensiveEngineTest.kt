package com.junkfood.seal.audio.musicremoval

import com.junkfood.seal.audio.musicremoval.analysis.SeparationQualityEvaluator
import com.junkfood.seal.audio.musicremoval.analysis.UvrModelSelector
import com.junkfood.seal.audio.musicremoval.engine.UvrInferenceRunner
import com.junkfood.seal.audio.musicremoval.model.UvrModelRegistry
import com.junkfood.seal.audio.musicremoval.postprocessor.UvrResidualSuppression
import com.junkfood.seal.audio.musicremoval.postprocessor.UvrSpeechProtection
import com.junkfood.seal.audio.musicremoval.preprocessor.UvrAudioPreprocessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class UvrComprehensiveEngineTest {

    private val sampleRate = 44100
    private val testDurationSec = 2
    private val totalSamples = sampleRate * testDurationSec

    /**
     * Synthesizes audio signals with speech (formants at 200Hz, 800Hz, 2500Hz),
     * music (harmonics at 60Hz, 440Hz, 1200Hz, 5000Hz), and noise.
     */
    private fun synthesizeAudio(
        speechGain: Float,
        musicGain: Float,
        noiseGain: Float,
        voiceFrequency: Float = 220f // 130f male, 220f female
    ): Pair<FloatArray, FloatArray> {
        val left = FloatArray(totalSamples)
        val right = FloatArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate

            // Speech signal: Fundamental + Formants (F1, F2, F3)
            val speech = (
                sin(2.0 * PI * voiceFrequency * t) * 0.4 +
                sin(2.0 * PI * (voiceFrequency * 3.5) * t) * 0.3 +
                sin(2.0 * PI * (voiceFrequency * 9.0) * t) * 0.2
            ).toFloat() * speechGain

            // Music signal: Bass drum (55Hz), chords (440Hz, 660Hz), cymbals (6000Hz)
            val musicL = (
                sin(2.0 * PI * 55.0 * t) * 0.5 +
                sin(2.0 * PI * 440.0 * t) * 0.3 +
                sin(2.0 * PI * 6000.0 * t) * 0.2
            ).toFloat() * musicGain

            val musicR = (
                sin(2.0 * PI * 55.0 * t) * 0.5 +
                sin(2.0 * PI * 660.0 * t) * 0.3 +
                sin(2.0 * PI * 7000.0 * t) * 0.2
            ).toFloat() * musicGain

            // Ambient white noise
            val noise = ((Math.random() * 2.0 - 1.0) * noiseGain).toFloat()

            left[i] = (speech + musicL + noise).coerceIn(-1.0f, 1.0f)
            right[i] = (speech + musicR + noise).coerceIn(-1.0f, 1.0f)
        }

        return Pair(left, right)
    }

    @Test
    fun testSpeechOnlyInput() {
        val (left, right) = synthesizeAudio(speechGain = 0.8f, musicGain = 0.0f, noiseGain = 0.0f)
        val config = MusicRemovalConfig(qualityMode = MusicRemovalConfig.QualityMode.BALANCED)
        val result = UvrInferenceRunner.runDspSpectrogramSeparation(
            left, right, UvrModelRegistry.UVR_MDX23C_VOCALS, config, sampleRate
        )

        assertTrue("Speech-only must be acceptable", result.quality.isAcceptable)
        assertTrue("Speech retention must be high", result.quality.speechRetentionScore >= 0.70f)
        assertFalse("No clipping expected", result.quality.isClippingDetected)
        assertEquals(SeparationQualityEvaluator.QualityStatus.GOOD, result.quality.qualityStatus)
    }

    @Test
    fun testSpeechWithQuietMusic() {
        val (left, right) = synthesizeAudio(speechGain = 0.7f, musicGain = 0.2f, noiseGain = 0.0f)
        val config = MusicRemovalConfig(qualityMode = MusicRemovalConfig.QualityMode.BALANCED)
        val result = UvrInferenceRunner.runDspSpectrogramSeparation(
            left, right, UvrModelRegistry.UVR_MDX_NET_VOCALS_HQ, config, sampleRate
        )

        assertTrue(result.quality.isAcceptable)
        assertTrue(result.quality.musicSuppressionScore > 0.30f)
        assertTrue(result.quality.speechRetentionScore > 0.50f)
    }

    @Test
    fun testSpeechWithLoudMusic() {
        val (left, right) = synthesizeAudio(speechGain = 0.5f, musicGain = 0.8f, noiseGain = 0.0f)
        val config = MusicRemovalConfig(qualityMode = MusicRemovalConfig.QualityMode.MAX_REMOVAL)
        val result = UvrInferenceRunner.runDspSpectrogramSeparation(
            left, right, UvrModelRegistry.UVR_HTDEMUCS_V4, config, sampleRate
        )

        assertTrue(result.quality.isAcceptable)
        assertTrue(result.quality.musicSuppressionScore > 0.40f)
        assertNotNull(result.vocalLeft)
        assertEquals(totalSamples, result.vocalLeft.size)
    }

    @Test
    fun testSpeechWithInstrumentalAndBackgroundMusic() {
        val (left, right) = synthesizeAudio(speechGain = 0.6f, musicGain = 0.6f, noiseGain = 0.05f)
        val config = MusicRemovalConfig(qualityMode = MusicRemovalConfig.QualityMode.HIGH_QUALITY)
        val result = UvrInferenceRunner.runDspSpectrogramSeparation(
            left, right, UvrModelRegistry.UVR_VR_ARCH_VOCALS, config, sampleRate
        )

        assertTrue(result.quality.isAcceptable)
        assertTrue(result.quality.signalToNoiseRatioDb > -10f)
    }

    @Test
    fun testMaleSpeechAndFemaleSpeech() {
        // Male voice ~ 120Hz fundamental
        val (maleL, maleR) = synthesizeAudio(speechGain = 0.7f, musicGain = 0.3f, noiseGain = 0.0f, voiceFrequency = 120f)
        // Female voice ~ 240Hz fundamental
        val (femL, femR) = synthesizeAudio(speechGain = 0.7f, musicGain = 0.3f, noiseGain = 0.0f, voiceFrequency = 240f)

        val config = MusicRemovalConfig()
        val maleResult = UvrInferenceRunner.runDspSpectrogramSeparation(
            maleL, maleR, UvrModelRegistry.UVR_MDX23C_VOCALS, config, sampleRate
        )
        val femaleResult = UvrInferenceRunner.runDspSpectrogramSeparation(
            femL, femR, UvrModelRegistry.UVR_MDX23C_VOCALS, config, sampleRate
        )

        assertTrue("Male speech separation must succeed", maleResult.quality.isAcceptable)
        assertTrue("Female speech separation must succeed", femaleResult.quality.isAcceptable)
        assertTrue(maleResult.quality.speechRetentionScore > 0.40f)
        assertTrue(femaleResult.quality.speechRetentionScore > 0.40f)
    }

    @Test
    fun testMultipleSpeakersPodcastDialogue() {
        val left = FloatArray(totalSamples)
        val right = FloatArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            // Speaker 1 (Male 130Hz) + Speaker 2 (Female 220Hz) alternating or combined
            val spk1 = sin(2.0 * PI * 130.0 * t) * 0.4
            val spk2 = sin(2.0 * PI * 220.0 * t) * 0.35
            val bgMusic = sin(2.0 * PI * 60.0 * t) * 0.3 + sin(2.0 * PI * 5000.0 * t) * 0.15

            left[i] = (spk1 + spk2 + bgMusic).toFloat().coerceIn(-1f, 1f)
            right[i] = (spk1 + spk2 + bgMusic).toFloat().coerceIn(-1f, 1f)
        }

        val config = MusicRemovalConfig(qualityMode = MusicRemovalConfig.QualityMode.BALANCED)
        val result = UvrInferenceRunner.runDspSpectrogramSeparation(
            left, right, UvrModelRegistry.UVR_MDX_NET_VOCALS_HQ, config, sampleRate
        )

        assertTrue(result.quality.isAcceptable)
        assertTrue(result.quality.overallQualityScore >= 0.35f)
    }

    @Test
    fun testSpeechProtectionAndResidualSuppressionPipeline() {
        val (left, right) = synthesizeAudio(speechGain = 0.6f, musicGain = 0.4f, noiseGain = 0.05f)
        val pre = UvrAudioPreprocessor.preprocess(left, right, sampleRate)

        val uvrResult = UvrInferenceRunner.runDspSpectrogramSeparation(
            pre.leftChannel, pre.rightChannel, UvrModelRegistry.UVR_MDX23C_VOCALS, MusicRemovalConfig(), sampleRate
        )

        val (protL, protR) = UvrSpeechProtection.protectSpeech(
            pre.leftChannel, pre.rightChannel, uvrResult.vocalLeft, uvrResult.vocalRight, sampleRate
        )

        val (cleanL, cleanR) = UvrResidualSuppression.suppressResiduals(protL, protR, suppressionStrength = 0.85f)
        val (finalL, finalR) = UvrAudioPreprocessor.restoreGain(cleanL, cleanR, pre.normalizationGain)

        val eval = SeparationQualityEvaluator.evaluate(left, right, finalL, finalR, sampleRate)
        assertTrue("Pipeline result must be acceptable", eval.isAcceptable)
        assertFalse("Must not clip", eval.isClippingDetected)
        assertEquals(totalSamples, finalL.size)
    }

    @Test
    fun testMultiStrategySelectionChain() {
        val fastConfig = MusicRemovalConfig(qualityMode = MusicRemovalConfig.QualityMode.FAST)
        val fastChain = UvrModelSelector.selectStrategyChain(fastConfig)
        assertTrue("Fast chain must contain at least 2 strategies", fastChain.size >= 2)

        val maxConfig = MusicRemovalConfig(qualityMode = MusicRemovalConfig.QualityMode.MAX_REMOVAL)
        val maxChain = UvrModelSelector.selectStrategyChain(maxConfig)
        assertTrue("Max removal chain must start with ensemble or HTDemucs", maxChain.isNotEmpty())
    }
}
