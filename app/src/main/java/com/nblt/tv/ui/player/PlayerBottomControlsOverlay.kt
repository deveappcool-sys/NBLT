package com.nblt.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nblt.tv.theme.NbltPrimary
import com.nblt.tv.theme.TvColors
import kotlin.math.roundToInt

@Composable
fun PlayerBottomControlsOverlay(
    visible: Boolean,
    positionMs: Long,
    durationMs: Long,
    isSeekPreviewMode: Boolean,
    modifier: Modifier = Modifier
) {
    if (!visible) {
        return
    }

    val safeDurationMs = durationMs.coerceAtLeast(0L)
    val displayPositionMs = positionMs.coerceAtLeast(0L)
    val progressFraction = if (safeDurationMs > 0L) {
        (displayPositionMs.toFloat() / safeDurationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0xF2000000))
                )
            )
            .padding(start = 56.dp, end = 56.dp, top = 64.dp, bottom = 42.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatBottomControlTime(displayPositionMs),
                color = TvColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(72.dp)
            )

            PlayerSeekProgressBar(
                progressFraction = progressFraction,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
            )

            Text(
                text = formatBottomControlTime(safeDurationMs),
                color = TvColors.TextSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(72.dp)
            )
        }

    }
}

@Composable
private fun PlayerSeekProgressBar(
    progressFraction: Float,
    modifier: Modifier = Modifier
) {
    val playedColor = NbltPrimary
    val unplayedColor = Color(0xFF4A5058)
    val thumbColor = Color.White
    val trackHeight = 8.dp
    val thumbSize = 14.dp

    BoxWithConstraints(
        modifier = modifier.height(thumbSize),
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val trackWidthPx = with(density) { maxWidth.toPx() }
        val thumbSizePx = with(density) { thumbSize.toPx() }
        val thumbRadiusPx = thumbSizePx / 2f
        val thumbOffsetPx = if (trackWidthPx > 0f) {
            (trackWidthPx * progressFraction - thumbRadiusPx).coerceIn(0f, trackWidthPx - thumbSizePx)
        } else {
            0f
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(unplayedColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(999.dp))
                    .background(playedColor)
            )
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffsetPx.roundToInt(), 0) }
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}

internal fun formatBottomControlTime(ms: Long): String {
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
