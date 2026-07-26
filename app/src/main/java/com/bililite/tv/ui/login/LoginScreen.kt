package com.bililite.tv.ui.login

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bililite.tv.model.LoginQrCode
import com.bililite.tv.theme.BiliLiteBackground
import com.bililite.tv.theme.BiliLitePrimary
import com.bililite.tv.theme.TvColors
import com.bililite.tv.ui.components.TvBackground
import com.bililite.tv.ui.state.UiState
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun LoginScreen(
    qrState: UiState<LoginQrCode>,
    message: String,
    onRetry: () -> Unit,
    awaitingUserInfo: Boolean = false,
    onRetryFetchUserInfo: (() -> Unit)? = null
) {
    TvBackground(modifier = Modifier.fillMaxSize()) {
    Box(modifier = Modifier.fillMaxSize().padding(56.dp), contentAlignment = Alignment.Center) {
        when (qrState) {
            UiState.Loading -> Text(
                text = "\u6b63\u5728\u83b7\u53d6\u767b\u5f55\u4e8c\u7ef4\u7801...",
                color = Color(0xFFB8BDC7),
                fontSize = 24.sp
            )

            is UiState.Success -> LoginQrContent(
                qrCode = qrState.data,
                message = message,
                onRetry = onRetry,
                awaitingUserInfo = awaitingUserInfo,
                onRetryFetchUserInfo = onRetryFetchUserInfo
            )

            is UiState.Error -> LoginErrorContent(
                message = qrState.message,
                onRetry = onRetry
            )
        }
    }}
}

@Composable
private fun LoginQrContent(
    qrCode: LoginQrCode,
    message: String,
    onRetry: () -> Unit,
    awaitingUserInfo: Boolean = false,
    onRetryFetchUserInfo: (() -> Unit)? = null
) {
    val qrBitmap = remember(qrCode.url) { createQrBitmap(qrCode.url) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "\u767b\u5f55 Bilibili \u8d26\u53f7",
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(320.dp)
                .background(Color.White, RoundedCornerShape(8.dp))
                .padding(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = qrBitmap.asImageBitmap(),
                contentDescription = "\u767b\u5f55\u4e8c\u7ef4\u7801",
                modifier = Modifier.fillMaxSize()
            )
        }

        Text(
            text = message.ifBlank { "\u8bf7\u4f7f\u7528\u624b\u673a Bilibili \u626b\u7801\u767b\u5f55" },
            color = if (awaitingUserInfo) Color(0xFFFFB4AB) else Color(0xFFD6DAE1),
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp, bottom = 24.dp)
        )

        if (awaitingUserInfo && onRetryFetchUserInfo != null) {
            LoginButton(
                text = "\u91cd\u8bd5\u83b7\u53d6\u7528\u6237\u4fe1\u606f",
                onClick = onRetryFetchUserInfo
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        LoginButton(
            text = "\u5237\u65b0\u4e8c\u7ef4\u7801",
            onClick = onRetry
        )
    }
}

@Composable
private fun LoginErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "\u767b\u5f55\u5931\u8d25",
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = message,
            color = Color(0xFFB8BDC7),
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
        )

        LoginButton(
            text = "\u91cd\u8bd5",
            onClick = onRetry
        )
    }
}

@Composable
private fun LoginButton(
    text: String,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    Button(
        onClick = onClick,
        modifier = Modifier
            .width(190.dp)
            .height(54.dp)
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) TvColors.FocusAccent else Color.Transparent,
                shape = RoundedCornerShape(999.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (focused) TvColors.FocusAccent else TvColors.Accent,
            contentColor = if (focused) Color(0xFF161817) else Color.White
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

private fun createQrBitmap(content: String): Bitmap {
    val size = 420
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    for (x in 0 until size) {
        for (y in 0 until size) {
            bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
    return bitmap
}
