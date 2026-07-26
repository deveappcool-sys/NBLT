package com.nblt.tv.data.api

import com.nblt.tv.model.VideoItem
import com.nblt.tv.data.repository.DynamicVideoPage
import com.nblt.tv.util.FormatUtils
import okhttp3.Request
import org.json.JSONObject

class BilibiliDynamicApi(
    private val client: BilibiliApiClient = BilibiliApiClient
) {
    fun fetchVideoDynamics(
        cookieHeader: String,
        offset: String? = null
    ): DynamicVideoPage {
        val offsetQuery = offset?.takeIf { it.isNotBlank() }?.let { "&offset=$it" }.orEmpty()
        val request = Request.Builder()
            .url("https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/all?type=video&page=1$offsetQuery")
            .header("User-Agent", BilibiliApiClient.USER_AGENT)
            .header("Referer", BilibiliApiClient.REFERER)
            .header("Origin", BilibiliApiClient.ORIGIN)
            .header("Cookie", cookieHeader)
            .get()
            .build()

        client.httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }

            val root = JSONObject(response.body?.string().orEmpty())
            val code = root.optInt("code", -1)
            SessionExpiredException.throwIfExpired(code)
            if (code != 0) {
                error(root.optString("message", "Dynamic feed error"))
            }

            val data = root.optJSONObject("data")
            val items = data?.optJSONArray("items") ?: return DynamicVideoPage(
                videos = emptyList(),
                offset = data?.optString("offset")?.takeIf { it.isNotBlank() },
                hasMore = false
            )
            val videos = buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    parseDynamicVideo(item, size)?.let(::add)
                }
            }
            return DynamicVideoPage(
                videos = videos,
                offset = data.optString("offset").takeIf { it.isNotBlank() },
                hasMore = data.optBoolean("has_more", videos.isNotEmpty())
            )
        }
    }

    private fun parseDynamicVideo(item: JSONObject, index: Int): VideoItem? {
        val modules = item.optJSONObject("modules") ?: return null
        val author = modules.optJSONObject("module_author")
        val major = modules.optJSONObject("module_dynamic")
            ?.optJSONObject("major")
        val archive = major?.optJSONObject("archive") ?: return null

        val aid = archive.optLong("aid")
        val bvid = archive.optString("bvid")
        val title = archive.optString("title")
        if (aid <= 0L && bvid.isBlank() || title.isBlank()) {
            return null
        }

        val stat = archive.optJSONObject("stat")
        val ownerMid = author?.optLong("mid") ?: archive.optLong("mid")
        val ownerName = author?.optString("name").orEmpty().ifBlank { archive.optString("owner_name") }.ifBlank { "Bilibili" }

        return VideoItem(
            aid = aid,
            bvid = bvid,
            cid = archive.optLong("cid"),
            coverUrl = FormatUtils.normalizeImageUrl(archive.optString("cover").ifBlank { archive.optString("pic") }),
            title = title,
            ownerName = ownerName,
            playCount = BilibiliDisplayCountParser.firstPositive(
                stat?.opt("play"),
                stat?.opt("view"),
                archive.opt("play"),
                archive.opt("view")
            ),
            duration = parseDurationSeconds(archive.optString("duration_text").ifBlank { archive.optString("duration") }),
            description = archive.optString("desc").ifBlank { title },
            accent = FormatUtils.accentFor(index),
            ownerMid = ownerMid,
            ownerFaceUrl = FormatUtils.normalizeImageUrl(author?.optString("face").orEmpty()),
            pubdate = author?.optLong("pub_ts") ?: 0L,
            danmakuCount = BilibiliDisplayCountParser
                .firstPositive(stat?.opt("danmaku"), archive.opt("danmaku"))
                .takeIf { it > 0L }
        )
    }

    private fun parseDurationSeconds(text: String): Long {
        val parts = text.split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> 0L
        }
    }
}
