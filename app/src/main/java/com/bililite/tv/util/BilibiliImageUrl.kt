package com.bililite.tv.util

import java.net.URI
import java.util.Locale

object BilibiliImageUrl {
    fun cover(
        url: String,
        width: Int = 480,
        height: Int = 270
    ): String = resized(url, width, height)

    fun avatar(
        url: String,
        size: Int = 96
    ): String = resized(url, size, size)

    private fun resized(
        rawUrl: String,
        width: Int,
        height: Int
    ): String {
        val normalized = normalizeUrl(rawUrl.trim())
        if (normalized.isBlank()) return ""
        if (width <= 0 || height <= 0) return normalized
        if (!isBilibiliImageHost(normalized)) return normalized

        val queryIndex = normalized.indexOf('?').takeIf { it >= 0 } ?: normalized.length
        val fragmentIndex = normalized.indexOf('#').takeIf { it >= 0 } ?: normalized.length
        val tailIndex = minOf(queryIndex, fragmentIndex)

        val base = normalized.substring(0, tailIndex)
        val tail = normalized.substring(tailIndex)
        val lastSlash = base.lastIndexOf('/')
        val lastAt = base.lastIndexOf('@')
        val originalBase = if (lastAt > lastSlash) {
            base.substring(0, lastAt)
        } else {
            base
        }

        return "${originalBase}@${width}w_${height}h_1c.webp$tail"
    }


    private fun normalizeUrl(url: String): String {
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://") -> url.replaceFirst("http://", "https://")
            else -> url
        }
    }

    private fun isBilibiliImageHost(url: String): Boolean {
        val host = runCatching {
            URI(url).host?.lowercase(Locale.US)
        }.getOrNull() ?: return false

        return host == "hdslb.com" ||
            host.endsWith(".hdslb.com") ||
            host == "biliimg.com" ||
            host.endsWith(".biliimg.com")
    }
}
