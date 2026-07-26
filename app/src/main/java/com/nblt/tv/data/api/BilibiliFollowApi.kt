package com.nblt.tv.data.api

import com.nblt.tv.model.FollowedUp
import com.nblt.tv.util.FormatUtils
import okhttp3.Request
import org.json.JSONObject

class BilibiliFollowApi(
    private val client: BilibiliApiClient = BilibiliApiClient
) {
    fun fetchFollowedUps(
        myMid: Long,
        cookieHeader: String,
        pageSize: Int = 50
    ): List<FollowedUp> {
        val request = Request.Builder()
            .url("https://api.bilibili.com/x/relation/followings?vmid=$myMid&pn=1&ps=$pageSize&order=desc&order_type=attention")
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
                error(root.optString("message", "Follow list error"))
            }

            val list = root.optJSONObject("data")?.optJSONArray("list") ?: return emptyList()
            return buildList {
                for (index in 0 until list.length()) {
                    val item = list.optJSONObject(index) ?: continue
                    val mid = item.optLong("mid")
                    val name = item.optString("uname").ifBlank { item.optString("name") }
                    if (mid > 0L && name.isNotBlank()) {
                        add(
                            FollowedUp(
                                mid = mid,
                                name = name,
                                avatarUrl = FormatUtils.normalizeImageUrl(item.optString("face"))
                            )
                        )
                    }
                }
            }
        }
    }
}
