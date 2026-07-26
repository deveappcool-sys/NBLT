package com.nblt.tv.data.repository

import android.util.Log
import com.nblt.tv.data.api.BilibiliRecommendApi
import com.nblt.tv.model.VideoItem
import com.nblt.tv.storage.CookieStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RecommendRepository(
    private val cookieStorage: CookieStorage? = null,
    private val api: BilibiliRecommendApi = BilibiliRecommendApi()
) {
    private var refreshCount = 0

    suspend fun getRecommendVideos(
        forceRefresh: Boolean = false,
        currentUserPresent: Boolean? = null
    ): Result<List<VideoItem>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                if (forceRefresh) {
                    refreshCount += 1
                }
                val cookieHeader = cookieStorage?.getCookieHeader().orEmpty()
                val isLoggedIn = cookieStorage?.hasLoginCookies() == true
                Log.i(
                    TAG_DEBUG,
                    "load recommend: isLoggedIn=$isLoggedIn, hasCookie=${cookieHeader.isNotBlank()}"
                )
                api.fetchRecommendVideos(
                    refreshCount = refreshCount,
                    forceRefresh = forceRefresh,
                    cookieHeader = cookieHeader,
                    isLoggedIn = currentUserPresent ?: isLoggedIn
                )
            }
        }
    }

    suspend fun getRecommendVideosPage(
        page: Int,
        currentUserPresent: Boolean? = null
    ): Result<List<VideoItem>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val cookieHeader = cookieStorage?.getCookieHeader().orEmpty()
                val isLoggedIn = cookieStorage?.hasLoginCookies() == true
                api.fetchRecommendVideos(
                    refreshCount = page,
                    forceRefresh = true,
                    cookieHeader = cookieHeader,
                    isLoggedIn = currentUserPresent ?: isLoggedIn
                )
            }
        }
    }

    private companion object {
        const val TAG_DEBUG = "BiliRecommendDebug"
    }
}
