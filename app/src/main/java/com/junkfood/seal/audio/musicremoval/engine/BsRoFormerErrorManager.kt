package com.junkfood.seal.audio.musicremoval.engine

/**
 * BsRoFormerErrorCode
 *
 * Categorized error codes for BS-RoFormer operations.
 */
enum class BsRoFormerErrorCode {
    INPUT_INVALID,
    DECODING_FAILED,
    UNSUPPORTED_FORMAT,
    MODEL_NOT_FOUND,
    MODEL_DOWNLOAD_FAILED,
    MODEL_CHECKSUM_FAILED,
    MODEL_LOAD_FAILED,
    GPU_OUT_OF_MEMORY,
    INFERENCE_FAILED,
    POSTPROCESS_FAILED,
    QUALITY_GATE_FAILED,
    OUTPUT_INVALID,
    OUTPUT_WRITE_FAILED
}

/**
 * BsRoFormerException
 *
 * Structured exception thrown by BS-RoFormer pipeline stages.
 */
class BsRoFormerException(
    val code: BsRoFormerErrorCode,
    override val message: String,
    override val cause: Throwable? = null
) : Exception("[$code] $message", cause)
