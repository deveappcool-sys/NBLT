package com.bililite.tv.data.api

import android.util.Log
import com.bililite.tv.model.VideoItem
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject

data class HeartbeatApiResponse(
    val code: Int,
    val message: String
)

class BilibiliHeartbeatApi {
    fun reportPlaybackProgress(
        video: VideoItem,
        cookieHeader: String,
        csrf: String,
        playedTimeSeconds: Long,
        durationSeconds: Long,
        startTsSeconds: Long,
        event: String
    ): HeartbeatApiResponse {
        val requestBody = FormBody.Builder()
            .add("aid", video.aid.toString())
            .add("cid", video.cid.toString())
            .add("bvid", video.bvid)
            .add("played_time", playedTimeSeconds.toString())
            .add("realtime", durationSeconds.toString())
            .add("type", "3")
            .add("dt", "2")
            .add("play_type", "1")
            .add("start_ts", startTsSeconds.toString())
            .add("csrf", csrf)
            .add("csrf_token", csrf)
            .build()

        val request = Request.Builder()
            .url(HEARTBEAT_URL)
            .header("User-Agent", BilibiliApiClient.USER_AGENT)
            .header("Referer", buildReferer(video))
            .header("Origin", BilibiliApiClient.ORIGIN)
            .header("Cookie", cookieHeader)
            .post(requestBody)
            .build()

        Log.i(
            TAG,
            "Request URL: ${request.url}, event=$event, aid=${video.aid}, cid=${video.cid}, " +
                "bvid=${video.bvid}, currentPosition=${playedTimeSeconds}s, duration=${durationSeconds}s"
        )

        BilibiliApiClient.httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val root = body.takeIf { it.isNotBlank() }?.let { JSONObject(it) }
            val code = root?.optInt("code", response.code) ?: response.code
            val message = root?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: response.message

            Log.i(
                TAG,
                "Response: httpCode=${response.code}, code=$code, message=$message, " +
                    "aid=${video.aid}, cid=${video.cid}, bvid=${video.bvid}, " +
                    "currentPosition=${playedTimeSeconds}s, duration=${durationSeconds}s"
            )

            return HeartbeatApiResponse(code = code, message = message)
        }
    }

    private fun buildReferer(video: VideoItem): String {
        return if (video.bvid.isNotBlank()) {
            "https://www.bilibili.com/video/${video.bvid}"
        } else {
            BilibiliApiClient.REFERER
        }
    }

    private companion object {
        const val TAG = "BiliHeartbeat"
        const val HEARTBEAT_URL = "https://api.bilibili.com/x/click-interface/web/heartbeat"
    }
}
