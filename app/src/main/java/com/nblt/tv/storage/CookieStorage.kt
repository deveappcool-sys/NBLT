package com.nblt.tv.storage

import android.content.Context
import com.nblt.tv.model.UserInfo

class CookieStorage(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveCookies(cookies: Map<String, String>) {
        preferences.edit().apply {
            COOKIE_KEYS.forEach { key ->
                cookies[key]?.takeIf { it.isNotBlank() }?.let { putString(key, it) }
            }
        }.apply()
    }

    fun getCookieHeader(): String {
        return COOKIE_KEYS.mapNotNull { key ->
            preferences.getString(key, null)?.let { value -> "$key=$value" }
        }.joinToString("; ")
    }

    fun getCookieValue(name: String): String? {
        return preferences.getString(name, null)
    }

    fun hasLoginCookies(): Boolean {
        return !preferences.getString("SESSDATA", null).isNullOrBlank()
    }

    fun saveUserInfo(userInfo: UserInfo) {
        preferences.edit()
            .putLong(KEY_MID, userInfo.mid)
            .putString(KEY_NICKNAME, userInfo.nickname)
            .putString(KEY_AVATAR, userInfo.avatarUrl)
            .apply()
    }

    fun getSavedUserInfo(): UserInfo? {
        val mid = preferences.getLong(KEY_MID, 0L)
        val nickname = preferences.getString(KEY_NICKNAME, null)
        val avatar = preferences.getString(KEY_AVATAR, null).orEmpty()
        return if (mid > 0L && !nickname.isNullOrBlank()) {
            UserInfo(mid = mid, nickname = nickname, avatarUrl = avatar)
        } else {
            null
        }
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "bili_auth"
        const val KEY_MID = "user_mid"
        const val KEY_NICKNAME = "user_nickname"
        const val KEY_AVATAR = "user_avatar"
        val COOKIE_KEYS = listOf("SESSDATA", "bili_jct", "DedeUserID", "DedeUserID__ckMd5", "sid")
    }
}
