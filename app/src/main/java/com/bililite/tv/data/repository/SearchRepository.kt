package com.bililite.tv.data.repository

import com.bililite.tv.data.api.BilibiliSearchApi
import com.bililite.tv.model.SearchSuggestion
import com.bililite.tv.model.VideoItem
import com.bililite.tv.storage.CookieStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SearchRepository(
    private val cookieStorage: CookieStorage? = null,
    private val api: BilibiliSearchApi = BilibiliSearchApi()
) {
    suspend fun getSuggestions(keyword: String): Result<List<SearchSuggestion>> {
        return withContext(Dispatchers.IO) {
            runCatching { api.fetchSuggestions(keyword, cookieStorage?.getCookieHeader().orEmpty()) }
                .recover { fallbackSuggestions(keyword) }
        }
    }

    suspend fun searchVideos(
        keyword: String,
        page: Int = 1
    ): Result<List<VideoItem>> {
        return withContext(Dispatchers.IO) {
            runCatching { api.searchVideos(keyword, page, cookieHeader = cookieStorage?.getCookieHeader().orEmpty()) }
        }
    }

    private fun fallbackSuggestions(keyword: String): List<SearchSuggestion> {
        val fallback = listOf("动画", "音乐", "游戏", "科技", "影视", "舞蹈", "美食", "纪录片")
        return fallback
            .filter { keyword.isBlank() || it.contains(keyword, ignoreCase = true) }
            .ifEmpty { fallback }
            .map(::SearchSuggestion)
    }
}
