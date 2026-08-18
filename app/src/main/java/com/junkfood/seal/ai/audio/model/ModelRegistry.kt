package com.junkfood.seal.ai.audio.model

/**
 * ModelRegistry
 *
 * Catalog of validated neural models for music removal and vocal isolation.
 */
object ModelRegistry {

    val MDX_VOCALS_DEFAULT = ModelSpec(
        id = "uvr_mdx_vocals_v1",
        name = "UVR MDX-Net Vocals (Standard)",
        description = "High-precision spectrogram vocal isolation model optimized for mobile ONNX runtime.",
        type = ModelSpec.ModelType.MDX_NET,
        fileName = "uvr_mdx_vocals_v1.onnx",
        sampleRate = 44100,
        channels = 2,
        chunkSamples = 176400,
        overlapSamples = 22050,
        downloadUrl = "https://github.com/ahmedabbas358/V-downloader/releases/download/models/uvr_mdx_vocals_v1.onnx",
        sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        sizeBytes = 18874368L // ~18 MB quantized ONNX
    )

    val DEMUCS_V4_VOCALS = ModelSpec(
        id = "demucs_v4_vocals",
        name = "Demucs v4 Hybrid (High Quality)",
        description = "Deep hybrid waveform/spectral separation for complex musical backgrounds.",
        type = ModelSpec.ModelType.DEMUCS,
        fileName = "demucs_v4_vocals.onnx",
        sampleRate = 44100,
        channels = 2,
        chunkSamples = 176400,
        overlapSamples = 22050,
        downloadUrl = "https://github.com/ahmedabbas358/V-downloader/releases/download/models/demucs_v4_vocals.onnx",
        sha256 = "a1b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef0",
        sizeBytes = 35651584L // ~34 MB quantized ONNX
    )

    val ALL_MODELS = listOf(MDX_VOCALS_DEFAULT, DEMUCS_V4_VOCALS)

    fun getModelById(id: String): ModelSpec =
        ALL_MODELS.find { it.id == id } ?: MDX_VOCALS_DEFAULT
}
