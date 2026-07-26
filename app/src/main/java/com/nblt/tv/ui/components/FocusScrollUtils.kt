package com.nblt.tv.ui.components

import android.util.Log
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.MutableState
import com.nblt.tv.ui.home.logDynamicCrashGuard
import com.nblt.tv.ui.home.logDynamicScroll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val FOCUS_SCROLL_TAG = "BiliFocusScroll"
private const val FOCUS_SCROLL_VERBOSE_LOG = false

internal fun requestSafeGridScrollForFocus(
    gridState: LazyGridState,
    focusedIndex: Int,
    totalItems: Int,
    columns: Int,
    scrollScope: CoroutineScope,
    scrollJobState: MutableState<Job?>? = null,
    lastFocusedIndexState: MutableState<Int?>? = null,
    tabKey: String? = null,
    reason: String = "focus",
    screenName: String = tabKey ?: "grid",
    safeEdgePx: Int = 120
) {
    val previousFocusedIndex = lastFocusedIndexState?.value
    val safeColumns = columns.coerceAtLeast(1)
    val isSameRowMove = previousFocusedIndex != null &&
        previousFocusedIndex / safeColumns == focusedIndex / safeColumns &&
        previousFocusedIndex != focusedIndex
    val direction = when {
        previousFocusedIndex == null -> "UNKNOWN"
        isSameRowMove && focusedIndex < previousFocusedIndex -> "LEFT"
        isSameRowMove -> "RIGHT"
        focusedIndex / safeColumns < previousFocusedIndex / safeColumns -> "UP"
        focusedIndex / safeColumns > previousFocusedIndex / safeColumns -> "DOWN"
        else -> "SAME"
    }
    lastFocusedIndexState?.value = focusedIndex
    val lastVideoIndex = (totalItems - 1).coerceAtLeast(0)
    if (focusedIndex !in 0..lastVideoIndex || totalItems <= 0) {
        logDynamicCrashGuard(
            action = "skipScroll",
            detail = "invalid focus index=$focusedIndex totalItems=$totalItems"
        )
        return
    }

    // Horizontal D-pad movement must never adjust the vertical viewport. Focus
    // decoration can change a card's measured edge by a few pixels; treating a
    // same-row index change as UP/DOWN made those pixels repeatedly trigger the
    // safe-edge scroll correction and caused the whole screen to bounce.
    if (isSameRowMove) {
        scrollJobState?.value?.cancel()
        scrollJobState?.value = null
        logFocusScroll(
            screenName = screenName,
            direction = direction,
            focusedIndex = focusedIndex,
            firstVisibleItemIndex = gridState.firstVisibleItemIndex,
            lastVisibleItemIndex = gridState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: -1,
            columns = safeColumns,
            shouldScroll = false,
            targetIndex = null,
            reason = "sameRowHorizontalMove"
        )
        return
    }

    val visibleIndexes = gridState.layoutInfo.visibleItemsInfo
        .asSequence()
        .map { it.index }
        .filter { it in 0..lastVideoIndex }
        .toList()

    if (visibleIndexes.isEmpty()) {
        logFocusScroll(
            screenName = screenName,
            direction = direction,
            focusedIndex = focusedIndex,
            firstVisibleItemIndex = -1,
            lastVisibleItemIndex = -1,
            columns = safeColumns,
            shouldScroll = false,
            targetIndex = null,
            reason = "noVisibleItems"
        )
        return
    }

    val firstVisible = visibleIndexes.min()
    val lastVisible = visibleIndexes.max()
    val focusedItem = gridState.layoutInfo.visibleItemsInfo
        .firstOrNull { it.index == focusedIndex }
    val viewportStart = gridState.layoutInfo.viewportStartOffset
    val viewportEnd = gridState.layoutInfo.viewportEndOffset

    if (focusedIndex in visibleIndexes) {
        val nearTopEdge = focusedItem != null && focusedItem.offset.y < viewportStart + safeEdgePx
        val nearBottomEdge = focusedItem != null && focusedItem.offset.y + focusedItem.size.height > viewportEnd - safeEdgePx
        val shouldScrollTop = nearTopEdge && direction == "UP" && firstVisible > 0
        val shouldScrollBottom = nearBottomEdge && direction == "DOWN" && lastVisible < lastVideoIndex
        if (!shouldScrollTop && !shouldScrollBottom) {
            logFocusScroll(
                screenName = screenName,
                direction = direction,
                focusedIndex = focusedIndex,
                firstVisibleItemIndex = firstVisible,
                lastVisibleItemIndex = lastVisible,
                columns = safeColumns,
                shouldScroll = false,
                targetIndex = null,
                reason = if (nearTopEdge || nearBottomEdge) "edgeButDirectionIgnored" else "insideSafeArea"
            )
            return
        }
        val targetIndex = when {
            shouldScrollTop -> (focusedIndex - safeColumns).coerceIn(0, lastVideoIndex)
            shouldScrollBottom -> (focusedIndex - safeColumns).coerceIn(0, lastVideoIndex)
            else -> focusedIndex
        }
        logFocusScroll(
            screenName = screenName,
            direction = direction,
            focusedIndex = focusedIndex,
            firstVisibleItemIndex = firstVisible,
            lastVisibleItemIndex = lastVisible,
            columns = safeColumns,
            shouldScroll = targetIndex != firstVisible,
            targetIndex = targetIndex,
            reason = if (shouldScrollBottom) "nearBottomSafeEdge" else "nearTopSafeEdge"
        )
        if (targetIndex != firstVisible) {
            launchDebouncedGridScroll(
                gridState = gridState,
                scrollScope = scrollScope,
                scrollJobState = scrollJobState,
                targetIndex = targetIndex,
                focusedIndex = focusedIndex,
                totalItems = totalItems,
                screenName = screenName,
                direction = direction
            )
        }
        return
    }

    val nearBottom = focusedIndex >= lastVisible - safeColumns + 1 && lastVisible < lastVideoIndex
    val nearTop = focusedIndex <= firstVisible + safeColumns - 1 && firstVisible > 0

    if (!nearBottom && !nearTop) {
        logFocusScroll(
            screenName = screenName,
            direction = direction,
            focusedIndex = focusedIndex,
            firstVisibleItemIndex = firstVisible,
            lastVisibleItemIndex = lastVisible,
            columns = safeColumns,
            shouldScroll = false,
            targetIndex = null,
            reason = "notNearBoundary"
        )
        return
    }

    val targetIndex = when {
        nearTop || direction == "UP" || focusedIndex < firstVisible -> (focusedIndex - safeColumns).coerceIn(0, lastVideoIndex)
        else -> (focusedIndex - safeColumns).coerceIn(0, lastVideoIndex)
    }

    logFocusScroll(
        screenName = screenName,
        direction = direction,
        focusedIndex = focusedIndex,
        firstVisibleItemIndex = firstVisible,
        lastVisibleItemIndex = lastVisible,
        columns = safeColumns,
        shouldScroll = true,
        targetIndex = targetIndex,
        reason = if (nearTop || direction == "UP" || focusedIndex < firstVisible) "nearTop" else "nearBottom"
    )
    logDynamicScroll(
        tabKey = tabKey,
        focusedIndex = focusedIndex,
        targetIndex = targetIndex,
        videoCount = totalItems,
        reason = reason
    )

    launchDebouncedGridScroll(
        gridState = gridState,
        scrollScope = scrollScope,
        scrollJobState = scrollJobState,
        targetIndex = targetIndex,
        focusedIndex = focusedIndex,
        totalItems = totalItems,
        screenName = screenName,
        direction = direction
    )
}

internal fun restoreGridScrollTarget(
    focusedIndex: Int,
    columns: Int,
    totalItems: Int
): Int {
    val safeColumns = columns.coerceAtLeast(1)
    val lastIndex = (totalItems - 1).coerceAtLeast(0)
    return (focusedIndex - safeColumns).coerceIn(0, lastIndex)
}

internal fun logFocusRestoreScroll(
    focusedIndex: Int,
    targetIndex: Int,
    columns: Int
) {
    logFocusScroll(
        screenName = "restore",
        direction = "RESTORE",
        focusedIndex = focusedIndex,
        firstVisibleItemIndex = -1,
        lastVisibleItemIndex = -1,
        columns = columns,
        shouldScroll = true,
        targetIndex = targetIndex,
        reason = "restoreFocus"
    )
}

private fun launchDebouncedGridScroll(
    gridState: LazyGridState,
    scrollScope: CoroutineScope,
    scrollJobState: MutableState<Job?>?,
    targetIndex: Int,
    focusedIndex: Int,
    totalItems: Int,
    screenName: String = "grid",
    direction: String = "UNKNOWN"
) {
    val cancelledPreviousJob = scrollJobState?.value?.isActive == true
    scrollJobState?.value?.cancel()
    Log.d(
        FOCUS_SCROLL_TAG,
        "screen=$screenName, direction=$direction, focusedIndex=$focusedIndex, " +
            "needScroll=true, targetIndex=$targetIndex, reason=debouncedAnimate, " +
            "cancelledPreviousJob=$cancelledPreviousJob"
    )
    val job = scrollScope.launch {
        delay(35)
        runCatching {
            gridState.animateScrollToItem(targetIndex)
        }.onFailure { error ->
            logDynamicCrashGuard(
                action = "animateScrollToItem",
                detail = "targetIndex=$targetIndex focusedIndex=$focusedIndex totalItems=$totalItems",
                throwable = error
            )
        }
    }
    scrollJobState?.value = job
}

private fun logFocusScroll(
    screenName: String,
    direction: String,
    focusedIndex: Int,
    firstVisibleItemIndex: Int,
    lastVisibleItemIndex: Int,
    columns: Int,
    shouldScroll: Boolean,
    targetIndex: Int?,
    reason: String
) {
    if (!shouldScroll && !FOCUS_SCROLL_VERBOSE_LOG) {
        return
    }
    Log.d(
        FOCUS_SCROLL_TAG,
        "screen=$screenName, direction=$direction, focusedIndex=$focusedIndex, " +
            "firstVisibleIndex=$firstVisibleItemIndex, lastVisibleIndex=$lastVisibleItemIndex, " +
            "visibleRange=$firstVisibleItemIndex..$lastVisibleItemIndex, columns=$columns, " +
            "needScroll=$shouldScroll, targetIndex=${targetIndex ?: -1}, reason=$reason, " +
            "cancelledPreviousJob=false"
    )
}
