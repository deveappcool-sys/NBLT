package com.bililite.tv.data.repository

import com.bililite.tv.data.api.BilibiliRegionApi
import com.bililite.tv.model.HomeSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class HomeSectionRepository(
    private val api: BilibiliRegionApi = BilibiliRegionApi()
) {
    suspend fun loadSections(): Result<List<HomeSection>> = withContext(Dispatchers.IO) {
        runCatching {
            coroutineScope {
                SECTION_SPECS.map { (id, title) ->
                    async { runCatching { api.fetchSection(id, title) }.getOrNull() }
                }.awaitAll().filterNotNull().filter { it.videos.isNotEmpty() }
            }
        }
    }

    suspend fun loadSectionPage(id: Int, title: String, page: Int): Result<HomeSection> =
        withContext(Dispatchers.IO) {
            runCatching { api.fetchSection(id = id, title = title, page = page) }
        }

    private companion object {
        val SECTION_SPECS = listOf(
            1002 to "电影",
            1005 to "电视剧",
            1003 to "纪录片",
            4 to "游戏",
            3 to "音乐",
            181 to "影视",
            36 to "知识"
        )
    }
}
