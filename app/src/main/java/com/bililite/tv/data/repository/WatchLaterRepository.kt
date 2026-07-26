package com.bililite.tv.data.repository

import android.util.Log
import com.bililite.tv.data.api.BilibiliWatchLaterApi
import com.bililite.tv.data.api.WatchLaterPage
import com.bililite.tv.storage.CookieStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WatchLaterRepository(
    private val cookieStorage: CookieStorage,
    private val api: BilibiliWatchLaterApi = BilibiliWatchLaterApi()
) {
    suspend fun loadWatchLater(page: Int = 1): Result<WatchLaterPage> {
        return withContext(Dispatchers.IO) {
            runCatching {
                if (!cookieStorage.hasLoginCookies()) {
                    Log.i(TAG, "cookie status=missing")
                    error("\u8bf7\u5148\u767b\u5f55")
                }
                api.fetchWatchLaterPage(
                    page = page,
                    cookieHeader = cookieStorage.getCookieHeader()
                )
            }
        }
    }

    private companion object {
        const val TAG = "BiliWatchLater"
    }
}
