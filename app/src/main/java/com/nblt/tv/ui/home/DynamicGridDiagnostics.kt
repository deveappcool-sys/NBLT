package com.nblt.tv.ui.home

import android.util.Log
import com.nblt.tv.model.VideoItem
import com.nblt.tv.ui.home.HomeNavTabs.DYNAMIC

private const val TAG_FOCUS = "BiliDynamicFocus"
private const val TAG_GRID = "BiliDynamicGrid"
private const val TAG_GUARD = "BiliDynamicCrashGuard"

internal fun isDynamicGridTab(tabKey: String?): Boolean = tabKey == DYNAMIC

internal fun logDynamicFocusChanged(
    tabKey: String?,
    index: Int,
    video: VideoItem,
    videoCount: Int,
    columns: Int,
    firstVisible: Int,
    lastVisible: Int,
    itemKey: String,
    focusRequesterCount: Int,
    isLoadingMore: Boolean
) {
    if (!isDynamicGridTab(tabKey)) return
    Log.i(
        TAG_FOCUS,
        "focus changed index=$index videoCount=$videoCount columns=$columns " +
            "firstVisible=$firstVisible lastVisible=$lastVisible " +
            "bvid=${video.bvid} aid=${video.aid} cid=${video.cid} " +
            "title=${video.title.take(24)} key=$itemKey " +
            "focusRequesters=$focusRequesterCount loadingMore=$isLoadingMore"
    )
}

internal fun logDynamicLoadMoreTriggered(
    tabKey: String?,
    index: Int,
    videoCount: Int,
    threshold: Int,
    isLoadingMore: Boolean,
    hasMore: Boolean
) {
    if (!isDynamicGridTab(tabKey)) return
    Log.i(
        TAG_GRID,
        "loadMore triggered focusIndex=$index videoCount=$videoCount threshold=$threshold " +
            "loadingMore=$isLoadingMore hasMore=$hasMore"
    )
}

internal fun logDynamicScroll(
    tabKey: String?,
    focusedIndex: Int,
    targetIndex: Int,
    videoCount: Int,
    reason: String
) {
    if (!isDynamicGridTab(tabKey)) return
    Log.i(
        TAG_GRID,
        "scroll focusedIndex=$focusedIndex targetIndex=$targetIndex videoCount=$videoCount reason=$reason"
    )
}

internal fun logDynamicFocusRestore(
    tabKey: String?,
    restoreKey: String,
    restoreIndex: Int,
    resolvedIndex: Int,
    videoCount: Int,
    focusRequesterCount: Int
) {
    if (!isDynamicGridTab(tabKey)) return
    Log.i(
        TAG_FOCUS,
        "focus restore key=$restoreKey restoreIndex=$restoreIndex resolvedIndex=$resolvedIndex " +
            "videoCount=$videoCount focusRequesters=$focusRequesterCount"
    )
}

internal fun logDynamicCrashGuard(action: String, detail: String, throwable: Throwable? = null) {
    Log.w(TAG_GUARD, "$action: $detail", throwable)
}

internal fun buildStableVideoGridKeys(videos: List<VideoItem>): List<String> {
    val seen = mutableSetOf<String>()
    return videos.mapIndexed { index, video ->
        var key = video.homeFocusKey(index)
        if (!seen.add(key)) {
            key = "${key}@$index"
            seen.add(key)
        }
        key
    }
}
