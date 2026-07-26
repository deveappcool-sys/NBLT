package com.bililite.tv.data.storage

import android.content.Context
import android.util.Log
import org.json.JSONArray

class DanmakuBlocklistStorage(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean {
        val enabled = preferences.getBoolean(KEY_ENABLED, true)
        Log.i(TAG, "blocklist enabled=$enabled")
        return enabled
    }

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
        Log.i(TAG, "blocklist enabled=$enabled")
    }

    fun loadKeywords(): List<String> {
        val raw = preferences.getString(KEY_KEYWORDS, "[]").orEmpty()
        val keywords = parseKeywords(raw)
        Log.i(TAG, "load blocklist count=${keywords.size}")
        return keywords
    }

    fun addKeyword(keyword: String): List<String> {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) {
            return loadKeywords()
        }
        val current = loadKeywords().toMutableList()
        if (current.any { it.equals(trimmed, ignoreCase = true) }) {
            return current
        }
        current += trimmed
        saveKeywords(current)
        Log.i(TAG, "add keyword=$trimmed")
        return current
    }

    fun removeKeyword(keyword: String): List<String> {
        val current = loadKeywords().filterNot { it.equals(keyword, ignoreCase = true) }
        saveKeywords(current)
        Log.i(TAG, "remove keyword=$keyword")
        return current
    }

    fun clearKeywords(): List<String> {
        saveKeywords(emptyList())
        Log.i(TAG, "clear blocklist")
        return emptyList()
    }

    private fun saveKeywords(keywords: List<String>) {
        val array = JSONArray()
        keywords.forEach { array.put(it) }
        preferences.edit().putString(KEY_KEYWORDS, array.toString()).apply()
        Log.i(TAG, "load blocklist count=${keywords.size}")
    }

    private fun parseKeywords(raw: String): List<String> {
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optString(index).trim()
                    if (item.isNotBlank()) {
                        add(item)
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val PREFS_NAME = "danmaku_blocklist"
        const val KEY_ENABLED = "enabled"
        const val KEY_KEYWORDS = "keywords"
        const val TAG = "BiliDanmakuBlock"
    }
}
