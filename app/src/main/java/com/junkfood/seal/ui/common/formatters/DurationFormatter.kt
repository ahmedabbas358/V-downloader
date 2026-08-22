package com.junkfood.seal.ui.common.formatters

object DurationFormatter {

    /**
     * Formats a duration in milliseconds into a readable string (HH:mm:ss or mm:ss).
     * Uses LTR override (\u202D) to ensure safe display in RTL contexts.
     */
    fun format(durationMs: Long?): String {
        if (durationMs == null || durationMs <= 0) return ""

        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        val formattedDuration = if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
        
        return "\u202D$formattedDuration\u202C"
    }
}
