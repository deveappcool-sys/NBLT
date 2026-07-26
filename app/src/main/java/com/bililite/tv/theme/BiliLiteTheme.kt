package com.bililite.tv.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.bililite.tv.ui.components.TvBackground

val BiliLiteBackground = TvColors.BackgroundBottom
val BiliLiteCardBackground = TvColors.Surface
val BiliLitePrimary = Color(0xFF00A1D6)

@Composable
fun BiliLiteTheme(content: @Composable () -> Unit) {
    TvBackground {
        content()
    }
}
