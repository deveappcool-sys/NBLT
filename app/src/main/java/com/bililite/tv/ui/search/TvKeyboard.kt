package com.bililite.tv.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bililite.tv.theme.BiliLitePrimary

@Composable
fun TvKeyboard(
    onInput: (String) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onSpace: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = listOf(
        listOf("A", "B", "C", "D", "E", "F", "G"),
        listOf("H", "I", "J", "K", "L", "M", "N"),
        listOf("O", "P", "Q", "R", "S", "T"),
        listOf("U", "V", "W", "X", "Y", "Z"),
        listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    KeyboardKey(text = key, onClick = { onInput(key.lowercase()) })
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KeyboardKey(text = "\u5220\u9664", wide = true, onClick = onDelete)
            KeyboardKey(text = "\u6e05\u7a7a", wide = true, onClick = onClear)
            KeyboardKey(text = "\u7a7a\u683c", wide = true, onClick = onSpace)
            KeyboardKey(text = "\u641c\u7d22", wide = true, primary = true, onClick = onSearch)
        }
    }
}

@Composable
private fun KeyboardKey(
    text: String,
    wide: Boolean = false,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale = if (focused) 1.08f else 1f
    val background = when {
        focused -> BiliLitePrimary
        primary -> Color(0xFF27323A)
        else -> Color(0xFF1B1E24)
    }

    Text(
        text = text,
        color = Color.White,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .height(46.dp)
            .widthIn(min = if (wide) 82.dp else 46.dp)
            .scale(scale)
            .background(background, RoundedCornerShape(8.dp))
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) Color.White else Color(0xFF343A45),
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp)
    )
}
