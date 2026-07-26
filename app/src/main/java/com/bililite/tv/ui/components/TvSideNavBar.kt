package com.bililite.tv.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.bililite.tv.theme.TvColors
import com.bililite.tv.theme.TvDimensions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TvSideNavBar(
    items: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    onRefreshSelected: (String) -> Unit,
    selectedItemFocusRequester: FocusRequester? = null,
    selectedItemRightFocusRequester: FocusRequester? = null,
    selectedItemOnRight: (() -> Unit)? = null,
    onFocusWithinChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var railFocused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var collapseJob by remember { mutableStateOf<Job?>(null) }
    fun onItemFocusChanged(focused: Boolean) {
        Log.i("BiliSideNav", "item focus=$focused expandedBefore=$railFocused")
        if (focused) {
            // Moving between two rail items produces a brief false -> true sequence.
            // Cancel the pending collapse so that internal rail navigation is not
            // mistaken for leaving the drawer.
            collapseJob?.cancel()
            collapseJob = null
            railFocused = true
            onFocusWithinChanged(true)
        } else {
            // Delay the "drawer lost focus" notification. If another rail item
            // receives focus during this window, its focused=true callback cancels
            // this job and focus remains inside the drawer.
            collapseJob?.cancel()
            collapseJob = scope.launch {
                delay(80)
                railFocused = false
                onFocusWithinChanged(false)
                Log.i("BiliSideNav", "rail collapsed")
            }
        }
    }
    Box(
        modifier = modifier
            .width(TvDimensions.sideRailCollapsedWidth)
            .fillMaxHeight()
            .zIndex(20f),
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .wrapContentSize(Alignment.CenterStart, unbounded = true)
                .requiredWidth(if (railFocused) TvDimensions.sideRailExpandedWidth else TvDimensions.sideRailCollapsedWidth)
                .background(TvColors.NavBarTrack, RoundedCornerShape(28.dp))
                .border(1.dp, TvColors.CardBorder, RoundedCornerShape(28.dp))
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            items.forEachIndexed { index, item ->
                SideNavItem(
                    text = item,
                    expanded = railFocused,
                    selected = item == selected,
                    itemFocusRequester = selectedItemFocusRequester.takeIf { item == selected },
                    rightFocusRequester = selectedItemRightFocusRequester.takeIf { item == selected },
                    onRight = selectedItemOnRight.takeIf { item == selected },
                    blockUp = index == 0,
                    blockDown = index == items.lastIndex,
                    onFocusChanged = ::onItemFocusChanged,
                    onClick = {
                        if (item != selected) onSelected(item)
                    }
                )
            }
        }
    }
}

@Composable
private fun SideNavItem(
    text: String,
    expanded: Boolean,
    selected: Boolean,
    itemFocusRequester: FocusRequester?,
    rightFocusRequester: FocusRequester?,
    onRight: (() -> Unit)?,
    blockUp: Boolean,
    blockDown: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val background = when {
        focused -> TvColors.SurfaceElevated
        selected -> TvColors.AccentSoft
        else -> Color.Transparent
    }
    // The outer Box is the focusable node, always 48dp wide. This ensures
    // moveFocus(Right) always finds content elements to the right (they
    // start at x≈100), regardless of whether the rail is visually expanded.
    // The inner Row overflows the Box via wrapContentSize(unbounded=true)
    // to draw the full 174dp background/border/text when expanded.
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(46.dp)
            .then(
                if (itemFocusRequester != null) Modifier.focusRequester(itemFocusRequester)
                else Modifier
            )
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp && blockUp) {
                    true
                } else if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown && blockDown) {
                    true
                } else if (
                    event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionRight
                ) {
                    // Only consume the key if spatial navigation actually
                    // moved focus. No explicit requester fallback — the
                    // fixed 48dp focus bounds guarantee content elements
                    // are always to the right.
                    focusManager.moveFocus(FocusDirection.Right)
                } else {
                    false
                }
            }
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .focusable()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .wrapContentSize(Alignment.CenterStart, unbounded = true)
                .width(if (expanded) 174.dp else 48.dp)
                .height(46.dp)
                .background(background, RoundedCornerShape(18.dp))
                .border(
                    if (focused) TvDimensions.focusBorderWidth else 0.dp,
                    if (focused) TvColors.FocusBorder else Color.Transparent,
                    RoundedCornerShape(18.dp)
                )
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text.first().toString(),
                color = when {
                    focused -> TvColors.FocusAccent
                    selected -> TvColors.Accent
                    else -> TvColors.TextSecondary
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
            if (expanded) {
                Text(
                    text = text,
                    color = if (focused) TvColors.TextPrimary else TvColors.TextSecondary,
                    fontSize = 16.sp,
                    fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }
}
