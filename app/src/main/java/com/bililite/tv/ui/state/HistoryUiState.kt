package com.bililite.tv.ui.state

import com.bililite.tv.model.VideoItem

sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data class Success(
        val videos: List<VideoItem>,
        val cursorMax: Long = 0L,
        val cursorViewAt: Long = 0L,
        val hasMore: Boolean = true,
        val isLoadingMore: Boolean = false,
        val loadMoreError: String? = null
    ) : HistoryUiState
    data class Error(val message: String) : HistoryUiState
    data object Empty : HistoryUiState
}
