package com.bililite.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bililite.tv.model.VideoItem
import com.bililite.tv.theme.TvColors
import com.bililite.tv.theme.TvDimensions
import com.bililite.tv.ui.components.VideoCard
import com.bililite.tv.ui.components.VideoCardStyle
import com.bililite.tv.ui.components.logFocusRestoreScroll
import com.bililite.tv.ui.components.restoreGridScrollTarget
import com.bililite.tv.ui.components.rememberVideoGridColumnCount
import com.bililite.tv.ui.components.rememberDeterministicGridVerticalFocusHandler
import com.bililite.tv.model.stableContentKey

private const val LOAD_MORE_ROW_THRESHOLD = 2

@Composable
fun VideoGrid(
    videos: List<VideoItem>,
    onVideoClick: (VideoItem, Int) -> Unit,
    modifier: Modifier = Modifier,
    leftFocusRequester: FocusRequester? = null,
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    loadMoreError: String? = null,
    onLoadMore: (() -> Unit)? = null,
    onOwnerClick: ((Long, String) -> Unit)? = null,
    showRanking: Boolean = false,
    tabKey: String? = null,
    focusRestore: HomeVideoFocusRestore? = null,
    onVideoFocused: ((VideoItem, Int) -> Unit)? = null,
    onContentFocusRequesterChanged: (FocusRequester) -> Unit = {},
    onFocusRestored: () -> Unit = {},
    requestInitialFocus: Boolean = false,
    entryFocusRequester: FocusRequester? = null,
    cardStyle: VideoCardStyle = VideoCardStyle.Standard,
    showProgress: Boolean = false
) {
    val columnCount = rememberVideoGridColumnCount().coerceAtLeast(1)
    val gridState = rememberLazyGridState()
    val itemKeys = remember(videos) { buildStableVideoGridKeys(videos) }

    // Keep requester creation synchronous with composition. The old LaunchedEffect-based
    // resizing produced one composition with null requesters, then rebound every card on a
    // later frame. That extra frame made detail-return focus visibly "pop" back in.
    // A plain remembered list stays stable across pagination while being ready before the
    // LazyVerticalGrid items are composed.
    val focusRequesters = remember { mutableListOf<FocusRequester>() }
    while (focusRequesters.size < videos.size) {
        focusRequesters.add(FocusRequester())
    }
    while (focusRequesters.size > videos.size) {
        focusRequesters.removeAt(focusRequesters.lastIndex)
    }
    if (entryFocusRequester != null && focusRequesters.isNotEmpty() &&
        focusRequesters[0] !== entryFocusRequester
    ) {
        focusRequesters[0] = entryFocusRequester
    }

    val verticalFocusHandler = rememberDeterministicGridVerticalFocusHandler(
        gridState = gridState,
        itemCount = videos.size,
        columns = columnCount,
        focusRequesterAt = { index -> focusRequesters.getOrNull(index) }
    )

    // When this grid is opened as a fresh "view all" page (no incoming focusRestore
    // to restore from), grab focus onto the first card so the side rail collapses and
    // the user lands inside the content instead of being stuck on the rail.
    LaunchedEffect(Unit) {
        if (!requestInitialFocus) return@LaunchedEffect
        val firstRequester = focusRequesters.firstOrNull() ?: return@LaunchedEffect
        val requestedImmediately = runCatching {
            firstRequester.requestFocus()
        }.isSuccess
        if (!requestedImmediately) {
            withFrameNanos { }
            runCatching { firstRequester.requestFocus() }
        }
    }

    LaunchedEffect(focusRestore?.restoreToken, focusRestore?.videoKey, videos.size, itemKeys) {
        val restore = focusRestore ?: return@LaunchedEffect
        if (restore.restoreToken == 0L || videos.isEmpty()) {
            return@LaunchedEffect
        }
        if (tabKey != null && restore.tab != tabKey) {
            return@LaunchedEffect
        }

        logRestoreFocusAttempt(restore)

        val matchedIndex = videos.indexOfFirst { it.matchesHomeFocusKey(restore.videoKey) }
        val targetIndex = when {
            matchedIndex >= 0 -> matchedIndex
            restore.index in videos.indices -> restore.index
            videos.isNotEmpty() -> restore.index.coerceIn(0, videos.lastIndex)
            else -> -1
        }

        logDynamicFocusRestore(
            tabKey = tabKey,
            restoreKey = restore.videoKey,
            restoreIndex = restore.index,
            resolvedIndex = targetIndex,
            videoCount = videos.size,
            focusRequesterCount = focusRequesters.size
        )

        if (targetIndex < 0 || targetIndex >= focusRequesters.size) {
            logDynamicCrashGuard(
                action = "restoreFocus",
                detail = "skip invalid targetIndex=$targetIndex focusRequesters=${focusRequesters.size}"
            )
            onFocusRestored()
            return@LaunchedEffect
        }

        val targetRequester = focusRequesters[targetIndex]

        // The grid state is saved by MainActivity's SaveableStateProvider, so on the
        // normal detail-return path the original card is already composed at the original
        // scroll offset. Request it immediately and do not scroll at all. This removes the
        // old animateScrollToItem -> delay -> requestFocus sequence that visibly moved the
        // list and then scaled the focused card a second time.
        var focusRequested = runCatching {
            targetRequester.requestFocus()
        }.isSuccess

        // Fallback only when the target is genuinely not attached (for example after data
        // changed while the detail page was open). Use one non-animated pre-position and a
        // single frame boundary, then request focus exactly once more.
        if (!focusRequested) {
            val scrollTarget = restoreGridScrollTarget(targetIndex, columnCount, videos.size)
            logFocusRestoreScroll(
                focusedIndex = targetIndex,
                targetIndex = scrollTarget,
                columns = columnCount
            )
            runCatching {
                gridState.scrollToItem(scrollTarget)
            }.onFailure { error ->
                logDynamicCrashGuard(
                    action = "restoreScrollToItem",
                    detail = "targetIndex=$scrollTarget videoCount=${videos.size}",
                    throwable = error
                )
            }
            withFrameNanos { }
            focusRequested = runCatching {
                targetRequester.requestFocus()
            }.isSuccess
        }

        if (!focusRequested) {
            logDynamicCrashGuard(
                action = "restoreRequestFocus",
                detail = "targetIndex=$targetIndex"
            )
        }
        val success = matchedIndex >= 0 && focusRequested
        logRestoreFocusResult(success = success, resolvedIndex = targetIndex, fallback = !success)
        onFocusRestored()
    }

    fun maybeLoadMoreOnFocus(index: Int) {
        if (onLoadMore == null || !hasMore || isLoadingMore || loadMoreError != null) {
            return
        }
        if (index !in videos.indices) {
            return
        }
        val threshold = (columnCount * LOAD_MORE_ROW_THRESHOLD).coerceAtLeast(columnCount)
        if (index >= videos.size - threshold) {
            logDynamicLoadMoreTriggered(
                tabKey = tabKey,
                index = index,
                videoCount = videos.size,
                threshold = threshold,
                isLoadingMore = isLoadingMore,
                hasMore = hasMore
            )
            onLoadMore()
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columnCount),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = TvDimensions.gridBottomPadding),
        horizontalArrangement = Arrangement.spacedBy(TvDimensions.gridHorizontalSpacing),
        verticalArrangement = Arrangement.spacedBy(TvDimensions.gridVerticalSpacing)
    ) {
        itemsIndexed(videos, key = { index, _ -> itemKeys.getOrElse(index) { "idx:$index" } }) { index, video ->
            if (index !in videos.indices) {
                return@itemsIndexed
            }
            val clickHandler = remember(video, index, onVideoClick) {
                {
                    if (index in videos.indices) {
                        onVideoClick(video, index)
                    }
                }
            }
            val focusHandler = remember(
                video,
                index,
                tabKey,
                onVideoFocused,
                gridState,
                columnCount,
                videos.size,
                isLoadingMore,
                hasMore
            ) {
                { focused: Boolean ->
                    if (focused && index in videos.indices) {
                        val visibleIndexes = gridState.layoutInfo.visibleItemsInfo
                            .map { it.index }
                            .filter { it in videos.indices }
                        logDynamicFocusChanged(
                            tabKey = tabKey,
                            index = index,
                            video = video,
                            videoCount = videos.size,
                            columns = columnCount,
                            firstVisible = visibleIndexes.minOrNull() ?: -1,
                            lastVisible = visibleIndexes.maxOrNull() ?: -1,
                            itemKey = itemKeys.getOrElse(index) { video.homeFocusKey(index) },
                            focusRequesterCount = focusRequesters.size,
                            isLoadingMore = isLoadingMore
                        )
                        tabKey?.let { logVideoFocused(it, index, video) }
                        onVideoFocused?.invoke(video, index)
                        focusRequesters.getOrNull(index)
                            ?.let { onContentFocusRequesterChanged(it) }
                        maybeLoadMoreOnFocus(index)
                    }
                }
            }
            VideoCard(
                video = video,
                onClick = clickHandler,
                rank = (index + 1).takeIf { showRanking },
                leftFocusRequester = leftFocusRequester.takeIf { index % columnCount == 0 },
                onOwnerClick = onOwnerClick,
                focusRequester = focusRequesters.getOrNull(index),
                blockDownFocus = false,
                onVerticalFocusMove = { rowDelta ->
                    val isBottomEdge = rowDelta > 0 && index + columnCount >= videos.size
                    if (isBottomEdge && loadMoreError != null && onLoadMore != null) {
                        onLoadMore()
                        true
                    } else {
                        verticalFocusHandler(index, rowDelta)
                    }
                },
                onCardFocusChanged = focusHandler,
                style = cardStyle,
                showProgress = showProgress
            )
        }

        item(key = "load-more-footer", span = { GridItemSpan(maxLineSpan) }) {
            LoadMoreFooter(
                hasMore = hasMore,
                isLoadingMore = isLoadingMore,
                error = loadMoreError,
                onRetry = onLoadMore
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LoadMoreFooter(
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
            .background(TvColors.Surface, RoundedCornerShape(TvDimensions.cardRadius))
            .focusProperties { down = FocusRequester.Cancel }
            .clickable(enabled = error != null && onRetry != null) { onRetry?.invoke() }
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = text,
            color = if (error == null) TvColors.TextSecondary else TvColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 18.dp)
        )
    }
}
