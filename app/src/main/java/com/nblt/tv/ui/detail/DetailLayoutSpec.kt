package com.nblt.tv.ui.detail

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val TAG_DETAIL_LAYOUT = "BiliDetailLayout"
private const val COMPACT_HEIGHT_THRESHOLD_DP = 720

@Immutable
internal data class DetailLayoutSpec(
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val fontScale: Float,
    val isCompactHeight: Boolean,
    val titleMaxLines: Int,
    val descMaxLines: Int,
    val titleFontSizeSp: Int,
    val titleLineHeightSp: Int,
    val descFontSizeSp: Int,
    val coverMaxWidth: Dp,
    val coverMaxHeight: Dp,
    val topSafePadding: Dp,
    val bottomSafePadding: Dp,
    val heroHeight: Dp,
    val actionHeight: Dp,
    val lowerSectionHeight: Dp,
    val partRowHeight: Dp,
    val playButtonHeight: Dp,
    val playButtonWidth: Dp,
    val secondaryButtonHeight: Dp,
    val layoutMode: String,
    val outerScrollEnabled: Boolean
)

@Composable
internal fun rememberDetailLayoutSpec(
    hasParts: Boolean = false,
    hasRelated: Boolean = false
): DetailLayoutSpec {
    val configuration = LocalConfiguration.current
    val fontScale = LocalDensity.current.fontScale
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val isCompactHeight = screenHeightDp < COMPACT_HEIGHT_THRESHOLD_DP || fontScale > 1.15f

    return remember(screenWidthDp, screenHeightDp, fontScale, hasParts, hasRelated) {
        buildDetailLayoutSpec(screenWidthDp, screenHeightDp, fontScale, isCompactHeight, hasParts, hasRelated)
    }
}

private fun buildDetailLayoutSpec(
    screenWidthDp: Int,
    screenHeightDp: Int,
    fontScale: Float,
    isCompactHeight: Boolean,
    hasParts: Boolean,
    hasRelated: Boolean
): DetailLayoutSpec {
    val topPaddingDp = 36
    val bottomPaddingDp = 24
    val actionHeightDp = if (isCompactHeight) 64 else 76
    val dividerHeightDp = 14
    val availableDp = screenHeightDp - topPaddingDp - bottomPaddingDp
    val hasLowerContent = hasParts || hasRelated

    val minHeroDp = if (isCompactHeight) 152 else 196
    val minLowerDp = if (isCompactHeight) 96 else 128

    var lowerSectionHeightDp = if (!hasLowerContent) {
        0
    } else {
        val fraction = when {
            hasParts && hasRelated -> if (isCompactHeight) 0.42f else 0.46f
            hasParts -> if (isCompactHeight) 0.34f else 0.38f
            else -> if (isCompactHeight) 0.34f else 0.38f
        }
        (availableDp * fraction).toInt().coerceIn(minLowerDp, if (isCompactHeight) 210 else 280)
    }

    var heroHeightDp = availableDp - actionHeightDp - dividerHeightDp - lowerSectionHeightDp
    if (heroHeightDp < minHeroDp && hasLowerContent) {
        lowerSectionHeightDp = (availableDp - actionHeightDp - dividerHeightDp - minHeroDp)
            .coerceAtLeast(minLowerDp)
        heroHeightDp = availableDp - actionHeightDp - dividerHeightDp - lowerSectionHeightDp
    } else if (!hasLowerContent) {
        heroHeightDp = availableDp - actionHeightDp
    }

    val partRowHeightDp = when {
        !hasParts -> 0
        isCompactHeight && hasRelated -> 118
        isCompactHeight -> 132
        hasRelated -> 168
        else -> 188
    }

    val coverMaxWidth = if (isCompactHeight) 250.dp else 360.dp
    val coverMaxHeight = (heroHeightDp * 0.92f).dp

    return if (isCompactHeight) {
        DetailLayoutSpec(
            screenWidthDp = screenWidthDp,
            screenHeightDp = screenHeightDp,
            fontScale = fontScale,
            isCompactHeight = true,
            titleMaxLines = 2,
            descMaxLines = 1,
            titleFontSizeSp = 26,
            titleLineHeightSp = 32,
            descFontSizeSp = 14,
            coverMaxWidth = coverMaxWidth,
            coverMaxHeight = coverMaxHeight,
            topSafePadding = topPaddingDp.dp,
            bottomSafePadding = bottomPaddingDp.dp,
            heroHeight = heroHeightDp.dp,
            actionHeight = actionHeightDp.dp,
            lowerSectionHeight = lowerSectionHeightDp.dp,
            partRowHeight = partRowHeightDp.dp,
            playButtonHeight = 50.dp,
            playButtonWidth = 168.dp,
            secondaryButtonHeight = 46.dp,
            layoutMode = "fixed",
            outerScrollEnabled = false
        )
    } else {
        DetailLayoutSpec(
            screenWidthDp = screenWidthDp,
            screenHeightDp = screenHeightDp,
            fontScale = fontScale,
            isCompactHeight = false,
            titleMaxLines = 2,
            descMaxLines = 2,
            titleFontSizeSp = 36,
            titleLineHeightSp = 42,
            descFontSizeSp = 15,
            coverMaxWidth = coverMaxWidth,
            coverMaxHeight = coverMaxHeight,
            topSafePadding = topPaddingDp.dp,
            bottomSafePadding = bottomPaddingDp.dp,
            heroHeight = heroHeightDp.dp,
            actionHeight = actionHeightDp.dp,
            lowerSectionHeight = lowerSectionHeightDp.dp,
            partRowHeight = partRowHeightDp.dp,
            playButtonHeight = 58.dp,
            playButtonWidth = 190.dp,
            secondaryButtonHeight = 54.dp,
            layoutMode = "fixed",
            outerScrollEnabled = false
        )
    }
}

internal fun logDetailLayout(spec: DetailLayoutSpec) {
    Log.i(
        TAG_DETAIL_LAYOUT,
        "screenWidthDp=${spec.screenWidthDp}, screenHeightDp=${spec.screenHeightDp}, " +
            "compactDetail=${spec.isCompactHeight}, layoutMode=${spec.layoutMode}, " +
            "outerScrollEnabled=${spec.outerScrollEnabled}, titleMaxLines=${spec.titleMaxLines}, " +
            "descMaxLines=${spec.descMaxLines}, heroHeight=${spec.heroHeight.value}dp, " +
            "lowerSectionHeight=${spec.lowerSectionHeight.value}dp, " +
            "actionHeight=${spec.actionHeight.value}dp, partRowHeight=${spec.partRowHeight.value}dp"
    )
}
