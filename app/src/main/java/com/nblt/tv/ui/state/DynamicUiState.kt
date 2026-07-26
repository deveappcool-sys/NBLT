package com.nblt.tv.ui.state

import com.nblt.tv.data.repository.DynamicHomeData

sealed interface DynamicUiState {
    data object Loading : DynamicUiState
    data class Success(val data: DynamicHomeData) : DynamicUiState
    data class Error(val message: String) : DynamicUiState
    data object Empty : DynamicUiState
}
