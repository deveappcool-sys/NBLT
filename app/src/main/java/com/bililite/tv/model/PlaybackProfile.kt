package com.bililite.tv.model

enum class PlaybackProfile(
    val label: String,
    val shortName: String,
    val userAgent: String
) {
    WEB_CHROME_WINDOWS(
        label = "Chrome Windows",
        shortName = "ChromeWin",
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
    ),
    WEB_SAFARI_MAC(
        label = "Safari Mac",
        shortName = "SafariMac",
        userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"
    ),
    WEB_SAFARI_IPAD(
        label = "Safari iPad",
        shortName = "SafariIPad",
        userAgent = "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
    );

    companion object {
        val fallbackOrder = listOf(WEB_SAFARI_MAC, WEB_CHROME_WINDOWS, WEB_SAFARI_IPAD)

        fun fromName(name: String?): PlaybackProfile {
            return values().firstOrNull { it.name == name } ?: WEB_CHROME_WINDOWS
        }
    }
}
