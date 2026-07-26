package com.nblt.tv.data.storage

import android.content.Context
import android.util.Log
import org.json.JSONArray

class SearchHistoryStorage(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadHistory(): List<String> {
        val raw = preferences.getString(KEY_HISTORY, "[]").orEmpty()
        val history = runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val keyword = array.optString(index).trim()
                    if (keyword.isNotBlank()) {
                        add(keyword)
                    }
                }
            }
        }.getOrElse { emptyList() }
        Log.i(TAG, "load history count=${history.size}")
        return history
    }

    fun addHistory(keyword: String): List<String> {
        val clean = keyword.trim()
        if (clean.isBlank()) {
            return loadHistory()
        }
        val oldHistory = loadHistory()
        val withoutDuplicate = oldHistory.filterNot { it.equals(clean, ignoreCase = true) }
        if (withoutDuplicate.size != oldHistory.size) {
            Log.i(TAG, "duplicate keyword moved to top=$clean")
        } else {
            Log.i(TAG, "add history keyword=$clean")
        }
        val newHistory = (listOf(clean) + withoutDuplicate).take(MAX_HISTORY_COUNT)
        saveHistory(newHistory)
        return newHistory
    }

    fun clearHistory() {
        preferences.edit().remove(KEY_HISTORY).apply()
        Log.i(TAG, "clear history")
    }

    private fun saveHistory(history: List<String>) {
        val array = JSONArray()
        history.forEach { array.put(it) }
        preferences.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "search_history"
        const val KEY_HISTORY = "keywords"
        const val MAX_HISTORY_COUNT = 20
        const val TAG = "BiliSearchHistory"
    }
}
