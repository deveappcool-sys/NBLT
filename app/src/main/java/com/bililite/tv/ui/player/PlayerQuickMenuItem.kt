package com.bililite.tv.ui.player

enum class PlayerQuickMenuItem {
    Quality,
    Speed,
    Pages,
    Recommendations,
    DanmakuToggle,
    DanmakuSettings,
    RestartPlayback,
    BackToDetail
}

enum class PlayerSubMenu {
    None,
    Quality,
    Speed,
    Pages,
    DanmakuSettings
}

val playbackSpeeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
