package com.bililite.tv.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bililite.tv.model.VideoItem

@Composable
internal fun DetailHeroSection(
    video: VideoItem,
    leftPanel: @Composable () -> Unit,
    layoutSpec: DetailLayoutSpec,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (layoutSpec.isCompactHeight) 14.dp else 20.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .weight(0.60f)
                .fillMaxHeight()
        ) {
            leftPanel()
        }

        DetailCoverPreview(
            video = video,
            maxCoverWidth = layoutSpec.coverMaxWidth,
            maxCoverHeight = layoutSpec.coverMaxHeight,
            modifier = Modifier
                .weight(0.36f)
                .fillMaxHeight()
        )
    }
}
