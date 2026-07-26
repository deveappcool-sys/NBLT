package com.nblt.tv.data.repository

import android.util.Log
import com.nblt.tv.util.UpSpaceDebugLog

class DuplicateUpSpaceRequestException : Exception("duplicate up space video request")

class UpSpaceRequestGuard {
    private val inFlightKeys = mutableSetOf<String>()

    @Synchronized
    fun tryStart(mid: Long, page: Int): Boolean {
        val key = requestKey(mid, page)
        if (!inFlightKeys.add(key)) {
            Log.i(TAG, "ignore duplicate request mid=$mid page=$page")
            UpSpaceDebugLog.logDuplicateRequestGuard(mid = mid, page = page, allowed = false)
            return false
        }
        UpSpaceDebugLog.logDuplicateRequestGuard(mid = mid, page = page, allowed = true)
        return true
    }

    @Synchronized
    fun finish(mid: Long, page: Int) {
        inFlightKeys.remove(requestKey(mid, page))
        UpSpaceDebugLog.logRepositoryPath(
            path = "request guard finish",
            detail = "mid=$mid, page=$page"
        )
    }

    @Synchronized
    fun isInFlight(mid: Long, page: Int): Boolean {
        val inFlight = requestKey(mid, page) in inFlightKeys
        UpSpaceDebugLog.logRepositoryPath(
            path = "request guard inFlight check",
            detail = "mid=$mid, page=$page, inFlight=$inFlight"
        )
        return inFlight
    }

    private fun requestKey(mid: Long, page: Int): String = "$mid:$page"

    private companion object {
        const val TAG = "BiliUpSpace"
    }
}
