package com.bililite.tv.ui.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.bililite.tv.data.api.BilibiliCdnPreference
import com.bililite.tv.model.PlayUrl
import java.net.URI

internal data class PlaybackBufferPolicy(
    val networkClass: String,
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int
)

internal fun Context.resolvePlaybackBufferPolicy(): PlaybackBufferPolicy {
    val connectivityManager = getSystemService(
        Context.CONNECTIVITY_SERVICE
    ) as? ConnectivityManager

    val capabilities = connectivityManager
        ?.activeNetwork
        ?.let(connectivityManager::getNetworkCapabilities)

    val isWifiOrEthernet = capabilities?.let {
        it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    } == true

    return if (isWifiOrEthernet) {
        PlaybackBufferPolicy(
            networkClass = "wifi_or_ethernet",
            minBufferMs = 15_000,
            maxBufferMs = 45_000,
            bufferForPlaybackMs = 1_500,
            bufferForPlaybackAfterRebufferMs = 3_000
        )
    } else {
        PlaybackBufferPolicy(
            networkClass = "other_or_unknown",
            minBufferMs = 20_000,
            maxBufferMs = 60_000,
            bufferForPlaybackMs = 2_000,
            bufferForPlaybackAfterRebufferMs = 4_000
        )
    }
}

internal class PlaybackQoeTracker(
    private val playUrl: PlayUrl
) {
    private val createdAtMs = SystemClock.elapsedRealtime()
    private var firstReadyAtMs: Long? = null
    private var bufferingStartedAtMs: Long? = null
    private var currentBufferingIsRebuffer = false
    private var hasEverBeenReady = false
    private var rebufferCount = 0
    private var totalRebufferMs = 0L
    private var lastSampleAtMs = 0L
    private var hadPlayerError = false
    private var hasFinished = false

    fun onPlaybackStateChanged(
        playbackState: Int,
        player: ExoPlayer
    ) {
        val now = SystemClock.elapsedRealtime()

        when (playbackState) {
            Player.STATE_BUFFERING -> {
                if (bufferingStartedAtMs == null) {
                    bufferingStartedAtMs = now
                    currentBufferingIsRebuffer = hasEverBeenReady
                    if (currentBufferingIsRebuffer) {
                        rebufferCount += 1
                    }
                }
            }

            Player.STATE_READY -> {
                if (!hasEverBeenReady) {
                    hasEverBeenReady = true
                    firstReadyAtMs = now
                    Log.i(
                        TAG_QOE,
                        "first ready startupMs=${now - createdAtMs}, " +
                            baseFields(player)
                    )
                }

                closeBufferingWindow(now)
            }

            Player.STATE_ENDED -> {
                closeBufferingWindow(now)
                finish(player)
            }
        }
    }

    fun onPlayerError(
        error: PlaybackException,
        player: ExoPlayer,
        penalizeCdn: Boolean = true
    ) {
        if (penalizeCdn) {
            hadPlayerError = true
        }
        closeBufferingWindow(SystemClock.elapsedRealtime())
        Log.e(
            TAG_QOE,
            "player error code=${error.errorCodeName}, penalizeCdn=$penalizeCdn, " +
                "rebufferCount=$rebufferCount, " +
                "totalRebufferMs=$totalRebufferMs, " +
                baseFields(player),
            error
        )
    }

    fun sample(player: ExoPlayer) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSampleAtMs < SAMPLE_INTERVAL_MS) {
            return
        }
        lastSampleAtMs = now

        val bufferedDurationMs = (
            player.bufferedPosition - player.currentPosition
        ).coerceAtLeast(0L)

        Log.i(
            TAG_QOE,
            "sample state=${player.playbackState.asName()}, " +
                "playing=${player.isPlaying}, " +
                "bufferedDurationMs=$bufferedDurationMs, " +
                "rebufferCount=$rebufferCount, " +
                "totalRebufferMs=$totalRebufferMs, " +
                baseFields(player)
        )
    }

    fun finish(player: ExoPlayer) {
        if (hasFinished) return
        hasFinished = true

        closeBufferingWindow(SystemClock.elapsedRealtime())

        val startupMs = firstReadyAtMs?.minus(createdAtMs) ?: -1L
        Log.i(
            TAG_QOE,
            "session finish startupMs=$startupMs, " +
                "rebufferCount=$rebufferCount, " +
                "totalRebufferMs=$totalRebufferMs, " +
                baseFields(player)
        )
        BilibiliCdnPreference.recordPlaybackQoe(
            videoHost = playUrl.videoUrl.hostOnlyForQoe(),
            audioHost = playUrl.audioUrl.orEmpty().hostOnlyForQoe(),
            startupMs = startupMs,
            rebufferCount = rebufferCount,
            totalRebufferMs = totalRebufferMs,
            hadPlayerError = hadPlayerError
        )
    }

    private fun closeBufferingWindow(now: Long) {
        val startedAt = bufferingStartedAtMs ?: return

        if (currentBufferingIsRebuffer) {
            totalRebufferMs += (now - startedAt).coerceAtLeast(0L)
        }

        bufferingStartedAtMs = null
        currentBufferingIsRebuffer = false
    }

    private fun baseFields(player: ExoPlayer): String {
        return "positionMs=${player.currentPosition.coerceAtLeast(0L)}, " +
            "bufferedPositionMs=${player.bufferedPosition.coerceAtLeast(0L)}, " +
            "qn=${playUrl.quality?.qn ?: 0}, " +
            "codec=${playUrl.videoCodec.orEmpty()}, " +
            "cdn=${playUrl.videoUrl.hostOnlyForQoe()}, " +
            "candidate=${playUrl.selectedVideoUrlIndex + 1}/" +
            "${playUrl.videoUrlCandidates.size.coerceAtLeast(1)}"
    }

    private companion object {
        const val TAG_QOE = "BiliPlaybackQoE"
        const val SAMPLE_INTERVAL_MS = 5_000L
    }
}

private fun String.hostOnlyForQoe(): String {
    if (isBlank()) return ""
    return runCatching {
        URI(this).host.orEmpty()
    }.getOrDefault("")
}

private fun Int.asName(): String {
    return when (this) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> toString()
    }
}
