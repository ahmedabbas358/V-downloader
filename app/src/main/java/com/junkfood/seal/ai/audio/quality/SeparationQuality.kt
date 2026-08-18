package com.junkfood.seal.ai.audio.quality

/**
 * SeparationQuality
 *
 * Concrete metrics representing the acoustic quality and separation performance
 * of an isolated speech/vocal stem.
 */
data class SeparationQuality(
    val speechQuality: Float, // Estimated speech clarity score (0.0 .. 1.0)
    val musicResidual: Float, // Estimated residual music energy (0.0 .. 1.0)
    val artifactLevel: Float, // Estimated phase/distortion artifact level (0.0 .. 1.0)
    val confidence: Float,    // Model certainty and evaluation confidence (0.0 .. 1.0)
)
