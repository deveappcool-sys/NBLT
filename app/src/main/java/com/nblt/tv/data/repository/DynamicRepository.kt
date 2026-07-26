package com.nblt.tv.data.repository

import android.util.Log
import com.nblt.tv.data.api.BilibiliDynamicApi
import com.nblt.tv.data.api.BilibiliFollowApi
import com.nblt.tv.data.api.BilibiliUpVideoApi
import com.nblt.tv.model.FollowedUp
import com.nblt.tv.model.VideoItem
import com.nblt.tv.storage.CookieStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DynamicRepository(
    private val cookieStorage: CookieStorage,
    private val followApi: BilibiliFollowApi = BilibiliFollowApi(),
    private val dynamicApi: BilibiliDynamicApi = BilibiliDynamicApi(),
    private val upVideoApi: BilibiliUpVideoApi = BilibiliUpVideoApi()
) {
    private val upVideoCache = mutableMapOf<Long, List<VideoItem>>()
    private val loadingUpMids = mutableSetOf<Long>()
    private val lastRequestTime = mutableMapOf<Long, Long>()
    private val upVideoLock = Any()

    suspend fun loadDynamicHome(): Result<DynamicHomeData> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val user = cookieStorage.getSavedUserInfo() ?: error("请先登录")
                val cookieHeader = cookieStorage.getCookieHeader()
                if (cookieHeader.isBlank()) {
                    error("请先登录")
                }

                val page = dynamicApi.fetchVideoDynamics(cookieHeader)
                DynamicHomeData(
                    followedUps = followApi.fetchFollowedUps(user.mid, cookieHeader),
                    videos = page.videos,
                    offset = page.offset,
                    hasMore = page.hasMore
                )
            }
        }
    }

    suspend fun loadMoreDynamicVideos(offset: String?): Result<DynamicVideoPage> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val cookieHeader = cookieStorage.getCookieHeader()
                if (cookieHeader.isBlank()) {
                    error("\u8bf7\u5148\u767b\u5f55")
                }
                dynamicApi.fetchVideoDynamics(cookieHeader, offset)
            }
        }
    }

    suspend fun loadUpVideos(
        mid: Long,
        upName: String,
        forceRefresh: Boolean = false,
        page: Int = 1
    ): Result<List<VideoItem>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val decision = decideUpVideoLoad(mid, upName, forceRefresh)
                when (decision) {
                    is UpVideoLoadDecision.UseCache -> decision.videos
                    is UpVideoLoadDecision.SkipAlreadyLoading -> {
                        decision.cachedVideos ?: error("投稿视频正在加载，请稍候")
                    }
                    is UpVideoLoadDecision.SkipCooldown -> {
                        decision.cachedVideos ?: error("请求太频繁，请稍后再试")
                    }
                    UpVideoLoadDecision.Request -> requestUpVideos(mid, upName, page)
                }
            }
        }
    }

    suspend fun loadMoreUpVideos(
        mid: Long,
        upName: String,
        page: Int
    ): Result<List<VideoItem>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                upVideoApi.fetchUpVideos(
                    mid = mid,
                    upName = upName,
                    cookieHeader = cookieStorage.getCookieHeader(),
                    pageNumber = page
                )
            }
        }
    }

    private fun decideUpVideoLoad(
        mid: Long,
        upName: String,
        forceRefresh: Boolean
    ): UpVideoLoadDecision {
        val now = System.currentTimeMillis()
        synchronized(upVideoLock) {
            val cachedVideos = upVideoCache[mid]
            if (!forceRefresh && cachedVideos != null) {
                Log.i(TAG_UP, "selected up name=$upName, selected mid=$mid, whether using cache=true")
                return UpVideoLoadDecision.UseCache(cachedVideos)
            }

            if (loadingUpMids.contains(mid)) {
                Log.i(
                    TAG_UP,
                    "selected up name=$upName, selected mid=$mid, " +
                        "whether request skipped because already loading=true"
                )
                return UpVideoLoadDecision.SkipAlreadyLoading(cachedVideos)
            }

            val lastRequest = lastRequestTime[mid] ?: 0L
            if (!forceRefresh && now - lastRequest < UP_VIDEO_COOLDOWN_MS) {
                Log.i(
                    TAG_UP,
                    "selected up name=$upName, selected mid=$mid, " +
                        "whether request skipped because cooldown=true"
                )
                return UpVideoLoadDecision.SkipCooldown(cachedVideos)
            }

            loadingUpMids.add(mid)
            lastRequestTime[mid] = now
            Log.i(TAG_UP, "selected up name=$upName, selected mid=$mid, whether using cache=false")
            return UpVideoLoadDecision.Request
        }
    }

    private fun requestUpVideos(
        mid: Long,
        upName: String,
        page: Int
    ): List<VideoItem> {
        val cachedVideos = synchronized(upVideoLock) { upVideoCache[mid] }
        return try {
            val videos = upVideoApi.fetchUpVideos(
                mid = mid,
                upName = upName,
                cookieHeader = cookieStorage.getCookieHeader(),
                pageNumber = page
            )
            synchronized(upVideoLock) {
                upVideoCache[mid] = videos
            }
            videos
        } catch (throwable: Throwable) {
            if (cachedVideos != null) {
                Log.w(
                    TAG_UP,
                    "Up video request failed, keep cached list: selected up name=$upName, selected mid=$mid",
                    throwable
                )
                cachedVideos
            } else {
                val message = throwable.message.orEmpty()
                if (message.contains("频繁") || message.contains("太快")) {
                    error("请求太频繁，请稍后再试")
                } else {
                    throw throwable
                }
            }
        } finally {
            synchronized(upVideoLock) {
                loadingUpMids.remove(mid)
            }
        }
    }

    private sealed interface UpVideoLoadDecision {
        data class UseCache(val videos: List<VideoItem>) : UpVideoLoadDecision
        data class SkipAlreadyLoading(val cachedVideos: List<VideoItem>?) : UpVideoLoadDecision
        data class SkipCooldown(val cachedVideos: List<VideoItem>?) : UpVideoLoadDecision
        data object Request : UpVideoLoadDecision
    }

    private companion object {
        const val TAG_UP = "BiliUpVideos"
        const val UP_VIDEO_COOLDOWN_MS = 20_000L
    }
}

data class DynamicHomeData(
    val followedUps: List<FollowedUp>,
    val videos: List<VideoItem>,
    val offset: String? = null,
    val hasMore: Boolean = true,
    val isLoadingMore: Boolean = false,
    val loadMoreError: String? = null
)

data class DynamicVideoPage(
    val videos: List<VideoItem>,
    val offset: String?,
    val hasMore: Boolean
)
