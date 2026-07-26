package com.bililite.tv.model

import androidx.compose.ui.graphics.Color
import com.bililite.tv.util.FormatUtils

data class UpVideoItem(
    val aid: Long,
    val bvid: String,
    val cid: Long,
    val title: String,
    val coverUrl: String,
    val playCount: Long,
    val danmakuCount: Long,
    val duration: Long,
    val pubdate: Long,
    val accent: Color
) {
    val playCountText: String
        get() = FormatUtils.formatPlayCount(playCount)

    val danmakuCountText: String
        get() = if (danmakuCount > 0) FormatUtils.formatPlayCount(danmakuCount) else "--"

    val durationText: String
        get() = FormatUtils.formatDuration(duration)

    val pubdateText: String
        get() = FormatUtils.formatHistoryTime(pubdate)

    fun toVideoItem(
        ownerMid: Long,
        ownerName: String
    ): VideoItem {
        return VideoItem(
            aid = aid,
            bvid = bvid,
            cid = cid,
            coverUrl = coverUrl,
            title = title,
            ownerName = ownerName,
            playCount = playCount,
            duration = duration,
            description = title,
            accent = accent,
            ownerMid = ownerMid,
            pubdate = pubdate
        )
    }
}

data class UpVideoPage(
    val videos: List<UpVideoItem>,
    val page: Int,
    val hasMore: Boolean,
    val totalCount: Int = 0
)
