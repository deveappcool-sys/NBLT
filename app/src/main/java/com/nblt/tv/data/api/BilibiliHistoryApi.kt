package com.nblt.tv.data.api

import android.util.Log
import com.nblt.tv.model.VideoItem
import com.nblt.tv.util.FormatUtils
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class BilibiliHistoryApi(
    private val client: BilibiliApiClient = BilibiliApiClient
) {
    fun fetchHistoryVideos(
        cookieHeader: String,
        pageSize: Int = 30
    ): List<VideoItem> {
        return fetchHistoryPage(cookieHeader, pageSize).videos
    }

    fun fetchHistoryPage(
        cookieHeader: String,
        pageSize: Int = 30,
        cursorMax: Long = 0L,
        cursorViewAt: Long = 0L
    ): HistoryPage {
        Log.i(
            TAG,
            "Cookie status: hasCookie=${cookieHeader.isNotBlank()}, " +
                "hasSESSDATA=${cookieHeader.contains("SESSDATA=")}, " +
                "hasDedeUserID=${cookieHeader.contains("DedeUserID=")}, " +
                "hasBiliJct=${cookieHeader.contains("bili_jct=")}"
        )

        val cursorQuery = if (cursorMax > 0L || cursorViewAt > 0L) {
            "&max=$cursorMax&view_at=$cursorViewAt"
        } else {
            ""
        }
        val primaryUrl = "https://api.bilibili.com/x/web-interface/history/cursor?ps=$pageSize$cursorQuery"
        val primaryResult = requestHistory(primaryUrl, cookieHeader)
        if (primaryResult.rawCount > 0 || primaryResult.videos.isNotEmpty()) {
            return primaryResult.toPage()
        }

        Log.w(TAG, "Primary history request returned empty list, fallback to business=archive")
        val fallbackUrl = "https://api.bilibili.com/x/web-interface/history/cursor?ps=$pageSize$cursorQuery&business=archive"
        return requestHistory(fallbackUrl, cookieHeader).toPage()
    }

    private fun requestHistory(
        url: String,
        cookieHeader: String
    ): HistoryParseResult {
        Log.i(TAG, "Request URL: $url")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BilibiliApiClient.USER_AGENT)
            .header("Referer", BilibiliApiClient.REFERER)
            .header("Origin", BilibiliApiClient.ORIGIN)
            .header("Cookie", cookieHeader)
            .get()
            .build()

        client.httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(TAG, "History HTTP error: ${response.code}")
                error("HTTP ${response.code}")
            }

            val root = JSONObject(response.body?.string().orEmpty())
            val code = root.optInt("code", -1)
            val message = root.optString("message")
            val data = root.optJSONObject("data")
            val list = findHistoryList(data)
            val rawCount = list?.length() ?: 0
            Log.i(
                TAG,
                "History response: code=$code, message=$message, " +
                    "hasData=${data != null}, hasList=${list != null}, rawCount=$rawCount"
            )
            Log.d(TAG, "History data preview: ${data?.toString()?.take(2000).orEmpty()}")

            SessionExpiredException.throwIfExpired(code)
            if (code != 0) {
                error("接口返回错误码 $code：${message.ifBlank { "History list error" }}")
            }

            if (list == null) {
                Log.w(TAG, "History list is missing. data keys may have changed.")
                return HistoryParseResult(rawCount = 0, videos = emptyList(), cursorMax = 0L, cursorViewAt = 0L, hasMore = false)
            }

            var skippedNonVideo = 0
            var skippedInvalid = 0
            val videos = buildList {
                for (index in 0 until list.length()) {
                    val item = list.optJSONObject(index)
                    if (item == null) {
                        skippedInvalid += 1
                        continue
                    }
                    val parsed = parseHistoryVideo(item, size)
                    if (parsed == null) {
                        val business = item.optJSONObject("history")?.optString("business").orEmpty()
                        if (business.isNotBlank() && business != "archive") {
                            skippedNonVideo += 1
                        } else {
                            skippedInvalid += 1
                        }
                    } else {
                        add(parsed)
                    }
                }
            }

            Log.i(
                TAG,
                "Parsed history videos: parsed=${videos.size}, " +
                    "skippedNonVideo=$skippedNonVideo, skippedInvalid=$skippedInvalid"
            )
            if (videos.isEmpty()) {
                val reason = if (rawCount == 0) {
                    "server returned empty list"
                } else {
                    "all items non-video or missing required fields"
                }
                Log.w(TAG, "History parsed empty. rawCount=$rawCount, reason=$reason")
            }
            val cursor = data?.optJSONObject("cursor")
            return HistoryParseResult(
                rawCount = rawCount,
                videos = videos,
                cursorMax = cursor?.optLong("max") ?: 0L,
                cursorViewAt = cursor?.optLong("view_at") ?: 0L,
                hasMore = rawCount > 0 && cursor?.optLong("max") != 0L
            )
        }
    }

    private fun findHistoryList(data: JSONObject?): JSONArray? {
        return data?.optJSONArray("list")
            ?: data?.optJSONArray("items")
    }

    private fun parseHistoryVideo(item: JSONObject, index: Int): VideoItem? {
        val history = item.optJSONObject("history") ?: JSONObject()
        val stat = item.optJSONObject("stat")
        val business = history.optString("business")
        if (business.isNotBlank() && business != "archive") {
            return null
        }

        val aid = history.optLong("oid", item.optLong("aid", item.optLong("id")))
        val bvid = history.optString("bvid").ifBlank { item.optString("bvid") }
        val title = item.optString("title").ifBlank { item.optString("name") }
        if ((aid <= 0L && bvid.isBlank()) || title.isBlank()) {
            Log.w(
                TAG,
                "Skip invalid history item: aid=$aid, bvid=$bvid, titleBlank=${title.isBlank()}, item=${item.toString().take(500)}"
            )
            return null
        }

        val duration = item.optLong("duration")
            .takeIf { it > 0L }
            ?: history.optLong("duration")
        val progress = findHistoryProgress(item, history)
        Log.i(
            TAG,
            "History item: title=$title, bvid=$bvid, cid=${history.optLong("cid", item.optLong("cid"))}, " +
                "duration=$duration, progress=$progress"
        )

        return VideoItem(
            aid = aid,
            bvid = bvid,
            cid = history.optLong("cid", item.optLong("cid")),
            coverUrl = findCoverUrl(item),
            title = title,
            ownerName = findOwnerName(item),
            playCount = BilibiliDisplayCountParser.firstPositive(
                stat?.opt("view"),
                stat?.opt("play"),
                item.opt("view"),
                item.opt("view_count"),
                item.opt("play"),
                item.opt("play_count")
            ),
            duration = duration,
            description = item.optString("tag_name").ifBlank { item.optString("desc") }.ifBlank { title },
            accent = FormatUtils.accentFor(index),
            ownerMid = item.optLong("author_mid", item.optLong("owner_mid")),
            ownerFaceUrl = FormatUtils.normalizeImageUrl(
                item.optString("author_face").ifBlank { item.optString("owner_face") }
            ),
            historyViewAt = item.optLong("view_at", history.optLong("view_at")),
            historyProgress = progress,
            pubdate = item.optLong("pubdate", item.optLong("ctime")),
            danmakuCount = BilibiliDisplayCountParser
                .firstPositive(stat?.opt("danmaku"), item.opt("danmaku"))
                .takeIf { it > 0L }
        )
    }

    private fun findHistoryProgress(
        item: JSONObject,
        history: JSONObject
    ): Long {
        return history.optLong("progress")
            .takeIf { it > 0L }
            ?: item.optLong("progress").takeIf { it > 0L }
            ?: history.optLong("played_time").takeIf { it > 0L }
            ?: item.optLong("played_time").takeIf { it > 0L }
            ?: item.optLong("view_progress").takeIf { it > 0L }
            ?: 0L
    }

    private fun findCoverUrl(item: JSONObject): String {
        val directCover = item.optString("cover").ifBlank { item.optString("pic") }
        if (directCover.isNotBlank()) {
            return FormatUtils.normalizeImageUrl(directCover)
        }

        val covers = item.optJSONArray("covers")
        return FormatUtils.normalizeImageUrl(covers?.optString(0).orEmpty())
    }

    private fun findOwnerName(item: JSONObject): String {
        return item.optString("author_name")
            .ifBlank { item.optString("owner_name") }
            .ifBlank { item.optJSONObject("owner")?.optString("name").orEmpty() }
            .ifBlank { "Bilibili" }
    }

    private data class HistoryParseResult(
        val rawCount: Int,
        val videos: List<VideoItem>,
        val cursorMax: Long,
        val cursorViewAt: Long,
        val hasMore: Boolean
    ) {
        fun toPage(): HistoryPage {
            return HistoryPage(
                videos = videos,
                cursorMax = cursorMax,
                cursorViewAt = cursorViewAt,
                hasMore = hasMore
            )
        }
    }

    private companion object {
        const val TAG = "BiliHistory"
    }
}

data class HistoryPage(
    val videos: List<VideoItem>,
    val cursorMax: Long,
    val cursorViewAt: Long,
    val hasMore: Boolean
)
