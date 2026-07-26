package com.nblt.tv.data.api

import kotlin.math.roundToLong

/** Parses Bilibili count fields that may be JSON numbers or display strings such as 533.8万. */
internal object BilibiliDisplayCountParser {
    private val compactCountPattern = Regex("""([0-9]+(?:\.[0-9]+)?)\s*(亿|万|千)?""")

    fun firstPositive(vararg values: Any?): Long {
        for (value in values) {
            val parsed = parse(value)
            if (parsed > 0L) return parsed
        }
        return 0L
    }

    fun parse(value: Any?): Long {
        return when (value) {
            null -> 0L
            is Number -> value.toLong().coerceAtLeast(0L)
            is String -> parseText(value)
            else -> parseText(value.toString())
        }
    }

    private fun parseText(raw: String): Long {
        val text = raw.trim().replace(",", "")
        if (text.isBlank() || text == "-" || text == "--") return 0L

        val match = compactCountPattern.find(text) ?: return 0L
        val number = match.groupValues[1].toDoubleOrNull() ?: return 0L
        val multiplier = when (match.groupValues[2]) {
            "千" -> 1_000.0
            "万" -> 10_000.0
            "亿" -> 100_000_000.0
            else -> 1.0
        }
        return (number * multiplier).roundToLong().coerceAtLeast(0L)
    }
}
