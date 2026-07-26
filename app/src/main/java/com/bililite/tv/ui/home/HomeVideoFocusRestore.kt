package com.bililite.tv.ui.home

import android.util.Log
import com.bililite.tv.model.VideoItem

private const val TAG_FOCUS = "BiliFocus"

data class HomeVideoFocusRestore(
    val tab: String,
    val videoKey: String,
    val index: Int,
    val dynamicUpMid: Long? = null,
    val restoreToken: Long = 0L
)

fun VideoItem.homeFocusKey(fallbackIndex: Int = 0): String {
    return when {
        bvid.isNotBlank() -> bvid
        aid > 0L -> "aid:$aid"
        cid > 0L -> "cid:$cid"
        epId > 0L -> "ep:$epId"
        else -> "idx:$fallbackIndex"
    }
}

fun VideoItem.matchesHomeFocusKey(storedKey: String, index: Int = 0): Boolean {
    if (storedKey.isBlank()) return false
    if (homeFocusKey(index) == storedKey) return true
    if (homeFocusKey(0) == storedKey) return true
    if (bvid.isNotBlank() && bvid == storedKey) return true
    if (aid > 0L && storedKey == "aid:$aid") return true
    if (cid > 0L && storedKey == "cid:$cid") return true
    if (epId > 0L && storedKey == "ep:$epId") return true
    return false
}

fun VideoItem.isOpenableVideo(): Boolean {
    return bvid.isNotBlank() || aid > 0L || epId > 0L
}

internal fun logVideoFocused(tab: String, index: Int, video: VideoItem) {
    if (!FOCUS_LOG_ENABLED) return
    Log.i(TAG_FOCUS, "video focused tab=$tab index=$index id=${video.homeFocusKey(index)}")
}

internal fun logOpenDetailFromTab(tab: String, index: Int, video: VideoItem) {
    if (!FOCUS_LOG_ENABLED) return
    Log.i(
        TAG_FOCUS,
        "open detail from tab=$tab index=$index id=${video.homeFocusKey(index)}"
    )
}

internal fun logRestoreFocusAttempt(restore: HomeVideoFocusRestore) {
    if (!FOCUS_LOG_ENABLED) return
    Log.i(
        TAG_FOCUS,
        "restore focus tab=${restore.tab} index=${restore.index} id=${restore.videoKey}"
    )
}

internal fun logRestoreFocusResult(success: Boolean, resolvedIndex: Int, fallback: Boolean) {
    if (!FOCUS_LOG_ENABLED) return
    Log.i(
        TAG_FOCUS,
        "restore focus ${if (success) "success" else "fallback"} index=$resolvedIndex fallback=$fallback"
    )
}

private const val FOCUS_LOG_ENABLED = false
