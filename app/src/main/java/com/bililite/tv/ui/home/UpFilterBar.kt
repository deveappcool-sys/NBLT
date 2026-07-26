package com.bililite.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bililite.tv.model.FollowedUp
import com.bililite.tv.theme.TvColors
import com.bililite.tv.theme.TvDimensions
import com.bililite.tv.util.BilibiliImageUrl

@Composable
fun UpFilterBar(
    followedUps: List<FollowedUp>,
    selectedUpMid: Long?,
    onSelected: (Long?) -> Unit,
    selectedItemFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val items = listOf<FollowedUp?>(null) + followedUps

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(start = 4.dp, end = 8.dp)
    ) {
        items(items, key = { it?.mid ?: -1L }) { up ->
            UpFilterChip(
                text = up?.name ?: "\u5168\u90e8",
                avatarUrl = up?.avatarUrl.orEmpty(),
                selected = selectedUpMid == up?.mid,
                focusRequester = selectedItemFocusRequester.takeIf { selectedUpMid == up?.mid },
                onClick = { onSelected(up?.mid) }
            )
        }
    }
}

@Composable
private fun UpFilterChip(
    text: String,
    avatarUrl: String,
    selected: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> TvColors.SurfaceElevated
        selected -> TvColors.NavPillSelected
        else -> TvColors.NavPillNormal
    }
    val textColor = when {
        selected -> TvColors.NavPillSelectedText
        focused -> TvColors.TextPrimary
        else -> TvColors.TextSecondary
    }

    Row(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(background)
            .border(
                width = if (focused) TvDimensions.focusBorderWidth else 0.dp,
                color = if (focused) TvColors.FocusBorder else Color.Transparent,
                shape = RoundedCornerShape(17.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (avatarUrl.isNotBlank()) {
            AsyncImage(
                model = BilibiliImageUrl.avatar(avatarUrl, size = 48),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(24.dp).clip(CircleShape)
            )
        }
        Text(
            text = text,
            color = if (focused) TvColors.FocusAccent else textColor,
            fontSize = 15.sp,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = if (avatarUrl.isNotBlank()) 8.dp else 4.dp)
        )
    }
}
