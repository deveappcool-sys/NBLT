package com.nblt.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nblt.tv.theme.TvColors
import com.nblt.tv.theme.TvDimensions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Unified cinematic nav icon set (ImageVector), keyed by the HomeNavTabs labels. */
private val NAV_ICONS: Map<String, ImageVector> = mapOf(
    "\u9996\u9875" to CinematicNavIcons.HOME,         // 首页
    "\u63a8\u8350" to CinematicNavIcons.RECOMMEND,     // 推荐
    "\u70ed\u95e8" to CinematicNavIcons.POPULAR,       // 热门
    "\u76f4\u64ad" to CinematicNavIcons.LIVE,          // 直播
    "\u641c\u7d22" to CinematicNavIcons.SEARCH,        // 搜索
    "\u52a8\u6001" to CinematicNavIcons.DYNAMIC,       // 动态
    "\u5386\u53f2" to CinematicNavIcons.HISTORY,       // 历史
    "\u6211\u7684" to CinematicNavIcons.MY,            // 我的
    "\u8bbe\u7f6e" to CinematicNavIcons.SETTINGS       // 设置
)

/** Fixed icon size — identical for selected and unselected (no scale on focus). */
private val RAIL_ICON_SIZE = 28.dp

private class RailFocusTracker {
    var focused: Boolean = false
    var lossJob: Job? = null
}

/**
 * Fixed-width cinematic side rail. Never expands or collapses.
 *
 * Preserves the focus state machine contract from [TvSideNavBar]:
 * - [items] list of 9 navigation labels
 * - [selected] currently active page (gold tint)
 * - [onSelected] page switch callback
 * - [onRefreshSelected] re-tap current page callback
 * - [selectedItemFocusRequester] focus requester for the currently selected item
 * - [selectedItemRightFocusRequester] optional right-side content requester
 * - [selectedItemOnRight] fallback when right-key spatial move fails
 * - [preferSelectedItemRightFocusRequester] when true AND the focused item is the
 *   selected page, Right uses [selectedItemRightFocusRequester] FIRST (before
 *   spatial navigation). Defaults to false so all existing pages keep the original
 *   spatial-first behavior; only SETTINGS opts into explicit-requester-first.
 * - [onFocusWithinChanged] notified when rail gains/loses focus (with debounce)
 * - [disabledItems] set of item labels that should be non-focusable and dimmed
 */
@Composable
fun CinematicSideRail(
    items: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    onRefreshSelected: (String) -> Unit,
    selectedItemFocusRequester: FocusRequester? = null,
    selectedItemRightFocusRequester: FocusRequester? = null,
    selectedItemOnRight: (() -> Unit)? = null,
    preferSelectedItemRightFocusRequester: Boolean = false,
    itemFocusRequesters: Map<String, FocusRequester> = emptyMap(),
    onItemPositioned: (String, Float) -> Unit = { _, _ -> },
    onItemRight: ((String) -> Boolean)? = null,
    preferItemRightHandler: Boolean = false,
    onFocusWithinChanged: (Boolean) -> Unit = {},
    disabledItems: Set<String> = emptySet(),
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val focusTracker = remember { RailFocusTracker() }
    val enabledItems = remember(items, disabledItems) {
        items.filter { it !in disabledItems }
    }

    fun onItemFocusChanged(focused: Boolean) {
        if (focused) {
            focusTracker.lossJob?.cancel()
            focusTracker.lossJob = null
            if (!focusTracker.focused) {
                focusTracker.focused = true
                onFocusWithinChanged(true)
            }
        } else {
            focusTracker.lossJob?.cancel()
            focusTracker.lossJob = scope.launch {
                delay(60)
                if (focusTracker.focused) {
                    focusTracker.focused = false
                    onFocusWithinChanged(false)
                }
            }
        }
    }

    val railShape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .width(TvDimensions.sideRailWidth)
            .fillMaxHeight()
            .zIndex(20f)
            .background(TvColors.Glass.BodyRail.copy(alpha = 0.72f), railShape)
            .border(1.dp, TvColors.CardBorder.copy(alpha = 0.42f), railShape)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.alpha(0.58f)) {
                NbltWordmark()
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .width(TvDimensions.sideRailWidth),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items.forEachIndexed { index, item ->
                    val isDisabled = item in disabledItems
                    val isSelected = item == selected
                    val enabledIndex = enabledItems.indexOf(item)

                    val isFirstEnabled = !isDisabled && enabledIndex == 0
                    val isLastEnabled = !isDisabled && enabledIndex == enabledItems.lastIndex

                    SideRailItem(
                        icon = NAV_ICONS.getValue(item),
                        contentDescription = item,
                        selected = isSelected,
                        disabled = isDisabled,
                        itemFocusRequester = itemFocusRequesters[item]
                            ?: selectedItemFocusRequester?.takeIf { item == selected },
                        // Right always exits the rail into the currently displayed page.
                        // The focused rail item does not have to be the selected page item.
                        // OK/Enter still performs page selection; Right only leaves the rail.
                        rightFocusRequester = selectedItemRightFocusRequester,
                        preferRightFocusRequester = preferSelectedItemRightFocusRequester && isSelected,
                        onRight = selectedItemOnRight,
                        onItemRight = onItemRight?.let { handler -> { handler(item) } },
                        preferItemRightHandler = preferItemRightHandler,
                        onPositioned = { centerY -> onItemPositioned(item, centerY) },
                        blockUp = isFirstEnabled,
                        blockDown = isLastEnabled,
                        onFocusChanged = if (isDisabled) ({ _ -> }) else ::onItemFocusChanged,
                        onClick = {
                            if (!isDisabled) {
                                if (item != selected) {
                                    onSelected(item)
                                }
                                onRefreshSelected(item)
                            }
                        }
                    )
                }
            }
        }
    }
}



private fun requestFocusSafely(requester: FocusRequester?): Boolean {
    if (requester == null) return false
    return runCatching {
        requester.requestFocus()
        true
    }.getOrDefault(false)
}

@Composable
private fun SideRailItem(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    disabled: Boolean,
    itemFocusRequester: FocusRequester?,
    rightFocusRequester: FocusRequester?,
    preferRightFocusRequester: Boolean = false,
    onRight: (() -> Unit)?,
    onItemRight: (() -> Boolean)?,
    preferItemRightHandler: Boolean,
    onPositioned: (Float) -> Unit,
    blockUp: Boolean,
    blockDown: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val isFocused = focused && !disabled
    val focusManager = LocalFocusManager.current

    // Icon tint: cold-blue state language. normal=TextSecondary 0.72,
    // selected/focused/selected+focused all use TextPrimary, disabled=TextMuted 0.55.
    val iconTint = when {
        disabled -> TvColors.TextMuted.copy(alpha = 0.55f)
        selected || focused -> TvColors.TextPrimary
        else -> TvColors.TextSecondary.copy(alpha = 0.72f)
    }

    // Full-width row (matches the rail width) so the gold selected indicator can
    // sit at the rail's left edge. The focusable node itself stays the frozen
    // 48x44 box, centered — so Up/Down geometry and item spacing are untouched.
    Box(
        modifier = Modifier
            .width(TvDimensions.sideRailWidth)
            .height(TvDimensions.sideRailItemHeight)
    ) {
        // Left cold-blue selected indicator — follows SELECTED only, non-focusable,
        // non-clickable, 3×28.dp rounded bar. Visible when selected (with or without focus).
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .width(3.dp)
                    .height(28.dp)
                    .background(TvColors.FocusRing.copy(alpha = 0.90f), RoundedCornerShape(1.5.dp))
            )
        }

        if (selected || isFocused) {
            val stateShape = RoundedCornerShape(14.dp)
            val stateColor = when {
                isFocused && selected -> TvColors.SurfaceElevated.copy(alpha = 0.96f)
                isFocused -> TvColors.SurfaceGlass.copy(alpha = 0.90f)
                else -> TvColors.SurfaceSoft.copy(alpha = 0.88f)
            }
            val edgeColor = if (isFocused) {
                TvColors.FocusRing
            } else {
                TvColors.CardBorder.copy(alpha = 0.70f)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(
                        width = TvDimensions.sideRailItemWidth,
                        height = TvDimensions.sideRailItemHeight
                    )
                    .background(stateColor, stateShape)
                    .border(if (isFocused) 2.dp else 1.dp, edgeColor, stateShape)
            ) { }
        }

        // Focusable node — unchanged focus / key wiring. The per-state cold-blue
        // glass card (above) provides the visual background; this node only carries
        // focus geometry (48×44), key events, and the icon.
        // Aligned to Center so it shares the same horizontal center as the state
        // glass card — both are 48×44 children of the 88×44 outer Box.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(TvDimensions.sideRailItemWidth)
                .height(TvDimensions.sideRailItemHeight)
                .onGloballyPositioned { coordinates ->
                    onPositioned(coordinates.boundsInRoot().center.y)
                }
                .then(
                    if (!disabled && itemFocusRequester != null) {
                        Modifier.focusRequester(itemFocusRequester)
                    } else Modifier
                )
                .then(
                    if (!disabled) {
                        Modifier.onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) {
                                false
                            } else {
                                when (event.key) {
                                    Key.DirectionUp -> {
                                        if (blockUp) true
                                        else focusManager.moveFocus(FocusDirection.Up)
                                    }
                                    Key.DirectionDown -> {
                                        if (blockDown) true
                                        else focusManager.moveFocus(FocusDirection.Down)
                                    }
                                    Key.DirectionLeft -> true
                                    Key.DirectionRight -> {
                                        // HOME uses a measured height bridge. It must run before
                                        // Compose spatial navigation so every rail item enters the
                                        // nearest visible home row deterministically.
                                        if (preferItemRightHandler && onItemRight?.invoke() == true) {
                                            true
                                        } else if (preferRightFocusRequester &&
                                            rightFocusRequester != null &&
                                            requestFocusSafely(rightFocusRequester)
                                        ) {
                                            true
                                        } else {
                                            val moved = focusManager.moveFocus(FocusDirection.Right)
                                            if (moved) {
                                                true
                                            } else if (onItemRight?.invoke() == true) {
                                                true
                                            } else {
                                                val requested = requestFocusSafely(rightFocusRequester)
                                                if (requested) {
                                                    true
                                                } else if (onRight != null) {
                                                    onRight()
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                        }
                                    }
                                    else -> false
                                }
                            }
                        }
                    } else Modifier
                )
                .then(
                    if (!disabled) {
                        Modifier.onFocusChanged {
                            if (focused != it.isFocused) {
                                focused = it.isFocused
                            }
                            onFocusChanged(it.isFocused)
                        }
                    } else Modifier
                )
                .then(
                    if (!disabled) Modifier.focusable() else Modifier
                )
                .then(
                    if (!disabled) Modifier.clickable(onClick = onClick) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(RAIL_ICON_SIZE)
            )
        }
    }
}
