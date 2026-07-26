package com.bililite.tv.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

class BilibiliDisplayCountParserTest {
    @Test
    fun parsesNumericValues() {
        assertEquals(5338000L, BilibiliDisplayCountParser.parse(5338000L))
        assertEquals(8655L, BilibiliDisplayCountParser.parse("8655"))
    }

    @Test
    fun parsesChineseCompactCounts() {
        assertEquals(5338000L, BilibiliDisplayCountParser.parse("533.8万"))
        assertEquals(4715000L, BilibiliDisplayCountParser.parse("471.5万播放"))
        assertEquals(120000000L, BilibiliDisplayCountParser.parse("1.2亿"))
    }

    @Test
    fun returnsFirstPositiveValue() {
        assertEquals(
            936000L,
            BilibiliDisplayCountParser.firstPositive(null, "--", 0, "93.6万")
        )
    }
}
