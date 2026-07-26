package com.nblt.tv.model

data class VideoPage(
    val cid: Long,
    val page: Int,
    val part: String,
    val duration: Int,
    val aid: Long = 0L,
    val bvid: String = "",
    val epId: Long = 0L,
    val seasonId: Long = 0L,
    val coverUrl: String = "",
    val sectionTitle: String = "",
    val isCollectionEpisode: Boolean = false
)
