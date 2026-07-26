package com.bililite.tv.ui.favorite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bililite.tv.model.FavoriteFolder
import com.bililite.tv.model.VideoItem
import com.bililite.tv.theme.BiliLiteBackground
import com.bililite.tv.ui.components.TvEmptyContent
import com.bililite.tv.ui.components.TvErrorContent
import com.bililite.tv.ui.components.TvLoadingContent
import com.bililite.tv.ui.components.TvPageHeader
import com.bililite.tv.ui.home.VideoGrid
import com.bililite.tv.ui.state.PagedVideoList
import com.bililite.tv.ui.state.UiState

@Composable
fun FavoriteFolderDetailScreen(
    folder: FavoriteFolder,
    videosState: UiState<PagedVideoList>?,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BiliLiteBackground)
    ) {
        TvPageHeader(
            title = folder.title,
            subtitle = "${folder.mediaCount} 个视频"
        )

        when (val state = videosState ?: UiState.Loading) {
            UiState.Loading -> TvLoadingContent(message = "\u6b63\u5728\u52a0\u8f7d\u6536\u85cf\u89c6\u9891...")
            is UiState.Error -> TvErrorContent(
                title = "\u6536\u85cf\u89c6\u9891\u52a0\u8f7d\u5931\u8d25",
                message = state.message,
                onRetry = onRetry
            )
            is UiState.Success -> {
                if (state.data.videos.isEmpty()) {
                    TvEmptyContent(message = "\u8be5\u6536\u85cf\u5939\u6682\u65e0\u89c6\u9891")
                } else {
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
