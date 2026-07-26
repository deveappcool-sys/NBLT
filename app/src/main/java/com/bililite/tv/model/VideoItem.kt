package com.bililite.tv.model

import androidx.compose.ui.graphics.Color
import com.bililite.tv.util.FormatUtils
import kotlin.math.absoluteValue

data class VideoItem(
    val aid: Long,
    val bvid: String,
    val cid: Long,
    val coverUrl: String,
    val title: String,
    val ownerName: String,
    val playCount: Long,
    val duration: Long,
    val description: String,
    val accent: Color,
    val ownerMid: Long = 0L,
    val ownerFaceUrl: String = "",
    val historyViewAt: Long = 0L,
    val historyProgress: Long = 0L,
    val pubdate: Long = 0L,
    val pages: List<VideoPage> = emptyList(),
    val currentPage: Int = 1,
    val likeCount: Long? = null,
    val coinCount: Long? = null,
    val favoriteCount: Long? = null,
    val danmakuCount: Long? = null,
    val epId: Long = 0L,
    val seasonId: Long = 0L,
    val contentType: VideoContentType = VideoContentType.UGC,
    val badgeText: String = "",
    val indexShow: String = "",
    val orderText: String = "",
    val scoreText: String = "",
    val mediaId: Long = 0L,
    val seasonType: Int = 0
) {
    val id: Int
        get() = when {
            aid != 0L -> aid.hashCode()
            bvid.isNotBlank() -> bvid.hashCode()
            epId > 0L -> epId.hashCode()
            else -> title.hashCode()
        }.absoluteValue

    val uploader: String
        get() = ownerName

    val views: String
        get() = FormatUtils.formatPlayCount(playCount)

    val durationText: String
        get() = FormatUtils.formatDuration(duration)
}

fun VideoItem.mergeDetailFields(fetched: VideoItem): VideoItem {
    return copy(
        aid = fetched.aid.takeIf { it > 0 } ?: aid,
        bvid = fetched.bvid.ifBlank { bvid },
        cid = fetched.cid.takeIf { it > 0 } ?: cid,
        duration = if (fetched.duration > 0) fetched.duration else duration,
        playCount = if (fetched.playCount > 0L) fetched.playCount else playCount,
        description = fetched.description.ifBlank { description },
        pages = fetched.pages.ifEmpty { pages },
        currentPage = fetched.currentPage.takeIf { it > 0 } ?: currentPage,
        ownerMid = fetched.ownerMid.takeIf { it > 0 } ?: ownerMid,
        ownerName = fetched.ownerName.ifBlank { ownerName },
        ownerFaceUrl = fetched.ownerFaceUrl.ifBlank { ownerFaceUrl },
        likeCount = fetched.likeCount ?: likeCount,
        coinCount = fetched.coinCount ?: coinCount,
        favoriteCount = fetched.favoriteCount ?: favoriteCount,
        danmakuCount = fetched.danmakuCount ?: danmakuCount,
        epId = fetched.epId.takeIf { it > 0L } ?: epId,
        seasonId = fetched.seasonId.takeIf { it > 0L } ?: seasonId
    )
}

fun VideoItem.displayTitleForPlayer(): String {
    if (pages.size <= 1) {
        return title
    }
    val part = pages.firstOrNull { it.cid == cid }?.part?.takeIf { it.isNotBlank() }
    return if (part != null) {
        "$title - $part"
    } else {
        title
    }
}

fun VideoItem.isSameVideo(other: VideoItem): Boolean {
    return when {
        bvid.isNotBlank() && other.bvid.isNotBlank() -> bvid == other.bvid
        aid > 0 && other.aid > 0 -> aid == other.aid
        epId > 0 && other.epId > 0 -> epId == other.epId
        else -> id == other.id
    }
}

enum class VideoContentType {
    UGC,
    PGC
}
