package com.nblt.tv.model

data class UpProfile(
    val mid: Long,
    val nickname: String,
    val avatarUrl: String,
    val sign: String,
    val followerCount: Long,
    val videoCount: Int
)
