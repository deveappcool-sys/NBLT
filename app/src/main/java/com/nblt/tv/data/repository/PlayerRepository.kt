package com.nblt.tv.data.repository

import android.util.Log
import com.nblt.tv.data.api.BilibiliApiClient
import com.nblt.tv.data.api.BilibiliPlayUrlApi
import com.nblt.tv.data.api.BilibiliVideoDetailApi
import com.nblt.tv.model.PlaybackProfile
import com.nblt.tv.model.PlayUrlResult
import com.nblt.tv.model.VideoItem
import com.nblt.tv.storage.CookieStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

class PlayerRepository(
    private val detailApi: BilibiliVideoDetailApi = BilibiliVideoDetailApi(),
    private val playUrlApi: BilibiliPlayUrlApi = BilibiliPlayUrlApi(),
    private val cookieStorage: CookieStorage? = null
) {
    suspend fun getPlayUrl(
        video: VideoItem,
        preferredQualityQn: Int = 0,
        avoidedHosts: Set<String> = emptySet(),
        playbackProfile: PlaybackProfile = PlaybackProfile.WEB_CHROME_WINDOWS,
        enableMediaPreconnect: Boolean = false
    ): Result<PlayUrlResult> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val needsDetailFetch = video.cid <= 0 || video.pages.isEmpty()
                val videoWithCid = if (needsDetailFetch) {
                    detailApi.fetchVideoWithCid(video)
                } else {
                    video
                }
                if (videoWithCid.pages.isNotEmpty()) {
                    Log.i(TAG_PAGES, "playUrl loaded pages count=${videoWithCid.pages.size}")
                }
                Log.i(
                    TAG_QUALITY,
                    "user preferred qn=$preferredQualityQn, bvid=${video.bvid}, aid=${video.aid}, cid=${videoWithCid.cid}"
                )
                val cookieHeader = cookieStorage?.getCookieHeader().orEmpty()
                val playUrl = playUrlApi.fetchPlayUrl(
                    video = videoWithCid,
                    preferredQn = preferredQualityQn,
                    cookieHeader = cookieHeader,
                    avoidedHosts = avoidedHosts,
                    playbackProfile = playbackProfile
                )
                if (enableMediaPreconnect) {
                    BilibiliApiClient.startMediaPreconnect(playUrl)
                }
                Log.i(
                    TAG_PLAYER,
                    "refresh play url success: bvid=${video.bvid}, aid=${video.aid}, cid=${videoWithCid.cid}"
                )
                Log.i(
                    TAG_DEBUG,
                    "play source selected: bvid=${videoWithCid.bvid}, aid=${videoWithCid.aid}, " +
                        "cid=${videoWithCid.cid}, requested qn=${playUrl.requestedQn}, " +
                        "selected qn=${playUrl.quality?.qn ?: 0}, " +
                        "selected format=${playUrl.selectedFormat.orEmpty()}, " +
                        "selected codec=${playUrl.videoCodec.orEmpty()}, " +
                        "selected url type=${playUrl.sourceType}, " +
                        "video url host=${playUrl.videoUrl.hostOnly()}, " +
                        "video backupUrl count=${(playUrl.videoUrlCandidates.size - 1).coerceAtLeast(0)}, " +
                        "audio url host=${playUrl.audioUrl.orEmpty().hostOnly()}, " +
                        "audio backupUrl count=${(playUrl.audioUrlCandidates.size - 1).coerceAtLeast(0)}, " +
                        "url has expires/deadline-like params=${playUrl.hasExpiringUrlParams}, " +
                        "estimatedUrlExpireTime=${playUrl.estimatedExpireTimeSeconds ?: 0}, " +
                        "failedHosts=${avoidedHosts.joinToString(prefix = "[", postfix = "]")}, " +
                        "hasCookie=${cookieHeader.isNotBlank()}, " +
                        "hasSessData=${cookieHeader.contains("SESSDATA")}, " +
                        "hasReferer=${playUrl.referer.isNotBlank()}, " +
                        "playbackProfile=${playUrl.playbackProfile.name}, ua=${playUrl.playbackProfile.shortName}"
                )
                PlayUrlResult(
                    playUrl = playUrl,
                    updatedVideo = if (needsDetailFetch) videoWithCid else null
                )
            }
        }
    }

    private companion object {
        const val TAG = "PlayerRepository"
        const val TAG_PLAYER = "BiliPlayer"
        const val TAG_DEBUG = "BiliPlayerDebug"
        const val TAG_QUALITY = "BiliQuality"
        const val TAG_PAGES = "BiliPages"
    }
}

private fun String.hostOnly(): String {
    if (isBlank()) return ""
    return runCatching { URI(this).host.orEmpty() }.getOrDefault("")
}
