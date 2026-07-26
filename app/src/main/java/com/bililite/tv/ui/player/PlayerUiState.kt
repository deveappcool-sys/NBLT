package com.bililite.tv.ui.player

data class PlayerUiState(
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L
)
