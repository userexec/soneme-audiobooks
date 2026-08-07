package com.userexec.soneme

object Formatters {
    fun compactDuration(ms: Long): String {
        if (ms <= 0) return ""
        val totalMinutes = ms / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}:${minutes.toString().padStart(2, '0')}" else "0:${minutes.toString().padStart(2, '0')}"
    }

    fun clock(ms: Long): String {
        val totalSeconds = ms.coerceAtLeast(0) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }

    fun words(ms: Long): String {
        val totalMinutes = ms.coerceAtLeast(0) / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    fun interval(ms: Long): String = when (ms) {
        10_000L -> "10s"
        60_000L -> "1m"
        600_000L -> "10m"
        3_600_000L -> "1h"
        else -> "${ms / 1000}s"
    }
}
