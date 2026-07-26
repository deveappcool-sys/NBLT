package com.bililite.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bililite.tv.theme.BiliLitePrimary
import com.bililite.tv.theme.TvColors
import com.bililite.tv.theme.TvDimensions
import kotlinx.coroutines.delay

@Composable
fun TvLoadingContent(message: String = "\u6b63\u5728\u52a0\u8f7d...") {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                color = TvColors.TextSecondary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun TvEmptyContent(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = TvColors.TextSecondary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun TvNotLoggedInContent(
    hint: String,
    onLoginClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "\u8bf7\u5148\u767b\u5f55",
                color = TvColors.TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = hint,
                color = TvColors.TextSecondary,
                fontSize = 17.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 24.dp)
            )
            TvFocusButton(text = "\u53bb\u767b\u5f55", onClick = onLoginClick)
        }
    }
}

@Composable
fun TvErrorContent(
    title: String,
    message: String,
    onRetry: () -> Unit
) {
    val retryFocusRequester = remember { FocusRequester() }
    LaunchedEffect(title, message) {
        delay(80)
        runCatching { retryFocusRequester.requestFocus() }
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                color = TvColors.TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = message,
                color = TvColors.TextSecondary,
                fontSize = 17.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 24.dp)
            )
            TvFocusButton(
                text = "\u91cd\u8bd5",
                onClick = onRetry,
                modifier = Modifier.focusRequester(retryFocusRequester)
            )
        }
    }
}

@Composable
fun TvFocusButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    blockUp: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        modifier = modifier
            .height(52.dp)
            .border(
                width = if (focused) TvDimensions.focusBorderWidth else 0.dp,
                color = if (focused) TvColors.FocusAccent else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(999.dp)
            )
            .onPreviewKeyEvent { event ->
                blockUp &&
                    event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionUp
            }
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (focused) TvColors.FocusAccent else TvColors.Accent,
            contentColor = if (focused) androidx.compose.ui.graphics.Color(0xFF161817) else TvColors.TextPrimary
        ),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
