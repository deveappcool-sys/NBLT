package com.bililite.tv.ui.search

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bililite.tv.theme.TvColors
import com.bililite.tv.ui.components.CinematicGlassSurface
import com.bililite.tv.ui.components.GlassVariant

enum class SearchInputMode {
    CHINESE,
    ENGLISH,
    NUMBER
}

data class NineKey(
    val label: String,
    val chars: List<String> = emptyList(),
    val action: NineKeyAction = NineKeyAction.INPUT
)

enum class NineKeyAction {
    INPUT,
    DELETE,
    CLEAR,
    SPACE,
    SEARCH,
    SWITCH_CHINESE,
    SWITCH_ENGLISH,
    SWITCH_NUMBER
}

@Composable
fun TvNineKeyKeyboard(
    mode: SearchInputMode,
    restoreFocusKeyIndex: Int,
    restoreFocusSignal: Int,
    firstKeyFocusRequester: FocusRequester? = null,
    topRowUpFocusRequester: FocusRequester? = null,
    onKeyFocused: (Int, NineKey) -> Unit,
    onKeySelected: (Int, NineKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val keys = remember(mode) { buildKeys(mode) }
    val focusRequesters = remember(keys, firstKeyFocusRequester) {
        List(keys.size) { index ->
            if (index == 0 && firstKeyFocusRequester != null) firstKeyFocusRequester else FocusRequester()
        }
    }

    LaunchedEffect(restoreFocusSignal) {
        if (restoreFocusSignal <= 1) {
            return@LaunchedEffect
        }
        val index = restoreFocusKeyIndex.coerceIn(0, focusRequesters.lastIndex)
        focusRequesters[index].requestFocus()
        Log.i(FOCUS_TAG, "focus restored to nine key index=$index, label=${keys[index].label}")
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        keys.chunked(3).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { columnIndex, key ->
                    val keyIndex = rowIndex * 3 + columnIndex
                    val isTopRow = keyIndex in 0..2
                    val isBottomRow = keyIndex in 9..11
                    NineKeyButton(
                        key = key,
                        upFocusRequester = topRowUpFocusRequester.takeIf { isTopRow },
                        blockDownFocus = isBottomRow,
                        modifier = Modifier.focusRequester(focusRequesters[keyIndex]),
                        onFocused = {
                            Log.i(FOCUS_TAG, "nine key focused index=$keyIndex, label=${key.label}")
                            onKeyFocused(keyIndex, key)
                        },
                        onClick = {
                            Log.i(TAG, "input mode=$mode, selected nine key=${key.label}")
                            Log.i(FOCUS_TAG, "nine key clicked index=$keyIndex, label=${key.label}")
                            onKeySelected(keyIndex, key)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NineKeyButton(
    key: NineKey,
    modifier: Modifier = Modifier,
    upFocusRequester: FocusRequester? = null,
    blockDownFocus: Boolean = false,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    CinematicGlassSurface(
        modifier = modifier
            .width(104.dp)
            .height(52.dp)
            .focusProperties {
                if (upFocusRequester != null) {
                    up = upFocusRequester
                }
                if (blockDownFocus) {
                    down = FocusRequester.Cancel
                }
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    onFocused()
                }
            }
            .focusable()
            .clickable(onClick = onClick),
        variant = GlassVariant.Control,
        focused = focused,
        visualOverrides = SearchControlGlassOverrides,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
    ) {
        Text(
            text = key.label,
            color = TvColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun buildKeys(mode: SearchInputMode): List<NineKey> {
    val inputKeys = when (mode) {
        SearchInputMode.NUMBER -> listOf(
            NineKey("1", listOf("1")),
            NineKey("2", listOf("2")),
            NineKey("3", listOf("3")),
            NineKey("4", listOf("4")),
            NineKey("5", listOf("5")),
            NineKey("6", listOf("6")),
            NineKey("7", listOf("7")),
            NineKey("8", listOf("8")),
            NineKey("9", listOf("9")),
            NineKey("0", listOf("0"))
        )
        else -> listOf(
            NineKey("1  符", listOf("1")),
            NineKey("2  ABC", listOf("A", "B", "C", "2")),
            NineKey("3  DEF", listOf("D", "E", "F", "3")),
            NineKey("4  GHI", listOf("G", "H", "I", "4")),
            NineKey("5  JKL", listOf("J", "K", "L", "5")),
            NineKey("6  MNO", listOf("M", "N", "O", "6")),
            NineKey("7  PQRS", listOf("P", "Q", "R", "S", "7")),
            NineKey("8  TUV", listOf("T", "U", "V", "8")),
            NineKey("9  WXYZ", listOf("W", "X", "Y", "Z", "9"))
        )
    }

    return inputKeys + listOf(
        NineKey("\u5220\u9664", action = NineKeyAction.DELETE),
        NineKey("\u6e05\u7a7a", action = NineKeyAction.CLEAR),
        NineKey("\u7a7a\u683c", action = NineKeyAction.SPACE)
    )
}

private const val TAG = "BiliSearch"
private const val FOCUS_TAG = "BiliSearchInput"
