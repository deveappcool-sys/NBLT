package com.bililite.tv.model

data class DanmakuItem(
    val timeSeconds: Float,
    val text: String,
    val mode: Int,
    val color: Int,
    val fontSize: Float
)

data class DanmakuSettings(
    val enabledByDefault: Boolean = true,
    val fontScale: Float = 1.0f,
    val alpha: Float = 0.8f,
    val speed: Float = 1.0f,
    val displayAreaRatio: Float = 0.5f
)
