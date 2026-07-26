package com.nblt.tv.data.api

import android.util.Log
import com.nblt.tv.model.SearchSuggestion
import com.nblt.tv.model.VideoItem
import com.nblt.tv.util.FormatUtils
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class BilibiliSearchApi(
    private val client: BilibiliApiClient = BilibiliApiClient,
    private val wbiSigner: WbiSigner = WbiSigner()
) {
    fun fetchSuggestions(
        keyword: String,
        cookieHeader: String = ""
    ): List<SearchSuggestion> {
        val encoded = keyword.urlEncode()
        val url = if (keyword.isBlank()) {
            "https://api.bilibili.com/x/web-interface/search/square?limit=10"
        } else {
            "https://s.search.bilibili.com/main/suggest?term=$encoded&main_ver=v1"
        }
        Log.i(TAG, "input keyword=$keyword, suggest request URL=$url")

        val root = JSONObject(executeGet(url, cookieHeader))
        val suggestions = if (keyword.isBlank()) {
            parseHotSuggestions(root)
        } else {
            parseKeywordSuggestions(root)
        }
        Log.i(TAG, "suggestion count=${suggestions.size}")
        return suggestions
    }

    fun searchVideos(
        keyword: String,
        page: Int = 1,
        pageSize: Int = 20,
        cookieHeader: String = ""
    ): List<VideoItem> {
        val baseParams = linkedMapOf(
            "search_type" to "video",
            "keyword" to keyword,
            "page" to page.toString(),
            "page_size" to pageSize.toString()
        )
        val signedParams = wbiSigner.signParams(baseParams, cookieHeader)
        val signedUrl = WBI_SEARCH_URL.toHttpUrl().newBuilder()
        signedParams.forEach { (key, value) ->
            signedUrl.addQueryParameter(key, value)
        }
        val url = signedUrl.build().toString()
        Log.i(TAG, "search keyword=$keyword, search page=$page, WBI signed request URL")
        Log.i(TAG, "WBI params: wts=${signedParams["wts"]}, w_rid=${signedParams["w_rid"]}")

        val root = JSONObject(executeGet(url, cookieHeader))
        val code = root.optInt("code", -1)
        if (code != 0) {
            error(root.optString("message", "Search error"))
        }

        val result = root.optJSONObject("data")?.optJSONArray("result") ?: JSONArray()
        val videos = buildList {
            for (index in 0 until result.length()) {
                val item = result.optJSONObject(index) ?: continue
                parseSearchVideo(item, index)?.let(::add)
            }
        }
        Log.i(TAG, "raw result count=${result.length()}, parsed video count=${videos.size}")
        return videos
    }

    private fun executeGet(
        url: String,
        cookieHeader: String
    ): String {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", BilibiliApiClient.USER_AGENT)
            .header("Referer", "https://search.bilibili.com/")
            .header("Origin", BilibiliApiClient.ORIGIN)
            .header("Accept", "application/json, text/plain, */*")
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .get()

        if (cookieHeader.isNotBlank()) {
            builder.header("Cookie", cookieHeader)
        }

        client.httpClient.newCall(builder.build()).execute().use { response ->
            Log.i(TAG, "HTTP code=${response.code}, response message=${response.message}")
            val body = response.body?.string().orEmpty()
            if (response.code == 412) {
                error("搜索请求被限制，请稍后再试")
            }
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }
            return body
        }
    }

    private fun parseHotSuggestions(root: JSONObject): List<SearchSuggestion> {
        val list = root.optJSONObject("data")
            ?.optJSONObject("trending")
            ?.optJSONArray("list")
            ?: root.optJSONObject("data")?.optJSONArray("list")
            ?: JSONArray()
        return buildList {
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                val keyword = item.optString("keyword")
                    .ifBlank { item.optString("show_name") }
                    .ifBlank { item.optString("word") }
                if (keyword.isNotBlank()) {
                    add(SearchSuggestion(keyword))
                }
            }
        }.distinctBy { it.keyword }
    }

    private fun parseKeywordSuggestions(root: JSONObject): List<SearchSuggestion> {
        val data = root.optJSONObject("result") ?: root.optJSONObject("data") ?: root
        val candidates = listOf("tag", "term", "suggest", "result")
        return buildList {
            candidates.forEach { key ->
                val array = data.optJSONArray(key) ?: return@forEach
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index)
                    val keyword = item?.optString("value")
                        ?.ifBlank { item.optString("term") }
                        ?.ifBlank { item.optString("name") }
                        ?: array.optString(index)
                    if (keyword.isNotBlank()) {
                        add(SearchSuggestion(keyword.stripHtml()))
                    }
                }
            }
        }.distinctBy { it.keyword }.take(12)
    }

    private fun parseSearchVideo(item: JSONObject, index: Int): VideoItem? {
        val title = item.optString("title").stripHtml().ifBlank { return null }
        val bvid = item.optString("bvid")
        val aid = item.optLong("aid", item.optLong("id"))
        if (bvid.isBlank() && aid <= 0L) {
            return null
        }

        return VideoItem(
            aid = aid,
            bvid = bvid,
            cid = item.optLong("cid"),
            coverUrl = FormatUtils.normalizeImageUrl(item.optString("pic").ifBlank { item.optString("cover") }),
            title = title,
            ownerName = item.optString("author").ifBlank { item.optString("owner_name") }.ifBlank { "Bilibili" },
            playCount = item.optLong("play", item.optLong("view")),
            duration = parseDuration(item.optString("duration")),
            description = item.optString("description").stripHtml().ifBlank { title },
            accent = FormatUtils.accentFor(index),
            ownerMid = item.optLong("mid", item.optLong("owner_mid")),
            pubdate = item.optLong("pubdate", item.optLong("created"))
        )
    }

    private fun parseDuration(value: String): Long {
        val parts = value.split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            1 -> parts[0]
            else -> 0L
        }
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")

    private fun String.stripHtml(): String = replace(Regex("<[^>]+>"), "")

    private companion object {
        const val TAG = "BiliSearch"
        const val WBI_SEARCH_URL = "https://api.bilibili.com/x/web-interface/wbi/search/type"
    }
}
