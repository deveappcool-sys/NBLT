package com.bililite.tv.ui.detail

import com.bililite.tv.model.VideoItem
import com.bililite.tv.model.isSameVideo

/**
 * In-memory video pools already loaded elsewhere in the app (no new network calls).
 */
data class DetailRelatedSources(
    val sourceList: List<VideoItem> = emptyList(),
    val sameUpVideos: List<VideoItem> = emptyList(),
    val fallbackPools: List<List<VideoItem>> = emptyList()
)

fun resolveDetailRelatedVideos(
    current: VideoItem,
    sources: DetailRelatedSources,
    maxCount: Int = 20
): List<VideoItem> {
    val seen = mutableSetOf<String>()
    val result = mutableListOf<VideoItem>()

    fun key(item: VideoItem): String = when {
        item.bvid.isNotBlank() -> "bvid:${item.bvid}"
        item.aid > 0L -> "aid:${item.aid}"
        else -> "id:${item.id}"
    }

    fun append(pool: List<VideoItem>) {
        for (item in pool) {
            if (item.isSameVideo(current)) continue
            if (!seen.add(key(item))) continue
            result += item
            if (result.size >= maxCount) return
        }
    }

    if (current.ownerMid > 0L) {
        append(
            sources.sameUpVideos.filter { video ->
                video.ownerMid == current.ownerMid || video.ownerMid == 0L
            }
        )
    }
    append(sources.sourceList)
    for (pool in sources.fallbackPools) {
        append(pool)
        if (result.size >= maxCount) break
    }
    return result
}
