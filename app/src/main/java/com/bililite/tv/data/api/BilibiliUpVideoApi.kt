package com.bililite.tv.data.api

import android.util.Log
import com.bililite.tv.model.VideoItem
import com.bililite.tv.util.FormatUtils
import okhttp3.Request
import org.json.JSONObject

class BilibiliUpVideoApi(
    private val client: BilibiliApiClient = BilibiliApiClient
) {
    fun fetchUpVideos(
        mid: Long,
        upName: String,
        cookieHeader: String,
        pageNumber: Int = 1,
        pageSize: Int = 30
    ): List<VideoItem> {
        val url = "https://api.bilibili.com/x/space/arc/search" +
            "?mid=$mid&vmid=$mid&pn=$pageNumber&ps=$pageSize&tid=0&keyword=&order=pubdate"
        Log.i(TAG, "selected up name=$upName, selected mid=$mid, request URL=$url")

        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", BilibiliApiClient.USER_AGENT)
            .header("Referer", "https://space.bilibili.com/$mid/video")
            .header("Origin", BilibiliApiClient.ORIGIN)
            .header("Accept", "application/json, text/plain, */*")
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .get()

        if (cookieHeader.isNotBlank()) {
            requestBuilder.header("Cookie", cookieHeader)
        }

        client.httpClient.newCall(requestBuilder.build()).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Log.e(TAG, "Up videos HTTP error: ${response.code}, error body preview=${body.take(1000)}")
                error("HTTP ${response.code}")
            }

            val root = JSONObject(body)
            val code = root.optInt("code", -1)
            val message = root.optString("message")
            Log.i(TAG, "response code=$code, message=$message, error body preview=${body.take(500)}")
            SessionExpiredException.throwIfExpired(code)
            if (code != 0) {
                if (message.contains("频繁") || message.contains("太快")) {
                    error("请求太频繁，请稍后再试")
                } else {
                    error(message.ifBlank { "UP video list error" })
                }
            }

            val data = root.optJSONObject("data")
            val list = data
                ?.optJSONObject("list")
                ?.optJSONArray("vlist")
                ?: data?.optJSONArray("list")
            val rawCount = list?.length() ?: 0
            val videos = buildList {
                if (list != null) {
                    for (index in 0 until list.length()) {
                        val item = list.optJSONObject(index) ?: continue
                        val video = item.toVideoItem(
                            index = index,
                            ownerMid = mid,
                            ownerName = upName
                        )
                        if (video.bvid.isNotBlank() || video.aid > 0L) {
                            add(video)
                        }
                    }
                }
            }.sortedByDescending { it.pubdate }

            val first = videos.firstOrNull()
            Log.i(
                TAG,
                "raw video count=$rawCount, parsed video count=${videos.size}, " +
                    "first video title=${first?.title.orEmpty()}, first video pubdate=${first?.pubdate ?: 0L}"
            )
            if (rawCount == 0) {
                Log.w(
                    TAG,
                    "empty reason: response list missing or empty, data preview=${data?.toString()?.take(1000).orEmpty()}"
                )
            } else if (videos.isEmpty()) {
                Log.w(
                    TAG,
                    "empty reason: parse failed, first raw item=${list?.optJSONObject(0)?.toString()?.take(1000).orEmpty()}"
                )
            }
            return videos
        }
    }

    private fun JSONObject.toVideoItem(
        index: Int,
        ownerMid: Long,
        ownerName: String
    ): VideoItem {
        val title = optString("title").ifBlank { "Untitled" }
        val pubdate = optLong("created", optLong("pubdate"))
        return VideoItem(
            aid = optLong("aid", optLong("id")),
            bvid = optString("bvid"),
            cid = optLong("cid"),
            coverUrl = FormatUtils.normalizeImageUrl(optString("pic").ifBlank { optString("cover") }),
            title = title,
            ownerName = ownerName,
            playCount = optLong("play", optLong("view")),
            duration = optLong("duration").takeIf { it > 0L } ?: parseLength(optString("length")),
            description = optString("description").ifBlank { optString("desc") }.ifBlank { title },
            accent = FormatUtils.accentFor(index),
            ownerMid = ownerMid,
            pubdate = pubdate,
            danmakuCount = optLong("video_review", optLong("danmaku")).takeIf { it > 0L }
        )
    }

    private fun parseLength(length: String): Long {
        val parts = length.split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> 0L
        }
    }

    private companion object {
        const val TAG = "BiliUpVideos"
    }
}
