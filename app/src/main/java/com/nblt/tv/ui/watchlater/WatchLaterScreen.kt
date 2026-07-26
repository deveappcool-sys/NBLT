package com.nblt.tv.ui.watchlater

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nblt.tv.model.UserInfo
import com.nblt.tv.model.VideoItem
import com.nblt.tv.theme.NbltBackground
import com.nblt.tv.ui.components.TvEmptyContent
import com.nblt.tv.ui.components.TvErrorContent
import com.nblt.tv.ui.components.TvLoadingContent
import com.nblt.tv.ui.components.TvNotLoggedInContent
import com.nblt.tv.ui.components.TvPageHeader
import com.nblt.tv.ui.home.VideoGrid
import com.nblt.tv.ui.state.PagedVideoList
import com.nblt.tv.ui.state.UiState

@Composable
fun WatchLaterScreen(
    currentUser: UserInfo?,
    watchLaterState: UiState<PagedVideoList>?,
    onLoginClick: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (currentUser == null) {
        TvNotLoggedInContent(
            hint = "\u767b\u5f55\u540e\u53ef\u67e5\u770b\u7a0d\u540e\u518d\u770b",
            onLoginClick = onLoginClick
        )
        return
    }

    when (val state = watchLaterState ?: UiState.Loading) {
        UiState.Loading -> TvLoadingContent(message = "\u6b63\u5728\u52a0\u8f7d\u7a0d\u540e\u518d\u770b...")
        is UiState.Error -> TvErrorContent(
            title = "\u7a0d\u540e\u518d\u770b\u52a0\u8f7d\u5931\u8d25",
            message = state.message,
            onRetry = onRetry
        )
        is UiState.Success -> {
            if (state.data.videos.isEmpty()) {
                TvEmptyContent(message = "\u6682\u65e0\u7a0d\u540e\u518d\u770b\u89c6\u9891")
            } else {
                Column(modifier = modifier.fillMaxSize()) {
                    TvPageHeader(title = "稍后再看", subtitle = "你的待播清单")
                    VideoGrid(
                        videos = state.data.videos,
                        onVideoClick = { video, _ -> onVideoClick(video) },
                        hasMore = state.data.hasMore,
                        isLoadingMore = state.data.isLoadingMore,
                        loadMoreError = state.data.loadMoreError,
                        onLoadMore = onLoadMore
                    )
                }
            }
        }
    }
}
