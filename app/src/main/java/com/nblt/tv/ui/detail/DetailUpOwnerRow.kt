package com.nblt.tv.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nblt.tv.theme.TvColors
import com.nblt.tv.theme.TvDimensions
import com.nblt.tv.util.BilibiliImageUrl

@Composable
internal fun DetailUpOwnerRow(
    ownerName: String,
    avatarUrl: String,
    onClick: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .background(if (focused) Color(0x801A222C) else Color.Transparent, shape)
            .border(
                width = if (focused) TvDimensions.focusBorderWidth else 1.dp,
                color = if (focused) TvColors.FocusRing else Color.Transparent,
                shape = shape
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) DetailFocusLog.focusedUpName()
            }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 26.dp else 32.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(TvColors.SurfaceDarkElevated, TvColors.SurfaceDark)
                    )
                )
                .border(1.dp, Color(0x55FFFFFF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = BilibiliImageUrl.avatar(avatarUrl, size = 64),
                    contentDescription = ownerName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = ownerName.firstOrNull()?.toString().orEmpty(),
                    color = TvColors.TextPrimary,
                    fontSize = if (compact) 12.sp else 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            text = ownerName,
            color = TvColors.TextPrimary,
            fontSize = if (compact) 16.sp else 19.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
