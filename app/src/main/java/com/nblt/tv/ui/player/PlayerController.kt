package com.nblt.tv.ui.player

class PlayerController(
    val playOrPause: () -> Unit,
    val seekBack: () -> Unit,
    val seekForward: () -> Unit,
    val seekTo: (Long) -> Unit,
    val seekToStart: () -> Unit,
    val restartAndPlay: () -> Unit,
    val play: () -> Unit,
    val pause: () -> Unit,
    val setPlaybackSpeed: (Float) -> Unit
)
