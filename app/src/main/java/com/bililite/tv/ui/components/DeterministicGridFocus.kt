package com.bililite.tv.ui.components

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel

private const val GRID_FOCUS_QUEUE_CAPACITY = 8
private const val GRID_FOCUS_ATTACH_RETRY_FRAMES = 2

private data class GridVerticalFocusMove(
    val sourceIndex: Int,
    val rowDelta: Int
)

internal fun lockedColumnGridTargetIndex(
    currentIndex: Int,
    rowDelta: Int,
    columns: Int,
    itemCount: Int
): Int? {
    if (itemCount <= 0 || currentIndex !in 0 until itemCount || rowDelta == 0) {
        return null
    }
    val safeColumns = columns.coerceAtLeast(1)
    val sourceColumn = currentIndex % safeColumns
    val targetIndex = currentIndex + rowDelta.coerceIn(-1, 1) * safeColumns
    return targetIndex.takeIf {
        it in 0 until itemCount && it % safeColumns == sourceColumn
    }
}

/**
 * Serializes vertical D-pad moves for a lazy grid and keeps every move in the
 * same logical column. Compose's default spatial search is intentionally not
 * used for Up/Down because recycled rows can make the nearest candidate drift
 * into a neighbouring column during a long key press.
 */
@Composable
internal fun rememberDeterministicGridVerticalFocusHandler(
    gridState: LazyGridState,
    itemCount: Int,
    columns: Int,
    focusRequesterAt: (Int) -> FocusRequester?
): (sourceIndex: Int, rowDelta: Int) -> Boolean {
    val currentItemCount = rememberUpdatedState(itemCount)
    val currentColumns = rememberUpdatedState(columns.coerceAtLeast(1))
    val currentFocusRequesterAt = rememberUpdatedState(focusRequesterAt)
    val moves = remember {
        Channel<GridVerticalFocusMove>(
            capacity = GRID_FOCUS_QUEUE_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    }

    DisposableEffect(moves) {
        onDispose { moves.close() }
    }

    LaunchedEffect(gridState, moves) {
        var logicalIndex = -1

        for (move in moves) {
            val safeItemCount = currentItemCount.value
            val safeColumns = currentColumns.value
            if (safeItemCount <= 0 || move.sourceIndex !in 0 until safeItemCount) {
                logicalIndex = -1
                continue
            }

            // Repeated KeyDown events can arrive before focus has visibly moved.
            // Keep advancing from the last successful logical target while the
            // event source is in the same column and merely lags behind it. A
            // horizontal column change or a source already farther in the new
            // direction re-anchors the logical cursor immediately.
            val sourceColumn = move.sourceIndex % safeColumns
            val logicalColumn = logicalIndex.takeIf { it in 0 until safeItemCount }
                ?.rem(safeColumns)
            val sourceIsAheadInDirection = when {
                logicalIndex !in 0 until safeItemCount -> true
                move.rowDelta > 0 -> move.sourceIndex > logicalIndex
                else -> move.sourceIndex < logicalIndex
            }
            if (logicalColumn != sourceColumn || sourceIsAheadInDirection) {
                logicalIndex = move.sourceIndex
            }

            val targetIndex = lockedColumnGridTargetIndex(
                currentIndex = logicalIndex,
                rowDelta = move.rowDelta,
                columns = safeColumns,
                itemCount = safeItemCount
            ) ?: continue

            val requester = currentFocusRequesterAt.value(targetIndex)
            if (requester == null) {
                logicalIndex = move.sourceIndex
                continue
            }

            var focusMoved = runCatching { requester.requestFocus() }
                .isSuccess

            if (!focusMoved) {
                // Put one context row above the destination when moving down so
                // the focused card does not hug the viewport edge. Moving up can
                // place the destination row directly at the top.
                val targetRowStart = targetIndex - sourceColumn
                val scrollTarget = if (move.rowDelta > 0) {
                    (targetRowStart - safeColumns).coerceAtLeast(0)
                } else {
                    targetRowStart.coerceAtLeast(0)
                }

                runCatching { gridState.scrollToItem(scrollTarget) }

                repeat(GRID_FOCUS_ATTACH_RETRY_FRAMES) {
                    if (!focusMoved) {
                        withFrameNanos { }
                        focusMoved = runCatching { requester.requestFocus() }
                            .isSuccess
                    }
                }
            }

            logicalIndex = if (focusMoved) targetIndex else move.sourceIndex
        }
    }

    return remember(moves) {
        { sourceIndex, rowDelta ->
            if (rowDelta == 0) {
                false
            } else {
                moves.trySend(
                    GridVerticalFocusMove(
                        sourceIndex = sourceIndex,
                        rowDelta = rowDelta.coerceIn(-1, 1)
                    )
                )
                // Always consume vertical movement. Even when there is no next
                // item in the same column, focus must stay on the current card
                // instead of falling back to spatial search in another column.
                true
            }
        }
    }
}
