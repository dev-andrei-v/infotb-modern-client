package ro.infotb.modern.util

/**
 * Formats arrival time in seconds to a human-readable string.
 */
fun formatArrivalTime(seconds: Long): String {
    if (seconds < 60) return "<1 min"
    val minutes = seconds / 60
    if (minutes < 60) return "$minutes min"
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return "${hours}h ${remainingMinutes}m"
}

/**
 * Formats arrival time as a countdown badge string.
 */
fun formatArrivalBadge(seconds: Long): String {
    if (seconds < 60) return "<1 min"
    val minutes = seconds / 60
    if (minutes < 60) return "$minutes min"
    val hours = minutes / 60
    val mins = minutes % 60
    return if (mins > 0) "${hours}h $mins min" else "${hours}h"
}
