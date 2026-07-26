package com.nblt.tv.ui.home

object HomeNavTabs {
    const val HOME = "\u9996\u9875"
    const val RECOMMEND = "\u63a8\u8350"
    const val POPULAR = "\u70ed\u95e8"
    const val LIVE = "\u76f4\u64ad"
    const val SEARCH = "\u641c\u7d22"
    const val DYNAMIC = "\u52a8\u6001"
    const val HISTORY = "\u5386\u53f2"
    const val MY = "\u6211\u7684"
    const val SETTINGS = "\u8bbe\u7f6e"

    val ALL = listOf(HOME, RECOMMEND, POPULAR, LIVE, SEARCH, DYNAMIC, HISTORY, MY, SETTINGS)

    /** Items that are visible but disabled (non-focusable, dimmed, skipped by D-pad). */
    val DISABLED = setOf(LIVE)
}
