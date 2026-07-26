package com.nblt.tv.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.nblt.tv.model.VideoItem
import com.nblt.tv.ui.player.PlayerUiState
import kotlinx.coroutines.delay

enum class PlaybackReportEvent {
    START,
    PROGRESS,
    PAUSE,
    STOP
}

@Composable
fun PlaybackProgressReporter(
    video: VideoItem,
    state: PlayerUiState,
    enabled: Boolean,
    onReport: (VideoItem, PlayerUiState, PlaybackReportEvent) -> Unit
) {
    var hasReportedStart by remember(video.id) { mutableStateOf(false) }
    var wasPlaying by remember(video.id) { mutableStateOf(false) }
    val latestState = rememberUpdatedState(state)
    val latestOnReport = rememberUpdatedState(onReport)

    LaunchedEffect(enabled, video.id, state.isPlaying) {
        if (!enabled) {
            return@LaunchedEffect
        }

        if (state.isPlaying && !hasReportedStart) {
            hasReportedStart = true
            latestOnReport.value(video, state, PlaybackReportEvent.START)
        }

        if (wasPlaying && !state.isPlaying && hasReportedStart) {
            latestOnReport.value(video, state, PlaybackReportEvent.PAUSE)
        }
        wasPlaying = state.isPlaying
    }

    LaunchedEffect(enabled, video.id) {
        while (enabled) {
            delay(REPORT_INTERVAL_MS)
            val currentState = latestState.value
            if (currentState.isPlaying && hasReportedStart) {
                latestOnReport.value(video, currentState, PlaybackReportEvent.PROGRESS)
            }
        }
    }

    DisposableEffect(enabled, video.id) {
        onDispose {
            if (enabled && hasReportedStart) {
                latestOnReport.value(video, latestState.value, PlaybackReportEvent.STOP)
            }
        }
    }
}

private const val REPORT_INTERVAL_MS = 20_000L
