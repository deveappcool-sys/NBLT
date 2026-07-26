package com.bililite.tv.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bililite.tv.model.DanmakuSettings
import com.bililite.tv.model.VideoPage
import com.bililite.tv.model.VideoQuality

@Composable
fun PlayerControlsOverlay(
    title: String,
    state: PlayerUiState,
    seekHint: String?,
    seekPreviewPositionMs: Long? = null,
    qualityText: String?,
    availableQualities: List<VideoQuality>,
    currentQualityQn: Int?,
    playbackSpeed: Float,
    pages: List<VideoPage>,
    currentCid: Long,
    danmakuEnabled: Boolean,
    danmakuSettings: DanmakuSettings,
    quickMenuOpen: Boolean,
    activeSubMenu: PlayerSubMenu,
    quickMenuIndex: Int,
    subMenuIndex: Int,
    modifier: Modifier = Modifier
) {
    if (!quickMenuOpen) {
        return
    }

    val quickMenuItems = buildQuickMenuItems(
        hasQualities = availableQualities.isNotEmpty(),
        hasMultiplePages = pages.size > 1
    )
    val currentPage = pages.firstOrNull { it.cid == currentCid }
    val currentPageLabel = currentPage?.let { page ->
        "P${page.page}${if (page.part.isNotBlank()) " ${page.part}" else ""}"
    }

    Box(modifier = modifier) {
        if (activeSubMenu != PlayerSubMenu.None) {
            PlayerSubMenuPanel(
                activeMenu = activeSubMenu,
                qualities = availableQualities,
                currentQualityQn = currentQualityQn,
                playbackSpeed = playbackSpeed,
                pages = pages,
                currentCid = currentCid,
                danmakuSettings = danmakuSettings,
                focusedIndex = subMenuIndex,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 42.dp)
            )
        } else {
            PlayerQuickMenuPanel(
                items = quickMenuItems,
                focusedIndex = quickMenuIndex.coerceIn(0, (quickMenuItems.size - 1).coerceAtLeast(0)),
                danmakuEnabled = danmakuEnabled,
                qualityText = qualityText,
                playbackSpeed = playbackSpeed,
                currentPageLabel = currentPageLabel,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 42.dp)
            )
        }
    }
}
