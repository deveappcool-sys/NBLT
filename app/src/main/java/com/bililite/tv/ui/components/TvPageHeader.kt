package com.bililite.tv.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bililite.tv.theme.TvColors

@Composable
fun TvPageHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(bottom = 18.dp)) {
        Text(
            text = title,
            color = TvColors.TextPrimary,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = TvColors.TextSecondary,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 5.dp)
            )
        }
    }
}
