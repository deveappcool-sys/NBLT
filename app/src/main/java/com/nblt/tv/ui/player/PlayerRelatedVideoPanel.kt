package com.nblt.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nblt.tv.model.VideoItem
import com.nblt.tv.theme.NbltPrimary
import com.nblt.tv.theme.TvColors
import com.nblt.tv.ui.components.CompactVideoCard
import coil.compose.AsyncImage
import com.nblt.tv.util.BilibiliImageUrl

@Composable
fun PlayerRelatedVideoPanel(
    title: String,
    owner: VideoItem,
    videos: List<VideoItem>,
    loading: Boolean,
    loadingMore: Boolean,
    loadMoreError: String?,
    playbackStatusMessage: String?,
    ownerFocused: Boolean,
    focusedVideoIndex: Int,
    modifier: Modifier = Modifier
) {
    val videoListState = rememberLazyListState()
    LaunchedEffect(focusedVideoIndex, ownerFocused, videos.size) {
        if (ownerFocused || videos.isEmpty()) return@LaunchedEffect
        val targetIndex = focusedVideoIndex.coerceIn(0, videos.lastIndex)
        val visibleItems = videoListState.layoutInfo.visibleItemsInfo
        val targetVisible = visibleItems.any { it.index == targetIndex }
        if (!targetVisible) {
            videoListState.animateScrollToItem(targetIndex)
        } else {
            val target = visibleItems.first { it.index == targetIndex }
            val viewportStart = videoListState.layoutInfo.viewportStartOffset
            val viewportEnd = videoListState.layoutInfo.viewportEndOffset
            if (target.offset < viewportStart) {
                videoListState.animateScrollToItem(targetIndex)
            } else if (target.offset + target.size > viewportEnd) {
                videoListState.animateScrollToItem(targetIndex, -24)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(350.dp)
            .background(Color(0xF2121414))
            .padding(start = 48.dp, end = 48.dp, top = 16.dp, bottom = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.weight(1f))
            if (playbackStatusMessage != null) {
                Text(
                    text = playbackStatusMessage,
                    color = if (playbackStatusMessage.startsWith("播放失败")) {
                        Color(0xFFFFC857)
                    } else {
                        TvColors.TextSecondary
                    },
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }
        }

        if (owner.ownerMid > 0L) {
            PlayerOwnerSpaceCard(
                owner = owner,
                focused = ownerFocused,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "正在加载该 UP 主的其他视频…",
                    color = TvColors.TextSecondary,
                    fontSize = 18.sp
                )
            }
        } else if (videos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "\u6682\u65e0\u76f8\u5173\u89c6\u9891",
                    color = Color(0xFFB8BDC7),
                    fontSize = 18.sp
                )
            }
        } else {
            LazyRow(
                state = videoListState,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
            ) {
                itemsIndexed(videos, key = { _, item -> item.bvid.ifBlank { "aid:${item.aid}" } }) { index, item ->
                    Box(
                        modifier = Modifier
                            .width(210.dp)
                            .scale(if (index == focusedVideoIndex && !ownerFocused) 1.025f else 1f)
                            .border(
                                width = if (index == focusedVideoIndex && !ownerFocused) 3.dp else 0.dp,
                                color = if (index == focusedVideoIndex && !ownerFocused) TvColors.FocusAccent else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(3.dp)
                    ) {
                        CompactVideoCard(
                            video = item,
                            onClick = {},
                            sublineText = item.ownerName.takeIf { it.isNotBlank() }
                        )
                    }
                }
                if (loadingMore) {
                    item(key = "up-videos-loading-more") {
                        Box(
                            modifier = Modifier
                                .width(160.dp)
                                .height(150.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("正在加载更多…", color = TvColors.TextSecondary, fontSize = 15.sp)
                        }
                    }
                }
                if (loadMoreError != null) {
                    item(key = "up-videos-load-more-error") {
                        Box(
                            modifier = Modifier
                                .width(220.dp)
                                .height(150.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = loadMoreError,
                                color = Color(0xFFFFC857),
                                fontSize = 14.sp,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerOwnerSpaceCard(
    owner: VideoItem,
    focused: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .background(if (focused) TvColors.FocusAccent else TvColors.SurfaceElevated)
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) TvColors.FocusAccent else TvColors.CardBorder,
                shape = shape
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = BilibiliImageUrl.avatar(owner.ownerFaceUrl, size = 64),
            contentDescription = owner.ownerName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(TvColors.Surface)
        )
        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(
                text = owner.ownerName.ifBlank { "UP 主" },
                color = if (focused) Color(0xFF161817) else TvColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "进入 UP 主主页空间",
                color = if (focused) Color(0xFF3B3D3C) else TvColors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "查看空间  ›",
            color = if (focused) Color(0xFF161817) else TvColors.TextSecondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
