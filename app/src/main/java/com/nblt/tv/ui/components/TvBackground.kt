package com.nblt.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import com.nblt.tv.theme.TvColors

/**
 * Cinematic dark blue-gray background.
 *
 * Three-layer composition:
 * 1. Solid base [TvColors.BackgroundDarkBottom] = #06090D
 * 2. Vertical gradient: #0B0F14 (top) -> #06090D (bottom) over 900dp
 *
 * Pure dark blue-gray tones — no red, pink, purple or neon.
 * No backdrop blur (perf-unfriendly on TV chipsets) and no
 * Material tonalElevation.
 */
@Composable
fun TvBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TvColors.BackgroundDark)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        TvColors.BackgroundDarkTop,
                        TvColors.BackgroundDarkTop,
                        TvColors.BackgroundDark
                    ),
                    startY = 0f,
                    endY = 900f
                )
            )
    ) {
        content()
    }
}
