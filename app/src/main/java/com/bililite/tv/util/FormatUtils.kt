package com.bililite.tv.util

import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FormatUtils {
    private val accentColors = listOf(
        Color(0xFF00A1D6),
        Color(0xFFE86A92),
        Color(0xFF8BC34A),
        Color(0xFFFFB300),
        Color(0xFF7E57C2),
        Color(0xFF26A69A),
        Color(0xFFFF7043),
        Color(0xFF5C6BC0)
    )

    fun formatPlayCount(count: Long): String {
        return when {
            count >= 100_000_000 -> String.format("%.1f\u4ebf", count / 100_000_000.0)
            count >= 10_000 -> String.format("%.1f\u4e07", count / 10_000.0)
            count > 0 -> count.toString()
            else -> "--"
        }
    }

    fun formatDuration(seconds: Long): String {
        val safeSeconds = seconds.coerceAtLeast(0)
        val hours = safeSeconds / 3600
        val minutes = (safeSeconds % 3600) / 60
        val remainSeconds = safeSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, remainSeconds)
        } else {
            "%02d:%02d".format(minutes, remainSeconds)
        }
    }

    fun normalizeImageUrl(url: String): String {
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://") -> url.replaceFirst("http://", "https://")
            else -> url
        }
    }

    fun accentFor(index: Int): Color {
        return accentColors[index % accentColors.size]
    }

    fun formatHistoryTime(timestampSeconds: Long): String {
        if (timestampSeconds <= 0L) {
            return ""
        }
        val formatter = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        return formatter.format(Date(timestampSeconds * 1000))
    }

    fun formatProgress(progressSeconds: Long): String {
        return if (progressSeconds > 0L) {
            "\u5df2\u770b ${formatDuration(progressSeconds)}"
        } else {
            ""
        }
    }

    fun formatProgressWithDuration(
        progressSeconds: Long,
        durationSeconds: Long
    ): String {
        if (progressSeconds <= 0L) {
            return ""
        }
        return if (durationSeconds > 0L) {
            "\u5df2\u770b ${formatDuration(progressSeconds)} / ${formatDuration(durationSeconds)}"
        } else {
            "\u5df2\u770b ${formatDuration(progressSeconds)}"
        }
    }
}
