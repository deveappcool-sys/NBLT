package com.nblt.tv.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.nblt.tv.ui.components.TvBackground

val NbltBackground = TvColors.BackgroundBottom
val NbltCardBackground = TvColors.Surface
val NbltPrimary = Color(0xFF00A1D6)

@Composable
fun NbltTheme(content: @Composable () -> Unit) {
    TvBackground {
        content()
    }
}
