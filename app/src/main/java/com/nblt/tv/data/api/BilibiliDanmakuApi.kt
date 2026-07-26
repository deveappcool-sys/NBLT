package com.nblt.tv.data.api

import android.util.Log
import com.nblt.tv.model.DanmakuItem
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

class BilibiliDanmakuApi(
    private val client: BilibiliApiClient = BilibiliApiClient
) {
    fun fetchDanmaku(cid: Long, maxItems: Int = Int.MAX_VALUE): List<DanmakuItem> {
        if (cid <= 0) {
            return emptyList()
        }
        val url = "https://comment.bilibili.com/$cid.xml"
        Log.i(TAG, "cid=$cid, danmaku request URL=$url")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BilibiliApiClient.USER_AGENT)
            .header("Referer", BilibiliApiClient.REFERER)
            .header("Origin", BilibiliApiClient.ORIGIN)
            .header("Accept", "text/xml, application/xml, text/plain, */*")
            .header("Accept-Encoding", "gzip")
            .get()
            .build()

        client.httpClient.newCall(request).execute().use { response ->
            Log.i(TAG, "HTTP code=${response.code}")
            val encoding = response.header("Content-Encoding").orEmpty()
            val contentType = response.header("Content-Type").orEmpty()
            val contentLength = response.header("Content-Length").orEmpty()
            Log.i(TAG, "Content-Encoding=$encoding, Content-Type=$contentType, Content-Length=$contentLength")
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }
            val rawBytes = response.body?.bytes() ?: ByteArray(0)
            Log.i(TAG, "raw byte size=${rawBytes.size}")
            val decodeResult = decodeResponseBody(rawBytes, encoding)
            Log.i(
                TAG,
                "whether decompressed=${decodeResult.decompressed}, decompression method=${decodeResult.method}"
            )
            val xml = decodeResult.text
            Log.i(TAG, "XML preview=${xml.take(300)}")
            val result = parseXml(xml, maxItems)
            val items = result.items
            Log.i(TAG, "load success, parsed count=${items.size}")
            Log.i(TAG, "mode distribution=${items.groupingBy { it.mode }.eachCount().toSortedMap()}")
            Log.i(TAG, "raw d node count=${result.rawCount}, parsed count=${items.size}, skipped count=${result.skippedCount}")
            items.take(5).forEachIndexed { index, item ->
                Log.i(TAG, "preview ${index + 1}: time=${item.timeSeconds}, text=${item.text.take(24)}")
            }
            return items.sortedBy { it.timeSeconds }
        }
    }

    private fun decodeResponseBody(
        bytes: ByteArray,
        contentEncoding: String
    ): DecodeResult {
        val plainText = bytes.toUtf8String()
        if (plainText.trimStart().startsWith("<")) {
            return DecodeResult(
                text = plainText,
                decompressed = false,
                method = "plain"
            )
        }

        val encoding = contentEncoding.lowercase()
        if ("br" in encoding) {
            Log.i(TAG, "unsupported br response, request should avoid br")
            return DecodeResult(
                text = plainText,
                decompressed = false,
                method = "unsupported-br"
            )
        }

        if ("gzip" in encoding || bytes.isGzip()) {
            return DecodeResult(
                text = runCatching { GZIPInputStream(ByteArrayInputStream(bytes)).readBytes().toUtf8String() }
                    .onFailure { Log.i(TAG, "gzip decompress fail=${it.message}") }
                    .getOrDefault(plainText),
                decompressed = true,
                method = "gzip"
            )
        }

        if ("deflate" in encoding || "zlib" in encoding || bytes.isLikelyZlib()) {
            inflate(bytes, nowrap = false)?.let {
                return DecodeResult(
                    text = it,
                    decompressed = true,
                    method = "zlib"
                )
            }
        }

        inflate(bytes, nowrap = false)?.takeIf { it.trimStart().startsWith("<") }?.let {
            return DecodeResult(
                text = it,
                decompressed = true,
                method = "zlib-fallback"
            )
        }
        inflate(bytes, nowrap = true)?.takeIf { it.trimStart().startsWith("<") }?.let {
            return DecodeResult(
                text = it,
                decompressed = true,
                method = "deflate-raw-fallback"
            )
        }

        return DecodeResult(
            text = plainText,
            decompressed = false,
            method = "plain-fallback"
        )
    }

    private fun inflate(
        bytes: ByteArray,
        nowrap: Boolean
    ): String? {
        return runCatching {
            InflaterInputStream(ByteArrayInputStream(bytes), Inflater(nowrap)).use {
                it.readBytes().toUtf8String()
            }
        }.onFailure {
            Log.i(TAG, "inflate fail nowrap=$nowrap, reason=${it.message}")
        }.getOrNull()
    }

    private fun parseXml(xml: String, maxItems: Int): DanmakuParseResult {
        val items = mutableListOf<DanmakuItem>()
        var skippedCount = 0
        var rawCount = 0
        var cursor = 0
        while (cursor < xml.length && items.size < maxItems) {
            val nodeStart = xml.indexOf("<d ", startIndex = cursor)
            if (nodeStart < 0) break
            val attributesEnd = xml.indexOf('>', startIndex = nodeStart + 3)
            if (attributesEnd < 0) break
            val nodeEnd = xml.indexOf("</d>", startIndex = attributesEnd + 1)
            if (nodeEnd < 0) break
            cursor = nodeEnd + 4
            rawCount += 1
            val attributes = xml.substring(nodeStart + 3, attributesEnd)
            val pStart = attributes.indexOf("p=\"")
            val pEnd = if (pStart >= 0) attributes.indexOf('"', startIndex = pStart + 3) else -1
            runCatching {
                if (pStart < 0 || pEnd < 0) error("missing p attribute")
                val p = attributes.substring(pStart + 3, pEnd).split(",", limit = 5)
                val text = decodeHtmlEntities(xml.substring(attributesEnd + 1, nodeEnd)).trim()
                if (text.isBlank()) {
                    skippedCount += 1
                    return@runCatching
                }
                items += DanmakuItem(
                    timeSeconds = p.getOrNull(0)?.toFloatOrNull() ?: 0f,
                    mode = p.getOrNull(1)?.toIntOrNull() ?: 1,
                    fontSize = p.getOrNull(2)?.toFloatOrNull() ?: 25f,
                    color = p.getOrNull(3)?.toIntOrNull() ?: 0xFFFFFF,
                    text = text
                )
            }.onFailure {
                skippedCount += 1
                Log.i(TAG, "skip bad danmaku index=${rawCount - 1}, reason=${it.message}")
            }
        }
        return DanmakuParseResult(
            items = items,
            rawCount = rawCount,
            skippedCount = skippedCount
        )
    }

    private fun decodeHtmlEntities(raw: String): String {
        return ENTITY_REGEX.replace(raw) { match ->
            val entity = match.value
            when {
                entity == "&amp;" -> "&"
                entity == "&lt;" -> "<"
                entity == "&gt;" -> ">"
                entity == "&quot;" -> "\""
                entity == "&#39;" || entity == "&apos;" -> "'"
                entity.startsWith("&#x", ignoreCase = true) && entity.endsWith(";") -> {
                    entity
                        .removePrefix("&#x")
                        .removeSuffix(";")
                        .toIntOrNull(radix = 16)
                        ?.let { code -> String(Character.toChars(code)) }
                        ?: entity
                }
                entity.startsWith("&#") && entity.endsWith(";") -> {
                    entity
                        .removePrefix("&#")
                        .removeSuffix(";")
                        .toIntOrNull()
                        ?.let { code -> String(Character.toChars(code)) }
                        ?: entity
                }
                else -> entity
            }
        }
    }

    private data class DanmakuParseResult(
        val items: List<DanmakuItem>,
        val rawCount: Int,
        val skippedCount: Int
    )

    private data class DecodeResult(
        val text: String,
        val decompressed: Boolean,
        val method: String
    )

    private fun ByteArray.toUtf8String(): String {
        return String(this, StandardCharsets.UTF_8)
    }

    private fun ByteArray.isGzip(): Boolean {
        return size >= 2 &&
            this[0].toInt() and 0xFF == 0x1F &&
            this[1].toInt() and 0xFF == 0x8B
    }

    private fun ByteArray.isLikelyZlib(): Boolean {
        if (size < 2) {
            return false
        }
        val first = this[0].toInt() and 0xFF
        val second = this[1].toInt() and 0xFF
        return first == 0x78 && ((first shl 8) + second) % 31 == 0
    }

    private companion object {
        const val TAG = "BiliDanmaku"
        val ENTITY_REGEX = Regex("""&(?:amp|lt|gt|quot|apos|#39|#[0-9]+|#x[0-9A-Fa-f]+);""")
    }
}
