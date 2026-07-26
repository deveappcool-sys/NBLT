package com.bililite.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bililite.tv.theme.TvColors
import com.bililite.tv.theme.TvDimensions

@Composable
fun TvTopNavBar(
    items: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    onRefreshSelected: (String) -> Unit,
    downFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(TvDimensions.navBarHeight / 2))
                .background(TvColors.NavBarTrack)
                .border(1.dp, TvColors.CardBorder, RoundedCornerShape(TvDimensions.navBarHeight / 2))
                .padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(TvDimensions.navPillSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                TvNavPill(
                    text = item,
                    selected = selected == item,
                    downFocusRequester = downFocusRequester,
                    onClick = {
                        if (selected == item) {
                            onRefreshSelected(item)
                        } else {
                            onSelected(item)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun TvNavPill(
    text: String,
    selected: Boolean,
    downFocusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> TvColors.SurfaceElevated
        selected -> TvColors.NavPillSelected
        else -> TvColors.NavPillNormal
    }
    val textColor = when {
        focused -> TvColors.FocusAccent
        selected -> TvColors.NavPillSelectedText
        else -> TvColors.TextSecondary
    }

    Box(
        modifier = Modifier
            .height(TvDimensions.navBarHeight - 10.dp)
            .clip(RoundedCornerShape(TvDimensions.navBarHeight / 2))
            .background(background)
            .border(
                width = if (focused) TvDimensions.focusBorderWidth else 0.dp,
                color = if (focused) TvColors.FocusBorder else Color.Transparent,
                shape = RoundedCornerShape(TvDimensions.navBarHeight / 2)
            )
            .then(
                if (downFocusRequester != null) {
                    Modifier.focusProperties { down = downFocusRequester }
                } else Modifier
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(
                horizontal = TvDimensions.navPillHorizontalPadding,
                vertical = TvDimensions.navPillVerticalPadding
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 16.sp,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium
        )
    }
}
