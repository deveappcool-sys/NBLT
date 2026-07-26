package com.nblt.tv.data.repository

import android.util.Log
import com.nblt.tv.data.api.BilibiliHistoryApi
import com.nblt.tv.data.api.HistoryPage
import com.nblt.tv.model.VideoItem
import com.nblt.tv.storage.CookieStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HistoryRepository(
    private val cookieStorage: CookieStorage,
    private val historyApi: BilibiliHistoryApi = BilibiliHistoryApi()
) {
    suspend fun loadHistory(): Result<HistoryPage> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val cookieHeader = cookieStorage.getCookieHeader()
                Log.i(
                    TAG,
                    "Load history: hasLoginCookies=${cookieStorage.hasLoginCookies()}, " +
                        "cookieBlank=${cookieHeader.isBlank()}, " +
                        "hasSESSDATA=${cookieHeader.contains("SESSDATA=")}, " +
                        "hasDedeUserID=${cookieHeader.contains("DedeUserID=")}, " +
                        "hasBiliJct=${cookieHeader.contains("bili_jct=")}"
                )
                if (!cookieStorage.hasLoginCookies()) {
                    error("请先登录")
                }
                historyApi.fetchHistoryPage(cookieHeader)
            }
        }
    }

    suspend fun loadMoreHistory(
        cursorMax: Long,
        cursorViewAt: Long
    ): Result<HistoryPage> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val cookieHeader = cookieStorage.getCookieHeader()
                if (!cookieStorage.hasLoginCookies()) {
                    error("\u8bf7\u5148\u767b\u5f55")
                }
                historyApi.fetchHistoryPage(
                    cookieHeader = cookieHeader,
                    cursorMax = cursorMax,
                    cursorViewAt = cursorViewAt
                )
            }
        }
    }

    private companion object {
        const val TAG = "BiliHistory"
    }
}
