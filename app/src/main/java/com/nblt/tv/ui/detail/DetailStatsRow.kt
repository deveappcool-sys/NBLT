package com.nblt.tv.ui.detail

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nblt.tv.model.VideoItem
import com.nblt.tv.model.VideoPage
import com.nblt.tv.theme.TvColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun DetailStatsRow(
    video: VideoItem,
    currentPage: VideoPage?,
    modifier: Modifier = Modifier
) {
    val displayStats = remember(video) { video.toDetailDisplayStats() }
    val chips = remember(video, currentPage, displayStats) {
        buildDetailStatChips(video, currentPage, displayStats)
    }
    if (chips.isEmpty()) {
        return
    }

    Text(
        text = chips.joinToString("  \u00B7  "),
        color = TvColors.TextSecondary,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth()
    )
}

private fun buildDetailStatChips(
    video: VideoItem,
    currentPage: VideoPage?,
    displayStats: DetailDisplayStats
): List<String> {
    val chips = mutableListOf<String>()
    formatDetailPubDate(video.pubdate).takeIf { it.isNotBlank() }?.let(chips::add)
    if (video.playCount > 0L) {
        chips += "${video.views}\u64ad\u653e"
    }
    formatDetailCount(displayStats.danmakuCount)?.let { chips += "${it}\u5f39\u5e55" }
    formatDetailCount(displayStats.coinCount)?.let { chips += "${it}\u6295\u5e01" }
    formatDetailCount(displayStats.favoriteCount)?.let { chips += "${it}\u6536\u85cf" }
    if (video.duration > 0L) {
        chips += video.durationText
    }
    if (video.pages.size > 1) {
        val pageLabel = currentPage?.let { "\u5171 ${video.pages.size}P" }
            ?: "\u5171 ${video.pages.size}P"
        chips += pageLabel
    }
    if (video.historyProgress > 0L) {
        chips += "\u5df2\u770b\u8fdb\u5ea6"
    }
    return chips
}

private fun formatDetailPubDate(timestampSeconds: Long): String {
    if (timestampSeconds <= 0L) {
        return ""
    }
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return formatter.format(Date(timestampSeconds * 1000))
}
