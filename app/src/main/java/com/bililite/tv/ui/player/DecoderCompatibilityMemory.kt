package com.bililite.tv.ui.player

internal data class DecoderCompatibilityPlan(
    val mode: VideoDecoderMode,
    val targetQn: Int,
    val expiresAtMs: Long
)

internal object DecoderCompatibilityMemory {
    private const val DEFAULT_TTL_MS = 30L * 60L * 1000L
    private const val SAFE_SOFTWARE_QN = 64

    private val plansByCid = linkedMapOf<Long, DecoderCompatibilityPlan>()

    @Synchronized
    fun recommendedPlan(
        cid: Long,
        nowMs: Long = System.currentTimeMillis()
    ): DecoderCompatibilityPlan? {
        val plan = plansByCid[cid] ?: return null
        if (plan.expiresAtMs <= nowMs) {
            plansByCid.remove(cid)
            return null
        }
        return plan
    }

    @Synchronized
    fun recordSoftwareRequired(
        cid: Long,
        observedQn: Int,
        nowMs: Long = System.currentTimeMillis(),
        ttlMs: Long = DEFAULT_TTL_MS
    ): DecoderCompatibilityPlan {
        val targetQn = observedQn
            .takeIf { it > 0 }
            ?.coerceAtMost(SAFE_SOFTWARE_QN)
            ?: SAFE_SOFTWARE_QN
        return DecoderCompatibilityPlan(
            mode = VideoDecoderMode.SOFTWARE_PREFERRED,
            targetQn = targetQn,
            expiresAtMs = nowMs + ttlMs.coerceAtLeast(1L)
        ).also { plan ->
            plansByCid[cid] = plan
        }
    }

    @Synchronized
    fun clear(cid: Long) {
        plansByCid.remove(cid)
    }

    @Synchronized
    internal fun clearAllForTest() {
        plansByCid.clear()
    }
}
