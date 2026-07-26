package com.bililite.tv.data.repository

import com.bililite.tv.data.api.BilibiliPopularApi
import com.bililite.tv.model.VideoItem
import com.bililite.tv.storage.CookieStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PopularRepository(
    private val api: BilibiliPopularApi = BilibiliPopularApi(),
    private val cookieStorage: CookieStorage? = null
) {
    private var refreshCount = 0

    suspend fun getPopularVideos(forceRefresh: Boolean = false): Result<List<VideoItem>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                if (forceRefresh) {
                    refreshCount += 1
                }
                val cookieHeader = cookieStorage
                    ?.getCookieHeader()
                    .orEmpty()

                api.fetchPopularVideos(
                    pageNumber = 1,
                    refreshCount = refreshCount,
                    forceRefresh = forceRefresh,
                    cookieHeader = cookieHeader
                )
            }
        }
    }

    suspend fun getPopularVideosPage(page: Int): Result<List<VideoItem>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val cookieHeader = cookieStorage
                    ?.getCookieHeader()
                    .orEmpty()

                api.fetchPopularVideos(
                    pageNumber = page,
                    refreshCount = page,
                    forceRefresh = true,
                    cookieHeader = cookieHeader
                )
            }
        }
    }

    private companion object {
        const val MAX_REFRESH_PAGE = 5
    }
}
