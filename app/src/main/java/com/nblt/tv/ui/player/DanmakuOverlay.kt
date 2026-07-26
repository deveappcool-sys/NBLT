package com.nblt.tv.ui.player

import android.util.Log
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nblt.tv.model.DanmakuItem
import com.nblt.tv.model.DanmakuSettings
import kotlinx.coroutines.delay
import org.json.JSONArray
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun DanmakuOverlay(
    danmakuItems: List<DanmakuItem>,
    state: PlayerUiState,
    enabled: Boolean,
    settings: DanmakuSettings,
    blocklistEnabled: Boolean,
    blocklist: List<String>,
    playbackSpeed: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    if (!enabled || danmakuItems.isEmpty()) return

    val filteredItems = remember(danmakuItems, blocklistEnabled, blocklist) {
        filterDanmakuByBlocklist(danmakuItems, blocklistEnabled, blocklist)
    }
    if (filteredItems.isEmpty()) return

    val sortedItems = remember(filteredItems) { filteredItems.sortedBy { it.timeSeconds } }
    val activeDanmaku = remember(sortedItems) { mutableStateListOf<ActiveDanmaku>() }
    val latestState = rememberUpdatedState(state)
    val latestSettings = rememberUpdatedState(settings)
    val latestPlaybackSpeed = rememberUpdatedState(playbackSpeed.coerceAtLeast(0.1f))

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val topPaddingPx = with(density) { 18.dp.toPx() }
        val bottomReservedPx = with(density) { BOTTOM_RESERVED_DP.dp.toPx() }
        val trackHeightPx = with(density) { (34.dp * settings.fontScale).toPx() }
        val displayAreaRatio = settings.displayAreaRatio.coerceIn(0.25f, 1f)
        val availableHeightPx = if (displayAreaRatio >= 1f) {
            (screenHeightPx - bottomReservedPx).coerceAtLeast(trackHeightPx)
        } else {
            (screenHeightPx * displayAreaRatio).coerceAtLeast(trackHeightPx)
        }
        val trackCount = ((availableHeightPx - topPaddingPx) / trackHeightPx)
            .toInt()
            .coerceIn(1, MAX_TRACK_COUNT)

        LaunchedEffect(enabled, screenWidthPx, screenHeightPx) {
            var lastFrameTimeNs = 0L
            while (enabled) {
                val frameTimeNs = withFrameNanos { it }
                if (lastFrameTimeNs == 0L) {
                    lastFrameTimeNs = frameTimeNs
                    continue
                }
                val deltaSeconds = (frameTimeNs - lastFrameTimeNs) / 1_000_000_000f
                lastFrameTimeNs = frameTimeNs
                val playerState = latestState.value
                if (!playerState.isPlaying) continue

                val speedFactor = latestPlaybackSpeed.value
                activeDanmaku.forEach { active ->
                    when (active.kind) {
                        DanmakuKind.ScrollRightToLeft -> active.x -= active.speedPxPerSecond * deltaSeconds * speedFactor
                        DanmakuKind.ScrollLeftToRight -> active.x += active.speedPxPerSecond * deltaSeconds * speedFactor
                        else -> Unit
                    }
                }
                val currentSeconds = playerState.currentPositionMs / 1000f
                activeDanmaku.removeAll { active ->
                    when (active.kind) {
                        DanmakuKind.ScrollRightToLeft -> active.x < -active.textWidthPx
                        DanmakuKind.ScrollLeftToRight -> active.x > screenWidthPx
                        DanmakuKind.TopFixed,
                        DanmakuKind.BottomFixed,
                        DanmakuKind.Advanced -> currentSeconds >= active.endTimeSeconds
                    }
                }
            }
        }

        LaunchedEffect(
            sortedItems,
            enabled,
            trackCount,
            settings.speed,
            settings.fontScale,
            settings.displayAreaRatio,
            screenWidthPx,
            screenHeightPx
        ) {
            activeDanmaku.clear()
            var nextIndex = findNextIndex(sortedItems, latestState.value.currentPositionMs / 1000f)
            var lastPositionMs = latestState.value.currentPositionMs
            Log.i(TAG, "multi-mode overlay started, count=${sortedItems.size}, tracks=$trackCount")

            while (enabled) {
                delay(TICK_MS)
                val playerState = latestState.value
                val currentMs = playerState.currentPositionMs.coerceAtLeast(0L)
                val currentSeconds = currentMs / 1000f
                val jumped = abs(currentMs - lastPositionMs) > SEEK_THRESHOLD_MS
                if (jumped) {
                    activeDanmaku.clear()
                    nextIndex = findNextIndex(sortedItems, currentSeconds)
                    Log.i(TAG, "seek reset, current=$currentMs next=$nextIndex")
                }
                lastPositionMs = currentMs
                if (!playerState.isPlaying || jumped) continue

                while (
                    nextIndex < sortedItems.size &&
                    sortedItems[nextIndex].timeSeconds <= currentSeconds + LOOK_AHEAD_SECONDS
                ) {
                    val item = sortedItems[nextIndex++]
                    if (item.timeSeconds < currentSeconds - LATE_DROP_SECONDS) continue
                    if (activeDanmaku.size >= MAX_ACTIVE_DANMAKU) continue

                    val kind = item.mode.toDanmakuKind()
                    val advanced = if (kind == DanmakuKind.Advanced) parseAdvancedDanmaku(item.text) else null
                    val displayText = advanced?.text ?: item.text
                    if (displayText.isBlank()) continue
                    val itemScale = (item.fontSize / 25f).coerceIn(0.72f, 1.45f)
                    val fontSizeSp = 18f * latestSettings.value.fontScale * itemScale
                    val textWidthPx = estimateTextWidthPx(
                        displayText,
                        with(density) { fontSizeSp.sp.toPx() }
                    )
                    val durationMs = durationMsFor(latestSettings.value, textWidthPx, screenWidthPx)
                    val scrollSpeed = (screenWidthPx + textWidthPx) / (durationMs / 1000f)

                    val active = when (kind) {
                        DanmakuKind.ScrollRightToLeft -> {
                            val track = findScrollingTrack(activeDanmaku, trackCount, screenWidthPx, scrollSpeed)
                                ?: continue
                            ActiveDanmaku(
                                item = item,
                                displayText = displayText,
                                kind = kind,
                                trackIndex = track,
                                x = screenWidthPx,
                                y = topPaddingPx + track * trackHeightPx,
                                speedPxPerSecond = scrollSpeed,
                                textWidthPx = textWidthPx,
                                fontSizeSp = fontSizeSp,
                                endTimeSeconds = Float.MAX_VALUE,
                                alphaMultiplier = 1f
                            )
                        }
                        DanmakuKind.ScrollLeftToRight -> {
                            val track = findExclusiveScrollingTrack(activeDanmaku, trackCount) ?: continue
                            ActiveDanmaku(
                                item, displayText, kind, track,
                                x = -textWidthPx,
                                y = topPaddingPx + track * trackHeightPx,
                                speedPxPerSecond = scrollSpeed,
                                textWidthPx = textWidthPx,
                                fontSizeSp = fontSizeSp,
                                endTimeSeconds = Float.MAX_VALUE,
                                alphaMultiplier = 1f
                            )
                        }
                        DanmakuKind.TopFixed,
                        DanmakuKind.BottomFixed -> {
                            val fixedTrack = findFixedTrack(activeDanmaku, kind, currentSeconds) ?: continue
                            val y = if (kind == DanmakuKind.TopFixed) {
                                topPaddingPx + fixedTrack * trackHeightPx
                            } else {
                                screenHeightPx - bottomReservedPx - (fixedTrack + 1) * trackHeightPx
                            }
                            ActiveDanmaku(
                                item, displayText, kind, fixedTrack,
                                x = ((screenWidthPx - textWidthPx) / 2f).coerceAtLeast(0f),
                                y = y,
                                speedPxPerSecond = 0f,
                                textWidthPx = textWidthPx,
                                fontSizeSp = fontSizeSp,
                                endTimeSeconds = currentSeconds + FIXED_DURATION_SECONDS,
                                alphaMultiplier = 1f
                            )
                        }
                        DanmakuKind.Advanced -> {
                            val spec = advanced ?: continue
                            ActiveDanmaku(
                                item, displayText, kind, 0,
                                x = (spec.xFraction * screenWidthPx).coerceIn(0f, (screenWidthPx - textWidthPx).coerceAtLeast(0f)),
                                y = (spec.yFraction * (screenHeightPx - bottomReservedPx)).coerceAtLeast(0f),
                                speedPxPerSecond = 0f,
                                textWidthPx = textWidthPx,
                                fontSizeSp = fontSizeSp,
                                endTimeSeconds = currentSeconds + spec.durationSeconds,
                                alphaMultiplier = spec.alpha
                            )
                        }
                    }
                    activeDanmaku += active
                }
            }
        }

        activeDanmaku.forEach { active ->
            Text(
                text = active.displayText,
                color = Color(active.item.color or 0xFF000000.toInt())
                    .copy(alpha = (settings.alpha * active.alphaMultiplier).coerceIn(0.15f, 1f)),
                fontSize = active.fontSizeSp.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                style = TextStyle(
                    shadow = Shadow(Color.Black, Offset(2f, 2f), 3f)
                ),
                modifier = Modifier.offset {
                    IntOffset(active.x.roundToInt(), active.y.roundToInt())
                }
            )
        }
    }
}

private enum class DanmakuKind {
    ScrollRightToLeft,
    ScrollLeftToRight,
    TopFixed,
    BottomFixed,
    Advanced
}

private fun Int.toDanmakuKind(): DanmakuKind = when (this) {
    4 -> DanmakuKind.BottomFixed
    5 -> DanmakuKind.TopFixed
    6 -> DanmakuKind.ScrollLeftToRight
    7 -> DanmakuKind.Advanced
    else -> DanmakuKind.ScrollRightToLeft
}

private class ActiveDanmaku(
    val item: DanmakuItem,
    val displayText: String,
    val kind: DanmakuKind,
    val trackIndex: Int,
    x: Float,
    val y: Float,
    val speedPxPerSecond: Float,
    val textWidthPx: Float,
    val fontSizeSp: Float,
    val endTimeSeconds: Float,
    val alphaMultiplier: Float
) {
    var x by mutableFloatStateOf(x)
}

private data class AdvancedDanmaku(
    val xFraction: Float,
    val yFraction: Float,
    val alpha: Float,
    val durationSeconds: Float,
    val text: String
)

private fun parseAdvancedDanmaku(raw: String): AdvancedDanmaku? = runCatching {
    val array = JSONArray(raw)
    val rawX = array.optDouble(0, 0.5).toFloat()
    val rawY = array.optDouble(1, 0.5).toFloat()
    val alpha = array.optString(2, "1")
        .substringAfterLast('-')
        .toFloatOrNull()
        ?.coerceIn(0.15f, 1f)
        ?: 1f
    val durationRaw = array.optDouble(3, 4.0).toFloat()
    AdvancedDanmaku(
        xFraction = if (rawX <= 1f) rawX else rawX / 1000f,
        yFraction = if (rawY <= 1f) rawY else rawY / 450f,
        alpha = alpha,
        durationSeconds = (if (durationRaw > 100f) durationRaw / 1000f else durationRaw).coerceIn(1f, 12f),
        text = array.optString(4, "").ifBlank { raw }
    )
}.getOrNull()

private fun findScrollingTrack(
    active: List<ActiveDanmaku>,
    trackCount: Int,
    screenWidthPx: Float,
    newSpeed: Float
): Int? = (0 until trackCount).firstOrNull { track ->
    active.filter { it.trackIndex == track && it.kind in scrollingKinds }.all {
        if (it.kind != DanmakuKind.ScrollRightToLeft) return@all false
        val enoughGap = it.x < screenWidthPx - MIN_TRACK_GAP_PX
        val willNotCatch = newSpeed <= it.speedPxPerSecond ||
            (screenWidthPx - it.x) / (newSpeed - it.speedPxPerSecond) > 5f
        enoughGap && willNotCatch
    }
}

private fun findExclusiveScrollingTrack(active: List<ActiveDanmaku>, trackCount: Int): Int? =
    (0 until trackCount).firstOrNull { track ->
        active.none { it.trackIndex == track && it.kind in scrollingKinds }
    }

private fun findFixedTrack(
    active: List<ActiveDanmaku>,
    kind: DanmakuKind,
    currentSeconds: Float
): Int? = (0 until FIXED_TRACK_COUNT).firstOrNull { track ->
    active.none { it.kind == kind && it.trackIndex == track && it.endTimeSeconds > currentSeconds }
}

private fun findNextIndex(items: List<DanmakuItem>, currentSeconds: Float): Int {
    var low = 0
    var high = items.size
    val target = currentSeconds - 0.1f
    while (low < high) {
        val mid = (low + high) / 2
        if (items[mid].timeSeconds < target) low = mid + 1 else high = mid
    }
    return low
}

private fun durationMsFor(settings: DanmakuSettings, textWidthPx: Float, screenWidthPx: Float): Float {
    val base = when {
        settings.speed < 0.9f -> 9_000f
        settings.speed > 1.15f -> 6_000f
        else -> 7_500f
    }
    val ratio = if (screenWidthPx > 0f) textWidthPx / screenWidthPx else 0f
    return (base + ((ratio - 0.22f) * 1_800f).coerceIn(-1_000f, 1_500f))
        .coerceIn(5_500f, 10_000f)
}

private fun estimateTextWidthPx(text: String, fontSizePx: Float): Float =
    (text.length.coerceAtLeast(2) * fontSizePx * 0.9f).coerceAtLeast(80f)

fun filterDanmakuByBlocklist(
    items: List<DanmakuItem>,
    blocklistEnabled: Boolean,
    blocklist: List<String>
): List<DanmakuItem> {
    if (!blocklistEnabled || blocklist.isEmpty()) return items
    val keywords = blocklist.map { it.trim().lowercase() }.filter { it.isNotBlank() }
    if (keywords.isEmpty()) return items
    val filtered = items.filter { item ->
        val text = item.text.lowercase()
        keywords.none(text::contains)
    }
    val removed = items.size - filtered.size
    if (removed > 0) Log.i(TAG_BLOCK, "filtered danmaku count=$removed")
    return filtered
}

private val scrollingKinds = setOf(
    DanmakuKind.ScrollRightToLeft,
    DanmakuKind.ScrollLeftToRight
)
private const val TICK_MS = 100L
private const val SEEK_THRESHOLD_MS = 2_000L
private const val LOOK_AHEAD_SECONDS = 0.45f
private const val LATE_DROP_SECONDS = 0.3f
private const val MAX_TRACK_COUNT = 12
private const val MAX_ACTIVE_DANMAKU = 28
private const val FIXED_TRACK_COUNT = 3
private const val FIXED_DURATION_SECONDS = 4f
private const val MIN_TRACK_GAP_PX = 280f
private const val BOTTOM_RESERVED_DP = 132
private const val TAG = "BiliDanmaku"
private const val TAG_BLOCK = "BiliDanmakuBlock"
