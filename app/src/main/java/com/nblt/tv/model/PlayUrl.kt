package com.nblt.tv.model

data class VideoQuality(
    val qn: Int,
    val description: String,
    val format: String? = null,
    val available: Boolean = true
)

data class PlayUrl(
    val videoUrl: String,
    val audioUrl: String?,
    val referer: String,
    val origin: String,
    val userAgent: String,
    val playbackProfile: PlaybackProfile = PlaybackProfile.WEB_CHROME_WINDOWS,
    val cookieHeader: String = "",
    val requestedQn: Int = 0,
    val sourceType: String = "unknown",
    val selectedFormat: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val backupUrls: List<String> = emptyList(),
    val videoUrlCandidates: List<String> = listOf(videoUrl),
    val audioUrlCandidates: List<String> = audioUrl?.let(::listOf).orEmpty(),
    val selectedVideoUrlIndex: Int = 0,
    val selectedAudioUrlIndex: Int = 0,
    val hasExpiringUrlParams: Boolean = false,
    val estimatedExpireTimeSeconds: Long? = null,
    val quality: VideoQuality? = null,
    val availableQualities: List<VideoQuality> = emptyList(),
    val requestId: Long = System.nanoTime()
) {
    val primaryUrl: String
        get() = videoUrl
}
