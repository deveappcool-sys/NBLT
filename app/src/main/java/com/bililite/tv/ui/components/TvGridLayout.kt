package com.bililite.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

private const val GRID_COLUMNS_WIDE = 5
private const val GRID_COLUMNS_NARROW = 4
private const val WIDE_SCREEN_WIDTH_DP = 1000
private const val MY_PREVIEW_COLUMNS = 4
private const val MY_PREVIEW_COLUMNS_NARROW = 3
private const val MY_PREVIEW_PANE_WIDTH_DP = 520

@Composable
fun rememberVideoGridColumnCount(): Int {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    return remember(screenWidthDp) {
        if (screenWidthDp >= WIDE_SCREEN_WIDTH_DP) {
            GRID_COLUMNS_WIDE
        } else {
            GRID_COLUMNS_NARROW
        }
    }
}

@Composable
fun rememberMyPreviewColumnCount(): Int {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    return remember(screenWidthDp) {
        val paneWidth = (screenWidthDp * 0.48f).toInt()
        if (paneWidth >= MY_PREVIEW_PANE_WIDTH_DP) {
            MY_PREVIEW_COLUMNS
        } else {
            MY_PREVIEW_COLUMNS_NARROW
        }
    }
}
