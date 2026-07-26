package com.bililite.tv.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bililite.tv.model.VideoItem
import com.bililite.tv.theme.TvColors
import com.bililite.tv.ui.components.CoverPlaceholder
import com.bililite.tv.util.BilibiliImageUrl

private val RelatedTitleBottomSpacing = 12.dp

@Composable
internal fun DetailRelatedSection(
    videos: List<VideoItem>,
    onVideoClick: (VideoItem) -> Unit,
    layoutSpec: DetailLayoutSpec,
    modifier: Modifier = Modifier,
    title: String = "相关推荐",
    showTopSpacing: Boolean = false
) {
    if (videos.isEmpty()) return

    val compact = layoutSpec.isCompactHeight
    val cardWidth = if (compact) 220.dp else 252.dp
    val coverHeight = cardWidth * 9f / 16f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(top = if (showTopSpacing) 8.dp else 0.dp)
    ) {
        Text(
            text = title,
            color = TvColors.TextPrimary,
            fontSize = if (compact) 20.sp else 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = RelatedTitleBottomSpacing)
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 20.dp),
            contentPadding = PaddingValues(
                start = 2.dp,
                end = 18.dp,
                top = 4.dp,
                bottom = 8.dp
            )
        ) {
            itemsIndexed(videos, key = { _, item -> item.bvid.ifBlank { "aid:${item.aid}" } }) { index, item ->
                DetailRelatedVideoCard(
                    item = item,
                    index = index,
                    cardWidth = cardWidth,
                    coverHeight = coverHeight,
                    onVideoClick = onVideoClick
                )
            }
        }
    }
}

@Composable
private fun DetailRelatedVideoCard(
    item: VideoItem,
    index: Int,
    cardWidth: androidx.compose.ui.unit.Dp,
    coverHeight: androidx.compose.ui.unit.Dp,
    onVideoClick: (VideoItem) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(14.dp)
    val coverShape = RoundedCornerShape(13.dp)

    Column(
        modifier = Modifier
            .width(cardWidth)
            .shadow(
                elevation = if (focused) 12.dp else 0.dp,
                shape = cardShape,
                ambientColor = Color(0x554EC0E4),
                spotColor = Color(0x444EC0E4),
                clip = false
            )
            .clip(cardShape)
            .background(if (focused) Color(0xB3121721) else Color.Transparent, cardShape)
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) TvColors.FocusRing else Color.Transparent,
                shape = cardShape
            )
            .onPreviewKeyEvent { event ->
                index == 0 &&
                    event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionLeft
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) DetailFocusLog.focusedRelatedItem(index)
            }
            .focusable()
            .clickable { onVideoClick(item) }
            .padding(4.dp)
    ) {
        CoverPlaceholder(
            video = item,
            showDuration = true,
            showStatsOverlay = true,
            badgeFontSize = 11.sp,
            modifier = Modifier
                .fillMaxWidth()
                .height(coverHeight)
                .clip(coverShape)
        )

        Text(
            text = item.title.ifBlank { "无标题" },
            color = TvColors.TextPrimary,
            fontSize = 15.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .padding(start = 4.dp, end = 4.dp, top = 8.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .padding(horizontal = 4.dp, vertical = 3.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF5A6674), Color(0xFF242D37))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (item.ownerFaceUrl.isNotBlank()) {
                    AsyncImage(
                        model = BilibiliImageUrl.avatar(item.ownerFaceUrl, size = 48),
                        contentDescription = item.ownerName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = item.ownerName.firstOrNull()?.toString().orEmpty(),
                        color = TvColors.TextPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = item.ownerName.ifBlank { "未知UP主" },
                color = TvColors.TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 7.dp)
            )
        }
    }
}
