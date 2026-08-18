package com.junkfood.seal.util

import com.junkfood.seal.download.engine.subtitle.conversion.SubtitleConverter
import java.io.File

/**
 * SubtitleUtil provides timing shifting and format conversion utilities,
 * delegating to [SubtitleConverter].
 */
object SubtitleUtil {

    /**
     * Shifts subtitle timestamps in an SRT or VTT file by a given offset in milliseconds.
     */
    suspend fun shiftSubtitleTiming(
        file: File,
        offsetMillis: Long,
        outputFile: File = file
    ): Result<File> {
        return SubtitleConverter.shiftSubtitleTiming(file, offsetMillis, outputFile)
    }

    /**
     * Converts an SRT file content to WebVTT format.
     */
    suspend fun convertSrtToVtt(srtFile: File, vttFile: File): Result<File> {
        return SubtitleConverter.convert(
            sourceFile = srtFile,
            targetFormat = com.junkfood.seal.download.engine.subtitle.model.SubtitleOutputFormat.VTT,
            outputFile = vttFile
        )
    }

    /**
     * Converts a WebVTT file content to SRT format.
     */
    suspend fun convertVttToSrt(vttFile: File, srtFile: File): Result<File> {
        return SubtitleConverter.convert(
            sourceFile = vttFile,
            targetFormat = com.junkfood.seal.download.engine.subtitle.model.SubtitleOutputFormat.SRT,
            outputFile = srtFile
        )
    }
}
