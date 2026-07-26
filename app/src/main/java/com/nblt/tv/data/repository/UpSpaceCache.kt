package com.nblt.tv.data.repository

import com.nblt.tv.model.UpProfile
import com.nblt.tv.model.UpVideoItem

data class UpSpaceCachedVideos(
    val videos: List<UpVideoItem>,
    val page: Int,
    val hasMore: Boolean,
    val lastLoadedAt: Long = System.currentTimeMillis()
)

private data class UpSpaceCacheEntry(
    val profile: UpProfile? = null,
    val profileLoadedAt: Long = 0L,
    val firstPage: UpSpaceCachedVideos? = null
)

class UpSpaceCache(
    private val ttlMs: Long = CACHE_TTL_MS
) {
    private val entries = mutableMapOf<Long, UpSpaceCacheEntry>()

    @Synchronized
    fun getValidFirstPage(mid: Long, now: Long = System.currentTimeMillis()): UpSpaceCachedVideos? {
        val cached = entries[mid]?.firstPage ?: return null
        return if (now - cached.lastLoadedAt <= ttlMs) cached else null
    }

    @Synchronized
    fun getValidProfile(mid: Long, now: Long = System.currentTimeMillis()): UpProfile? {
        val entry = entries[mid] ?: return null
        val profile = entry.profile ?: return null
        return if (now - entry.profileLoadedAt <= ttlMs) profile else null
    }

    @Synchronized
    fun putFirstPage(
        mid: Long,
        videos: List<UpVideoItem>,
        page: Int,
        hasMore: Boolean
    ) {
        val existing = entries[mid]
        entries[mid] = (existing ?: UpSpaceCacheEntry()).copy(
            firstPage = UpSpaceCachedVideos(
                videos = videos,
                page = page,
                hasMore = hasMore
            )
        )
    }

    @Synchronized
    fun putProfile(mid: Long, profile: UpProfile) {
        val existing = entries[mid]
        entries[mid] = (existing ?: UpSpaceCacheEntry()).copy(
            profile = profile,
            profileLoadedAt = System.currentTimeMillis()
        )
    }

    @Synchronized
    fun invalidateVideos(mid: Long) {
        val entry = entries[mid] ?: return
        entries[mid] = entry.copy(firstPage = null)
    }

    @Synchronized
    fun remove(mid: Long) {
        entries.remove(mid)
    }

    companion object {
        const val CACHE_TTL_MS = 5 * 60 * 1000L
    }
}
