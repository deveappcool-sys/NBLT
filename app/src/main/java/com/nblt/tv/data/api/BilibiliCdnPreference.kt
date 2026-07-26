package com.nblt.tv.data.api

import android.os.SystemClock
import android.util.Log
import java.net.URI
import kotlin.math.pow
import kotlin.math.roundToInt

object BilibiliCdnPreference {
    private const val TAG = "BiliCdnFallback"
    private const val TAG_ROUTER = "BiliCdnRouter"
    private const val TAG_SCORE = "BiliCdnScore"

    enum class StreamRole {
        VIDEO,
        AUDIO,
        BOTH,
        UNKNOWN;

        fun appliesTo(streamName: String): Boolean {
            val normalized = streamName.trim().lowercase()
            if (normalized == "both" || normalized.isBlank()) return true
            return when (this) {
                VIDEO -> normalized == "video" || normalized == "durl"
                AUDIO -> normalized == "audio"
                BOTH, UNKNOWN -> true
            }
        }

        fun merge(other: StreamRole): StreamRole {
            if (this == other) return this
            if (this == UNKNOWN) return other
            if (other == UNKNOWN) return this
            return BOTH
        }
    }

    enum class NetworkObservationSource {
        PLAYBACK,
        PRECONNECT
    }

    data class HostRouteStatus(
        val host: String,
        val score: Int,
        val coolingDown: Boolean,
        val cooldownRemainingMs: Long,
        val failureCount: Int,
        val weak: Boolean,
        val preferred: Boolean,
        val badSource: Boolean,
        val reason: String,
        val dynamicScore: Int,
        val dynamicConfidencePercent: Int,
        val performanceSamples: Int,
        val performanceSummary: String
    )

    private data class FailureRecord(
        var failureCount: Int,
        var lastFailureAtMs: Long,
        var cooldownUntilMs: Long,
        var lastReason: String,
        var streamRole: StreamRole,
        var badSource: Boolean,
        var lastProbeAtMs: Long = 0L
    )

    private data class PerformanceRecord(
        var weightedNetworkSamples: Double = 0.0,
        var networkSuccesses: Double = 0.0,
        var networkFailures: Double = 0.0,
        var connectionSamples: Double = 0.0,
        var reusedConnections: Double = 0.0,
        var ewmaDnsMs: Double? = null,
        var ewmaConnectMs: Double? = null,
        var ewmaTlsMs: Double? = null,
        var ewmaTtfbMs: Double? = null,
        var ewmaStartupMs: Double? = null,
        var qoeSessions: Double = 0.0,
        var rebufferEvents: Double = 0.0,
        var totalRebufferMs: Double = 0.0,
        var playbackErrors: Double = 0.0,
        var stableSuccesses: Double = 0.0,
        var routeFailures: Double = 0.0,
        var bufferingTimeouts: Double = 0.0,
        var badSourceFailures: Double = 0.0,
        var lastStableSuccessAtMs: Long = 0L,
        var lastUpdatedAtMs: Long = 0L,
        var lastDecayAtMs: Long = 0L
    )

    private data class DynamicScoreSnapshot(
        val score: Int,
        val confidencePercent: Int,
        val samples: Int,
        val summary: String
    )

    private val lock = Any()
    private val defaultWeakBlacklistedHosts = setOf(
        "upos-sz-mirrorcosov.bilivideo.com"
    )
    private val weakBlacklistedHosts = defaultWeakBlacklistedHosts.toMutableSet()
    private val failureRecords = mutableMapOf<String, FailureRecord>()
    private val performanceRecords = mutableMapOf<String, PerformanceRecord>()
    private val preferredHostsByCid = mutableMapOf<Long, Set<String>>()
    private var currentPlaybackCid: Long = 0L
    private var forcedProbeBudget: Int = 0

    fun recordFailedHost(
        host: String,
        reason: String,
        streamRole: StreamRole = StreamRole.BOTH
    ) {
        val normalized = host.normalizeHost()
        if (normalized.isBlank()) return

        val now = SystemClock.elapsedRealtime()
        val badSource = isBadSourceReason(reason)
        synchronized(lock) {
            pruneExpiredLocked(now)
            val previous = failureRecords[normalized]
            val nextCount = ((previous?.failureCount ?: 0) + 1).coerceAtMost(MAX_FAILURE_COUNT)
            val cooldownMs = cooldownFor(reason, nextCount)
            val mergedRole = previous?.streamRole?.merge(streamRole) ?: streamRole
            failureRecords[normalized] = FailureRecord(
                failureCount = nextCount,
                lastFailureAtMs = now,
                cooldownUntilMs = maxOf(previous?.cooldownUntilMs ?: 0L, now + cooldownMs),
                lastReason = reason,
                streamRole = mergedRole,
                badSource = badSource || previous?.badSource == true,
                lastProbeAtMs = previous?.lastProbeAtMs ?: 0L
            )
            recordRouteFailureLocked(normalized, reason, badSource, now)
            if (badSource) {
                preferredHostsByCid.keys.toList().forEach { cid ->
                    preferredHostsByCid[cid] = preferredHostsByCid[cid].orEmpty() - normalized
                }
                Log.w(
                    "BiliBadSourceHost",
                    "badHost=$normalized, reason=$reason, errorCodeName=$reason, " +
                        "willCooldown=true, cooldownMs=$cooldownMs, willRecordPreferred=false"
                )
            }
            val dynamic = dynamicScoreLocked(normalized, now)
            Log.w(
                TAG_ROUTER,
                "failure recorded host=$normalized, streamRole=$mergedRole, reason=$reason, " +
                    "failureCount=$nextCount, cooldownMs=$cooldownMs, badSource=$badSource, " +
                    "dynamicScore=${dynamic.score}, confidence=${dynamic.confidencePercent}"
            )
        }
        Log.w(TAG, "record failed host=$normalized, reason=$reason, streamRole=$streamRole")
    }

    fun recordPreferredHost(cid: Long, host: String, streamRole: StreamRole) {
        val normalized = host.normalizeHost()
        if (cid <= 0 || normalized.isBlank()) return
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            pruneExpiredLocked(now)
            val record = failureRecords[normalized]
            if (record != null) {
                when {
                    record.streamRole == streamRole || streamRole == StreamRole.BOTH -> {
                        failureRecords.remove(normalized)
                    }
                    record.streamRole == StreamRole.BOTH && streamRole == StreamRole.VIDEO -> {
                        record.streamRole = StreamRole.AUDIO
                    }
                    record.streamRole == StreamRole.BOTH && streamRole == StreamRole.AUDIO -> {
                        record.streamRole = StreamRole.VIDEO
                    }
                }
            }
            if (failureRecords[normalized] == null) {
                if (normalized !in defaultWeakBlacklistedHosts) {
                    weakBlacklistedHosts.remove(normalized)
                }
                preferredHostsByCid[cid] = preferredHostsByCid[cid].orEmpty() + normalized
            } else {
                preferredHostsByCid[cid] = preferredHostsByCid[cid].orEmpty() - normalized
            }
            recordStableSuccessLocked(normalized, now)
            val dynamic = dynamicScoreLocked(normalized, now)
            Log.i(
                TAG_ROUTER,
                "stable success host recovery cid=$cid, host=$normalized, streamRole=$streamRole, " +
                    "remainingFailureRole=${failureRecords[normalized]?.streamRole}, " +
                    "activeCooldowns=${activeCooldownHostsLocked(now)}, " +
                    "dynamicScore=${dynamic.score}, confidence=${dynamic.confidencePercent}"
            )
        }
    }

    fun recordPreferredHosts(cid: Long, hosts: Set<String>) {
        hosts.forEach { host -> recordPreferredHost(cid, host, StreamRole.BOTH) }
        if (cid > 0) {
            Log.i(TAG, "record preferred hosts cid=$cid, hosts=${preferredHosts(cid)}")
        }
    }

    fun recordNetworkObservation(
        host: String,
        source: NetworkObservationSource,
        dnsMs: Long?,
        connectMs: Long?,
        tlsMs: Long?,
        ttfbMs: Long,
        reusedConnection: Boolean,
        responseCode: Int
    ) {
        val normalized = host.normalizeHost()
        if (normalized.isBlank()) return
        val now = SystemClock.elapsedRealtime()
        val weight = if (source == NetworkObservationSource.PLAYBACK) {
            PLAYBACK_NETWORK_SAMPLE_WEIGHT
        } else {
            PRECONNECT_NETWORK_SAMPLE_WEIGHT
        }
        val successful = responseCode in 200..399
        val snapshot = synchronized(lock) {
            pruneExpiredLocked(now)
            val record = performanceRecords.getOrPut(normalized) { PerformanceRecord() }
            decayPerformanceLocked(record, now)
            record.weightedNetworkSamples =
                (record.weightedNetworkSamples + weight).coerceAtMost(MAX_WEIGHTED_NETWORK_SAMPLES)
            record.connectionSamples =
                (record.connectionSamples + weight).coerceAtMost(MAX_WEIGHTED_NETWORK_SAMPLES)
            if (reusedConnection) {
                record.reusedConnections =
                    (record.reusedConnections + weight).coerceAtMost(record.connectionSamples)
            }
            if (successful) {
                record.networkSuccesses =
                    (record.networkSuccesses + weight).coerceAtMost(MAX_WEIGHTED_NETWORK_SAMPLES)
                val alpha = if (source == NetworkObservationSource.PLAYBACK) {
                    PLAYBACK_EWMA_ALPHA
                } else {
                    PRECONNECT_EWMA_ALPHA
                }
                record.ewmaDnsMs = updateEwma(record.ewmaDnsMs, dnsMs, alpha)
                record.ewmaConnectMs = updateEwma(record.ewmaConnectMs, connectMs, alpha)
                record.ewmaTlsMs = updateEwma(record.ewmaTlsMs, tlsMs, alpha)
                record.ewmaTtfbMs = updateEwma(record.ewmaTtfbMs, ttfbMs, alpha)
            } else {
                record.networkFailures =
                    (record.networkFailures + weight).coerceAtMost(MAX_WEIGHTED_NETWORK_SAMPLES)
            }
            record.lastUpdatedAtMs = now
            dynamicScoreLocked(normalized, now)
        }
        Log.i(
            TAG_SCORE,
            "network observation host=$normalized, source=$source, code=$responseCode, " +
                "dnsMs=${dnsMs ?: -1}, connectMs=${connectMs ?: -1}, tlsMs=${tlsMs ?: -1}, " +
                "ttfbMs=$ttfbMs, reused=$reusedConnection, ${snapshot.summary}"
        )
    }

    fun recordNetworkFailure(
        host: String,
        source: NetworkObservationSource,
        reason: String
    ) {
        val normalized = host.normalizeHost()
        if (normalized.isBlank()) return
        val now = SystemClock.elapsedRealtime()
        val weight = if (source == NetworkObservationSource.PLAYBACK) {
            PLAYBACK_NETWORK_FAILURE_WEIGHT
        } else {
            PRECONNECT_NETWORK_FAILURE_WEIGHT
        }
        val snapshot = synchronized(lock) {
            pruneExpiredLocked(now)
            val record = performanceRecords.getOrPut(normalized) { PerformanceRecord() }
            decayPerformanceLocked(record, now)
            record.weightedNetworkSamples =
                (record.weightedNetworkSamples + weight).coerceAtMost(MAX_WEIGHTED_NETWORK_SAMPLES)
            record.networkFailures =
                (record.networkFailures + weight).coerceAtMost(MAX_WEIGHTED_NETWORK_SAMPLES)
            record.lastUpdatedAtMs = now
            dynamicScoreLocked(normalized, now)
        }
        Log.w(
            TAG_SCORE,
            "network failure host=$normalized, source=$source, reason=${reason.take(160)}, " +
                snapshot.summary
        )
    }

    fun recordPlaybackQoe(
        videoHost: String,
        audioHost: String,
        startupMs: Long,
        rebufferCount: Int,
        totalRebufferMs: Long,
        hadPlayerError: Boolean
    ) {
        val hosts = linkedSetOf(videoHost.normalizeHost(), audioHost.normalizeHost())
            .filter { it.isNotBlank() }
        if (hosts.isEmpty() || startupMs < 0L) return
        val now = SystemClock.elapsedRealtime()
        hosts.forEach { normalized ->
            val snapshot = synchronized(lock) {
                pruneExpiredLocked(now)
                val record = performanceRecords.getOrPut(normalized) { PerformanceRecord() }
                decayPerformanceLocked(record, now)
                record.qoeSessions = (record.qoeSessions + 1.0).coerceAtMost(MAX_QOE_SESSIONS)
                record.ewmaStartupMs = updateEwma(
                    current = record.ewmaStartupMs,
                    sampleMs = startupMs,
                    alpha = QOE_EWMA_ALPHA
                )
                record.rebufferEvents =
                    (record.rebufferEvents + rebufferCount.coerceAtLeast(0).toDouble())
                        .coerceAtMost(MAX_REBUFFER_EVENTS)
                record.totalRebufferMs =
                    (record.totalRebufferMs + totalRebufferMs.coerceAtLeast(0L).toDouble())
                        .coerceAtMost(MAX_TOTAL_REBUFFER_MS)
                if (hadPlayerError) {
                    record.playbackErrors =
                        (record.playbackErrors + 1.0).coerceAtMost(MAX_PLAYBACK_ERRORS)
                }
                record.lastUpdatedAtMs = now
                dynamicScoreLocked(normalized, now)
            }
            Log.i(
                TAG_SCORE,
                "qoe observation host=$normalized, startupMs=$startupMs, " +
                    "rebufferCount=$rebufferCount, totalRebufferMs=$totalRebufferMs, " +
                    "hadPlayerError=$hadPlayerError, ${snapshot.summary}"
            )
        }
    }

    fun preferredHosts(cid: Long): Set<String> = synchronized(lock) {
        preferredHostsByCid[cid].orEmpty().toSet()
    }

    fun beginPlaybackSession(cid: Long) {
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            pruneExpiredLocked(now)
            currentPlaybackCid = cid
            forcedProbeBudget = 0
            Log.i(
                TAG_ROUTER,
                "session begin cid=$cid, activeCooldowns=${activeCooldownHostsLocked(now)}, " +
                    "cooldownsPreserved=true, performanceHosts=${performanceRecords.size}"
            )
        }
    }

    fun allowImmediateControlledProbe(reason: String) {
        synchronized(lock) {
            forcedProbeBudget = 1
            Log.i(TAG_ROUTER, "controlled probe budget granted reason=$reason")
        }
    }

    fun reserveControlledProbe(
        host: String,
        streamName: String,
        forceWhenExhausted: Boolean = false
    ): Boolean {
        val normalized = host.normalizeHost()
        if (normalized.isBlank()) return false
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            pruneExpiredLocked(now)
            val record = failureRecords[normalized]
            val forced = forcedProbeBudget > 0
            val elapsedSinceProbe = now - (record?.lastProbeAtMs ?: 0L)
            if (!forced && !forceWhenExhausted && elapsedSinceProbe < CONTROLLED_PROBE_INTERVAL_MS) {
                return false
            }
            if (forced) {
                forcedProbeBudget -= 1
            }
            if (record != null) {
                record.lastProbeAtMs = now
            }
            Log.w(
                TAG_ROUTER,
                "controlled probe reserved host=$normalized, stream=$streamName, forced=$forced, " +
                    "forceWhenExhausted=$forceWhenExhausted, cid=$currentPlaybackCid"
            )
            return true
        }
    }

    fun routeStatus(
        host: String,
        cid: Long,
        explicitFailedHosts: Set<String>,
        streamName: String
    ): HostRouteStatus {
        val normalized = host.normalizeHost()
        val explicit = explicitFailedHosts.any { it.normalizeHost() == normalized }
        val now = SystemClock.elapsedRealtime()
        return synchronized(lock) {
            pruneExpiredLocked(now)
            val record = failureRecords[normalized]
            val scopedRecord = record?.takeIf { it.streamRole.appliesTo(streamName) }
            val recordCooling = scopedRecord != null && scopedRecord.cooldownUntilMs > now
            val legacyExplicitCooling = explicit && record == null
            val cooling = recordCooling || legacyExplicitCooling
            val cooldownRemaining = when {
                recordCooling -> (scopedRecord!!.cooldownUntilMs - now).coerceAtLeast(0L)
                legacyExplicitCooling -> LEGACY_EXPLICIT_FAILURE_COOLDOWN_MS
                else -> 0L
            }
            val weak = normalized in weakBlacklistedHosts ||
                normalized.contains("mirrorcosov") ||
                (scopedRecord?.let { isWeakReason(it.lastReason) } == true)
            val preferred = normalized in preferredHostsByCid[cid].orEmpty()
            val badSource = scopedRecord?.badSource == true
            val failureCount = scopedRecord?.failureCount ?: if (legacyExplicitCooling) 1 else 0
            val preferredBonus = if (preferred) 200 else 0
            val stableBonus = when {
                normalized.contains("akamaized.net") -> 80
                normalized.contains("akamai") -> 60
                else -> 0
            }
            val weakPenalty = if (weak) 120 else 0
            val failurePenalty = if (cooling) 1_000 else 0
            val repeatedFailurePenalty = (failureCount * 20).coerceAtMost(100)
            val dynamic = dynamicScoreLocked(normalized, now)
            HostRouteStatus(
                host = normalized,
                score = 1_000 + preferredBonus + stableBonus + dynamic.score -
                    weakPenalty - failurePenalty - repeatedFailurePenalty,
                coolingDown = cooling,
                cooldownRemainingMs = cooldownRemaining,
                failureCount = failureCount,
                weak = weak,
                preferred = preferred,
                badSource = badSource,
                reason = scopedRecord?.lastReason.orEmpty(),
                dynamicScore = dynamic.score,
                dynamicConfidencePercent = dynamic.confidencePercent,
                performanceSamples = dynamic.samples,
                performanceSummary = dynamic.summary
            )
        }
    }

    fun sessionFailedHosts(streamName: String = "both"): Set<String> {
        val now = SystemClock.elapsedRealtime()
        return synchronized(lock) {
            pruneExpiredLocked(now)
            failureRecords
                .filterValues { it.cooldownUntilMs > now && it.streamRole.appliesTo(streamName) }
                .keys
                .toSet()
        }
    }

    fun badSourceHosts(streamName: String = "both"): Set<String> {
        val now = SystemClock.elapsedRealtime()
        return synchronized(lock) {
            pruneExpiredLocked(now)
            failureRecords
                .filterValues {
                    it.badSource && it.cooldownUntilMs > now && it.streamRole.appliesTo(streamName)
                }
                .keys
                .toSet()
        }
    }

    fun clearTransientFailures() {
        val now = SystemClock.elapsedRealtime()
        synchronized(lock) {
            pruneExpiredLocked(now)
            forcedProbeBudget = 1
            Log.i(
                TAG_ROUTER,
                "legacy reset converted to controlled probe; activeCooldowns=${activeCooldownHostsLocked(now)}, " +
                    "cooldownsPreserved=true"
            )
        }
    }

    fun isWeakBlacklisted(host: String): Boolean {
        val normalized = host.normalizeHost()
        return synchronized(lock) {
            normalized in weakBlacklistedHosts || normalized.contains("mirrorcosov")
        }
    }

    fun hostScore(
        host: String,
        cid: Long,
        explicitFailedHosts: Set<String>,
        streamName: String = "both"
    ): Int = routeStatus(host, cid, explicitFailedHosts, streamName).score

    fun failedHostPenalty(
        host: String,
        explicitFailedHosts: Set<String>,
        streamName: String = "both"
    ): Int = if (routeStatus(host, 0L, explicitFailedHosts, streamName).coolingDown) 1_000 else 0

    fun String.hostOnly(): String {
        if (isBlank()) return ""
        return runCatching { URI(this).host.orEmpty().normalizeHost() }.getOrDefault("")
    }

    private fun recordRouteFailureLocked(
        host: String,
        reason: String,
        badSource: Boolean,
        now: Long
    ) {
        val record = performanceRecords.getOrPut(host) { PerformanceRecord() }
        decayPerformanceLocked(record, now)
        val bufferingTimeout = reason.contains("BUFFERING_TIMEOUT", ignoreCase = true)
        record.routeFailures = (
            record.routeFailures + when {
                badSource -> BAD_SOURCE_ROUTE_FAILURE_WEIGHT
                bufferingTimeout -> BUFFERING_ROUTE_FAILURE_WEIGHT
                else -> GENERIC_ROUTE_FAILURE_WEIGHT
            }
        ).coerceAtMost(MAX_ROUTE_FAILURE_WEIGHT)
        if (bufferingTimeout) {
            record.bufferingTimeouts =
                (record.bufferingTimeouts + 1.0).coerceAtMost(MAX_BUFFERING_TIMEOUTS)
        }
        if (badSource) {
            record.badSourceFailures =
                (record.badSourceFailures + 1.0).coerceAtMost(MAX_BAD_SOURCE_FAILURES)
        }
        record.lastUpdatedAtMs = now
    }

    private fun recordStableSuccessLocked(host: String, now: Long) {
        val record = performanceRecords.getOrPut(host) { PerformanceRecord() }
        decayPerformanceLocked(record, now)
        if (now - record.lastStableSuccessAtMs >= STABLE_SUCCESS_DEDUP_MS) {
            record.stableSuccesses =
                (record.stableSuccesses + 1.0).coerceAtMost(MAX_STABLE_SUCCESSES)
            record.networkSuccesses =
                (record.networkSuccesses + STABLE_SUCCESS_NETWORK_WEIGHT)
                    .coerceAtMost(MAX_WEIGHTED_NETWORK_SAMPLES)
            record.lastStableSuccessAtMs = now
            record.lastUpdatedAtMs = now
        }
    }

    private fun dynamicScoreLocked(host: String, now: Long): DynamicScoreSnapshot {
        val record = performanceRecords[host]
            ?: return DynamicScoreSnapshot(
                score = 0,
                confidencePercent = 0,
                samples = 0,
                summary = "dynamicScore=0, confidence=0, samples=0, data=neutral"
            )
        decayPerformanceLocked(record, now)
        val ageMs = (now - record.lastUpdatedAtMs).coerceAtLeast(0L)
        val freshness = when {
            ageMs <= PERFORMANCE_FULL_WEIGHT_AGE_MS -> 1.0
            ageMs >= PERFORMANCE_ZERO_WEIGHT_AGE_MS -> 0.0
            else -> 1.0 - (
                ageMs - PERFORMANCE_FULL_WEIGHT_AGE_MS
            ).toDouble() / (
                PERFORMANCE_ZERO_WEIGHT_AGE_MS - PERFORMANCE_FULL_WEIGHT_AGE_MS
            ).toDouble()
        }
        val effectiveSamples = record.weightedNetworkSamples + record.qoeSessions +
            record.stableSuccesses * STABLE_SUCCESS_SAMPLE_WEIGHT
        val sampleConfidence = (effectiveSamples / PERFORMANCE_FULL_CONFIDENCE_SAMPLES)
            .coerceIn(0.0, 1.0)
        val confidence = (sampleConfidence * freshness).coerceIn(0.0, 1.0)

        var rawScore = 0.0
        rawScore += latencyScore(record.ewmaTtfbMs, TTFB_SCORE_THRESHOLDS)
        rawScore += latencyScore(record.ewmaConnectMs, CONNECT_SCORE_THRESHOLDS)
        rawScore += latencyScore(record.ewmaDnsMs, DNS_SCORE_THRESHOLDS)
        rawScore += latencyScore(record.ewmaStartupMs, STARTUP_SCORE_THRESHOLDS)

        if (record.connectionSamples >= MIN_REUSE_CONFIDENCE_SAMPLES) {
            val reuseRatio = (
                record.reusedConnections / record.connectionSamples.coerceAtLeast(0.0001)
            ).coerceIn(0.0, 1.0)
            rawScore += reuseRatio * MAX_REUSE_BONUS
        }
        rawScore += (record.stableSuccesses * STABLE_SUCCESS_BONUS_PER_EVENT)
            .coerceAtMost(MAX_STABLE_SUCCESS_BONUS)

        val totalNetworkOutcomes = record.networkSuccesses + record.networkFailures
        if (totalNetworkOutcomes >= MIN_FAILURE_RATIO_SAMPLES) {
            val failureRatio = (record.networkFailures / totalNetworkOutcomes).coerceIn(0.0, 1.0)
            rawScore -= failureRatio * MAX_NETWORK_FAILURE_PENALTY
        }
        rawScore -= (record.routeFailures * ROUTE_FAILURE_PENALTY_PER_WEIGHT)
            .coerceAtMost(MAX_ROUTE_FAILURE_PENALTY)
        rawScore -= (record.bufferingTimeouts * BUFFERING_TIMEOUT_PENALTY)
            .coerceAtMost(MAX_BUFFERING_TIMEOUT_PENALTY)
        rawScore -= (record.badSourceFailures * BAD_SOURCE_FAILURE_PENALTY)
            .coerceAtMost(MAX_BAD_SOURCE_FAILURE_PENALTY)
        rawScore -= (record.playbackErrors * PLAYBACK_ERROR_PENALTY)
            .coerceAtMost(MAX_PLAYBACK_ERROR_PENALTY)

        if (record.qoeSessions > 0) {
            val averageRebufferEvents = record.rebufferEvents.toDouble() / record.qoeSessions
            val averageRebufferMs = record.totalRebufferMs.toDouble() / record.qoeSessions
            rawScore -= (averageRebufferEvents * REBUFFER_EVENT_PENALTY)
                .coerceAtMost(MAX_REBUFFER_EVENT_PENALTY)
            rawScore -= (averageRebufferMs / 1_000.0 * REBUFFER_SECOND_PENALTY)
                .coerceAtMost(MAX_REBUFFER_TIME_PENALTY)
        }

        val finalScore = (rawScore * confidence)
            .roundToInt()
            .coerceIn(MIN_DYNAMIC_SCORE, MAX_DYNAMIC_SCORE)
        val confidencePercent = (confidence * 100.0).roundToInt().coerceIn(0, 100)
        val sampleCount = effectiveSamples.roundToInt().coerceAtLeast(0)
        val reusePercent = if (record.connectionSamples > 0.0) {
            (record.reusedConnections / record.connectionSamples * 100.0)
                .roundToInt()
                .coerceIn(0, 100)
        } else {
            0
        }
        return DynamicScoreSnapshot(
            score = finalScore,
            confidencePercent = confidencePercent,
            samples = sampleCount,
            summary = "dynamicScore=$finalScore, confidence=$confidencePercent, samples=$sampleCount, " +
                "ttfbMs=${record.ewmaTtfbMs.asLogMs()}, " +
                "connectMs=${record.ewmaConnectMs.asLogMs()}, " +
                "startupMs=${record.ewmaStartupMs.asLogMs()}, reusePercent=$reusePercent, " +
                "stable=${record.stableSuccesses.roundToInt()}, routeFailures=${record.routeFailures.roundToInt()}, " +
                "rebufferEvents=${record.rebufferEvents.roundToInt()}, " +
                "badSourceFailures=${record.badSourceFailures.roundToInt()}"
        )
    }

    private fun pruneExpiredLocked(now: Long) {
        val expiredFailures = failureRecords
            .filterValues { it.cooldownUntilMs <= now && now - it.lastFailureAtMs >= EXPIRED_RECORD_RETENTION_MS }
            .keys
        expiredFailures.forEach { host ->
            failureRecords.remove(host)
            if (host !in defaultWeakBlacklistedHosts) {
                weakBlacklistedHosts.remove(host)
            }
        }
        val expiredPerformance = performanceRecords
            .filterValues { now - it.lastUpdatedAtMs >= PERFORMANCE_RECORD_RETENTION_MS }
            .keys
        expiredPerformance.forEach { host -> performanceRecords.remove(host) }
    }

    private fun activeCooldownHostsLocked(now: Long): Map<String, Long> =
        failureRecords
            .filterValues { it.cooldownUntilMs > now }
            .mapValues { (_, value) -> (value.cooldownUntilMs - now).coerceAtLeast(0L) }

    private fun cooldownFor(reason: String, failureCount: Int): Long {
        val multiplier = failureCount.coerceIn(1, 3).toLong()
        return when {
            isBadSourceReason(reason) -> BAD_SOURCE_COOLDOWN_MS
            reason.contains("412") || reason.contains("429") -> BLOCKED_REQUEST_COOLDOWN_MS * multiplier
            reason.contains("403") -> HTTP_FORBIDDEN_COOLDOWN_MS * multiplier
            reason.contains("BUFFERING_TIMEOUT", ignoreCase = true) -> BUFFERING_TIMEOUT_COOLDOWN_MS * multiplier
            reason.contains("TIMEOUT", ignoreCase = true) -> NETWORK_TIMEOUT_COOLDOWN_MS * multiplier
            else -> GENERIC_FAILURE_COOLDOWN_MS * multiplier
        }.coerceAtMost(MAX_COOLDOWN_MS)
    }

    private fun decayPerformanceLocked(record: PerformanceRecord, now: Long) {
        val base = when {
            record.lastDecayAtMs > 0L -> record.lastDecayAtMs
            record.lastUpdatedAtMs > 0L -> record.lastUpdatedAtMs
            else -> now
        }
        val elapsedMs = (now - base).coerceAtLeast(0L)
        if (elapsedMs < PERFORMANCE_DECAY_MIN_STEP_MS) {
            if (record.lastDecayAtMs == 0L) record.lastDecayAtMs = now
            return
        }
        val decayFactor = 0.5.pow(
            elapsedMs.toDouble() / PERFORMANCE_SIGNAL_HALF_LIFE_MS.toDouble()
        ).coerceIn(0.0, 1.0)
        record.weightedNetworkSamples *= decayFactor
        record.networkSuccesses *= decayFactor
        record.networkFailures *= decayFactor
        record.connectionSamples *= decayFactor
        record.reusedConnections *= decayFactor
        record.qoeSessions *= decayFactor
        record.rebufferEvents *= decayFactor
        record.totalRebufferMs *= decayFactor
        record.playbackErrors *= decayFactor
        record.stableSuccesses *= decayFactor
        record.routeFailures *= decayFactor
        record.bufferingTimeouts *= decayFactor
        record.badSourceFailures *= decayFactor
        record.lastDecayAtMs = now
    }

    private fun updateEwma(current: Double?, sampleMs: Long?, alpha: Double): Double? {
        if (sampleMs == null || sampleMs < 0L) return current
        val sample = sampleMs.toDouble()
        return current?.let { previous ->
            previous + alpha.coerceIn(0.0, 1.0) * (sample - previous)
        } ?: sample
    }

    private fun latencyScore(valueMs: Double?, thresholds: List<Pair<Long, Int>>): Double {
        val value = valueMs ?: return 0.0
        return thresholds.firstOrNull { value <= it.first }?.second?.toDouble()
            ?: thresholds.lastOrNull()?.second?.toDouble().orZero()
    }

    private fun Double?.asLogMs(): Int = this?.roundToInt() ?: -1

    private fun Double?.orZero(): Double = this ?: 0.0

    private fun String.normalizeHost(): String = trim().lowercase()

    private fun isWeakReason(reason: String): Boolean {
        return reason.contains("TIMEOUT", ignoreCase = true) ||
            reason.contains("BUFFERING_TIMEOUT", ignoreCase = true) ||
            isBadSourceReason(reason)
    }

    private fun isBadSourceReason(reason: String): Boolean {
        return reason.contains("BAD_SOURCE_HOST", ignoreCase = true) ||
            reason.contains("ERROR_CODE_PARSING_CONTAINER_MALFORMED", ignoreCase = true) ||
            reason.contains("Invalid NAL length", ignoreCase = true) ||
            reason.contains("ParserException", ignoreCase = true)
    }

    private val DNS_SCORE_THRESHOLDS = listOf(
        30L to 12,
        80L to 7,
        180L to 2,
        350L to -6,
        Long.MAX_VALUE to -12
    )
    private val CONNECT_SCORE_THRESHOLDS = listOf(
        80L to 34,
        160L to 24,
        300L to 12,
        600L to 2,
        1_000L to -16,
        Long.MAX_VALUE to -34
    )
    private val TTFB_SCORE_THRESHOLDS = listOf(
        150L to 90,
        300L to 68,
        600L to 38,
        900L to 12,
        1_400L to -22,
        2_200L to -58,
        Long.MAX_VALUE to -94
    )
    private val STARTUP_SCORE_THRESHOLDS = listOf(
        700L to 48,
        1_200L to 32,
        2_000L to 10,
        3_000L to -22,
        4_500L to -48,
        Long.MAX_VALUE to -74
    )

    private const val MAX_FAILURE_COUNT = 8
    private const val GENERIC_FAILURE_COOLDOWN_MS = 15_000L
    private const val NETWORK_TIMEOUT_COOLDOWN_MS = 30_000L
    private const val BUFFERING_TIMEOUT_COOLDOWN_MS = 45_000L
    private const val HTTP_FORBIDDEN_COOLDOWN_MS = 60_000L
    private const val BLOCKED_REQUEST_COOLDOWN_MS = 90_000L
    private const val BAD_SOURCE_COOLDOWN_MS = 5 * 60_000L
    private const val MAX_COOLDOWN_MS = 5 * 60_000L
    private const val LEGACY_EXPLICIT_FAILURE_COOLDOWN_MS = 15_000L
    private const val CONTROLLED_PROBE_INTERVAL_MS = 15_000L
    private const val EXPIRED_RECORD_RETENTION_MS = 2 * 60_000L

    private const val PLAYBACK_NETWORK_SAMPLE_WEIGHT = 1.0
    private const val PRECONNECT_NETWORK_SAMPLE_WEIGHT = 0.35
    private const val PLAYBACK_NETWORK_FAILURE_WEIGHT = 1.0
    private const val PRECONNECT_NETWORK_FAILURE_WEIGHT = 0.2
    private const val PLAYBACK_EWMA_ALPHA = 0.35
    private const val PRECONNECT_EWMA_ALPHA = 0.18
    private const val QOE_EWMA_ALPHA = 0.35
    private const val MAX_WEIGHTED_NETWORK_SAMPLES = 24.0
    private const val MAX_QOE_SESSIONS = 20.0
    private const val MAX_REBUFFER_EVENTS = 40.0
    private const val MAX_TOTAL_REBUFFER_MS = 120_000.0
    private const val MAX_PLAYBACK_ERRORS = 8.0
    private const val MAX_STABLE_SUCCESSES = 12.0
    private const val STABLE_SUCCESS_DEDUP_MS = 5_000L
    private const val STABLE_SUCCESS_NETWORK_WEIGHT = 0.5
    private const val STABLE_SUCCESS_SAMPLE_WEIGHT = 0.5
    private const val PERFORMANCE_FULL_CONFIDENCE_SAMPLES = 6.0
    private const val PERFORMANCE_FULL_WEIGHT_AGE_MS = 10 * 60_000L
    private const val PERFORMANCE_ZERO_WEIGHT_AGE_MS = 60 * 60_000L
    private const val PERFORMANCE_RECORD_RETENTION_MS = 90 * 60_000L
    private const val PERFORMANCE_SIGNAL_HALF_LIFE_MS = 30 * 60_000L
    private const val PERFORMANCE_DECAY_MIN_STEP_MS = 30_000L
    private const val MIN_REUSE_CONFIDENCE_SAMPLES = 1.0
    private const val MAX_REUSE_BONUS = 20.0
    private const val STABLE_SUCCESS_BONUS_PER_EVENT = 10.0
    private const val MAX_STABLE_SUCCESS_BONUS = 40.0
    private const val MIN_FAILURE_RATIO_SAMPLES = 1.0
    private const val MAX_NETWORK_FAILURE_PENALTY = 70.0
    private const val BAD_SOURCE_ROUTE_FAILURE_WEIGHT = 3.0
    private const val BUFFERING_ROUTE_FAILURE_WEIGHT = 1.5
    private const val GENERIC_ROUTE_FAILURE_WEIGHT = 1.0
    private const val MAX_ROUTE_FAILURE_WEIGHT = 12.0
    private const val ROUTE_FAILURE_PENALTY_PER_WEIGHT = 14.0
    private const val MAX_ROUTE_FAILURE_PENALTY = 90.0
    private const val MAX_BUFFERING_TIMEOUTS = 6.0
    private const val BUFFERING_TIMEOUT_PENALTY = 18.0
    private const val MAX_BUFFERING_TIMEOUT_PENALTY = 54.0
    private const val MAX_BAD_SOURCE_FAILURES = 4.0
    private const val BAD_SOURCE_FAILURE_PENALTY = 44.0
    private const val MAX_BAD_SOURCE_FAILURE_PENALTY = 88.0
    private const val PLAYBACK_ERROR_PENALTY = 12.0
    private const val MAX_PLAYBACK_ERROR_PENALTY = 48.0
    private const val REBUFFER_EVENT_PENALTY = 14.0
    private const val MAX_REBUFFER_EVENT_PENALTY = 48.0
    private const val REBUFFER_SECOND_PENALTY = 2.0
    private const val MAX_REBUFFER_TIME_PENALTY = 32.0
    private const val MIN_DYNAMIC_SCORE = -180
    private const val MAX_DYNAMIC_SCORE = 180
}
