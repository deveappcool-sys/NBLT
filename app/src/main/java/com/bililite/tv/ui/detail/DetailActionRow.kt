package com.bililite.tv.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bililite.tv.model.VideoItem
import com.bililite.tv.theme.TvColors
import com.bililite.tv.theme.TvDimensions

@Composable
internal fun DetailActionRow(
    video: VideoItem,
    layoutSpec: DetailLayoutSpec,
    playFocusRequester: FocusRequester,
    onPlayClick: (VideoItem) -> Unit,
    onUpClick: ((Long, String) -> Unit)?,
    onRetryPagesLoad: (() -> Unit)? = null,
    pagesLoadFailed: Boolean = false,
    onLikeClick: (() -> Unit)? = null,
    onCoinClick: (() -> Unit)? = null,
    onFavoriteClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val displayStats = remember(video) { video.toDetailDisplayStats() }
    val horizontalScroll = rememberScrollState()
    val secondaryHeight = layoutSpec.secondaryButtonHeight

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(horizontalScroll),
        horizontalArrangement = Arrangement.spacedBy(if (layoutSpec.isCompactHeight) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DetailPlayButton(
            onClick = { onPlayClick(video) },
            buttonHeight = layoutSpec.playButtonHeight,
            buttonWidth = layoutSpec.playButtonWidth,
            modifier = Modifier.focusRequester(playFocusRequester)
        )

        if (video.historyProgress > 0L) {
            DetailSecondaryActionButton(
                label = "继续",
                width = if (layoutSpec.isCompactHeight) 118.dp else 136.dp,
                buttonHeight = secondaryHeight,
                onClick = { onPlayClick(video) }
            )
        }

        if (pagesLoadFailed && onRetryPagesLoad != null) {
            DetailSecondaryActionButton(
                label = "重试分P",
                width = if (layoutSpec.isCompactHeight) 128.dp else 146.dp,
                buttonHeight = secondaryHeight,
                onClick = onRetryPagesLoad
            )
        }

        if (onUpClick != null && video.ownerMid > 0L) {
            DetailSecondaryActionButton(
                label = "UP 空间",
                width = if (layoutSpec.isCompactHeight) 128.dp else 148.dp,
                buttonHeight = secondaryHeight,
                onClick = { onUpClick(video.ownerMid, video.ownerName) },
                emphasized = true
            )
        }

        formatDetailCountLabel("点赞", displayStats.likeCount)?.let { text ->
            if (onLikeClick != null) {
                DetailSecondaryActionButton(
                    label = text,
                    width = if (layoutSpec.isCompactHeight) 142.dp else 166.dp,
                    buttonHeight = secondaryHeight,
                    onClick = onLikeClick
                )
            } else {
                DetailStatDisplayChip(text, if (layoutSpec.isCompactHeight) 44.dp else 50.dp)
            }
        }
        formatDetailCountLabel("投币", displayStats.coinCount)?.let { text ->
            if (onCoinClick != null) {
                DetailSecondaryActionButton(
                    label = text,
                    width = if (layoutSpec.isCompactHeight) 142.dp else 166.dp,
                    buttonHeight = secondaryHeight,
                    onClick = onCoinClick
                )
            } else {
                DetailStatDisplayChip(text, if (layoutSpec.isCompactHeight) 44.dp else 50.dp)
            }
        }
        formatDetailCountLabel("收藏", displayStats.favoriteCount)?.let { text ->
            if (onFavoriteClick != null) {
                DetailSecondaryActionButton(
                    label = text,
                    width = if (layoutSpec.isCompactHeight) 142.dp else 166.dp,
                    buttonHeight = secondaryHeight,
                    onClick = onFavoriteClick
                )
            } else {
                DetailStatDisplayChip(text, if (layoutSpec.isCompactHeight) 44.dp else 50.dp)
            }
        }
    }
}

@Composable
private fun DetailPlayButton(
    onClick: () -> Unit,
    buttonHeight: Dp,
    buttonWidth: Dp,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    val innerShape = RoundedCornerShape(16.dp)
    val borderWidth = if (focused) 3.dp else 1.dp
    val borderColor = if (focused) Color(0xFFF1C45F) else Color(0xB3E2B15A)
    val goldBrush = Brush.verticalGradient(
        if (focused) {
            listOf(Color(0xFFFFE8B7), Color(0xFFF1C66F), Color(0xFFE4AC4D))
        } else {
            listOf(Color(0xFFFFDFA0), Color(0xFFE9B55C), Color(0xFFD99D3E))
        }
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .width(buttonWidth)
            .height(buttonHeight)
            .shadow(
                elevation = if (focused) 20.dp else 8.dp,
                shape = shape,
                ambientColor = if (focused) Color(0xB3D9A55F) else Color(0x66D9A55F),
                spotColor = if (focused) Color(0xB3D9A55F) else Color(0x66D9A55F),
                clip = false
            )
            .clip(shape)
            .background(borderColor, shape)
            .padding(borderWidth)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) DetailFocusLog.focusedPlayButton()
            }
            .onPreviewKeyEvent { event ->
                event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionLeft
            }
            .focusable()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(innerShape)
                .background(goldBrush, innerShape)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (buttonHeight <= 50.dp) 10.dp else 12.dp)
        ) {
            PlayTriangleGlyph(glyphSize = if (buttonHeight <= 50.dp) 19.dp else 23.dp)
            Text(
                text = "播放",
                color = Color(0xFF1A1309),
                fontSize = if (buttonHeight <= 50.dp) 17.sp else 20.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Visible,
                textAlign = TextAlign.Center
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 1.dp)
                .height(1.dp)
                .background(Color(0xCCFFF7DF))
        )
    }
}

@Composable
private fun PlayTriangleGlyph(glyphSize: Dp) {
    Canvas(modifier = Modifier.size(glyphSize)) {
        val path = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.16f)
            lineTo(size.width * 0.82f, size.height * 0.50f)
            lineTo(size.width * 0.28f, size.height * 0.84f)
            close()
        }
        drawPath(path = path, color = Color(0xFF241707))
    }
}

@Composable
private fun DetailSecondaryActionButton(
    label: String,
    width: Dp,
    buttonHeight: Dp,
    onClick: () -> Unit,
    selected: Boolean = false,
    emphasized: Boolean = false,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val glassBrush = when {
        selected -> Brush.verticalGradient(
            listOf(Color(0x7AD9A55F), Color(0x4D2A241B))
        )
        emphasized && focused -> Brush.verticalGradient(
            listOf(Color(0xEE6A7684), Color(0xD6384653))
        )
        emphasized -> Brush.verticalGradient(
            listOf(Color(0xD45C6875), Color(0xB82C3742))
        )
        focused -> Brush.verticalGradient(
            listOf(Color(0xE6576471), Color(0xC92A3540))
        )
        else -> Brush.verticalGradient(
            listOf(Color(0xBF485460), Color(0xA3222C36))
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .width(width)
            .height(buttonHeight)
            .shadow(
                elevation = if (focused) 10.dp else 5.dp,
                shape = shape,
                ambientColor = if (focused) Color(0x664EC0E4) else Color.Black,
                spotColor = if (focused) Color(0x554EC0E4) else Color.Black,
                clip = false
            )
            .clip(shape)
            .background(glassBrush, shape)
            .border(
                width = if (focused) TvDimensions.focusBorderWidth else 1.dp,
                color = when {
                    focused -> TvColors.FocusRing
                    emphasized -> Color(0x8AFFFFFF)
                    else -> Color(0x55FFFFFF)
                },
                shape = shape
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) DetailFocusLog.focusedSecondaryButton(label)
            }
            .focusable()
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = TvColors.TextPrimary,
            fontSize = if (buttonHeight <= 46.dp) 15.sp else 17.sp,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .height(1.dp)
                .background(Color(0x66FFFFFF))
        )
    }
}

@Composable
private fun DetailStatDisplayChip(
    text: String,
    chipHeight: Dp,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .wrapContentWidth()
            .height(chipHeight)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0x99434D59), Color(0x801A222C))
                ),
                shape
            )
            .border(1.dp, Color(0x44FFFFFF), shape)
            .padding(horizontal = 18.dp)
    ) {
        Text(
            text = text,
            color = TvColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Visible
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0x55FFFFFF))
        )
    }
}
