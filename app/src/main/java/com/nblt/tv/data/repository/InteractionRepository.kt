package com.nblt.tv.data.repository

import com.nblt.tv.data.api.BilibiliApiClient
import com.nblt.tv.storage.CookieStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject

class InteractionRepository(private val cookieStorage: CookieStorage) {
    suspend fun like(aid: Long) = post("/x/web-interface/archive/like", mapOf("aid" to aid.toString(), "like" to "1"))
    suspend fun coin(aid: Long) = post("/x/web-interface/coin/add", mapOf("aid" to aid.toString(), "multiply" to "1", "select_like" to "0"))
    suspend fun favorite(aid: Long, mediaId: Long) = post(
        "/x/v3/fav/resource/deal",
        mapOf("rid" to aid.toString(), "type" to "2", "add_media_ids" to mediaId.toString(), "del_media_ids" to "")
    )

    private suspend fun post(path: String, fields: Map<String, String>): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(aidValid(fields)) { "视频信息不完整" }
            val csrf = cookieStorage.getCookieValue("bili_jct") ?: error("请先登录")
            val body = FormBody.Builder().apply {
                fields.forEach { (key, value) -> add(key, value) }
                add("csrf", csrf)
            }.build()
            val request = Request.Builder()
                .url("https://api.bilibili.com$path")
                .header("User-Agent", BilibiliApiClient.USER_AGENT)
                .header("Referer", BilibiliApiClient.REFERER)
                .header("Origin", BilibiliApiClient.ORIGIN)
                .header("Cookie", cookieStorage.getCookieHeader())
                .post(body)
                .build()
            BilibiliApiClient.httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("网络请求失败（${response.code}）")
                val root = JSONObject(response.body?.string().orEmpty())
                if (root.optInt("code", -1) != 0) error(root.optString("message").ifBlank { "操作失败" })
                root.optString("message").ifBlank { "操作成功" }
            }
        }
    }

    private fun aidValid(fields: Map<String, String>) = fields["aid"]?.toLongOrNull()?.let { it > 0 } ?: true
}
