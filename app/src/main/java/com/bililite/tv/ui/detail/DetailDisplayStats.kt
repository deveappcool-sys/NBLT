package com.bililite.tv.ui.detail

import com.bililite.tv.model.VideoItem
import com.bililite.tv.util.FormatUtils

internal data class DetailDisplayStats(
    val danmakuCount: Long? = null,
    val likeCount: Long? = null,
    val coinCount: Long? = null,
    val favoriteCount: Long? = null
)

internal fun VideoItem.toDetailDisplayStats(): DetailDisplayStats {
    return DetailDisplayStats(
        danmakuCount = danmakuCount,
        likeCount = likeCount,
        coinCount = coinCount,
        favoriteCount = favoriteCount
    )
}

/** Returns formatted count, or null when the value is unknown (not loaded from detail API). */
internal fun formatDetailCount(count: Long?): String? {
    return when (count) {
        null -> null
        0L -> "0"
        else -> FormatUtils.formatPlayCount(count)
    }
}

internal fun formatDetailCountLabel(label: String, count: Long?): String? {
    return formatDetailCount(count)?.let { "$label $it" }
}
