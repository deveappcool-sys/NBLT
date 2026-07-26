package com.bililite.tv.data.api

import android.util.Log
import com.bililite.tv.data.repository.UpSpaceVideoErrors
import com.bililite.tv.model.UpProfile
import com.bililite.tv.model.UpVideoItem
import com.bililite.tv.model.UpVideoPage
import com.bililite.tv.util.FormatUtils
import com.bililite.tv.util.UpSpaceDebugLog
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

class BilibiliUserApi(
    private val wbiSigner: WbiSigner = WbiSigner()
) {
    fun fetchUserProfile(
        mid: Long,
        cookieHeader: String
    ): UpProfile {
        val url = "https://api.bilibili.com/x/web-interface/card".toHttpUrl()
            .newBuilder()
            .addQueryParameter("mid", mid.toString())
            .build()
        Log.i(TAG, "profile request URL: $url")

        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", BilibiliApiClient.USER_AGENT)
            .header("Referer", "https://space.bilibili.com/$mid")
            .header("Origin", BilibiliApiClient.ORIGIN)
            .get()
        if (cookieHeader.isNotBlank()) {
            requestBuilder.header("Cookie", cookieHeader)
        }

        BilibiliApiClient.httpClient.newCall(requestBuilder.build()).execute().use { response ->
            Log.i(TAG, "profile response code=${response.code}")
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }
            val root = JSONObject(response.body?.string().orEmpty())
            val code = root.optInt("code", -1)
            val message = root.optString("message")
            SessionExpiredException.throwIfExpired(code)
            if (code != 0) {
                error(message.ifBlank { "\u7528\u6237\u8d44\u6599\u52a0\u8f7d\u5931\u8d25\uff08$code\uff09" })
            }
            val data = root.optJSONObject("data") ?: error("\u7528\u6237\u8d44\u6599\u4e3a\u7a7a")
            val card = data.optJSONObject("card") ?: error("\u7528\u6237\u5361\u7247\u4e3a\u7a7a")
            val nickname = card.optString("name").ifBlank { "\u7528\u6237$mid" }
            Log.i(TAG, "profile parsed nickname=$nickname")
            return UpProfile(
                mid = card.optLong("mid", mid).takeIf { it > 0L } ?: mid,
                nickname = nickname,
                avatarUrl = FormatUtils.normalizeImageUrl(card.optString("face")),
                sign = card.optString("sign"),
                followerCount = card.optLong("fans"),
                videoCount = data.optInt(
                    "archive_count",
                    card.optInt("archive_count")
                )
            )
        }
    }

    fun fetchUpVideos(
        mid: Long,
        upName: String,
        page: Int,
        pageSize: Int = 30,
        cookieHeader: String
    ): UpVideoPage {
        val order = "pubdate"
        val coercedPageSize = pageSize.coerceIn(1, 30)
        val baseParams = buildWbiArcSearchParams(mid, page, coercedPageSize, order)
        val signedParams = wbiSigner.signParams(
            params = baseParams,
            cookieHeader = cookieHeader
        )
        val signedUrlBuilder = WBI_ARC_SEARCH_URL.toHttpUrl().newBuilder()
        signedParams.forEach { (key, value) ->
            signedUrlBuilder.addQueryParameter(key, value)
        }
        val signedUrl = signedUrlBuilder.build()
        val userAgent = BilibiliApiClient.USER_AGENT
        val origin = UP_SPACE_ORIGIN
        val referer = upSpaceReferer(mid)
        val hasCookie = cookieHeader.isNotBlank()
        val signedParamKeys = signedParams.keys.sorted()
        UpSpaceDebugLog.logBeforeVideoRequest(
            endpoint = WBI_ARC_SEARCH_ENDPOINT,
            mid = mid,
            page = page,
            pn = page,
            ps = coercedPageSize,
            order = order,
            requestUrl = signedUrl.toString(),
            hasWbi = true,
            hasCookie = hasCookie,
            hasWebLocation = signedParams.containsKey("web_location"),
            hasDmImgParams = signedParams.containsKey("dm_img_list") &&
                signedParams.containsKey("dm_img_str") &&
                signedParams.containsKey("dm_cover_img_str"),
            hasReferer = true,
            hasOrigin = true,
            signedParamKeys = signedParamKeys,
            userAgent = userAgent
        )
        Log.i(TAG, "video request URL: $signedUrl")
        Log.i(TAG, "video page number=$page")

        val requestBuilder = Request.Builder()
            .url(signedUrl)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .header("Origin", origin)
            .header("Referer", referer)
            .get()
        if (hasCookie) {
            requestBuilder.header("Cookie", cookieHeader)
        }

        return executeUpVideoRequest(
            response = BilibiliApiClient.httpClient.newCall(requestBuilder.build()).execute(),
            page = page,
            pageSize = pageSize,
            mid = mid
        )
    }

    private fun buildWbiArcSearchParams(
        mid: Long,
        page: Int,
        pageSize: Int,
        order: String
    ): LinkedHashMap<String, String> {
        return linkedMapOf(
            "mid" to mid.toString(),
            "pn" to page.toString(),
            "ps" to pageSize.toString(),
            "tid" to "0",
            "keyword" to "",
            "order" to order,
            "platform" to "web",
            "web_location" to "1550101",
            "order_avoided" to "true",
            "dm_img_list" to "[]",
            "dm_img_str" to "",
            "dm_cover_img_str" to ""
        )
    }

    private fun executeUpVideoRequest(
        response: Response,
        page: Int,
        pageSize: Int,
        mid: Long
    ): UpVideoPage {
        response.use { httpResponse ->
            val httpStatus = httpResponse.code
            val body = httpResponse.body?.string().orEmpty()
            val root = JSONObject(body)
            val code = root.optInt("code", -1)
            val message = root.optString("message")
            val sessionExpiredByCode = UpSpaceDebugLog.isSessionExpiredCode(code)
            val rateLimitByMessage = UpSpaceDebugLog.isRateLimitMessage(message)
            val mappedToRateLimitMessage = code != 0 && rateLimitByMessage
            UpSpaceDebugLog.logAfterVideoResponse(
                httpStatus = httpStatus,
                bilibiliCode = code,
                bilibiliMessage = message,
                bodyPreview = body
            )
            UpSpaceDebugLog.logResponseClassification(
                bilibiliCode = code,
                bilibiliMessage = message,
                sessionExpiredRecognized = sessionExpiredByCode,
                rateLimitRecognized = rateLimitByMessage,
                mappedToRateLimitMessage = mappedToRateLimitMessage
            )
            Log.i(TAG, "video response code=$httpStatus")
            if (!httpResponse.isSuccessful) {
                error("HTTP ${httpResponse.code}")
            }
            SessionExpiredException.throwIfExpired(code)
            if (code != 0) {
                error(UpSpaceVideoErrors.mapUpVideoApiError(code, message))
            }

            return try {
                parseUpVideoPage(root, page, pageSize, mid)
            } catch (_: Exception) {
                error(PARSE_FAILED_MESSAGE)
            }
        }
    }

    private fun parseUpVideoPage(
        root: JSONObject,
        page: Int,
        pageSize: Int,
        mid: Long
    ): UpVideoPage {
        val data = root.optJSONObject("data") ?: error(PARSE_FAILED_MESSAGE)
        val list = data.optJSONObject("list")?.optJSONArray("vlist")
            ?: data.optJSONArray("list")
        val pageInfo = data.optJSONObject("page") ?: data.optJSONObject("list")?.optJSONObject("page")
        val totalCount = pageInfo?.optInt("count") ?: 0
        val rawCount = list?.length() ?: 0

        val videos = buildList {
            if (list != null) {
                for (index in 0 until list.length()) {
                    val item = list.optJSONObject(index) ?: continue
                    parseUpVideoItem(item, index, mid)?.let(::add)
                }
            }
        }.sortedByDescending { it.pubdate }

        if (rawCount > 0 && videos.isEmpty()) {
            error(PARSE_FAILED_MESSAGE)
        }

        Log.i(TAG, "video parsed count=${videos.size}, rawCount=$rawCount, totalCount=$totalCount")
        val hasMore = if (totalCount > 0) {
            page * pageSize < totalCount
        } else {
            rawCount >= pageSize
        }
        UpSpaceDebugLog.logUiStateTarget(
            target = if (videos.isEmpty()) "Empty" else "Success",
            detail = "api layer parsedCount=${videos.size}, hasMore=$hasMore, page=$page"
        )
        return UpVideoPage(
            videos = videos,
            page = page,
            hasMore = hasMore,
            totalCount = totalCount
        )
    }

    private fun parseUpVideoItem(
        item: JSONObject,
        index: Int,
        ownerMid: Long
    ): UpVideoItem? {
        val aid = item.optLong("aid", item.optLong("id"))
        val bvid = item.optString("bvid")
        val title = item.optString("title")
        if ((aid <= 0L && bvid.isBlank()) || title.isBlank()) {
            return null
        }
        return UpVideoItem(
            aid = aid,
            bvid = bvid,
            cid = item.optLong("cid"),
            title = title,
            coverUrl = FormatUtils.normalizeImageUrl(item.optString("pic").ifBlank { item.optString("cover") }),
            playCount = item.optLong("play", item.optLong("view")),
            danmakuCount = item.optLong("video_review", item.optLong("danmaku")),
            duration = item.optLong("duration").takeIf { it > 0L } ?: parseLength(item.optString("length")),
            pubdate = item.optLong("created", item.optLong("pubdate")),
            accent = FormatUtils.accentFor(index)
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
        const val TAG = "BiliUpSpace"
        const val WBI_ARC_SEARCH_URL = "https://api.bilibili.com/x/space/wbi/arc/search"
        const val WBI_ARC_SEARCH_ENDPOINT = "/x/space/wbi/arc/search"
        const val UP_SPACE_ORIGIN = "https://space.bilibili.com"
        const val PARSE_FAILED_MESSAGE = "\u6295\u7a3f\u89c6\u9891\u89e3\u6790\u5931\u8d25"

        fun upSpaceReferer(mid: Long): String = "$UP_SPACE_ORIGIN/$mid/video"
    }
}
