package com.bililite.tv.ui.player

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DecoderCompatibilityMemoryTest {
    @Before
    fun setUp() {
        DecoderCompatibilityMemory.clearAllForTest()
    }

    @After
    fun tearDown() {
        DecoderCompatibilityMemory.clearAllForTest()
    }

    @Test
    fun remembersSoftwareAtSafeQualityForSameCid() {
        DecoderCompatibilityMemory.recordSoftwareRequired(
            cid = 123L,
            observedQn = 80,
            nowMs = 1_000L,
            ttlMs = 10_000L
        )

        val plan = DecoderCompatibilityMemory.recommendedPlan(
            cid = 123L,
            nowMs = 2_000L
        )

        assertEquals(VideoDecoderMode.SOFTWARE_PREFERRED, plan?.mode)
        assertEquals(64, plan?.targetQn)
    }

    @Test
    fun doesNotLeakPlanAcrossDifferentCid() {
        DecoderCompatibilityMemory.recordSoftwareRequired(
            cid = 123L,
            observedQn = 64,
            nowMs = 1_000L,
            ttlMs = 10_000L
        )

        assertNull(
            DecoderCompatibilityMemory.recommendedPlan(
                cid = 456L,
                nowMs = 2_000L
            )
        )
    }

    @Test
    fun expiresPlan() {
        DecoderCompatibilityMemory.recordSoftwareRequired(
            cid = 123L,
            observedQn = 64,
            nowMs = 1_000L,
            ttlMs = 500L
        )

        assertNull(
            DecoderCompatibilityMemory.recommendedPlan(
                cid = 123L,
                nowMs = 1_500L
            )
        )
    }
}
