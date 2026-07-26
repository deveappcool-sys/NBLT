package com.nblt.tv.ui.my

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nblt.tv.model.UserInfo
import com.nblt.tv.model.VideoItem
import com.nblt.tv.theme.TvColors
import com.nblt.tv.theme.TvDimensions
import com.nblt.tv.ui.components.CompactVideoCard
import com.nblt.tv.ui.components.TvFocusButton
import com.nblt.tv.ui.components.TvLoadingContent
import com.nblt.tv.ui.components.rememberMyPreviewColumnCount
import com.nblt.tv.ui.state.HistoryUiState
import com.nblt.tv.ui.state.PagedVideoList
import com.nblt.tv.ui.state.UiState
import com.nblt.tv.util.BilibiliImageUrl

@Composable
fun MyScreen(
    currentUser: UserInfo?,
    historyState: HistoryUiState?,
    watchLaterState: UiState<PagedVideoList>?,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenWatchLater: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    accountActionFocusRequester: FocusRequester,
    leftFocusRequester: FocusRequester? = null,
    restoreFocusEntry: MyMenuItem? = null,
    onFocusRestored: () -> Unit = {},
    onLoginRequired: () -> Unit,
    onVideoClick: (VideoItem) -> Unit
) {
    val entries = buildMyMenuEntries(currentUser != null)
    val menuFocusRequesters = remember {
        MyMenuItem.entries.associateWith { FocusRequester() }
    }
    val enabledEntries = remember(currentUser) {
        entries.filter { !it.requiresLogin || currentUser != null }
    }
    val firstEnabledMenuRequester = enabledEntries.firstOrNull()?.let {
        menuFocusRequesters.getValue(it.item)
    }

    LaunchedEffect(restoreFocusEntry) {
        val entry = restoreFocusEntry ?: return@LaunchedEffect
        menuFocusRequesters[entry]?.requestFocus()
        onFocusRestored()
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight()
                .padding(end = 20.dp)
        ) {
            Text(
                text = "\u8d26\u53f7\u72b6\u6001",
                color = TvColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            if (currentUser == null) {
                Text(
                    text = "\u672a\u767b\u5f55",
                    color = TvColors.TextSecondary,
                    fontSize = 17.sp,
                    modifier = Modifier.padding(top = 10.dp, bottom = 18.dp)
                )
                TvFocusButton(
                    text = "\u767b\u5f55\u8d26\u53f7",
                    onClick = onLoginClick,
                    blockUp = true,
                    modifier = Modifier
                        .focusRequester(accountActionFocusRequester)
                        .focusProperties {
                            firstEnabledMenuRequester?.let { down = it }
                            leftFocusRequester?.let { left = it }
                        }
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 14.dp, bottom = 16.dp)
                ) {
                    AsyncImage(
                        model = BilibiliImageUrl.avatar(currentUser.avatarUrl, size = 96),
                        contentDescription = currentUser.nickname,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(TvColors.SurfaceElevated)
                    )
                    Column(modifier = Modifier.padding(start = 14.dp)) {
                        Text(
                            text = currentUser.nickname.ifBlank { "\u5df2\u767b\u5f55\u7528\u6237" },
                            color = TvColors.TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "UID ${currentUser.mid}",
                            color = TvColors.TextSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                TvFocusButton(
                    text = "\u9000\u51fa\u767b\u5f55",
                    onClick = onLogoutClick,
                    blockUp = true,
                    modifier = Modifier
                        .focusRequester(accountActionFocusRequester)
                        .focusProperties {
                            firstEnabledMenuRequester?.let { down = it }
                            leftFocusRequester?.let { left = it }
                        }
                )
            }

            Text(
                text = "\u529f\u80fd\u5165\u53e3",
                color = TvColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 22.dp, bottom = 10.dp)
            )

            entries.forEach { entry ->
                val enabledIndex = enabledEntries.indexOfFirst { it.item == entry.item }
                val upRequester = when {
                    enabledIndex < 0 -> null
                    enabledIndex == 0 -> accountActionFocusRequester
                    else -> menuFocusRequesters.getValue(enabledEntries[enabledIndex - 1].item)
                }
                val downRequester = enabledEntries.getOrNull(enabledIndex + 1)?.let {
                    menuFocusRequesters.getValue(it.item)
                }
                MyMenuRow(
                    entry = entry,
                    enabled = !entry.requiresLogin || currentUser != null,
                    focusRequester = menuFocusRequesters.getValue(entry.item),
                    upFocusRequester = upRequester,
                    downFocusRequester = downRequester,
                    leftFocusRequester = leftFocusRequester,
                    onClick = {
                        if (entry.requiresLogin && currentUser == null) {
                            onLoginRequired()
                        } else {
                            when (entry.item) {
                                MyMenuItem.Favorites -> onOpenFavorites()
                                MyMenuItem.WatchLater -> onOpenWatchLater()
                                MyMenuItem.History -> onOpenHistory()
                                MyMenuItem.Settings -> onOpenSettings()
                            }
                        }
                    }
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(TvDimensions.cardRadius))
                .background(TvColors.Surface)
                .padding(20.dp)
        ) {
            MyPreviewPanel(
                currentUser = currentUser,
                historyState = historyState,
                watchLaterState = watchLaterState,
                onVideoClick = onVideoClick
            )
        }
    }
}

@Composable
private fun MyPreviewPanel(
    currentUser: UserInfo?,
    historyState: HistoryUiState?,
    watchLaterState: UiState<PagedVideoList>?,
    onVideoClick: (VideoItem) -> Unit
) {
    when {
        currentUser == null -> {
            MyDualEmptyPreview()
        }
        isPreviewLoading(historyState, watchLaterState) -> {
            Text(
                text = "\u5185\u5bb9\u9884\u89c8",
                color = TvColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 14.dp)
            )
            TvLoadingContent(message = "\u6b63\u5728\u52a0\u8f7d\u9884\u89c8...")
        }
        else -> {
            val historyVideos = historyVideos(historyState).take(6)
            val watchLaterVideos = watchLaterVideos(watchLaterState).take(6)
            when {
                historyVideos.isNotEmpty() -> {
                    MyPreviewSection(
                        title = "\u6700\u8fd1\u89c2\u770b",
                        videos = historyVideos,
                        onVideoClick = onVideoClick
                    )
                }
                watchLaterVideos.isNotEmpty() -> {
                    MyPreviewSection(
                        title = "\u7a0d\u540e\u518d\u770b",
                        videos = watchLaterVideos,
                        onVideoClick = onVideoClick
                    )
                }
                else -> MyDualEmptyPreview()
            }
        }
    }
}

@Composable
private fun MyPreviewSection(
    title: String,
    videos: List<VideoItem>,
    onVideoClick: (VideoItem) -> Unit
) {
    Text(
        text = title,
        color = TvColors.TextPrimary,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    MyPreviewGrid(
        videos = videos,
        onVideoClick = onVideoClick
    )
}

@Composable
private fun MyDualEmptyPreview() {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        MyPreviewEmptySection(
            title = "\u6700\u8fd1\u89c2\u770b",
            message = "\u6682\u65e0\u8bb0\u5f55",
            modifier = Modifier.weight(1f)
        )
        MyPreviewEmptySection(
            title = "\u7a0d\u540e\u518d\u770b",
            message = "\u6682\u65e0\u5185\u5bb9",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MyPreviewEmptySection(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxHeight()) {
        Text(
            text = title,
            color = TvColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 14.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(TvColors.SurfaceElevated)
                .border(1.dp, TvColors.CardBorder, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                color = TvColors.TextSecondary,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun MyPreviewGrid(
    videos: List<VideoItem>,
    onVideoClick: (VideoItem) -> Unit
) {
    val columns = rememberMyPreviewColumnCount()
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(TvDimensions.compactGridHorizontalSpacing),
        verticalArrangement = Arrangement.spacedBy(TvDimensions.compactGridVerticalSpacing)
    ) {
        items(videos, key = { it.bvid.ifBlank { "aid:${it.aid}" } }) { video ->
            CompactVideoCard(
                video = video,
                onClick = { onVideoClick(video) }
            )
        }
    }
}

@Composable
private fun MyMenuRow(
    entry: MyMenuEntry,
    enabled: Boolean,
    focusRequester: FocusRequester,
    upFocusRequester: FocusRequester?,
    downFocusRequester: FocusRequester?,
    leftFocusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) TvDimensions.focusScale else 1f,
        animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing),
        label = "myMenuScale"
    )
    val cardShape = RoundedCornerShape(TvDimensions.cardRadius)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .scale(scale)
            .clip(cardShape)
            .background(if (focused) TvColors.SurfaceElevated else TvColors.Surface)
            .border(
                width = if (focused) TvDimensions.focusBorderWidth else 1.dp,
                color = if (focused) TvColors.FocusBorder else TvColors.CardBorder,
                shape = cardShape
            )
            .onFocusChanged {
                if (focused != it.isFocused) {
                    focused = it.isFocused
                }
            }
            .focusRequester(focusRequester)
            .focusProperties {
                upFocusRequester?.let { up = it }
                downFocusRequester?.let { down = it }
                leftFocusRequester?.let { left = it }
            }
            .focusable(enabled = enabled)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = entry.title,
            color = if (enabled) TvColors.TextPrimary else TvColors.TextMuted,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = entry.subtitle,
            color = if (enabled) TvColors.TextSecondary else TvColors.TextMuted,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

private fun isPreviewLoading(
    historyState: HistoryUiState?,
    watchLaterState: UiState<PagedVideoList>?
): Boolean {
    if (historyState == null || historyState is HistoryUiState.Loading) {
        return true
    }
    if (historyVideos(historyState).isNotEmpty()) {
        return false
    }
    return watchLaterState == null || watchLaterState is UiState.Loading
}

private fun historyVideos(historyState: HistoryUiState?): List<VideoItem> {
    return when (historyState) {
        is HistoryUiState.Success -> historyState.videos
        else -> emptyList()
    }
}

private fun watchLaterVideos(watchLaterState: UiState<PagedVideoList>?): List<VideoItem> {
    return when (watchLaterState) {
        is UiState.Success -> watchLaterState.data.videos
        else -> emptyList()
    }
}
