package com.nblt.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nblt.tv.model.VideoItem

@Composable
fun PlayerPlaceholderScreen(video: VideoItem) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(46.dp)
    ) {
        Text(
            text = video.title,
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart)
        )

        Text(
            text = "播放器占位页",
            color = Color(0xFFB8BDC7),
            fontSize = 24.sp,
            modifier = Modifier.align(Alignment.Center)
        )

        Text(
            text = "按返回键返回上一级",
            color = Color(0xFF9EA3AE),
            fontSize = 18.sp,
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}
