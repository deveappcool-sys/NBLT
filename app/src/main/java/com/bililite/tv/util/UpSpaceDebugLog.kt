package com.bililite.tv.util

import android.util.Log
import com.bililite.tv.data.api.SessionExpiredException
import com.bililite.tv.data.repository.UpSpaceVideoErrors

object UpSpaceDebugLog {
    const val TAG = "BiliUpSpaceDebug"

    fun logOpenUpSpace(
        mid: Long,
        upName: String,
        sourceScreen: String,
        sourceTab: String
    ) {
        Log.i(
            TAG,
            "enter up space: mid=$mid, upName=$upName, sourceScreen=$sourceScreen, sourceTab=$sourceTab"
        )
    }

    fun logBeforeVideoRequest(
        endpoint: String,
        mid: Long,
        page: Int,
        pn: Int,
        ps: Int,
        order: String,
        requestUrl: String,
        hasWbi: Boolean,
        hasCookie: Boolean,
        hasWebLocation: Boolean,
        hasDmImgParams: Boolean,
        hasReferer: Boolean,
        hasOrigin: Boolean,
        signedParamKeys: List<String>,
        userAgent: String
    ) {
        Log.i(
            TAG,
            "before video request: endpoint=$endpoint, mid=$mid, page=$page, pn=$pn, ps=$ps, " +
                "order=$order, hasWbi=$hasWbi, hasCookie=$hasCookie, hasWebLocation=$hasWebLocation, " +
                "hasDmImgParams=$hasDmImgParams, hasReferer=$hasReferer, hasOrigin=$hasOrigin, " +
                "signedParamKeys=$signedParamKeys, requestUrl=$requestUrl, userAgent=$userAgent"
        )
    }

    fun logWbiSign(
        imgKeySuffix: String,
        subKeySuffix: String,
        wts: String,
        wRid: String,
        signedParamKeys: List<String>
    ) {
        Log.i(
            TAG,
            "wbi sign: imgKeySuffix=$imgKeySuffix, subKeySuffix=$subKeySuffix, wts=$wts, " +
                "wRid=$wRid, wRidLen=${wRid.length}, signedParamKeys=$signedParamKeys"
        )
    }

    fun logAfterVideoResponse(
        httpStatus: Int,
        bilibiliCode: Int,
        bilibiliMessage: String,
        bodyPreview: String
    ) {
        Log.i(
            TAG,
            "after video response: httpStatus=$httpStatus, bilibiliCode=$bilibiliCode, " +
                "bilibiliMessage=$bilibiliMessage, bodyPreview=${bodyPreview.take(500)}"
        )
    }

    fun logResponseClassification(
        bilibiliCode: Int,
        bilibiliMessage: String,
        sessionExpiredRecognized: Boolean,
        rateLimitRecognized: Boolean,
        mappedToRateLimitMessage: Boolean
    ) {
        Log.i(
            TAG,
            "response classification: bilibiliCode=$bilibiliCode, bilibiliMessage=$bilibiliMessage, " +
                "sessionExpiredRecognized=$sessionExpiredRecognized, " +
                "rateLimitRecognized=$rateLimitRecognized, " +
                "mappedToRateLimitMessage=$mappedToRateLimitMessage"
        )
    }

    fun logUiStateTarget(target: String, detail: String = "") {
        val suffix = if (detail.isBlank()) "" else ", $detail"
        Log.i(TAG, "ui state target: $target$suffix")
    }

    fun logRepositoryPath(path: String, detail: String = "") {
        val suffix = if (detail.isBlank()) "" else ", $detail"
        Log.i(TAG, "repository: $path$suffix")
    }

    fun logPaginationGuard(
        listKey: String,
        pageKey: String,
        isLoadingMore: Boolean,
        hasMore: Boolean,
        allowed: Boolean,
        reason: String
    ) {
        Log.i(
            TAG,
            "pagination guard: listKey=$listKey, pageKey=$pageKey, isLoadingMore=$isLoadingMore, " +
                "hasMore=$hasMore, allowed=$allowed, reason=$reason"
        )
    }

    fun logDuplicateRequestGuard(mid: Long, page: Int, allowed: Boolean) {
        Log.i(TAG, "duplicate request guard: mid=$mid, page=$page, allowed=$allowed")
    }

    fun logLoadMoreTrigger(
        videosSize: Int,
        hasMore: Boolean,
        isLoadingMore: Boolean,
        loadMoreError: String?
    ) {
        Log.i(
            TAG,
            "load more trigger: videosSize=$videosSize, hasMore=$hasMore, " +
                "isLoadingMore=$isLoadingMore, loadMoreError=$loadMoreError"
        )
    }

    fun isSessionExpiredCode(code: Int): Boolean = code == -101

    fun isRateLimitMessage(message: String?): Boolean {
        return UpSpaceVideoErrors.isRateLimitedMessage(message)
    }

    fun isMappedToRateLimitMessage(message: String?): Boolean {
        return message == UpSpaceVideoErrors.RATE_LIMIT_MESSAGE ||
            UpSpaceVideoErrors.isRateLimitedMessage(message)
    }

    fun isSessionExpiredThrowable(error: Throwable): Boolean {
        return SessionExpiredException.isExpired(error)
    }
}
