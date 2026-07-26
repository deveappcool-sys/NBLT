package com.bililite.tv.data.repository

import android.util.Log
import com.bililite.tv.data.api.BilibiliFavoriteApi
import com.bililite.tv.data.api.FavoriteVideoPage
import com.bililite.tv.model.FavoriteFolder
import com.bililite.tv.storage.CookieStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FavoriteRepository(
    private val cookieStorage: CookieStorage,
    private val api: BilibiliFavoriteApi = BilibiliFavoriteApi()
) {
    suspend fun loadFolders(): Result<List<FavoriteFolder>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureLoggedIn()
                val user = cookieStorage.getSavedUserInfo() ?: error("\u8bf7\u5148\u767b\u5f55")
                api.fetchCreatedFolders(user.mid, cookieStorage.getCookieHeader())
            }
        }
    }

    suspend fun loadFolderVideos(
        mediaId: Long,
        page: Int = 1
    ): Result<FavoriteVideoPage> {
        return withContext(Dispatchers.IO) {
            runCatching {
                ensureLoggedIn()
                api.fetchFolderVideos(
                    mediaId = mediaId,
                    page = page,
                    cookieHeader = cookieStorage.getCookieHeader()
                )
            }
        }
    }

    private fun ensureLoggedIn() {
        if (!cookieStorage.hasLoginCookies()) {
            Log.i(TAG, "cookie status=missing")
            error("\u8bf7\u5148\u767b\u5f55")
        }
    }

    private companion object {
        const val TAG = "BiliFavorite"
    }
}
