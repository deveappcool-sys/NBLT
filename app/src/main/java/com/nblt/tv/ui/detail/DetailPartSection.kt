package com.nblt.tv.ui.detail

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nblt.tv.model.VideoItem
import com.nblt.tv.model.VideoPage
import com.nblt.tv.theme.NbltPrimary
import com.nblt.tv.theme.TvColors
import com.nblt.tv.theme.TvDimensions
import com.nblt.tv.ui.components.CoverPlaceholder

@Composable
internal fun DetailPartSection(
    video: VideoItem,
    pages: List<VideoPage>,
    selectedCid: Long,
    onPageSelect: (VideoPage) -> Unit,
    layoutSpec: DetailLayoutSpec,
    modifier: Modifier = Modifier,
    title: String = "\u5206 P / \u5408\u96c6"
) {
    val compact = layoutSpec.isCompactHeight
    val cardWidth = if (compact) 148.dp else 176.dp
    val titleFontSize = if (compact) 16.sp else 18.sp

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = titleFontSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = if (compact) 6.dp else 8.dp)
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp),
            contentPadding = PaddingValues(start = 4.dp, end = 12.dp, bottom = 4.dp)
        ) {
            itemsIndexed(pages, key = { _, page -> page.cid }) { index, page ->
                DetailPartCard(
                    video = video,
                    page = page,
                    index = index,
                    selected = page.cid == selectedCid,
                    cardWidth = cardWidth,
                    compact = compact,
                    onClick = { onPageSelect(page) }
                )
            }
        }
    }
}

@Composable
private fun DetailPartCard(
    video: VideoItem,
    page: VideoPage,
    index: Int,
    selected: Boolean,
    cardWidth: androidx.compose.ui.unit.Dp,
    compact: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.03f else 1f,
        animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing),
        label = "partCardScale"
    )
    val shape = RoundedCornerShape(8.dp)

    val coverVideo = remember(video, page) {
        video.copy(
            coverUrl = page.coverUrl.ifBlank { video.coverUrl },
            duration = page.duration.toLong().coerceAtLeast(0L)
        )
    }
    val hasCover = coverVideo.coverUrl.isNotBlank()
    val displayTitle = if (page.isCollectionEpisode) {
        page.part.ifBlank { "第${index + 1}集" }
    } else {
        "P${page.page}  ${page.part}"
    }

    Column(
        modifier = Modifier
            .width(cardWidth)
            .fillMaxHeight()
            .scale(scale)
            .clip(shape)
            .border(
                width = when {
                    focused -> TvDimensions.focusBorderWidth
                    selected -> 2.dp
                    else -> 1.dp
                },
                color = when {
                    focused -> Color.White
                    selected -> NbltPrimary
                    else -> TvColors.CardBorder
                },
                shape = shape
            )
            .background(
                color = when {
                    selected -> Color(0x332FB8C5)
                    focused -> Color(0xFF27323A)
                    else -> Color(0xFF151A22)
                },
                shape = shape
            )
            .onFocusChanged {
                if (focused != it.isFocused) {
                    focused = it.isFocused
                }
                if (it.isFocused) {
                    DetailFocusLog.focusedPartItem(index)
                }
            }
            .focusable()
            .clickable(onClick = onClick)
            .padding(if (compact) 6.dp else 8.dp)
    ) {
        if (hasCover) {
            CoverPlaceholder(
                video = coverVideo,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .aspectRatio(16f / 9f, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(6.dp)),
                showDuration = true,
                showStatsOverlay = false,
                badgeFontSize = if (compact) 9.sp else 10.sp
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .aspectRatio(16f / 9f, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF27323A)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (page.isCollectionEpisode) {
                        "${index + 1}"
                    } else {
                        "P${page.page}"
                    },
                    color = Color.White,
                    fontSize = if (compact) 18.sp else 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            text = displayTitle,
            color = if (selected || focused) Color.White else Color(0xFFD6DAE1),
            fontSize = if (compact) 12.sp else 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = if (compact) 15.sp else 17.sp,
            modifier = Modifier.padding(top = if (compact) 4.dp else 6.dp)
        )
        if (page.duration > 0 && !compact) {
            Text(
                text = coverVideo.durationText,
                color = TvColors.TextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
