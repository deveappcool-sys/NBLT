package com.nblt.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nblt.tv.theme.NbltPrimary
import com.nblt.tv.theme.TvColors
import com.nblt.tv.ui.components.TvFocusButton

@Composable
fun DanmakuBlocklistScreen(
    enabled: Boolean,
    keywords: List<String>,
    onEnabledChanged: (Boolean) -> Unit,
    onAddKeyword: (String) -> Unit,
    onRemoveKeyword: (String) -> Unit,
    onClearKeywords: () -> Unit,
    onBack: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(top = 8.dp)
    ) {
        Text(
            text = "\u5f39\u5e55\u5c4f\u853d\u8bcd",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "\u5339\u914d\u65b9\u5f0f\uff1a\u5305\u542b\u5339\u914d\uff08\u5ffd\u7565\u5927\u5c0f\u5199\uff09",
            color = Color(0xFFB8BDC7),
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 18.dp)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ToggleChip(
                label = "\u542f\u7528\u5c4f\u853d\u8bcd",
                selected = enabled,
                onClick = { onEnabledChanged(true) }
            )
            ToggleChip(
                label = "\u5173\u95ed\u5c4f\u853d\u8bcd",
                selected = !enabled,
                onClick = { onEnabledChanged(false) }
            )
        }

        Text(
            text = "\u6dfb\u52a0\u5c4f\u853d\u8bcd",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp, bottom = 10.dp)
        )

        BasicTextField(
            value = input,
            onValueChange = { input = it },
            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
            cursorBrush = SolidColor(NbltPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF27323A))
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .focusable()
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 12.dp)
        ) {
            TvFocusButton(
                text = "\u6dfb\u52a0",
                onClick = {
                    if (input.isNotBlank()) {
                        onAddKeyword(input)
                        input = ""
                    }
                }
            )
            TvFocusButton(
                text = "\u6e05\u7a7a\u5168\u90e8",
                onClick = onClearKeywords
            )
            TvFocusButton(
                text = "\u8fd4\u56de\u8bbe\u7f6e",
                onClick = onBack
            )
        }

        Text(
            text = "\u5df2\u6dfb\u52a0 ${keywords.size} \u4e2a\u5c4f\u853d\u8bcd",
            color = Color(0xFFD6DAE1),
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 24.dp, bottom = 10.dp)
        )

        if (keywords.isEmpty()) {
            Text(
                text = "\u6682\u65e0\u5c4f\u853d\u8bcd",
                color = Color(0xFFB8BDC7),
                fontSize = 17.sp
            )
        } else {
            keywords.forEach { keyword ->
                KeywordRow(
                    keyword = keyword,
                    onRemove = { onRemoveKeyword(keyword) }
                )
            }
        }
    }
}

@Composable
private fun ToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Text(
        text = label,
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) TvColors.AccentSoft else TvColors.SurfaceElevated)
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) TvColors.FocusAccent else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun KeywordRow(
    keyword: String,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(TvColors.SurfaceElevated)
            .border(1.dp, TvColors.CardBorder, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            text = keyword,
            color = Color.White,
            fontSize = 17.sp,
            modifier = Modifier.weight(1f)
        )
        TvFocusButton(
            text = "\u5220\u9664",
            onClick = onRemove
        )
    }
}
