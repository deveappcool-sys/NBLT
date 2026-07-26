package com.nblt.tv.data.repository

import android.util.Log
import com.nblt.tv.data.api.BilibiliUserApi
import com.nblt.tv.data.api.SessionExpiredException
import com.nblt.tv.model.UpProfile
import com.nblt.tv.model.UpVideoItem
import com.nblt.tv.model.UpVideoPage
import com.nblt.tv.storage.CookieStorage
import com.nblt.tv.util.UpSpaceDebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserRepository(
    private val cookieStorage: CookieStorage,
    private val api: BilibiliUserApi = BilibiliUserApi(),
    private val upSpaceCache: UpSpaceCache = UpSpaceCache(),
    private val upSpaceRequestGuard: UpSpaceRequestGuard = UpSpaceRequestGuard()
) {
    private var lastRateLimitedAt: Long = 0L

    fun getCachedFirstPage(mid: Long): UpSpaceCachedVideos? {
        return upSpaceCache.getValidFirstPage(mid)
    }

    fun getCachedProfile(mid: Long): UpProfile? {
        return upSpaceCache.getValidProfile(mid)
    }

    fun invalidateVideoCache(mid: Long) {
        upSpaceCache.invalidateVideos(mid)
    }

    fun isRetryCooldownActive(now: Long = System.currentTimeMillis()): Boolean {
        return lastRateLimitedAt > 0L &&
            now - lastRateLimitedAt < UpSpaceVideoErrors.RETRY_COOLDOWN_MS
    }

    fun markRateLimited(now: Long = System.currentTimeMillis()) {
        lastRateLimitedAt = now
        Log.i(TAG, "rate limited mid cache retained")
    }

    fun clearRateLimited() {
        lastRateLimitedAt = 0L
    }

    fun isVideoRequestInFlight(mid: Long, page: Int): Boolean {
        return upSpaceRequestGuard.isInFlight(mid, page)
    }

    fun tryStartVideoRequest(mid: Long, page: Int): Boolean {
        return upSpaceRequestGuard.tryStart(mid, page)
    }

    fun finishVideoRequest(mid: Long, page: Int) {
        upSpaceRequestGuard.finish(mid, page)
    }

    fun cacheFirstPage(mid: Long, page: UpVideoPage) {
        upSpaceCache.putFirstPage(
            mid = mid,
            videos = page.videos,
            page = page.page,
            hasMore = page.hasMore
        )
    }

    fun cacheProfile(mid: Long, profile: UpProfile) {
        upSpaceCache.putProfile(mid, profile)
    }

    fun updateCachedVideos(mid: Long, videos: List<UpVideoItem>, page: Int, hasMore: Boolean) {
        upSpaceCache.putFirstPage(mid, videos, page, hasMore)
    }

    suspend fun loadUpProfile(mid: Long, forceRefresh: Boolean = false): Result<UpProfile> {
        if (!forceRefresh) {
            getCachedProfile(mid)?.let { cached ->
                Log.i(TAG, "use cached profile mid=$mid nickname=${cached.nickname}")
                return Result.success(cached)
            }
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                api.fetchUserProfile(mid, cookieStorage.getCookieHeader()).also { profile ->
                    cacheProfile(mid, profile)
                }
            }
        }
    }

    suspend fun loadUpVideos(
        mid: Long,
        upName: String,
        page: Int = 1,
        forceRefresh: Boolean = false
    ): Result<UpVideoPage> {
        if (page == 1 && !forceRefresh) {
            getCachedFirstPage(mid)?.let { cached ->
                Log.i(TAG, "use cached videos mid=$mid count=${cached.videos.size}")
                UpSpaceDebugLog.logRepositoryPath(
                    path = "cache hit first page",
                    detail = "mid=$mid, count=${cached.videos.size}, skip network"
                )
                UpSpaceDebugLog.logUiStateTarget(
                    target = if (cached.videos.isEmpty()) "Empty" else "Success",
                    detail = "from cache page=1"
                )
                return Result.success(
                    UpVideoPage(
                        videos = cached.videos,
                        page = cached.page,
                        hasMore = cached.hasMore,
                        totalCount = cached.videos.size
                    )
                )
            }
        }

        val duplicateAllowed = upSpaceRequestGuard.tryStart(mid, page)
        UpSpaceDebugLog.logDuplicateRequestGuard(mid = mid, page = page, allowed = duplicateAllowed)
        if (!duplicateAllowed) {
            UpSpaceDebugLog.logRepositoryPath(
                path = "duplicate blocked",
                detail = "mid=$mid, page=$page"
            )
            return Result.failure(DuplicateUpSpaceRequestException())
        }

        UpSpaceDebugLog.logRepositoryPath(
            path = "network request start",
            detail = "mid=$mid, page=$page, forceRefresh=$forceRefresh, hasCookie=${cookieStorage.getCookieHeader().isNotBlank()}"
        )
        return try {
            withContext(Dispatchers.IO) {
                runCatching {
                    Log.i(TAG, "request videos mid=$mid page=$page")
                    api.fetchUpVideos(
                        mid = mid,
                        upName = upName,
                        page = page,
                        cookieHeader = cookieStorage.getCookieHeader()
                    )
                }
            }.also { result ->
                result.onSuccess { videoPage ->
                    clearRateLimited()
                    if (page == 1) {
                        cacheFirstPage(mid, videoPage)
                    }
                    Log.i(
                        TAG,
                        "videos loaded count=${videoPage.videos.size} hasMore=${videoPage.hasMore} page=${videoPage.page}"
                    )
                    UpSpaceDebugLog.logUiStateTarget(
                        target = if (videoPage.videos.isEmpty()) "Empty" else "Success",
                        detail = "repository success mid=$mid page=$page count=${videoPage.videos.size}"
                    )
                }.onFailure { error ->
                    val sessionExpired = SessionExpiredException.isExpired(error)
                    val rateLimit = UpSpaceVideoErrors.isRateLimitedMessage(error.message)
                    val mappedRateLimit = UpSpaceVideoErrors.normalizeVideoErrorMessage(
                        error.message,
                        defaultMessage = ""
                    ) == UpSpaceVideoErrors.RATE_LIMIT_MESSAGE
                    UpSpaceDebugLog.logResponseClassification(
                        bilibiliCode = -1,
                        bilibiliMessage = error.message.orEmpty(),
                        sessionExpiredRecognized = sessionExpired,
                        rateLimitRecognized = rateLimit,
                        mappedToRateLimitMessage = mappedRateLimit || rateLimit
                    )
                    UpSpaceDebugLog.logUiStateTarget(
                        target = "Error",
                        detail = "repository failure mid=$mid page=$page message=${error.message}"
                    )
                    if (UpSpaceVideoErrors.isRateLimitedMessage(error.message)) {
                        markRateLimited()
                        Log.i(TAG, "rate limited mid=$mid page=$page")
                    }
                }
            }
        } finally {
            upSpaceRequestGuard.finish(mid, page)
        }
    }

    private companion object {
        const val TAG = "BiliUpSpace"
    }
}
