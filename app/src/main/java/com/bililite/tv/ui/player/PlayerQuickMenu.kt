package com.bililite.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bililite.tv.model.DanmakuSettings
import com.bililite.tv.model.VideoPage
import com.bililite.tv.model.VideoQuality
import com.bililite.tv.theme.BiliLitePrimary
import com.bililite.tv.theme.TvColors

fun buildQuickMenuItems(hasQualities: Boolean, hasMultiplePages: Boolean): List<PlayerQuickMenuItem> {
    return buildList {
        if (hasQualities) {
            add(PlayerQuickMenuItem.Quality)
        }
        add(PlayerQuickMenuItem.Speed)
        if (hasMultiplePages) {
            add(PlayerQuickMenuItem.Pages)
        }
        add(PlayerQuickMenuItem.Recommendations)
        add(PlayerQuickMenuItem.DanmakuToggle)
        add(PlayerQuickMenuItem.DanmakuSettings)
        add(PlayerQuickMenuItem.RestartPlayback)
        add(PlayerQuickMenuItem.BackToDetail)
    }
}

data class DanmakuSettingsMenuEntry(
    val label: String,
    val isSelected: (DanmakuSettings) -> Boolean,
    val apply: (DanmakuSettings) -> DanmakuSettings
)

fun danmakuSettingsMenuEntries(): List<DanmakuSettingsMenuEntry> {
    return listOf(
        DanmakuSettingsMenuEntry("\u5b57\u53f7\uff1a\u5c0f", { it.fontScale == 0.85f }) {
            it.copy(fontScale = 0.85f)
        },
        DanmakuSettingsMenuEntry("\u5b57\u53f7\uff1a\u4e2d", { it.fontScale == 1.0f }) {
            it.copy(fontScale = 1.0f)
        },
        DanmakuSettingsMenuEntry("\u5b57\u53f7\uff1a\u5927", { it.fontScale == 1.2f }) {
            it.copy(fontScale = 1.2f)
        },
        DanmakuSettingsMenuEntry("\u900f\u660e\u5ea6\uff1a\u4f4e", { it.alpha == 0.45f }) {
            it.copy(alpha = 0.45f)
        },
        DanmakuSettingsMenuEntry("\u900f\u660e\u5ea6\uff1a\u4e2d", { it.alpha == 0.7f }) {
            it.copy(alpha = 0.7f)
        },
        DanmakuSettingsMenuEntry("\u900f\u660e\u5ea6\uff1a\u6807\u51c6", { it.alpha == 0.8f }) {
            it.copy(alpha = 0.8f)
        },
        DanmakuSettingsMenuEntry("\u900f\u660e\u5ea6\uff1a\u9ad8", { it.alpha == 0.9f }) {
            it.copy(alpha = 0.9f)
        },
        DanmakuSettingsMenuEntry("\u901f\u5ea6\uff1a\u6162", { it.speed == 0.75f }) {
            it.copy(speed = 0.75f)
        },
        DanmakuSettingsMenuEntry("\u901f\u5ea6\uff1a\u4e2d", { it.speed == 1.0f }) {
            it.copy(speed = 1.0f)
        },
        DanmakuSettingsMenuEntry("\u901f\u5ea6\uff1a\u5feb", { it.speed == 1.35f }) {
            it.copy(speed = 1.35f)
        },
        DanmakuSettingsMenuEntry("\u663e\u793a\u533a\u57df\uff1a1/4 \u5c4f", { it.displayAreaRatio == 0.25f }) {
            it.copy(displayAreaRatio = 0.25f)
        },
        DanmakuSettingsMenuEntry("\u663e\u793a\u533a\u57df\uff1a1/2 \u5c4f", { it.displayAreaRatio == 0.5f }) {
            it.copy(displayAreaRatio = 0.5f)
        },
        DanmakuSettingsMenuEntry("\u663e\u793a\u533a\u57df\uff1a3/4 \u5c4f", { it.displayAreaRatio == 0.75f }) {
            it.copy(displayAreaRatio = 0.75f)
        },
        DanmakuSettingsMenuEntry("\u663e\u793a\u533a\u57df\uff1a\u5168\u5c4f", { it.displayAreaRatio == 1.0f }) {
            it.copy(displayAreaRatio = 1.0f)
        }
    )
}

fun quickMenuItemLabel(
    item: PlayerQuickMenuItem,
    danmakuEnabled: Boolean,
    qualityText: String?,
    playbackSpeed: Float,
    currentPageLabel: String?
): String {
    return when (item) {
        PlayerQuickMenuItem.Quality -> "\u6e05\u6670\u5ea6 >"
        PlayerQuickMenuItem.Speed -> "\u500d\u901f >"
        PlayerQuickMenuItem.Pages -> "\u5206 P >"
        PlayerQuickMenuItem.Recommendations -> "相关推荐 >"
        PlayerQuickMenuItem.DanmakuToggle -> "\u5f39\u5e55\uff1a${if (danmakuEnabled) "\u5f00" else "\u5173"}"
        PlayerQuickMenuItem.DanmakuSettings -> "\u5f39\u5e55\u8bbe\u7f6e >"
        PlayerQuickMenuItem.RestartPlayback -> "\u4ece\u5934\u64ad\u653e"
        PlayerQuickMenuItem.BackToDetail -> "\u8fd4\u56de\u8be6\u60c5\u9875"
    }
}

fun quickMenuItemSubtitle(
    item: PlayerQuickMenuItem,
    qualityText: String?,
    playbackSpeed: Float,
    currentPageLabel: String?
): String? {
    return when (item) {
        PlayerQuickMenuItem.Quality -> qualityText?.takeIf { it.isNotBlank() }
        PlayerQuickMenuItem.Speed -> formatSpeed(playbackSpeed)
        PlayerQuickMenuItem.Pages -> currentPageLabel
        PlayerQuickMenuItem.Recommendations -> "打开推荐视频面板"
        PlayerQuickMenuItem.BackToDetail -> null
        else -> null
    }
}

@Composable
fun PlayerQuickMenuPanel(
    items: List<PlayerQuickMenuItem>,
    focusedIndex: Int,
    danmakuEnabled: Boolean,
    qualityText: String?,
    playbackSpeed: Float,
    currentPageLabel: String?,
    modifier: Modifier = Modifier
) {
    MenuPanelContainer(title = "\u5feb\u6377\u83dc\u5355", modifier = modifier) {
        items.forEachIndexed { index, item ->
            val subtitle = quickMenuItemSubtitle(item, qualityText, playbackSpeed, currentPageLabel)
            MenuRow(
                text = quickMenuItemLabel(item, danmakuEnabled, qualityText, playbackSpeed, currentPageLabel),
                subtitle = subtitle,
                focused = index == focusedIndex,
                selected = false
            )
        }
    }
}

@Composable
fun PlayerSubMenuPanel(
    activeMenu: PlayerSubMenu,
    qualities: List<VideoQuality>,
    currentQualityQn: Int?,
    playbackSpeed: Float,
    pages: List<VideoPage>,
    currentCid: Long,
    danmakuSettings: DanmakuSettings,
    focusedIndex: Int,
    modifier: Modifier = Modifier
) {
    val title = when (activeMenu) {
        PlayerSubMenu.Quality -> "\u6e05\u6670\u5ea6"
        PlayerSubMenu.Speed -> "\u500d\u901f"
        PlayerSubMenu.Pages -> "\u5206 P"
        PlayerSubMenu.DanmakuSettings -> "\u5f39\u5e55\u8bbe\u7f6e"
        PlayerSubMenu.None -> ""
    }
    val rows = when (activeMenu) {
        PlayerSubMenu.Quality -> qualities.map { quality ->
            MenuRowData(
                text = quality.description,
                selected = quality.qn == currentQualityQn
            )
        }
        PlayerSubMenu.Speed -> playbackSpeeds.map { speed ->
            MenuRowData(
                text = formatSpeed(speed),
                selected = speed == playbackSpeed
            )
        }
        PlayerSubMenu.Pages -> pages.map { page ->
            MenuRowData(
                text = "P${page.page} ${page.part} ${formatPageDuration(page.duration)}",
                selected = page.cid == currentCid
            )
        }
        PlayerSubMenu.DanmakuSettings -> danmakuSettingsMenuEntries().map { entry ->
            MenuRowData(
                text = entry.label,
                selected = entry.isSelected(danmakuSettings)
            )
        }
        PlayerSubMenu.None -> emptyList()
    }
    val scrollState = rememberScrollState()
    MenuPanelContainer(title = title, modifier = modifier) {
        Column(
            modifier = Modifier.verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rows.forEachIndexed { index, row ->
                MenuRow(
                    text = row.text,
                    subtitle = null,
                    focused = index == focusedIndex,
                    selected = row.selected
                )
            }
        }
    }
}

@Composable
private fun MenuPanelContainer(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .widthIn(min = 280.dp, max = 390.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xF2121414))
            .border(1.dp, TvColors.CardBorder, RoundedCornerShape(18.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        content()
    }
}

@Composable
private fun MenuRow(
    text: String,
    subtitle: String?,
    focused: Boolean,
    selected: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    focused -> TvColors.FocusAccent
                    selected -> TvColors.AccentSoft
                    else -> Color.Transparent
                }
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = if (selected && subtitle == null) "$text  \u5f53\u524d" else text,
            color = if (focused) Color(0xFF161817) else Color.White,
            fontSize = 17.sp,
            fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Medium
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
            color = if (focused) Color(0xFF3B3D3C) else TvColors.TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

private data class MenuRowData(
    val text: String,
    val selected: Boolean
)

fun formatSpeed(speed: Float): String {
    return if (speed == 1.0f) "1.0x" else "${speed}x"
}

private fun formatPageDuration(seconds: Int): String {
    if (seconds <= 0) return ""
    val minutes = seconds / 60
    val remain = seconds % 60
    return "%02d:%02d".format(minutes, remain)
}
