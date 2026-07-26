package com.nblt.tv.ui.upspace

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nblt.tv.model.UpProfile
import com.nblt.tv.model.UpVideoItem
import com.nblt.tv.theme.TvColors
import com.nblt.tv.theme.TvDimensions
import com.nblt.tv.ui.components.CoverCornerBadge
import com.nblt.tv.ui.components.TvBackground
import com.nblt.tv.ui.components.TvEmptyContent
import com.nblt.tv.ui.components.TvErrorContent
import com.nblt.tv.ui.components.TvLoadingContent
import com.nblt.tv.ui.components.rememberVideoGridColumnCount
import com.nblt.tv.ui.components.rememberDeterministicGridVerticalFocusHandler
import com.nblt.tv.ui.components.tvVideoCardShell
import com.nblt.tv.ui.state.PagedUpVideoList
import com.nblt.tv.ui.state.UiState
import com.nblt.tv.util.FormatUtils
import com.nblt.tv.util.BilibiliImageUrl
import com.nblt.tv.util.UpSpaceDebugLog
import kotlinx.coroutines.delay

@Composable
fun UpSpaceScreen(
    profileState: UiState<UpProfile>?,
    videosState: UiState<PagedUpVideoList>?,
    fallbackName: String,
    onRetryProfile: () -> Unit,
    onRetryVideos: () -> Unit,
    onLoadMore: () -> Unit,
    onVideoClick: (UpVideoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var sort by rememberSaveable { mutableStateOf(UpVideoSort.Latest) }
    LaunchedEffect(videosState) {
        when (val state = videosState) {
            null, UiState.Loading -> UpSpaceDebugLog.logUiStateTarget(
                target = "Loading",
                detail = "UpSpaceScreen render"
            )
            is UiState.Error -> UpSpaceDebugLog.logUiStateTarget(
                target = "Error",
                detail = "UpSpaceScreen message=${state.message}"
            )
            is UiState.Success -> UpSpaceDebugLog.logUiStateTarget(
                target = if (state.data.videos.isEmpty()) "Empty" else "Success",
                detail = "UpSpaceScreen count=${state.data.videos.size}, page=${state.data.page}, " +
                    "hasMore=${state.data.hasMore}, isLoadingMore=${state.data.isLoadingMore}, " +
                    "loadMoreError=${state.data.loadMoreError}"
            )
        }
    }

    TvBackground(
        modifier = modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
        UpProfileHeader(
            profileState = profileState,
            fallbackName = fallbackName,
            onRetry = onRetryProfile
        )

        UpVideoSortBar(
            selected = sort,
            onSelect = { sort = it },
            modifier = Modifier.padding(bottom = 10.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (val state = videosState ?: UiState.Loading) {
                UiState.Loading -> TvLoadingContent(message = "\u6b63\u5728\u52a0\u8f7d\u6295\u7a3f\u89c6\u9891...")
                is UiState.Error -> TvErrorContent(
                    title = "\u6295\u7a3f\u89c6\u9891\u52a0\u8f7d\u5931\u8d25",
                    message = state.message,
                    onRetry = onRetryVideos
                )
                is UiState.Success -> {
                    if (state.data.videos.isEmpty()) {
                        TvEmptyContent(message = "\u6682\u65e0\u6295\u7a3f\u89c6\u9891")
                    } else {
                        UpVideoGrid(
                            videos = remember(state.data.videos, sort) {
                                when (sort) {
                                    UpVideoSort.Latest -> state.data.videos.sortedByDescending { it.pubdate }
                                    UpVideoSort.MostPlayed -> state.data.videos.sortedByDescending { it.playCount }
                                }
                            },
                            hasMore = state.data.hasMore,
                            isLoadingMore = state.data.isLoadingMore,
                            loadMoreError = state.data.loadMoreError,
                            onLoadMore = onLoadMore,
                            onVideoClick = onVideoClick,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun UpProfileHeader(
    profileState: UiState<UpProfile>?,
    fallbackName: String,
    onRetry: () -> Unit
) {
    when (val state = profileState ?: UiState.Loading) {
        UiState.Loading -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2C313A))
                )
                Column(modifier = Modifier.padding(start = 14.dp)) {
                    Text(
                        text = fallbackName.ifBlank { "\u52a0\u8f7d\u4e2d..." },
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "\u6b63\u5728\u52a0\u8f7d\u8d44\u6599...",
                        color = Color(0xFFB8BDC7),
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        is UiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
            ) {
                TvErrorContent(
                    title = "\u7528\u6237\u8d44\u6599\u52a0\u8f7d\u5931\u8d25",
                    message = state.message,
                    onRetry = onRetry
                )
            }
        }
        is UiState.Success -> {
            val profile = state.data
            var signExpanded by rememberSaveable(profile.mid) { mutableStateOf(false) }
            var signFocused by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UpAvatar(
                    avatarUrl = profile.avatarUrl,
                    nickname = profile.nickname,
                    modifier = Modifier.size(56.dp)
                )
                Column(modifier = Modifier.padding(start = 14.dp)) {
                    Text(
                        text = profile.nickname,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val stats = buildList {
                        if (profile.followerCount > 0L) {
                            add("${FormatUtils.formatPlayCount(profile.followerCount)}\u7c89\u4e1d")
                        }
                        if (profile.videoCount > 0) {
                            add("${profile.videoCount}\u6295\u7a3f")
                        }
                    }.joinToString("  \u00b7  ")
                    if (stats.isNotBlank()) {
                        Text(
                            text = stats,
                            color = Color(0xFFB8BDC7),
                            fontSize = 15.sp,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                    if (profile.sign.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (signFocused) Color(0xFF2A323A) else Color.Transparent)
                                .border(
                                    if (signFocused) 1.dp else 0.dp,
                                    if (signFocused) TvColors.FocusAccent else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .onFocusChanged { signFocused = it.isFocused }
                                .focusable()
                                .clickable { signExpanded = !signExpanded }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (signExpanded) profile.sign else "${profile.sign}  ·  按确认展开",
                                color = Color(0xFFD6DAE1),
                                fontSize = 15.sp,
                                lineHeight = 18.sp,
                                maxLines = if (signExpanded) 4 else 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class UpVideoSort(val label: String) {
    Latest("最新发布"),
    MostPlayed("最多播放")
}

@Composable
private fun UpVideoSortBar(
    selected: UpVideoSort,
    onSelect: (UpVideoSort) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "投稿视频",
            color = TvColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 6.dp)
        )
        UpSortButton(
            sort = UpVideoSort.Latest,
            selected = selected == UpVideoSort.Latest,
            onClick = { onSelect(UpVideoSort.Latest) }
        )
        UpSortButton(
            sort = UpVideoSort.MostPlayed,
            selected = selected == UpVideoSort.MostPlayed,
            onClick = { onSelect(UpVideoSort.MostPlayed) }
        )
    }
}

@Composable
private fun UpSortButton(
    sort: UpVideoSort,
    selected: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = Modifier
            .width(112.dp)
            .height(38.dp)
            .clip(shape)
            .background(
                when {
                    focused -> TvColors.FocusAccent
                    selected -> Color(0xFF2A323A)
                    else -> Color(0xFF1B1F24)
                }
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else TvColors.CardBorder,
                shape = shape
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = sort.label,
            color = if (focused) Color(0xFF161817) else TvColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun UpAvatar(
    avatarUrl: String,
    nickname: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF00A1D6), Color(0xFF20242C))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (avatarUrl.isNotBlank()) {
            AsyncImage(
                model = BilibiliImageUrl.avatar(avatarUrl, size = 160),
                contentDescription = nickname,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = nickname.firstOrNull()?.toString().orEmpty(),
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun UpVideoGrid(
    videos: List<UpVideoItem>,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    loadMoreError: String?,
    onLoadMore: () -> Unit,
    onVideoClick: (UpVideoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val columnCount = rememberVideoGridColumnCount()
    val gridState = rememberLazyGridState()
    var restoredFocusIndex by rememberSaveable { mutableStateOf(0) }
    val focusRequesters = remember { mutableListOf<FocusRequester>() }
    while (focusRequesters.size < videos.size) {
        focusRequesters.add(FocusRequester())
    }
    while (focusRequesters.size > videos.size) {
        focusRequesters.removeAt(focusRequesters.lastIndex)
    }
    val verticalFocusHandler = rememberDeterministicGridVerticalFocusHandler(
        gridState = gridState,
        itemCount = videos.size,
        columns = columnCount,
        focusRequesterAt = { index -> focusRequesters.getOrNull(index) }
    )
    LaunchedEffect(videos.size) {
        if (videos.isEmpty()) return@LaunchedEffect
        delay(120)
        val targetIndex = restoredFocusIndex.coerceIn(0, videos.lastIndex)
        val requester = focusRequesters.getOrNull(targetIndex) ?: return@LaunchedEffect
        if (runCatching { requester.requestFocus() }.isFailure) {
            gridState.scrollToItem((targetIndex - columnCount).coerceAtLeast(0))
            runCatching { requester.requestFocus() }
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columnCount),
        state = gridState,
        modifier = modifier,
        contentPadding = PaddingValues(bottom = TvDimensions.gridBottomPadding),
        horizontalArrangement = Arrangement.spacedBy(TvDimensions.gridHorizontalSpacing),
        verticalArrangement = Arrangement.spacedBy(TvDimensions.gridVerticalSpacing)
    ) {
        itemsIndexed(videos, key = { _, video -> video.bvid.ifBlank { video.aid.toString() } }) { index, video ->
            UpVideoCard(
                video = video,
                onClick = { onVideoClick(video) },
                focusRequester = focusRequesters.getOrNull(index),
                onVerticalFocusMove = { rowDelta ->
                    val isBottomEdge = rowDelta > 0 && index + columnCount >= videos.size
                    if (isBottomEdge && loadMoreError != null) {
                        onLoadMore()
                        true
                    } else {
                        verticalFocusHandler(index, rowDelta)
                    }
                },
                onFocusChanged = { focused ->
                    if (focused) {
                        restoredFocusIndex = index
                    }
                }
            )
        }

        if (videos.isNotEmpty()) {
            item(key = "up-load-more-sentinel", span = { GridItemSpan(maxLineSpan) }) {
                if (hasMore && !isLoadingMore && loadMoreError == null) {
                    LaunchedEffect(videos.size, hasMore, isLoadingMore) {
                        UpSpaceDebugLog.logLoadMoreTrigger(
                            videosSize = videos.size,
                            hasMore = hasMore,
                            isLoadingMore = isLoadingMore,
                            loadMoreError = loadMoreError
                        )
                        onLoadMore()
                    }
                }
                Spacer(modifier = Modifier.height(1.dp))
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            UpLoadMoreFooter(
                hasMore = hasMore,
                isLoadingMore = isLoadingMore,
                error = loadMoreError,
                onRetry = onLoadMore
            )
        }
    }
}

@Composable
private fun UpVideoCard(
    video: UpVideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onVerticalFocusMove: ((Int) -> Boolean)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) TvDimensions.focusScale else 1f,
        animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing),
        label = "upVideoScale"
    )
    val context = LocalContext.current
    val coverModel = remember(video.coverUrl) {
        if (video.coverUrl.isBlank()) {
            null
        } else {
            ImageRequest.Builder(context)
                .data(BilibiliImageUrl.cover(video.coverUrl, width = 480, height = 270))
                .crossfade(false)
                .size(480, 270)
                .build()
        }
    }
    val subline = buildList {
        if (video.danmakuCountText.isNotBlank()) {
            add("${video.danmakuCountText}\u5f39\u5e55")
        }
        if (video.pubdateText.isNotBlank()) {
            add(video.pubdateText)
        }
    }.joinToString("  \u00b7  ")

    Column(
        modifier = Modifier
            .scale(scale)
            .tvVideoCardShell(focused)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .then(modifier)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        when (event.key) {
                            Key.DirectionUp -> onVerticalFocusMove?.invoke(-1) ?: false
                            Key.DirectionDown -> onVerticalFocusMove?.invoke(1) ?: false
                            else -> false
                        }
                    }
                }
                .onFocusChanged {
                    if (focused != it.isFocused) {
                        focused = it.isFocused
                    }
                    onFocusChanged?.invoke(it.isFocused)
                }
                .focusable()
                .clickable(onClick = onClick)
                .background(
                    Brush.linearGradient(listOf(video.accent, Color(0xFF20242C)))
                )
        ) {
            if (coverModel != null) {
                AsyncImage(
                    model = coverModel,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (video.playCount > 0L) {
                CoverCornerBadge(
                    text = "${video.playCountText}\u64ad\u653e",
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                )
            }
            CoverCornerBadge(
                text = video.durationText,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(TvDimensions.compactInfoAreaHeight)
                .padding(
                    start = 6.dp,
                    end = 6.dp,
                    top = TvDimensions.compactInfoPaddingTop,
                    bottom = TvDimensions.compactInfoPaddingBottom
                )
        ) {
            Text(
                text = video.title,
                color = TvColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TvDimensions.compactTitleBlockHeight)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TvDimensions.compactSublineHeight),
                contentAlignment = Alignment.CenterStart
            ) {
                if (subline.isNotBlank()) {
                    Text(
                        text = subline,
                        color = TvColors.TextMuted,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun UpLoadMoreFooter(
    hasMore: Boolean,
    isLoadingMore: Boolean,
    error: String?,
    onRetry: (() -> Unit)?
) {
    val text = when {
        isLoadingMore -> "\u6b63\u5728\u52a0\u8f7d\u66f4\u591a..."
        error != null -> "\u52a0\u8f7d\u5931\u8d25\uff0c\u6309 OK \u91cd\u8bd5"
        !hasMore -> "\u6ca1\u6709\u66f4\u591a\u4e86"
        else -> ""
    }
    if (text.isBlank()) {
        return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 10.dp)
            .background(Color(0xFF1B1E24), RoundedCornerShape(8.dp))
            .clickable(enabled = error != null && onRetry != null) { onRetry?.invoke() }
            .padding(vertical = 18.dp)
    ) {
        Text(
            text = text,
            color = if (error == null) Color(0xFFB8BDC7) else Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 18.dp)
        )
    }
}
