package com.bililite.tv.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BilibiliImageUrlTest {
    @Test
    fun coverAddsHttpsAndThumbnailSuffix() {
        assertEquals(
            "https://i0.hdslb.com/bfs/archive/cover.jpg@480w_270h_1c.webp",
            BilibiliImageUrl.cover("//i0.hdslb.com/bfs/archive/cover.jpg")
        )
    }

    @Test
    fun coverReplacesExistingThumbnailSuffix() {
        assertEquals(
            "https://archive.biliimg.com/bfs/archive/cover.jpg@960w_540h_1c.webp",
            BilibiliImageUrl.cover(
                "https://archive.biliimg.com/bfs/archive/cover.jpg@320w_180h_1c.webp",
                width = 960,
                height = 540
            )
        )
    }

    @Test
    fun coverPreservesQueryString() {
        assertEquals(
            "https://i0.hdslb.com/bfs/archive/cover.jpg@480w_270h_1c.webp?token=abc",
            BilibiliImageUrl.cover(
                "https://i0.hdslb.com/bfs/archive/cover.jpg?token=abc"
            )
        )
    }

    @Test
    fun avatarUsesSquareThumbnail() {
        assertEquals(
            "https://i1.hdslb.com/bfs/face/avatar.jpg@64w_64h_1c.webp",
            BilibiliImageUrl.avatar(
                "https://i1.hdslb.com/bfs/face/avatar.jpg",
                size = 64
            )
        )
    }

    @Test
    fun nonBilibiliImageIsUnchanged() {
        assertEquals(
            "https://example.com/image.jpg",
            BilibiliImageUrl.cover("https://example.com/image.jpg")
        )
    }
}
