package com.bililite.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bililite.tv.model.VideoItem

/**
 * Lightweight video card for preview panels (e.g. My page recent watch).
 */
@Composable
fun CompactVideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    sublineText: String? = null,
    modifier: Modifier = Modifier,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    onCardFocusChanged: ((Boolean) -> Unit)? = null
) {
    VideoCardLayout(
        variant = VideoCardVariant.Compact,
        video = video,
        onClick = onClick,
        onOwnerClick = null,
        compactSubline = sublineText,
        modifier = modifier,
        focusRequester = focusRequester,
        onCardFocusChanged = onCardFocusChanged
    )
}
