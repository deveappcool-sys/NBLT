package com.bililite.tv.ui.my

enum class MyMenuItem {
    Favorites,
    WatchLater,
    History,
    Settings
}

data class MyMenuEntry(
    val item: MyMenuItem,
    val title: String,
    val subtitle: String,
    val requiresLogin: Boolean
)

fun buildMyMenuEntries(loggedIn: Boolean): List<MyMenuEntry> {
    return listOf(
        MyMenuEntry(
            item = MyMenuItem.Favorites,
            title = "\u6211\u7684\u6536\u85cf",
            subtitle = if (loggedIn) "\u67e5\u770b\u6536\u85cf\u5939\u4e0e\u6536\u85cf\u89c6\u9891" else "\u767b\u5f55\u540e\u53ef\u7528",
            requiresLogin = true
        ),
        MyMenuEntry(
            item = MyMenuItem.WatchLater,
            title = "\u7a0d\u540e\u518d\u770b",
            subtitle = if (loggedIn) "\u67e5\u770b\u7a0d\u540e\u518d\u770b\u5217\u8868" else "\u767b\u5f55\u540e\u53ef\u7528",
            requiresLogin = true
        ),
        MyMenuEntry(
            item = MyMenuItem.History,
            title = "\u5386\u53f2\u8bb0\u5f55",
            subtitle = if (loggedIn) "\u67e5\u770b\u89c2\u770b\u5386\u53f2" else "\u767b\u5f55\u540e\u53ef\u7528",
            requiresLogin = true
        ),
        MyMenuEntry(
            item = MyMenuItem.Settings,
            title = "\u8bbe\u7f6e",
            subtitle = "\u64ad\u653e\u3001\u5f39\u5e55\u4e0e\u754c\u9762\u9009\u9879",
            requiresLogin = false
        )
    )
}
