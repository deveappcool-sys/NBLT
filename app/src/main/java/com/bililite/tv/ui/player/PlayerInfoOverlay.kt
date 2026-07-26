package com.bililite.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bililite.tv.theme.TvColors

@Composable
fun PlayerInfoOverlay(
    title: String,
    ownerName: String,
    partTitle: String?,
    qualityText: String?,
    playbackSpeed: Float,
    danmakuEnabled: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xE6121414), RoundedCornerShape(16.dp))
            .border(1.dp, TvColors.CardBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = buildString {
                if (ownerName.isNotBlank()) append("UP: ").append(ownerName).append("    ")
                if (!partTitle.isNullOrBlank()) append(partTitle).append("    ")
                append("\u6e05\u6670\u5ea6: ").append(qualityText.orEmpty().ifBlank { "-" }).append("    ")
                append("\u500d\u901f: ").append(formatSpeed(playbackSpeed)).append("    ")
                append("\u5f39\u5e55: ").append(if (danmakuEnabled) "\u5f00" else "\u5173")
            },
            color = TvColors.TextSecondary,
            fontSize = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = "${formatPlayerTimeForOverlay(currentPositionMs)} / ${formatPlayerTimeForOverlay(durationMs)}",
            color = TvColors.FocusAccent,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

private fun formatPlayerTimeForOverlay(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
