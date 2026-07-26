package com.bililite.tv.ui.state

import com.bililite.tv.model.UpVideoItem

data class PagedUpVideoList(
    val videos: List<UpVideoItem>,
    val page: Int = 1,
    val hasMore: Boolean = true,
    val isLoadingMore: Boolean = false,
    val loadMoreError: String? = null
)
