package com.bililite.tv.data.api

import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.bililite.tv.model.PlayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@OptIn(UnstableApi::class)
object BilibiliApiClient {
    private const val BASE_URL = "https://api.bilibili.com"
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
    const val REFERER = "https://www.bilibili.com/"
    const val ORIGIN = "https://www.bilibili.com"

    private const val API_CONNECT_TIMEOUT_SECONDS = 10L
    private const val API_READ_TIMEOUT_SECONDS = 10L
    private const val API_WRITE_TIMEOUT_SECONDS = 10L
    private const val MEDIA_CONNECT_TIMEOUT_SECONDS = 15L
    private const val MEDIA_READ_TIMEOUT_SECONDS = 30L
    private const val MEDIA_WRITE_TIMEOUT_SECONDS = 30L
    private const val PRECONNECT_CONNECT_TIMEOUT_MS = 1_200L
    private const val PRECONNECT_READ_TIMEOUT_MS = 1_200L
    private const val PRECONNECT_WRITE_TIMEOUT_MS = 1_200L
    private const val PRECONNECT_CALL_TIMEOUT_MS = 1_500L
    private const val PRECONNECT_ENTRY_TTL_MS = 60_000L
    private const val PRECONNECT_HOST_WARM_TTL_MS = 30_000L
    private const val MAX_PRECONNECT_ENTRIES = 24
    private const val PRECONNECT_MAX_BODY_BYTES = 4_096L
    private const val PRECONNECT_RANGE_HEADER = "bytes=0-0"

    /**
     * Shared application HTTP client. Keep a single pool/dispatcher for API,
     * playurl and media requests so repeated calls can reuse established
     * connections instead of creating an isolated network stack per player.
     */
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(API_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(API_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(API_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Media-specific timeouts with the same connection pool and dispatcher as
     * [httpClient]. OkHttpClient.newBuilder() preserves those shared resources
     * while allowing streaming timeouts and media-only network diagnostics.
     */
    val mediaHttpClient: OkHttpClient = httpClient.newBuilder()
        .connectTimeout(MEDIA_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(MEDIA_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(MEDIA_WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .eventListenerFactory(
            EventListener.Factory { call ->
                MediaNetworkEventListener(call)
            }
        )
        .build()

    /**
     * Short-lived range requests use the same dispatcher and connection pool as
     * real media traffic. A failed preconnect is diagnostic only and never
     * changes CDN scoring or playback recovery state.
     */
    private val preconnectHttpClient: OkHttpClient = mediaHttpClient.newBuilder()
        .connectTimeout(PRECONNECT_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(PRECONNECT_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(PRECONNECT_WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(PRECONNECT_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .eventListenerFactory(
            EventListener.Factory { call ->
                MediaNetworkEventListener(call)
            }
        )
        .build()

    /**
     * One process-wide Media3 factory. Per-playback headers are applied to each
     * newly created data source rather than mutating this shared factory. This
     * keeps Cookie/Referer isolation while avoiding repeated factory setup.
     */
    private val sharedMediaDataSourceFactory: OkHttpDataSource.Factory =
        OkHttpDataSource.Factory(mediaHttpClient)
            .setUserAgent(USER_AGENT)

    private val preconnectScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preconnectLock = Any()
    private val preconnectEntries = LinkedHashMap<Long, MediaPreconnectEntry>()
    private val preconnectWarmHostsUntilMs = LinkedHashMap<String, Long>()

    fun playbackDataSourceFactory(
        requestHeaders: Map<String, String>
    ): DataSource.Factory {
        val frozenHeaders = requestHeaders.toMap()
        return object : DataSource.Factory {
            override fun createDataSource(): DataSource {
                return sharedMediaDataSourceFactory.createDataSource().apply {
                    frozenHeaders.forEach { (name, value) ->
                        setRequestProperty(name, value)
                    }
                }
            }
        }
    }

    /**
     * Starts preconnect immediately after playurl selection. This method never
     * blocks its caller. The player later waits only for a small bounded budget.
     */
    fun startMediaPreconnect(playUrl: PlayUrl) {
        ensureMediaPreconnect(playUrl)
    }

    /**
     * Waits briefly for the already-running preconnect. Timeout does not cancel
     * the background work; playback proceeds while it completes independently.
     */
    suspend fun awaitMediaPreconnect(
        playUrl: PlayUrl,
        maxWaitMs: Long
    ): MediaPreconnectResult {
        val entry = synchronized(preconnectLock) {
            cleanupPreconnectEntriesLocked(System.currentTimeMillis())
            preconnectEntries[playUrl.requestId]
        } ?: return MediaPreconnectResult(
            requestId = playUrl.requestId,
            status = "not_scheduled",
            targetCount = 0,
            succeeded = 0,
            failed = 0,
            skipped = 0,
            elapsedMs = 0L,
            waitedMs = 0L
        )

        if (entry.deferred.isCompleted) {
            return entry.deferred.await().copy(waitedMs = 0L)
        }

        val waitStartedAtNs = System.nanoTime()
        val completed = withTimeoutOrNull(maxWaitMs.coerceAtLeast(1L)) {
            entry.deferred.await()
        }
        val waitedMs = elapsedMs(waitStartedAtNs)
        return completed?.copy(waitedMs = waitedMs) ?: MediaPreconnectResult(
            requestId = playUrl.requestId,
            status = "in_flight",
            targetCount = entry.targetCount,
            succeeded = 0,
            failed = 0,
            skipped = 0,
            elapsedMs = elapsedMs(entry.startedAtNs),
            waitedMs = waitedMs
        )
    }

    fun connectionPoolSnapshot(): String {
        val pool = httpClient.connectionPool
        return "connections=${pool.connectionCount()}, idle=${pool.idleConnectionCount()}"
    }

    fun mediaNetworkSnapshot(): String {
        val apiPool = httpClient.connectionPool
        val mediaPool = mediaHttpClient.connectionPool
        val preconnectPool = preconnectHttpClient.connectionPool
        return buildString {
            append("sharedPool=${apiPool === mediaPool && apiPool === preconnectPool}")
            append(", apiClientId=${identityId(httpClient)}")
            append(", mediaClientId=${identityId(mediaHttpClient)}")
            append(", preconnectClientId=${identityId(preconnectHttpClient)}")
            append(", poolId=${identityId(apiPool)}")
            append(", dataSourceFactoryId=${identityId(sharedMediaDataSourceFactory)}")
            append(", ")
            append(connectionPoolSnapshot())
            append(", ")
            append(MediaNetworkMetrics.snapshot())
        }
    }

    fun request(path: String): Request {
        return Request.Builder()
            .url("$BASE_URL$path")
            .header("User-Agent", USER_AGENT)
            .header("Referer", REFERER)
            .header("Origin", ORIGIN)
            .get()
            .build()
    }

    private fun ensureMediaPreconnect(playUrl: PlayUrl): MediaPreconnectEntry {
        val allTargets = buildPreconnectTargets(playUrl)
        val nowMs = System.currentTimeMillis()
        synchronized(preconnectLock) {
            cleanupPreconnectEntriesLocked(nowMs)
            cleanupWarmHostsLocked(nowMs)
            preconnectEntries[playUrl.requestId]?.let { return it }

            val targets = allTargets.filter { target ->
                (preconnectWarmHostsUntilMs[target.host] ?: 0L) <= nowMs
            }
            val startedAtNs = System.nanoTime()
            val deferred = preconnectScope.async(start = CoroutineStart.LAZY) {
                if (targets.isEmpty() && allTargets.isNotEmpty()) {
                    MediaPreconnectResult(
                        requestId = playUrl.requestId,
                        status = "skipped_warm",
                        targetCount = allTargets.size,
                        succeeded = 0,
                        failed = 0,
                        skipped = allTargets.size,
                        elapsedMs = elapsedMs(startedAtNs)
                    ).also { result ->
                        Log.i(
                            TAG_MEDIA_PRECONNECT,
                            "preconnect finish ${result.logSummary()}, " +
                                "warmHosts=${allTargets.joinToString(separator = "|") { it.host }}, " +
                                connectionPoolSnapshot()
                        )
                    }
                } else {
                    runMediaPreconnect(playUrl, targets, startedAtNs)
                }
            }
            val entry = MediaPreconnectEntry(
                startedAtNs = startedAtNs,
                createdAtMs = nowMs,
                targetCount = allTargets.size,
                deferred = deferred
            )
            preconnectEntries[playUrl.requestId] = entry
            Log.i(
                TAG_MEDIA_PRECONNECT,
                "preconnect scheduled requestId=${playUrl.requestId}, " +
                    "targets=${targets.joinToString(separator = "|") { "${it.roles}:${it.host}" }}, " +
                    "warmSkipped=${allTargets.size - targets.size}, " +
                    mediaNetworkSnapshot()
            )
            deferred.start()
            return entry
        }
    }

    private fun cleanupWarmHostsLocked(nowMs: Long) {
        val iterator = preconnectWarmHostsUntilMs.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value <= nowMs) {
                iterator.remove()
            }
        }
    }

    private fun cleanupPreconnectEntriesLocked(nowMs: Long) {
        val iterator = preconnectEntries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            if (nowMs - entry.createdAtMs > PRECONNECT_ENTRY_TTL_MS) {
                iterator.remove()
            }
        }
        while (preconnectEntries.size >= MAX_PRECONNECT_ENTRIES) {
            val oldestKey = preconnectEntries.keys.firstOrNull() ?: break
            preconnectEntries.remove(oldestKey)
        }
    }

    private fun buildPreconnectTargets(playUrl: PlayUrl): List<MediaPreconnectTarget> {
        val targetsByHost = LinkedHashMap<String, MediaPreconnectTarget>()
        val candidates = buildList {
            add("video" to playUrl.videoUrl)
            playUrl.audioUrl
                ?.takeIf { it.isNotBlank() }
                ?.let { add("audio" to it) }
        }

        candidates.forEach { (role, url) ->
            val httpUrl = url.toHttpUrlOrNull() ?: return@forEach
            val host = httpUrl.host
            val existing = targetsByHost[host]
            targetsByHost[host] = if (existing == null) {
                MediaPreconnectTarget(
                    host = host,
                    url = url,
                    roles = role
                )
            } else {
                existing.copy(roles = "${existing.roles}+$role")
            }
        }
        return targetsByHost.values.toList()
    }

    private suspend fun runMediaPreconnect(
        playUrl: PlayUrl,
        targets: List<MediaPreconnectTarget>,
        startedAtNs: Long
    ): MediaPreconnectResult {
        if (targets.isEmpty()) {
            return MediaPreconnectResult(
                requestId = playUrl.requestId,
                status = "skipped",
                targetCount = 0,
                succeeded = 0,
                failed = 0,
                skipped = 0,
                elapsedMs = elapsedMs(startedAtNs)
            )
        }

        val results = coroutineScope {
            targets.map { target ->
                async {
                    executePreconnectTarget(playUrl, target)
                }
            }.awaitAll()
        }
        val succeeded = results.count { it.status == PreconnectTargetStatus.SUCCEEDED }
        val failed = results.count { it.status == PreconnectTargetStatus.FAILED }
        val skipped = results.count { it.status == PreconnectTargetStatus.SKIPPED }
        val status = when {
            succeeded == targets.size -> "completed"
            succeeded > 0 -> "partial"
            skipped == targets.size -> "skipped"
            else -> "failed"
        }
        return MediaPreconnectResult(
            requestId = playUrl.requestId,
            status = status,
            targetCount = targets.size,
            succeeded = succeeded,
            failed = failed,
            skipped = skipped,
            elapsedMs = elapsedMs(startedAtNs)
        ).also { result ->
            Log.i(
                TAG_MEDIA_PRECONNECT,
                "preconnect finish ${result.logSummary()}, ${connectionPoolSnapshot()}"
            )
        }
    }

    private fun executePreconnectTarget(
        playUrl: PlayUrl,
        target: MediaPreconnectTarget
    ): MediaPreconnectTargetResult {
        val request = runCatching {
            Request.Builder()
                .url(target.url)
                .header("User-Agent", playUrl.userAgent)
                .header("Referer", playUrl.referer)
                .header("Origin", playUrl.origin)
                .header("Accept", "*/*")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Accept-Encoding", "identity")
                .header("Range", PRECONNECT_RANGE_HEADER)
                .apply {
                    if (playUrl.cookieHeader.isNotBlank()) {
                        header("Cookie", playUrl.cookieHeader)
                    }
                }
                .tag(MediaRequestKind::class.java, MediaRequestKind.PRECONNECT)
                .get()
                .build()
        }.getOrElse { error ->
            Log.w(
                TAG_MEDIA_PRECONNECT,
                "preconnect skipped requestId=${playUrl.requestId}, roles=${target.roles}, " +
                    "host=${target.host}, reason=${safeError(error)}"
            )
            return MediaPreconnectTargetResult(PreconnectTargetStatus.SKIPPED)
        }

        return runCatching {
            preconnectHttpClient.newCall(request).execute().use { response ->
                val drainResult = drainSmallResponseBody(response)
                val accepted = response.isSuccessful && drainResult.fullyDrained
                val status = if (accepted) {
                    PreconnectTargetStatus.SUCCEEDED
                } else {
                    PreconnectTargetStatus.FAILED
                }
                val log =
                    "preconnect target requestId=${playUrl.requestId}, roles=${target.roles}, " +
                        "host=${target.host}, status=${status.logName}, code=${response.code}, " +
                        "bytes=${drainResult.bytesRead}, fullyDrained=${drainResult.fullyDrained}, " +
                        connectionPoolSnapshot()
                if (accepted) {
                    synchronized(preconnectLock) {
                        preconnectWarmHostsUntilMs[target.host] =
                            System.currentTimeMillis() + PRECONNECT_HOST_WARM_TTL_MS
                    }
                    Log.i(TAG_MEDIA_PRECONNECT, log)
                } else {
                    Log.w(TAG_MEDIA_PRECONNECT, log)
                }
                MediaPreconnectTargetResult(status)
            }
        }.getOrElse { error ->
            Log.w(
                TAG_MEDIA_PRECONNECT,
                "preconnect target requestId=${playUrl.requestId}, roles=${target.roles}, " +
                    "host=${target.host}, status=failed, error=${safeError(error)}, " +
                    connectionPoolSnapshot()
            )
            MediaPreconnectTargetResult(PreconnectTargetStatus.FAILED)
        }
    }

    private fun drainSmallResponseBody(response: Response): BodyDrainResult {
        val body = response.body ?: return BodyDrainResult(fullyDrained = true, bytesRead = 0L)
        val declaredLength = body.contentLength()
        if (declaredLength > PRECONNECT_MAX_BODY_BYTES) {
            return BodyDrainResult(fullyDrained = false, bytesRead = 0L)
        }

        val source = body.source()
        val buffer = Buffer()
        var totalBytes = 0L
        while (totalBytes <= PRECONNECT_MAX_BODY_BYTES) {
            val remaining = PRECONNECT_MAX_BODY_BYTES + 1L - totalBytes
            val read = source.read(buffer, remaining)
            if (read == -1L) {
                return BodyDrainResult(fullyDrained = true, bytesRead = totalBytes)
            }
            totalBytes += read
            buffer.clear()
        }
        return BodyDrainResult(fullyDrained = false, bytesRead = totalBytes)
    }

    private fun identityId(value: Any): String {
        return Integer.toHexString(System.identityHashCode(value))
    }

}

data class MediaPreconnectResult(
    val requestId: Long,
    val status: String,
    val targetCount: Int,
    val succeeded: Int,
    val failed: Int,
    val skipped: Int,
    val elapsedMs: Long,
    val waitedMs: Long = 0L
) {
    fun logSummary(): String {
        return "requestId=$requestId, status=$status, targets=$targetCount, " +
            "succeeded=$succeeded, failed=$failed, skipped=$skipped, " +
            "elapsedMs=$elapsedMs, waitedMs=$waitedMs"
    }
}

private data class MediaPreconnectEntry(
    val startedAtNs: Long,
    val createdAtMs: Long,
    val targetCount: Int,
    val deferred: Deferred<MediaPreconnectResult>
)

private data class MediaPreconnectTarget(
    val host: String,
    val url: String,
    val roles: String
)

private data class MediaPreconnectTargetResult(
    val status: PreconnectTargetStatus
)

private data class BodyDrainResult(
    val fullyDrained: Boolean,
    val bytesRead: Long
)

private enum class PreconnectTargetStatus(val logName: String) {
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    SKIPPED("skipped")
}

private enum class MediaRequestKind(val logName: String) {
    PLAYBACK("playback"),
    PRECONNECT("preconnect");

    fun toObservationSource(): BilibiliCdnPreference.NetworkObservationSource {
        return when (this) {
            PLAYBACK -> BilibiliCdnPreference.NetworkObservationSource.PLAYBACK
            PRECONNECT -> BilibiliCdnPreference.NetworkObservationSource.PRECONNECT
        }
    }
}

private class MediaNetworkEventListener(
    call: Call
) : EventListener() {
    private val callId = MediaNetworkMetrics.nextCallId()
    private val requestKind = call.request().tag(MediaRequestKind::class.java)
        ?: MediaRequestKind.PLAYBACK
    private val host = call.request().url.host
    private val path = call.request().url.encodedPath
    private val startedAtNs = System.nanoTime()
    private var dnsStartedAtNs = 0L
    private var connectStartedAtNs = 0L
    private var tlsStartedAtNs = 0L
    private var dnsDurationMs: Long? = null
    private var connectDurationMs: Long? = null
    private var tlsDurationMs: Long? = null
    private var connectAttempts = 0
    private var acquiredConnections = 0
    private var lastConnectionId = "none"
    private var lastConnectionReused = false
    private var responseCode = 0
    private var responseContentType = ""
    private var responseBytes = 0L
    private var routeObservationRecorded = false
    private var canceledByCaller = false
    private var finished = false

    override fun callStart(call: Call) {
        MediaNetworkMetrics.recordCallStarted(requestKind)
        Log.i(
            TAG_MEDIA_NETWORK,
            "call start id=$callId, kind=${requestKind.logName}, method=${call.request().method}, " +
                "host=$host, path=${safePath(path)}, " +
                "range=${call.request().header("Range").orEmpty()}, " +
                BilibiliApiClient.connectionPoolSnapshot()
        )
    }

    override fun dnsStart(call: Call, domainName: String) {
        dnsStartedAtNs = System.nanoTime()
        Log.d(
            TAG_MEDIA_NETWORK,
            "dns start id=$callId, kind=${requestKind.logName}, host=$domainName"
        )
    }

    override fun dnsEnd(
        call: Call,
        domainName: String,
        inetAddressList: List<java.net.InetAddress>
    ) {
        val addresses = inetAddressList.joinToString(separator = "|") { address ->
            address.hostAddress.orEmpty()
        }
        dnsDurationMs = elapsedMs(dnsStartedAtNs)
        Log.i(
            TAG_MEDIA_NETWORK,
            "dns end id=$callId, kind=${requestKind.logName}, host=$domainName, " +
                "elapsedMs=$dnsDurationMs, addresses=$addresses"
        )
    }

    override fun connectStart(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy
    ) {
        connectAttempts += 1
        connectStartedAtNs = System.nanoTime()
        Log.i(
            TAG_MEDIA_NETWORK,
            "connect start id=$callId, kind=${requestKind.logName}, attempt=$connectAttempts, " +
                "host=$host, ip=${inetSocketAddress.address?.hostAddress.orEmpty()}, " +
                "port=${inetSocketAddress.port}, proxy=${proxy.type()}"
        )
    }

    override fun secureConnectStart(call: Call) {
        tlsStartedAtNs = System.nanoTime()
        Log.d(
            TAG_MEDIA_NETWORK,
            "tls start id=$callId, kind=${requestKind.logName}, host=$host"
        )
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        tlsDurationMs = elapsedMs(tlsStartedAtNs)
        Log.i(
            TAG_MEDIA_NETWORK,
            "tls end id=$callId, kind=${requestKind.logName}, host=$host, " +
                "elapsedMs=$tlsDurationMs, " +
                "version=${handshake?.tlsVersion?.javaName.orEmpty()}, " +
                "cipher=${handshake?.cipherSuite?.javaName.orEmpty()}"
        )
    }

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?
    ) {
        connectDurationMs = elapsedMs(connectStartedAtNs)
        Log.i(
            TAG_MEDIA_NETWORK,
            "connect end id=$callId, kind=${requestKind.logName}, attempt=$connectAttempts, " +
                "host=$host, elapsedMs=$connectDurationMs, " +
                "protocol=${protocol?.toString().orEmpty()}"
        )
    }

    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ioe: IOException
    ) {
        Log.w(
            TAG_MEDIA_NETWORK,
            "connect failed id=$callId, kind=${requestKind.logName}, attempt=$connectAttempts, " +
                "host=$host, ip=${inetSocketAddress.address?.hostAddress.orEmpty()}, " +
                "elapsedMs=${elapsedMs(connectStartedAtNs)}, error=${safeError(ioe)}"
        )
    }

    override fun connectionAcquired(call: Call, connection: Connection) {
        acquiredConnections += 1
        val reused = connectAttempts == 0
        lastConnectionReused = reused
        lastConnectionId = Integer.toHexString(System.identityHashCode(connection))
        MediaNetworkMetrics.recordConnectionAcquired(requestKind, reused)
        Log.i(
            TAG_MEDIA_NETWORK,
            "connection acquired id=$callId, kind=${requestKind.logName}, host=$host, " +
                "connectionId=$lastConnectionId, reused=$reused, " +
                "protocol=${connection.protocol()}, acquisitions=$acquiredConnections, " +
                BilibiliApiClient.connectionPoolSnapshot()
        )
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        responseCode = response.code
        responseContentType = response.header("Content-Type").orEmpty()
        val ttfbMs = elapsedMs(startedAtNs)
        if (!routeObservationRecorded) {
            routeObservationRecorded = true
            BilibiliCdnPreference.recordNetworkObservation(
                host = host,
                source = requestKind.toObservationSource(),
                dnsMs = dnsDurationMs,
                connectMs = connectDurationMs,
                tlsMs = tlsDurationMs,
                ttfbMs = ttfbMs,
                reusedConnection = lastConnectionReused,
                responseCode = responseCode
            )
        }
        Log.i(
            TAG_MEDIA_NETWORK,
            "response headers id=$callId, kind=${requestKind.logName}, host=$host, " +
                "code=$responseCode, contentType=$responseContentType, " +
                "connectionId=$lastConnectionId, elapsedMs=$ttfbMs"
        )
    }

    override fun responseBodyEnd(call: Call, byteCount: Long) {
        responseBytes = byteCount
        Log.d(
            TAG_MEDIA_NETWORK,
            "response body end id=$callId, kind=${requestKind.logName}, host=$host, " +
                "bytes=$byteCount, elapsedMs=${elapsedMs(startedAtNs)}"
        )
    }

    override fun connectionReleased(call: Call, connection: Connection) {
        Log.d(
            TAG_MEDIA_NETWORK,
            "connection released id=$callId, kind=${requestKind.logName}, host=$host, " +
                "connectionId=${Integer.toHexString(System.identityHashCode(connection))}, " +
                BilibiliApiClient.connectionPoolSnapshot()
        )
    }

    override fun callEnd(call: Call) {
        finish(outcome = CallOutcome.SUCCEEDED, error = "")
    }

    override fun callFailed(call: Call, ioe: IOException) {
        val canceled = canceledByCaller ||
            ioe.message.orEmpty().equals("Canceled", ignoreCase = true)
        finish(
            outcome = if (canceled) CallOutcome.CANCELED else CallOutcome.FAILED,
            error = safeError(ioe)
        )
    }

    override fun canceled(call: Call) {
        canceledByCaller = true
        Log.d(
            TAG_MEDIA_NETWORK,
            "call canceled id=$callId, kind=${requestKind.logName}, host=$host, " +
                "elapsedMs=${elapsedMs(startedAtNs)}"
        )
    }

    private fun finish(outcome: CallOutcome, error: String) {
        if (finished) return
        finished = true
        MediaNetworkMetrics.recordCallFinished(requestKind, outcome)
        if (
            outcome == CallOutcome.FAILED &&
            !routeObservationRecorded &&
            !canceledByCaller
        ) {
            routeObservationRecorded = true
            BilibiliCdnPreference.recordNetworkFailure(
                host = host,
                source = requestKind.toObservationSource(),
                reason = error
            )
        }
        Log.i(
            TAG_MEDIA_NETWORK,
            "call finish id=$callId, kind=${requestKind.logName}, host=$host, " +
                "outcome=${outcome.logName}, code=$responseCode, " +
                "contentType=$responseContentType, bytes=$responseBytes, " +
                "connectAttempts=$connectAttempts, connectionId=$lastConnectionId, " +
                "elapsedMs=${elapsedMs(startedAtNs)}, error=$error, " +
                BilibiliApiClient.connectionPoolSnapshot()
        )
    }
}

private object MediaNetworkMetrics {
    private val callSequence = AtomicLong(0L)
    private val callsStarted = AtomicLong(0L)
    private val callsSucceeded = AtomicLong(0L)
    private val callsFailed = AtomicLong(0L)
    private val callsCanceled = AtomicLong(0L)
    private val connectionsAcquired = AtomicLong(0L)
    private val reusedConnections = AtomicLong(0L)
    private val newConnections = AtomicLong(0L)
    private val preconnectCallsStarted = AtomicLong(0L)
    private val preconnectCallsSucceeded = AtomicLong(0L)
    private val preconnectCallsFailed = AtomicLong(0L)
    private val preconnectCallsCanceled = AtomicLong(0L)
    private val preconnectConnectionsAcquired = AtomicLong(0L)
    private val preconnectReusedConnections = AtomicLong(0L)
    private val preconnectNewConnections = AtomicLong(0L)

    fun nextCallId(): Long = callSequence.incrementAndGet()

    fun recordCallStarted(kind: MediaRequestKind) {
        when (kind) {
            MediaRequestKind.PLAYBACK -> callsStarted.incrementAndGet()
            MediaRequestKind.PRECONNECT -> preconnectCallsStarted.incrementAndGet()
        }
    }

    fun recordCallFinished(kind: MediaRequestKind, outcome: CallOutcome) {
        when (kind) {
            MediaRequestKind.PLAYBACK -> when (outcome) {
                CallOutcome.SUCCEEDED -> callsSucceeded.incrementAndGet()
                CallOutcome.FAILED -> callsFailed.incrementAndGet()
                CallOutcome.CANCELED -> callsCanceled.incrementAndGet()
            }

            MediaRequestKind.PRECONNECT -> when (outcome) {
                CallOutcome.SUCCEEDED -> preconnectCallsSucceeded.incrementAndGet()
                CallOutcome.FAILED -> preconnectCallsFailed.incrementAndGet()
                CallOutcome.CANCELED -> preconnectCallsCanceled.incrementAndGet()
            }
        }
    }

    fun recordConnectionAcquired(kind: MediaRequestKind, reused: Boolean) {
        when (kind) {
            MediaRequestKind.PLAYBACK -> {
                connectionsAcquired.incrementAndGet()
                if (reused) {
                    reusedConnections.incrementAndGet()
                } else {
                    newConnections.incrementAndGet()
                }
            }

            MediaRequestKind.PRECONNECT -> {
                preconnectConnectionsAcquired.incrementAndGet()
                if (reused) {
                    preconnectReusedConnections.incrementAndGet()
                } else {
                    preconnectNewConnections.incrementAndGet()
                }
            }
        }
    }

    fun snapshot(): String {
        return "mediaCalls=${callsStarted.get()}, succeeded=${callsSucceeded.get()}, " +
            "failed=${callsFailed.get()}, canceled=${callsCanceled.get()}, " +
            "acquired=${connectionsAcquired.get()}, reused=${reusedConnections.get()}, " +
            "new=${newConnections.get()}, " +
            "preconnectCalls=${preconnectCallsStarted.get()}, " +
            "preconnectSucceeded=${preconnectCallsSucceeded.get()}, " +
            "preconnectFailed=${preconnectCallsFailed.get()}, " +
            "preconnectCanceled=${preconnectCallsCanceled.get()}, " +
            "preconnectAcquired=${preconnectConnectionsAcquired.get()}, " +
            "preconnectReused=${preconnectReusedConnections.get()}, " +
            "preconnectNew=${preconnectNewConnections.get()}"
    }
}

private enum class CallOutcome(val logName: String) {
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    CANCELED("canceled")
}

private fun elapsedMs(startedAtNs: Long): Long {
    if (startedAtNs <= 0L) return 0L
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNs)
}

private fun safePath(path: String): String {
    if (path.isBlank()) return "/"
    return path.take(MAX_LOG_PATH_LENGTH)
}

private fun safeError(error: Throwable): String {
    val message = error.message.orEmpty().replace('\n', ' ')
    return "${error.javaClass.simpleName}:${message.take(MAX_LOG_ERROR_LENGTH)}"
}

private const val TAG_MEDIA_NETWORK = "BiliMediaNet"
private const val TAG_MEDIA_PRECONNECT = "BiliMediaPreconnect"
private const val MAX_LOG_PATH_LENGTH = 96
private const val MAX_LOG_ERROR_LENGTH = 160
