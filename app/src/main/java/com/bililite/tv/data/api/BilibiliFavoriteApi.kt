package com.bililite.tv.data.api

import android.util.Log
import com.bililite.tv.model.FavoriteFolder
import com.bililite.tv.model.VideoItem
import com.bililite.tv.util.FormatUtils
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject

class BilibiliFavoriteApi(
    private val client: BilibiliApiClient = BilibiliApiClient
) {
    fun fetchCreatedFolders(
        upMid: Long,
        cookieHeader: String
    ): List<FavoriteFolder> {
        val url = "https://api.bilibili.com/x/v3/fav/folder/created/list-all".toHttpUrl()
            .newBuilder()
            .addQueryParameter("up_mid", upMid.toString())
            .addQueryParameter("type", "2")
            .build()
        Log.i(TAG, "request URL: $url")
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
                error(message.ifBlank { "\u6536\u85cf\u5939\u52a0\u8f7d\u5931\u8d25\uff08$code\uff09" })
            }
            val data = root.optJSONObject("data") ?: return emptyList()
            val list = data.optJSONArray("list") ?: return emptyList()
            val folders = buildList {
                for (index in 0 until list.length()) {
                    val item = list.optJSONObject(index) ?: continue
                    val id = item.optLong("id")
                    if (id <= 0L) continue
                    add(
                        FavoriteFolder(
                            id = id,
                            title = item.optString("title").ifBlank { "\u6536\u85cf\u5939" },
                            mediaCount = item.optInt("media_count")
                        )
                    )
                }
            }
            Log.i(TAG, "folder count=${folders.size}")
            return folders
        }
    }

    fun fetchFolderVideos(
        mediaId: Long,
        page: Int,
        pageSize: Int = 20,
        cookieHeader: String
    ): FavoriteVideoPage {
        val url = "https://api.bilibili.com/x/v3/fav/resource/list".toHttpUrl()
            .newBuilder()
            .addQueryParameter("media_id", mediaId.toString())
            .addQueryParameter("platform", "web")
            .addQueryParameter("pn", page.toString())
            .addQueryParameter("ps", pageSize.coerceIn(1, 20).toString())
            .addQueryParameter("type", "0")
            .build()
        Log.i(TAG, "request URL: $url")
        Log.i(TAG, "media id=$mediaId, page number=$page")
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
                error(message.ifBlank { "\u6536\u85cf\u89c6\u9891\u52a0\u8f7d\u5931\u8d25\uff08$code\uff09" })
            }
            val data = root.optJSONObject("data")
            val medias = data?.optJSONArray("medias")
            val rawCount = medias?.length() ?: 0
            val info = data?.optJSONObject("info")
            val totalCount = info?.optInt("media_count") ?: 0
            Log.i(TAG, "video count=$totalCount")

            var skipped = 0
            val videos = buildList {
                if (medias != null) {
                    for (index in 0 until medias.length()) {
                        val item = medias.optJSONObject(index) ?: continue
                        val parsed = parseFavoriteVideo(item, index)
                        if (parsed == null) {
                            skipped += 1
                        } else {
                            add(parsed)
                        }
                    }
                }
            }
            Log.i(TAG, "parsed count=${videos.size}, skipped=$skipped")
            val hasMore = rawCount >= pageSize && (page * pageSize) < totalCount.coerceAtLeast(videos.size)
            return FavoriteVideoPage(
                videos = videos,
                page = page,
                hasMore = hasMore
            )
        }
    }

    private fun parseFavoriteVideo(item: JSONObject, index: Int): VideoItem? {
        val type = item.optInt("type")
        if (type == 12 || type == 21) {
            return null
        }
        val aid = item.optLong("id")
        val bvid = item.optString("bvid").ifBlank { item.optString("bv_id") }
        val title = item.optString("title")
        if ((aid <= 0L && bvid.isBlank()) || title.isBlank()) {
            return null
        }
        val upper = item.optJSONObject("upper")
        val cntInfo = item.optJSONObject("cnt_info")
        return VideoItem(
            aid = aid,
            bvid = bvid,
            cid = 0L,
            coverUrl = FormatUtils.normalizeImageUrl(item.optString("cover")),
            title = title,
            ownerName = upper?.optString("name").orEmpty().ifBlank { "Bilibili" },
            playCount = cntInfo?.optLong("play") ?: item.optLong("play"),
            duration = item.optLong("duration"),
            description = item.optString("intro").ifBlank { title },
            accent = FormatUtils.accentFor(index),
            ownerMid = upper?.optLong("mid") ?: 0L
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
        const val TAG = "BiliFavorite"
    }
}

data class FavoriteVideoPage(
    val videos: List<VideoItem>,
    val page: Int,
    val hasMore: Boolean
)
