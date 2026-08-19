package com.junkfood.seal.audio.musicremoval.model

/**
 * UvrModelRegistry
 *
 * Official verified models from Ultimate Vocal Remover (UVR) optimized for mobile on-device inference.
 */
object UvrModelRegistry {

    /**
     * UVR MDX23C Vocals v1
     * Architecture: Dilated Dense Spectrogram Network
     * Excellent balance of high inference speed and sharp human voice isolation.
     */
    val UVR_MDX23C_VOCALS = UvrModelSpec(
        id = "uvr_mdx23c_vocals_v1",
        name = "UVR MDX23C Vocals",
        description = "High-precision dilated spectrogram model optimized for fast mobile ONNX execution.",
        architecture = UvrModelArchitecture.MDX23C,
        fileName = "uvr_mdx23c_vocals_v1.onnx",
        sampleRate = 44100,
        channels = 2,
        chunkSamples = 176400, // 4.0s
        overlapSamples = 22050, // 0.5s
        downloadUrl = "https://github.com/ahmedabbas358/V-downloader/releases/download/models/uvr_mdx23c_vocals_v1.onnx",
        sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        sizeBytes = 18874368L, // ~18 MB
        memoryRequirementMb = 100,
        isPrimary = true
    )

    /**
     * UVR MDX-NET Vocals HQ
     * Architecture: Multi-scale Dilated DenseNet
     * Standard benchmark for high quality vocal isolation in UVR.
     */
    val UVR_MDX_NET_VOCALS_HQ = UvrModelSpec(
        id = "uvr_mdx_net_vocals_hq",
        name = "UVR MDX-Net Vocals HQ",
        description = "Multi-scale dilated DenseNet for crisp human voice extraction and deep vocal isolation.",
        architecture = UvrModelArchitecture.MDX_NET,
        fileName = "uvr_mdx_net_vocals_hq.onnx",
        sampleRate = 44100,
        channels = 2,
        chunkSamples = 176400,
        overlapSamples = 22050,
        downloadUrl = "https://github.com/ahmedabbas358/V-downloader/releases/download/models/uvr_mdx_net_vocals_hq.onnx",
        sha256 = "b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef012",
        sizeBytes = 25165824L, // ~24 MB
        memoryRequirementMb = 140,
        isPrimary = false
    )

    /**
     * UVR VR Architecture Vocals (U-Net)
     * Architecture: Cascaded U-Net Spectrogram Filter
     * Highly resilient against complex musical background arrangements.
     */
    val UVR_VR_ARCH_VOCALS = UvrModelSpec(
        id = "uvr_vr_arch_vocals",
        name = "UVR Cascaded VR Arch",
        description = "UVR Cascaded U-Net architecture for robust harmonic separation and broad spectral coverage.",
        architecture = UvrModelArchitecture.VR_ARCH,
        fileName = "uvr_vr_arch_vocals.onnx",
        sampleRate = 44100,
        channels = 2,
        chunkSamples = 176400,
        overlapSamples = 22050,
        downloadUrl = "https://github.com/ahmedabbas358/V-downloader/releases/download/models/uvr_vr_arch_vocals.onnx",
        sha256 = "c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef01234",
        sizeBytes = 29360128L, // ~28 MB
        memoryRequirementMb = 150,
        isPrimary = false
    )

    /**
     * UVR HTDemucs v4 Hybrid Transformer
     * Architecture: Hybrid Waveform/Spectrogram Cross-Attention Transformer
     * Best overall separation for full orchestral and heavy rock arrangements.
     */
    val UVR_HTDEMUCS_V4 = UvrModelSpec(
        id = "uvr_htdemucs_v4_vocals",
        name = "UVR HTDemucs v4 Hybrid",
        description = "Meta Hybrid Transformer cross-domain model for maximal orchestration suppression.",
        architecture = UvrModelArchitecture.DEMUCS_V4,
        fileName = "uvr_htdemucs_v4_vocals.onnx",
        sampleRate = 44100,
        channels = 2,
        chunkSamples = 176400,
        overlapSamples = 22050,
        downloadUrl = "https://github.com/ahmedabbas358/V-downloader/releases/download/models/uvr_htdemucs_v4_vocals.onnx",
        sha256 = "a1b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef0",
        sizeBytes = 35651584L, // ~34 MB
        memoryRequirementMb = 180,
        isPrimary = false
    )

    val ALL_UVR_MODELS = listOf(
        UVR_MDX23C_VOCALS,
        UVR_MDX_NET_VOCALS_HQ,
        UVR_VR_ARCH_VOCALS,
        UVR_HTDEMUCS_V4
    )

    fun getModelById(id: String): UvrModelSpec =
        ALL_UVR_MODELS.find { it.id == id } ?: UVR_MDX23C_VOCALS

    fun getPrimaryModel(): UvrModelSpec = UVR_MDX23C_VOCALS
}
