package com.nblt.tv.ui.search

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nblt.tv.theme.TvColors
import com.nblt.tv.ui.components.CinematicGlassSurface
import com.nblt.tv.ui.components.GlassVariant

@Composable
fun CharacterCandidateBar(
    candidates: List<String>,
    focusSignal: Int,
    onCandidateSelected: (String) -> Unit,
    onReturnToNineKey: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleCandidates = candidates.ifEmpty { listOf("") }
    val focusRequesters = remember(visibleCandidates) {
        List(visibleCandidates.size) { FocusRequester() }
    }

    LaunchedEffect(focusSignal) {
        if (candidates.isNotEmpty() && focusSignal > 0) {
            focusRequesters.first().requestFocus()
            Log.i(FOCUS_TAG, "move focus to candidate first=${candidates.first()}")
        }
    }

    CinematicGlassSurface(
        modifier = modifier,
        variant = GlassVariant.Panel,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            visibleCandidates.forEachIndexed { index, candidate ->
                CandidateButton(
                    text = candidate,
                    enabled = candidates.isNotEmpty(),
                    modifier = Modifier.focusRequester(focusRequesters[index]),
                    onFocused = {
                        if (candidate.isNotEmpty()) {
                            Log.i(FOCUS_TAG, "candidate focused=$candidate")
                        }
                    },
                    onReturnToNineKey = onReturnToNineKey,
                    onClick = {
                        if (candidate.isNotEmpty()) {
                            Log.i(FOCUS_TAG, "candidate selected=$candidate")
                            onCandidateSelected(candidate)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CandidateButton(
    text: String,
    enabled: Boolean,
    modifier: Modifier,
    onFocused: () -> Unit,
    onReturnToNineKey: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    CinematicGlassSurface(
        modifier = modifier
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    false
                } else {
                    when (event.key) {
                        Key.Back -> {
                            onReturnToNineKey()
                            true
                        }
                        else -> false
                    }
                }
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    onFocused()
                }
            }
            .focusable(enabled)
            .clickable(enabled = enabled, onClick = onClick),
        variant = GlassVariant.Control,
        focused = focused,
        visualOverrides = SearchControlGlassOverrides,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp)
    ) {
        Text(
            text = text.uppercase(),
            color = if (enabled) TvColors.TextPrimary else TvColors.TextMuted,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private const val FOCUS_TAG = "BiliSearchFocus"
