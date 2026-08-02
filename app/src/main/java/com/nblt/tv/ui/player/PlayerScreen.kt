package com.nblt.tv.ui.player

import android.util.Log
import android.view.KeyEvent
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nblt.tv.data.repository.DanmakuRepository
import com.nblt.tv.data.api.BilibiliCdnPreference
import com.nblt.tv.data.api.BilibiliCdnPreference.hostOnly
import com.nblt.tv.data.api.DashVideoCodecPreferenceMemory
import com.nblt.tv.model.DanmakuItem
import com.nblt.tv.model.DanmakuSettings
import com.nblt.tv.model.PlayUrl
import com.nblt.tv.model.VideoItem
import com.nblt.tv.model.VideoPage
import com.nblt.tv.model.displayTitleForPlayer
import com.nblt.tv.player.PlaybackProgressReporter
import com.nblt.tv.player.PlaybackReportEvent
import com.nblt.tv.theme.NbltPrimary
import com.nblt.tv.ui.state.UiState
import com.nblt.tv.util.BilibiliImageUrl
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun PlayerScreen(
    video: VideoItem,
    playUrlState: UiState<PlayUrl>,
    onRetry: () -> Unit,
    onRefreshPlayUrl: (Set<String>) -> Unit,
    onSwitchCdn: (PlayUrl) -> Unit,
    onSwitchPlaybackProfile: (Set<String>) -> Boolean,
    onSwitchQuality: (Int, Long) -> Unit,
    onSwitchPage: (VideoPage) -> Unit,
    onExit: () -> Unit,
    defaultPlaybackSpeed: Float,
    defaultDanmakuSettings: DanmakuSettings,
    danmakuBlocklistEnabled: Boolean,
    danmakuBlocklist: List<String>,
    onPlaybackEnded: () -> Unit,
    onReportPlaybackProgress: (VideoItem, PlayerUiState, PlaybackReportEvent) -> Unit,
    onPlaybackError: (String) -> Unit,
    onPlaybackRecovered: (PlayUrl) -> Unit,
    relatedPanelTitle: String = "\u76f8\u5173\u63a8\u8350",
    relatedPanelOwner: VideoItem = video,
    relatedPanelVideos: List<VideoItem> = emptyList(),
    recommendationPanelVideos: List<VideoItem> = emptyList(),
    relatedPanelLoading: Boolean = false,
    relatedPanelLoadingMore: Boolean = false,
    relatedPanelHasMore: Boolean = false,
    relatedPanelLoadMoreError: String? = null,
    onLoadMoreRelatedPanel: () -> Unit = {},
    onOpenOwnerSpace: (Long, String) -> Unit = { _, _ -> },
    onRelatedPanelVideoClick: (VideoItem) -> Unit = {}
) {
    val lowMemoryPlaybackMode = remember { isLowMemoryPlaybackDevice() }
    val rememberedDecoderPlan = remember(video.id, video.cid) {
        DecoderCompatibilityMemory.recommendedPlan(video.cid)
    }
    var hasRenderedFirstFrame by remember(video.id, video.cid) { mutableStateOf(false) }
    var showStartupIndicator by remember(video.id, video.cid) { mutableStateOf(false) }
    var controlsVisible by remember(video.id, video.cid) { mutableStateOf(false) }
    var controlsVersion by remember(video.id, video.cid) { mutableStateOf(0) }
    var seekHint by remember { mutableStateOf<String?>(null) }
    var seekBarVisible by remember(video.id) { mutableStateOf(false) }
    var isSeekPreviewMode by remember(video.id) { mutableStateOf(false) }
    var previewPositionMs by remember(video.id) { mutableLongStateOf(0L) }
    var pendingSeekPositionMs by remember(video.id, video.cid) { mutableStateOf<Long?>(null) }
    var pendingSeekAwaitingSourceChange by remember(video.id, video.cid) { mutableStateOf(false) }
    var pendingSeekJob by remember(video.id) { mutableStateOf<Job?>(null) }
    var wasPlayingBeforeSeekPreview by remember(video.id) { mutableStateOf(false) }
    var infoOverlayVisible by remember(video.id) { mutableStateOf(false) }
    var infoOverlayVersion by remember(video.id) { mutableStateOf(0) }
    var bottomPanelVisible by remember(video.ownerMid) { mutableStateOf(false) }
    var bottomPanelIndex by remember(video.ownerMid) { mutableStateOf(0) }
    var bottomPanelOwnerFocused by remember(video.ownerMid) { mutableStateOf(true) }
    var bottomPanelOpenedWhileLoading by remember(video.ownerMid) { mutableStateOf(false) }
    var lastBottomPanelNavigationAt by remember { mutableLongStateOf(0L) }
    var keepBottomPanelOnVideoSwitch by remember { mutableStateOf(false) }
    var showingRecommendations by remember { mutableStateOf(false) }
    var sameUpPanelIndex by remember(video.ownerMid) { mutableStateOf(0) }
    var recommendationPanelIndex by remember(video.id) { mutableStateOf(0) }
    val displayedPanelVideos = if (showingRecommendations) recommendationPanelVideos else relatedPanelVideos
    var resumeHintVisible by remember(video.id) { mutableStateOf(false) }
    var playerUiState by remember { mutableStateOf(PlayerUiState()) }
    var retainedDurationMs by remember(video.id, video.cid) { mutableLongStateOf(0L) }
    var retainedPlayUrl by remember(video.id, video.cid) {
        mutableStateOf((playUrlState as? UiState.Success)?.data)
    }
    var resumePlaybackAfterSourceChange by remember(video.id, video.cid) { mutableStateOf(true) }
    var playerController by remember { mutableStateOf<PlayerController?>(null) }
    var isExitRequested by remember(video.id, video.cid) { mutableStateOf(false) }
    var recoveryAttempt by remember(video.id) { mutableStateOf(0) }
    var decoderMode by remember(video.id, video.cid) {
        mutableStateOf(
            rememberedDecoderPlan?.mode
                ?: VideoDecoderMode.AUTO_HARDWARE_FIRST
        )
    }
    var decoderRetryToken by remember(video.id, video.cid) { mutableStateOf(0) }
    var decoderPlayerGeneration by remember(video.id, video.cid) { mutableStateOf(0) }
    var pendingDecoderTargetQn by remember(video.id, video.cid) {
        mutableStateOf(rememberedDecoderPlan?.targetQn)
    }
    var rememberedDecoderPlanRequestIssued by remember(video.id, video.cid) {
        mutableStateOf(false)
    }
    var decoderRecoveryAttempts by remember(video.id, video.cid) { mutableStateOf(0) }
    var isRecovering by remember(video.id) { mutableStateOf(false) }
    var recoverySeekPositionMs by remember(video.id) { mutableStateOf(0L) }
    var recoveryObservedLoading by remember(video.id) { mutableStateOf(false) }
    var isUserRestartFromBeginning by remember(video.id, video.cid) { mutableStateOf(false) }
    var isRetryingSource by remember(video.id, video.cid) { mutableStateOf(false) }
    var isChangingQuality by remember(video.id, video.cid) { mutableStateOf(false) }
    var isChangingPart by remember(video.id, video.cid) { mutableStateOf(false) }
    var isAutoNextPart by remember(video.id, video.cid) { mutableStateOf(false) }
    var failedStreamHosts by remember(video.id, video.cid) { mutableStateOf<Set<String>>(emptySet()) }
    var failedVideoHosts by remember(video.id, video.cid) { mutableStateOf<Set<String>>(emptySet()) }
    var failedAudioHosts by remember(video.id, video.cid) { mutableStateOf<Set<String>>(emptySet()) }
    var lastFailedHost by remember(video.id, video.cid) { mutableStateOf<String?>(null) }
    var failedHostWithReason by remember(video.id, video.cid) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var qn80BufferingTimeoutCount by remember(video.id, video.cid) { mutableStateOf(0) }
    var lastProactiveRecoveryAtMs by remember(video.id, video.cid) {
        mutableLongStateOf(0L)
    }
    var quickMenuOpen by remember(video.id) { mutableStateOf(false) }
    var activeSubMenu by remember(video.id) { mutableStateOf(PlayerSubMenu.None) }
    var quickMenuIndex by remember(video.id) { mutableStateOf(0) }
    var subMenuIndex by remember(video.id) { mutableStateOf(0) }
    var qualitySwitchSeekPositionMs by remember(video.id) { mutableStateOf(0L) }
    var playbackSpeed by remember { mutableStateOf(defaultPlaybackSpeed) }
    var danmakuEnabled by remember {
        mutableStateOf(defaultDanmakuSettings.enabledByDefault && !lowMemoryPlaybackMode)
    }
    var sessionDanmakuSettings by remember { mutableStateOf(defaultDanmakuSettings) }
    var danmakuItems by remember(video.cid) { mutableStateOf<List<DanmakuItem>>(emptyList()) }
    var danmakuLoadNotifiedCid by remember(video.id) { mutableStateOf<Long?>(null) }
    var hasHandledPlaybackEnd by remember(video.id, video.cid) { mutableStateOf(false) }
    var lastObservedPositionMs by remember(video.id, video.cid) { mutableStateOf(0L) }
    var lastBackPressTimeMs by remember { mutableLongStateOf(0L) }
    val danmakuRepository = remember { DanmakuRepository() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val initialSeekPositionMs = remember(video.id, video.cid) { video.resumePositionMs() }
    val effectiveInitialSeekPositionMs = when {
        isRecovering -> recoverySeekPositionMs
        isChangingQuality -> qualitySwitchSeekPositionMs
        isUserRestartFromBeginning -> 0L
        else -> initialSeekPositionMs
    }
    val shouldResume = !isUserRestartFromBeginning && initialSeekPositionMs > 0L

    fun hasQualities(): Boolean {
        return (playUrlState as? UiState.Success)?.data?.availableQualities?.isNotEmpty() == true
    }

    fun quickMenuItems(): List<PlayerQuickMenuItem> {
        return buildQuickMenuItems(
            hasQualities = hasQualities(),
            hasMultiplePages = video.pages.size > 1
        )
    }

    fun subMenuItemCount(): Int {
        return when (activeSubMenu) {
            PlayerSubMenu.Quality -> (playUrlState as? UiState.Success)?.data?.availableQualities.orEmpty().size
            PlayerSubMenu.Speed -> playbackSpeeds.size
            PlayerSubMenu.Pages -> video.pages.size
            PlayerSubMenu.DanmakuSettings -> danmakuSettingsMenuEntries().size
            PlayerSubMenu.None -> 0
        }
    }

    fun isMenuActive(): Boolean = quickMenuOpen

    fun showControls() {
        controlsVisible = true
        controlsVersion += 1
    }

    fun closeAllMenus() {
        if (quickMenuOpen || activeSubMenu != PlayerSubMenu.None) {
            Log.i(TAG_MENU, "close quick menu")
        }
        quickMenuOpen = false
        activeSubMenu = PlayerSubMenu.None
        quickMenuIndex = 0
        subMenuIndex = 0
    }

    fun openQuickMenu() {
        bottomPanelVisible = false
        infoOverlayVisible = false
        isSeekPreviewMode = false
        quickMenuOpen = true
        activeSubMenu = PlayerSubMenu.None
        quickMenuIndex = 0
        subMenuIndex = 0
        Log.i(TAG_MENU, "open quick menu")
        showControls()
    }

    fun closeQuickMenu() {
        if (!quickMenuOpen) {
            return
        }
        quickMenuOpen = false
        activeSubMenu = PlayerSubMenu.None
        quickMenuIndex = 0
        subMenuIndex = 0
        Log.i(TAG_MENU, "close quick menu")
    }

    fun showInfoOverlay() {
        closeAllMenus()
        bottomPanelVisible = false
        infoOverlayVisible = true
        infoOverlayVersion += 1
        Log.i(TAG_OVERLAY, "infoOverlay shown")
        showControls()
    }

    fun openBottomPanel() {
        closeAllMenus()
        infoOverlayVisible = false
        isSeekPreviewMode = false
        seekBarVisible = false
        controlsVisible = false
        controlsVersion = 0
        seekHint = null
        showStartupIndicator = false
        bottomPanelIndex = bottomPanelIndex.coerceIn(0, (displayedPanelVideos.size - 1).coerceAtLeast(0))
        bottomPanelOwnerFocused = !showingRecommendations && displayedPanelVideos.isEmpty() && relatedPanelOwner.ownerMid > 0L
        bottomPanelOpenedWhileLoading = relatedPanelLoading
        bottomPanelVisible = true
        Log.i(TAG_OVERLAY, "bottomPanel opened, size=${displayedPanelVideos.size}")
        showControls()
    }

    fun closeBottomPanel() {
        if (bottomPanelVisible) {
            Log.i(TAG_OVERLAY, "bottomPanel closed")
        }
        bottomPanelVisible = false
        bottomPanelOpenedWhileLoading = false
    }

    fun acceptBottomPanelNavigation(): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - lastBottomPanelNavigationAt < 85L) return false
        lastBottomPanelNavigationAt = now
        return true
    }

    LaunchedEffect(bottomPanelVisible, relatedPanelLoading, displayedPanelVideos.size) {
        if (
            bottomPanelVisible &&
            bottomPanelOpenedWhileLoading &&
            !relatedPanelLoading &&
            displayedPanelVideos.isNotEmpty()
        ) {
            bottomPanelOwnerFocused = false
            bottomPanelIndex = 0
            bottomPanelOpenedWhileLoading = false
        }
    }

    fun commitSeekPreview() {
        if (!isSeekPreviewMode) return
        val duration = (playerUiState.durationMs.takeIf { it > 0L } ?: retainedDurationMs)
            .takeIf { it > 0L } ?: Long.MAX_VALUE
        val target = previewPositionMs.coerceIn(0L, duration)
        Log.i(TAG_KEYS, "commitSeek position=$target, keepLastFrame=true")
        pendingSeekJob?.cancel()
        pendingSeekJob = null
        pendingSeekPositionMs = target
        pendingSeekAwaitingSourceChange = false
        playerController?.seekTo(target)
        if (wasPlayingBeforeSeekPreview) {
            playerController?.play()
        } else {
            playerController?.pause()
        }
        isSeekPreviewMode = false
        seekBarVisible = true
        seekHint = null
        showControls()
    }

    fun cancelSeekPreview() {
        if (!isSeekPreviewMode && !seekBarVisible) return
        pendingSeekJob?.cancel()
        pendingSeekJob = null
        if (isSeekPreviewMode && wasPlayingBeforeSeekPreview) {
            playerController?.play()
        }
        isSeekPreviewMode = false
        seekBarVisible = false
        seekHint = null
        Log.i(TAG_KEYS, "seek preview cancelled")
    }

    fun scheduleSeekCommit() {
        pendingSeekJob?.cancel()
        pendingSeekJob = scope.launch {
            delay(SEEK_PREVIEW_COMMIT_DELAY_MS)
            commitSeekPreview()
            delay(SEEK_BAR_AUTO_HIDE_MS)
            if (!isSeekPreviewMode && !quickMenuOpen && !bottomPanelVisible) {
                seekBarVisible = false
                seekHint = null
            }
        }
    }

    fun updateSeekPreview(deltaMs: Long) {
        closeAllMenus()
        infoOverlayVisible = false
        if (!isSeekPreviewMode) {
            previewPositionMs = pendingSeekPositionMs ?: playerUiState.currentPositionMs
            wasPlayingBeforeSeekPreview = playerUiState.playWhenReady
            playerController?.pause()
            Log.i(TAG_KEYS, "seek preview freeze current frame, wasPlaying=$wasPlayingBeforeSeekPreview")
        }
        isSeekPreviewMode = true
        seekBarVisible = true
        controlsVisible = true
        val duration = (playerUiState.durationMs.takeIf { it > 0L } ?: retainedDurationMs)
            .takeIf { it > 0L } ?: Long.MAX_VALUE
        previewPositionMs = (previewPositionMs + deltaMs).coerceIn(0L, duration)
        seekHint = null
        val fraction = if (playerUiState.durationMs > 0L) {
            previewPositionMs.toFloat() / playerUiState.durationMs.toFloat()
        } else {
            0f
        }
        Log.i(
            TAG_KEYS,
            "seek preview, position=$previewPositionMs, duration=${playerUiState.durationMs}, " +
                "progressFraction=$fraction, seekBarVisible=true"
        )
        Log.i(TAG_OVERLAY, "seekBarVisible=true, controls shown by seek")
        scheduleSeekCommit()
    }

    fun freezeFrameForSourceChange(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0L)
        if (!pendingSeekAwaitingSourceChange) {
            resumePlaybackAfterSourceChange = playerUiState.playWhenReady
        }
        pendingSeekPositionMs = target
        pendingSeekAwaitingSourceChange = true
        previewPositionMs = target
        seekBarVisible = true
        controlsVisible = true
        playerController?.pause()
        Log.i(
            TAG_BEHAVIOR,
            "freeze frame for source change positionMs=$target, " +
                "resumeAfter=$resumePlaybackAfterSourceChange"
        )
    }

    fun openSubMenu(menu: PlayerSubMenu) {
        activeSubMenu = menu
        subMenuIndex = when (menu) {
            PlayerSubMenu.Quality -> {
                val qualities = (playUrlState as? UiState.Success)?.data?.availableQualities.orEmpty()
                val currentQn = (playUrlState as? UiState.Success)?.data?.quality?.qn
                qualities.indexOfFirst { it.qn == currentQn }.takeIf { it >= 0 } ?: 0
            }
            PlayerSubMenu.Speed -> playbackSpeeds.indexOfFirst { it == playbackSpeed }.takeIf { it >= 0 } ?: 1
            PlayerSubMenu.Pages -> video.pages.indexOfFirst { it.cid == video.cid }.takeIf { it >= 0 } ?: 0
            PlayerSubMenu.DanmakuSettings -> {
                danmakuSettingsMenuEntries().indexOfFirst { it.isSelected(sessionDanmakuSettings) }
                    .takeIf { it >= 0 } ?: 0
            }
            PlayerSubMenu.None -> 0
        }
        Log.i(TAG_MENU, "open submenu: $menu")
        showControls()
    }

    fun closeSubMenu() {
        if (activeSubMenu == PlayerSubMenu.None) {
            return
        }
        Log.i(TAG_MENU, "close submenu")
        activeSubMenu = PlayerSubMenu.None
        subMenuIndex = 0
    }

    fun resetPlaybackEndHandledFlag() {
        if (hasHandledPlaybackEnd) {
            Log.i(TAG_PLAYER, "reset playback end handled flag")
        }
        hasHandledPlaybackEnd = false
    }

    fun restartFromBeginning(): Boolean {
        if (!resumeHintVisible) {
            return false
        }
        Log.i(
            TAG_RESUME,
            "back pressed to restart: bvid=${video.bvid}, duration=${video.duration}, progress=${video.historyProgress}"
        )
        isUserRestartFromBeginning = true
        playerController?.restartAndPlay()
        resetPlaybackEndHandledFlag()
        resumeHintVisible = false
        seekHint = null
        controlsVisible = true
        return true
    }

    fun restartPlaybackFromMenu() {
        Log.i(TAG_MENU, "restart playback")
        isUserRestartFromBeginning = true
        playerController?.restartAndPlay()
        resetPlaybackEndHandledFlag()
        resumeHintVisible = false
        seekHint = null
        closeAllMenus()
        showControls()
    }

    fun requestRecovery(
        reason: String,
        positionMs: Long,
        failedHost: String? = null,
        decoderSourceQn: Int? = null
    ) {
        val decoderFailure = reason.isDecoderFailureReason()
        if (isRecovering) {
            if (decoderFailure) {
                Log.w(
                    TAG_PLAYER_RECOVERY,
                    "decoder failure while recovering; continue decoder fallback chain, " +
                        "mode=$decoderMode, reason=$reason"
                )
                isRecovering = false
                recoveryObservedLoading = false
            } else {
                if (!reason.isBadSourceHostReason()) {
                    return
                }

                Log.w(
                    TAG_PLAYER_RECOVERY,
                    "bad source while recovering; " +
                        "continue fallback immediately, " +
                        "reason=$reason"
                )

                isRecovering = false
                recoveryObservedLoading = false
            }
        }

        recoverySeekPositionMs = positionMs.coerceAtLeast(0L)
        val currentPlayUrl = (playUrlState as? UiState.Success)?.data
        val currentQn = if (decoderFailure) {
            resolveDecoderRecoveryQn(
                sourceQn = decoderSourceQn,
                fallbackQn = retainedPlayUrl?.quality?.qn
                    ?: currentPlayUrl?.quality?.qn
                    ?: 0
            )
        } else {
            currentPlayUrl?.quality?.qn ?: 0
        }

        if (decoderFailure) {
            freezeFrameForSourceChange(recoverySeekPositionMs)
            if (decoderRecoveryAttempts >= MAX_DECODER_RECOVERY_ATTEMPTS) {
                Log.e(
                    TAG_DECODER_POLICY,
                    "decoder recovery limit reached attempts=$decoderRecoveryAttempts, " +
                        "mode=$decoderMode, qn=$currentQn, reason=$reason"
                )
                isRecovering = false
                isRetryingSource = false
                onPlaybackError("硬件和软件解码均失败，请尝试较低清晰度")
                return
            }

            decoderRecoveryAttempts += 1
            val isStartupDecoderFailure = recoverySeekPositionMs <= DECODER_STARTUP_POSITION_THRESHOLD_MS
            val legacyAmlogicRuntime = DeviceCapabilityProfiler.isLegacyAmlogicRuntime()
            val currentCodec = (currentPlayUrl ?: retainedPlayUrl)?.videoCodec.orEmpty()

            if (
                legacyAmlogicRuntime &&
                decoderMode == VideoDecoderMode.AUTO_HARDWARE_FIRST &&
                currentCodec.isAvcCodec()
            ) {
                val codecPlan = DashVideoCodecPreferenceMemory.recordHevcPreferred(
                    cid = video.cid,
                    requestedQn = currentQn
                )
                decoderMode = VideoDecoderMode.SOFTWARE_PREFERRED
                decoderRetryToken += 1
                decoderPlayerGeneration += 1
                pendingDecoderTargetQn = codecPlan.targetQn
                playerController = null
                qualitySwitchSeekPositionMs = recoverySeekPositionMs
                isChangingQuality = true
                isRecovering = false
                isRetryingSource = true
                controlsVisible = true
                closeAllMenus()
                Log.w(
                    TAG_DECODER_POLICY,
                    "legacy AVC hardware decoder failed; prefer HEVC software track " +
                        "cid=${video.cid}, codec=$currentCodec, " +
                        "qn=$currentQn->${codecPlan.targetQn}, " +
                        "decoderMode=$decoderMode, keepCdn=true, " +
                        "seekBackPosition=$recoverySeekPositionMs, " +
                        "skipCdnPenalty=true, playerGeneration=$decoderPlayerGeneration"
                )
                onSwitchQuality(codecPlan.targetQn, recoverySeekPositionMs)
                return
            }

            if (
                legacyAmlogicRuntime &&
                decoderMode == VideoDecoderMode.SOFTWARE_PREFERRED &&
                currentCodec.isHevcCodec()
            ) {
                DashVideoCodecPreferenceMemory.clear(video.cid)
                val fallbackQn = currentQn.takeIf { it > 0 }?.coerceAtMost(64) ?: 64
                decoderRetryToken += 1
                decoderPlayerGeneration += 1
                pendingDecoderTargetQn = fallbackQn
                playerController = null
                qualitySwitchSeekPositionMs = recoverySeekPositionMs
                isChangingQuality = true
                isRecovering = false
                isRetryingSource = true
                controlsVisible = true
                closeAllMenus()
                Log.w(
                    TAG_DECODER_POLICY,
                    "HEVC software decoder failed; return to AVC software fallback " +
                        "cid=${video.cid}, codec=$currentCodec, targetQn=$fallbackQn, " +
                        "keepCdn=true, seekBackPosition=$recoverySeekPositionMs, " +
                        "skipCdnPenalty=true, playerGeneration=$decoderPlayerGeneration"
                )
                onSwitchQuality(fallbackQn, recoverySeekPositionMs)
                return
            }

            val decision = decideDecoderRecovery(
                currentMode = decoderMode,
                currentQn = currentQn,
                recoveryAttempt = decoderRecoveryAttempts,
                isStartupFailure = isStartupDecoderFailure,
                preferLegacyAmlogicHardwareRecovery = legacyAmlogicRuntime
            )
            Log.w(
                TAG_DECODER_POLICY,
                "decoder failure detected mode=$decoderMode, qn=$currentQn, " +
                    "attempt=$decoderRecoveryAttempts/$MAX_DECODER_RECOVERY_ATTEMPTS, " +
                    "action=${decision.action}, startupFailure=$isStartupDecoderFailure, " +
                    "legacyAmlogic=$legacyAmlogicRuntime, positionMs=$recoverySeekPositionMs, " +
                    "skipCdnPenalty=true, reason=$reason"
            )

            when (decision.action) {
                DecoderRecoveryAction.RETRY_SAME_QUALITY_WITH_HARDWARE -> {
                    decoderMode = decision.nextMode
                    decoderRetryToken += 1
                    decoderPlayerGeneration += 1
                    playerController = null
                    isRecovering = true
                    isRetryingSource = true
                    controlsVisible = true
                    closeAllMenus()
                    Log.w(
                        TAG_DECODER_POLICY,
                        "transient startup decoder failure; cold retry same quality with hardware " +
                            "qn=$currentQn, decoderMode=$decoderMode, keepCdn=true, " +
                            "seekBackPosition=$recoverySeekPositionMs, " +
                            "decoderRetryToken=$decoderRetryToken, " +
                            "playerGeneration=$decoderPlayerGeneration"
                    )
                    return
                }

                DecoderRecoveryAction.RETRY_SAME_QUALITY_WITH_SOFTWARE -> {
                    decoderMode = decision.nextMode
                    decoderRetryToken += 1
                    decoderPlayerGeneration += 1
                    playerController = null
                    isRecovering = true
                    isRetryingSource = true
                    controlsVisible = true
                    closeAllMenus()
                    Log.w(
                        TAG_DECODER_POLICY,
                        "retry same quality with software decoder qn=$currentQn, " +
                            "decoderMode=$decoderMode, keepCdn=true, " +
                            "seekBackPosition=$recoverySeekPositionMs, " +
                            "decoderRetryToken=$decoderRetryToken, " +
                            "playerGeneration=$decoderPlayerGeneration"
                    )
                    return
                }

                DecoderRecoveryAction.LOWER_TO_QN64_WITH_HARDWARE -> {
                    decoderMode = decision.nextMode
                    decoderRetryToken += 1
                    decoderPlayerGeneration += 1
                    pendingDecoderTargetQn = decision.targetQn
                    playerController = null
                    qualitySwitchSeekPositionMs = recoverySeekPositionMs
                    isChangingQuality = true
                    isRecovering = false
                    isRetryingSource = true
                    controlsVisible = true
                    closeAllMenus()
                    Log.w(
                        TAG_DECODER_POLICY,
                        "lower quality and retry hardware with a fresh player " +
                            "qn=$currentQn->${decision.targetQn}, decoderMode=$decoderMode, " +
                            "keepCdn=true, seekBackPosition=$recoverySeekPositionMs, " +
                            "playerGeneration=$decoderPlayerGeneration"
                    )
                    onSwitchQuality(decision.targetQn, recoverySeekPositionMs)
                    return
                }

                DecoderRecoveryAction.LOWER_TO_QN64_WITH_SOFTWARE -> {
                    decoderMode = decision.nextMode
                    decoderRetryToken += 1
                    decoderPlayerGeneration += 1
                    pendingDecoderTargetQn = decision.targetQn
                    playerController = null
                    qualitySwitchSeekPositionMs = recoverySeekPositionMs
                    isChangingQuality = true
                    isRecovering = false
                    isRetryingSource = true
                    controlsVisible = true
                    closeAllMenus()
                    Log.w(
                        TAG_DECODER_POLICY,
                        "legacy hardware decoder failed; lower quality and retry software " +
                            "qn=$currentQn->${decision.targetQn}, decoderMode=$decoderMode, " +
                            "keepCdn=true, seekBackPosition=$recoverySeekPositionMs, " +
                            "playerGeneration=$decoderPlayerGeneration"
                    )
                    onSwitchQuality(decision.targetQn, recoverySeekPositionMs)
                    return
                }

                DecoderRecoveryAction.FAIL -> {
                    isRecovering = false
                    isRetryingSource = false
                    Log.e(
                        TAG_DECODER_POLICY,
                        "decoder fallback exhausted mode=$decoderMode, qn=$currentQn, " +
                            "attempts=$decoderRecoveryAttempts, reason=$reason"
                    )
                    onPlaybackError("硬件和软件解码均失败，请尝试其他清晰度")
                    return
                }
            }
        }

        if (recoveryAttempt >= MAX_RECOVERY_ATTEMPTS) {
            Log.e(TAG_RECOVERY, "recovery fail: max attempts reached, reason=$reason")
            onPlaybackError(reason)
            return
        }
        recoveryAttempt += 1
        freezeFrameForSourceChange(recoverySeekPositionMs)

        var nextFailedHosts = failedStreamHosts
        if (!decoderFailure) {
            failedHost?.takeIf { it.isNotBlank() }?.let { host ->
                val failureRole = currentPlayUrl?.failureRoleForHost(host)
                    ?: BilibiliCdnPreference.StreamRole.BOTH
                BilibiliCdnPreference.recordFailedHost(host, reason, failureRole)
                lastFailedHost = host
                nextFailedHosts = failedStreamHosts + host
                failedStreamHosts = nextFailedHosts
                when (failureRole) {
                    BilibiliCdnPreference.StreamRole.VIDEO -> {
                        failedVideoHosts = failedVideoHosts + host
                    }
                    BilibiliCdnPreference.StreamRole.AUDIO -> {
                        failedAudioHosts = failedAudioHosts + host
                    }
                    BilibiliCdnPreference.StreamRole.BOTH,
                    BilibiliCdnPreference.StreamRole.UNKNOWN -> {
                        failedVideoHosts = failedVideoHosts + host
                        failedAudioHosts = failedAudioHosts + host
                    }
                }
                failedHostWithReason = failedHostWithReason + (host to reason)
                Log.w(
                    TAG_CDN,
                    "failedHost=$host, failureRole=$failureRole, " +
                        "failedHosts=${nextFailedHosts.joinToString(prefix = "[", postfix = "]")}, " +
                        "failedVideoHosts=${failedVideoHosts.joinToString(prefix = "[", postfix = "]")}, " +
                        "failedAudioHosts=${failedAudioHosts.joinToString(prefix = "[", postfix = "]")}, " +
                        "errorCodeName=$reason, recoveryAttempt=${recoveryAttempt}, currentPosition=$recoverySeekPositionMs, " +
                        "selected qn=${currentPlayUrl?.quality?.qn ?: 0}, " +
                        "stream type=${currentPlayUrl?.sourceType.orEmpty()}"
                )
            }
        }
        if (reason.contains("BUFFERING_TIMEOUT", ignoreCase = true) && currentQn == 80) {
            qn80BufferingTimeoutCount += 1
        } else if (currentQn != 80) {
            qn80BufferingTimeoutCount = 0
        }
        val isBadSourceHost =
            reason.isBadSourceHostReason()

        val isBufferingTimeout =
            reason.contains(
                "BUFFERING_TIMEOUT",
                ignoreCase = true
            )

        val shouldSkipWeakSameQnFallback =
            currentPlayUrl != null &&
                currentQn == 80 &&
                isBufferingTimeout &&
                !currentPlayUrl.hasSafeAlternativeCdn(
                    failedVideoHosts = failedVideoHosts,
                    failedAudioHosts = failedAudioHosts
                )

        if (shouldSkipWeakSameQnFallback) {
            recoverySeekPositionMs =
                positionMs.coerceAtLeast(0L)

            qualitySwitchSeekPositionMs =
                recoverySeekPositionMs

            isChangingQuality = true
            isRecovering = false
            isRetryingSource = true
            controlsVisible = true
            qn80BufferingTimeoutCount = 0

            failedStreamHosts = emptySet()
            failedVideoHosts = emptySet()
            failedAudioHosts = emptySet()
            lastFailedHost = null
            failedHostWithReason = emptyMap()

            Log.i(TAG_CDN, "quality downgrade preserves active CDN cooldowns")

            closeAllMenus()

            Log.w(
                TAG_CDN,
                "fallback action=lowerQn, " +
                    "reason=no safe same-qn CDN, " +
                    "skipWeakFallback=true, " +
                    "currentQn=$currentQn, " +
                    "targetQn=64, " +
                    "seekBackPosition=" +
                    "$recoverySeekPositionMs"
            )

            onSwitchQuality(
                64,
                recoverySeekPositionMs
            )
            return
        }

        val switchedPlayUrl =
            currentPlayUrl?.switchToBetterCdn(
                cid = video.cid,
                failedVideoHosts = failedVideoHosts,
                failedAudioHosts = failedAudioHosts
            )
        if (switchedPlayUrl != null) {
            recoveryObservedLoading = false
            isRecovering = true
            isRetryingSource = true
            controlsVisible = true
            closeAllMenus()
            Log.i(
                TAG_CDN,
                "fallbackStep=switchCdn, selectedVideoHost=${switchedPlayUrl.videoUrl.hostOnly()}, " +
                    "selectedAudioHost=${switchedPlayUrl.audioUrl.orEmpty().hostOnly()}, qn=$currentQn, " +
                    "seekBackPosition=$recoverySeekPositionMs"
            )
            onSwitchCdn(switchedPlayUrl)
            return
        }
        val allSameQnCdnFailed =
            currentPlayUrl?.allCurrentQnCandidatesFailed(
                failedVideoHosts = failedVideoHosts,
                failedAudioHosts = failedAudioHosts
            ) == true

        val shouldLowerQn =
            currentQn == 80 &&
                allSameQnCdnFailed &&
                (
                    isBadSourceHost ||
                        qn80BufferingTimeoutCount >= 1
                    )

        if (shouldLowerQn) {
            recoverySeekPositionMs =
                positionMs.coerceAtLeast(0L)

            qualitySwitchSeekPositionMs =
                recoverySeekPositionMs

            isChangingQuality = true
            isRecovering = false
            isRetryingSource = true
            controlsVisible = true
            qn80BufferingTimeoutCount = 0

            failedStreamHosts = emptySet()
            failedVideoHosts = emptySet()
            failedAudioHosts = emptySet()

            Log.i(TAG_CDN, "quality downgrade preserves active CDN cooldowns")

            val lowerReason =
                if (isBadSourceHost) {
                    "backup source malformed"
                } else {
                    "all qn80 candidates unavailable"
                }

            Log.w(
                TAG_CDN,
                "fallback action=lowerQn, " +
                    "reason=$lowerReason, " +
                    "currentQn=$currentQn, " +
                    "targetQn=64, " +
                    "seekBackPosition=" +
                    "$recoverySeekPositionMs"
            )

            onSwitchQuality(
                64,
                recoverySeekPositionMs
            )
            return
        }

        if (
            allSameQnCdnFailed &&
            onSwitchPlaybackProfile(nextFailedHosts)
        ) {
            recoveryObservedLoading = false
            isRecovering = true
            isRetryingSource = true
            controlsVisible = true
            closeAllMenus()

            Log.i(
                TAG_CDN,
                "fallbackStep=switchProfile, " +
                    "qn=$currentQn, " +
                    "seekBackPosition=" +
                    "$recoverySeekPositionMs, " +
                    "failedHosts=" +
                    nextFailedHosts.joinToString(
                        prefix = "[",
                        postfix = "]"
                    )
            )
            return
        }
        recoveryObservedLoading = false
        isRecovering = true
        isRetryingSource = true
        controlsVisible = true
        closeAllMenus()
        Log.i(
            TAG_PLAYER_RECOVERY,
            "recover attempt $recoveryAttempt/$MAX_RECOVERY_ATTEMPTS, " +
                "current position=$recoverySeekPositionMs, reason=$reason, " +
                "fallbackStep=refreshPlayUrl, action=refreshPlayUrl, useHistoryResume=false, failedHosts=${nextFailedHosts.joinToString(prefix = "[", postfix = "]")}, " +
                "seekBackPosition=$recoverySeekPositionMs"
        )
        if (reason.contains("412") || reason.contains("429")) {
            scope.launch {
                delay(BLOCKED_REQUEST_RECOVERY_DELAY_MS)
                onRefreshPlayUrl(nextFailedHosts)
            }
        } else {
            onRefreshPlayUrl(nextFailedHosts)
        }
    }

    fun selectQualityFromMenu() {
        val current = playUrlState as? UiState.Success ?: return
        val quality = current.data.availableQualities.getOrNull(subMenuIndex) ?: return
        val position = playerUiState.currentPositionMs.coerceAtLeast(0L)
        qualitySwitchSeekPositionMs = position
        isChangingQuality = true
        freezeFrameForSourceChange(position)
        closeAllMenus()
        controlsVisible = true
        Log.i(TAG_MENU, "change quality qn=${quality.qn}")
        Log.i(TAG_QUALITY, "user selected in-player qn=${quality.qn}")
        Log.i(TAG_QUALITY, "switch quality current position=$position")
        onSwitchQuality(quality.qn, position)
    }

    fun selectSpeedFromMenu() {
        val speed = playbackSpeeds.getOrNull(subMenuIndex) ?: return
        playbackSpeed = speed
        playerController?.setPlaybackSpeed(speed)
        closeSubMenu()
        Log.i(TAG_MENU, "change speed=$speed")
        Log.i(TAG_SPEED, "current speed changed=$speed")
        showControls()
    }

    fun selectPageFromMenu() {
        val page = video.pages.getOrNull(subMenuIndex) ?: return
        closeAllMenus()
        isChangingPart = true
        isAutoNextPart = false
        failedStreamHosts = emptySet()
        failedVideoHosts = emptySet()
        failedAudioHosts = emptySet()
        lastFailedHost = null
        failedHostWithReason = emptyMap()
        qualitySwitchSeekPositionMs = 0L
        Log.i(TAG_MENU, "change page=${page.page}, cid=${page.cid}")
        Log.i(TAG_PAGES, "selected page=${page.page}, selected cid=${page.cid}")
        onSwitchPage(page)
    }

    fun toggleDanmaku() {
        danmakuEnabled = !danmakuEnabled
        Log.i(TAG_MENU, "toggle danmaku enabled=$danmakuEnabled")
        Log.i(TAG_DANMAKU, "danmaku enabled / disabled=$danmakuEnabled")
        showControls()
    }

    fun selectDanmakuSettingsFromMenu() {
        val entry = danmakuSettingsMenuEntries().getOrNull(subMenuIndex) ?: return
        sessionDanmakuSettings = entry.apply(sessionDanmakuSettings)
        Log.i(TAG_MENU, "selected menu item=${entry.label}")
        showControls()
    }

    fun confirmSubMenuSelection() {
        when (activeSubMenu) {
            PlayerSubMenu.Quality -> selectQualityFromMenu()
            PlayerSubMenu.Speed -> selectSpeedFromMenu()
            PlayerSubMenu.Pages -> selectPageFromMenu()
            PlayerSubMenu.DanmakuSettings -> selectDanmakuSettingsFromMenu()
            PlayerSubMenu.None -> Unit
        }
    }

    fun requestPlayerExit() {
        if (isExitRequested) {
            return
        }
        if (playUrlState !is UiState.Success) {
            onExit()
            return
        }

        isExitRequested = true
        pendingSeekJob?.cancel()
        playerController?.pause()
        controlsVisible = false
        seekBarVisible = false
        isSeekPreviewMode = false
        infoOverlayVisible = false
        bottomPanelVisible = false
        closeAllMenus()
        Log.i(TAG_BEHAVIOR, "safe player exit requested")
    }

    fun confirmQuickMenuSelection() {
        val item = quickMenuItems().getOrNull(quickMenuIndex) ?: return
        Log.i(TAG_MENU, "selected menu item=$item")
        when (item) {
            PlayerQuickMenuItem.Quality -> openSubMenu(PlayerSubMenu.Quality)
            PlayerQuickMenuItem.Speed -> openSubMenu(PlayerSubMenu.Speed)
            PlayerQuickMenuItem.Pages -> openSubMenu(PlayerSubMenu.Pages)
            PlayerQuickMenuItem.Recommendations -> {
                closeAllMenus()
                showingRecommendations = true
                bottomPanelIndex = recommendationPanelIndex
                openBottomPanel()
            }
            PlayerQuickMenuItem.DanmakuToggle -> toggleDanmaku()
            PlayerQuickMenuItem.DanmakuSettings -> openSubMenu(PlayerSubMenu.DanmakuSettings)
            PlayerQuickMenuItem.RestartPlayback -> restartPlaybackFromMenu()
            PlayerQuickMenuItem.BackToDetail -> {
                requestPlayerExit()
            }
        }
    }

    fun handleBackPress(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastBackPressTimeMs < BACK_DEBOUNCE_MS) {
            return true
        }
        lastBackPressTimeMs = now

        if (restartFromBeginning()) {
            return true
        }
        if (quickMenuOpen || activeSubMenu != PlayerSubMenu.None) {
            if (activeSubMenu != PlayerSubMenu.None) {
                closeSubMenu()
            } else {
                closeQuickMenu()
            }
            Log.i(TAG_KEYS, "Back handled by menu overlay")
            return true
        }
        if (bottomPanelVisible) {
            closeBottomPanel()
            Log.i(TAG_KEYS, "Back handled by bottom panel")
            return true
        }
        if (isSeekPreviewMode || seekBarVisible) {
            cancelSeekPreview()
            Log.i(TAG_KEYS, "Back handled by seek preview")
            return true
        }
        if (infoOverlayVisible) {
            infoOverlayVisible = false
            Log.i(TAG_KEYS, "Back handled by info overlay")
            return true
        }
        if (activeSubMenu != PlayerSubMenu.None) {
            closeSubMenu()
            return true
        }
        if (quickMenuOpen) {
            closeQuickMenu()
            return true
        }
        if (playUrlState is UiState.Success && controlsVisible) {
            controlsVisible = false
            seekHint = null
            return true
        }
        requestPlayerExit()
        return true
    }

    BackHandler {
        handleBackPress()
    }

    LaunchedEffect(video.id, video.cid) {
        logPlayerMemory("enter player bvid=${video.bvid} cid=${video.cid}")
    }

    DisposableEffect(video.id, video.cid) {
        onDispose {
            logPlayerMemory("exit player bvid=${video.bvid} cid=${video.cid}")
            pendingSeekJob?.cancel()
        }
    }

    LaunchedEffect(video.id, video.cid, hasRenderedFirstFrame) {
        showStartupIndicator = false
        if (!hasRenderedFirstFrame) {
            delay(PLAYER_STARTUP_INDICATOR_DELAY_MS)
            if (!hasRenderedFirstFrame) {
                showStartupIndicator = true
            }
        }
    }

    LaunchedEffect(video.id, shouldResume) {
        Log.i(
            TAG_RESUME,
            "resume check: bvid=${video.bvid}, duration=${video.duration}, " +
                "progress=${video.historyProgress}, shouldResume=$shouldResume, startPositionMs=$effectiveInitialSeekPositionMs"
        )
    }

    LaunchedEffect(video.cid) {
        BilibiliCdnPreference.beginPlaybackSession(video.cid)
        Log.i(TAG_DANMAKU, "page changed reset danmaku cid=${video.cid}")
        Log.i(TAG_RECOVERY, "page changed reset recovery state cid=${video.cid}")
        recoveryAttempt = 0
        decoderMode = rememberedDecoderPlan?.mode
            ?: VideoDecoderMode.AUTO_HARDWARE_FIRST
        decoderRecoveryAttempts = 0
        decoderRetryToken = 0
        decoderPlayerGeneration = 0
        pendingDecoderTargetQn = rememberedDecoderPlan?.targetQn
        rememberedDecoderPlanRequestIssued = false
        isRecovering = false
        recoverySeekPositionMs = 0L
        recoveryObservedLoading = false
        isUserRestartFromBeginning = false
        isRetryingSource = false
        isChangingQuality = false
        isChangingPart = false
        isAutoNextPart = false
        pendingSeekJob?.cancel()
        pendingSeekJob = null
        qn80BufferingTimeoutCount = 0
        lastProactiveRecoveryAtMs = 0L
        isSeekPreviewMode = false
        seekBarVisible = false
        if (keepBottomPanelOnVideoSwitch) {
            bottomPanelVisible = true
            bottomPanelOwnerFocused = false
            keepBottomPanelOnVideoSwitch = false
        } else {
            bottomPanelVisible = false
        }
        infoOverlayVisible = false
        danmakuItems = emptyList()
        closeAllMenus()
    }

    LaunchedEffect(video.cid, danmakuEnabled) {
        if (!danmakuEnabled) {
            Log.i(TAG_DANMAKU, "danmaku delayed/disabled cid=${video.cid}, lowMemory=$lowMemoryPlaybackMode")
            return@LaunchedEffect
        }
        delay(DANMAKU_LOAD_DELAY_MS)
        logPlayerMemory("before danmaku load cid=${video.cid}")
        danmakuRepository.loadDanmaku(video.cid, MAX_DANMAKU_ITEMS).fold(
            onSuccess = {
                danmakuItems = it
                Log.i(TAG_DANMAKU, "danmaku parsed limited count=${danmakuItems.size}, max=$MAX_DANMAKU_ITEMS")
                logPlayerMemory("after danmaku parse cid=${video.cid}")
            },
            onFailure = {
                danmakuItems = emptyList()
                if (danmakuLoadNotifiedCid != video.cid) {
                    danmakuLoadNotifiedCid = video.cid
                    Toast.makeText(
                        context,
                        "\u5f39\u5e55\u52a0\u8f7d\u5931\u8d25",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    LaunchedEffect(defaultDanmakuSettings, video.id) {
        sessionDanmakuSettings = defaultDanmakuSettings
        danmakuEnabled = defaultDanmakuSettings.enabledByDefault && !lowMemoryPlaybackMode
    }

    LaunchedEffect(controlsVisible, controlsVersion, quickMenuOpen, seekBarVisible, bottomPanelVisible, isSeekPreviewMode, playerUiState.isPlaying) {
        if (
            controlsVisible &&
            playUrlState is UiState.Success &&
            !quickMenuOpen &&
            !seekBarVisible &&
            !bottomPanelVisible &&
            !isSeekPreviewMode &&
            playerUiState.isPlaying
        ) {
            delay(3_000)
            if (
                !seekBarVisible &&
                !bottomPanelVisible &&
                !isSeekPreviewMode &&
                !quickMenuOpen &&
                playerUiState.isPlaying
            ) {
                controlsVisible = false
                seekHint = null
            }
        }
    }

    LaunchedEffect(seekBarVisible, playerUiState.isPlaying, isSeekPreviewMode, bottomPanelVisible, quickMenuOpen) {
        if (!seekBarVisible || !playerUiState.isPlaying || isSeekPreviewMode || bottomPanelVisible || quickMenuOpen) {
            return@LaunchedEffect
        }
        delay(SEEK_BAR_AUTO_HIDE_MS)
        if (seekBarVisible && playerUiState.isPlaying && !isSeekPreviewMode && !bottomPanelVisible && !quickMenuOpen) {
            seekBarVisible = false
        }
    }

    LaunchedEffect(seekHint, controlsVersion) {
        if (seekHint != null) {
            delay(1_000)
            seekHint = null
        }
    }

    LaunchedEffect(resumeHintVisible) {
        if (resumeHintVisible) {
            delay(5_000)
            resumeHintVisible = false
        }
    }

    LaunchedEffect(infoOverlayVisible, infoOverlayVersion) {
        if (infoOverlayVisible) {
            delay(INFO_OVERLAY_AUTO_HIDE_MS)
            infoOverlayVisible = false
            Log.i(TAG_OVERLAY, "infoOverlay hidden")
        }
    }

    LaunchedEffect(playUrlState, video.id, video.cid) {
        if (playUrlState is UiState.Success) {
            retainedPlayUrl = playUrlState.data
            delay(50)
            runCatching { focusRequester.requestFocus() }
            delay(250)
            runCatching { focusRequester.requestFocus() }
            Log.i(TAG_KEYS, "requestFocus after playUrl ready")
        }
    }

    LaunchedEffect(
        video.cid,
        rememberedDecoderPlan?.targetQn,
        (playUrlState as? UiState.Success)?.data?.requestId
    ) {
        val plan = rememberedDecoderPlan ?: return@LaunchedEffect
        if (rememberedDecoderPlanRequestIssued) {
            return@LaunchedEffect
        }
        val playUrl = (playUrlState as? UiState.Success)?.data ?: return@LaunchedEffect
        val selectedQn = playUrl.quality?.qn ?: 0
        decoderMode = plan.mode
        rememberedDecoderPlanRequestIssued = true
        if (selectedQn <= 0 || selectedQn > plan.targetQn) {
            pendingDecoderTargetQn = plan.targetQn
            qualitySwitchSeekPositionMs = initialSeekPositionMs
            isChangingQuality = true
            isRetryingSource = true
            Log.i(
                TAG_DECODER_POLICY,
                "apply remembered decoder plan cid=${video.cid}, " +
                    "selectedQn=$selectedQn->${plan.targetQn}, " +
                    "decoderMode=${plan.mode}, skipKnownBadHardware=true"
            )
            onSwitchQuality(plan.targetQn, initialSeekPositionMs)
        } else {
            pendingDecoderTargetQn = null
            Log.i(
                TAG_DECODER_POLICY,
                "remembered decoder plan already satisfied cid=${video.cid}, " +
                    "selectedQn=$selectedQn, decoderMode=${plan.mode}"
            )
        }
    }

    LaunchedEffect(
        (playUrlState as? UiState.Success)?.data?.requestId,
        pendingDecoderTargetQn
    ) {
        val targetQn = pendingDecoderTargetQn ?: return@LaunchedEffect
        val playUrl = (playUrlState as? UiState.Success)?.data ?: return@LaunchedEffect
        val selectedQn = playUrl.quality?.qn ?: 0
        if (selectedQn in 1..targetQn) {
            pendingDecoderTargetQn = null
            Log.i(
                TAG_DECODER_POLICY,
                "decoder quality target ready requestedQn=$targetQn, selectedQn=$selectedQn, " +
                    "playerGeneration=$decoderPlayerGeneration, requestId=${playUrl.requestId}"
            )
        }
    }

    LaunchedEffect(playUrlState, video.id, video.cid) {
        val playUrl = (playUrlState as? UiState.Success)?.data ?: return@LaunchedEffect
        val currentPage = video.pages.firstOrNull { it.cid == video.cid }
        Log.i(
            TAG_DEBUG,
            "enter player: bvid=${video.bvid}, aid=${video.aid}, cid=${video.cid}, " +
                "part index=${currentPage?.page ?: video.currentPage}, " +
                "part title=${currentPage?.part.orEmpty()}, " +
                "requested qn=${playUrl.requestedQn}, selected qn=${playUrl.quality?.qn ?: 0}, " +
                "selected format=${playUrl.selectedFormat.orEmpty()}, selected codec=${playUrl.videoCodec.orEmpty()}, " +
                "selected url type=${playUrl.sourceType}, " +
                "hasCookie=${playUrl.cookieHeader.isNotBlank()}, " +
                "hasSessData=${playUrl.cookieHeader.contains("SESSDATA")}, " +
                "hasReferer=${playUrl.referer.isNotBlank()}, " +
                "resumePositionMs=$initialSeekPositionMs, shouldResume=$shouldResume, " +
                "startPositionMs=$effectiveInitialSeekPositionMs, " +
                "isRetry=$isRetryingSource, retryCount=$recoveryAttempt, " +
                "isChangingQuality=$isChangingQuality, isChangingPart=$isChangingPart, " +
                "isAutoNextPart=$isAutoNextPart"
        )
    }

    LaunchedEffect(isRecovering, recoveryAttempt, decoderRetryToken) {
        if (!isRecovering) {
            return@LaunchedEffect
        }
        delay(RECOVERY_TIMEOUT_MS)
        if (isRecovering) {
            Log.e(
                TAG_RECOVERY,
                "recovery timeout after ${RECOVERY_TIMEOUT_MS}ms, attempt=$recoveryAttempt/$MAX_RECOVERY_ATTEMPTS"
            )
            isRecovering = false
            recoveryObservedLoading = false
            onPlaybackError("\u64ad\u653e\u6e90\u6062\u590d\u8d85\u65f6\uff0c\u8bf7\u91cd\u8bd5")
        }
    }

    LaunchedEffect(playUrlState, isRecovering, recoveryAttempt) {
        if (!isRecovering) {
            return@LaunchedEffect
        }
        when (playUrlState) {
            UiState.Loading -> recoveryObservedLoading = true
            is UiState.Success -> {
                if (recoveryObservedLoading) {
                    Log.i(TAG_PLAYER_RECOVERY, "refresh play url ready, wait stable playback before preferred host, seek back position=$recoverySeekPositionMs")
                }
            }
            is UiState.Error -> {
                if (recoveryObservedLoading) {
                    Log.e(TAG_PLAYER_RECOVERY, "refresh play url fail: ${playUrlState.message}")
                    isRecovering = false
                    if (recoveryAttempt < MAX_RECOVERY_ATTEMPTS) {
                        requestRecovery(playUrlState.message, recoverySeekPositionMs)
                    }
                }
            }
        }
    }

    LaunchedEffect(
        (playUrlState as? UiState.Success)?.data?.requestId,
        playerUiState.isPlaying,
        playerUiState.isBuffering
    ) {
        val playUrl = (playUrlState as? UiState.Success)?.data ?: return@LaunchedEffect
        if (!playerUiState.isPlaying || playerUiState.isBuffering) {
            return@LaunchedEffect
        }
        val requestId = playUrl.requestId
        delay(PREFERRED_HOST_STABLE_PLAYBACK_MS)
        val latestPlayUrl = (playUrlState as? UiState.Success)?.data ?: return@LaunchedEffect
        if (
            latestPlayUrl.requestId == requestId &&
            playerUiState.isPlaying &&
            !playerUiState.isBuffering &&
            !isRecovering
        ) {
            BilibiliCdnPreference.recordPreferredHost(
                cid = video.cid,
                host = latestPlayUrl.videoUrl.hostOnly(),
                streamRole = BilibiliCdnPreference.StreamRole.VIDEO
            )
            BilibiliCdnPreference.recordPreferredHost(
                cid = video.cid,
                host = latestPlayUrl.audioUrl.orEmpty().hostOnly(),
                streamRole = BilibiliCdnPreference.StreamRole.AUDIO
            )
            recoveryAttempt = 0
            decoderRecoveryAttempts = 0
            Log.i(
                TAG_CDN,
                "stable playback preferred host recorded after ${PREFERRED_HOST_STABLE_PLAYBACK_MS}ms, " +
                    "videoHost=${latestPlayUrl.videoUrl.hostOnly()}, audioHost=${latestPlayUrl.audioUrl.orEmpty().hostOnly()}, " +
                    "recoveryAttemptReset=true, decoderRecoveryAttemptReset=true, " +
                    "decoderMode=$decoderMode"
            )
        }
    }

    LaunchedEffect(
        playUrlState,
        playerUiState.isPlaying,
        playerUiState.isBuffering,
        retainedPlayUrl
    ) {
        val recoveredPlayUrl = retainedPlayUrl ?: return@LaunchedEffect
        if (
            playUrlState is UiState.Error &&
            playerUiState.isPlaying &&
            !playerUiState.isBuffering
        ) {
            Log.i(
                TAG_RECOVERY,
                "playback resumed after transient error; clear stale error overlay, " +
                    "requestId=${recoveredPlayUrl.requestId}"
            )
            recoveryAttempt = 0
            decoderRecoveryAttempts = 0
            isRecovering = false
            isRetryingSource = false
            recoveryObservedLoading = false
            onPlaybackRecovered(recoveredPlayUrl)
        }
    }

    LaunchedEffect(
        (playUrlState as? UiState.Success)?.data?.requestId,
        playerUiState.isBuffering,
        isRecovering
    ) {
        val playUrl =
            (playUrlState as? UiState.Success)?.data
                ?: return@LaunchedEffect

        if (!playerUiState.isBuffering || isRecovering) {
            return@LaunchedEffect
        }

        val now = SystemClock.elapsedRealtime()
        val cooldownRemainingMs = (
            lastProactiveRecoveryAtMs +
                PROACTIVE_RECOVERY_COOLDOWN_MS -
                now
        ).coerceAtLeast(0L)

        delay(
            maxOf(
                PROACTIVE_BUFFERING_TIMEOUT_MS,
                cooldownRemainingMs
            )
        )

        val latestPlayUrl =
            (playUrlState as? UiState.Success)?.data
                ?: return@LaunchedEffect

        if (
            latestPlayUrl.requestId == playUrl.requestId &&
            playerUiState.isBuffering &&
            !isRecovering
        ) {
            lastProactiveRecoveryAtMs =
                SystemClock.elapsedRealtime()

            Log.w(
                TAG_CDN,
                "proactive trigger=longBuffering, " +
                    "timeoutMs=$PROACTIVE_BUFFERING_TIMEOUT_MS, " +
                    "positionMs=${playerUiState.currentPositionMs}, " +
                    "videoHost=${latestPlayUrl.videoUrl.hostOnly()}, " +
                    "candidate=${latestPlayUrl.selectedVideoUrlIndex + 1}/" +
                    "${latestPlayUrl.videoUrlCandidates.size.coerceAtLeast(1)}"
            )

            requestRecovery(
                reason =
                    "BUFFERING_TIMEOUT_" +
                        "${PROACTIVE_BUFFERING_TIMEOUT_MS}MS",
                positionMs =
                    playerUiState.currentPositionMs,
                failedHost =
                    latestPlayUrl.videoUrl.hostOnly()
            )
        }
    }

    val displayTitle = video.displayTitleForPlayer()
    val resolvedPlayUrl = (playUrlState as? UiState.Success)?.data ?: retainedPlayUrl
    val visiblePlayUrl = resolvedPlayUrl?.takeUnless { candidate ->
        pendingDecoderTargetQn?.let { targetQn ->
            val candidateQn = candidate.quality?.qn ?: 0
            candidateQn <= 0 || candidateQn > targetQn
        } ?: false
    }
    val displayDurationMs = playerUiState.durationMs
        .takeIf { it > 0L }
        ?: retainedDurationMs
    val bottomProgressVisible = hasRenderedFirstFrame &&
        visiblePlayUrl != null &&
        !bottomPanelVisible &&
        !quickMenuOpen &&
        activeSubMenu == PlayerSubMenu.None &&
        (seekBarVisible || controlsVisible)
    val displayPositionMs = when {
        isSeekPreviewMode -> previewPositionMs
        pendingSeekPositionMs != null -> pendingSeekPositionMs ?: 0L
        else -> playerUiState.currentPositionMs
    }

    fun handlePlayerPreviewKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) {
            return false
        }

        return when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_BACK -> {
                        handleBackPress()
                        true
                    }

                    KeyEvent.KEYCODE_MENU -> {
                        Log.i(TAG_KEYS, "key down MENU, action=${event.nativeKeyEvent.action}")
                        if (quickMenuOpen) {
                            closeQuickMenu()
                        } else {
                            openQuickMenu()
                        }
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (isMenuActive()) {
                            if (activeSubMenu != PlayerSubMenu.None) {
                                closeSubMenu()
                            } else {
                                closeQuickMenu()
                            }
                            return true
                        }
                        if (bottomPanelVisible) {
                            if (!acceptBottomPanelNavigation()) return true
                            if (!bottomPanelOwnerFocused) {
                                bottomPanelIndex = (bottomPanelIndex - 1).coerceAtLeast(0)
                                if (showingRecommendations) recommendationPanelIndex = bottomPanelIndex
                                else sameUpPanelIndex = bottomPanelIndex
                            }
                            Log.i(TAG_KEYS, "key down LEFT, overlayState=bottomPanel, index=$bottomPanelIndex")
                            return true
                        }
                        Log.i(TAG, "Player key event: DPAD_LEFT")
                        Log.i(TAG_KEYS, "key down LEFT, overlayState=seekPreview")
                        updateSeekPreview(-10_000L)
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (isMenuActive()) {
                            return true
                        }
                        if (bottomPanelVisible) {
                            if (!acceptBottomPanelNavigation()) return true
                            if (!bottomPanelOwnerFocused) {
                                bottomPanelIndex = (bottomPanelIndex + 1)
                                    .coerceAtMost((displayedPanelVideos.size - 1).coerceAtLeast(0))
                                if (showingRecommendations) recommendationPanelIndex = bottomPanelIndex
                                else sameUpPanelIndex = bottomPanelIndex
                                if (!showingRecommendations && relatedPanelHasMore && bottomPanelIndex >= displayedPanelVideos.size - 3) {
                                    onLoadMoreRelatedPanel()
                                }
                            }
                            Log.i(TAG_KEYS, "key down RIGHT, overlayState=bottomPanel, index=$bottomPanelIndex")
                            return true
                        }
                        Log.i(TAG, "Player key event: DPAD_RIGHT")
                        Log.i(TAG_KEYS, "key down RIGHT, overlayState=seekPreview")
                        updateSeekPreview(10_000L)
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER -> {
                        if (activeSubMenu != PlayerSubMenu.None) {
                            confirmSubMenuSelection()
                            return true
                        }
                        if (quickMenuOpen) {
                            confirmQuickMenuSelection()
                            return true
                        }
                        if (bottomPanelVisible) {
                            if (bottomPanelOwnerFocused && relatedPanelOwner.ownerMid > 0L) {
                                closeBottomPanel()
                                onOpenOwnerSpace(relatedPanelOwner.ownerMid, relatedPanelOwner.ownerName)
                            } else {
                                displayedPanelVideos.getOrNull(bottomPanelIndex)?.let { selected ->
                                    Log.i(TAG_OVERLAY, "bottomPanel selected video=${selected.bvid}")
                                    keepBottomPanelOnVideoSwitch = true
                                    onRelatedPanelVideoClick(selected)
                                }
                            }
                            return true
                        }
                        if (isSeekPreviewMode) {
                            commitSeekPreview()
                        }
                        Log.i(TAG, "Player key event: DPAD_CENTER")
                        playerController?.playOrPause()
                        seekBarVisible = true
                        controlsVisible = true
                        controlsVersion += 1
                        Log.i(
                            TAG_OVERLAY,
                            "OK pressed, show bottom progress bar, controlsVisible=true, seekBarVisible=true, " +
                                "isPlaying=${playerUiState.isPlaying}"
                        )
                        Log.i(TAG_KEYS, "OK playPause, seekBarVisible=true")
                        showControls()
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (bottomPanelVisible) {
                            if (!acceptBottomPanelNavigation()) return true
                            if (!bottomPanelOwnerFocused && relatedPanelOwner.ownerMid > 0L) {
                                bottomPanelOwnerFocused = true
                            } else {
                                closeBottomPanel()
                            }
                            return true
                        }
                        if (activeSubMenu != PlayerSubMenu.None) {
                            subMenuIndex = (subMenuIndex - 1).coerceAtLeast(0)
                        } else if (quickMenuOpen) {
                            val count = quickMenuItems().size
                            quickMenuIndex = (quickMenuIndex - 1).coerceAtLeast(0)
                            if (count == 0) {
                                closeQuickMenu()
                            }
                        } else {
                            showInfoOverlay()
                        }
                        showControls()
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (bottomPanelVisible) {
                            if (!acceptBottomPanelNavigation()) return true
                            if (bottomPanelOwnerFocused && displayedPanelVideos.isNotEmpty()) {
                                bottomPanelOwnerFocused = false
                                bottomPanelIndex = bottomPanelIndex.coerceIn(
                                    0,
                                    (displayedPanelVideos.size - 1).coerceAtLeast(0)
                                )
                            }
                            return true
                        }
                        if (isSeekPreviewMode) {
                            return true
                        }
                        if (activeSubMenu != PlayerSubMenu.None) {
                            val count = subMenuItemCount()
                            subMenuIndex = (subMenuIndex + 1).coerceAtMost((count - 1).coerceAtLeast(0))
                        } else if (quickMenuOpen) {
                            val count = quickMenuItems().size
                            quickMenuIndex = (quickMenuIndex + 1).coerceAtMost((count - 1).coerceAtLeast(0))
                        } else {
                            Log.i(TAG_KEYS, "key down DOWN, open bottomPanel")
                            showingRecommendations = false
                            bottomPanelIndex = sameUpPanelIndex
                            openBottomPanel()
                        }
                        showControls()
                        true
                    }

                    else -> false
                }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (visiblePlayUrl != null) {
            key(decoderPlayerGeneration) {
                VideoPlayerView(
                    playUrl = visiblePlayUrl,
                    initialSeekPositionMs = effectiveInitialSeekPositionMs,
                    playbackSpeed = playbackSpeed,
                    resumePlaybackAfterSourceChange = resumePlaybackAfterSourceChange,
                    decoderMode = decoderMode,
                    decoderRetryToken = decoderRetryToken,
                    decoderPlayerGeneration = decoderPlayerGeneration,
                    exitRequested = isExitRequested,
                    modifier = Modifier.fillMaxSize(),
                    onPlayerStateChanged = { state ->
                        val previousPosition = lastObservedPositionMs
                        playerUiState = state
                        if (state.durationMs > 0L) {
                            retainedDurationMs = state.durationMs
                        }
                        val pendingPosition = pendingSeekPositionMs
                        if (
                            pendingPosition != null &&
                            !pendingSeekAwaitingSourceChange &&
                            !state.isBuffering &&
                            abs(state.currentPositionMs - pendingPosition) <= SEEK_SETTLE_TOLERANCE_MS
                        ) {
                            Log.i(
                                TAG_KEYS,
                                "seek settled target=$pendingPosition actual=${state.currentPositionMs}"
                            )
                            pendingSeekPositionMs = null
                        }
                        if (hasHandledPlaybackEnd) {
                            if (state.currentPositionMs <= PLAYBACK_RESTART_POSITION_MS) {
                                resetPlaybackEndHandledFlag()
                            } else if (previousPosition - state.currentPositionMs >= SEEK_BACK_RESET_MS) {
                                resetPlaybackEndHandledFlag()
                            }
                        }
                        lastObservedPositionMs = state.currentPositionMs
                    },
                    onPlayerControllerReady = { playerController = it },
                    onControlsRequested = ::showControls,
                    onSeekHint = {
                        seekHint = it
                        showControls()
                    },
                    onInitialSeekApplied = {
                        pendingSeekPositionMs = null
                        pendingSeekAwaitingSourceChange = false
                        if (isRecovering || isRetryingSource) {
                            Log.i(
                                TAG_RECOVERY,
                                "recovery ready, wait stable playback before marking preferred host, " +
                                    "seek back position=$recoverySeekPositionMs, decoderMode=$decoderMode"
                            )
                            isRecovering = false
                            isRetryingSource = false
                            recoveryObservedLoading = false
                            if (isChangingQuality) {
                                qualitySwitchSeekPositionMs = 0L
                                isChangingQuality = false
                            }
                        } else if (isChangingQuality) {
                            Log.i(TAG_QUALITY, "switch quality success, seek back position=$qualitySwitchSeekPositionMs")
                            qualitySwitchSeekPositionMs = 0L
                            isChangingQuality = false
                        } else if (shouldResume) {
                            resumeHintVisible = true
                        }
                    },
                    onRenderedFirstFrame = {
                        hasRenderedFirstFrame = true
                        showStartupIndicator = false
                        controlsVisible = false
                        seekBarVisible = false
                        seekHint = null

                        val currentQn = visiblePlayUrl.quality?.qn
                            ?: visiblePlayUrl.requestedQn
                        if (decoderMode == VideoDecoderMode.SOFTWARE_PREFERRED) {
                            val plan = DecoderCompatibilityMemory.recordSoftwareRequired(
                                cid = video.cid,
                                observedQn = currentQn
                            )
                            Log.i(
                                TAG_DECODER_POLICY,
                                "software decoder first frame; remember compatibility plan " +
                                    "cid=${video.cid}, qn=${plan.targetQn}, " +
                                    "expiresAtMs=${plan.expiresAtMs}"
                            )
                        }
                    },
                    onRecoverablePlaybackError = ::requestRecovery,
                    onPlaybackEnded = {
                        if (!hasHandledPlaybackEnd) {
                            hasHandledPlaybackEnd = true
                            closeAllMenus()
                            Log.i(TAG_BEHAVIOR, "playback ended, current cid=${video.cid}")
                            onPlaybackEnded()
                        }
                    },
                    onPlaybackError = onPlaybackError,
                    onSafeExitCompleted = {
                        Log.i(TAG_BEHAVIOR, "safe player exit completed; navigate to detail")
                        onExit()
                    }
                )
            }
        }

        if (visiblePlayUrl != null) {
            DanmakuOverlay(
                danmakuItems = danmakuItems,
                state = playerUiState,
                enabled = danmakuEnabled,
                settings = sessionDanmakuSettings,
                blocklistEnabled = danmakuBlocklistEnabled,
                blocklist = danmakuBlocklist,
                playbackSpeed = playbackSpeed,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (!hasRenderedFirstFrame && playUrlState !is UiState.Error) {
            PlayerStartupCover(
                coverUrl = video.coverUrl,
                showIndicator = showStartupIndicator,
                modifier = Modifier.fillMaxSize()
            )
        }

        val isRuntimePlaybackError =
            playUrlState is UiState.Error &&
                hasRenderedFirstFrame &&
                retainedPlayUrl != null

        if (playUrlState is UiState.Error && !isRuntimePlaybackError) {
            PlayerErrorContent(
                title = displayTitle,
                message = playUrlState.message,
                onRetry = {
                    Log.i(TAG_RECOVERY, "manual retry: reset recovery attempts and refresh play url")

                    val retryPosition =
                        playerUiState.currentPositionMs
                            .coerceAtLeast(
                                lastObservedPositionMs
                            )
                            .coerceAtLeast(0L)

                    BilibiliCdnPreference
                        .allowImmediateControlledProbe("manualRetry")

                    failedStreamHosts = emptySet()
                    failedVideoHosts = emptySet()
                    failedAudioHosts = emptySet()
                    lastFailedHost = null
                    failedHostWithReason = emptyMap()

                    DecoderCompatibilityMemory.clear(video.cid)
                    DashVideoCodecPreferenceMemory.clear(video.cid)
                    recoveryAttempt = 0
                    decoderMode = VideoDecoderMode.AUTO_HARDWARE_FIRST
                    decoderRecoveryAttempts = 0
                    decoderRetryToken += 1
                    decoderPlayerGeneration += 1
                    pendingDecoderTargetQn = null
                    playerController = null
                    isRecovering = true
                    isRetryingSource = true
                    recoverySeekPositionMs = retryPosition
                    recoveryObservedLoading = false
                    Log.i(TAG_DEBUG, "manual retry startPositionMs=$retryPosition, skip history resume=true")
                    onRetry()
                }
            )
        }

        if (isRuntimePlaybackError) {
            Text(
                text = "网络波动，等待播放恢复…",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 46.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            )
        }

        if (playUrlState is UiState.Success && quickMenuOpen) {
            PlayerControlsOverlay(
                title = displayTitle,
                state = playerUiState,
                seekHint = seekHint,
                seekPreviewPositionMs = previewPositionMs.takeIf { isSeekPreviewMode || seekBarVisible },
                qualityText = playUrlState.data.quality?.description,
                availableQualities = playUrlState.data.availableQualities,
                currentQualityQn = playUrlState.data.quality?.qn,
                playbackSpeed = playbackSpeed,
                pages = video.pages,
                currentCid = video.cid,
                danmakuEnabled = danmakuEnabled,
                danmakuSettings = sessionDanmakuSettings,
                quickMenuOpen = quickMenuOpen,
                activeSubMenu = activeSubMenu,
                quickMenuIndex = quickMenuIndex,
                subMenuIndex = subMenuIndex,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (bottomProgressVisible) {
            PlayerBottomControlsOverlay(
                visible = true,
                positionMs = displayPositionMs,
                durationMs = displayDurationMs,
                isSeekPreviewMode = isSeekPreviewMode,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        if (visiblePlayUrl != null && infoOverlayVisible) {
            val currentPage = video.pages.firstOrNull { it.cid == video.cid }
            PlayerInfoOverlay(
                title = displayTitle,
                ownerName = video.ownerName,
                partTitle = currentPage?.let { "P${it.page} ${it.part}" },
                qualityText = visiblePlayUrl.quality?.description,
                playbackSpeed = playbackSpeed,
                danmakuEnabled = danmakuEnabled,
                currentPositionMs = displayPositionMs,
                durationMs = displayDurationMs,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 42.dp, top = 34.dp, end = 42.dp)
            )
        }

        if (bottomPanelVisible) {
            PlayerRelatedVideoPanel(
                title = if (showingRecommendations) "相关推荐" else relatedPanelTitle,
                owner = if (showingRecommendations) relatedPanelOwner.copy(ownerMid = 0L) else relatedPanelOwner,
                videos = displayedPanelVideos,
                loading = relatedPanelLoading,
                loadingMore = relatedPanelLoadingMore,
                loadMoreError = relatedPanelLoadMoreError,
                playbackStatusMessage = when (playUrlState) {
                    UiState.Loading -> "正在切换视频…"
                    is UiState.Error -> "播放失败，可直接选择其他视频"
                    is UiState.Success -> null
                },
                ownerFocused = bottomPanelOwnerFocused,
                focusedVideoIndex = bottomPanelIndex,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        if (playUrlState is UiState.Success && resumeHintVisible) {
            ResumeHint(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 34.dp)
            )
        }

        PlaybackProgressReporter(
            video = video,
            state = playerUiState,
            enabled = playUrlState is UiState.Success,
            onReport = onReportPlaybackProgress
        )

        if (playUrlState !is UiState.Error) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            Log.i(
                                TAG_KEYS,
                                "player focus gained"
                            )
                        } else {
                            Log.w(
                                TAG_KEYS,
                                "player focus lost"
                            )
                        }
                    }
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        handlePlayerPreviewKey(event)
                    }
            )
        }
    }
}

private fun PlayUrl.failureRoleForHost(host: String): BilibiliCdnPreference.StreamRole {
    val normalized = host.trim().lowercase()
    if (normalized.isBlank()) {
        return BilibiliCdnPreference.StreamRole.UNKNOWN
    }
    val matchesVideo = videoUrl.hostOnly() == normalized
    val matchesAudio = audioUrl.orEmpty().hostOnly() == normalized
    return when {
        matchesVideo && matchesAudio -> BilibiliCdnPreference.StreamRole.BOTH
        matchesVideo -> BilibiliCdnPreference.StreamRole.VIDEO
        matchesAudio -> BilibiliCdnPreference.StreamRole.AUDIO
        else -> BilibiliCdnPreference.StreamRole.UNKNOWN
    }
}

private fun PlayUrl.switchToBetterCdn(
    cid: Long,
    failedVideoHosts: Set<String>,
    failedAudioHosts: Set<String>
): PlayUrl? {
    if (!sourceType.contains("dash", ignoreCase = true)) {
        return null
    }
    val video = selectBestCdnCandidate(
        candidates = videoUrlCandidates,
        currentIndex = selectedVideoUrlIndex,
        cid = cid,
        failedHosts = failedVideoHosts,
        streamName = "video"
    )
    val audio = selectBestCdnCandidate(
        candidates = audioUrlCandidates,
        currentIndex = selectedAudioUrlIndex,
        cid = cid,
        failedHosts = failedAudioHosts,
        streamName = "audio"
    )
    val nextVideo = video ?: return null
    val nextAudio = audio ?: return null
    if (nextVideo.index == selectedVideoUrlIndex && nextAudio.index == selectedAudioUrlIndex) {
        Log.i(
            TAG_CDN,
            "fallbackStep=switchCdnCandidate, no route change, " +
                "videoHost=${nextVideo.url.hostOnly()}, audioHost=${nextAudio.url.hostOnly()}, " +
                "videoControlledProbe=${nextVideo.controlledProbe}, audioControlledProbe=${nextAudio.controlledProbe}"
        )
        return null
    }
    Log.i(
        TAG_CDN,
        "fallbackStep=switchCdnCandidate, videoHost=${nextVideo.url.hostOnly()}, " +
            "videoScore=${nextVideo.score}, videoDynamicScore=${nextVideo.dynamicScore}, " +
            "audioHost=${nextAudio.url.hostOnly()}, audioScore=${nextAudio.score}, " +
            "audioDynamicScore=${nextAudio.dynamicScore}, " +
            "preferredVideo=${nextVideo.preferred}, preferredAudio=${nextAudio.preferred}, " +
            "failedHostPenaltyVideo=${nextVideo.failedPenalty}, failedHostPenaltyAudio=${nextAudio.failedPenalty}, " +
            "videoControlledProbe=${nextVideo.controlledProbe}, audioControlledProbe=${nextAudio.controlledProbe}"
    )
    return copy(
        videoUrl = nextVideo.url,
        audioUrl = nextAudio.url,
        selectedVideoUrlIndex = nextVideo.index,
        selectedAudioUrlIndex = nextAudio.index,
        requestId = System.nanoTime()
    )
}

private fun PlayUrl.hasSafeAlternativeCdn(
    failedVideoHosts: Set<String>,
    failedAudioHosts: Set<String>
): Boolean {
    if (!sourceType.contains("dash", ignoreCase = true)) {
        return false
    }

    fun streamHasSafeRoute(
        candidates: List<String>,
        currentIndex: Int,
        failedHosts: Set<String>,
        streamName: String
    ): Boolean {
        if (candidates.isEmpty()) return false
        val current = candidates.getOrNull(currentIndex)
        val currentStatus = current?.let { url ->
            BilibiliCdnPreference.routeStatus(
                host = url.hostOnly(),
                cid = 0L,
                explicitFailedHosts = failedHosts,
                streamName = streamName
            )
        }
        if (currentStatus != null && !currentStatus.coolingDown) {
            return true
        }
        return candidates.withIndex().any { candidate ->
            if (candidate.index == currentIndex) return@any false
            val status = BilibiliCdnPreference.routeStatus(
                host = candidate.value.hostOnly(),
                cid = 0L,
                explicitFailedHosts = failedHosts,
                streamName = streamName
            )
            !status.coolingDown && !status.weak
        }
    }

    val hasSafeVideo = streamHasSafeRoute(
        candidates = videoUrlCandidates,
        currentIndex = selectedVideoUrlIndex,
        failedHosts = failedVideoHosts,
        streamName = "video"
    )
    val hasSafeAudio = streamHasSafeRoute(
        candidates = audioUrlCandidates,
        currentIndex = selectedAudioUrlIndex,
        failedHosts = failedAudioHosts,
        streamName = "audio"
    )

    Log.i(
        TAG_CDN,
        "safe same-qn route check: video=$hasSafeVideo, audio=$hasSafeAudio, " +
            "currentVideoHost=${videoUrl.hostOnly()}, currentAudioHost=${audioUrl.orEmpty().hostOnly()}, " +
            "failedVideoHosts=${failedVideoHosts.joinToString(prefix = "[", postfix = "]")}, " +
            "failedAudioHosts=${failedAudioHosts.joinToString(prefix = "[", postfix = "]")}"
    )
    return hasSafeVideo && hasSafeAudio
}

private fun PlayUrl.allCurrentQnCandidatesFailed(
    failedVideoHosts: Set<String>,
    failedAudioHosts: Set<String>
): Boolean {
    fun allUnavailable(
        candidates: List<String>,
        failedHosts: Set<String>,
        streamName: String
    ): Boolean {
        if (candidates.isEmpty()) return false
        return candidates.all { url ->
            BilibiliCdnPreference.routeStatus(
                host = url.hostOnly(),
                cid = 0L,
                explicitFailedHosts = failedHosts,
                streamName = streamName
            ).coolingDown
        }
    }

    val videoUnavailable = allUnavailable(videoUrlCandidates, failedVideoHosts, "video")
    val audioUnavailable = allUnavailable(audioUrlCandidates, failedAudioHosts, "audio")
    Log.i(
        TAG_CDN,
        "all current qn routes unavailable: video=$videoUnavailable, audio=$audioUnavailable"
    )
    return videoUnavailable || audioUnavailable
}

private data class CdnCandidate(
    val url: String,
    val index: Int,
    val score: Int,
    val preferred: Boolean,
    val failedPenalty: Int,
    val cooldownRemainingMs: Long,
    val failureCount: Int,
    val dynamicScore: Int,
    val dynamicConfidencePercent: Int,
    val performanceSamples: Int,
    val controlledProbe: Boolean
)

private fun selectBestCdnCandidate(
    candidates: List<String>,
    currentIndex: Int,
    cid: Long,
    failedHosts: Set<String>,
    streamName: String
): CdnCandidate? {
    if (candidates.isEmpty()) {
        return null
    }
    val ranked = candidates.mapIndexed { index, url ->
        val status = BilibiliCdnPreference.routeStatus(
            host = url.hostOnly(),
            cid = cid,
            explicitFailedHosts = failedHosts,
            streamName = streamName
        )
        CdnCandidate(
            url = url,
            index = index,
            score = status.score,
            preferred = status.preferred,
            failedPenalty = if (status.coolingDown) 1_000 else 0,
            cooldownRemainingMs = status.cooldownRemainingMs,
            failureCount = status.failureCount,
            dynamicScore = status.dynamicScore,
            dynamicConfidencePercent = status.dynamicConfidencePercent,
            performanceSamples = status.performanceSamples,
            controlledProbe = false
        )
    }

    val current = ranked.getOrNull(currentIndex)
    val selected = if (current != null && current.failedPenalty == 0) {
        current
    } else {
        ranked
            .filter { it.failedPenalty == 0 }
            .sortedWith(
                compareByDescending<CdnCandidate> { it.score }
                    .thenBy { if (it.index == currentIndex) 1 else 0 }
                    .thenBy { it.index }
            )
            .firstOrNull()
            ?: ranked
                .sortedWith(
                    compareBy<CdnCandidate> { it.cooldownRemainingMs }
                        .thenBy { it.failureCount }
                        .thenByDescending { it.score }
                        .thenBy { if (it.index == currentIndex) 1 else 0 }
                        .thenBy { it.index }
                )
                .firstOrNull { candidate ->
                    BilibiliCdnPreference.reserveControlledProbe(
                        host = candidate.url.hostOnly(),
                        streamName = streamName,
                        forceWhenExhausted = false
                    )
                }
                ?.copy(controlledProbe = true)
    }

    return selected?.also {
        Log.i(
            TAG_CDN,
            "stream=$streamName selectedHostScore=${it.score}, selectedHost=${it.url.hostOnly()}, " +
                "preferredHost=${it.preferred}, failedHostPenalty=${it.failedPenalty}, " +
                "cooldownRemainingMs=${it.cooldownRemainingMs}, failureCount=${it.failureCount}, " +
                "dynamicScore=${it.dynamicScore}, dynamicConfidence=${it.dynamicConfidencePercent}, " +
                "performanceSamples=${it.performanceSamples}, " +
                "controlledProbe=${it.controlledProbe}, currentIndex=$currentIndex"
        )
    }
}

private fun String.isDecoderFailureReason(): Boolean {
    return startsWith("DECODER_FAILURE:", ignoreCase = true) ||
        contains("ERROR_CODE_DECODING_FAILED", ignoreCase = true) ||
        contains("MediaCodecVideoDecoderException", ignoreCase = true) ||
        contains("解码失败")
}

private fun String.isBadSourceHostReason(): Boolean {
    return contains("BAD_SOURCE_HOST", ignoreCase = true) ||
        contains("ERROR_CODE_PARSING_CONTAINER_MALFORMED", ignoreCase = true) ||
        contains("Invalid NAL length", ignoreCase = true) ||
        contains("ParserException", ignoreCase = true)
}

private fun String.isAvcCodec(): Boolean {
    return startsWith("avc1", ignoreCase = true) ||
        startsWith("avc3", ignoreCase = true)
}

private fun String.isHevcCodec(): Boolean {
    return startsWith("hvc1", ignoreCase = true) ||
        startsWith("hev1", ignoreCase = true)
}

private const val TAG = "PlayerScreen"
private const val TAG_MENU = "BiliPlayerMenu"
private const val TAG_RESUME = "BiliResume"
private const val TAG_RECOVERY = "BiliRecovery"
private const val TAG_DECODER_POLICY = "BiliDecoderPolicy"
private const val TAG_QUALITY = "BiliQuality"
private const val TAG_SPEED = "BiliSpeed"
private const val TAG_PAGES = "BiliPages"
private const val TAG_DANMAKU = "BiliDanmaku"
private const val TAG_BEHAVIOR = "BiliPlaybackBehavior"
private const val TAG_PLAYER = "BiliPlayer"
private const val TAG_DEBUG = "BiliPlayerDebug"
private const val TAG_PLAYER_RECOVERY = "BiliPlayerRecovery"
private const val TAG_CDN = "BiliCdnFallback"
private const val PLAYBACK_RESTART_POSITION_MS = 3_000L
private const val SEEK_BACK_RESET_MS = 5_000L
private const val SEEK_SETTLE_TOLERANCE_MS = 5_000L
private const val MAX_RECOVERY_ATTEMPTS = 3
private const val MAX_DECODER_RECOVERY_ATTEMPTS = 4
private const val DECODER_STARTUP_POSITION_THRESHOLD_MS = 1_500L
private const val BACK_DEBOUNCE_MS = 300L
private const val RECOVERY_TIMEOUT_MS = 15_000L
private const val SEEK_PREVIEW_COMMIT_DELAY_MS = 700L
private const val SEEK_BAR_AUTO_HIDE_MS = 3_000L
private const val PLAYER_STARTUP_INDICATOR_DELAY_MS = 700L
private const val INFO_OVERLAY_AUTO_HIDE_MS = 3_000L
private const val BLOCKED_REQUEST_RECOVERY_DELAY_MS = 1_500L
private const val DANMAKU_LOAD_DELAY_MS = 2_500L
private const val MAX_DANMAKU_ITEMS = 3_000
private const val PREFERRED_HOST_STABLE_PLAYBACK_MS = 20_000L
private const val PROACTIVE_BUFFERING_TIMEOUT_MS = 6_000L
private const val PROACTIVE_RECOVERY_COOLDOWN_MS = 20_000L
private const val TAG_KEYS = "BiliPlayerKeys"
private const val TAG_OVERLAY = "BiliPlayerOverlay"

private fun VideoItem.resumePositionMs(): Long {
    val progress = historyProgress
    val duration = duration
    val shouldResume = historyViewAt > 0L &&
        progress >= MIN_RESUME_SECONDS &&
        (duration <= 0L || progress < duration - RESUME_END_THRESHOLD_SECONDS)
    return if (shouldResume) {
        progress * 1000L
    } else {
        0L
    }
}

private const val MIN_RESUME_SECONDS = 10L
private const val RESUME_END_THRESHOLD_SECONDS = 30L

private fun formatPlayerOverlayTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

@Composable
private fun ResumeHint(modifier: Modifier = Modifier) {
    Text(
        text = "\u5df2\u4ece\u4e0a\u6b21\u89c2\u770b\u4f4d\u7f6e\u7ee7\u7eed\u64ad\u653e\uff0c\u6309\u8fd4\u56de\u4ece\u5934\u64ad\u653e",
        color = Color.White,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(Color(0xCC000000), RoundedCornerShape(8.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp)
    )
}

@Composable
private fun PlayerStartupCover(
    coverUrl: String,
    showIndicator: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(Color.Black)
    ) {
        val resolvedCover = remember(coverUrl) {
            BilibiliImageUrl.cover(
                url = coverUrl,
                width = 960,
                height = 540
            )
        }

        if (resolvedCover.isNotBlank()) {
            AsyncImage(
                model = resolvedCover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
            )
        }

        if (showIndicator) {
            CircularProgressIndicator(
                color = Color.White.copy(alpha = 0.88f),
                strokeWidth = 2.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(34.dp)
            )
        }
    }
}

@Composable
private fun PlayerErrorContent(
    title: String,
    message: String,
    onRetry: () -> Unit
) {
    var focused by remember {
        mutableStateOf(false)
    }

    val retryFocusRequester =
        remember { FocusRequester() }

    val scale =
        if (focused) 1.06f else 1f

    LaunchedEffect(Unit) {
        delay(100)
        runCatching {
            retryFocusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(46.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.TopStart)
            )

            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "\u64ad\u653e\u5931\u8d25",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = message,
                    color = Color(0xFFB8BDC7),
                    fontSize = 17.sp,
                    modifier = Modifier.padding(top = 10.dp, bottom = 24.dp)
                )

                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .focusRequester(
                            retryFocusRequester
                        )
                        .width(140.dp)
                        .height(52.dp)
                        .scale(scale)
                        .border(
                            width = if (focused) 3.dp else 0.dp,
                            color = if (focused) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .onFocusChanged { focused = it.isFocused }
                        .focusable(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NbltPrimary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "\u91cd\u8bd5",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
