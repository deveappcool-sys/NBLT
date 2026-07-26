package com.nblt.tv.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nblt.tv.model.VideoItem
import com.nblt.tv.model.VideoPage

@Composable
internal fun DetailBottomContent(
    video: VideoItem,
    hasParts: Boolean,
    relatedVideos: List<VideoItem>,
    selectedCid: Long,
    layoutSpec: DetailLayoutSpec,
    onPageSelect: (VideoPage) -> Unit,
    onRelatedVideoClick: (VideoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasRelated = relatedVideos.isNotEmpty()
    if (!hasParts && !hasRelated) {
        return
    }

    Column(modifier = modifier.fillMaxWidth().fillMaxHeight()) {
        if (hasParts) {
            DetailPartSection(
                video = video,
                pages = video.pages,
                selectedCid = selectedCid,
                onPageSelect = onPageSelect,
                layoutSpec = layoutSpec,
                title = "\u5206 P / \u5408\u96c6",
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (hasRelated) {
                            Modifier.weight(0.52f).fillMaxHeight()
                        } else {
                            Modifier.fillMaxHeight()
                        }
                    )
            )
        }

        if (hasRelated) {
            DetailRelatedSection(
                videos = relatedVideos,
                onVideoClick = onRelatedVideoClick,
                layoutSpec = layoutSpec,
                showTopSpacing = hasParts,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (hasParts) {
                            Modifier.weight(0.48f).fillMaxHeight()
                        } else {
                            Modifier.fillMaxHeight()
                        }
                    )
            )
        }
    }
}
