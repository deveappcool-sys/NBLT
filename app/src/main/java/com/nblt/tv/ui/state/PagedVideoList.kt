package com.nblt.tv.ui.state

import com.nblt.tv.model.VideoItem

data class PagedVideoList(
    val videos: List<VideoItem>,
    val page: Int = 1,
    val cursor: String? = null,
    val hasMore: Boolean = true,
    val isLoadingMore: Boolean = false,
    val loadMoreError: String? = null
)
