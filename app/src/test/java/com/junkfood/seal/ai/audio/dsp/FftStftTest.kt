package com.junkfood.seal.ai.audio.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class FftStftTest {

    @Test
    fun testFftRoundTrip() {
        val n = 1024
        val originalReal = FloatArray(n) { i ->
            (sin(2.0 * PI * 440.0 * i / 44100.0) + 0.5 * sin(2.0 * PI * 1000.0 * i / 44100.0)).toFloat()
        }
        val real = originalReal.copyOf()
        val imag = FloatArray(n)

        // Forward FFT
        FFT.forward(real, imag)

        // Inverse FFT
        FFT.inverse(real, imag)

        // Check reconstruction error
        var maxError = 0.0f
        for (i in 0 until n) {
            val err = abs(real[i] - originalReal[i])
            if (err > maxError) maxError = err
        }

        assertTrue("FFT round-trip reconstruction error should be < 1e-4, got $maxError", maxError < 1e-4f)
    }

    @Test
    fun testStftIstftReconstruction() {
        val totalSamples = 44100 // 1 second
        val signal = FloatArray(totalSamples) { i ->
            sin(2.0 * PI * 440.0 * i / 44100.0).toFloat()
        }

        val nFft = 2048
        val hopLength = 512

        val spec = STFT.stft(signal, nFft = nFft, hopLength = hopLength)
        val reconstructed = STFT.istft(spec, nFft = nFft, hopLength = hopLength, targetLength = totalSamples)

        assertEquals(totalSamples, reconstructed.size)

        // Check middle region (avoiding boundary windowing edge effects)
        var maxError = 0.0f
        for (i in (nFft until totalSamples - nFft)) {
            val err = abs(reconstructed[i] - signal[i])
            if (err > maxError) maxError = err
        }

        assertTrue("STFT/iSTFT round-trip error should be < 1e-3, got $maxError", maxError < 1e-3f)
    }
}
