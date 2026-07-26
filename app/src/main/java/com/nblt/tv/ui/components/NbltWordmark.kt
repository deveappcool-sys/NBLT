package com.nblt.tv.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nblt.tv.theme.TvColors

/**
 * Fixed brand wordmark "NBLT" displayed at the top of [CinematicSideRail].
 * Always visible, no collapse / expand variants.
 */
@Composable
fun NbltWordmark(modifier: Modifier = Modifier) {
    Text(
        text = "NBLT",
        color = TvColors.TextPrimary.copy(alpha = 0.85f),
        fontSize = 13.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.04.sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Visible,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .padding(top = 8.dp, bottom = 4.dp)
    )
}
