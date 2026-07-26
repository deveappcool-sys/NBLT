package com.bililite.tv.model

data class HomeSection(
    val id: Int,
    val title: String,
    val videos: List<VideoItem>,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val isLoadingMore: Boolean = false,
    val loadMoreError: String? = null
)
