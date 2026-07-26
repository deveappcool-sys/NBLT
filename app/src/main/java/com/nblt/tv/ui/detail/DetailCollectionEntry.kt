package com.nblt.tv.ui.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nblt.tv.theme.NbltPrimary
import com.nblt.tv.theme.TvColors
import com.nblt.tv.theme.TvDimensions

@Composable
internal fun DetailCollectionEntry(
    pageCount: Int,
    seriesTitle: String?,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "collectionEntryScale")
    val shape = RoundedCornerShape(10.dp)
    val label = seriesTitle?.takeIf { it.isNotBlank() } ?: "\u5408\u96c6 / \u5206 P"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(shape)
            .background(Color(0xFF1E2630))
            .border(
                width = if (focused) TvDimensions.focusBorderWidth else 1.dp,
                color = if (focused) Color.White else TvColors.CardBorder,
                shape = shape
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(NbltPrimary.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "P",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "\u5171 $pageCount \u96c6",
                color = TvColors.TextMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            text = "\u203a",
            color = TvColors.TextSecondary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Light
        )
    }
}
