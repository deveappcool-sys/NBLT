package com.nblt.tv.data.repository

import android.util.Log
import com.nblt.tv.data.api.BilibiliHeartbeatApi
import com.nblt.tv.model.VideoItem
import com.nblt.tv.player.PlaybackReportEvent
import com.nblt.tv.storage.CookieStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

class HeartbeatRepository(
    private val cookieStorage: CookieStorage,
    private val heartbeatApi: BilibiliHeartbeatApi = BilibiliHeartbeatApi()
) {
    private val startTsByVideo = mutableMapOf<String, Long>()

    suspend fun report(
        video: VideoItem,
        currentPositionMs: Long,
        durationMs: Long,
        event: PlaybackReportEvent
    ) {
        withContext(Dispatchers.IO) {
            runCatching {
                val cookieHeader = cookieStorage.getCookieHeader()
                val csrf = cookieStorage.getCookieValue("bili_jct").orEmpty()

                if (!cookieStorage.hasLoginCookies() || cookieHeader.isBlank()) {
                    Log.i(TAG, "Skip heartbeat: not logged in")
                    return@withContext
                }
                if (csrf.isBlank()) {
                    Log.i(TAG, "Skip heartbeat: missing bili_jct")
                    return@withContext
                }
                if (video.aid <= 0L || video.cid <= 0L || video.bvid.isBlank()) {
                    Log.i(
                        TAG,
                        "Skip heartbeat: invalid ids, aid=${video.aid}, cid=${video.cid}, bvid=${video.bvid}"
                    )
                    return@withContext
                }

                val videoKey = video.bvid.ifBlank { video.aid.toString() }
                val startTsSeconds = startTsByVideo.getOrPut(videoKey) {
                    System.currentTimeMillis() / 1000L
                }
                val durationSeconds = (durationMs / 1000L).coerceAtLeast(0L)
                val playedSeconds = normalizePlayedSeconds(currentPositionMs, durationSeconds)

                val response = heartbeatApi.reportPlaybackProgress(
                    video = video,
                    cookieHeader = cookieHeader,
                    csrf = csrf,
                    playedTimeSeconds = playedSeconds,
                    durationSeconds = durationSeconds,
                    startTsSeconds = startTsSeconds,
                    event = event.name
                )

                Log.i(
                    TAG,
                    "Reported ${event.name}: aid=${video.aid}, cid=${video.cid}, bvid=${video.bvid}, " +
                        "currentPosition=${playedSeconds}s, duration=${durationSeconds}s, " +
                        "code=${response.code}, message=${response.message}"
                )

                if (event == PlaybackReportEvent.STOP) {
                    startTsByVideo.remove(videoKey)
                }
            }.onFailure { throwable ->
                Log.w(
                    TAG,
                    "Heartbeat failed: aid=${video.aid}, cid=${video.cid}, bvid=${video.bvid}, " +
                        "currentPosition=${currentPositionMs}ms, duration=${durationMs}ms",
                    throwable
                )
            }
        }
    }

    private fun normalizePlayedSeconds(currentPositionMs: Long, durationSeconds: Long): Long {
        val currentSeconds = max(0L, currentPositionMs / 1000L)
        return if (durationSeconds > 0L) {
            min(currentSeconds, durationSeconds)
        } else {
            currentSeconds
        }
    }

    private companion object {
        const val TAG = "BiliHeartbeat"
    }
}
