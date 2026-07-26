package com.bililite.tv.data.api

import android.util.Log
import com.bililite.tv.model.VideoItem
import com.bililite.tv.util.FormatUtils
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject

class BilibiliWatchLaterApi(
    private val client: BilibiliApiClient = BilibiliApiClient
) {
    fun fetchWatchLaterPage(
        page: Int,
        pageSize: Int = 20,
        cookieHeader: String
    ): WatchLaterPage {
        val url = "https://api.bilibili.com/x/v2/history/toview".toHttpUrl()
            .newBuilder()
            .addQueryParameter("pn", page.toString())
            .addQueryParameter("ps", pageSize.coerceIn(1, 20).toString())
            .addQueryParameter("add_media_ids", "0")
            .addQueryParameter("viewed", "0")
            .build()
        Log.i(TAG, "request URL: $url")
        Log.i(TAG, "page/cursor=$page")
        logCookieStatus(cookieHeader)

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BilibiliApiClient.USER_AGENT)
            .header("Referer", BilibiliApiClient.REFERER)
            .header("Origin", BilibiliApiClient.ORIGIN)
            .header("Cookie", cookieHeader)
            .get()
            .build()

        client.httpClient.newCall(request).execute().use { response ->
            Log.i(TAG, "response code=${response.code}")
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }
            val root = JSONObject(response.body?.string().orEmpty())
            val code = root.optInt("code", -1)
            val message = root.optString("message")
            SessionExpiredException.throwIfExpired(code)
            if (code != 0) {
                error(message.ifBlank { "\u7a0d\u540e\u518d\u770b\u52a0\u8f7d\u5931\u8d25\uff08$code\uff09" })
            }
            val data = root.optJSONObject("data")
            val list = data?.optJSONArray("list")
            val totalCount = data?.optInt("count") ?: list?.length() ?: 0
            Log.i(TAG, "video count=$totalCount")

            var skipped = 0
            val videos = buildList {
                if (list != null) {
                    for (index in 0 until list.length()) {
                        val item = list.optJSONObject(index) ?: continue
                        val parsed = parseWatchLaterVideo(item, index)
                        if (parsed == null) {
                            skipped += 1
                        } else {
                            add(parsed)
                        }
                    }
                }
            }
            Log.i(TAG, "parsed count=${videos.size}, skipped=$skipped")
            val rawCount = list?.length() ?: 0
            val hasMore = rawCount >= pageSize && (page * pageSize) < totalCount.coerceAtLeast(videos.size)
            return WatchLaterPage(
                videos = videos,
                page = page,
                hasMore = hasMore
            )
        }
    }

    private fun parseWatchLaterVideo(item: JSONObject, index: Int): VideoItem? {
        val aid = item.optLong("aid", item.optLong("id"))
        val bvid = item.optString("bvid")
        val title = item.optString("title")
        if ((aid <= 0L && bvid.isBlank()) || title.isBlank()) {
            return null
        }
        val owner = item.optJSONObject("owner")
        val stat = item.optJSONObject("stat")
        return VideoItem(
            aid = aid,
            bvid = bvid,
            cid = item.optLong("cid"),
            coverUrl = FormatUtils.normalizeImageUrl(item.optString("pic").ifBlank { item.optString("cover") }),
            title = title,
            ownerName = owner?.optString("name").orEmpty().ifBlank { "Bilibili" },
            playCount = stat?.optLong("view") ?: item.optLong("view"),
            duration = item.optLong("duration"),
            description = item.optString("desc").ifBlank { title },
            accent = FormatUtils.accentFor(index),
            ownerMid = owner?.optLong("mid") ?: 0L
        )
    }

    private fun logCookieStatus(cookieHeader: String) {
        Log.i(
            TAG,
            "cookie status: hasCookie=${cookieHeader.isNotBlank()}, " +
                "hasSESSDATA=${cookieHeader.contains("SESSDATA=")}"
        )
    }

    private companion object {
        const val TAG = "BiliWatchLater"
    }
}

data class WatchLaterPage(
    val videos: List<VideoItem>,
    val page: Int,
    val hasMore: Boolean
)
