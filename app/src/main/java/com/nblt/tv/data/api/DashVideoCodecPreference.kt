package com.nblt.tv.data.api

internal enum class DashVideoCodecPreference {
    AVC_FIRST,
    HEVC_FIRST
}

internal data class DashVideoCodecPreferencePlan(
    val preference: DashVideoCodecPreference,
    val targetQn: Int,
    val expiresAtMs: Long
)

internal object DashVideoCodecPreferenceMemory {
    private const val DEFAULT_TTL_MS = 30L * 60L * 1000L
    private const val SAFE_FALLBACK_QN = 64

    private val plansByCid = linkedMapOf<Long, DashVideoCodecPreferencePlan>()

    @Synchronized
    fun recommendedPlan(
        cid: Long,
        nowMs: Long = System.currentTimeMillis()
    ): DashVideoCodecPreferencePlan? {
        val plan = plansByCid[cid] ?: return null
        if (plan.expiresAtMs <= nowMs) {
            plansByCid.remove(cid)
            return null
        }
        return plan
    }

    @Synchronized
    fun recordHevcPreferred(
        cid: Long,
        requestedQn: Int,
        nowMs: Long = System.currentTimeMillis(),
        ttlMs: Long = DEFAULT_TTL_MS
    ): DashVideoCodecPreferencePlan {
        val targetQn = requestedQn
            .takeIf { it > 0 }
            ?.coerceAtMost(SAFE_FALLBACK_QN)
            ?: SAFE_FALLBACK_QN
        return DashVideoCodecPreferencePlan(
            preference = DashVideoCodecPreference.HEVC_FIRST,
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
