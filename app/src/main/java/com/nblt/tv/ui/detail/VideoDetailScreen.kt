package com.nblt.tv.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import com.nblt.tv.model.VideoItem
import com.nblt.tv.model.VideoPage
import com.nblt.tv.theme.TvColors
import com.nblt.tv.ui.components.TvBackground
import com.nblt.tv.ui.state.UiState
import com.nblt.tv.util.BilibiliImageUrl

private val DetailPageHorizontalPadding = 56.dp

@Composable
fun VideoDetailScreen(
    video: VideoItem,
    pagesLoadState: UiState<Unit>?,
    onRetryPagesLoad: () -> Unit,
    onPlayClick: (VideoItem) -> Unit,
    onPageSelect: (VideoPage) -> Unit = {},
    onUpClick: ((Long, String) -> Unit)? = null,
    onLikeClick: (() -> Unit)? = null,
    onCoinClick: (() -> Unit)? = null,
    onFavoriteClick: (() -> Unit)? = null,
    relatedSources: DetailRelatedSources = DetailRelatedSources(),
    onRelatedVideoClick: (VideoItem) -> Unit = {}
) {
    val playFocusRequester = remember { FocusRequester() }
    val currentPage = remember(video.cid, video.pages) {
        video.pages.firstOrNull { it.cid == video.cid }
            ?: video.pages.firstOrNull { it.page == video.currentPage }
    }
    val hasParts = video.pages.size > 1
    val pagesLoadFailed = pagesLoadState is UiState.Error
    val relatedVideos = remember(video, relatedSources) {
        resolveDetailRelatedVideos(video, relatedSources)
    }
    val hasBottomContent = hasParts || relatedVideos.isNotEmpty()
    val layoutSpec = rememberDetailLayoutSpec(hasParts = hasParts, hasRelated = relatedVideos.isNotEmpty())
    val heroCoverUrl = remember(video.coverUrl) {
        BilibiliImageUrl.cover(video.coverUrl, width = 960, height = 540)
    }

    LaunchedEffect(video.cid, video.aid, layoutSpec) {
        logDetailLayout(layoutSpec)
        playFocusRequester.requestFocus()
    }

    TvBackground(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = heroCoverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopEnd,
                modifier = Modifier
                    .fillMaxWidth(0.70f)
                    .fillMaxHeight(0.74f)
                    .align(Alignment.TopEnd)
                    .alpha(0.45f)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to Color(0xF20B0F14),
                            0.50f to Color(0x990B0F14),
                            1f to Color(0x330B0F14)
                        )
                    )
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0x330B0F14),
                            0.45f to Color.Transparent,
                            1f to TvColors.BackgroundDark
                        )
                    )
            )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = DetailPageHorizontalPadding,
                    end = DetailPageHorizontalPadding,
                    top = layoutSpec.topSafePadding,
                    bottom = layoutSpec.bottomSafePadding
                )
        ) {
            DetailHeroSection(
                video = video,
                layoutSpec = layoutSpec,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(layoutSpec.heroHeight - 42.dp),
                leftPanel = {
                    DetailInfoPanel(
                        video = video,
                        pagesLoadState = pagesLoadState,
                        currentPage = currentPage,
                        onUpClick = onUpClick,
                        layoutSpec = layoutSpec,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            )

            DetailActionRow(
                video = video,
                layoutSpec = layoutSpec,
                playFocusRequester = playFocusRequester,
                onPlayClick = onPlayClick,
                onUpClick = onUpClick,
                onRetryPagesLoad = onRetryPagesLoad,
                pagesLoadFailed = pagesLoadFailed,
                onLikeClick = onLikeClick,
                onCoinClick = onCoinClick,
                onFavoriteClick = onFavoriteClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(layoutSpec.actionHeight)
            )

            if (hasBottomContent) {
                HorizontalDivider(
                    color = TvColors.SideRailBorder,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 18.dp)
                )

                DetailBottomContent(
                    video = video,
                    hasParts = hasParts,
                    relatedVideos = relatedVideos,
                    selectedCid = video.cid,
                    layoutSpec = layoutSpec,
                    onPageSelect = onPageSelect,
                    onRelatedVideoClick = onRelatedVideoClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }
        }
    }
}
