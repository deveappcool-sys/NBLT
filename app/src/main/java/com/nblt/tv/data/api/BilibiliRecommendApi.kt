package com.nblt.tv.data.api

import android.util.Log
import com.nblt.tv.model.VideoItem
import com.nblt.tv.util.FormatUtils
import org.json.JSONObject

class BilibiliRecommendApi(
    private val client: BilibiliApiClient = BilibiliApiClient
) {
    fun fetchRecommendVideos(
        pageSize: Int = 20,
        refreshCount: Int = 0,
        forceRefresh: Boolean = false,
        cookieHeader: String = "",
        isLoggedIn: Boolean = false
    ): List<VideoItem> {
        val freshIndex = (refreshCount + 1).coerceAtLeast(1)
        val brush = refreshCount.coerceAtLeast(0)
        val cacheBuster = if (forceRefresh) "&refresh_ts=${System.currentTimeMillis()}" else ""
        val request = client.request(
            "/x/web-interface/index/top/feed/rcmd" +
                "?fresh_idx=$freshIndex&fresh_idx_1h=$freshIndex&feed_version=V2" +
                "&fresh_type=4&ps=$pageSize&plat=1&brush=$brush&homepage_ver=1" +
                "&screen=2560-1440&web_location=1430650$cacheBuster"
        ).newBuilder()
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .apply {
                if (cookieHeader.isNotBlank()) {
                    header("Cookie", cookieHeader)
                }
            }
            .build()

        Log.i(TAG_REFRESH, "refresh tab=推荐, refresh count=$refreshCount, request URL=${request.url}")
        Log.i(
            TAG_DEBUG,
            "request URL=${request.url}, hasCookie=${cookieHeader.isNotBlank()}, " +
                "hasSessData=${cookieHeader.hasCookieKey("SESSDATA")}, " +
                "hasDedeUserId=${cookieHeader.hasCookieKey("DedeUserID")}, " +
                "hasBiliJct=${cookieHeader.hasCookieKey("bili_jct")}, " +
                "hasBuvid=${cookieHeader.hasAnyCookieKey("buvid3", "buvid4", "b_nut")}, " +
                "User-Agent=${BilibiliApiClient.USER_AGENT}, Referer=${BilibiliApiClient.REFERER}, " +
                "currentUser != null=$isLoggedIn"
        )

        client.httpClient.newCall(request).execute().use { response ->
            Log.i(TAG_DEBUG, "HTTP status=${response.code}")
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }

            val root = JSONObject(response.body?.string().orEmpty())
            val code = root.optInt("code", -1)
            val message = root.optString("message")
            Log.i(TAG_DEBUG, "B站 code=$code, message=$message")
            SessionExpiredException.throwIfExpired(code)
            if (code != 0) {
                error(message.ifBlank { "Bilibili API error" })
            }

            val items = root.optJSONObject("data")
                ?.optJSONArray("item")
                ?: error("Recommend list is empty")

            return buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    if (item.optString("goto") != "av" && item.optString("bvid").isBlank()) {
                        continue
                    }
                    add(item.toVideoItem(size))
                }
            }.also { videos ->
                Log.i(TAG_DEBUG, "recommend looks visitor=${!isLoggedIn || cookieHeader.isBlank()}")
                videos.take(10).forEachIndexed { index, video ->
                    Log.i(TAG_DEBUG, "item ${index + 1}: title=${video.title}, up=${video.ownerName}")
                }
            }
        }
    }

    private fun JSONObject.toVideoItem(index: Int): VideoItem {
        val owner = optJSONObject("owner")
        val stat = optJSONObject("stat")
        val title = optString("title").ifBlank { "Untitled" }
        val description = optString("desc")
            .ifBlank { optString("desc_button") }
            .ifBlank { title }

        return VideoItem(
            aid = optLong("id", optLong("aid", index.toLong() + 1)),
            bvid = optString("bvid"),
            cid = optLong("cid"),
            coverUrl = FormatUtils.normalizeImageUrl(optString("pic")),
            title = title,
            ownerName = owner?.optString("name").orEmpty().ifBlank { "Bilibili" },
            playCount = stat?.optLong("view") ?: optLong("view", optLong("play")),
            duration = optLong("duration"),
            description = description,
            accent = FormatUtils.accentFor(index),
            ownerMid = owner?.optLong("mid") ?: optLong("mid"),
            ownerFaceUrl = FormatUtils.normalizeImageUrl(owner?.optString("face").orEmpty()),
            pubdate = optLong("pubdate", optLong("ctime")),
            danmakuCount = stat?.optLong("danmaku")
        )
    }

    private companion object {
        const val TAG_REFRESH = "BiliRefresh"
        const val TAG_DEBUG = "BiliRecommendDebug"
    }
}

private fun String.hasCookieKey(key: String): Boolean {
    return split(";").any { it.trim().startsWith("$key=") }
}

private fun String.hasAnyCookieKey(vararg keys: String): Boolean {
    return keys.any(::hasCookieKey)
}
