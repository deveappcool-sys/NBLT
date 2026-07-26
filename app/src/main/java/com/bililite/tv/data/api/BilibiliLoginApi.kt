package com.bililite.tv.data.api

import com.bililite.tv.model.LoginQrCode
import com.bililite.tv.model.UserInfo
import com.bililite.tv.util.FormatUtils
import okhttp3.Request
import org.json.JSONObject

class BilibiliLoginApi(
    private val client: BilibiliApiClient = BilibiliApiClient
) {
    fun generateQrCode(): LoginQrCode {
        val request = Request.Builder()
            .url("https://passport.bilibili.com/x/passport-login/web/qrcode/generate")
            .header("User-Agent", BilibiliApiClient.USER_AGENT)
            .header("Referer", BilibiliApiClient.REFERER)
            .get()
            .build()

        client.httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }
            val root = JSONObject(response.body?.string().orEmpty())
            val code = root.optInt("code", -1)
            if (code != 0) {
                error(root.optString("message", "QRCode generate error"))
            }
            val data = root.optJSONObject("data") ?: error("QRCode data is empty")
            return LoginQrCode(
                url = data.optString("url"),
                qrcodeKey = data.optString("qrcode_key")
            )
        }
    }

    fun pollQrCode(qrcodeKey: String): LoginPollResult {
        val request = Request.Builder()
            .url("https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=$qrcodeKey")
            .header("User-Agent", BilibiliApiClient.USER_AGENT)
            .header("Referer", BilibiliApiClient.REFERER)
            .get()
            .build()

        client.httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return LoginPollResult.Error("HTTP ${response.code}")
            }
            val root = JSONObject(response.body?.string().orEmpty())
            val data = root.optJSONObject("data") ?: return LoginPollResult.Error("Login poll data is empty")
            return when (val statusCode = data.optInt("code", -1)) {
                0 -> LoginPollResult.Success(extractCookies(response.headers.values("Set-Cookie")))
                86101 -> LoginPollResult.Waiting("\u7b49\u5f85\u624b\u673a\u626b\u7801")
                86090 -> LoginPollResult.Waiting("\u5df2\u626b\u7801\uff0c\u8bf7\u5728\u624b\u673a\u4e0a\u786e\u8ba4")
                86038 -> LoginPollResult.Expired("\u4e8c\u7ef4\u7801\u5df2\u8fc7\u671f")
                else -> LoginPollResult.Error(data.optString("message", "Login poll code $statusCode"))
            }
        }
    }

    fun fetchUserInfo(cookieHeader: String): UserInfo {
        val request = Request.Builder()
            .url("https://api.bilibili.com/x/web-interface/nav")
            .header("User-Agent", BilibiliApiClient.USER_AGENT)
            .header("Referer", BilibiliApiClient.REFERER)
            .header("Cookie", cookieHeader)
            .get()
            .build()

        client.httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }
            val root = JSONObject(response.body?.string().orEmpty())
            val code = root.optInt("code", -1)
            if (code != 0) {
                error(root.optString("message", "User info error"))
            }
            val data = root.optJSONObject("data") ?: error("User info is empty")
            if (!data.optBoolean("isLogin", false)) {
                error("\u8d26\u53f7\u672a\u767b\u5f55")
            }
            return UserInfo(
                mid = data.optLong("mid"),
                nickname = data.optString("uname", "\u5df2\u767b\u5f55\u7528\u6237"),
                avatarUrl = FormatUtils.normalizeImageUrl(data.optString("face"))
            )
        }
    }

    private fun extractCookies(setCookieHeaders: List<String>): Map<String, String> {
        return setCookieHeaders.mapNotNull { header ->
            val firstPart = header.substringBefore(";")
            val name = firstPart.substringBefore("=")
            val value = firstPart.substringAfter("=", "")
            if (name.isNotBlank() && value.isNotBlank()) {
                name to value
            } else {
                null
            }
        }.toMap()
    }
}

sealed interface LoginPollResult {
    data class Success(val cookies: Map<String, String>) : LoginPollResult
    data class Waiting(val message: String) : LoginPollResult
    data class Expired(val message: String) : LoginPollResult
    data class Error(val message: String) : LoginPollResult
}
