package com.bililite.tv.data.repository

import com.bililite.tv.data.api.BilibiliLoginApi
import com.bililite.tv.data.api.LoginPollResult
import com.bililite.tv.model.LoginQrCode
import com.bililite.tv.model.UserInfo
import com.bililite.tv.storage.CookieStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LoginRepository(
    private val cookieStorage: CookieStorage,
    private val loginApi: BilibiliLoginApi = BilibiliLoginApi()
) {
    fun getSavedUserInfo(): UserInfo? = cookieStorage.getSavedUserInfo()

    fun isLoggedIn(): Boolean = cookieStorage.hasLoginCookies()

    suspend fun generateQrCode(): Result<LoginQrCode> {
        return withContext(Dispatchers.IO) {
            runCatching { loginApi.generateQrCode() }
        }
    }

    suspend fun pollQrCode(qrcodeKey: String): LoginPollResult {
        return withContext(Dispatchers.IO) {
            when (val result = loginApi.pollQrCode(qrcodeKey)) {
                is LoginPollResult.Success -> {
                    cookieStorage.saveCookies(result.cookies)
                    result
                }

                else -> result
            }
        }
    }

    suspend fun fetchUserInfo(): Result<UserInfo> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val userInfo = loginApi.fetchUserInfo(cookieStorage.getCookieHeader())
                cookieStorage.saveUserInfo(userInfo)
                userInfo
            }
        }
    }

    fun logout() {
        cookieStorage.clear()
    }
}
