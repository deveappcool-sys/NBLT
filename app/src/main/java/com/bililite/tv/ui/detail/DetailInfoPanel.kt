package com.bililite.tv.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bililite.tv.model.VideoItem
import com.bililite.tv.model.VideoPage
import com.bililite.tv.theme.TvColors
import com.bililite.tv.ui.state.UiState

private val DetailTitleStyle = TextStyle(
    lineBreak = LineBreak.Simple,
    hyphens = Hyphens.None
)

@Composable
internal fun DetailInfoPanel(
    video: VideoItem,
    pagesLoadState: UiState<Unit>?,
    currentPage: VideoPage?,
    onUpClick: ((Long, String) -> Unit)?,
    layoutSpec: DetailLayoutSpec,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (layoutSpec.isCompactHeight) 6.dp else 8.dp)
    ) {
        Text(
            text = video.title,
            color = TvColors.TextPrimary,
            fontSize = layoutSpec.titleFontSizeSp.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = layoutSpec.titleLineHeightSp.sp,
            maxLines = layoutSpec.titleMaxLines,
            overflow = TextOverflow.Ellipsis,
            style = DetailTitleStyle,
            modifier = Modifier.fillMaxWidth(0.95f)
        )

        if (onUpClick != null && video.ownerName.isNotBlank()) {
            DetailUpOwnerRow(
                ownerName = video.ownerName,
                avatarUrl = video.ownerFaceUrl,
                onClick = { onUpClick(video.ownerMid, video.ownerName) },
                compact = layoutSpec.isCompactHeight
            )
        } else if (video.ownerName.isNotBlank()) {
            Text(
                text = video.ownerName,
                color = TvColors.TextPrimary,
                fontSize = if (layoutSpec.isCompactHeight) 16.sp else 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        DetailStatsRow(video = video, currentPage = currentPage)

        when (pagesLoadState) {
            UiState.Loading -> {
                Text(
                    text = "\u6b63\u5728\u52a0\u8f7d\u5206 P \u4fe1\u606f...",
                    color = Color(0xFFB8BDC7),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            is UiState.Error -> {
                Text(
                    text = "\u5206 P \u4fe1\u606f\u52a0\u8f7d\u5931\u8d25",
                    color = Color(0xFFFFB4AB),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            else -> Unit
        }

        if (video.description.isNotBlank() && layoutSpec.descMaxLines > 0) {
            Text(
                text = video.description,
                color = TvColors.TextSecondary,
                fontSize = layoutSpec.descFontSizeSp.sp,
                lineHeight = (layoutSpec.descFontSizeSp + 6).sp,
                maxLines = layoutSpec.descMaxLines,
                overflow = TextOverflow.Ellipsis,
                style = DetailTitleStyle,
                modifier = Modifier.fillMaxWidth(0.95f)
            )
        }
    }
}
