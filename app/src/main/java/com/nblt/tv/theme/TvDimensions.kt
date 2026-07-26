package com.nblt.tv.theme

import androidx.compose.ui.unit.dp

object TvDimensions {
    val pageHorizontal = 56.dp
    val pageVertical = 18.dp
    val navContentGap = 12.dp
    val sideRailCollapsedWidth = 64.dp
    val sideRailExpandedWidth = 190.dp
    val sideRailContentInset = 104.dp
    val gridColumns = 5
    val gridHorizontalSpacing = 12.dp
    val gridVerticalSpacing = 10.dp
    val gridBottomPadding = 16.dp
    val cardRadius = 12.dp
    val compactCardRadius = 10.dp
    val navBarHeight = 50.dp
    val navPillHorizontalPadding = 22.dp
    val navPillVerticalPadding = 10.dp
    val navPillSpacing = 8.dp
    // Large item scaling causes expensive redraws on several Android TV chipsets.
    val focusScale = 1f
    val focusBorderWidth = 3.dp
    val cardShadowFocused = 0.dp
    val cardShadowNormal = 0.dp

    val standardInfoPaddingTop = 4.dp
    val standardInfoPaddingBottom = 6.dp
    val standardTitleBlockHeight = 34.dp
    val standardSublineHeight = 18.dp
    val standardInfoAreaHeight =
        standardInfoPaddingTop + standardTitleBlockHeight + standardSublineHeight + standardInfoPaddingBottom
    val standardOwnerAvatarSize = 16.dp

    val compactInfoPaddingTop = 3.dp
    val compactInfoPaddingBottom = 5.dp
    val compactTitleBlockHeight = 26.dp
    val compactSublineHeight = 14.dp
    val compactInfoAreaHeight =
        compactInfoPaddingTop + compactTitleBlockHeight + compactSublineHeight + compactInfoPaddingBottom
    val compactGridHorizontalSpacing = 10.dp
    val compactGridVerticalSpacing = 8.dp
    val myPreviewColumns = 4

    @Deprecated("Use standardInfoAreaHeight", ReplaceWith("standardInfoAreaHeight"))
    val cardInfoAreaHeight = standardInfoAreaHeight

    @Deprecated("Use standardTitleBlockHeight", ReplaceWith("standardTitleBlockHeight"))
    val cardTitleBlockHeight = standardTitleBlockHeight

    @Deprecated("Use standardSublineHeight", ReplaceWith("standardSublineHeight"))
    val cardSublineHeight = standardSublineHeight

    @Deprecated("Removed; history merged into subline", ReplaceWith("standardSublineHeight"))
    val cardHistoryLineHeight = standardSublineHeight

    @Deprecated("Use standardOwnerAvatarSize", ReplaceWith("standardOwnerAvatarSize"))
    val cardOwnerAvatarSize = standardOwnerAvatarSize

    // === UI-R1 new tokens for CinematicSideRail ===

    val sideRailWidth = 88.dp
    val sideRailStartMargin = 20.dp
    val sideRailVerticalMargin = 16.dp
    val sideRailContentGap = 20.dp
    val sideRailItemWidth = 48.dp
    val sideRailItemHeight = 44.dp
    val sideRailIconSize = 24.dp

    // Combined inset from screen edge to content area start:
    // railStartMargin(20) + railWidth(88) + contentGap(20) = 128.dp
    val sideRailContentInsetNew = sideRailStartMargin + sideRailWidth + sideRailContentGap

    // Generic spacing tokens
    val screenPadding = 48.dp
    val sectionGap = 34.dp
    val cardGap = 16.dp
    val inlineGap = 8.dp
    val buttonHeight = 48.dp
    val buttonHeightLg = 56.dp
    val cardCoverHeight = 152.dp
    val cardHeroHeight = 240.dp
}
