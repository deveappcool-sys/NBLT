package com.nblt.tv.data.api

import android.util.Log
import com.nblt.tv.model.VideoItem
import com.nblt.tv.util.FormatUtils
import org.json.JSONObject

class BilibiliPopularApi(
    private val client: BilibiliApiClient = BilibiliApiClient
) {
    fun fetchPopularVideos(
        pageSize: Int = 20,
        pageNumber: Int = 1,
        refreshCount: Int = 0,
        forceRefresh: Boolean = false,
        cookieHeader: String = ""
    ): List<VideoItem> {
        val cacheBuster = if (forceRefresh) "&refresh_ts=${System.currentTimeMillis()}" else ""
        val request = client.request(
            "/x/web-interface/popular?ps=$pageSize&pn=$pageNumber$cacheBuster"
        )
            .newBuilder()
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

        Log.i(
            TAG_REFRESH,
            "refresh tab=热门, refresh count=$refreshCount, page=$pageNumber, " +
                "hasCookie=${cookieHeader.isNotBlank()}, " +
                "hasSessData=${cookieHeader.contains("SESSDATA=")}, " +
                "request URL=${request.url}"
        )

        client.httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }

            val root = JSONObject(response.body?.string().orEmpty())
            val code = root.optInt("code", -1)
            SessionExpiredException.throwIfExpired(code)
            if (code != 0) {
                error(root.optString("message", "Bilibili API error"))
            }

            val list = root.optJSONObject("data")
                ?.optJSONArray("list")
                ?: error("Popular list is empty")

            return buildList {
                for (index in 0 until list.length()) {
                    val item = list.optJSONObject(index) ?: continue
                    add(item.toVideoItem(index))
                }
            }
        }
    }

    private fun JSONObject.toVideoItem(index: Int): VideoItem {
        val owner = optJSONObject("owner")
        val stat = optJSONObject("stat")
        val title = optString("title").ifBlank { "Untitled" }

        return VideoItem(
            aid = optLong("aid", optLong("id", index.toLong() + 1)),
            bvid = optString("bvid"),
            cid = optLong("cid"),
            coverUrl = FormatUtils.normalizeImageUrl(optString("pic")),
            title = title,
            ownerName = owner?.optString("name").orEmpty().ifBlank { "Bilibili" },
            playCount = stat?.optLong("view") ?: optLong("view"),
            duration = optLong("duration"),
            description = optString("desc").ifBlank { title },
            accent = FormatUtils.accentFor(index),
            ownerMid = owner?.optLong("mid") ?: 0L,
            ownerFaceUrl = FormatUtils.normalizeImageUrl(owner?.optString("face").orEmpty()),
            pubdate = optLong("pubdate", optLong("ctime")),
            danmakuCount = stat?.optLong("danmaku")
        )
    }

    private companion object {
        const val TAG_REFRESH = "BiliRefresh"
    }
}
