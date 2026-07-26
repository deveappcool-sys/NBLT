package com.nblt.tv.util

import android.util.Log

class PaginationLoadGuard {
    private val inFlightKeys = mutableSetOf<String>()

    fun tryStart(
        listKey: String,
        pageKey: String,
        isLoadingMore: Boolean,
        hasMore: Boolean
    ): Boolean {
        if (!hasMore) {
            Log.i(
                TAG,
                "list=$listKey, page=$pageKey, isLoadingMore=$isLoadingMore, hasMore=false, ignored"
            )
            logUpSpaceGuard(listKey, pageKey, isLoadingMore, hasMore, allowed = false, reason = "hasMore=false")
            return false
        }
        if (isLoadingMore) {
            Log.i(
                TAG,
                "list=$listKey, page=$pageKey, isLoadingMore=true, hasMore=true, ignored"
            )
            logUpSpaceGuard(listKey, pageKey, isLoadingMore, hasMore, allowed = false, reason = "isLoadingMore=true")
            return false
        }
        val token = "$listKey:$pageKey"
        if (!inFlightKeys.add(token)) {
            Log.i(
                TAG,
                "list=$listKey, page=$pageKey, isLoadingMore=false, ignored duplicate page"
            )
            logUpSpaceGuard(listKey, pageKey, isLoadingMore, hasMore, allowed = false, reason = "duplicate page")
            return false
        }
        logUpSpaceGuard(listKey, pageKey, isLoadingMore, hasMore, allowed = true, reason = "allowed")
        return true
    }

    fun finish(listKey: String, pageKey: String) {
        inFlightKeys.remove("$listKey:$pageKey")
        if (listKey == UP_SPACE_LIST_KEY) {
            UpSpaceDebugLog.logPaginationGuard(
                listKey = listKey,
                pageKey = pageKey,
                isLoadingMore = false,
                hasMore = true,
                allowed = true,
                reason = "finish"
            )
        }
    }

    fun reset(listKey: String) {
        inFlightKeys.removeAll { it.startsWith("$listKey:") }
    }

    private fun logUpSpaceGuard(
        listKey: String,
        pageKey: String,
        isLoadingMore: Boolean,
        hasMore: Boolean,
        allowed: Boolean,
        reason: String
    ) {
        if (listKey == UP_SPACE_LIST_KEY) {
            UpSpaceDebugLog.logPaginationGuard(
                listKey = listKey,
                pageKey = pageKey,
                isLoadingMore = isLoadingMore,
                hasMore = hasMore,
                allowed = allowed,
                reason = reason
            )
        }
    }

    private companion object {
        const val TAG = "BiliPagination"
        const val UP_SPACE_LIST_KEY = "upspace"
    }
}
