package com.nblt.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nblt.tv.model.VideoItem
import com.nblt.tv.theme.NbltPrimary
import com.nblt.tv.theme.TvColors
import com.nblt.tv.theme.TvDimensions
import com.nblt.tv.util.FormatUtils
import com.nblt.tv.util.BilibiliImageUrl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class VideoCardVariant {
    Standard,
    Compact
}

@Composable
fun VideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    rank: Int? = null,
    leftFocusRequester: FocusRequester? = null,
    onOwnerClick: ((Long, String) -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    blockDownFocus: Boolean = false,
    onVerticalFocusMove: ((Int) -> Boolean)? = null,
    onCardFocusChanged: ((Boolean) -> Unit)? = null,
    style: VideoCardStyle = VideoCardStyle.Standard,
    showProgress: Boolean = false
) {
    StandardVideoCard(
        video = video,
        onClick = onClick,
        rank = rank,
        leftFocusRequester = leftFocusRequester,
        onOwnerClick = onOwnerClick,
        focusRequester = focusRequester,
        blockDownFocus = blockDownFocus,
        onVerticalFocusMove = onVerticalFocusMove,
        onCardFocusChanged = onCardFocusChanged,
        style = style,
        showProgress = showProgress
    )
}

@Composable
fun StandardVideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    rank: Int? = null,
    leftFocusRequester: FocusRequester? = null,
    onOwnerClick: ((Long, String) -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    blockDownFocus: Boolean = false,
    onVerticalFocusMove: ((Int) -> Boolean)? = null,
    onCardFocusChanged: ((Boolean) -> Unit)? = null,
    style: VideoCardStyle = VideoCardStyle.Standard,
    showProgress: Boolean = false
) {
    VideoCardLayout(
        variant = VideoCardVariant.Standard,
        video = video,
        onClick = onClick,
        rank = rank,
        leftFocusRequester = leftFocusRequester,
        onOwnerClick = onOwnerClick,
        focusRequester = focusRequester,
        blockDownFocus = blockDownFocus,
        onVerticalFocusMove = onVerticalFocusMove,
        onCardFocusChanged = onCardFocusChanged,
        style = style,
        showProgress = showProgress
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun VideoCardLayout(
    variant: VideoCardVariant,
    video: VideoItem,
    onClick: () -> Unit,
    rank: Int? = null,
    leftFocusRequester: FocusRequester? = null,
    onOwnerClick: ((Long, String) -> Unit)? = null,
    compactSubline: String? = null,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    blockDownFocus: Boolean = false,
    onVerticalFocusMove: ((Int) -> Boolean)? = null,
    onCardFocusChanged: ((Boolean) -> Unit)? = null,
    style: VideoCardStyle = VideoCardStyle.Standard,
    showProgress: Boolean = false
) {
    var cardFocused by remember { mutableStateOf(false) }
    var ownerFocused by remember { mutableStateOf(false) }
    val focused = cardFocused || ownerFocused
    val showOwnerClick = onOwnerClick != null && video.ownerMid > 0L
    val isCompact = variant == VideoCardVariant.Compact
    val infoHeight = if (isCompact) {
        TvDimensions.compactInfoAreaHeight
    } else {
        TvDimensions.standardInfoAreaHeight
    }
    val badgeFontSize = if (isCompact) 10.sp else 11.sp
    val coverRadius = if (isCompact) {
        TvDimensions.compactCardRadius
    } else {
        TvDimensions.cardRadius
    }

    Column(
        modifier = when (style) {
            VideoCardStyle.Cinematic -> modifier.cinematicVideoCardShell(focused)
            else -> modifier.tvVideoCardShell(focused = focused, compact = isCompact)
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(
                    RoundedCornerShape(
                        topStart = coverRadius,
                        topEnd = coverRadius,
                        bottomStart = 0.dp,
                        bottomEnd = 0.dp
                    )
                )
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .then(
                    if (leftFocusRequester != null || blockDownFocus) {
                        Modifier.focusProperties {
                            leftFocusRequester?.let { left = it }
                            if (blockDownFocus) down = FocusRequester.Cancel
                        }
                    } else Modifier
                )
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        when (event.key) {
                            Key.DirectionUp -> onVerticalFocusMove?.invoke(-1) ?: false
                            Key.DirectionDown -> {
                                if (blockDownFocus && onVerticalFocusMove == null) {
                                    true
                                } else {
                                    onVerticalFocusMove?.invoke(1) ?: blockDownFocus
                                }
                            }
                            else -> false
                        }
                    }
                }
                .onFocusChanged {
                    if (cardFocused != it.isFocused) {
                        cardFocused = it.isFocused
                        onCardFocusChanged?.invoke(it.isFocused)
                    }
                }
                .focusable()
                .clickable(onClick = onClick)
        ) {
            CoverPlaceholder(
                video = video,
                modifier = Modifier.fillMaxSize(),
                showDuration = true,
                showStatsOverlay = true,
                badgeFontSize = badgeFontSize
            )
            if (rank != null) {
                Text(
                    text = rank.toString().padStart(2, '0'),
                    color = if (rank <= 3) TvColors.FocusAccent else TvColors.TextPrimary,
                    fontSize = if (rank <= 3) 25.sp else 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(7.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xD9000000))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        if (
            showProgress &&
            video.duration > 0L &&
            video.historyProgress > 0L
        ) {
            val progress = (
                video.historyProgress.toFloat() /
                    video.duration.toFloat()
            ).coerceIn(0f, 1f)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(TvColors.ProgressTrack)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(4.dp)
                        .background(TvColors.ProgressFill)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(infoHeight)
                .padding(
                    start = if (isCompact) 4.dp else 6.dp,
                    end = if (isCompact) 4.dp else 6.dp,
                    top = if (isCompact) {
                        TvDimensions.compactInfoPaddingTop
                    } else {
                        TvDimensions.standardInfoPaddingTop
                    },
                    bottom = if (isCompact) {
                        TvDimensions.compactInfoPaddingBottom
                    } else {
                        TvDimensions.standardInfoPaddingBottom
                    }
                )
        ) {
                if (isCompact) {
                    CompactCardInfo(
                        video = video,
                        sublineOverride = compactSubline
                    )
                } else {
                    StandardCardInfo(
                        video = video,
                        showOwnerClick = showOwnerClick,
                        onOwnerClick = onOwnerClick,
                        onOwnerFocusChanged = { ownerFocused = it }
                    )
                }
        }
    }
}

@Composable
private fun StandardCardInfo(
    video: VideoItem,
    showOwnerClick: Boolean,
    onOwnerClick: ((Long, String) -> Unit)?,
    onOwnerFocusChanged: (Boolean) -> Unit
) {
    Text(
        text = video.title.ifBlank { "\u65e0\u6807\u9898" },
        color = TvColors.TextPrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        minLines = 2,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        lineHeight = 17.sp,
        modifier = Modifier
            .fillMaxWidth()
            .height(TvDimensions.standardTitleBlockHeight)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TvDimensions.standardSublineHeight),
        contentAlignment = Alignment.CenterStart
    ) {
        val historyText = buildHistorySubline(video)
        if (historyText.isNotBlank()) {
            Text(
                text = historyText,
                color = TvColors.TextMuted,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            StandardCardSubline(
                video = video,
                showOwnerClick = showOwnerClick,
                onOwnerClick = onOwnerClick,
                onOwnerFocusChanged = onOwnerFocusChanged
            )
        }
    }
}

@Composable
private fun CompactCardInfo(
    video: VideoItem,
    sublineOverride: String?
) {
    Text(
        text = video.title.ifBlank { "\u65e0\u6807\u9898" },
        color = TvColors.TextPrimary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        minLines = 1,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        lineHeight = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .height(TvDimensions.compactTitleBlockHeight)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TvDimensions.compactSublineHeight),
        contentAlignment = Alignment.CenterStart
    ) {
        val text = sublineOverride?.takeIf { it.isNotBlank() }
            ?: buildCompactSubline(video)
        if (text.isNotBlank()) {
            Text(
                text = text,
                color = TvColors.TextMuted,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StandardCardSubline(
    video: VideoItem,
    showOwnerClick: Boolean,
    onOwnerClick: ((Long, String) -> Unit)?,
    onOwnerFocusChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val avatarModel = remember(video.ownerFaceUrl) {
        if (video.ownerFaceUrl.isBlank()) {
            null
        } else {
            ImageRequest.Builder(context)
                .data(BilibiliImageUrl.avatar(video.ownerFaceUrl, size = 64))
                .crossfade(false)
                .size(64, 64)
                .build()
        }
    }
    val pubDateText = formatPubDate(video.pubdate)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (showOwnerClick) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(4.dp))
                    .onFocusChanged { onOwnerFocusChanged(it.isFocused) }
                    .focusable()
                    .clickable {
                        onOwnerClick?.invoke(video.ownerMid, video.ownerName)
                    }
                    .padding(end = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(TvDimensions.standardOwnerAvatarSize)
                        .clip(CircleShape)
                        .background(NbltPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarModel != null) {
                        AsyncImage(
                            model = avatarModel,
                            contentDescription = video.ownerName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                Text(
                    text = video.ownerName.ifBlank { "\u672a\u77e5UP\u4e3b" }.firstOrNull()?.toString().orEmpty(),
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = video.ownerName.ifBlank { "\u672a\u77e5UP\u4e3b" },
                    color = TvColors.TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 5.dp)
                )
            }
        } else if (video.ownerName.isNotBlank()) {
            Text(
                text = video.ownerName.ifBlank { "\u672a\u77e5UP\u4e3b" },
                color = TvColors.TextSecondary,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }

        if (pubDateText.isNotBlank() && video.ownerName.isNotBlank()) {
            Text(
                text = "  \u00b7  $pubDateText",
                color = TvColors.TextMuted,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else if (pubDateText.isNotBlank()) {
            Text(
                text = pubDateText,
                color = TvColors.TextMuted,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun buildHistorySubline(video: VideoItem): String {
    val viewAt = FormatUtils.formatHistoryTime(video.historyViewAt)
    val progress = FormatUtils.formatProgressWithDuration(video.historyProgress, video.duration)
    return listOf(viewAt, progress)
        .filter { it.isNotBlank() }
        .joinToString("  \u00b7  ")
}

private fun buildCompactSubline(video: VideoItem): String {
    val history = buildHistorySubline(video)
    if (history.isNotBlank()) {
        return history
    }
    return video.ownerName.ifBlank { "\u672a\u77e5UP\u4e3b" }
}

@Composable
fun CoverPlaceholder(
    video: VideoItem,
    modifier: Modifier = Modifier,
    showDuration: Boolean = true,
    showStatsOverlay: Boolean = false,
    badgeFontSize: androidx.compose.ui.unit.TextUnit = 11.sp
) {
    val context = LocalContext.current
    val coverModel = remember(video.coverUrl) {
        if (video.coverUrl.isBlank()) {
            null
        } else {
            ImageRequest.Builder(context)
                .data(BilibiliImageUrl.cover(video.coverUrl, width = 480, height = 270))
                .crossfade(false)
                .size(480, 270)
                .build()
        }
    }
    val statsText = remember(
        video.playCount,
        video.danmakuCount,
        video.historyProgress,
        video.views
    ) {
        buildList {
            if (video.playCount > 0L) add("▶ ${video.views}")
            video.danmakuCount?.takeIf { it > 0L }?.let {
                add("▣ ${FormatUtils.formatPlayCount(it)}")
            }
            if (isEmpty() && video.historyProgress > 0L) {
                add("已看 ${FormatUtils.formatDuration(video.historyProgress)}")
            }
        }.joinToString("   ")
    }
    Box(
        modifier = modifier.background(Color(0xFF20242C))
    ) {
        if (coverModel != null) {
            AsyncImage(
                model = coverModel,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (showStatsOverlay && statsText.isNotBlank()) {
            CoverCornerBadge(
                text = statsText,
                fontSize = badgeFontSize,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(7.dp)
            )
        }

        if (showDuration && video.duration > 0L) {
            CoverCornerBadge(
                text = video.durationText,
                fontSize = badgeFontSize,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(7.dp)
            )
        }
    }
}

@Composable
internal fun CoverCornerBadge(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 11.sp
) {
    Text(
        text = text,
        color = Color.White,
        fontSize = fontSize,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0xB3000000))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

fun Modifier.tvVideoCardShell(
    focused: Boolean,
    compact: Boolean = false
): Modifier {
    val radius = if (compact) TvDimensions.compactCardRadius else TvDimensions.cardRadius
    val shape = RoundedCornerShape(radius)
    return this
        .background(
            color = when {
                focused -> TvColors.SurfaceElevated
                compact -> Color.Transparent
                else -> TvColors.SurfaceSoft
            },
            shape = shape
        )
        .border(
            width = if (focused) TvDimensions.focusBorderWidth else 1.dp,
            color = if (focused) TvColors.FocusBorder else TvColors.CardBorder,
            shape = shape
        )
}

private fun Modifier.cinematicVideoCardShell(focused: Boolean): Modifier {
    val shape = RoundedCornerShape(TvDimensions.cardRadius)
    return this
        .background(
            color = if (focused) TvColors.SurfaceElevated else TvColors.SurfaceSoft,
            shape = shape
        )
        .border(
            width = if (focused) 2.dp else 1.dp,
            color = if (focused) TvColors.FocusRing else TvColors.CardBorder,
            shape = shape
        )
}

private val pubDateFormatter = ThreadLocal.withInitial {
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
}

internal fun formatPubDate(timestampSeconds: Long): String {
    if (timestampSeconds <= 0L) {
        return ""
    }
    return pubDateFormatter.get().format(Date(timestampSeconds * 1000))
}
