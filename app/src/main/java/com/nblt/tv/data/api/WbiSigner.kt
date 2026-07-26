package com.nblt.tv.data.api

import com.nblt.tv.util.UpSpaceDebugLog
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Minimal WBI signer for Bilibili open API (img_key + sub_key from nav).
 */
class WbiSigner(
    private val client: OkHttpClient = BilibiliApiClient.httpClient
) {
    private data class CachedKeys(
        val imgKey: String,
        val subKey: String,
        val loadedAt: Long
    )

    @Volatile
    private var cachedKeys: CachedKeys? = null

    fun signUrl(
        baseUrl: String,
        params: Map<String, String>,
        cookieHeader: String,
        forceRefreshKeys: Boolean = false
    ): okhttp3.HttpUrl {
        val signedParams = signParams(params, cookieHeader, forceRefreshKeys)
        val builder = baseUrl.toHttpUrl().newBuilder()
        signedParams.forEach { (key, value) ->
            builder.addQueryParameter(key, value)
        }
        return builder.build()
    }

    fun signParams(
        params: Map<String, String>,
        cookieHeader: String,
        forceRefreshKeys: Boolean = false
    ): Map<String, String> {
        val keys = loadKeys(cookieHeader, forceRefreshKeys)
        return signParams(params, keys.imgKey, keys.subKey)
    }

    private fun loadKeys(cookieHeader: String, forceRefresh: Boolean): CachedKeys {
        val now = System.currentTimeMillis()
        val cached = cachedKeys
        if (!forceRefresh && cached != null && now - cached.loadedAt < KEY_CACHE_TTL_MS) {
            return cached
        }
        val requestBuilder = Request.Builder()
            .url(NAV_URL)
            .header("User-Agent", BilibiliApiClient.USER_AGENT)
            .header("Origin", BilibiliApiClient.ORIGIN)
            .get()
        if (cookieHeader.isNotBlank()) {
            requestBuilder.header("Cookie", cookieHeader)
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                error("WBI nav HTTP ${response.code}")
            }
            val root = JSONObject(response.body?.string().orEmpty())
            val code = root.optInt("code", -1)
            if (code != 0) {
                error(root.optString("message").ifBlank { "WBI nav failed ($code)" })
            }
            val wbiImg = root.optJSONObject("data")?.optJSONObject("wbi_img")
                ?: error("WBI nav missing wbi_img")
            val imgKey = extractKeyFromUrl(wbiImg.optString("img_url"))
            val subKey = extractKeyFromUrl(wbiImg.optString("sub_url"))
            if (imgKey.isBlank() || subKey.isBlank()) {
                error("WBI nav invalid img/sub key")
            }
            UpSpaceDebugLog.logWbiSign(
                imgKeySuffix = imgKey.takeLast(8),
                subKeySuffix = subKey.takeLast(8),
                wts = "",
                wRid = "",
                signedParamKeys = listOf("nav_keys_loaded")
            )
            return CachedKeys(imgKey, subKey, now).also { cachedKeys = it }
        }
    }

    private fun signParams(
        params: Map<String, String>,
        imgKey: String,
        subKey: String
    ): Map<String, String> {
        val mixinKey = buildMixinKey(imgKey, subKey)
        val signed = LinkedHashMap(params)
        val wts = (System.currentTimeMillis() / TimeUnit.SECONDS.toMillis(1)).toString()
        signed["wts"] = wts
        val sorted = signed.toSortedMap()
        val query = sorted.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }.filterDisallowedSymbols()
        val wRid = md5Hex(query + mixinKey)
        require(wRid.length == 32) { "WBI w_rid must be 32-char hex" }
        signed["w_rid"] = wRid
        UpSpaceDebugLog.logWbiSign(
            imgKeySuffix = imgKey.takeLast(8),
            subKeySuffix = subKey.takeLast(8),
            wts = wts,
            wRid = wRid,
            signedParamKeys = signed.keys.sorted()
        )
        return signed
    }

    private fun buildMixinKey(imgKey: String, subKey: String): String {
        val raw = imgKey + subKey
        require(raw.length == 64) { "WBI mixin raw length must be 64" }
        return buildString(32) {
            for (index in MIXIN_KEY_ENC_TAB) {
                append(raw[index])
                if (length >= 32) break
            }
        }
    }

    private fun extractKeyFromUrl(url: String): String {
        return url.substringAfterLast('/').substringBefore('.')
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name())
            .replace("+", "%20")
    }

    private fun String.filterDisallowedSymbols(): String {
        return replace(Regex("[!'()*]"), "")
    }

    private fun md5Hex(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte ->
            String.format(Locale.US, "%02x", byte)
        }
    }

    companion object {
        private const val NAV_URL = "https://api.bilibili.com/x/web-interface/nav"
        private const val KEY_CACHE_TTL_MS = 60 * 60 * 1000L

        private val MIXIN_KEY_ENC_TAB = intArrayOf(
            46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
            33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
            61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 52, 62, 57, 11, 36,
            20, 34, 63, 64, 6
        )
    }
}
