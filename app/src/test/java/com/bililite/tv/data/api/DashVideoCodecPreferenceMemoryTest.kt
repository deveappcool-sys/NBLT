package com.bililite.tv.data.api

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashVideoCodecPreferenceMemoryTest {
    @After
    fun tearDown() {
        DashVideoCodecPreferenceMemory.clearAllForTest()
    }

    @Test
    fun recordHevcPreferredCapsFallbackAtQn64() {
        val plan = DashVideoCodecPreferenceMemory.recordHevcPreferred(
            cid = 100L,
            requestedQn = 80,
            nowMs = 1_000L,
            ttlMs = 5_000L
        )

        assertEquals(DashVideoCodecPreference.HEVC_FIRST, plan.preference)
        assertEquals(64, plan.targetQn)
        assertEquals(plan, DashVideoCodecPreferenceMemory.recommendedPlan(100L, 2_000L))
    }

    @Test
    fun lowerRequestedQualityIsPreserved() {
        val plan = DashVideoCodecPreferenceMemory.recordHevcPreferred(
            cid = 200L,
            requestedQn = 32,
            nowMs = 1_000L,
            ttlMs = 5_000L
        )

        assertEquals(32, plan.targetQn)
    }

    @Test
    fun expiredPlanIsRemoved() {
        DashVideoCodecPreferenceMemory.recordHevcPreferred(
            cid = 300L,
            requestedQn = 64,
            nowMs = 1_000L,
            ttlMs = 100L
        )

        assertNull(DashVideoCodecPreferenceMemory.recommendedPlan(300L, 1_100L))
    }
}
