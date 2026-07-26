package com.nblt.tv.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nblt.tv.model.VideoItem
import com.nblt.tv.theme.TvColors
import com.nblt.tv.ui.components.CoverPlaceholder

private val CoverShape = RoundedCornerShape(18.dp)

@Composable
internal fun DetailCoverPreview(
    video: VideoItem,
    modifier: Modifier = Modifier,
    maxCoverWidth: Dp = 400.dp,
    maxCoverHeight: Dp = Dp.Unspecified
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopEnd
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxCoverWidth)
                .then(
                    if (maxCoverHeight != Dp.Unspecified) {
                        Modifier.heightIn(max = maxCoverHeight)
                    } else {
                        Modifier
                    }
                )
                .fillMaxWidth()
                .aspectRatio(16f / 9f, matchHeightConstraintsFirst = maxCoverHeight != Dp.Unspecified)
                .shadow(
                    elevation = 18.dp,
                    shape = CoverShape,
                    ambientColor = Color(0x66000000),
                    spotColor = Color(0x99000000),
                    clip = false
                )
                .clip(CoverShape)
                .border(1.dp, Color(0x55FFFFFF), CoverShape)
        ) {
            CoverPlaceholder(
                video = video,
                modifier = Modifier.fillMaxSize(),
                showDuration = true,
                showStatsOverlay = false
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .shadow(10.dp, CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(Color(0xA6141820))
                    .border(1.dp, Color(0x66FFFFFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "▶",
                    color = TvColors.TextPrimary,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}
