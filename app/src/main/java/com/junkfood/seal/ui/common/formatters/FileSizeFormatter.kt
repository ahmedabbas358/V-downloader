package com.junkfood.seal.ui.common.formatters

import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow

object FileSizeFormatter {
    private val units = arrayOf("B", "KB", "MB", "GB", "TB")
    private val df = DecimalFormat("#,##0.##")

    /**
     * Formats a byte count into a readable string.
     * Uses LTR override (\u202D) to ensure safe display in RTL contexts.
     */
    fun format(sizeBytes: Long): String {
        if (sizeBytes <= 0) return "\u202D0 B\u202C"
        
        val digitGroups = (log10(sizeBytes.toDouble()) / log10(1024.0)).toInt()
        val formattedSize = df.format(sizeBytes / 1024.0.pow(digitGroups.toDouble()))
        val unit = units.getOrElse(digitGroups) { "B" }
        
        return "\u202D$formattedSize $unit\u202C"
    }
}
