package com.nblt.tv.ui.player

import android.graphics.Color as AndroidColor
import android.util.Log
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.nblt.tv.data.api.BilibiliApiClient
import com.nblt.tv.model.PlayUrl
import kotlinx.coroutines.delay
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(UnstableApi::class)
@Composable
internal fun VideoPlayerView(
    playUrl: PlayUrl,
    initialSeekPositionMs: Long,
    playbackSpeed: Float,
    resumePlaybackAfterSourceChange: Boolean,
    decoderMode: VideoDecoderMode,
    decoderRetryToken: Int,
    decoderPlayerGeneration: Int,
    exitRequested: Boolean,
    modifier: Modifier = Modifier,
    onPlayerStateChanged: (PlayerUiState) -> Unit,
    onPlayerControllerReady: (PlayerController) -> Unit,
    onControlsRequested: () -> Unit,
    onSeekHint: (String) -> Unit,
    onInitialSeekApplied: () -> Unit,
    onRenderedFirstFrame: () -> Unit,
    onRecoverablePlaybackError: (String, Long, String?, Int) -> Unit,
    onPlaybackEnded: () -> Unit,
    onPlaybackError: (String) -> Unit,
    onSafeExitCompleted: () -> Unit
) {
    val context = LocalContext.current
    val bufferPolicy = remember(context) {
        context.applicationContext.resolvePlaybackBufferPolicy()
    }
    val latestOnPlayerStateChanged = rememberUpdatedState(onPlayerStateChanged)
    val latestOnControlsRequested = rememberUpdatedState(onControlsRequested)
    val latestOnSeekHint = rememberUpdatedState(onSeekHint)
    val latestOnInitialSeekApplied = rememberUpdatedState(onInitialSeekApplied)
    val latestOnRenderedFirstFrame = rememberUpdatedState(onRenderedFirstFrame)
    val latestOnRecoverablePlaybackError = rememberUpdatedState(onRecoverablePlaybackError)
    val latestOnPlaybackEnded = rememberUpdatedState(onPlaybackEnded)
    val latestOnPlaybackError = rememberUpdatedState(onPlaybackError)
    val latestOnSafeExitCompleted = rememberUpdatedState(onSafeExitCompleted)
    val latestPlaybackSpeed = rememberUpdatedState(playbackSpeed)
    val latestResumePlaybackAfterSourceChange = rememberUpdatedState(
        resumePlaybackAfterSourceChange
    )

    val playerViewRef = remember { arrayOfNulls<PlayerView>(1) }
    val adaptiveCodecSelector = remember { AdaptiveVideoMediaCodecSelector() }
    adaptiveCodecSelector.updateMode(decoderMode)
    val safeExitStarted = remember { AtomicBoolean(false) }
    val releaseCompleted = remember { AtomicBoolean(false) }

    var activePlayUrl by remember { mutableStateOf<PlayUrl?>(null) }
    var activeQoeTracker by remember { mutableStateOf<PlaybackQoeTracker?>(null) }
    var activeRequestId by remember { mutableLongStateOf(NO_ACTIVE_REQUEST_ID) }
    var finishedRequestId by remember { mutableLongStateOf(NO_ACTIVE_REQUEST_ID) }
    var pendingSourcePositionMs by remember { mutableLongStateOf(0L) }
    var sourceChangeInProgress by remember { mutableStateOf(false) }
    var sourceReadyNotified by remember { mutableStateOf(false) }

    val player = remember(context, bufferPolicy, adaptiveCodecSelector) {
        val renderersFactory = DefaultRenderersFactory(context)
            .setMediaCodecSelector(adaptiveCodecSelector)
            .setEnableDecoderFallback(true)

        Log.i(
            TAG_QOE,
            "buffer policy network=${bufferPolicy.networkClass}, " +
                "min=${bufferPolicy.minBufferMs}, max=${bufferPolicy.maxBufferMs}, " +
                "start=${bufferPolicy.bufferForPlaybackMs}, " +
                "rebuffer=${bufferPolicy.bufferForPlaybackAfterRebufferMs}"
        )

        ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        bufferPolicy.minBufferMs,
                        bufferPolicy.maxBufferMs,
                        bufferPolicy.bufferForPlaybackMs,
                        bufferPolicy.bufferForPlaybackAfterRebufferMs
                    )
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()
            )
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .also { exoPlayer ->
                exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                Log.i(
                    TAG_BEHAVIOR,
                    "single player instance created playerId=${identityId(exoPlayer)}, " +
                        "playerGeneration=$decoderPlayerGeneration"
                )
            }
    }

    fun finishActiveQoeOnce() {
        val currentRequestId = activeRequestId
        if (
            currentRequestId != NO_ACTIVE_REQUEST_ID &&
            finishedRequestId != currentRequestId
        ) {
            activeQoeTracker?.finish(player)
            Log.i(
                TAG_MEDIA_NETWORK,
                "playback network finish requestId=$currentRequestId, " +
                    BilibiliApiClient.mediaNetworkSnapshot()
            )
            finishedRequestId = currentRequestId
        }
    }

    fun buildMediaSource(source: PlayUrl): MediaSource {
        val requestHeaders = buildMap {
            put("User-Agent", source.userAgent)
            put("Referer", source.referer)
            put("Origin", source.origin)
            put("Accept", "*/*")
            put("Accept-Language", "zh-CN,zh;q=0.9")
            if (source.cookieHeader.isNotBlank()) {
                put("Cookie", source.cookieHeader)
            }
        }
        val safeLogHeaders = mapOf(
            "User-Agent" to source.userAgent,
            "Referer" to source.referer,
            "Origin" to source.origin,
            "Accept" to "*/*",
            "Accept-Language" to "zh-CN,zh;q=0.9",
            "Cookie" to source.cookieHeader.isNotBlank().toString()
        )
        val dataSourceFactory = BilibiliApiClient.playbackDataSourceFactory(requestHeaders)

        Log.i(
            TAG,
            "Creating MediaSource on retained player: playerId=${identityId(player)}, " +
                "videoHost=${source.videoUrl.hostOnly()}, " +
                "audioHost=${source.audioUrl.orEmpty().hostOnly()}, " +
                "quality=${source.quality?.qn ?: 0}/${source.quality?.description.orEmpty()}, " +
                "sourceType=${source.sourceType}, codec=${source.videoCodec.orEmpty()}, " +
                "headers=$safeLogHeaders"
        )
        Log.i(
            TAG_STREAM_DEBUG,
            "stream request headers: referer=${source.referer}, hasCookie=${source.cookieHeader.isNotBlank()}, " +
                "videoHost=${source.videoUrl.hostOnly()}, audioHost=${source.audioUrl.orEmpty().hostOnly()}, " +
                "videoCandidateIndex=${source.selectedVideoUrlIndex}, videoCandidateCount=${source.videoUrlCandidates.size}, " +
                "audioCandidateIndex=${source.selectedAudioUrlIndex}, audioCandidateCount=${source.audioUrlCandidates.size}, " +
                "urlExpiring=${source.hasExpiringUrlParams}, estimatedExpire=${source.estimatedExpireTimeSeconds ?: 0}"
        )
        Log.i(
            TAG_WEB_LIKE,
            "web-like stream config: networkStack=okhttp, sharedFactory=true, " +
                "connectTimeoutMs=$STREAM_CONNECT_TIMEOUT_MS, readTimeoutMs=$STREAM_READ_TIMEOUT_MS, " +
                "redirects=true, range=exo-default, ${BilibiliApiClient.mediaNetworkSnapshot()}, " +
                "selected video host=${source.videoUrl.hostOnly()}, selected audio host=${source.audioUrl.orEmpty().hostOnly()}"
        )

        val mediaSourceFactory = ProgressiveMediaSource.Factory(dataSourceFactory)
        val videoSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(source.videoUrl))
        return source.audioUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { audioUrl ->
                MergingMediaSource(
                    videoSource,
                    mediaSourceFactory.createMediaSource(MediaItem.fromUri(audioUrl))
                )
            }
            ?: videoSource
    }

    LaunchedEffect(playUrl.requestId, decoderRetryToken) {
        if (exitRequested || releaseCompleted.get()) {
            return@LaunchedEffect
        }

        val isInitialSource = activeRequestId == NO_ACTIVE_REQUEST_ID
        val previousRequestId = activeRequestId
        val isSameRequestDecoderRetry =
            !isInitialSource && previousRequestId == playUrl.requestId && decoderRetryToken > 0
        if (!isInitialSource && previousRequestId != playUrl.requestId) {
            finishActiveQoeOnce()
        }

        activePlayUrl = playUrl
        activeRequestId = playUrl.requestId
        if (!isSameRequestDecoderRetry || activeQoeTracker == null) {
            activeQoeTracker = PlaybackQoeTracker(playUrl)
        }
        finishedRequestId = NO_ACTIVE_REQUEST_ID
        pendingSourcePositionMs = initialSeekPositionMs.coerceAtLeast(0L)
        sourceChangeInProgress = true
        sourceReadyNotified = false

        val preconnectResult = BilibiliApiClient.awaitMediaPreconnect(
            playUrl = playUrl,
            maxWaitMs = MEDIA_PRECONNECT_AWAIT_BUDGET_MS
        )
        Log.i(
            TAG_MEDIA_PRECONNECT,
            "preconnect gate ${preconnectResult.logSummary()}, " +
                BilibiliApiClient.connectionPoolSnapshot()
        )

        val mediaSource = buildMediaSource(playUrl)
        val shouldPlay = if (isInitialSource) {
            true
        } else {
            latestResumePlaybackAfterSourceChange.value
        }

        Log.i(
            TAG_BEHAVIOR,
            "single player source change begin playerId=${identityId(player)}, " +
                "fromRequestId=$previousRequestId, toRequestId=${playUrl.requestId}, " +
                "positionMs=$pendingSourcePositionMs, playWhenReady=$shouldPlay, " +
                "keepSurface=true, decoderMode=$decoderMode, " +
                "decoderRetryToken=$decoderRetryToken, " +
                    "playerGeneration=$decoderPlayerGeneration"
        )
        Log.i(
            TAG_DEBUG,
            "enter playback source: requested qn=${playUrl.requestedQn}, " +
                "selected qn=${playUrl.quality?.qn ?: 0}, " +
                "selected format=${playUrl.selectedFormat.orEmpty()}, " +
                "selected codec=${playUrl.videoCodec.orEmpty()}, " +
                "selected url type=${playUrl.sourceType}, " +
                "video url host=${playUrl.videoUrl.hostOnly()}, " +
                "audio url host=${playUrl.audioUrl.orEmpty().hostOnly()}, " +
                "hasCookie=${playUrl.cookieHeader.isNotBlank()}, " +
                "hasSessData=${playUrl.cookieHeader.contains("SESSDATA")}, " +
                "hasReferer=${playUrl.referer.isNotBlank()}, " +
                "startPositionMs=$pendingSourcePositionMs"
        )
        Log.i(
            TAG_MEDIA_NETWORK,
            "playback network start requestId=${playUrl.requestId}, " +
                BilibiliApiClient.mediaNetworkSnapshot()
        )

        if (isSameRequestDecoderRetry) {
            player.playWhenReady = false
            player.stop()
            val settleMs = if (
                decoderMode == VideoDecoderMode.AUTO_HARDWARE_FIRST &&
                DeviceCapabilityProfiler.isLegacyAmlogicRuntime()
            ) {
                LEGACY_AMLOGIC_HARDWARE_RETRY_SETTLE_MS
            } else {
                0L
            }
            if (settleMs > 0L) {
                delay(settleMs)
            }
            Log.i(
                TAG_DECODER_SELECTOR,
                "released failed decoder before same-source retry playerId=${identityId(player)}, " +
                    "decoderMode=$decoderMode, positionMs=$pendingSourcePositionMs, " +
                    "settleMs=$settleMs"
            )
        }

        player.playWhenReady = shouldPlay
        player.setMediaSource(mediaSource, pendingSourcePositionMs)
        player.setPlaybackSpeed(latestPlaybackSpeed.value)
        player.prepare()
        Log.i(TAG_SPEED, "speed applied to retained ExoPlayer=${latestPlaybackSpeed.value}")
    }

    LaunchedEffect(player, playbackSpeed) {
        player.setPlaybackSpeed(playbackSpeed)
        Log.i(TAG_SPEED, "speed applied to ExoPlayer=$playbackSpeed")
    }

    LaunchedEffect(exitRequested, player) {
        if (!exitRequested || !safeExitStarted.compareAndSet(false, true)) {
            return@LaunchedEffect
        }

        val startedAtMs = System.currentTimeMillis()
        Log.i(TAG_BEHAVIOR, "safe exit begin: stop decoder before detaching surface")
        finishActiveQoeOnce()

        player.playWhenReady = false
        player.pause()
        player.stop()

        var idleWaitStep = 0
        while (
            player.playbackState != Player.STATE_IDLE &&
            idleWaitStep < SAFE_EXIT_IDLE_WAIT_STEPS
        ) {
            delay(SAFE_EXIT_IDLE_WAIT_STEP_MS)
            idleWaitStep += 1
        }

        Log.i(
            TAG_BEHAVIOR,
            "safe exit renderer state=${player.playbackState.nameOfPlaybackState()}, " +
                "elapsedMs=${System.currentTimeMillis() - startedAtMs}"
        )

        try {
            playerViewRef[0]?.player = null
            delay(SAFE_EXIT_SURFACE_SETTLE_MS)
            if (releaseCompleted.compareAndSet(false, true)) {
                player.release()
            }
        } catch (error: RuntimeException) {
            Log.e(TAG_BEHAVIOR, "safe exit release failed: ${rootCauseMessage(error)}", error)
        } finally {
            Log.i(
                TAG_BEHAVIOR,
                "safe exit complete elapsedMs=${System.currentTimeMillis() - startedAtMs}"
            )
            latestOnSafeExitCompleted.value()
        }
    }

    LaunchedEffect(player) {
        onPlayerControllerReady(
            PlayerController(
                playOrPause = {
                    if (player.isPlaying) {
                        player.pause()
                    } else {
                        player.play()
                    }
                },
                seekBack = { player.seekBack() },
                seekForward = { player.seekForward() },
                seekTo = { player.seekTo(it.coerceAtLeast(0L)) },
                seekToStart = { player.seekTo(0L) },
                restartAndPlay = {
                    player.seekTo(0L)
                    player.playWhenReady = true
                    player.play()
                },
                play = {
                    player.playWhenReady = true
                    player.play()
                },
                pause = {
                    player.playWhenReady = false
                    player.pause()
                },
                setPlaybackSpeed = {
                    player.setPlaybackSpeed(it)
                    Log.i(TAG_SPEED, "speed applied to ExoPlayer=$it")
                }
            )
        )
    }

    LaunchedEffect(player) {
        while (!releaseCompleted.get()) {
            latestOnPlayerStateChanged.value(player.toUiState())
            activeQoeTracker?.sample(player)
            delay(PLAYER_STATE_SAMPLE_INTERVAL_MS)
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                latestOnPlayerStateChanged.value(player.toUiState())
            }

            override fun onRenderedFirstFrame() {
                Log.i(
                    TAG_BEHAVIOR,
                    "first video frame rendered playerId=${identityId(player)}, " +
                        "requestId=$activeRequestId, decoderMode=$decoderMode, " +
                        "playerGeneration=$decoderPlayerGeneration"
                )
                latestOnRenderedFirstFrame.value()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d(TAG_DEBUG, "player state transition=${playbackState.nameOfPlaybackState()}")
                activeQoeTracker?.onPlaybackStateChanged(playbackState, player)

                if (
                    playbackState == Player.STATE_READY &&
                    sourceChangeInProgress &&
                    !sourceReadyNotified
                ) {
                    sourceReadyNotified = true
                    sourceChangeInProgress = false
                    logPlayerMemory("READY")
                    Log.i(
                        TAG_RESUME,
                        "source position ready requested=${pendingSourcePositionMs}ms, " +
                            "actual=${player.currentPosition.coerceAtLeast(0L)}ms, " +
                            "playerId=${identityId(player)}"
                    )
                    Log.i(
                        TAG_BEHAVIOR,
                        "single player source change complete playerId=${identityId(player)}, " +
                            "requestId=$activeRequestId, keepSurface=true"
                    )
                    latestOnInitialSeekApplied.value()
                }

                if (playbackState == Player.STATE_ENDED) {
                    Log.i(TAG_BEHAVIOR, "playback ended")
                    latestOnPlaybackEnded.value()
                }
                latestOnPlayerStateChanged.value(player.toUiState())
            }

            override fun onPlayerError(error: PlaybackException) {
                if (safeExitStarted.get()) {
                    Log.w(
                        TAG_BEHAVIOR,
                        "Ignore playback error during controlled player shutdown: ${rootCauseMessage(error)}"
                    )
                    return
                }

                if (isSurfaceDetachTimeout(error)) {
                    Log.w(
                        TAG_BEHAVIOR,
                        "Ignore timeout while detaching video surface: ${rootCauseMessage(error)}"
                    )
                    return
                }

                val source = activePlayUrl ?: playUrl
                val decoderFailure = isDecoderFailure(error)
                activeQoeTracker?.onPlayerError(
                    error = error,
                    player = player,
                    penalizeCdn = !decoderFailure
                )
                val badSource = isBadSourceError(error)
                val message = when {
                    decoderFailure ->
                        "DECODER_FAILURE: decoderMode=$decoderMode, errorCodeName=${error.errorCodeName}, " +
                            "cause=${error.cause?.javaClass?.name}, message=${rootCauseMessage(error)}"
                    badSource ->
                        "BAD_SOURCE_HOST: errorCodeName=${error.errorCodeName}, cause=${error.cause?.javaClass?.name}, message=${rootCauseMessage(error)}"
                    else -> formatPlaybackError(error, source)
                }
                val responseCode = findHttpResponseCode(error)
                val failedHost = if (decoderFailure) {
                    null
                } else {
                    findFailedHost(error) ?: source.primaryUrl.hostOnly()
                }
                val logHost = failedHost ?: source.primaryUrl.hostOnly()
                if (badSource) {
                    Log.w(
                        TAG_BAD_SOURCE,
                        "badHost=$failedHost, reason=${rootCauseMessage(error)}, errorCodeName=${error.errorCodeName}, " +
                            "willBlacklist=true, willRecordPreferred=false"
                    )
                }
                Log.e(
                    TAG_RECOVERY,
                    "Playback error: errorCodeName=${error.errorCodeName}, " +
                        "cause class=${error.cause?.javaClass?.name}, " +
                        "HTTP response code=${responseCode ?: 0}, " +
                        "current position=${player.currentPosition.coerceAtLeast(0L)}, " +
                        "message=${error.message}, currentUrlHost=$logHost, decoderFailure=$decoderFailure",
                    error
                )
                Log.e(
                    TAG_DEBUG,
                    "ExoPlayer error type=${error::class.java.simpleName}, " +
                        "errorCode=${error.errorCode}, errorCodeName=${error.errorCodeName}, " +
                        "message=${error.message}, cause=${error.cause?.javaClass?.name}, " +
                        "http=${responseCode ?: 0}"
                )
                Log.w(
                    TAG_PLAYER_RECOVERY,
                    "PlaybackException errorCode=${error.errorCode}, errorCodeName=${error.errorCodeName}, " +
                        "cause class=${error.cause?.javaClass?.name}, http status code=${responseCode ?: 0}, " +
                        "currentPosition=${player.currentPosition.coerceAtLeast(0L)}, " +
                        "bufferedPosition=${player.bufferedPosition.coerceAtLeast(0L)}, " +
                        "selected qn=${source.quality?.qn ?: 0}, selected codec=${source.videoCodec.orEmpty()}, " +
                        "selected CDN host=$logHost, decoderFailure=$decoderFailure, " +
                        "current stream url index=${source.selectedVideoUrlIndex}, " +
                        "recovery action=${recoveryActionFor(error, responseCode, source)}"
                )
                latestOnRecoverablePlaybackError.value(
                    message,
                    player.currentPosition.coerceAtLeast(0L),
                    failedHost,
                    source.quality?.qn ?: source.requestedQn
                )
            }
        }

        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            finishActiveQoeOnce()

            if (!releaseCompleted.get() && !safeExitStarted.get()) {
                Log.w(
                    TAG_BEHAVIOR,
                    "player view disposed without explicit exit; release retained player safely"
                )
                runCatching {
                    player.playWhenReady = false
                    player.stop()
                    if (playerViewRef[0]?.player === player) {
                        playerViewRef[0]?.player = null
                    }
                    if (releaseCompleted.compareAndSet(false, true)) {
                        player.release()
                    }
                }.onFailure { error ->
                    Log.e(
                        TAG_BEHAVIOR,
                        "fallback retained player release failed: ${rootCauseMessage(error)}",
                        error
                    )
                }
            }
            playerViewRef[0] = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                playerViewRef[0] = this
                setKeepContentOnPlayerReset(true)
                setShutterBackgroundColor(AndroidColor.TRANSPARENT)
                this.player = player
                useController = false
                isFocusable = false
                isFocusableInTouchMode = false
                isClickable = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                Log.i(
                    TAG_BEHAVIOR,
                    "single PlayerView created viewId=${identityId(this)}, playerId=${identityId(player)}"
                )
            }
        },
        update = { playerView ->
            playerViewRef[0] = playerView
            playerView.setKeepContentOnPlayerReset(true)
            playerView.setShutterBackgroundColor(AndroidColor.TRANSPARENT)
            if (!exitRequested && playerView.player !== player) {
                playerView.player = player
            }
            playerView.useController = false
            playerView.isFocusable = false
            playerView.isFocusableInTouchMode = false
            playerView.isClickable = false
            playerView.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
    )
}

private fun ExoPlayer.toUiState(): PlayerUiState {
    val safeDuration = duration.takeUnless { it == C.TIME_UNSET } ?: 0L
    return PlayerUiState(
        isPlaying = isPlaying,
        playWhenReady = playWhenReady,
        isBuffering = playbackState == Player.STATE_BUFFERING,
        currentPositionMs = currentPosition.coerceAtLeast(0L),
        durationMs = safeDuration.coerceAtLeast(0L)
    )
}

private fun identityId(value: Any): String {
    return Integer.toHexString(System.identityHashCode(value))
}

private fun formatPlaybackError(
    error: PlaybackException,
    playUrl: PlayUrl
): String {
    val responseCode = findHttpResponseCode(error)
    Log.e(
        TAG,
        "Playback error details: errorCodeName=${error.errorCodeName}, " +
            "cause=${error.cause}, message=${error.message}, currentUrlHost=${playUrl.primaryUrl.hostOnly()}"
    )
    return when (responseCode) {
        403 -> "HTTP 403: \u64ad\u653e\u6e90\u88ab\u62d2\u7edd\uff0c\u6b63\u5728\u5c1d\u8bd5\u5207\u6362\u5907\u7528\u6e90\u6216\u964d\u7ea7\u6e05\u6670\u5ea6"
        404 -> "HTTP 404: \u64ad\u653e\u6e90\u5931\u6548\uff0c\u6b63\u5728\u91cd\u65b0\u83b7\u53d6"
        412 -> "HTTP 412: \u64ad\u653e\u6e90\u8bf7\u6c42\u53d7\u9650\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5"
        null -> when {
            error.errorCodeName.contains("DECOD", ignoreCase = true) ->
                "\u89e3\u7801\u5931\u8d25\uff0c\u6b63\u5728\u5c1d\u8bd5\u5207\u6362\u66f4\u517c\u5bb9\u7684\u64ad\u653e\u6e90"
            else -> error.message ?: "\u64ad\u653e\u5931\u8d25: ${error.errorCodeName}"
        }
        else -> "HTTP $responseCode: \u64ad\u653e\u6e90\u8bf7\u6c42\u5931\u8d25"
    }
}

private fun findHttpResponseCode(throwable: Throwable?): Int? {
    var current = throwable
    while (current != null) {
        if (current is HttpDataSource.InvalidResponseCodeException) {
            return current.responseCode
        }
        current = current.cause
    }
    return null
}

@OptIn(UnstableApi::class)
private fun findFailedHost(throwable: Throwable?): String? {
    var current = throwable
    while (current != null) {
        if (current is HttpDataSource.HttpDataSourceException) {
            current.dataSpec.uri.host?.takeIf { it.isNotBlank() }?.let { return it }
        }
        current = current.cause
    }
    return null
}

private class AdaptiveVideoMediaCodecSelector : MediaCodecSelector {
    @Volatile
    private var mode: VideoDecoderMode = VideoDecoderMode.AUTO_HARDWARE_FIRST

    fun updateMode(newMode: VideoDecoderMode) {
        if (mode == newMode) return
        val previous = mode
        mode = newMode
        Log.i(
            TAG_DECODER_SELECTOR,
            "decoder mode changed from=$previous to=$newMode"
        )
    }

    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean
    ): List<MediaCodecInfo> {
        val currentMode = mode
        val isVideo = mimeType.startsWith("video/", ignoreCase = true)
        val delegate = if (
            isVideo && currentMode == VideoDecoderMode.SOFTWARE_PREFERRED
        ) {
            MediaCodecSelector.PREFER_SOFTWARE
        } else {
            MediaCodecSelector.DEFAULT
        }
        val decoderInfos = delegate.getDecoderInfos(
            mimeType,
            requiresSecureDecoder,
            requiresTunnelingDecoder
        )
        if (isVideo) {
            Log.i(
                TAG_DECODER_SELECTOR,
                "decoder candidates mode=$currentMode, mime=$mimeType, " +
                    "secure=$requiresSecureDecoder, tunneling=$requiresTunnelingDecoder, " +
                    "candidates=${decoderInfos.joinToString(separator = "|") { it.name }}"
            )
        }
        return decoderInfos
    }
}

private fun isSurfaceDetachTimeout(
    error: PlaybackException
): Boolean {
    return throwableChain(error).any { throwable ->
        throwable.javaClass.name.contains(
            "ExoTimeoutException",
            ignoreCase = true
        ) &&
            throwable.message.orEmpty().contains(
                "Detaching surface timed out",
                ignoreCase = true
            )
    }
}

private fun isBadSourceError(error: PlaybackException): Boolean {
    return error.errorCodeName.contains("ERROR_CODE_PARSING_CONTAINER_MALFORMED", ignoreCase = true) ||
        throwableChain(error).any { throwable ->
            throwable.javaClass.name.contains("ParserException", ignoreCase = true) ||
                throwable.message.orEmpty().contains("Invalid NAL length", ignoreCase = true)
        }
}

private fun isDecoderFailure(error: PlaybackException): Boolean {
    return throwableChain(error).any { throwable ->
        throwable.javaClass.name.contains(
            "MediaCodecVideoDecoderException",
            ignoreCase = true
        ) || throwable.message.orEmpty().contains(
            "MediaCodecVideoRenderer",
            ignoreCase = true
        )
    }
}

private fun rootCauseMessage(throwable: Throwable): String {
    return throwableChain(throwable).lastOrNull()?.message ?: throwable.message.orEmpty()
}

private fun throwableChain(throwable: Throwable?): List<Throwable> {
    val result = mutableListOf<Throwable>()
    var current = throwable
    while (current != null) {
        result += current
        current = current.cause
    }
    return result
}

private fun recoveryActionFor(error: PlaybackException, responseCode: Int?, playUrl: PlayUrl): String {
    return when {
        responseCode in setOf(403, 404, 412, 429) -> {
            if (playUrl.videoUrlCandidates.size > playUrl.selectedVideoUrlIndex + 1) {
                "refresh playurl and prefer backupUrl/CDN"
            } else {
                "force refresh playurl"
            }
        }
        isDecoderFailure(error) -> "run decoder recovery policy with a fresh player"
        error.errorCodeName.contains("SOURCE", ignoreCase = true) -> "force refresh playurl"
        error.cause?.javaClass?.name?.contains("Timeout", ignoreCase = true) == true -> "force refresh playurl after timeout"
        error.cause?.javaClass?.name?.contains("EOF", ignoreCase = true) == true -> "force refresh playurl after EOF"
        else -> "show error if retry limit reached"
    }
}

private fun String.hostOnly(): String {
    if (isBlank()) return ""
    return runCatching { URI(this).host.orEmpty() }.getOrDefault("")
}

private fun Int.nameOfPlaybackState(): String {
    return when (this) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> toString()
    }
}

private const val NO_ACTIVE_REQUEST_ID = Long.MIN_VALUE
private const val PLAYER_STATE_SAMPLE_INTERVAL_MS = 250L
private const val SAFE_EXIT_IDLE_WAIT_STEPS = 8
private const val SAFE_EXIT_IDLE_WAIT_STEP_MS = 40L
private const val SAFE_EXIT_SURFACE_SETTLE_MS = 80L
private const val LEGACY_AMLOGIC_HARDWARE_RETRY_SETTLE_MS = 180L
private const val TAG = "VideoPlayerView"
private const val TAG_DECODER_SELECTOR = "BiliDecoderSelector"
private const val TAG_DEBUG = "BiliPlayerDebug"
private const val TAG_STREAM_DEBUG = "BiliStreamDebug"
private const val TAG_PLAYER_RECOVERY = "BiliPlayerRecovery"
private const val TAG_BAD_SOURCE = "BiliBadSourceHost"
private const val TAG_WEB_LIKE = "BiliWebLikePlayback"
private const val TAG_RESUME = "BiliResume"
private const val TAG_RECOVERY = "BiliRecovery"
private const val TAG_SPEED = "BiliSpeed"
private const val TAG_BEHAVIOR = "BiliPlaybackBehavior"
private const val TAG_QOE = "BiliPlaybackQoE"
private const val TAG_MEDIA_NETWORK = "BiliMediaNet"
private const val TAG_MEDIA_PRECONNECT = "BiliMediaPreconnect"
private const val BUFFERING_RECOVERY_TIMEOUT_MS = 8_000L
private const val START_POSITION_DASH_BUFFERING_TIMEOUT_MS = 8_000L
private const val STREAM_CONNECT_TIMEOUT_MS = 15_000
private const val STREAM_READ_TIMEOUT_MS = 30_000
private const val MEDIA_PRECONNECT_AWAIT_BUDGET_MS = 120L
